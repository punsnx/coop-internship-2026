# Intraprocedural Liveness Analysis

## Functionality

This tool computes intraprocedural (single-method) liveness using a Kildall-style backward dataflow analysis over WALA's `ExplodedControlFlowGraph` (one SSA instruction per node). It reports, for every instruction, which value numbers are live-in and live-out — the basis for a downstream dead-store check.

It consists of two classes:

- **`IntraprocLiveness`** — the analysis engine. Builds the exploded CFG, defines the bit-vector transfer functions (gen/kill per value number), runs the fixpoint solve on the inverted (backward) graph, and formats results.
- **`LivenessAnalysisDriver`** — the entry point. Loads a class from a WALA analysis scope, locates `main`, builds its IR, and runs `IntraprocLiveness` against it.

## Input & Output

| | |
|---|---|
| **Input** | Path to a WALA analysis scope file, and (optionally) a path to source for line-text lookup |
| **Output** | Per-instruction dataflow report printed to stdout: source line (if resolvable), live-in set, and live-out set, as value numbers or resolved local names |

## Design Notes

- **Granularity**: `ExplodedControlFlowGraph` gives one instruction per node, so transfer functions don't need to reason about multiple instructions per basic block.
- **Direction**: liveness is a backward analysis, so the solve runs over `GraphInverter.invert(ecfg)` rather than the forward CFG.
- **Framework**: `BitVectorFramework` + `BitVectorSolver` implement the Kildall-style fixpoint iteration (one tier below IFDS — no call-string/path sensitivity, purely intraprocedural).
- **Transfer functions**: `BitVectorKillGen` encodes gen/kill per node. A normal instruction gens its uses and kills its def; `SSAPhiInstruction`s are handled separately since WALA doesn't return them from the ordinary instruction slot — their uses are genned and their def killed the same way.
- **Value-number domain**: built once in the constructor as an `OrdinalSetMapping<Integer>` over `1..maxValueNumber`, shared by the framework and by result rendering.
- **Source mapping**: like the bytecode CFG driver, line numbers are resolved via `IBytecodeMethod.getBytecodeIndex()` → `IMethod.getLineNumber()`, not AST positions.
- **Name resolution**: `renderLiveSet` tries to resolve a value number to its local variable name via `ir.getLocalNames()`; synthetic value numbers (exceptions, phis, unnamed temporaries) fall back to `v<n>`. The `namedOnly` flag (see below) suppresses those fallback entries entirely.

### `namedOnly` parameter

```java
liveness.printDataFlowFacts(solver, true, sourceLines);
```

The second parameter of `printDataFlowFacts` is `boolean namedOnly`:

| Value | Behavior |
|---|---|
| `true` | Only shows live values with a resolved local variable name (e.g. `x`, `y`, `sum`). Unnamed/synthetic value numbers (`v<n>` fallback entries — temporaries, phis, exceptions) are filtered out. |
| `false` | Shows all live values, including `v<n>` fallback entries alongside named locals. |

