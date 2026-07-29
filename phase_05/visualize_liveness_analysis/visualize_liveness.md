# Intraprocedural Liveness Analysis (Improved — CFG Visualizations)

## Functionality

Extends the base liveness tool: in addition to printing per-instruction liveness facts to the terminal, the driver now renders three Graphviz PDFs of the exploded CFG at different levels of detail, each annotated with LIVE-IN/LIVE-OUT dataflow facts.

## Input & Output

| | |
|---|---|
| **Input** | Path to a WALA analysis scope file, and a path to source for line-text lookup |
| **Output** | Terminal dataflow report, plus three PDFs written to `out-class/cfg-out/`: `Liveness_Exploded_CFG.pdf`, `Liveness_Source_Exploded_CFG.pdf`, `Liveness_Source_Merged_CFG.pdf` |

## Program Code

### `IntraprocLiveness.java`

```java
package com.ibm.wala.examples.analysis.dataflow;

import com.ibm.wala.classLoader.IBytecodeMethod;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.dataflow.graph.AbstractMeetOperator;
import com.ibm.wala.dataflow.graph.BitVectorFramework;
import com.ibm.wala.dataflow.graph.BitVectorKillGen;
import com.ibm.wala.dataflow.graph.BitVectorSolver;
import com.ibm.wala.dataflow.graph.BitVectorUnion;
import com.ibm.wala.dataflow.graph.ITransferFunctionProvider;
import com.ibm.wala.fixpoint.BitVectorVariable;
import com.ibm.wala.fixpoint.UnaryOperator;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAPhiInstruction;
import com.ibm.wala.ssa.analysis.ExplodedControlFlowGraph;
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.graph.Graph;
import com.ibm.wala.util.graph.impl.GraphInverter;
import com.ibm.wala.util.intset.BitVector;
import com.ibm.wala.util.intset.IntIterator;
import com.ibm.wala.util.intset.MutableMapping;
import com.ibm.wala.util.intset.OrdinalSetMapping;

import java.util.Iterator;
import java.util.List;

public class IntraprocLiveness {

    private final IR ir;
    private final ExplodedControlFlowGraph ecfg;
    private final OrdinalSetMapping<Integer> valueNumberDomain;

    public IntraprocLiveness(IR ir) {
        this.ir = ir;
        this.ecfg = ExplodedControlFlowGraph.make(ir);

        MutableMapping<Integer> domain = MutableMapping.make();
        int maxVN = ir.getSymbolTable().getMaxValueNumber();
        for (int vn = 1; vn <= maxVN; vn++) {
            domain.add(vn);
        }
        this.valueNumberDomain = domain;
    }

    /** Runs the Kildall-style backward liveness solve and returns the raw fixpoint result. */
    public BitVectorSolver<IExplodedBasicBlock> analyze() {
        Graph<IExplodedBasicBlock> backwardGraph = GraphInverter.invert(ecfg);
        BitVectorFramework<IExplodedBasicBlock, Integer> framework =
                new BitVectorFramework<>(backwardGraph, new LivenessTransferFunctions(), valueNumberDomain);
        BitVectorSolver<IExplodedBasicBlock> solver = new BitVectorSolver<>(framework);

        try {
            solver.solve(null);
        } catch (CancelException e) {
            assert false;
        }
        return solver;
    }

    // Getters
    public IR getIR() { return ir; }
    public ExplodedControlFlowGraph getECFG() { return ecfg; }
    public OrdinalSetMapping<Integer> getValueNumberDomain() { return valueNumberDomain; }

    public void printDataFlowFacts(BitVectorSolver<IExplodedBasicBlock> solver, boolean namedOnly, List<String> sourceLines){
        for (IExplodedBasicBlock ebb : ecfg) {
            int instrIndex = ebb.getFirstInstructionIndex();
            SSAInstruction instr = ebb.getInstruction();
            boolean isEntryOrExit = ebb.isEntryBlock() || ebb.isExitBlock();

            if (instr == null && !isEntryOrExit) {
                continue;
            }

            Integer line = lineNumberForInstruction(ir, instrIndex);
            String srcText = sourceLineText(line, sourceLines);

            System.out.println("");
            System.out.println(ebb);
            System.out.println(instr);
            if (line != null) {
                System.out.println("  SOURCE:   line " + line + (srcText != null ? ": " + srcText : " (source unavailable)"));
            }
            System.out.println("  LIVE-IN:  "  + renderLiveSet(solver.getOut(ebb), ir, instrIndex, namedOnly));
            System.out.println("  LIVE-OUT: " + renderLiveSet(solver.getIn(ebb), ir, instrIndex, namedOnly));
            System.out.println("");
        }
    }

    // Helper to resolve variable names across the entire IR (SSA scope fallback)
    public String nameForValueNumber(IR ir, int valueNumber) {
        SSAInstruction[] instructions = ir.getInstructions();
        for (int i = 0; i < instructions.length; i++) {
            try {
                String[] names = ir.getLocalNames(i, valueNumber);
                if (names != null && names.length > 0 && names[0] != null) {
                    return names[0];
                }
            } catch (Exception e) {
                // Keep searching
            }
        }
        return "v" + valueNumber;
    }

    public String renderLiveSet(BitVectorVariable bv, IR ir, int instructionIndex, boolean namedOnly) {
        if (bv == null || bv.getValue() == null) return "{ }";
        StringBuilder sb = new StringBuilder("{ ");
        IntIterator it = bv.getValue().intIterator();
        while (it.hasNext()) {
            int domainIdx = it.next();
            int vn = valueNumberDomain.getMappedObject(domainIdx);
            String name = nameForValueNumber(ir, vn);
            if (namedOnly && name.startsWith("v") && name.substring(1).chars().allMatch(Character::isDigit)) {
                continue; // suppress unnamed fallback entries
            }
            sb.append(name).append(" ");
        }
        sb.append("}");
        return sb.toString();
    }

    public Integer lineNumberForInstruction(IR ir, int instrIndex) {
        if (instrIndex < 0) return null;
        IMethod method = ir.getMethod();
        if (!(method instanceof IBytecodeMethod)) return null;
        try {
            int bcIndex = ((IBytecodeMethod<?>) method).getBytecodeIndex(instrIndex);
            if (bcIndex < 0) return null;
            return method.getLineNumber(bcIndex);
        } catch (Exception e) {
            return null;
        }
    }

    public String sourceLineText(Integer lineNumber, List<String> sourceLines) {
        if (lineNumber == null || sourceLines == null) return null;
        try {
            if (lineNumber < 1 || lineNumber > sourceLines.size()) return null;
            return sourceLines.get(lineNumber - 1).trim();
        } catch (Exception e) {
            return null;
        }
    }

    // Transfer functions class
    private class LivenessTransferFunctions
            implements ITransferFunctionProvider<IExplodedBasicBlock, BitVectorVariable> {

        @Override
        public AbstractMeetOperator<BitVectorVariable> getMeetOperator() {
            return BitVectorUnion.instance();
        }

        @Override
        public UnaryOperator<BitVectorVariable> getNodeTransferFunction(IExplodedBasicBlock node) {
            BitVector gen = new BitVector();
            BitVector kill = new BitVector();

            // 1. Process normal instructions
            SSAInstruction instruction = node.getInstruction();
            if (instruction != null) {
                for (int i = 0; i < instruction.getNumberOfUses(); i++) {
                    int use = instruction.getUse(i);
                    if (use != -1) {
                        int idx = valueNumberDomain.getMappedIndex(use);
                        if (idx != -1) gen.set(idx);
                    }
                }
                if (instruction.hasDef()) {
                    int def = instruction.getDef();
                    int idx = valueNumberDomain.getMappedIndex(def);
                    if (idx != -1) kill.set(idx);
                }
            }

            // 2. Process Phi instructions
            for (Iterator<SSAPhiInstruction> it = node.iteratePhis(); it.hasNext(); ) {
                SSAPhiInstruction phi = it.next();

                for (int i = 0; i < phi.getNumberOfUses(); i++) {
                    int use = phi.getUse(i);
                    if (use != -1) {
                        int idx = valueNumberDomain.getMappedIndex(use);
                        if (idx != -1) gen.set(idx);
                    }
                }

                if (phi.hasDef()) {
                    int def = phi.getDef();
                    int idx = valueNumberDomain.getMappedIndex(def);
                    if (idx != -1) kill.set(idx);
                }
            }

            return new BitVectorKillGen(kill, gen);
        }

        @Override
        public boolean hasNodeTransferFunctions() {
            return true;
        }

        @Override
        public UnaryOperator<BitVectorVariable> getEdgeTransferFunction(IExplodedBasicBlock src, IExplodedBasicBlock dst) {
            return null;
        }

        @Override
        public boolean hasEdgeTransferFunctions() {
            return false;
        }
    }
}
```

