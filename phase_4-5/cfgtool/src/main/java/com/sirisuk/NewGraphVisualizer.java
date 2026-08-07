package com.sirisuk;

import com.ibm.wala.classLoader.IBytecodeMethod;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ssa.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Splits WALA's low-level basic blocks back into readable, control-flow-aware blocks,
 * Each method is processed in four stages:
 *
 *   Resolve  -> resolveAllBlocks : every block's instructions -> its source line(s)
 *   Classify -> classify         : the NFA's INPUT ALPHABET - a source line becomes one
 *                                  token (IF/ELSEIF/ELSE/WHILE/FOR/DO), or INSTR otherwise
 *   Group    -> buildGroups      : RUN THE MACHINE - the `start` accumulator folds INSTR
 *                                  tokens into the current block; any control keyword
 *                                  closes it and a fresh accumulator begins (the NFA's
 *                                  SplitBB -> start edge). findMergeTarget is the
 *                                  TRANSITION FUNCTION deciding "does this block stay in
 *                                  the accumulator, or start a new one?"
 *   Print    -> printGroup       : one node per merged group - label, source lines, edges
 */
public class NewGraphVisualizer {

    // one class's source file, read from disk once
    private static final Map<IClass, String[]> sourceCache = new HashMap<>();

    // The NFA's input alphabet: a block's role, read from its first resolved source line.
    private enum Kind { IF, ELSEIF, ELSE, WHILE, FOR, DO, INSTR }

    // one resolved source line, in the order it was found within a basic block (not yet deduped)
    private record ResolvedLine(int lineNumber, String text) {}

    // Walk every application method and print its control-flow-aware merged CFG.
    public static void printMergedCFG(CallGraph cg, String sourceDir) {
        for (CGNode node : cg) {
            IR ir = node.getIR();
            IMethod method = node.getMethod();
            if (!SimpleVisualizer.isAppMethod(node) || ir == null) continue;
            if (!(method instanceof IBytecodeMethod)) continue; // no bytecode -> no debug info

            System.out.println("\n=== " + method.getSignature() + " ===");
            printMethod(ir.getControlFlowGraph(), (IBytecodeMethod<?>) method, sourceDir);
        }
    }

    // The four-stage pipeline for one method: Resolve -> Classify -> Group -> Print.
    private static void printMethod(SSACFG cfg, IBytecodeMethod<?> method, String sourceDir) {
        String[] sourceLines = sourceCache.computeIfAbsent(
                method.getDeclaringClass(), klass -> loadSource(klass, sourceDir));

        // Resolve:  every block's instructions -> its source line(s)
        Map<Integer, List<ResolvedLine>> blockLines = resolveAllBlocks(cfg, method, sourceLines);
        // Classify: each block's first source line -> an NFA token (Kind)
        Map<Integer, Kind> kindOf = classifyAll(cfg, blockLines);
        // Group:    run the NFA - fold blocks into control-flow-aware groups
        List<List<ISSABasicBlock>> groups = buildGroups(cfg, kindOf);
        Map<Integer, Integer> blockToGroup = indexByBlockNumber(groups);

        // Print:    one node per merged group
        for (List<ISSABasicBlock> group : groups) {
            printGroup(group, cfg, groups, blockToGroup, blockLines, kindOf);
        }
    }

    // Resolve: each block's instructions -> source lines, in order, undeduped
    // (deduping happens once, later, across a group's full member list - see printSourceLines).
    private static Map<Integer, List<ResolvedLine>> resolveAllBlocks(
            SSACFG cfg, IBytecodeMethod<?> method, String[] sourceLines) {
        Map<Integer, List<ResolvedLine>> result = new HashMap<>();
        for (ISSABasicBlock bb : cfg) {
            List<ResolvedLine> lines = new ArrayList<>();
            for (int i = bb.getFirstInstructionIndex(); i >= 0 && i <= bb.getLastInstructionIndex(); i++) {
                int line = bytecodeLine(method, i);
                if (line < 0) continue;
                String text = (line - 1 < sourceLines.length) ? sourceLines[line - 1].strip() : "unavailable";
                lines.add(new ResolvedLine(line, text));
            }
            result.put(bb.getNumber(), lines);
        }
        return result;
    }

    // Classify: give every block its NFA token, read from its first resolvable source line.
    private static Map<Integer, Kind> classifyAll(SSACFG cfg, Map<Integer, List<ResolvedLine>> blockLines) {
        Map<Integer, Kind> result = new HashMap<>();
        for (ISSABasicBlock bb : cfg) {
            List<ResolvedLine> lines = blockLines.get(bb.getNumber());
            String text = lines.isEmpty() ? null : lines.get(0).text();
            result.put(bb.getNumber(), classify(text));
        }
        return result;
    }