This is a rendering choice, not a soundness issue in the analysis: a real source variable that WALA could only resolve to a synthetic name (e.g. certain compiler temporaries) is also hidden under `namedOnly = true`.

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

    public IR getIR() { return ir; }
    public ExplodedControlFlowGraph getECFG() { return ecfg; }
    public OrdinalSetMapping<Integer> getValueNumberDomain() { return valueNumberDomain; }

    /** Optional: dump per-node dataflow facts. Not called by analyze() — caller opts in. */
    public void printDebugging(BitVectorSolver<IExplodedBasicBlock> solver) {
        System.out.println("=========================================================================");
        System.out.println("Data Flow Facts In Each Basic Block");
        System.out.println("=========================================================================");
        for (IExplodedBasicBlock ebb : ecfg) {
            System.out.println(ebb);
            System.out.println(ebb.getInstruction());
            System.out.println(solver.getOut(ebb));
            System.out.println(solver.getIn(ebb));
        }
        System.out.println("=========================================================================");
    }

    /** Prints per-instruction source line, live-in, and live-out sets. */
    public void printDataFlowFacts(BitVectorSolver<IExplodedBasicBlock> solver, boolean namedOnly, List<String> sourceLines) {
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
            System.out.println("  LIVE-IN:  " + renderLiveSet(solver.getOut(ebb), ir, instrIndex, namedOnly));
            System.out.println("  LIVE-OUT: " + renderLiveSet(solver.getIn(ebb), ir, instrIndex, namedOnly));
            System.out.println("");
        }
    }

    // ---------- Helpers ----------

    private String nameForValueNumber(IR ir, int instructionIndex, int valueNumber) {
        if (instructionIndex < 0) {
            return "v" + valueNumber; // entry/exit blocks have no instruction index
        }
        try {
            String[] names = ir.getLocalNames(instructionIndex, valueNumber);
            if (names != null && names.length > 0 && names[0] != null) {
                return names[0];
            }
        } catch (Exception e) {
            // synthetic value numbers (exceptions, phis) can throw or return nothing useful
        }
        return "v" + valueNumber;
    }

    private String renderLiveSet(BitVectorVariable bv, IR ir, int instructionIndex, boolean namedOnly) {
        if (bv == null || bv.getValue() == null) return "{ }";
        StringBuilder sb = new StringBuilder("{ ");
        IntIterator it = bv.getValue().intIterator();
        while (it.hasNext()) {
            int domainIdx = it.next();
            int vn = valueNumberDomain.getMappedObject(domainIdx);
            String name = nameForValueNumber(ir, instructionIndex, vn);
            if (namedOnly && name.startsWith("v") && name.substring(1).chars().allMatch(Character::isDigit)) {
                continue; // suppress unnamed fallback entries (e.g. receiver temporaries)
            }
            sb.append(name).append(" ");
        }
        sb.append("}");
        return sb.toString();
    }

    private Integer lineNumberForInstruction(IR ir, int instrIndex) {
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

    private String sourceLineText(Integer lineNumber, List<String> sourceLines) {
        if (lineNumber == null || sourceLines == null) return null;
        try {
            if (lineNumber < 1 || lineNumber > sourceLines.size()) return null;
            return sourceLines.get(lineNumber - 1).trim();
        } catch (Exception e) {
            // source file not found or line out of range
        }
        return null;
    }

    // ---------- Transfer functions ----------

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

            // 1. Normal instructions: gen uses, kill def
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

            // 2. Phi instructions: gen uses (values from incoming branches), kill def
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
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.Selector;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;

public class LivenessAnalysisDriver {

    public static void main(String[] args) throws IOException, ClassHierarchyException {
        String scopeFile = args[0];
        String sourceRoot = args.length > 1 ? args[1] : new File(scopeFile).getParent();
        File srcFile = new File(sourceRoot);
        List<String> sourceLines = Files.readAllLines(srcFile.toPath());

        // --- 1. Set up scope and build class hierarchy ---
        AnalysisScope scope =
                AnalysisScopeReader.instance.readJavaScope(
                        scopeFile,
                        new File(Objects.requireNonNull(
                                LivenessAnalysisDriver.class.getClassLoader()
                                        .getResource("Exclusions.txt")).getFile()),
                        LivenessAnalysisDriver.class.getClassLoader());

        IClassHierarchy cha = ClassHierarchyFactory.make(scope);

        IClass targetClass = findSingleApplicationClass(cha);

        IMethod method = targetClass.getMethod(Selector.make("main([Ljava/lang/String;)V"));

        // --- 2. Build IR ---
        AnalysisCache cache = new AnalysisCacheImpl();
        IR ir = cache.getIR(method, Everywhere.EVERYWHERE);

        // --- 3. Run liveness analysis and print results ---
        IntraprocLiveness liveness = new IntraprocLiveness(ir);
        BitVectorSolver<IExplodedBasicBlock> solver = liveness.analyze();
        liveness.printDataFlowFacts(solver, true, sourceLines);
    }

    /** Returns the first class found in the Application loader scope. */
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
./gradlew run -PmainClass=com.ibm.wala.examples.drivers.LivenessAnalysisDriver --args="path/to/scope.txt path/to/Main.java"
```

- `args[0]` — path to a WALA analysis scope file
- `args[1]` *(optional)* — path to the source file for line-text lookup; if omitted, falls back to the scope file's parent directory, which is unlikely to resolve to a real file
- The target class must be compiled to `.class` first (the scope file references bytecode, not source)

### Sample Input

`Main.java`

```java
public class Main {
    public static void main(String[] args) {
        int alive = compute();
        int dead = compute2();
        System.out.println(alive);
    }

    static int compute() { return 10; }
    static int compute2() { return 20; }
}
```

### Sample Output

```text
ExplodedBlock[0](entry:< Application, LMain, main([Ljava/lang/String;)V >)
null
  LIVE-IN:  { }
  LIVE-OUT: { }


ExplodedBlock[9](exit:< Application, LMain, main([Ljava/lang/String;)V >)
null
  LIVE-IN:  { }
  LIVE-OUT: { }


ExplodedBlock[1](original:BB[SSA:0..0]1 - Main.main([Ljava/lang/String;)V)
4 = invokestatic < Application, LMain, compute()I > @0 exception:3
  SOURCE:   line 3: int alive = compute();
  LIVE-IN:  { }
  LIVE-OUT: { }


ExplodedBlock[3](original:BB[SSA:1..2]2 - Main.main([Ljava/lang/String;)V)
6 = invokestatic < Application, LMain, compute2()I > @4 exception:5
  SOURCE:   line 4: int dead = compute2();
  LIVE-IN:  { alive }
  LIVE-OUT: { alive }


ExplodedBlock[5](original:BB[SSA:3..6]3 - Main.main([Ljava/lang/String;)V)
7 = getstatic < Application, Ljava/lang/System, out, <Application,Ljava/io/PrintStream> >
  SOURCE:   line 5: System.out.println(alive);
  LIVE-IN:  { alive }
  LIVE-OUT: { alive }


ExplodedBlock[7](original:BB[SSA:3..6]3 - Main.main([Ljava/lang/String;)V)
invokevirtual < Application, Ljava/io/PrintStream, println(I)V > 7,4 @12 exception:8
  SOURCE:   line 5: System.out.println(alive);
  LIVE-IN:  { alive }
  LIVE-OUT: { }


ExplodedBlock[8](original:BB[SSA:7..7]4 - Main.main([Ljava/lang/String;)V)
return
  SOURCE:   line 6: }
  LIVE-IN:  { }
  LIVE-OUT: { }
```

Note the block for `line 4: int dead = compute2();`: `dead` never appears in either LIVE-IN or LIVE-OUT — it's defined but never used, which is exactly the signal a downstream dead-store check looks for.

## Notes / Limitations

- **Target selection**: `findSingleApplicationClass` returns the *first* class found in the Application loader scope, not a named lookup — fine for single-class test scopes, but silently wrong if the scope contains multiple application classes.
- **`targetClass` and `method` are unchecked**: if `findSingleApplicationClass` returns `null` or the class has no `main([Ljava/lang/String;)V]`, this throws an unhandled `NullPointerException` rather than a descriptive error.
- **Debug output disabled**: `System.out.println(ir)` and `liveness.printDebugging(solver)` are available in `IntraprocLiveness` but not called by the driver — useful to re-enable when diagnosing unexpected liveness results.
- **Source-line dependency**: as with the bytecode CFG driver, line resolution depends on debug info in the compiled class; without it, `SOURCE:` lines are omitted.