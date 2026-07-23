# Dead Store Analysis

## Functionality

Detects dead stores (assignments whose value is never used) in a single method, built on top of `IntraprocLiveness`. Combines three complementary approaches to catch cases a single technique would miss:

1. **SSA/liveness-based** — for each defining instruction, checks if its value number is live-out; flags it if not.
2. **Bytecode store sweep** — walks raw bytecode `StoreInstruction`s and does a forward reachability check to see if a stored value is ever loaded before being overwritten or the method returns. Catches simple constant assignments that WALA's pruned SSA construction omits entirely (so Approach 1 never sees them).
3. **Parameter sweep** — flags unused method parameters via `DefUse.getNumberOfUses()`, off by default (`excludeParameters = true`).

Findings are deduplicated by `line:variableName` (or `param:name` for parameters) into a `LinkedHashMap`, preserving first-seen order.

## Input & Output

| | |
|---|---|
| **Input** | An `IntraprocLiveness` instance (already built from an `IR`) |
| **Output** | `List<DeadStoreFinding>` — each with variable name, line number (or `-1` if unresolved), value number (or `-1` for bytecode-only findings), and a description |

## Program Code

### `DeadStoreAnalysis.java`

Core class: `DeadStoreAnalysis`, constructed from an `IntraprocLiveness`. Main entry point:

```java
public List<DeadStoreFinding> findDeadStores(boolean excludeParameters)
```
```java
package com.ibm.wala.examples.analysis;

import com.ibm.wala.classLoader.IBytecodeMethod;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.dataflow.graph.BitVectorSolver;
import com.ibm.wala.examples.analysis.dataflow.IntraprocLiveness;
import com.ibm.wala.fixpoint.BitVectorVariable;
import com.ibm.wala.shrike.shrikeBT.*;
import com.ibm.wala.ssa.DefUse;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SymbolTable;
import com.ibm.wala.ssa.analysis.ExplodedControlFlowGraph;
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock;
import com.ibm.wala.util.intset.OrdinalSetMapping;

import java.util.*;

public class DeadStoreAnalysis {

    private final IntraprocLiveness liveness;

    public DeadStoreAnalysis(IntraprocLiveness liveness) {
        this.liveness = liveness;
    }

    public List<DeadStoreFinding> findDeadStores() {
        return findDeadStores(true);
    }

    public List<DeadStoreFinding> findDeadStores(boolean excludeParameters) {
        IR ir = liveness.getIR();
        ExplodedControlFlowGraph ecfg = liveness.getECFG();
        OrdinalSetMapping<Integer> valueNumberDomain = liveness.getValueNumberDomain();

        Map<String, DeadStoreFinding> findingsMap = new LinkedHashMap<>();
        BitVectorSolver<IExplodedBasicBlock> solver = liveness.analyze();

        // APPROACH 1: Instruction-Based Flow-Sensitive Analysis (SSA)
        for (IExplodedBasicBlock node : ecfg) {
            SSAInstruction instruction = node.getInstruction();
            if (instruction == null || !instruction.hasDef()) {
                continue;
            }

            int defVN = instruction.getDef();
            int idx = valueNumberDomain.getMappedIndex(defVN);
            if (idx == -1) {
                continue;
            }

            BitVectorVariable liveOut = solver.getIn(node);
            boolean isLive = liveOut.get(idx);

            if (!isLive) {
                int instrIndex = node.getFirstInstructionIndex();
                String varName = getVarName(ir, defVN);
                if (varName == null) {
                    varName = "v" + defVN;
                }

                int lineNumber = resolveLineNumber(ir, instrIndex);
                if (lineNumber != -1) {
                    String key = lineNumber + ":" + varName;
                    findingsMap.put(key, new DeadStoreFinding(varName, lineNumber, defVN, "Unused computation: " + instruction.toString()));
                }
            }
        }

        // APPROACH 2: Bytecode Store Sweep for Pruned Constants / Reassignments
        IMethod method = ir.getMethod();
        if (method instanceof IBytecodeMethod<?>) {
            IBytecodeMethod<?> bcMethod = (IBytecodeMethod<?>) method;
            try {
                Object[] rawInstructions = bcMethod.getInstructions();
                if (rawInstructions != null && rawInstructions.length > 0 && rawInstructions[0] instanceof IInstruction) {
                    IInstruction[] instructions = (IInstruction[]) rawInstructions;

                    for (int i = 0; i < instructions.length; i++) {
                        if (instructions[i] instanceof StoreInstruction) {
                            StoreInstruction store = (StoreInstruction) instructions[i];
                            int varIndex = store.getVarIndex();
                            int bcOffset = bcMethod.getBytecodeIndex(i);
                            int line = method.getLineNumber(bcOffset);

                            if (line == -1) continue;

                            int nextOffset = (i + 1 < instructions.length) ? bcMethod.getBytecodeIndex(i + 1) : bcOffset;
                            String varName = method.getLocalVariableName(nextOffset, varIndex);
                            if (varName == null) {
                                varName = method.getLocalVariableName(bcOffset, varIndex);
                            }

                            if (varName == null || "this".equals(varName)) continue;

                            if (isBytecodeStoreDead(instructions, i, varIndex)) {
                                String desc = "Unused local variable assignment";
                                if (i > 0 && instructions[i - 1] instanceof ConstantInstruction) {
                                    Object cVal = ((ConstantInstruction) instructions[i - 1]).getValue();
                                    desc = "Constant assignment (value: " + cVal + ")";
                                }

                                String key = line + ":" + varName;
                                findingsMap.putIfAbsent(key, new DeadStoreFinding(varName, line, -1, desc));
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }

        // APPROACH 3: Parameter Sweep
        Set<Integer> paramVNs = new HashSet<>();
        int[] params = ir.getParameterValueNumbers();
        if (params != null) {
            for (int p : params) {
                paramVNs.add(p);
            }
        }

        DefUse defUse = new DefUse(ir);
        for (int pVn : paramVNs) {
            if (excludeParameters) continue;
            if (defUse.getNumberOfUses(pVn) == 0) {
                String name = getVarName(ir, pVn);
                if (name != null && !"this".equals(name)) {
                    findingsMap.put("param:" + name, new DeadStoreFinding(name, -1, pVn, "Unused parameter"));
                }
            }
        }

        return new ArrayList<>(findingsMap.values());
    }

    private static boolean isBytecodeStoreDead(IInstruction[] instructions, int storeIdx, int targetVar) {
        Set<Integer> visited = new HashSet<>();
        Queue<Integer> queue = new LinkedList<>();
        queue.add(storeIdx + 1);

        while (!queue.isEmpty()) {
            int curr = queue.poll();
            if (curr < 0 || curr >= instructions.length || !visited.add(curr)) {
                continue;
            }

            IInstruction inst = instructions[curr];

            if (inst instanceof LoadInstruction) {
                LoadInstruction load = (LoadInstruction) inst;
                if (load.getVarIndex() == targetVar) {
                    return false; // Found a read on this path -> LIVE
                }
            } else if (inst instanceof StoreInstruction) {
                StoreInstruction st = (StoreInstruction) inst;
                if (st.getVarIndex() == targetVar) {
                    continue; // Overwritten before read -> Path DEAD
                }
            } else if (inst instanceof ReturnInstruction || inst instanceof ThrowInstruction) {
                continue; // Reached method termination -> Path DEAD
            }

            // Shrike branch targets ARE direct instruction array indices!
            int[] branchTargets = inst.getBranchTargets();
            if (branchTargets != null && branchTargets.length > 0) {
                for (int targetInstIdx : branchTargets) {
                    queue.add(targetInstIdx);
                }
            }

            if (!isUnconditionalJump(inst)) {
                queue.add(curr + 1);
            }
        }

        return true;
    }

    private static boolean isUnconditionalJump(IInstruction inst) {
        return inst instanceof GotoInstruction || inst instanceof ReturnInstruction || inst instanceof ThrowInstruction;
    }

    private static String getVarName(IR ir, int vn) {
        SSAInstruction[] instructions = ir.getInstructions();
        for (int i = 0; i < instructions.length; i++) {
            String[] names = ir.getLocalNames(i, vn);
            if (names != null) {
                for (String name : names) {
                    if (name != null) return name;
                }
            }
        }
        return null;
    }

    private static int resolveLineNumber(IR ir, int instrIndex) {
        if (instrIndex == -1) return -1;
        IMethod method = ir.getMethod();
        if (method instanceof IBytecodeMethod<?>) {
            try {
                int bcIndex = ((IBytecodeMethod<?>) method).getBytecodeIndex(instrIndex);
                return method.getLineNumber(bcIndex);
            } catch (Exception e) {
                return -1;
            }
        }
        return -1;
    }
    public void dumpSymbolTableDiagnostics() {
        IR ir = liveness.getIR();
        SymbolTable symbolTable = ir.getSymbolTable();
        DefUse defUse = new DefUse(ir);
        int maxVN = symbolTable.getMaxValueNumber();

        Set<Integer> paramVNs = new HashSet<>();
        int[] params = ir.getParameterValueNumbers();
        if (params != null) {
            for (int p : params) {
                paramVNs.add(p);
            }
        }

        System.out.println("=========================================================================");
        System.out.println("Symbol Table Diagnostic Dump (vn=1.." + maxVN + ")");
        System.out.println("=========================================================================");

        for (int vn = 1; vn <= maxVN; vn++) {
            boolean isConstant = symbolTable.isConstant(vn);
            Object constantValue = isConstant ? symbolTable.getConstantValue(vn) : null;
            boolean isParam = paramVNs.contains(vn);
            int uses = defUse.getNumberOfUses(vn);
            SSAInstruction def = defUse.getDef(vn);

            String resolvedName = null;
            int numInstructions = ir.getInstructions().length;
            for (int i = 0; i < numInstructions && resolvedName == null; i++) {
                String[] names = ir.getLocalNames(i, vn);
                if (names != null) {
                    for (String name : names) {
                        if (name != null) {
                            resolvedName = name;
                            break;
                        }
                    }
                }
            }

            System.out.printf(
                    "vn=%-3d isParam=%-5b isConstant=%-5b value=%-10s uses=%-2d hasDefInstruction=%-5b name=%s%n",
                    vn, isParam, isConstant,
                    (constantValue == null ? "-" : constantValue.toString()),
                    uses, (def != null),
                    (resolvedName == null ? "-" : resolvedName)
            );
        }

        System.out.println("=========================================================================");
    }

    public static class DeadStoreFinding {
        public final String variableName;
        public final int lineNumber;
        public final int valueNumber;
        public final String instructionString;

        public DeadStoreFinding(String variableName, int lineNumber, int valueNumber, String instructionString) {
            this.variableName = variableName;
            this.lineNumber = lineNumber;
            this.valueNumber = valueNumber;
            this.instructionString = instructionString;
        }

        @Override
        public String toString() {
            String lineStr = (lineNumber > 0) ? String.format("Line %-3d", lineNumber) : "N/A";
            return String.format("  %-8s | %-6s | %s",
                    lineStr,
                    "'" + variableName + "'",
                    instructionString);
        }
    }
}
```