    // The NFA's tokenizer: one source line -> one token. A control keyword maps to its
    // Kind (IF/ELSEIF/ELSE/WHILE/FOR/DO); everything else - plain statements, "}" lines,
    // class/method headers - is INSTR (the alphabet symbol the accumulator folds).
    // ELSEIF is checked before ELSE since "else if" also matches a bare "else" prefix.
    private static Kind classify(String text) {
        if (text == null) return Kind.INSTR;
        String t = text.strip();
        if (t.matches("^}?\\s*else\\s+if\\s*\\(.*")) return Kind.ELSEIF;
        if (t.matches("^}?\\s*else\\b.*")) return Kind.ELSE;
        if (t.matches("^if\\s*\\(.*")) return Kind.IF;
        if (t.matches("^}?\\s*while\\s*\\(.*")) return Kind.WHILE; // \}? also covers do-while's "} while(...);"
        if (t.matches("^for\\s*\\(.*")) return Kind.FOR;
        if (t.matches("^do\\b.*")) return Kind.DO;
        return Kind.INSTR;
    }

    // Group: RUN THE NFA. Blocks are visited in order; each either joins the accumulator
    // (its predecessor's group) or opens a fresh one. findMergeTarget is the transition
    // function that decides which.
    private static List<List<ISSABasicBlock>> buildGroups(SSACFG cfg, Map<Integer, Kind> kindOf) {
        List<List<ISSABasicBlock>> groups = new ArrayList<>();
        Map<Integer, List<ISSABasicBlock>> groupOf = new HashMap<>(); // block number -> its group

        for (ISSABasicBlock bb : cfg) {
            List<ISSABasicBlock> accumulator = findMergeTarget(cfg, bb, groupOf, kindOf);
            if (accumulator != null) {          // stay in the accumulator (start --INSTR--> start)
                accumulator.add(bb);
                groupOf.put(bb.getNumber(), accumulator);
            } else {                            // open a fresh group (SplitBB --> start)
                List<ISSABasicBlock> newGroup = new ArrayList<>();
                newGroup.add(bb);
                groups.add(newGroup);
                groupOf.put(bb.getNumber(), newGroup);
            }
        }
        return groups;
    }

    // The NFA's transition function: returns the accumulator group `bb` continues, or null
    // if `bb` must start a fresh group. `bb` continues only when ALL three tests pass:
    private static List<ISSABasicBlock> findMergeTarget(
            SSACFG cfg, ISSABasicBlock bb,
            Map<Integer, List<ISSABasicBlock>> groupOf, Map<Integer, Kind> kindOf) {

        // (a) structural isolation: not a boundary block, and doesn't itself branch
        //     (real if/while/for conditions have >1 successor, isolating them here).
        if (bb.isEntryBlock() || bb.isExitBlock() || bb.isCatchBlock()) return null;
        if (cfg.getNormalSuccessors(bb).size() > 1) return null;

        // (b) NFA keyword override: a keyword block never folds into the accumulator - this
        //     is what isolates a for-loop's init/step blocks, which look structurally
        //     mergeable but semantically belong to the for(...) header.
        if (kindOf.get(bb.getNumber()) != Kind.INSTR) return null;

        // (c) single non-branching, non-keyword predecessor: otherwise bb is a join point,
        //     or its predecessor already closed as a keyword group (SplitBB starts fresh).
        Collection<ISSABasicBlock> preds = cfg.getNormalPredecessors(bb);
        if (preds.size() != 1) return null;
        ISSABasicBlock pred = preds.iterator().next();
        if (cfg.getNormalSuccessors(pred).size() != 1) return null;
        if (kindOf.get(pred.getNumber()) != Kind.INSTR) return null;

        return groupOf.get(pred.getNumber());
    }

    private static Map<Integer, Integer> indexByBlockNumber(List<List<ISSABasicBlock>> groups) {
        Map<Integer, Integer> index = new HashMap<>();
        for (int g = 0; g < groups.size(); g++) {
            for (ISSABasicBlock bb : groups.get(g)) index.put(bb.getNumber(), g);
        }
        return index;
    }

    // Print one merged node: its label line, its source line(s), then its outgoing edges.
    private static void printGroup(
            List<ISSABasicBlock> group,
            SSACFG cfg,
            List<List<ISSABasicBlock>> groups,
            Map<Integer, Integer> blockToGroup,
            Map<Integer, List<ResolvedLine>> blockLines,
            Map<Integer, Kind> kindOf) {

        System.out.println(label(group, kindOf, cfg));

        // Toggle on to also print each member block's raw SSA phi/instructions under the label:
//        for (ISSABasicBlock bb : group) {
//            Iterator<SSAPhiInstruction> phis = bb.iteratePhis();
//            while (phis.hasNext()) System.out.println("    " + phis.next());
//            for (SSAInstruction inst : bb) {
//                if (inst != null) System.out.println("    " + inst);
//            }
//        }

        printSourceLines(group, blockLines);
        printEdges(group, cfg, groups, blockToGroup, kindOf);
    }

