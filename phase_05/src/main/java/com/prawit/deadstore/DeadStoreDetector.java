package com.prawit.deadstore;

import com.ibm.wala.classLoader.IBytecodeMethod;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.core.util.config.AnalysisScopeReader;
import com.ibm.wala.ipa.callgraph.AnalysisCacheImpl;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.IAnalysisCacheView;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.ssa.DefUse;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAPutInstruction;
import com.ibm.wala.ssa.SymbolTable;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.MethodReference;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

public class DeadStoreDetector {
  public static void main(String[] args) throws Exception {
    if (args.length < 1) {
      System.out.println("Usage: DeadStoreDetector <path-to-java-file> [--debug]");
      System.exit(1);
    }

    boolean debug = false;
    for (String a : args) {
      if (a.equals("--debug")) {
        debug = true;
      }
    }

    // Resolve the input: either a single .java file or a directory of them.
    File input = new File(args[0]);
    if (!input.exists()) {
      System.out.println("Error: path not found: " + args[0]);
      System.exit(1);
    }

    // Collect the .java files to compile.
    List<String> sourceFiles = new ArrayList<>();
    if (input.isDirectory()) {
      // Recursively gather every .java file under the directory.
      try (Stream<Path> paths = Files.walk(input.toPath())) {
        paths
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .forEach(p -> sourceFiles.add(p.toAbsolutePath().toString()));
      }
      if (sourceFiles.isEmpty()) {
        System.out.println("Error: no .java files found under " + args[0]);
        System.exit(1);
      }
    } else {
      // A single file, exactly as before.
      sourceFiles.add(input.getAbsolutePath());
    }

    // 1. Compile all collected .java files to .class in a temp directory.
    //    Compiling them together puts every class in one scope, so a field or
    //    method defined in one file and used in another is seen correctly.
    Path classDir = Files.createTempDirectory("deadstore-classes");
    Runtime.getRuntime().addShutdownHook(new Thread(() -> deleteRecursively(classDir)));

    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) {
      System.out.println("Error: no system Java compiler (run with a JDK, not a JRE).");
      System.exit(1);
    }

    // Build the argument array: -g -d <classDir> <file1> <file2> ...
    List<String> compilerArgs = new ArrayList<>();
    compilerArgs.add("-g");
    compilerArgs.add("-d");
    compilerArgs.add(classDir.toString());
    compilerArgs.addAll(sourceFiles);

    int rc = compiler.run(null, null, null, compilerArgs.toArray(new String[0]));
    if (rc != 0) {
      System.out.println("Error: failed to compile the input source(s)");
      System.exit(1);
    }

    // 2. Scope only the classes we just compiled)
    AnalysisScope scope = AnalysisScopeReader.instance.makeJavaBinaryAnalysisScope(classDir.toString(), null);

    // 3. Class hierarchy
    ClassHierarchy cha = ClassHierarchyFactory.make(scope);

    IAnalysisCacheView cache = new AnalysisCacheImpl();

    // 4. Find parameters that are never read
    Map<MethodReference, Set<Integer>> unusedParams = findUnusedParameters(cha, cache);
    if (debug) {
      dumpUnusedParameters(unusedParams, cha, cache);
    }

    // 5. Build the SSA IR for each application method and inspect it
    List<Finding> deadStores = new ArrayList<>();

    for (IClass klass : cha) {
      if (!klass.getClassLoader().getReference().equals(ClassLoaderReference.Application)) {
        continue; // skip the JDK
      }
      for (IMethod method : klass.getDeclaredMethods()) {
        if (method.isAbstract() || method.isNative()) {
          continue;
        }
        IR ir = cache.getIR(method);
        if (ir == null) {
          continue;
        }
        DefUse du = new DefUse(ir);

        if (debug) {
          dumpIR(ir, du);
        }
        findDeadStores(ir, du, deadStores);
      }
    }

    // Unused parameters are reported as dead stores too (Phase 5)
    reportUnusedParameters(unusedParams, cha, cache, deadStores);
    // Unused fields are reported as dead stores too (Phase 5)
    reportUnusedFields(cha, cache, deadStores);

