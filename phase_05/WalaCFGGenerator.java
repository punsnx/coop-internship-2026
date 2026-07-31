package com.ibm.wala.examples.drivers;

import com.ibm.wala.classLoader.IBytecodeMethod;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisCacheImpl;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.core.util.config.AnalysisScopeReader;
import com.ibm.wala.ipa.cfg.PrunedCFG;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSACFG;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.ipa.cfg.ExceptionPrunedCFG;
import com.ibm.wala.shrike.shrikeBT.IInstruction;
import com.ibm.wala.util.graph.impl.SlowSparseNumberedGraph;

import java.io.*;
import java.util.*;

public class WalaCFGGenerator {

    private static final Map<String, List<String>> sourceFileMap = new HashMap<>();

    public static class BytecodeBlock {
        private final int id;
        private final List<String> instructions = new ArrayList<>();
        private final Set<Integer> lines = new TreeSet<>();
        private boolean isEntry;
        private boolean isExit;

        public BytecodeBlock(int id, boolean isEntry, boolean isExit) {
            this.id = id;
            this.isEntry = isEntry;
            this.isExit = isExit;
        }

        public void addInstruction(String inst) { this.instructions.add(inst); }
        public void addLine(int line) { this.lines.add(line); }
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
        public int hashCode() { return Objects.hash(id); }
    }

    public static class JavaCodeBlock {
        private final int id;
        private final Set<Integer> lines = new TreeSet<>();
        private boolean isEntry;
        private boolean isExit;

