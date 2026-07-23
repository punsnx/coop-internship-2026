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

Also exposes `dumpSymbolTableDiagnostics()` — an optional debug dump of every value number's constant/parameter/use-count status, not called by default.

*(Full source omitted here — see project file for the complete implementation of the three approaches.)*

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