### `LivenessAnalysisDriver.java`

```java
package com.ibm.wala.examples.drivers;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.core.util.config.AnalysisScopeReader;
import com.ibm.wala.dataflow.graph.BitVectorSolver;
import com.ibm.wala.examples.analysis.dataflow.IntraprocLiveness;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisCacheImpl;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.analysis.ExplodedControlFlowGraph;
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.Selector;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class LivenessAnalysisDriver {

    public static void main(String[] args) throws IOException, ClassHierarchyException, InterruptedException {
        if (args.length < 1) {
            System.err.println("Usage: java LivenessAnalysisDriver <scopeFile> [sourceRoot] [namedOnly]");
            System.exit(1);
        }

        String scopeFile = args[0];
        String sourceRoot = args.length > 1 ? args[1] : new File(scopeFile).getParent();
        // Dynamic argument: defaults to false if not provided
        boolean namedOnly = args.length > 2 && Boolean.parseBoolean(args[2]);

        File srcFile = new File(sourceRoot);
        List<String> sourceLines = Files.readAllLines(srcFile.toPath());

        AnalysisScope scope =
                AnalysisScopeReader.instance.readJavaScope(
                        scopeFile,
                        new File(Objects.requireNonNull(
                                LivenessAnalysisDriver.class.getClassLoader()
                                        .getResource("Exclusions.txt")).getFile()),
                        LivenessAnalysisDriver.class.getClassLoader());

        IClassHierarchy cha = ClassHierarchyFactory.make(scope);

        IClass targetClass = findSingleApplicationClass(cha);
        if (targetClass == null) {
            throw new IllegalStateException("No Application class found in target scope.");
        }

        IMethod method = targetClass.getMethod(Selector.make("main([Ljava/lang/String;)V"));

        AnalysisCache cache = new AnalysisCacheImpl();
        IR ir = cache.getIR(method, Everywhere.EVERYWHERE);

        // Run Liveness Analysis
        IntraprocLiveness liveness = new IntraprocLiveness(ir);
        BitVectorSolver<IExplodedBasicBlock> solver = liveness.analyze();

        // 1. Print analysis facts directly to terminal using dynamic namedOnly flag
        liveness.printDataFlowFacts(solver, namedOnly, sourceLines);

        Path outputDir = Path.of("out-class/cfg-out");
        Files.createDirectories(outputDir);

        // 2. Output File 1: Full Exploded CFG (All SSA nodes + Source + Dataflow Facts)
        Path dotFull = outputDir.resolve("Liveness_Exploded_CFG.dot");
        Path pdfFull = outputDir.resolve("Liveness_Exploded_CFG.pdf");
        Files.writeString(dotFull, toDotExplodedCFGWithDataflow(liveness, solver, sourceLines, true, false, namedOnly));
        generatePdfFromDot(dotFull, pdfFull);

        // 3. Output File 2: Unmerged Source Exploded CFG (Filters null instructions, multi-node per line)
        Path dotSourceOnly = outputDir.resolve("Liveness_Source_Exploded_CFG.dot");
        Path pdfSourceOnly = outputDir.resolve("Liveness_Source_Exploded_CFG.pdf");
        Files.writeString(dotSourceOnly, toDotExplodedCFGWithDataflow(liveness, solver, sourceLines, false, true, namedOnly));
        generatePdfFromDot(dotSourceOnly, pdfSourceOnly);

        // 4. Output File 3: Merged Source-Level CFG (Single node per line, merged LIVE-IN/LIVE-OUT)
        Path dotMerged = outputDir.resolve("Liveness_Source_Merged_CFG.dot");
        Path pdfMerged = outputDir.resolve("Liveness_Source_Merged_CFG.pdf");
        Files.writeString(dotMerged, toDotMergedSourceCFG(liveness, solver, sourceLines, namedOnly));
        generatePdfFromDot(dotMerged, pdfMerged);
    }

    /**
     * Builds a source-level CFG where all bytecode/SSA blocks belonging to the same source line
     * are merged into a single node.
     */
    private static String toDotMergedSourceCFG(
            IntraprocLiveness liveness,
            BitVectorSolver<IExplodedBasicBlock> solver,
            List<String> sourceLines,
            boolean namedOnly) {

        ExplodedControlFlowGraph ecfg = liveness.getECFG();
        IR ir = liveness.getIR();

        // Filter out null-instruction blocks (except ENTRY and EXIT)
        Set<IExplodedBasicBlock> keptNodes = new LinkedHashSet<>();
        for (IExplodedBasicBlock ebb : ecfg) {
            if (ebb.isEntryBlock() || ebb.isExitBlock() || ebb.getInstruction() != null) {
                keptNodes.add(ebb);
            }
        }

        // Group connected nodes that belong to the same source line
        List<SourceLineGroup> groups = buildMergedGroups(liveness, ecfg, sourceLines, keptNodes);

        StringBuilder sb = new StringBuilder();
        sb.append("digraph SourceMergedCFG {\n");
        sb.append("  node [shape=box, fontname=\"monospace\"];\n");

        // Render merged nodes and aggregated dataflow facts
        for (SourceLineGroup group : groups) {
            String liveIn = group.getStatementLiveIn(liveness, solver, ir, namedOnly);
            String liveOut = group.getStatementLiveOut(liveness, solver, ir, namedOnly);

            StringBuilder cfgLabel = new StringBuilder();
            if (group.isEntry) {
                cfgLabel.append("[ENTRY]\\l");
            } else if (group.isExit) {
                cfgLabel.append("[EXIT]\\l");
            } else {
                cfgLabel.append("line ").append(group.lineNumber).append(": ")
                        .append(escapeDot(group.sourceText)).append("\\l");
            }

            String styleAttr = (group.isEntry || group.isExit) ? ", style=filled, fillcolor=gray90" : "";

            // Main Source Node
            sb.append("  group_").append(group.id)
                    .append(" [label=\"").append(cfgLabel).append("\"").append(styleAttr).append("];\n");

            // Aggregated Dataflow Fact Note
            StringBuilder factLabel = new StringBuilder();
            factLabel.append("LIVE-IN:  ").append(escapeDot(liveIn)).append("\\l");
            factLabel.append("LIVE-OUT: ").append(escapeDot(liveOut)).append("\\l");

            sb.append("  fact_").append(group.id)
                    .append(" [label=\"").append(factLabel)
                    .append("\", shape=note, style=\"filled\", fillcolor=\"#EBF5FB\", color=\"#AED6F1\"];\n");

            sb.append("  { rank=same; group_").append(group.id).append("; fact_").append(group.id).append("; }\n");
            sb.append("  group_").append(group.id).append(" -> fact_").append(group.id)
                    .append(" [style=dashed, dir=none, constraint=false, color=\"#85929E\"];\n");
        }

        // Render edges between distinct source line groups
        for (SourceLineGroup srcGroup : groups) {
            for (IExplodedBasicBlock blockInSrc : srcGroup.blocks) {
                Set<IExplodedBasicBlock> succs = getKeptSuccessors(ecfg, blockInSrc, keptNodes);
                for (IExplodedBasicBlock succ : succs) {
                    SourceLineGroup dstGroup = findGroupContaining(groups, succ);
                    if (dstGroup != null && srcGroup != dstGroup) {
                        sb.append("  group_").append(srcGroup.id)
                                .append(" -> group_").append(dstGroup.id).append(";\n");
                    }
                }
            }
        }

        sb.append("}\n");
        return sb.toString();
    }

    private static String toDotExplodedCFGWithDataflow(
            IntraprocLiveness liveness,
            BitVectorSolver<IExplodedBasicBlock> solver,
            List<String> sourceLines,
            boolean includeSSAInstructions,
            boolean filterNullInstructions,
            boolean namedOnly) {

        ExplodedControlFlowGraph ecfg = liveness.getECFG();
        IR ir = liveness.getIR();

        Set<IExplodedBasicBlock> keptNodes = new LinkedHashSet<>();
        for (IExplodedBasicBlock ebb : ecfg) {
            boolean isEntryOrExit = ebb.isEntryBlock() || ebb.isExitBlock();
            if (filterNullInstructions && ebb.getInstruction() == null && !isEntryOrExit) {
                continue;
            }
            keptNodes.add(ebb);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("digraph ExplodedCFG {\n");
        sb.append("  node [shape=box, fontname=\"monospace\"];\n");

        for (IExplodedBasicBlock ebb : keptNodes) {
            int instrIndex = ebb.getFirstInstructionIndex();
            SSAInstruction instr = ebb.getInstruction();

            String liveIn = liveness.renderLiveSet(solver.getOut(ebb), ir, instrIndex, namedOnly);
            String liveOut = liveness.renderLiveSet(solver.getIn(ebb), ir, instrIndex, namedOnly);

            Integer line = liveness.lineNumberForInstruction(ir, instrIndex);
            String srcText = liveness.sourceLineText(line, sourceLines);

            StringBuilder cfgLabel = new StringBuilder();
            cfgLabel.append("ExplodedBlock[").append(ebb.getNumber()).append("]\\l");

            if (ebb.isEntryBlock()) {
                cfgLabel.append("[ENTRY]\\l");
            } else if (ebb.isExitBlock()) {
                cfgLabel.append("[EXIT]\\l");
            } else {
                if (includeSSAInstructions && instr != null) {
                    cfgLabel.append(escapeDot(instr.toString())).append("\\l");
                }
                if (line != null && srcText != null) {
                    cfgLabel.append("line ").append(line).append(": ").append(escapeDot(srcText)).append("\\l");
                }
            }

            String styleAttr = (ebb.isEntryBlock() || ebb.isExitBlock()) ? ", style=filled, fillcolor=gray90" : "";

            sb.append("  node_").append(ebb.getNumber())
                    .append(" [label=\"").append(cfgLabel).append("\"").append(styleAttr).append("];\n");

            StringBuilder factLabel = new StringBuilder();
            factLabel.append("LIVE-IN:  ").append(escapeDot(liveIn)).append("\\l");
            factLabel.append("LIVE-OUT: ").append(escapeDot(liveOut)).append("\\l");

            sb.append("  fact_").append(ebb.getNumber())
                    .append(" [label=\"").append(factLabel)
                    .append("\", shape=note, style=\"filled\", fillcolor=\"#EBF5FB\", color=\"#AED6F1\"];\n");

            sb.append("  { rank=same; node_").append(ebb.getNumber())
                    .append("; fact_").append(ebb.getNumber()).append("; }\n");

            sb.append("  node_").append(ebb.getNumber())
                    .append(" -> fact_").append(ebb.getNumber())
                    .append(" [style=dashed, dir=none, constraint=false, color=\"#85929E\"];\n");
        }

        for (IExplodedBasicBlock ebb : keptNodes) {
            Set<IExplodedBasicBlock> succs = getKeptSuccessors(ecfg, ebb, keptNodes);
            for (IExplodedBasicBlock succ : succs) {
                sb.append("  node_").append(ebb.getNumber())
                        .append(" -> node_").append(succ.getNumber()).append(";\n");
            }
        }

        sb.append("}\n");
        return sb.toString();
    }

    /**
     * Helper class representing a merged source line node in the CFG.
     */
    private static class SourceLineGroup {
        int id;
        Integer lineNumber;
        String sourceText;
        boolean isEntry;
        boolean isExit;
        List<IExplodedBasicBlock> blocks = new ArrayList<>();

        public String getStatementLiveIn(IntraprocLiveness liveness, BitVectorSolver<IExplodedBasicBlock> solver, IR ir, boolean namedOnly) {
            IExplodedBasicBlock entryBlock = blocks.get(0);
            return liveness.renderLiveSet(solver.getOut(entryBlock), ir, entryBlock.getFirstInstructionIndex(), namedOnly);
        }

        public String getStatementLiveOut(IntraprocLiveness liveness, BitVectorSolver<IExplodedBasicBlock> solver, IR ir, boolean namedOnly) {
            IExplodedBasicBlock exitBlock = blocks.get(blocks.size() - 1);
            return liveness.renderLiveSet(solver.getIn(exitBlock), ir, exitBlock.getFirstInstructionIndex(), namedOnly);
        }
    }

    private static List<SourceLineGroup> buildMergedGroups(
            IntraprocLiveness liveness,
            ExplodedControlFlowGraph ecfg,
            List<String> sourceLines,
            Set<IExplodedBasicBlock> keptNodes) {

        Map<IExplodedBasicBlock, Integer> blockToGroupId = new HashMap<>();
        Map<Integer, SourceLineGroup> groupsMap = new LinkedHashMap<>();
        int groupCounter = 0;

        for (IExplodedBasicBlock ebb : keptNodes) {
            int gid = groupCounter++;
            blockToGroupId.put(ebb, gid);

            SourceLineGroup grp = new SourceLineGroup();
            grp.id = gid;
            grp.isEntry = ebb.isEntryBlock();
            grp.isExit = ebb.isExitBlock();

            if (!grp.isEntry && !grp.isExit) {
                int instrIndex = ebb.getFirstInstructionIndex();
                grp.lineNumber = liveness.lineNumberForInstruction(liveness.getIR(), instrIndex);
                grp.sourceText = liveness.sourceLineText(grp.lineNumber, sourceLines);
            }
            grp.blocks.add(ebb);
            groupsMap.put(gid, grp);
        }

        boolean changed = true;
        while (changed) {
            changed = false;
            for (IExplodedBasicBlock u : keptNodes) {
                int uGroup = blockToGroupId.get(u);
                SourceLineGroup grpU = groupsMap.get(uGroup);
                if (grpU == null || grpU.lineNumber == null) continue;

                Set<IExplodedBasicBlock> succs = getKeptSuccessors(ecfg, u, keptNodes);
                for (IExplodedBasicBlock v : succs) {
                    int vGroup = blockToGroupId.get(v);
                    if (uGroup == vGroup) continue;

                    SourceLineGroup grpV = groupsMap.get(vGroup);
                    if (grpV != null && Objects.equals(grpU.lineNumber, grpV.lineNumber)) {
                        grpU.blocks.addAll(grpV.blocks);
                        for (IExplodedBasicBlock b : grpV.blocks) {
                            blockToGroupId.put(b, uGroup);
                        }
                        groupsMap.remove(vGroup);
                        changed = true;
                        break;
                    }
                }
                if (changed) break;
            }
        }

        for (SourceLineGroup grp : groupsMap.values()) {
            grp.blocks.sort(Comparator.comparingInt(IExplodedBasicBlock::getNumber));
        }

        return new ArrayList<>(groupsMap.values());
    }

    private static SourceLineGroup findGroupContaining(List<SourceLineGroup> groups, IExplodedBasicBlock target) {
        for (SourceLineGroup grp : groups) {
            if (grp.blocks.contains(target)) return grp;
        }
        return null;
    }

    private static Set<IExplodedBasicBlock> getKeptSuccessors(
            ExplodedControlFlowGraph ecfg,
            IExplodedBasicBlock src,
            Set<IExplodedBasicBlock> keptNodes) {

        Set<IExplodedBasicBlock> result = new LinkedHashSet<>();
        Set<IExplodedBasicBlock> visited = new HashSet<>();

        Queue<IExplodedBasicBlock> worklist = new LinkedList<>();
        for (Iterator<IExplodedBasicBlock> it = ecfg.getSuccNodes(src); it.hasNext(); ) {
            worklist.add(it.next());
        }

        while (!worklist.isEmpty()) {
            IExplodedBasicBlock curr = worklist.poll();
            if (!visited.add(curr)) continue;

            if (keptNodes.contains(curr)) {
                result.add(curr);
            } else {
                for (Iterator<IExplodedBasicBlock> it = ecfg.getSuccNodes(curr); it.hasNext(); ) {
                    worklist.add(it.next());
                }
            }
        }
        return result;
    }

    private static void generatePdfFromDot(Path dotFilePath, Path pdfFilePath) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("dot", "-Tpdf", dotFilePath.toString(), "-o", pdfFilePath.toString());
        pb.inheritIO();
        Process process = pb.start();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            System.err.println("dot command failed for " + dotFilePath.getFileName() + " with exit code " + exitCode);
        } else {
            System.out.println("PDF written to " + pdfFilePath.toAbsolutePath());
        }
    }

    private static String escapeDot(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    private static IClass findSingleApplicationClass(IClassHierarchy cha) {
        for (IClass c : cha) {
            if (c.getClassLoader().getReference().equals(ClassLoaderReference.Application)) {
                return c;
            }
        }
        return null;
    }
}
```

