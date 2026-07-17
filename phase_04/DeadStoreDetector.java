package com.ibm.wala.examples.drivers;

import java.io.IOException;
import com.ibm.wala.classLoader.IBytecodeMethod;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.AnalysisCacheImpl;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.ssa.DefUse;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAPhiInstruction;
import com.ibm.wala.ssa.SSAPiInstruction;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.core.util.config.AnalysisScopeReader;

public class DeadStoreDetector {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Error: Specify the target JAR/Class directory path.");
            System.exit(1);
        }

        String targetPath = args[0];
        try {
            analyzeTarget(targetPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void analyzeTarget(String path) throws IOException, ClassHierarchyException {
        AnalysisScope scope = AnalysisScopeReader.instance.makeJavaBinaryAnalysisScope(path, null);
        ClassHierarchy classHierarchy = ClassHierarchyFactory.make(scope);
        AnalysisCacheImpl analysisCache = new AnalysisCacheImpl();

        int classesInspected = 0;
        int violationsFound = 0;

        for (IClass clazz : classHierarchy) {
            // Only Application
            if (clazz.getClassLoader().getReference().equals(ClassLoaderReference.Application)) {
                classesInspected++;
                for (IMethod method : clazz.getDeclaredMethods()) {
                    if (method.isAbstract() || method.isNative()) {
                        continue;
                    }

                    IR ir = analysisCache.getIR(method, Everywhere.EVERYWHERE);
                    if (ir == null) {
                        continue;
                    }

                    // DefUse from WALA
                    DefUse defUse = new DefUse(ir);
                    SSAInstruction[] instructions = ir.getInstructions();

                    for (int i = 0; i < instructions.length; i++) {
                        SSAInstruction instruction = instructions[i];
                        if (instruction == null) {
                            continue;
                        }

                        if (instruction.hasDef()) {
                            if (instruction instanceof SSAPhiInstruction || instruction instanceof SSAPiInstruction) {
                                continue;
                            }

                            int defValueNumber = instruction.getDef();

                            // Look up in defUse map if it is unused
                            if (defUse.isUnused(defValueNumber)) {
                                violationsFound++;
                                printViolation(method, ir, i, defValueNumber, instruction);
                            }
                        }
                    }
                }
            }
        }

        System.out.println("\n====== Analysis Finished ======");
        System.out.println("Total classes analyzed   : " + classesInspected);
        System.out.println("Total dead stores located: " + violationsFound);
    }

    // Used to print information on dead store
    private static void printViolation(IMethod method, IR ir, int instrIndex, int vn, SSAInstruction inst) {
        int lineNumber = -1;
        if (method instanceof IBytecodeMethod) {
            try {
                int bcIndex = ((IBytecodeMethod) method).getBytecodeIndex(instrIndex);
                lineNumber = method.getLineNumber(bcIndex);
            } catch (Exception e) {
                lineNumber = -1;
            }
        }

        System.out.println("[ALERT] Unused Variable Definition (Dead Store) Found:");
        System.out.println("  Class       : " + method.getDeclaringClass().getName().toString());
        System.out.println("  Method      : " + method.getName().toString() + method.getDescriptor().toString());
        System.out.println("  Source Line : " + (lineNumber != -1 ? lineNumber : "Unknown"));
        System.out.println("  Instruction : " + inst.toString());
        System.out.println("-----------------------------------------------------------------");
    }
}