        public JavaCodeBlock(int id, Set<Integer> lines, boolean isEntry, boolean isExit) {
            this.id = id;
            if (lines != null) {
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
        public int hashCode() { return Objects.hash(id); }
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: WalaCFGGenerator <scope-file-or-binary-dir> [source-dir]");
            return;
        }

        String scopeOrDirInput = args[0];
        String sourceDir = args.length > 1 ? args[1] : null;

        try {
            if (sourceDir != null) {
                File srcDirFile = new File(sourceDir);
                if (srcDirFile.exists()) {
                    loadSourceDirectory(srcDirFile, srcDirFile);
                    System.out.println("Successfully indexed " + sourceFileMap.size() + " source entries from: " + sourceDir);
                } else {
                    System.err.println("Warning: Source directory does not exist: " + sourceDir);
                }
            } else {
                System.out.println("Notice: No source directory provided. Graph nodes will display line numbers only.");
            }

            File exclusionsFile = null;
            var resource = WalaCFGGenerator2.class.getClassLoader().getResource("Exclusions.txt");
            if (resource != null) {
                exclusionsFile = new File(resource.getFile());
            }

            AnalysisScope scope;
            File inputAsFile = new File(scopeOrDirInput);

            if (inputAsFile.isDirectory()) {
                scope = AnalysisScopeReader.instance.makeJavaBinaryAnalysisScope(
                        inputAsFile.getAbsolutePath(), exclusionsFile);
            } else {
                scope = AnalysisScopeReader.instance.readJavaScope(
                        scopeOrDirInput, exclusionsFile, WalaCFGGenerator2.class.getClassLoader());
            }

            IClassHierarchy cha = ClassHierarchyFactory.make(scope);
            List<IClass> classes = findAllApplicationClasses(cha);

            classes.sort(Comparator.comparing(c -> c.getName().toString()));

            AnalysisCache cache = new AnalysisCacheImpl();
            File exportDir = new File("export");
            if (!exportDir.exists()) {
                exportDir.mkdirs();
            }

            for (IClass klass : classes) {
                String rawClassName = klass.getName().toString(); // e.g. "Lcom/example/OuterClass$InnerClass"

                // 1. Identify Outer Class Name vs Inner Class Name
                String outerClassName;
                String innerClassName;

                if (rawClassName.contains("$")) {
                    // Extract everything before the '$' as the Outer Class
                    String outerRaw = rawClassName.substring(0, rawClassName.indexOf('$'));
                    outerClassName = sanitizeIdentifier(outerRaw);

                    // Extract everything after '$' for the nested class filename
                    innerClassName = sanitizeIdentifier(rawClassName.substring(rawClassName.indexOf('$') + 1));
                } else {
                    // Top-level class (not nested)
                    outerClassName = sanitizeIdentifier(rawClassName);
                    innerClassName = outerClassName;
                }

                System.out.println("Processing Class: " + rawClassName + " (Folder: " + outerClassName + ")");

                // 2. Create a dedicated folder for the main class inside 'export/'
                File mainClassDir = new File("export", outerClassName);
                if(!outerClassName.equals(innerClassName)) {
                    mainClassDir = new File("export/" + outerClassName, innerClassName);
                }
                if (!mainClassDir.exists()) {
                    mainClassDir.mkdirs();
                }

                for (IMethod method : klass.getDeclaredMethods()) {
                    if (!(method instanceof IBytecodeMethod)) {
                        continue;
                    }

                    IBytecodeMethod bytecodeMethod = (IBytecodeMethod) method;
                    String rawMethodName = method.getName().toString();
                    if(rawMethodName.equals("<init>")){continue;}
                    String safeMethodName = sanitizeIdentifier(rawMethodName);
                    System.out.println("    Processing Method: " + rawMethodName);

                    IR ir = cache.getIR(method);
                    if (ir == null) {
                        continue;
                    }

                    SSACFG ssaCFG = ir.getControlFlowGraph();
                    PrunedCFG prunedSsaCFG = ExceptionPrunedCFG.make(ssaCFG);

                    // 3. Output files directly into the outer class directory
                    String prefix = new File(mainClassDir, "cfg_" + innerClassName + "_" + safeMethodName).getPath();
                    // Step 1: Export Pruned SSA CFG
                    String ssaDotPath = prefix + "_ssa.dot";
                    exportSsaCFGToDot(prunedSsaCFG, ssaDotPath);
                    convertDotToPdf(ssaDotPath, prefix + "_ssa.pdf");

                    // Step 2: Map SSA to Bytecode CFG
                    SlowSparseNumberedGraph<BytecodeBlock> bytecodeCFG = mapSsaToBytecodeCFG(prunedSsaCFG, bytecodeMethod);
                    String bytecodeDotPath = prefix + "_bytecode.dot";
                    exportBytecodeCFGToDot(bytecodeCFG, bytecodeDotPath, rawClassName);
                    convertDotToPdf(bytecodeDotPath, prefix + "_bytecode.pdf");

                    // Step 3: Map Bytecode to Consolidating Java CFG
                    SlowSparseNumberedGraph<JavaCodeBlock> javaCFG = mapBytecodeToJavaCodeCFG(bytecodeCFG, rawClassName);
                    javaCFG = coalesceStraightLineBlocks(javaCFG);
                    String javaDotPath = prefix + "_javacode.dot";
                    exportJavaCodeCFGToDot(javaCFG, javaDotPath, rawClassName);
                    convertDotToPdf(javaDotPath, prefix + "_javacode.pdf");
                }
            }
        } catch(Exception e){
            e.printStackTrace();
        }
    }

    private static String sanitizeIdentifier(String identifier) {
        if (identifier == null) return "null";
        return identifier.replace("L", "")
                .replace("/", "_")
                .replace("\\", "_")
                .replace("<", "")
                .replace(">", "")
                .replace("$", "_")
                .replace(";", "");
    }

    private static void loadSourceDirectory(File rootDir, File currentDir) {
        if (!currentDir.exists() || !currentDir.isDirectory()) return;
        File[] files = currentDir.listFiles();
        if (files == null) return;

        for (File f : files) {
            if (f.isDirectory()) {
                loadSourceDirectory(rootDir, f);
            } else if (f.getName().endsWith(".java")) {
                List<String> lines = new ArrayList<>();
                try (BufferedReader br = new BufferedReader(new FileReader(f))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        lines.add(line);
                    }

                    // 1. Calculate relative package path: e.g. "com/example/MyClass"
                    String relativePath = rootDir.toURI().relativize(f.toURI()).getPath();
                    relativePath = relativePath.replace(".java", "").replace("\\", "/");
                    sourceFileMap.put(relativePath, lines);

                    // 2. Fallback key using simple class name: e.g. "MyClass"
                    String simpleName = f.getName().replace(".java", "");
                    sourceFileMap.putIfAbsent(simpleName, lines);

                } catch (IOException e) {
                    System.err.println("Warning: Unable to read source file: " + f.getAbsolutePath());
                }
            }
        }
    }

