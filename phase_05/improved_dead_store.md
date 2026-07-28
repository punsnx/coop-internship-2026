# Complex Dead Store Analysis

## Functionality

Extends the basic dead store tool to handle realistic multi-class, multi-method Java input: nested classes, multiple methods per class, class fields, and method arguments. Where `DeadStoreDriver` analyzes a single `main` method, `ComplexDeadStoreDriver` walks **every application class and every declared method**, running `ComplexDeadStoreAnalysis` on each and aggregating results.

1. **SSA flow-sensitive liveness** — runs backward dataflow (`IntraprocLiveness.analyze()`) over the ECFG; any defined value number not in the solver's `liveOut` set is dead. Constants are formatted via `SymbolTable` (e.g. `myVar2 = 23`) instead of printed as raw SSA.

2. **Unused field sweep** — scans `SSAPutInstruction`s and checks each field against a pre-collected `globalReadFieldsCache`. Fields never read anywhere in the app are flagged as unused.

3. **Bytecode store fallback** — for stores SSA construction may have pruned, DFS-walks Shrike bytecode from each `StoreInstruction`: a reachable `LoadInstruction` on that variable means live; hitting an overwrite or return with no load means dead.

4. **Unused parameter check** — checks `DefUse.getNumberOfUses()` on each parameter value number; zero uses (excluding `this`) flags it, with its line resolved via `getMethodStartLineNumber()`.

5. **Deduplication** — findings go into a `LinkedHashMap` keyed by location (`"15:myVar2"`, `"param:myPar1"`), so SSA and bytecode passes flagging the same spot don't double-report. Result returned to `ComplexDeadStoreDriver` for formatting.
## Input & Output

| | |
|---|---|
| **Input** | A WALA analysis scope file covering one or more application classes |
| **Output** | A per-class, per-method dead store report printed to stdout, plus an aggregate total (dead stores / methods with findings / classes with findings) |

## Program Code

### `ComplexDeadStoreAnalysis.java`