## Run Instructions

```bash
./gradlew run -PmainClass=com.ibm.wala.examples.drivers.LivenessAnalysisDriver --args="path/to/scope.txt path/to/Main.java true"
```

- `args[0]` — path to a WALA analysis scope file
- `args[1]` — path to the source file for line-text lookup
- `args[2]` — *(optional)* to include or omit temporary SSA variables like v1, v2
1. Include all variables (including temporary SSA variables like v1, v2, etc.)
     Pass `false` as the 3rd argument
2. Filter to show ONLY named variables (hide SSA compiler temporaries)
      Pass `true` as the 3rd argument:
- Output PDFs are written to `out-class/cfg-out/`

## Output Generation Steps

Here is how each step works, including the specific functions invoked and the mechanisms that produce their distinct results.

### Step 1: Terminal Output

**Goal:** Print a quick, readable summary of liveness facts directly to the console.

- **Function Used:** `liveness.printDataFlowFacts(solver, false, sourceLines)`
- **How It Works:**
    1. Iterates over every exploded basic block in the control flow graph.
    2. Filters out empty landing blocks (`instr == null`) to keep output clean.
    3. Queries `solver.getOut(ebb)` (for `LIVE-IN`) and `solver.getIn(ebb)` (for `LIVE-OUT`).
    4. Formats each block alongside its source line number and text, printing formatted text lines directly to `System.out`.