    private static String getFullyQualifiedClassName(String walaClassName) {
        if (walaClassName == null) return "";

        String cleaned = walaClassName;
        // Strip leading WALA JVM descriptor 'L'
        if (cleaned.startsWith("L")) {
            cleaned = cleaned.substring(1);
        }
        // Strip trailing semicolon if present
        if (cleaned.endsWith(";")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        // Handle nested/inner classes (e.g., com/example/MyClass$Inner -> com/example/MyClass)
        if (cleaned.contains("$")) {
            cleaned = cleaned.substring(0, cleaned.indexOf('$'));
        }
        return cleaned; // Returns "com/example/MyClass" or "MyClass"
    }

    private static String getSourceLineText(String walaClassName, int lineNum) {
        String fqName = getFullyQualifiedClassName(walaClassName);

        // Try finding by relative package path first (e.g. "com/example/MyClass")
        List<String> lines = sourceFileMap.get(fqName);

        // Fallback to simple name lookup (e.g. "MyClass")
        if (lines == null) {
            String simpleName = fqName.substring(fqName.lastIndexOf('/') + 1);
            lines = sourceFileMap.get(simpleName);
        }

        if (lines != null) {
            int index = lineNum - 1;
            if (index >= 0 && index < lines.size()) {
                return lines.get(index).trim();
            }
        }
        return "Line " + lineNum;
    }

    private static SlowSparseNumberedGraph<BytecodeBlock> mapSsaToBytecodeCFG(
            PrunedCFG ssaCFG, IBytecodeMethod bytecodeMethod) {

        SlowSparseNumberedGraph<BytecodeBlock> bytecodeCFG = SlowSparseNumberedGraph.make();
        Map<ISSABasicBlock, BytecodeBlock> ssaToBytecodeMap = new HashMap<>();

        IInstruction[] rawInstructions = null;
        try {
            rawInstructions = (IInstruction[]) bytecodeMethod.getInstructions();
        } catch (Exception ignored) {}

        for (Iterator<ISSABasicBlock> it = ssaCFG.iterator(); it.hasNext(); ) {
            ISSABasicBlock ssaBlock = it.next();
            BytecodeBlock bcBlock = new BytecodeBlock(ssaBlock.getNumber(), ssaBlock.isEntryBlock(), ssaBlock.isExitBlock());

            if (!ssaBlock.isEntryBlock() && !ssaBlock.isExitBlock()) {
                int start = ssaBlock.getFirstInstructionIndex();
                int end = ssaBlock.getLastInstructionIndex();
                for (int i = start; i <= end; i++) {
                    if (i >= 0) {
                        try {
                            int bcIndex = bytecodeMethod.getBytecodeIndex(i);
                            int line = bytecodeMethod.getLineNumber(bcIndex);
                            if (line > 0) {
                                bcBlock.addLine(line);
                            }

                            if (rawInstructions != null && i < rawInstructions.length) {
                                IInstruction inst = rawInstructions[i];
                                if (inst != null) {
                                    bcBlock.addInstruction(bcIndex + ": " + inst.toString());
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }

            bytecodeCFG.addNode(bcBlock);
            ssaToBytecodeMap.put(ssaBlock, bcBlock);
        }

        for (Iterator<ISSABasicBlock> it = ssaCFG.iterator(); it.hasNext(); ) {
            ISSABasicBlock srcSsa = it.next();
            BytecodeBlock srcBc = ssaToBytecodeMap.get(srcSsa);

            for (Iterator<ISSABasicBlock> succIt = ssaCFG.getSuccNodes(srcSsa); succIt.hasNext(); ) {
                ISSABasicBlock destSsa = succIt.next();
                BytecodeBlock destBc = ssaToBytecodeMap.get(destSsa);
                if (srcBc != null && destBc != null) {
                    bytecodeCFG.addEdge(srcBc, destBc);
                }
            }
        }

        return bytecodeCFG;
    }

    private static SlowSparseNumberedGraph<JavaCodeBlock> mapBytecodeToJavaCodeCFG(
            SlowSparseNumberedGraph<BytecodeBlock> bytecodeCFG, String className) {

        SlowSparseNumberedGraph<JavaCodeBlock> javaCFG = SlowSparseNumberedGraph.make();
        Map<Set<Integer>, JavaCodeBlock> normalBlocksMap = new HashMap<>();
        Map<BytecodeBlock, JavaCodeBlock> bytecodeToJavaMap = new HashMap<>();

        JavaCodeBlock entryBlock = null;
        JavaCodeBlock exitBlock = null;

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
            } else if (b.getLines() != null && !b.getLines().isEmpty()) {
                Set<Integer> lines = b.getLines();
                Set<Integer> filteredLines = new TreeSet<>();
                for (int lineNum : lines) {
                    String lineText = getSourceLineText(className, lineNum);
                    if (lineText != null && lineText.trim().equals("}")) {
                        continue;
                    }
                    filteredLines.add(lineNum);
                }

                if (filteredLines.isEmpty()) {
                    continue;
                }
                JavaCodeBlock targetBlock = normalBlocksMap.get(filteredLines);
                if (targetBlock == null) {
                    targetBlock = new JavaCodeBlock(b.getId(), filteredLines, false, false);
                    javaCFG.addNode(targetBlock);
                    normalBlocksMap.put(filteredLines, targetBlock);
                }
                bytecodeToJavaMap.put(b, targetBlock);
            }
        }

        for (BytecodeBlock b : bytecodeCFG) {
            JavaCodeBlock srcJava = bytecodeToJavaMap.get(b);
            if (srcJava != null) {
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

        boolean changed = true;
        while (changed) {
            changed = false;
            JavaCodeBlock uToMerge = null;
            JavaCodeBlock vToMerge = null;

            for (JavaCodeBlock u : graph) {
                if (u.isEntry() || u.isExit()) continue;

                int succCount = 0;
                JavaCodeBlock singleSucc = null;
                for (Iterator<JavaCodeBlock> it = graph.getSuccNodes(u); it.hasNext(); ) {
                    singleSucc = it.next();
                    succCount++;
                }

                if (succCount == 1 && singleSucc != null && !singleSucc.isEntry() && !singleSucc.isExit()) {
                    int predCount = 0;
                    JavaCodeBlock singlePred = null;
                    for (Iterator<JavaCodeBlock> it = graph.getPredNodes(singleSucc); it.hasNext(); ) {
                        singlePred = it.next();
                        predCount++;
                    }

                    if (predCount == 1 && u.equals(singlePred)) {
                        uToMerge = u;
                        vToMerge = singleSucc;
                        changed = true;
                        break;
                    }
                }
            }

            if (changed && uToMerge != null && vToMerge != null) {
                uToMerge.getLines().addAll(vToMerge.getLines());

                List<JavaCodeBlock> successors = new ArrayList<>();
                for (Iterator<JavaCodeBlock> it = graph.getSuccNodes(vToMerge); it.hasNext(); ) {
                    successors.add(it.next());
                }

                graph.removeEdge(uToMerge, vToMerge);

                for (JavaCodeBlock succ : successors) {
                    graph.removeEdge(vToMerge, succ);
                    if (!uToMerge.equals(succ) && !graph.hasEdge(uToMerge, succ)) {
                        graph.addEdge(uToMerge, succ);
                    }
                }

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

        if (visited.contains(currentBytecode)) return;
        visited.add(currentBytecode);

        JavaCodeBlock targetJava = bytecodeToJavaMap.get(currentBytecode);
        if (targetJava != null) {
            if (!srcJava.equals(targetJava)) {
                if (!javaCFG.hasEdge(srcJava, targetJava)) {
                    javaCFG.addEdge(srcJava, targetJava);
                }
            }
        } else {
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
                label.append("Block ").append(block.getNumber()).append("\n");
                if (block.isEntryBlock()) {
                    label.append("ENTRY");
                } else if (block.isExitBlock()) {
                    label.append("EXIT");
                } else {
                    int start = block.getFirstInstructionIndex();
                    int end = block.getLastInstructionIndex();
                    label.append("IR Range: [").append(start).append(", ").append(end).append("]\n");
                    for (int i = start; i <= end; i++) {
                        SSAInstruction inst = (SSAInstruction) ssaCFG.getInstructions()[i];
                        if (inst != null) {
                            String cleanInst = inst.toString().replace("\"", "\\\"");
                            label.append(i).append(": ").append(cleanInst).append("\n");
                        }
                    }
                }
                out.printf("  %d [label=\"%s\"];\n", block.getNumber(), escapeForDot(label.toString()));
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

    private static void exportBytecodeCFGToDot(
            SlowSparseNumberedGraph<BytecodeBlock> bytecodeCFG, String filepath, String className) throws IOException {
        try (PrintWriter out = new PrintWriter(new FileWriter(filepath))) {
            out.println("digraph Reconstructed_Bytecode_CFG {");
            out.println("  node [shape=box, color=blue, fontname=\"Courier\"];");

            for (Iterator<BytecodeBlock> it = bytecodeCFG.iterator(); it.hasNext(); ) {
                BytecodeBlock block = it.next();
                StringBuilder label = new StringBuilder();
                label.append("Block ").append(block.getId()).append("\n");

                if (block.isEntry()) {
                    label.append("ENTRY");
                } else if (block.isExit()) {
                    label.append("EXIT");
                } else {
                    label.append("JVM Instructions:\n-----------------------------\n");
                    for (String inst : block.getInstructions()) {
                        label.append("  ").append(inst).append("\n");
                    }

                    if (!block.getLines().isEmpty()) {
                        label.append("-----------------------------\nMapped Source Lines:\n");
                        for (int line : block.getLines()) {
                            String code = getSourceLineText(className, line);
                            label.append("  L").append(line).append(": ").append(code).append("\n");
                        }
                    }
                }
                out.printf("  %d [label=\"%s\"];\n", block.getId(), escapeForDot(label.toString()));
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

    private static void exportJavaCodeCFGToDot(
            SlowSparseNumberedGraph<JavaCodeBlock> lineCFG, String filepath, String className) throws IOException {
        try (PrintWriter out = new PrintWriter(new FileWriter(filepath))) {
            out.println("digraph Mapped_JavaCode_CFG {");
            out.println("  node [shape=box, style=filled, fillcolor=lightyellow, fontname=\"Courier\"];");

            for (Iterator<JavaCodeBlock> it = lineCFG.iterator(); it.hasNext(); ) {
                JavaCodeBlock node = it.next();
                StringBuilder label = new StringBuilder();

                if (node.isEntry()) {
                    label.append("ENTRY");
                } else if (node.isExit()) {
                    label.append("EXIT");
                } else {
                    label.append("Block ").append(node.getId()).append("\n-----------------------------\n");
                    boolean first = true;
                    for (int line : node.getLines()) {
                        String code = getSourceLineText(className, line);
                        if (!first) label.append("\n");
                        label.append("L").append(line).append(": ").append(code);
                        first = false;
                    }
                }
                out.printf("  %d [label=\"%s\"];\n", node.getId(), escapeForDot(label.toString()));
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
            p.waitFor();
        } catch (Exception ignored) {}
        new File(dotPath).delete();
    }

    private static List<IClass> findAllApplicationClasses(IClassHierarchy cha) {
        List<IClass> appClasses = new ArrayList<>();
        for (IClass c : cha) {
            if (c.getClassLoader().getReference().equals(ClassLoaderReference.Application)) {
                if (!c.isArrayClass() && !c.isInterface()) {
                    appClasses.add(c);
                }
            }
        }
        return appClasses;
    }
}