Also exposes `dumpSymbolTableDiagnostics()` — an optional debug dump of every value number's constant/parameter/use-count status, not called by default.


### `DeadStoreDriver.java`

```java
package com.ibm.wala.examples.drivers;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.core.util.config.AnalysisScopeReader;
import com.ibm.wala.examples.analysis.DeadStoreAnalysis;
import com.ibm.wala.examples.analysis.dataflow.IntraprocLiveness;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisCacheImpl;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.Selector;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

public class DeadStoreDriver {
    public static void main(String[] args) throws IOException, ClassHierarchyException {
        String scopeFile = args[0];
        AnalysisScope scope =
                AnalysisScopeReader.instance.readJavaScope(
                        scopeFile,
                        new File(Objects.requireNonNull(
                                DeadStoreDriver.class.getClassLoader()
                                        .getResource("Exclusions.txt")).getFile()),
                        DeadStoreDriver.class.getClassLoader());

        IClassHierarchy cha = ClassHierarchyFactory.make(scope);
        IClass targetClass = findSingleApplicationClass(cha);
        IMethod method = targetClass.getMethod(Selector.make("main([Ljava/lang/String;)V"));

        AnalysisCache cache = new AnalysisCacheImpl();
        IR ir = cache.getIR(method, Everywhere.EVERYWHERE);

        IntraprocLiveness liveness = new IntraprocLiveness(ir);
        DeadStoreAnalysis analyzer = new DeadStoreAnalysis(liveness);

        List<DeadStoreAnalysis.DeadStoreFinding> findings = analyzer.findDeadStores(false);

        System.out.println("\nDead Store Analysis Report");
        System.out.println("Input: " + scopeFile);
        System.out.println("\nFound " + findings.size() + " dead store(s):");
        for (DeadStoreAnalysis.DeadStoreFinding finding : findings) {
            System.out.println("  " + finding);
        }
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
./gradlew run -PmainClass=com.ibm.wala.examples.drivers.DeadStoreDriver --args="path/to/scope.txt"
```

- `args[0]` — path to a WALA analysis scope file
- Driver calls `findDeadStores(false)`, i.e. parameter-sweep findings are **included**

## Notes

- Approach 2 (bytecode sweep) is why this tool catches cases the pure SSA/liveness approach misses — WALA's pruned SSA construction silently drops `SSAInstruction`s for simple constant assignments.
- `DeadStoreDriver` reuses the same `findSingleApplicationClass` pattern (and same limitation: first class found, no null check) as `LivenessAnalysisDriver`.