### Step 2: Full Exploded CFG (`Liveness_Exploded_CFG.pdf`)

**Goal:** Render a 1-to-1 visual representation of WALA's complete, un-filtered SSA exploded control flow graph.

- **Main Function Used:** `toDotExplodedCFGWithDataflow(..., includeSSAInstructions = true, filterNullInstructions = false)`
- **How It Works:**
    1. **No Filtering** (`filterNullInstructions = false`): Keeps all basic blocks, including empty return landing pads (`instr == null`).
    2. **SSA Instruction Visibility** (`includeSSAInstructions = true`): Formats node labels to display raw WALA SSA instructions (e.g., `6 = binaryop(mul) 4, 4`) alongside source lines.
    3. **Dataflow Sticky Notes:** Creates a secondary Graphviz node (`fact_X`) for every block, displaying `LIVE-IN` and `LIVE-OUT` sets. Connects the CFG block to its fact note using horizontal alignment (`rank=same`) and dashed edges.
    4. **Direct Edges:** Directly renders edges between adjacent block numbers without modifying graph structure.

### Step 3: Unmerged Source Exploded CFG (`Liveness_Source_Exploded_CFG.pdf`)

**Goal:** Clean up the exploded graph by hiding raw SSA assembly and skipping empty bytecode blocks, while keeping distinct blocks for multi-instruction lines.

