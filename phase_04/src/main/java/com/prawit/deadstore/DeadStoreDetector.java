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
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SymbolTable;
import com.ibm.wala.types.ClassLoaderReference;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) {
      System.out.println("Error: no system Java compiler (run with a JDK, not a JRE).");
      System.exit(1);
    }

    int rc = compiler.run(null, null, null, "-g", "-d", classDir.toString(), source.getAbsolutePath());

    if (rc != 0) {
      System.out.println("Error: failed to compile " + source);
      System.exit(1);
    }

    // 2. Scope: only the classes we just compiled
    AnalysisScope scope = AnalysisScopeReader.instance.makeJavaBinaryAnalysisScope(classDir.toString(), null);

    // 3. Class hierarchy
    ClassHierarchy cha = ClassHierarchyFactory.make(scope);

    // 4. Build the SSA IR for each application method and inspect it
    IAnalysisCacheView cache = new AnalysisCacheImpl();
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
        findDeadStores(ir, du, deadStores);
      }
    }

    // 5. Report
    if (deadStores.isEmpty()) {
      System.out.println("No dead stores detected.");
    } else {
      System.out.println("Dead store detected:");
      deadStores.forEach(System.out::println);
    }
  }

  /** Walk every value number in the method, not every instruction */
  private static void findDeadStores(IR ir, DefUse du, List<String> out) {
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

      // The LocalVariableTable scope for a local opens immediately after its store, so the store is the preceding instruction
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
