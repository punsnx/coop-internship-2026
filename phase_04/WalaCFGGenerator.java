package com.ibm.wala.examples.drivers;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.ibm.wala.cfg.ShrikeCFG;
import com.ibm.wala.classLoader.IBytecodeMethod;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.core.util.config.AnalysisScopeReader;
import com.ibm.wala.ipa.callgraph.AnalysisCacheImpl;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.shrike.shrikeBT.IInstruction;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSACFG;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.TypeReference;

public class WalaCFGGenerator {

    // Used to store source code
    private static final List<String> sourceCodeLines = new ArrayList<>();
    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("WalaCFGGenerator need classpath, classname and sourcecodefile");
            return;
        }

        String classpath = args[0]; // path/to/class
        String className = args[1]; // Lcom/example/AnalysisClass
        loadSourceCode(args[2]);    // path/to/java

        try {
            // Set up Analysis Scope and Class Hierarchy
            System.out.println("Initializing Analysis Scope with classpath: " + classpath);
            AnalysisScope scope = AnalysisScopeReader.instance.makeJavaBinaryAnalysisScope(classpath, null);

            System.out.println("Building Class Hierarchy...");
            ClassHierarchy cha = ClassHierarchyFactory.make(scope);

            // Locate target class and method
            TypeReference typeRef = TypeReference.findOrCreate(ClassLoaderReference.Application, className);
            IClass targetClass = cha.lookupClass(typeRef);
            if (targetClass == null) {
                System.err.println("Error: Target class not found inside ClassHierarchy: " + className);
                return;
            }

            // Run for each method in the class
            for (IMethod targetMethod : targetClass.getDeclaredMethods()) {

                // Skip non-bytecode method
                if (!(targetMethod instanceof IBytecodeMethod)) {
                    continue;
                }

                IBytecodeMethod bytecodeMethod = (IBytecodeMethod) targetMethod;
                String rawMethodName = targetMethod.getName().toString();

                // Clean methodName for using as fileName
                String cleanMethodName = rawMethodName.replace("<", "").replace(">", "");
                System.out.println(">>> Processing Method: " + targetMethod.getSignature());

                // Generate IR SSA CFG
                System.out.println("\n--- Generating IR (SSA) CFG ---");
                AnalysisCacheImpl cache = new AnalysisCacheImpl();
                IR ir = cache.getIR(targetMethod);
                SSACFG ssaCFG = ir.getControlFlowGraph();

                // Generate dot file for visualization
                String ssaFileName = "cfg_" + cleanMethodName + "_ssa.dot";
                exportSsaCFGToDot(ssaCFG, ssaFileName);
                System.out.println("Exported " + cleanMethodName + " SSA CFG to: " + ssaFileName);

                // Generate Bytecode CFG using Shrike
                System.out.println("\n--- Generating Bytecode (Shrike) CFG ---");
                ShrikeCFG shrikeCFG = ShrikeCFG.make(bytecodeMethod);

                // Generate dot file for visualization
                String shrikeFileName = "cfg_" + cleanMethodName + "_bytecode.dot";
                exportShrikeCFGToDot(shrikeCFG, bytecodeMethod, shrikeFileName);
                System.out.println("Exported " + cleanMethodName + " Bytecode CFG to: " + shrikeFileName);

                System.out.println("\nAnalysis pipeline executed successfully.");

            }
        } catch(Exception e){
            e.printStackTrace();
        }
    }

    // Read source code and load it in
    private static void loadSourceCode(String filepath) {
        File file = new File(filepath);
        if (!file.exists()) {
            System.out.println("Warning: Source code file does not exist: " + filepath);
            return;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine())!= null) {
                sourceCodeLines.add(line);
            }
            System.out.println("Loaded " + sourceCodeLines.size() + " lines from " + filepath);
        } catch (IOException e) {
            System.err.println("Warning: Could not read source code file: " + e.getMessage());
        }
    }

    // Get source code as text
    private static String getSourceLineText(int lineNum) {
        if (lineNum <= 0 || lineNum > sourceCodeLines.size()) {
            return "";
        }
        return sourceCodeLines.get(lineNum - 1);
    }

    // Replace for dot usage
    private static String escapeForDot(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "")
                .trim();
    }

    // Create dot file for IR SSA CFG
    private static void exportSsaCFGToDot(SSACFG ssaCFG, String filepath) throws IOException {
        try (PrintWriter out = new PrintWriter(new FileWriter(filepath))) {
            out.println("digraph SSA_CFG {");
            out.println("  node [shape=box, fontname=\"Courier\"];");

            // Loop for each basic block
            for (Iterator<ISSABasicBlock> it = ssaCFG.iterator(); it.hasNext(); ) {
                ISSABasicBlock block = it.next();
                StringBuilder label = new StringBuilder();
                label.append("Block ").append(block.getNumber()).append("\\n");
                if (block.isEntryBlock()) {
                    label.append("");
                } else if (block.isExitBlock()) {
                    label.append("");
                } else {
                    int start = block.getFirstInstructionIndex();
                    int end = block.getLastInstructionIndex();
                    label.append("IR Range: [").append(start).append(", ").append(end).append("]\\n");
                    for (int i = start; i <= end; i++) {
                        SSAInstruction inst = ssaCFG.getInstructions()[i];
                        if (inst!= null) {
                            String cleanInst = inst.toString().replace("\"", "\\\"");
                            label.append(i).append(": ").append(cleanInst).append("\\n");
                        }
                    }
                }
                out.printf("  %d [label=\"%s\"];\n", block.getNumber(), label.toString());
            }

            // Connect edges
            for (Iterator<ISSABasicBlock> it = ssaCFG.iterator(); it.hasNext(); ) {
                ISSABasicBlock block = it.next();
                for (Iterator<ISSABasicBlock> succIt = ssaCFG.getSuccNodes(block); succIt.hasNext(); ) {
                    ISSABasicBlock succ = succIt.next();
                    out.printf("  %d -> %d;\n", block.getNumber(), succ.getNumber());
                }
            }
            out.println("}");
        }
    }

    // Create dot file for Shrike Bytecode CFG
    private static void exportShrikeCFGToDot(ShrikeCFG shrikeCFG, IBytecodeMethod bytecodeMethod, String filepath) throws IOException {
        try (PrintWriter out = new PrintWriter(new FileWriter(filepath))) {
            out.println("digraph Bytecode_CFG {");
            out.println("  node [shape=box, color=blue, fontname=\"Courier\"];");

            // Get bytecode instructions
            IInstruction[] instructions = null;
            try {
                instructions = (IInstruction[]) bytecodeMethod.getInstructions();
            } catch (Exception e) {
                System.err.println("Warning: Unable to fetch method bytecode instructions: " + e.getMessage());
            }

            // Loop for each basic block
            for (Iterator<ShrikeCFG.BasicBlock> it = shrikeCFG.iterator(); it.hasNext(); ) {
                ShrikeCFG.BasicBlock block = it.next();
                StringBuilder label = new StringBuilder();
                label.append("Block ").append(block.getNumber()).append("\\n");

                // Get Instruction Range
                if (block.isEntryBlock()) {
                    label.append("");
                } else if (block.isExitBlock()) {
                    label.append("");
                } else {
                    label.append("Instr Offset Range: [")
                            .append(block.getFirstInstructionIndex())
                            .append(", ")
                            .append(block.getLastInstructionIndex())
                            .append("]\\n");

                    // Display bytecode instruction in each block
                    if (instructions!= null) {
                        label.append("-----------------------------\\n");
                        label.append("Bytecode Instructions:\\n");
                        int start = block.getFirstInstructionIndex();
                        int end = block.getLastInstructionIndex();
                        for (int i = start; i <= end; i++) {
                            if (i >= 0 && i < instructions.length) {
                                IInstruction inst = instructions[i];
                                if (inst!= null) {
                                    int bcIndex = -1;
                                    try {
                                        bcIndex = bytecodeMethod.getBytecodeIndex(i);
                                    } catch (Exception e) {}

                                    String prefix = (bcIndex!= -1)? bcIndex + ": " : "";
                                    label.append("  ").append(prefix).append(escapeForDot(inst.toString())).append("\\n");
                                }
                            }
                        }
                    }

                    int start = block.getFirstInstructionIndex();
                    int end = block.getLastInstructionIndex();
                    Set<Integer> lines = new TreeSet<>();
                    // Get line corresponding to the source code
                    for (int i = start; i <= end; i++) {
                        if (i >= 0) {
                            try {
                                int bcIndex = bytecodeMethod.getBytecodeIndex(i);
                                int line = bytecodeMethod.getLineNumber(bcIndex);
                                if (line > 0) {
                                    lines.add(line);
                                }
                            } catch (Exception e) {
                            }
                        }
                    }

                    if (!lines.isEmpty()) {
                        label.append("-----------------------------\\n");
                        label.append("Mapped Source Lines:\\n");
                        // Get saved source code by line
                        for (int line : lines) {
                            String code = getSourceLineText(line);
                            if (!code.isEmpty()) {
                                label.append("  L").append(line).append(": ").append(escapeForDot(code)).append("\\n");
                            } else {
                                label.append("  L").append(line).append("\\n");
                            }
                        }
                    }
                }
                out.printf("  %d [label=\"%s\"];\n", block.getNumber(), label.toString());
            }

            // Connect edges
            for (Iterator<ShrikeCFG.BasicBlock> it = shrikeCFG.iterator(); it.hasNext(); ) {
                ShrikeCFG.BasicBlock block = it.next();
                for (Iterator<ShrikeCFG.BasicBlock> succIt = shrikeCFG.getSuccNodes(block); succIt.hasNext(); ) {
                    ShrikeCFG.BasicBlock succ = succIt.next();
                    out.printf("  %d -> %d;\n", block.getNumber(), succ.getNumber());
                }
            }
            out.println("}");
        }
    }
}