- **Main Functions Used:** `toDotExplodedCFGWithDataflow(..., includeSSAInstructions = false, filterNullInstructions = true)` and `getKeptSuccessors(...)`
- **How It Works:**
    1. **Node Filtering** (`filterNullInstructions = true`): Skips any non-entry/exit block where `instr == null`.
    2. **Hides SSA Code** (`includeSSAInstructions = false`): Removes raw SSA strings, rendering only the source line number and source text inside each block.
    3. **Edge Contraction via `getKeptSuccessors(...)`:** When a node is skipped, standard graph edges break. This function uses a Breadth-First Search (BFS) starting from the skipped node to find the next valid, kept successor nodes, re-routing control flow edges dynamically so the graph remains connected.

### Step 4: Merged Source-Level CFG (`Liveness_Source_Merged_CFG.pdf`)

**Goal:** Simulate a true source-level CFG where duplicate bytecode blocks belonging to the exact same Java line are combined into a single visual node.

- **Main Functions Used:** `toDotMergedSourceCFG(...)`, `buildMergedGroups(...)`, and `SourceLineGroup` methods.
- **How It Works:**
    1. **Grouping** (`buildMergedGroups`): Wraps kept blocks into `SourceLineGroup` objects. It iteratively scans connected blocks and merges adjacent nodes that share the exact same `lineNumber` into a single group.
    2. **Boundary Dataflow Fact Aggregation:**
        - **Statement `LIVE-IN`:** Calls `group.getStatementLiveIn()`, reading `solver.getOut()` of the first block in the group (the entry point of the source statement).
        - **Statement `LIVE-OUT`:** Calls `group.getStatementLiveOut()`, reading `solver.getIn()` of the last block in the group (the exit point of the source statement).
        - **Temporary Variable Removal:** Passes `namedOnly = true` to `renderLiveSet`, filtering out short-lived compiler temporaries (like `v7`) that live and die strictly inside the statement.
    3. **Inter-Group Edges:** Draws edges between distinct `SourceLineGroup` IDs if any block inside Group A can reach any block in Group B.

### Shared PDF Rendering Utility

- **Function Used:** `generatePdfFromDot(dotFilePath, pdfFilePath)`
- **How It Works:** Uses Java's `ProcessBuilder` to execute the system terminal command:

```bash
dot -Tpdf <input.dot> -o <output.pdf>
```

It captures the return exit code and prints the output file path upon success.