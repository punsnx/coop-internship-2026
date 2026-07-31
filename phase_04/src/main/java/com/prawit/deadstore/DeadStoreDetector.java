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
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SymbolTable;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.MethodReference;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
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

    File source = new File(args[0]);
    if (!source.exists()) {
      System.out.println("Error: File not found: " + args[0]);
      System.exit(1);
    }

    // 1. Compile the .java file to .class in a temp directory
    //    "-g is required!" It keeps the LocalVariableTable (variable names) and
    //    LineNumberTable (line numbers) in the bytecode. Without it, WALA can
    //    still build the IR but every name is lost and every line is -1
    Path classDir = Files.createTempDirectory("deadstore-classes");
    // Every error path below calls System.exit, so clean up from a shutdown hook
    // rather than at the end of main, which those paths never reach
    Runtime.getRuntime().addShutdownHook(new Thread(() -> deleteRecursively(classDir)));

    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) {
      System.out.println("Error: no system Java compiler (run with a JDK, not a JRE).");
      System.exit(1);
    }

    int rc =
        compiler.run(null, null, null, "-g", "-d", classDir.toString(), source.getAbsolutePath());

    if (rc != 0) {
      System.out.println("Error: failed to compile " + source);
      System.exit(1);
    }

    // 2. Scope: only the classes we just compiled
    AnalysisScope scope =
        AnalysisScopeReader.instance.makeJavaBinaryAnalysisScope(classDir.toString(), null);

    // 3. Class hierarchy
    ClassHierarchy cha = ClassHierarchyFactory.make(scope);

    IAnalysisCacheView cache = new AnalysisCacheImpl();

    // 4. Find parameters that are never read, then repeat until the set stops growing
    //    A value passed into an unused parameter accomplishes nothing, so the store
    //    feeding it is dead too, which can in turn make another parameter unused
    Map<MethodReference, Set<Integer>> unusedParams = findUnusedParameters(cha, cache);
    if (debug) {
      dumpUnusedParameters(unusedParams, cha, cache);
    }

    // 5. Build the SSA IR for each application method and inspect it
    List<String> deadStores = new ArrayList<>();

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
        findDeadStores(ir, du, unusedParams, cha, deadStores);
      }
    }

    // 6. Report
    if (deadStores.isEmpty()) {
      System.out.println("No dead stores detected.");
    } else {
      System.out.println("Dead store detected:");
      deadStores.forEach(System.out::println);
    }
  }

  /**
   * Delete the temp class directory, depth first. Best effort: a leftover file must not fail a run
   */
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

  /**
   * Find, for each application method, which parameter positions are never read
   *
   * <p>Repeats until the set stops growing. One round is not enough: discounting a use can make a
   * caller's parameter unused, which can make its caller's parameter unused, and so on
   *
   * <p>The set starts empty and only grows, so this terminates. Starting empty also means a
   * recursive method whose parameter is only passed to itself stays "used", which is the safe
   * answer
   */
  private static Map<MethodReference, Set<Integer>> findUnusedParameters(
      ClassHierarchy cha, IAnalysisCacheView cache) {

    Map<MethodReference, Set<Integer>> result = new HashMap<>();

    boolean changed = true;
    while (changed) {
      changed = false;

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

          // Position 0 of an instance method is `this`. A receiver cannot be removed
          // from a call, so discounting it would be meaningless
          int start = method.isStatic() ? 0 : 1;

          for (int pos = start; pos < ir.getNumberOfParameters(); pos++) {
            Set<Integer> known = result.get(method.getReference());
            if (known != null && known.contains(pos)) {
              continue; // already found in an earlier round
            }
            int v = ir.getParameter(pos);
            if (isEffectivelyUnused(v, du, result, cha)) {
              result.computeIfAbsent(method.getReference(), k -> new HashSet<>()).add(pos);
              changed = true;
            }
          }
        }
      }
    }
    return result;
  }

  /**
   * True if this value never affects what the program does
   *
   * <p>Stronger than DefUse.isUnused, which only asks whether a use exists. A value read only to be
   * passed into a parameter nobody reads has a use, but that use accomplishes nothing
   */
  private static boolean isEffectivelyUnused(
      int v, DefUse du, Map<MethodReference, Set<Integer>> unusedParams, ClassHierarchy cha) {

    if (du.isUnused(v)) {
      return true; // no uses at all
    }

    Iterator<SSAInstruction> uses = du.getUses(v);
    while (uses.hasNext()) {
      if (!isDiscountableUse(v, uses.next(), unusedParams, cha)) {
        return false; // a use that matters
      }
    }
    return true; // every use was discountable
  }

  /** True if this use is a call handing the value to a parameter nobody reads */
  private static boolean isDiscountableUse(
      int v,
      SSAInstruction use,
      Map<MethodReference, Set<Integer>> unusedParams,
      ClassHierarchy cha) {

    if (!(use instanceof SSAAbstractInvokeInstruction)) {
      return false; // any other use is a real one
    }
    SSAAbstractInvokeInstruction call = (SSAAbstractInvokeInstruction) use;

    IMethod callee = cha.resolveMethod(call.getDeclaredTarget());
    if (callee == null) {
      return false; // callee outside the scope, assume the argument matters
    }
    Set<Integer> unused = unusedParams.get(callee.getReference());
    if (unused == null) {
      return false; // no unused parameters on the callee
    }

    // The value may be passed at more than one position, as in g(a, a).
    // Every position it occupies must be an unused parameter
    boolean found = false;
    for (int pos = 0; pos < call.getNumberOfPositionalParameters(); pos++) {
      if (call.getUse(pos) == v) {
        found = true;
        if (!unused.contains(pos)) {
          return false;
        }
      }
    }
    return found;
  }

  /** Walk every value number in the method, not every instruction */
  private static void findDeadStores(
      IR ir,
      DefUse du,
      Map<MethodReference, Set<Integer>> unusedParams,
      ClassHierarchy cha,
      List<String> out) {

    SymbolTable symbolTable = ir.getSymbolTable();

    for (int v = 1; v <= symbolTable.getMaxValueNumber(); v++) {
      // Parameters are defined by the caller, not by a store in this body
      if (v <= ir.getNumberOfParameters()) {
        continue;
      }
      if (!isEffectivelyUnused(v, du, unusedParams, cha)) {
        continue; // read somewhere that matters -> alive
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
      out.add("  Variable: " + name + ", Line: " + line);
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
