package com.ibm.wala.examples.drivers;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.ibm.wala.classLoader.IBytecodeMethod;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.core.util.config.AnalysisScopeReader; // Modern WALA package
import com.ibm.wala.ipa.callgraph.AnalysisCacheImpl;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.cfg.ExceptionPrunedCFG;
import com.ibm.wala.ipa.cfg.PrunedCFG;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.shrike.shrikeBT.IInstruction; // WALA Shrike instruction set
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSACFG;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.graph.Graph;
import com.ibm.wala.util.graph.impl.SlowSparseNumberedGraph;

public class WalaCFGGenerator {

    private static final List<String> sourceCodeLines = new ArrayList<>();

    // Block for translated data SSA IR
    public static class BytecodeBlock {
        private final int id;
        private final List<String> instructions = new ArrayList<>();
        private final Set<Integer> lines = new TreeSet<>();
        private final boolean isEntry;
        private final boolean isExit;

        public BytecodeBlock(int id, boolean isEntry, boolean isExit) {
            this.id = id;
            this.isEntry = isEntry;
            this.isExit = isExit;
        }

        public void addInstruction(String instText) {
            this.instructions.add(instText);
        }

        public void addLine(int line) {
            if (line > 0) {
                this.lines.add(line);
            }
        }

        public int getId() { return id; }
        public List<String> getInstructions() { return instructions; }
        public Set<Integer> getLines() { return lines; }
        public boolean isEntry() { return isEntry; }
        public boolean isExit() { return isExit; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof BytecodeBlock)) return false;
            BytecodeBlock that = (BytecodeBlock) o;
            return this.id == that.id;
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(id);
        }
    }

    // Block for translated data from bytecode
    public static class JavaCodeBlock {
        private final int id;
        private final Set<Integer> lines = new TreeSet<>();
        private final boolean isEntry;
        private final boolean isExit;

        public JavaCodeBlock(int id, boolean isEntry, boolean isExit) {
            this.id = id;
            this.isEntry = isEntry;
            this.isExit = isExit;
        }

        public void addLine(int line) {
            if (line > 0) {
                lines.add(line);
            }
        }

        public int getId() { return id; }
        public Set<Integer> getLines() { return lines; }
        public boolean isEntry() { return isEntry; }
        public boolean isExit() { return isExit; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof JavaCodeBlock)) return false;
            JavaCodeBlock that = (JavaCodeBlock) o;
            return this.id == that.id;
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(id);
        }
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java WalaCFGGenerator <classpath-jar-or-dir> <JVM-class-name> [optional-source-java-file]");
            System.out.println("Example: java WalaCFGGenerator target/classes Lcom/example/Target src/com/example/Target.java");
            return;
        }

        String classpath = args[0];
        String className = args[1];

        if (args.length >= 3) {
            loadSourceCode(args[2]);
        } else {
            System.out.println("Notice: No Java source file supplied. Exported nodes will show lines without source code text.");
        }

        try {
            // Set up Analysis Scope and Class Hierarchy
            System.out.println("Initializing Analysis Scope with classpath: " + classpath);
            AnalysisScope scope = AnalysisScopeReader.instance.makeJavaBinaryAnalysisScope(classpath, null);

            System.out.println("Building Class Hierarchy...");
            ClassHierarchy cha = ClassHierarchyFactory.make(scope);

            // Locate target class
            TypeReference typeRef = TypeReference.findOrCreate(ClassLoaderReference.Application, className);
            IClass targetClass = cha.lookupClass(typeRef);
            if (targetClass == null) {
                System.err.println("Error: Target class not found inside ClassHierarchy: " + className);
                return;
            }

            System.out.println("Successfully resolved class: " + targetClass.getName());
            AnalysisCacheImpl cache = new AnalysisCacheImpl();

            // Loop over every declared method in the class
            for (IMethod targetMethod : targetClass.getDeclaredMethods()) {
                if (!(targetMethod instanceof IBytecodeMethod)) {
                    continue;
                }

                IBytecodeMethod bytecodeMethod = (IBytecodeMethod) targetMethod;
                String rawMethodName = targetMethod.getName().toString();
                String cleanMethodName = rawMethodName.replace("<", "").replace(">", "");
                System.out.println(">>> Processing Method: " + targetMethod.getSignature());
                String directory = "export/";

                // 1. Generate SSA IR CFG
                IR ir = cache.getIR(targetMethod);
                if (ir == null) continue;
                SSACFG ssaCFG = ir.getControlFlowGraph();
                PrunedCFG<SSAInstruction, ISSABasicBlock> prunedCFG = ExceptionPrunedCFG.make(ssaCFG);
                exportSsaCFGToDot(prunedCFG, directory + "cfg_ssa_" + cleanMethodName + ".dot");

                // 2. Generate Bytecode CFG by mapping directly from the SSACFG
                Graph<BytecodeBlock> bytecodeCFG = mapSsaToBytecodeCFG(prunedCFG, bytecodeMethod);
                exportBytecodeCFGToDot(bytecodeCFG, directory + "cfg_bytecode_" + cleanMethodName + ".dot");

                // 3. Generate Source-level Java Code CFG by mapping from the newly created Bytecode CFG
                Graph<JavaCodeBlock> javaCodeCFG = mapBytecodeToJavaCodeCFG(bytecodeCFG);
                exportJavaCodeCFGToDot(javaCodeCFG, directory + "cfg_javacode_" + cleanMethodName + ".dot");

                convertDotToPdf(directory + "cfg_ssa_" + cleanMethodName + ".dot",directory + "cfg_ssa_" + cleanMethodName + ".pdf");
                convertDotToPdf(directory + "cfg_bytecode_" + cleanMethodName + ".dot",directory + "cfg_bytecode_" + cleanMethodName + ".pdf");
                convertDotToPdf(directory + "cfg_javacode_" + cleanMethodName + ".dot",directory + "cfg_javacode_" + cleanMethodName + ".pdf");
            }

            System.out.println("Whole class CFG mapping pipeline completed successfully.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Graph<BytecodeBlock> mapSsaToBytecodeCFG(PrunedCFG ssaCFG, IBytecodeMethod bytecodeMethod) {
        SlowSparseNumberedGraph<BytecodeBlock> bytecodeCFG = SlowSparseNumberedGraph.make();
        Map<ISSABasicBlock, BytecodeBlock> ssaToBytecodeMap = new HashMap<>();

        IInstruction[] rawInstructions = null;
        try {
            rawInstructions = (IInstruction[]) bytecodeMethod.getInstructions();
        } catch (Exception e) {
            System.err.println("Warning: Unable to fetch method bytecode instructions.");
        }

        // Map blocks
        for (Iterator<ISSABasicBlock> it = ssaCFG.iterator(); it.hasNext(); ) {
            ISSABasicBlock ssaBlock = it.next();
            BytecodeBlock bcBlock = new BytecodeBlock(ssaBlock.getNumber(), ssaBlock.isEntryBlock(), ssaBlock.isExitBlock());

            if (!ssaBlock.isEntryBlock() &&!ssaBlock.isExitBlock()) {
                int start = ssaBlock.getFirstInstructionIndex();
                int end = ssaBlock.getLastInstructionIndex();
                for (int i = start; i <= end; i++) {
                    if (i >= 0) {
                        try {
                            int bcIndex = bytecodeMethod.getBytecodeIndex(i); // irIndex -> bcIndex
                            int line = bytecodeMethod.getLineNumber(bcIndex); // bcIndex -> lineNumber
                            bcBlock.addLine(line);

                            if (rawInstructions!= null && i < rawInstructions.length) {
                                IInstruction inst = rawInstructions[i];
                                if (inst!= null) {
                                    bcBlock.addInstruction(bcIndex + ": " + inst.toString());
                                }
                            }
                        } catch (Exception e) {
                            // Skip compiler-inserted synthetic markers
                        }
                    }
                }
            }

            bytecodeCFG.addNode(bcBlock);
            ssaToBytecodeMap.put(ssaBlock, bcBlock);
        }

        // Map edges
        for (Iterator<ISSABasicBlock> it = ssaCFG.iterator(); it.hasNext(); ) {
            ISSABasicBlock srcSsa = it.next();
            BytecodeBlock srcBc = ssaToBytecodeMap.get(srcSsa);

            for (Iterator<ISSABasicBlock> succIt = ssaCFG.getSuccNodes(srcSsa); succIt.hasNext(); ) {
                ISSABasicBlock destSsa = succIt.next();
                BytecodeBlock destBc = ssaToBytecodeMap.get(destSsa);
                bytecodeCFG.addEdge(srcBc, destBc);
            }
        }

        return bytecodeCFG;
    }

    private static Graph<JavaCodeBlock> mapBytecodeToJavaCodeCFG(Graph<BytecodeBlock> bytecodeCFG) {
        SlowSparseNumberedGraph<JavaCodeBlock> javaCodeCFG = SlowSparseNumberedGraph.make();
        Map<BytecodeBlock, JavaCodeBlock> bcToJavaMap = new HashMap<>();

        // 1. Create Java Code Blocks, skipping completely empty ones unless they are Entry/Exit
        for (Iterator<BytecodeBlock> it = bytecodeCFG.iterator(); it.hasNext(); ) {
            BytecodeBlock bcBlock = it.next();

            // Skip synthetic bytecode blocks that don't map to real code
            if (!bcBlock.isEntry() && !bcBlock.isExit() && bcBlock.getLines().isEmpty()) {
                continue;
            }

            JavaCodeBlock javaBlock = new JavaCodeBlock(bcBlock.getId(), bcBlock.isEntry(), bcBlock.isExit());
            for (int line : bcBlock.getLines()) {
                javaBlock.addLine(line);
            }

            javaCodeCFG.addNode(javaBlock);
            bcToJavaMap.put(bcBlock, javaBlock);
        }

        // 2. Map control-flow edges, bypassing any bypassed empty blocks
        for (Iterator<BytecodeBlock> it = bytecodeCFG.iterator(); it.hasNext(); ) {
            BytecodeBlock srcBc = it.next();
            JavaCodeBlock srcJava = bcToJavaMap.get(srcBc);
            if (srcJava == null) continue; // Skipped block

            for (Iterator<BytecodeBlock> succIt = bytecodeCFG.getSuccNodes(srcBc); succIt.hasNext(); ) {
                BytecodeBlock destBc = succIt.next();
                JavaCodeBlock destJava = findActiveJavaBlock(destBc, bcToJavaMap, bytecodeCFG, new java.util.HashSet<>());

                if (destJava != null && !srcJava.equals(destJava)) {
                    javaCodeCFG.addEdge(srcJava, destJava);
                }
            }
        }

        return javaCodeCFG;
    }

    // Helper method to traverse through skipped blocks to find the next valid destination block
    private static JavaCodeBlock findActiveJavaBlock(
            BytecodeBlock current,
            Map<BytecodeBlock, JavaCodeBlock> map,
            Graph<BytecodeBlock> graph,
            Set<BytecodeBlock> visited
    ) {
        if (current == null || !visited.add(current)) return null;

        JavaCodeBlock mapped = map.get(current);
        if (mapped != null) {
            return mapped;
        }

        // If current was skipped, look at its successors
        for (Iterator<BytecodeBlock> it = graph.getSuccNodes(current); it.hasNext(); ) {
            JavaCodeBlock found = findActiveJavaBlock(it.next(), map, graph, visited);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    // --- UTILITIES ---

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

    private static String getSourceLineText(int lineNum) {
        if (lineNum <= 0 || lineNum > sourceCodeLines.size()) {
            return "";
        }
        return sourceCodeLines.get(lineNum - 1);
    }

    private static String escapeForDot(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "")
                .trim();
    }

    // --- DOT GRAPH EXPORTERS ---

    private static void exportSsaCFGToDot(PrunedCFG ssaCFG, String filepath) throws IOException {
        try (PrintWriter out = new PrintWriter(new FileWriter(filepath))) {
            out.println("digraph SSA_CFG {");
            out.println("  node [shape=box, fontname=\"Courier\"];");

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
                        SSAInstruction inst = (SSAInstruction) ssaCFG.getInstructions()[i];
                        if (inst!= null) {
                            String cleanInst = inst.toString().replace("\"", "\\\"");
                            label.append(i).append(": ").append(cleanInst).append("\\n");
                        }
                    }
                }
                out.printf("  %d [label=\"%s\"];\n", block.getNumber(), label.toString());
            }

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

    private static void exportBytecodeCFGToDot(Graph<BytecodeBlock> bytecodeCFG, String filepath) throws IOException {
        try (PrintWriter out = new PrintWriter(new FileWriter(filepath))) {
            out.println("digraph Reconstructed_Bytecode_CFG {");
            out.println("  node [shape=box, color=blue, fontname=\"Courier\"];");

            for (Iterator<BytecodeBlock> it = bytecodeCFG.iterator(); it.hasNext(); ) {
                BytecodeBlock block = it.next();
                StringBuilder label = new StringBuilder();
                label.append("Block ").append(block.getId()).append("\\n");

                if (block.isEntry()) {
                    label.append("");
                } else if (block.isExit()) {
                    label.append("");
                } else {
                    label.append("JVM Instructions:\\n");
                    label.append("-----------------------------\\n");
                    for (String inst : block.getInstructions()) {
                        label.append("  ").append(escapeForDot(inst)).append("\\n");
                    }

                    if (!block.getLines().isEmpty()) {
                        label.append("-----------------------------\\n");
                        label.append("Mapped Source Lines:\\n");
                        for (int line : block.getLines()) {
                            String code = getSourceLineText(line);
                            if (!code.isEmpty()) {
                                label.append("  L").append(line).append(": ").append(escapeForDot(code)).append("\\n");
                            } else {
                                label.append("  L").append(line).append("\\n");
                            }
                        }
                    }
                }
                out.printf("  %d [label=\"%s\"];\n", block.getId(), label.toString());
            }

            for (Iterator<BytecodeBlock> it = bytecodeCFG.iterator(); it.hasNext(); ) {
                BytecodeBlock block = it.next();
                for (Iterator<BytecodeBlock> succIt = bytecodeCFG.getSuccNodes(block); succIt.hasNext(); ) {
                    BytecodeBlock succ = succIt.next();
                    out.printf("  %d -> %d;\n", block.getId(), succ.getId());
                }
            }
            out.println("}");
        }
    }

    private static void exportJavaCodeCFGToDot(Graph<JavaCodeBlock> lineCFG, String filepath) throws IOException {
        try (PrintWriter out = new PrintWriter(new FileWriter(filepath))) {
            out.println("digraph Mapped_JavaCode_CFG {");
            out.println("  node [shape=box, style=filled, fillcolor=lightyellow, fontname=\"Courier\"];");

            for (Iterator<JavaCodeBlock> it = lineCFG.iterator(); it.hasNext(); ) {
                JavaCodeBlock node = it.next();
                String label;

                if (node.isEntry()) {
                    label = "ENTRY";
                } else if (node.isExit()) {
                    label = "EXIT";
                } else {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Block ").append(node.getId()).append("\\n-----------------------------\\n");
                    boolean first = true;

                    for (int line : node.getLines()) {
                        String code = getSourceLineText(line);
                        if (!first) {
                            sb.append("\\n");
                        }
                        if (!code.isEmpty()) {
                            sb.append("L").append(line).append(": ").append(escapeForDot(code));
                        } else {
                            sb.append("Line ").append(line);
                        }
                        first = false;
                    }

                    if (node.getLines().isEmpty()) {
                        sb.append("");
                    }

                    label = sb.toString();
                }
                out.printf("  %d [label=\"%s\"];\n", node.getId(), label);
            }

            for (Iterator<JavaCodeBlock> it = lineCFG.iterator(); it.hasNext(); ) {
                JavaCodeBlock node = it.next();
                for (Iterator<JavaCodeBlock> succIt = lineCFG.getSuccNodes(node); succIt.hasNext(); ) {
                    JavaCodeBlock succ = succIt.next();
                    out.printf("  %d -> %d;\n", node.getId(), succ.getId());
                }
            }
            out.println("}");
        }
    }

    public static boolean convertDotToPdf(String dotFilePath, String pdfFilePath) {
        try {
            String[] command = { "dot", "-Tpdf", dotFilePath, "-o", pdfFilePath };

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true); // Merges error stream with standard output

            Process process = pb.start();

            int exitCode = process.waitFor();

            if (exitCode == 0) {
                System.out.println("Success! PDF generated at: " + pdfFilePath);
                return true;
            } else {
                System.err.println("Graphviz failed with exit code: " + exitCode);
                return false;
            }

        } catch (IOException e) {
            System.err.println("Failed to run Graphviz. Is 'dot' installed and added to your system PATH?");
            e.printStackTrace();
            return false;
        } catch (InterruptedException e) {
            System.err.println("The compilation process was interrupted.");
            Thread.currentThread().interrupt(); // Restore interrupted status
            return false;
        }
    }
}