```java
package com.ibm.wala.examples.analysis;

import com.ibm.wala.classLoader.IBytecodeMethod;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.dataflow.graph.BitVectorSolver;
import com.ibm.wala.examples.analysis.dataflow.IntraprocLiveness;
import com.ibm.wala.fixpoint.BitVectorVariable;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisCacheImpl;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.shrike.shrikeBT.*;
import com.ibm.wala.ssa.*;
import com.ibm.wala.ssa.analysis.ExplodedControlFlowGraph;
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.util.intset.OrdinalSetMapping;

import java.util.*;

public class ComplexDeadStoreAnalysis {

    private final IntraprocLiveness liveness;
    private final IClassHierarchy cha;
    private static Set<FieldReference> globalReadFieldsCache = null;

    public ComplexDeadStoreAnalysis(IntraprocLiveness liveness) {
        this(liveness, null);
    }

    public ComplexDeadStoreAnalysis(IntraprocLiveness liveness, IClassHierarchy cha) {
        this.liveness = liveness;
        this.cha = cha;
    }

    public List<DeadStoreFinding> findDeadStores() {
        return findDeadStores(false);
    }

    public List<DeadStoreFinding> findDeadStores(boolean excludeParameters) {
        IR ir = liveness.getIR();
        ExplodedControlFlowGraph ecfg = liveness.getECFG();
        OrdinalSetMapping<Integer> valueNumberDomain = liveness.getValueNumberDomain();

        Map<String, DeadStoreFinding> findingsMap = new LinkedHashMap<>();
        BitVectorSolver<IExplodedBasicBlock> solver = liveness.analyze();

        // APPROACH 1: Instruction-Based Flow-Sensitive Analysis (SSA Locals)
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
                    String formattedComputation = formatUnusedComputation(ir, instruction, varName);
                    String key = lineNumber + ":" + varName;
                    findingsMap.put(key, new DeadStoreFinding(
                            varName, lineNumber, defVN, "Unused computation: " + formattedComputation
                    ));
                }
            }
        }

        // APPROACH 2: Field Assignment Sweep (putfield / putstatic in <init>, <clinit>, or methods)
        Set<FieldReference> readFields = getOrCollectReadFields(cha);
        for (IExplodedBasicBlock node : ecfg) {
            SSAInstruction instruction = node.getInstruction();
            if (instruction instanceof SSAPutInstruction) {
                SSAPutInstruction putInst = (SSAPutInstruction) instruction;
                FieldReference fieldRef = putInst.getDeclaredField();
                String fieldName = fieldRef.getName().toString();

                // Filter out synthetic compiler fields (e.g., 'this$0' in inner class constructors)
                if (fieldName.startsWith("this$") || fieldName.contains("$")) {
                    continue;
                }

                if (!readFields.contains(fieldRef)) {
                    int instrIndex = node.getFirstInstructionIndex();
                    int lineNumber = resolveLineNumber(ir, instrIndex);
                    if (lineNumber != -1) {
                        String key = lineNumber + ":" + fieldName;
                        findingsMap.putIfAbsent(key, new DeadStoreFinding(
                                fieldName, lineNumber, -1, "Unused field (never read)"
                        ));
                    }
                }
            }
        }

        // APPROACH 3: Bytecode Store Sweep for Pruned Constants / Reassignments
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
                                    desc = "Unused computation: " + varName + " = " + cVal;
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

        // APPROACH 4: Parameter Sweep
        if (!excludeParameters) {
            Set<Integer> paramVNs = new HashSet<>();
            int[] params = ir.getParameterValueNumbers();
            if (params != null) {
                for (int p : params) {
                    paramVNs.add(p);
                }
            }

            int methodStartLine = getMethodStartLineNumber(ir);
            DefUse defUse = new DefUse(ir);
            for (int pVn : paramVNs) {
                if (defUse.getNumberOfUses(pVn) == 0) {
                    String name = getVarName(ir, pVn);
                    // Ignore 'this' and synthetic outer class parameters (e.g., 'this$0')
                    if (name != null && !"this".equals(name) && !name.startsWith("this$")) {
                        findingsMap.put("param:" + name, new DeadStoreFinding(
                                name, methodStartLine, pVn, "Unused parameter"
                        ));
                    }
                }
            }
        }

        return new ArrayList<>(findingsMap.values());
    }

    private static String formatUnusedComputation(IR ir, SSAInstruction instruction, String varName) {
        SymbolTable symbolTable = ir.getSymbolTable();
        if (instruction.getNumberOfUses() > 0) {
            int useVn = instruction.getUse(0);
            if (symbolTable.isConstant(useVn)) {
                Object cVal = symbolTable.getConstantValue(useVn);
                return varName + " = " + cVal;
            }
        }
        return instruction.toString();
    }

    private static int getMethodStartLineNumber(IR ir) {
        SSAInstruction[] insts = ir.getInstructions();
        for (int i = 0; i < insts.length; i++) {
            int line = resolveLineNumber(ir, i);
            if (line != -1) return line;
        }
        IMethod m = ir.getMethod();
        if (m instanceof IBytecodeMethod<?>) {
            try {
                int line = ((IBytecodeMethod<?>) m).getLineNumber(0);
                if (line != -1) return line;
            } catch (Exception ignored) {}
        }
        return -1;
    }

    private static Set<FieldReference> getOrCollectReadFields(IClassHierarchy cha) {
        if (cha == null) return Collections.emptySet();
        if (globalReadFieldsCache != null) return globalReadFieldsCache;

        Set<FieldReference> readFields = new HashSet<>();
        AnalysisCache cache = new AnalysisCacheImpl();

        for (IClass c : cha) {
            if (c.getClassLoader().getReference().equals(ClassLoaderReference.Application)) {
                for (IMethod m : c.getDeclaredMethods()) {
                    if (m.isAbstract() || m.isNative()) continue;
                    try {
                        IR ir = cache.getIR(m, Everywhere.EVERYWHERE);
                        if (ir == null) continue;
                        for (SSAInstruction inst : ir.getInstructions()) {
                            if (inst instanceof SSAGetInstruction) {
                                readFields.add(((SSAGetInstruction) inst).getDeclaredField());
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }
        }

        globalReadFieldsCache = readFields;
        return globalReadFieldsCache;
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
                    return false;
                }
            } else if (inst instanceof StoreInstruction) {
                StoreInstruction st = (StoreInstruction) inst;
                if (st.getVarIndex() == targetVar) {
                    continue;
                }
            } else if (inst instanceof ReturnInstruction || inst instanceof ThrowInstruction) {
                continue;
            }

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

    public static class DeadStoreFinding {
        private final String variableName;
        private final int lineNumber;
        private final int valueNumber;
        private final String description;

        public DeadStoreFinding(String variableName, int lineNumber, int valueNumber, String description) {
            this.variableName = variableName;
            this.lineNumber = lineNumber;
            this.valueNumber = valueNumber;
            this.description = description;
        }

        public String getVariableName() {
            return variableName;
        }

        public int getLineNumber() {
            return lineNumber;
        }

        public int getValueNumber() {
            return valueNumber;
        }

        public String getDescription() {
            return description;
        }

        public String getInstructionString() {
            return description;
        }

        @Override
        public String toString() {
            String lineStr = (lineNumber > 0) ? String.format("Line %-3d", lineNumber) : "N/A";
            return String.format("  %-8s | %-13s | %s",
                    lineStr,
                    "'" + variableName + "'",
                    description);
        }
    }
}
```

