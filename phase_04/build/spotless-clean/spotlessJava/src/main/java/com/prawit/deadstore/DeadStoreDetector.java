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
import com.ibm.wala.types.ClassLoaderReference;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

/**
 * DeadStoreDetector - detects dead stores in a single Java source file using WALA.
 *
 * <p>A dead store is a value that is defined (assigned) but never used. This tool builds WALA's SSA
 * IR for each method and uses {@link DefUse#isUnused(int)} to find defined-but-unused values.
 *
 * <p>Usage: ./gradlew run --args="path/to/Input.java"
 */
public class DeadStoreDetector {

  public static void main(String[] args) throws Exception {
    if (args.length < 1) {
      System.out.println("Usage: DeadStoreDetector <path-to-java-file>");
      System.exit(1);
    }

    File source = new File(args[0]);
    if (!source.exists()) {
      System.out.println("Error: File not found: " + args[0]);
      System.exit(1);
    }

    // 1. Compile the .java file to .class in a temp dir.
    //    -g is REQUIRED: it keeps the LocalVariableTable (variable names)
    //    and LineNumberTable (line numbers) in the bytecode.
    Path classDir = Files.createTempDirectory("deadstore-classes");
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

    // 2. Build the analysis scope over the compiled classes.
    AnalysisScope scope =
        AnalysisScopeReader.instance.makeJavaBinaryAnalysisScope(classDir.toString(), null);

    // 3. Build the class hierarchy.
    ClassHierarchy cha = ClassHierarchyFactory.make(scope);

    // 4. Build IR for each application method and look for unused defs.
    IAnalysisCacheView cache = new AnalysisCacheImpl();
    List<String> deadStores = new ArrayList<>();

    for (IClass klass : cha) {
      // Only our code, not the JDK.
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
        DefUse du = new DefUse(ir);
        findDeadStores(ir, du, deadStores);
      }
    }

    // 5. Report.
    if (deadStores.isEmpty()) {
      System.out.println("No dead stores detected.");
    } else {
      System.out.println("Dead store detected:");
      deadStores.forEach(System.out::println);
    }
  }

  /** Scan every instruction that defines a value; report the ones DefUse says are unused. */
  private static void findDeadStores(IR ir, DefUse du, List<String> out) throws IOException {
    SSAInstruction[] instructions = ir.getInstructions();

    for (int i = 0; i < instructions.length; i++) {
      SSAInstruction inst = instructions[i];
      if (inst == null || !inst.hasDef()) {
        continue;
      }

      int v = inst.getDef();

      // Skip method parameters: they are defined by the calling convention,
      // not by a store in this method body.
      if (v <= ir.getNumberOfParameters()) {
        continue;
      }

      if (!du.isUnused(v)) {
        continue; // the value is read somewhere -> alive
      }

      String name = localNameFor(ir, i, v);
      if (name == null) {
        continue; // a temporary with no source-level name -> not a source dead store
      }

      int line = lineNumberFor(ir, i);
      out.add("  Variable: " + name + ", Line: " + line);
    }
  }

  /** Map an SSA value number back to its source variable name, if it has one. */
  private static String localNameFor(IR ir, int instructionIndex, int valueNumber) {
    String[] names = ir.getLocalNames(instructionIndex, valueNumber);
    if (names == null || names.length == 0) {
      return null;
    }
    return names[0];
  }

  /** Map an instruction index back to its source line number. */
  private static int lineNumberFor(IR ir, int instructionIndex) throws IOException {
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
}