    // The group's source line(s), deduped across the WHOLE group (not per member block) -
    // this is what collapses a single source line that a call/invokedynamic split across
    // several low-level WALA blocks back into one printed line.
    private static void printSourceLines(
            List<ISSABasicBlock> group, Map<Integer, List<ResolvedLine>> blockLines) {
        int lastLine = -1;
        for (ISSABasicBlock bb : group) {
            for (ResolvedLine rl : blockLines.get(bb.getNumber())) {
                if (rl.lineNumber() == lastLine) continue;
                lastLine = rl.lineNumber();
                System.out.println("    src (line " + rl.lineNumber() + ")  " + rl.text());
            }
        }
    }

    // Edges to each external successor group, each tagged "T"/"F" when it leaves a real
    // conditional branch (a branching block is always its own singleton group - see
    // findMergeTarget - so at most one member here is the block being branched from).
    private static void printEdges(
            List<ISSABasicBlock> group,
            SSACFG cfg,
            List<List<ISSABasicBlock>> groups,
            Map<Integer, Integer> blockToGroup,
            Map<Integer, Kind> kindOf) {
        Map<Integer, String> externalGroupTag = new LinkedHashMap<>();
        for (ISSABasicBlock bb : group) {
            Collection<ISSABasicBlock> succs = cfg.getNormalSuccessors(bb);
            Integer trueBlockNumber = succs.size() > 1 ? trueTargetBlockNumber(bb, cfg) : null;
            for (ISSABasicBlock succ : succs) {
                if (group.contains(succ)) continue;
                int gid = blockToGroup.get(succ.getNumber());
                String tag = trueBlockNumber == null ? null : (succ.getNumber() == trueBlockNumber ? "T" : "F");
                externalGroupTag.putIfAbsent(gid, tag);
            }
        }
        System.out.print("  -> ");
        for (Map.Entry<Integer, String> entry : externalGroupTag.entrySet()) {
            String prefix = entry.getValue() != null ? entry.getValue() + ":" : "";
            System.out.print(prefix + label(groups.get(entry.getKey()), kindOf, cfg) + " ");
        }
        System.out.println();
    }

    // The block that a conditional branch's true edge jumps to, from the branch's "iindex"
    // target - lets the two normal successors be told apart as true (jumps there) vs. false
    // (falls through). Note this is the compiled comparator's BYTECODE-LITERAL truth, not the
    // source condition's polarity (javac often emits the negated test). Returns null if the
    // block's last instruction isn't actually a conditional branch.
    private static Integer trueTargetBlockNumber(ISSABasicBlock bb, SSACFG cfg) {
        SSAInstruction last = null;
        for (SSAInstruction inst : bb) if (inst != null) last = inst;
        if (!(last instanceof SSAConditionalBranchInstruction cond)) return null;
        return cfg.getBlockForInstruction(cond.getTarget()).getNumber();
    }

    private static String label(List<ISSABasicBlock> group, Map<Integer, Kind> kindOf, SSACFG cfg) {
        StringBuilder sb = new StringBuilder();
        for (ISSABasicBlock bb : group) {
            if (sb.length() > 0) sb.append("+");
            sb.append("BB").append(bb.getNumber());
        }
        ISSABasicBlock first = group.get(0);
        if (first.isEntryBlock()) sb.append("  [ENTRY]");
        else if (first.isExitBlock()) sb.append("  [EXIT]");
        else if (first.isCatchBlock()) sb.append("  [CATCH]");
        else {
            Kind kind = kindOf.get(first.getNumber());
            if (kind == Kind.FOR) sb.append("  [").append(forRole(first, cfg)).append("]");
            else if (kind != Kind.INSTR) sb.append("  [").append(kind).append("]");
        }
        return sb.toString();
    }

    // Sub-classifies a FOR-labeled block by its structural role, since text alone can't tell
    // init/cond/step apart (all three resolve to the same "for(...)" source line): >1 successor
    // -> the condition check; otherwise a HEURISTIC on WALA's block numbering distinguishes
    // init (successor forward, into the loop) from step (successor is a back-edge to the cond).
    private static String forRole(ISSABasicBlock bb, SSACFG cfg) {
        Collection<ISSABasicBlock> succs = cfg.getNormalSuccessors(bb);
        if (succs.size() > 1) return "FOR_COND";
        if (succs.isEmpty()) return "FOR";
        return succs.iterator().next().getNumber() <= bb.getNumber() ? "FOR_STEP" : "FOR_INIT";
    }

    // SSA instruction index -> bytecode offset (getBytecodeIndex) -> source line (getLineNumber).
    private static int bytecodeLine(IBytecodeMethod<?> method, int instructionIndex) {
        try {
            int bcIndex = method.getBytecodeIndex(instructionIndex);
            return bcIndex < 0 ? -1 : method.getLineNumber(bcIndex);
        } catch (Exception e) {
            return -1;
        }
    }

    // class name -> .java path under sourceDir -> its lines, read once.
    private static String[] loadSource(IClass klass, String sourceDir) {
        try {
            String slashName = klass.getName().toString().substring(1); // drop leading "L"
            Path path = Paths.get(sourceDir, slashName + ".java");
            if (!Files.exists(path)) return new String[0];
            return Files.readAllLines(path).toArray(new String[0]);
        } catch (IOException e) {
            return new String[0];
        }
    }
}