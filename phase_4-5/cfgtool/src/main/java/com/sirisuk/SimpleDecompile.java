package com.sirisuk;

import com.ibm.wala.classLoader.IBytecodeMethod;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSACFG;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class SimpleDecompile {

    // map source file from disk
    private static final Map<IClass, String[]> sourceCache = new HashMap<>();

    public static void printCFGWithSource(CallGraph cg, String sourceDir) {
        int i = 0;
        for (CGNode node : cg) {
            IR ir = node.getIR();
            IMethod method = node.getMethod();
            if (!SimpleVisualizer.isAppMethod(node) || ir == null) continue;
            if (!(method instanceof IBytecodeMethod)) continue; // no bytecode -> no debug info

            System.out.println("\n=== " + (++i) + method.getSignature() + " ===");

            SSACFG cfg = ir.getControlFlowGraph();
            for (ISSABasicBlock bb : cfg) {
                SimpleVisualizer.printBasicBlock(bb, cfg);
                printSourceLines(bb, (IBytecodeMethod<?>) method, sourceDir);
            }
        }
    }

    // traverse to this block's instructions in order abd resolving each to a source line
    // skipping consecutive repeats (a straight-line block usually stays on
    // one source line for several instructions in a row).
    private static void printSourceLines(ISSABasicBlock bb, IBytecodeMethod<?> method, String sourceDir) {
        String[] sourceLines = sourceCache.computeIfAbsent(
                method.getDeclaringClass(), klass -> loadSource(klass, sourceDir));

        System.out.println("SOURCE LINE:");
        System.out.println("    sourceLines.length: " + sourceLines.length);
        if (sourceLines.length == 0) {System.out.println("    src: unavailable");return;}

        System.out.println("    bb FirstInstructionIndex: " + bb.getFirstInstructionIndex());
        System.out.println("    bb LastInstructionIndex: " + bb.getLastInstructionIndex());
        int lastLine = -1;
        for (int i = bb.getFirstInstructionIndex(); i >= 0 && i <= bb.getLastInstructionIndex(); i++) {
            int line = bytecodeLine(method, i);
            if (line < 0 || line == lastLine) continue;
            lastLine = line;

            String text = (line - 1 < sourceLines.length) ? sourceLines[line - 1].strip() : "unavailable";
            System.out.println("    src (line " + line + ")  " + text);
        }
    }

    // the actual bytecode to source mapping. By resolve SSA instruction index to bytecode offset
    // using (getBytecodeIndex) then get source line number (getLineNumber)
    // it will lookup from the debug info javac wrote into the .class file.
    // returns -1 if either lookup has nothing for this instruction
    private static int bytecodeLine(IBytecodeMethod<?> method, int instructionIndex) {
        try {
            int bcIndex = method.getBytecodeIndex(instructionIndex);
            return bcIndex < 0 ? -1 : method.getLineNumber(bcIndex);
        } catch (Exception e) {
            return -1;
        }
    }

    // concat class name .java path under sourceDir and read lines once
    private static String[] loadSource(IClass klass, String sourceDir) {
        try {
            String slashName = klass.getName().toString().substring(1); // drop leading "L"
            Path path = Paths.get(sourceDir, slashName + ".java");
            if (!Files.exists(path)) return new String[0];
            String[] lines = Files.readAllLines(path).toArray(new String[0]);
            System.out.println("LOAD_SOURCE path : " + path);
//            Arrays.stream(lines).map(String::strip).forEach(System.out::println);
            int i = 0;
            for(String line : lines) {
                System.out.println("    " + (++i) + ". " + line);
            }
//            return Files.readAllLines(path).toArray(new String[0]);
            return lines;
        } catch (IOException e) {
            return new String[0];
        }
    }
}
