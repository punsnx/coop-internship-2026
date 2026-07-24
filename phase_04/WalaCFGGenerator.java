package com.ibm.wala.examples.drivers;

import com.ibm.wala.classLoader.IBytecodeMethod;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.AnalysisCacheImpl;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.core.util.config.AnalysisScopeReader;
import com.ibm.wala.ipa.cfg.PrunedCFG;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSACFG;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.ipa.cfg.ExceptionPrunedCFG;
import com.ibm.wala.shrike.shrikeBT.IInstruction;
import com.ibm.wala.util.graph.impl.SlowSparseNumberedGraph;

import java.io.*;
import java.util.*;

public class WalaCFGGenerator {

    private static final List<String> sourceCodeLines = new ArrayList<>();

    public static class BytecodeBlock {
        private final int id;
        private final List<String> instructions = new ArrayList<>();
        private final Set<Integer> lines = new TreeSet<>();
        private boolean isEntry = false;
        private boolean isExit = false;

        public BytecodeBlock(int id,boolean isEntry,boolean isExit) {this.id = id; this.isEntry = isEntry; this.isExit = isExit;}

        public void addInstruction(String inst) {
            this.instructions.add(inst);
        }

        public void addLine(int line) {
            this.lines.add(line);
        }

        public int getId() { return id; }
        public List<String> getInstructions() { return instructions; }
        public Set<Integer> getLines() { return lines; }
        public boolean isEntry() { return isEntry; }
        public void setEntry(boolean entry) { isEntry = entry; }
        public boolean isExit() { return isExit; }
        public void setExit(boolean exit) { isExit = exit; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof BytecodeBlock)) return false;
            BytecodeBlock that = (BytecodeBlock) o;
            return id == that.id;
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }

    public static class JavaCodeBlock {
        private final int id;
        private final Set<Integer> lines = new TreeSet<>();
        private boolean isEntry = false;
        private boolean isExit = false;

        public JavaCodeBlock(int id, Set<Integer> lines, boolean isEntry, boolean isExit) {
            this.id = id;
            if (lines!= null) {
                this.lines.addAll(lines);
            }
            this.isEntry = isEntry;
            this.isExit = isExit;
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
            return id == that.id;
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java WalaCFGGenerator2 <classpath> <main-class> [source-file-path]");
            System.exit(1);
        }

        String classpath = args[0];
        String className = args[1];
        String sourceFilePath = args[2];

        if (sourceFilePath!= null) {
            loadSourceCode(sourceFilePath);
        }

        try {
            AnalysisScope scope = AnalysisScopeReader.instance.makeJavaBinaryAnalysisScope(classpath, null);
            ClassHierarchy cha = ClassHierarchyFactory.make(scope);
            TypeReference typeRef = TypeReference.findOrCreate(ClassLoaderReference.Application, className);
            IClass klass = cha.lookupClass(typeRef);

            if (klass == null) {
                System.err.println("Class not found in hierarchy: " + className);
                System.exit(1);
            }

            File exportDir = new File("export");
            if (!exportDir.exists()) {
                exportDir.mkdirs();
            }

            for (IMethod method : klass.getDeclaredMethods()) {
                if (!(method instanceof IBytecodeMethod)) {
                    continue;
                }

                IBytecodeMethod bytecodeMethod = (IBytecodeMethod) method;
                String methodName = method.getName().toString().replace("<", "").replace(">", "");
                System.out.println("Processing Method: " + methodName);

                AnalysisCacheImpl cache = new AnalysisCacheImpl();
                IR ir = cache.getIR(method);
                if (ir == null) {
                    continue;
                }

                SSACFG ssaCFG = ir.getControlFlowGraph();
                PrunedCFG prunedSsaCFG = ExceptionPrunedCFG.make(ssaCFG);

                // Step 1: Export Pruned SSA CFG
                String ssaDotPath = "export/cfg_ssa_" + methodName + ".dot";
                exportSsaCFGToDot(prunedSsaCFG, ssaDotPath);
                convertDotToPdf(ssaDotPath, "export/cfg_ssa_" + methodName + ".pdf");

                // Step 2: Map SSA to Bytecode CFG
                SlowSparseNumberedGraph<BytecodeBlock> bytecodeCFG = mapSsaToBytecodeCFG(prunedSsaCFG, bytecodeMethod);
                String bytecodeDotPath = "export/cfg_bytecode_" + methodName + ".dot";
                exportBytecodeCFGToDot(bytecodeCFG, bytecodeDotPath);
                convertDotToPdf(bytecodeDotPath, "export/cfg_bytecode_" + methodName + ".pdf");

                // Step 3: Map Bytecode to Consolidating Java CFG
                SlowSparseNumberedGraph<JavaCodeBlock> javaCFG = mapBytecodeToJavaCodeCFG(bytecodeCFG);
                javaCFG = coalesceStraightLineBlocks(javaCFG);
                String javaDotPath = "export/cfg_javacode_" + methodName + ".dot";
                exportJavaCodeCFGToDot(javaCFG, javaDotPath);
                convertDotToPdf(javaDotPath, "export/cfg_javacode_" + methodName + ".pdf");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void loadSourceCode(String filepath) {
        try (BufferedReader reader = new BufferedReader(new FileReader(filepath))) {
            String line;
            while ((line = reader.readLine())!= null) {
                sourceCodeLines.add(line);
            }
        } catch (IOException e) {
            System.err.println("Warning: Unable to load source file: " + filepath);
        }
    }

    private static String getSourceLineText(int lineNum) {
        int index = lineNum - 1;
        if (index >= 0 && index < sourceCodeLines.size()) {
            return sourceCodeLines.get(index).trim();
        }
        return "Line " + lineNum;
    }

    private static SlowSparseNumberedGraph<BytecodeBlock> mapSsaToBytecodeCFG(
            PrunedCFG ssaCFG, IBytecodeMethod bytecodeMethod) {

        SlowSparseNumberedGraph<BytecodeBlock> bytecodeCFG = SlowSparseNumberedGraph.make();
        Map<ISSABasicBlock, BytecodeBlock> ssaToBytecodeMap = new HashMap<>();

        // Create Nodes
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

    private static SlowSparseNumberedGraph<JavaCodeBlock> mapBytecodeToJavaCodeCFG(
            SlowSparseNumberedGraph<BytecodeBlock> bytecodeCFG) {

        SlowSparseNumberedGraph<JavaCodeBlock> javaCFG = SlowSparseNumberedGraph.make();

        // Maps used to track collapsed nodes
        Map<Set<Integer>, JavaCodeBlock> normalBlocksMap = new HashMap<>();
        Map<BytecodeBlock, JavaCodeBlock> bytecodeToJavaMap = new HashMap<>();

        JavaCodeBlock entryBlock = null;
        JavaCodeBlock exitBlock = null;

        // Phase 1: Determine active blocks and partition them by equivalence classes
        for (BytecodeBlock b : bytecodeCFG) {
            if (b.isEntry()) {
                if (entryBlock == null) {
                    entryBlock = new JavaCodeBlock(b.getId(), new TreeSet<>(), true, false);
                    javaCFG.addNode(entryBlock);
                }
                bytecodeToJavaMap.put(b, entryBlock);
            } else if (b.isExit()) {
                if (exitBlock == null) {
                    exitBlock = new JavaCodeBlock(b.getId(), new TreeSet<>(), false, true);
                    javaCFG.addNode(exitBlock);
                }
                bytecodeToJavaMap.put(b, exitBlock);
            } else if (b.getLines()!= null &&!b.getLines().isEmpty()) {
                Set<Integer> lines = b.getLines();
                Set<Integer> filteredLines = new TreeSet<>();
                for (int lineNum : lines) {
                    String lineText = getSourceLineText(lineNum);
                    if (lineText != null && lineText.trim().equals("}")) {
                        continue; // Strip this line from the block mapping
                    }
                    filteredLines.add(lineNum);
                }

                // Skip the block completely if it contains only closing braces
                if (filteredLines.isEmpty()) {
                    continue;
                }
                JavaCodeBlock targetBlock = normalBlocksMap.get(filteredLines);
                if (targetBlock == null) {
                    // Use the ID of the first encountered block in this equivalence class
                    targetBlock = new JavaCodeBlock(b.getId(), filteredLines, false, false);
                    javaCFG.addNode(targetBlock);
                    normalBlocksMap.put(filteredLines, targetBlock);
                }
                bytecodeToJavaMap.put(b, targetBlock);
            }
            // Bytecode blocks with no mapped line numbers are excluded from Phase 1.
        }

        // Phase 2: Establish topological connections using transitive routing
        for (BytecodeBlock b : bytecodeCFG) {
            JavaCodeBlock srcJava = bytecodeToJavaMap.get(b);
            if (srcJava!= null) {
                for (Iterator<BytecodeBlock> it = bytecodeCFG.getSuccNodes(b); it.hasNext(); ) {
                    BytecodeBlock succ = it.next();
                    Set<BytecodeBlock> visited = new HashSet<>();
                    resolveAndAddEdges(bytecodeCFG, succ, srcJava, javaCFG, bytecodeToJavaMap, visited);
                }
            }
        }

        return javaCFG;
    }

    private static SlowSparseNumberedGraph<JavaCodeBlock> coalesceStraightLineBlocks(
            SlowSparseNumberedGraph<JavaCodeBlock> inputGraph) {

        SlowSparseNumberedGraph<JavaCodeBlock> graph = SlowSparseNumberedGraph.make();
        Map<JavaCodeBlock, JavaCodeBlock> oldToNew = new HashMap<>();

        // 1. Clone the graph to prevent concurrent modification of the parameter graph
        for (JavaCodeBlock b : inputGraph) {
            JavaCodeBlock copy = new JavaCodeBlock(b.getId(), b.getLines(), b.isEntry(), b.isExit());
            graph.addNode(copy);
            oldToNew.put(b, copy);
        }
        for (JavaCodeBlock b : inputGraph) {
            JavaCodeBlock srcNew = oldToNew.get(b);
            for (Iterator<JavaCodeBlock> it = inputGraph.getSuccNodes(b); it.hasNext(); ) {
                JavaCodeBlock dstNew = oldToNew.get(it.next());
                graph.addEdge(srcNew, dstNew);
            }
        }

        // 2. Perform iterative edge-contraction contracts
        boolean changed = true;
        while (changed) {
            changed = false;
            JavaCodeBlock uToMerge = null;
            JavaCodeBlock vToMerge = null;

            for (JavaCodeBlock u : graph) {
                if (u.isEntry() || u.isExit()) continue;

                // Condition 1: out-degree of u must be exactly 1
                int succCount = 0;
                JavaCodeBlock singleSucc = null;
                for (Iterator<JavaCodeBlock> it = graph.getSuccNodes(u); it.hasNext(); ) {
                    singleSucc = it.next();
                    succCount++;
                }

                if (succCount == 1 && singleSucc!= null &&!singleSucc.isEntry() &&!singleSucc.isExit()) {
                    // Condition 2: in-degree of the successor must be exactly 1
                    int predCount = 0;
                    JavaCodeBlock singlePred = null;
                    for (Iterator<JavaCodeBlock> it = graph.getPredNodes(singleSucc); it.hasNext(); ) {
                        singlePred = it.next();
                        predCount++;
                    }

                    if (predCount == 1 && singlePred.equals(u)) {
                        uToMerge = u;
                        vToMerge = singleSucc;
                        changed = true;
                        break;
                    }
                }
            }

            if (changed && uToMerge!= null && vToMerge!= null) {
                // Combine line numbers of the two statements
                uToMerge.getLines().addAll(vToMerge.getLines());

                // Fetch downstream successors of the destination block
                List<JavaCodeBlock> successors = new ArrayList<>();
                for (Iterator<JavaCodeBlock> it = graph.getSuccNodes(vToMerge); it.hasNext(); ) {
                    successors.add(it.next());
                }

                // Remove the internal connection edge
                graph.removeEdge(uToMerge, vToMerge);

                // Redirect remaining transitions to the consolidated node
                for (JavaCodeBlock succ : successors) {
                    graph.removeEdge(vToMerge, succ);
                    if (!uToMerge.equals(succ) &&!graph.hasEdge(uToMerge, succ)) {
                        graph.addEdge(uToMerge, succ);
                    }
                }

                // Remove the redundant, now empty, block
                graph.removeNode(vToMerge);
            }
        }

        return graph;
    }

    private static void resolveAndAddEdges(
            SlowSparseNumberedGraph<BytecodeBlock> bytecodeCFG,
            BytecodeBlock currentBytecode,
            JavaCodeBlock srcJava,
            SlowSparseNumberedGraph<JavaCodeBlock> javaCFG,
            Map<BytecodeBlock, JavaCodeBlock> bytecodeToJavaMap,
            Set<BytecodeBlock> visited) {

        if (visited.contains(currentBytecode)) {
            return;
        }
        visited.add(currentBytecode);

        JavaCodeBlock targetJava = bytecodeToJavaMap.get(currentBytecode);
        if (targetJava!= null) {
            // Found a valid destination block belonging to a different equivalence class
            if (!srcJava.equals(targetJava)) {
                if (!javaCFG.hasEdge(srcJava, targetJava)) {
                    javaCFG.addEdge(srcJava, targetJava);
                }
            }
        } else {
            // Recurse downstream through skipped or empty intermediate blocks
            for (Iterator<BytecodeBlock> it = bytecodeCFG.getSuccNodes(currentBytecode); it.hasNext(); ) {
                BytecodeBlock succ = it.next();
                resolveAndAddEdges(bytecodeCFG, succ, srcJava, javaCFG, bytecodeToJavaMap, visited);
            }
        }
    }

    private static String escapeForDot(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\l")
                .replace("\r", "");
    }

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

    private static void exportBytecodeCFGToDot(SlowSparseNumberedGraph<BytecodeBlock> bytecodeCFG, String filepath) throws IOException {
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

    private static void exportJavaCodeCFGToDot(SlowSparseNumberedGraph<JavaCodeBlock> lineCFG, String filepath) throws IOException {
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

    private static void convertDotToPdf(String dotPath, String pdfPath) {
        try {
            ProcessBuilder pb = new ProcessBuilder("dot", "-Tpdf", dotPath, "-o", pdfPath);
            Process p = pb.start();
            int exitCode = p.waitFor();
            if (exitCode!= 0) {
                System.err.println("Graphviz exited with error code " + exitCode + " for file: " + dotPath);
            }
        } catch (Exception e) {
            System.err.println("Graphviz conversion failed. Ensure 'dot' command is installed and in system PATH.");
        }
    }
}