`DeadStoreFinding` is now a proper value object with getters (`getVariableName()`, `getLineNumber()`, `getValueNumber()`, `getDescription()`) rather than public final fields, to support the driver's formatted output.


### `ComplexDeadStoreDriver.java`

```java
package com.ibm.wala.examples.drivers;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.core.util.config.AnalysisScopeReader;
import com.ibm.wala.examples.analysis.ComplexDeadStoreAnalysis;
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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class ComplexDeadStoreDriver {

    public static void main(String[] args) throws IOException, ClassHierarchyException {
        if (args.length < 1) {
            System.err.println("Usage: ComplexDeadStoreDriver <scope-file> [excludeParams (true|false)]");
            return;
        }
        String scopeFile = args[0];

        // Parse optional excludeParams argument (defaults to false)
        boolean excludeParams = false;
        if (args.length >= 2) {
            excludeParams = Boolean.parseBoolean(args[1]) || "--exclude-params".equalsIgnoreCase(args[1]);
        }

        AnalysisScope scope = AnalysisScopeReader.instance.readJavaScope(
                scopeFile,
                new File(Objects.requireNonNull(
                        ComplexDeadStoreDriver.class.getClassLoader()
                                .getResource("Exclusions.txt")).getFile()),
                ComplexDeadStoreDriver.class.getClassLoader());

        IClassHierarchy cha = ClassHierarchyFactory.make(scope);
        List<IClass> targetClasses = findAllApplicationClasses(cha);

        // Sort classes alphabetically so outer/nested classes render predictably
        targetClasses.sort(Comparator.comparing(c -> c.getName().toString()));

        AnalysisCache cache = new AnalysisCacheImpl();

        int totalDeadStores = 0;
        int totalMethodsWithFindings = 0;
        int totalClassesWithFindings = 0;

        System.out.println("Dead Store Analysis Report");
        System.out.println("Input: " + new File(scopeFile).getName());
        System.out.println("Exclude Parameters: " + excludeParams + "\n");

        for (IClass klass : targetClasses) {
            boolean classHasFindings = false;
            StringBuilder classOutput = new StringBuilder();

            // Clean class name: Lcom/example/Main$MyNestedClass -> com.example.Main$MyNestedClass
            String className = klass.getName().toString();
            if (className.startsWith("L")) className = className.substring(1);
            className = className.replace('/', '.');
            classOutput.append("Class: ").append(className).append("\n");

            // Sort methods so <clinit> and <init> appear before regular methods
            List<IMethod> methods = new ArrayList<>(klass.getDeclaredMethods());
            methods.sort(Comparator.comparing(m -> m.getName().toString()));

            for (IMethod method : methods) {
                if (method.isAbstract() || method.isNative()) continue;

                IR ir = cache.getIR(method, Everywhere.EVERYWHERE);
                if (ir == null) continue;

                IntraprocLiveness liveness = new IntraprocLiveness(ir);
                ComplexDeadStoreAnalysis analyzer = new ComplexDeadStoreAnalysis(liveness, cha);

                List<ComplexDeadStoreAnalysis.DeadStoreFinding> findings = analyzer.findDeadStores(excludeParams);

                if (!findings.isEmpty()) {
                    classHasFindings = true;
                    totalMethodsWithFindings++;
                    totalDeadStores += findings.size();

                    String methodName = method.getName().toString();
                    classOutput.append("  Method: ").append(methodName).append("\n");

                    for (ComplexDeadStoreAnalysis.DeadStoreFinding finding : findings) {
                        classOutput.append(String.format("    Line %-2d | '%-13s' | %s\n",
                                finding.getLineNumber(),
                                finding.getVariableName(),
                                finding.getDescription()));
                    }
                    classOutput.append("  Found ").append(findings.size())
                            .append(" dead store(s) in ").append(methodName).append("\n\n");
                }
            }

            if (classHasFindings) {
                totalClassesWithFindings++;
                System.out.print(classOutput.toString());
            }
        }

        System.out.println("=========================================================================");
        System.out.printf("Total: %d dead store(s) found across %d method(s) in %d class(es)\n",
                totalDeadStores, totalMethodsWithFindings, totalClassesWithFindings);
        System.out.println("=========================================================================");
    }

    private static List<IClass> findAllApplicationClasses(IClassHierarchy cha) {
        List<IClass> appClasses = new ArrayList<>();
        for (IClass c : cha) {
            if (c.getClassLoader().getReference().equals(ClassLoaderReference.Application)) {
                appClasses.add(c);
            }
        }
        return appClasses;
    }
}
```