    // 6. Report, sorted by category (Field, Parameter, Variable) then line
    if (deadStores.isEmpty()) {
      System.out.println("No dead stores detected.");
    } else {
      deadStores.sort(
          Comparator.comparingInt((Finding f) -> f.category.order).thenComparingInt(f -> f.line));
      System.out.println("Dead store detected:");
      for (Finding f : deadStores) {
        System.out.println("  " + f.category.label + ": " + f.name + ", Line: " + f.line);
      }
    }
  }

  /** A single dead-store finding: what kind of thing, its name, and its line */
  private enum Category {
    FIELD("Field", 0),
    PARAMETER("Parameter", 1),
    VARIABLE("Variable", 2);

    final String label;
    final int order;

    Category(String label, int order) {
      this.label = label;
      this.order = order;
    }
  }

  private static final class Finding {
    final Category category;
    final String name;
    final int line;

    Finding(Category category, String name, int line) {
      this.category = category;
      this.name = name;
      this.line = line;
    }
  }

  /** Delete the temp class directory, depth first. Best effort: a leftover file must not fail a run */
  private static void deleteRecursively(Path dir) {
    try (Stream<Path> paths = Files.walk(dir)) {
      paths
          .sorted(Comparator.reverseOrder())
          .forEach(
              p -> {
                try {
                  Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
              });
    } catch (IOException ignored) {
    }
  }

  /** Find, for each application method, which parameter positions are never ready */
  private static Map<MethodReference, Set<Integer>> findUnusedParameters(
      ClassHierarchy cha, IAnalysisCacheView cache) {

    Map<MethodReference, Set<Integer>> result = new HashMap<>();

    for (IClass klass : cha) {
      if (!klass.getClassLoader().getReference().equals(ClassLoaderReference.Application)) {
        continue; // skip the JDK
      }
      for (IMethod method : klass.getDeclaredMethods()) {
        if (method.isAbstract() || method.isNative()) {
          continue;
        }
        IR ir = cache.getIR(method);
        if (ir == null) {
          continue;
        }
        DefUse du = new DefUse(ir);

        Set<Integer> unused = new HashSet<>();

        // Position 0 of an instance method is `this`. A receiver is not a source-level parameter, so skip it
        int start = method.isStatic() ? 0 : 1;

        for (int pos = start; pos < ir.getNumberOfParameters(); pos++) {
          int v = ir.getParameter(pos);
          if (du.isUnused(v)) {
            unused.add(pos);
          }
        }

        if (!unused.isEmpty()) {
          result.put(method.getReference(), unused);
        }
      }
    }
    return result;
  }

  /** Walk every value number in the method, not every instruction */
  private static void findDeadStores(IR ir, DefUse du, List<Finding> out) {
    SymbolTable symbolTable = ir.getSymbolTable();

    for (int v = 1; v <= symbolTable.getMaxValueNumber(); v++) {
      // Parameters are defined by the caller, not by a store in this body
      if (v <= ir.getNumberOfParameters()) {
        continue;
      }
      if (!du.isUnused(v)) {
        continue; // read somewhere -> alive
      }

      // A value only counts as a source-level dead store if it has a source name
      // Compiler temporaries have none, and reporting them would be noise
      int nameIndex = firstIndexWithName(ir, v);
      if (nameIndex <= 0) {
        continue; // no source name, or no store before it
      }
      String name = ir.getLocalNames(nameIndex, v)[0];

      // The LocalVariableTable scope for a local opens immediately after its store, so the store is
      // the preceding instruction
      int line = lineNumberFor(ir, nameIndex - 1);
      out.add(new Finding(Category.VARIABLE, name, line));
    }
  }

  /** First instruction index at which this value number carries a source variable name */
  private static int firstIndexWithName(IR ir, int valueNumber) {
    SSAInstruction[] instructions = ir.getInstructions();
    for (int i = 0; i < instructions.length; i++) {
      String[] names = ir.getLocalNames(i, valueNumber);
      if (names != null && names.length > 0 && names[0] != null) {
        return i;
      }
    }
    return -1;
  }

  /** Source line of a method's declaration, used for its parameters */
  private static int parameterLine(IMethod method) {
    if (!(method instanceof IBytecodeMethod)) {
      return -1;
    }
    try {
      return ((IBytecodeMethod<?>) method).getLineNumber(0);
    } catch (Exception e) {
      return -1;
    }
  }

  /** Map an instruction index back to a source line number via the bytecode "LineNumberTable" */
  private static int lineNumberFor(IR ir, int instructionIndex) {
    IMethod method = ir.getMethod();
    if (!(method instanceof IBytecodeMethod)) {
      return -1;
    }
    try {
      IBytecodeMethod<?> bcMethod = (IBytecodeMethod<?>) method;
      int bcIndex = bcMethod.getBytecodeIndex(instructionIndex);
      return bcMethod.getLineNumber(bcIndex);
    } catch (Exception e) {
      return -1;
    }
  }

  /** Report each unused parameter as a dead store */
  private static void reportUnusedParameters(
      Map<MethodReference, Set<Integer>> unusedParams,
      ClassHierarchy cha,
      IAnalysisCacheView cache,
      List<Finding> out) {

    // Walk the methods directly and match by reference. Resolving a bare
    // MethodReference back to an IMethod is unreliable for private/instance
    // methods, so instead reuse the same iteration that built the map
    for (IClass klass : cha) {
      if (!klass.getClassLoader().getReference().equals(ClassLoaderReference.Application)) {
        continue;
      }
      for (IMethod method : klass.getDeclaredMethods()) {
        Set<Integer> positions = unusedParams.get(method.getReference());
        if (positions == null) {
          continue;
        }
        IR ir = cache.getIR(method);
        if (ir == null) {
          continue;
        }

        boolean isMain =
            method.getName().toString().equals("main")
                && method.getDescriptor().toString().equals("([Ljava/lang/String;)V");

        for (int pos : positions) {
          if (isMain) {
            continue; // args of the entry point is contractually required
          }
          String[] names = ir.getLocalNames(0, ir.getParameter(pos));
          if (names == null || names.length == 0 || names[0] == null) {
            continue; // synthetic parameter with no source name
          }
          if (names[0].startsWith("this$")) {
            continue; // synthetic outer-class reference on an inner class constructor
          }
          int line = parameterLine(method);
          out.add(new Finding(Category.PARAMETER, names[0], line));
        }
      }
    }
  }

  /** Report each field that is written but never read */
  private static void reportUnusedFields(
      ClassHierarchy cha, IAnalysisCacheView cache, List<Finding> out) {

    // field -> (line of its first write). A field is a candidate until proven read
    Map<FieldReference, Integer> written = new HashMap<>();
    Set<FieldReference> read = new HashSet<>();

    for (IClass klass : cha) {
      if (!klass.getClassLoader().getReference().equals(ClassLoaderReference.Application)) {
        continue;
      }
      for (IMethod method : klass.getDeclaredMethods()) {
        if (method.isAbstract() || method.isNative()) {
          continue;
        }
        IR ir = cache.getIR(method);
        if (ir == null) {
          continue;
        }

        SSAInstruction[] instructions = ir.getInstructions();
        for (int i = 0; i < instructions.length; i++) {
          SSAInstruction inst = instructions[i];
          if (inst instanceof SSAPutInstruction) {
            FieldReference f = ((SSAPutInstruction) inst).getDeclaredField();
            if (isApplicationField(f, cha)) {
              written.putIfAbsent(f, lineNumberFor(ir, i));
            }
          } else if (inst instanceof SSAGetInstruction) {
            FieldReference f = ((SSAGetInstruction) inst).getDeclaredField();
            if (isApplicationField(f, cha)) {
              read.add(f);
            }
          }
        }
      }
    }

    written.forEach(
        (field, line) -> {
          if (!read.contains(field)) {
            out.add(new Finding(Category.FIELD, field.getName().toString(), line));
          }
        });
  }

  /** True if the field is declared in application code, not the JDK */
  private static boolean isApplicationField(FieldReference field, ClassHierarchy cha) {
    IClass declaring = cha.lookupClass(field.getDeclaringClass());
    return declaring != null
        && declaring.getClassLoader().getReference().equals(ClassLoaderReference.Application);
  }

  /** Diagnostic: print which parameter of which method is never read */
  private static void dumpUnusedParameters(
      Map<MethodReference, Set<Integer>> unusedParams,
      ClassHierarchy cha,
      IAnalysisCacheView cache) {

    System.out.println("=== UNUSED PARAMETERS ===");
    if (unusedParams.isEmpty()) {
      System.out.println("(none)");
    }
    unusedParams.forEach(
        (methodRef, positions) -> {
          IMethod m = cha.resolveMethod(methodRef);
          IR ir = (m == null) ? null : cache.getIR(m);
          for (int pos : positions) {
            String name = "?";
            if (ir != null) {
              String[] names = ir.getLocalNames(0, ir.getParameter(pos));
              if (names != null && names.length > 0 && names[0] != null) {
                name = names[0];
              }
            }
            System.out.println(
                "  " + methodRef.getName() + " position " + pos + " = " + name + " (unused)");
          }
        });
    System.out.println("=== END ===\n");
  }

  /** Diagnostic: print the IR and what DefUse knows about every value number */
  private static void dumpIR(IR ir, DefUse du) {
    SymbolTable symbolTable = ir.getSymbolTable();
    System.out.println("=== METHOD: " + ir.getMethod().getName() + " ===");
    System.out.println(ir);
    System.out.println("--- parameters: " + ir.getNumberOfParameters());
    System.out.println("--- maxValueNumber: " + symbolTable.getMaxValueNumber());

    for (int v = 1; v <= symbolTable.getMaxValueNumber(); v++) {
      StringBuilder sb = new StringBuilder("v" + v);
      sb.append(" unused=").append(du.isUnused(v));
      sb.append(" constant=").append(symbolTable.isConstant(v));
      if (symbolTable.isConstant(v)) {
        sb.append("(").append(symbolTable.getConstantValue(v)).append(")");
      }
      sb.append(" def=").append(du.getDef(v));
      for (int i = 0; i < ir.getInstructions().length; i++) {
        String[] names = ir.getLocalNames(i, v);
        if (names != null && names.length > 0 && names[0] != null) {
          sb.append(" | name@").append(i).append("=").append(names[0]);
          sb.append("(line ").append(lineNumberFor(ir, i)).append(")");
        }
      }
      System.out.println(sb);
    }
    System.out.println("=== END METHOD ===\n");
  }
}