## Run Instructions

```bash
./gradlew run -PmainClass=com.ibm.wala.examples.drivers.ComplexDeadStoreDriver --args="path/to/scope.txt"
```

```bash
./gradlew run -PmainClass=com.ibm.wala.examples.drivers.ComplexDeadStoreDriver --args="path/to/scope.txt --exclude-params"
```

- `args[0]` — path to a WALA analysis scope file covering the application classes to analyze
- `args[1]` — --exclude-params decide to track params as dead store or not

### Sample Output Shape

```text
Dead Store Analysis Report
Input: scopeFile.txt

Class: com.example.Main
  Method: helper
    Line 12 | 'temp'        | Unused computation: temp = 5
  Found 1 dead store(s) in helper

Class: com.example.Main$Nested
  Method: <init>
    Line 20 | 'unusedField' | Unused field (never read)
  Found 1 dead store(s) in <init>

=========================================================================
Total: 2 dead store(s) found across 2 method(s) in 2 class(es)
=========================================================================
```

## Notes / Limitations

- **`method.isAbstract() || method.isNative()`** skip means interface methods and native methods produce no findings, silently — expected, but worth remembering if a class's finding count looks lower than anticipated.
- **`cache.getIR(method, Everywhere.EVERYWHERE)` returning `null`** is also silently skipped (`if (ir == null) continue;`), with no logged reason.
- **Field-read cache is static and never invalidated**: `globalReadFieldsCache` persists for the process lifetime. Fine for a single driver run, but would give stale results if `ComplexDeadStoreAnalysis` were reused across multiple distinct scopes in the same JVM.
