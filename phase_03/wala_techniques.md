# Static Analysis Techniques in WALA (Draft)

---

## Table of Contents

1. [Constant Propagation](#1-constant-propagation)
2. [Sign Analysis](#2-sign-analysis)
3. [Reaching Definitions](#3-reaching-definitions)
4. [Live Variable Analysis](#4-live-variable-analysis)
5. [Available Expressions](#5-available-expressions)
6. [Taint Analysis](#6-taint-analysis)
7. [Points-to Analysis](#7-points-to-analysis)
8. [Type Inference](#8-type-inference)
9. [Null Pointer Analysis](#9-null-pointer-analysis)
10. [Interval Analysis](#10-interval-analysis)

---

## 1. Constant Propagation

**WALA Class:** No dedicated class. Constants are tracked in the SSA IR via `SymbolTable` (constant value numbers). A constant-propagation analysis can be built on the dataflow framework (`BasicFramework`).

- Javadoc: https://wala.github.io/javadoc/com/ibm/wala/ssa/SymbolTable.html
- Javadoc: https://wala.github.io/javadoc/com/ibm/wala/dataflow/graph/BasicFramework.html
- GitHub: https://github.com/wala/WALA/blob/master/core/src/main/java/com/ibm/wala/ssa/SymbolTable.java
- GitHub: https://github.com/wala/WALA/blob/master/core/src/main/java/com/ibm/wala/dataflow/graph/BasicFramework.java

---

## 2. Sign Analysis

**WALA Class:** No dedicated class. Implementable as a forward dataflow analysis over a CFG using the Killdall framework (`IKilldallFramework`).

- Javadoc: https://wala.github.io/javadoc/com/ibm/wala/dataflow/graph/IKilldallFramework.html
- GitHub: https://github.com/wala/WALA/blob/master/core/src/main/java/com/ibm/wala/dataflow/graph/IKilldallFramework.java

---

## 3. Reaching Definitions

**WALA Class:** Built on the IFDS tabulation solver (`TabulationSolver`). with a working example, `ContextSensitiveReachingDefs` in WALA-start.

- Javadoc: https://wala.github.io/javadoc/com/ibm/wala/dataflow/IFDS/TabulationSolver.html
- GitHub (solver): https://github.com/wala/WALA/blob/master/core/src/main/java/com/ibm/wala/dataflow/IFDS/TabulationSolver.java
- GitHub (example): https://github.com/wala/WALA-start/blob/master/src/main/java/com/ibm/wala/examples/analysis/dataflow/ContextSensitiveReachingDefs.java

---

## 4. Live Variable Analysis

**WALA Class:** No dedicated class. Implementable as a backward dataflow analysis over a CFG using the Killdall framework.

- Javadoc: https://wala.github.io/javadoc/com/ibm/wala/dataflow/graph/BasicFramework.html
- GitHub: https://github.com/wala/WALA/blob/master/core/src/main/java/com/ibm/wala/dataflow/graph/BasicFramework.java

---

## 5. Available Expressions

**WALA Class:** No dedicated class. Implementable as a forward dataflow analysis over a CFG using the Killdall framework.

- Javadoc: https://wala.github.io/javadoc/com/ibm/wala/dataflow/graph/BasicFramework.html
- GitHub: https://github.com/wala/WALA/blob/master/core/src/main/java/com/ibm/wala/dataflow/graph/BasicFramework.java

---

## 6. Taint Analysis

**WALA Class:** No dedicated class. Taint analysis is implemented on top of WALA's call graph, pointer analysis, and the IFDS tabulation solver. WALA-based taint analysis is well documented in the WALA tutorial and IBM's AppScan taint analysis was built on WALA.

- Javadoc (IFDS solver): https://wala.github.io/javadoc/com/ibm/wala/dataflow/IFDS/TabulationSolver.html
- GitHub (IFDS solver): https://github.com/wala/WALA/blob/master/core/src/main/java/com/ibm/wala/dataflow/IFDS/TabulationSolver.java
- Reference (TAJ / AppScan): Tripp et al., "TAJ: Effective Taint Analysis of Web Applications", PLDI 2009. https://doi.org/10.1145/1542476.1542486

---

## 7. Points-to Analysis

**WALA Class:** `PointerAnalysis` (interface), `PointerAnalysisImpl` (implementation), and `DemandRefinementPointsTo` (demand-driven variant in `com.ibm.wala.demandpa`).

- Javadoc: https://wala.github.io/javadoc/com/ibm/wala/ipa/callgraph/propagation/PointerAnalysis.html
- Javadoc: https://wala.github.io/javadoc/com/ibm/wala/ipa/callgraph/propagation/PointerAnalysisImpl.html
- Javadoc: https://wala.github.io/javadoc/com/ibm/wala/demandpa/alg/DemandRefinementPointsTo.html
- GitHub: https://github.com/wala/WALA/blob/master/core/src/main/java/com/ibm/wala/ipa/callgraph/propagation/PointerAnalysis.java
- GitHub: https://github.com/wala/WALA/blob/master/core/src/main/java/com/ibm/wala/ipa/callgraph/propagation/PointerAnalysisImpl.java

---

## 8. Type Inference

**WALA Class:** `TypeInference` (package `com.ibm.wala.analysis.typeInference`) which is intraprocedural type inference over the SSA form.

- Javadoc: https://wala.github.io/javadoc/com/ibm/wala/analysis/typeInference/TypeInference.html
- GitHub: https://github.com/wala/WALA/blob/master/core/src/main/java/com/ibm/wala/analysis/typeInference/TypeInference.java

---

## 9. Null Pointer Analysis

**WALA Class:** WALA has a dedicated null-pointer analysis package, `com.ibm.wala.analysis.nullpointer`, used by the CFG exception-pruning machinery (`com.ibm.wala.cfg.exc`). Related null/exception reasoning is available in `IntraproceduralExceptionAnalysis` and the pointer analysis.

- Javadoc (package): https://wala.github.io/javadoc/com/ibm/wala/analysis/nullpointer/package-summary.html
- Javadoc: https://wala.github.io/javadoc/com/ibm/wala/analysis/exceptionanalysis/IntraproceduralExceptionAnalysis.html
- GitHub (package): https://github.com/wala/WALA/tree/master/core/src/main/java/com/ibm/wala/analysis/nullpointer

---

## 10. Interval Analysis

**WALA Class:** WALA implements an interval-style analysis for array-bounds checking in the `com.ibm.wala.analysis.arraybounds` package, based on `ArrayBoundsGraph`.

- Javadoc: https://wala.github.io/javadoc/com/ibm/wala/analysis/arraybounds/ArrayBoundsGraph.html
- GitHub: https://github.com/wala/WALA/blob/master/core/src/main/java/com/ibm/wala/analysis/arraybounds/ArrayBoundsGraph.java

---

## References

- WALA Javadoc: https://wala.github.io/javadoc/
- WALA GitHub repository: https://github.com/wala/WALA
- WALA-start (example drivers): https://github.com/wala/WALA-start
- WALA Wiki - Intermediate Representation (IR): https://github.com/wala/WALA/wiki/Intermediate-Representation-(IR)
- WALA dataflow framework (Killdall): package `com.ibm.wala.dataflow.graph`
- WALA IFDS/IDE solver: package `com.ibm.wala.dataflow.IFDS`
- IBM "Program Analysis using WALA" tutorial (ESEC/FSE 2022): https://research.ibm.com/publications/program-analysis-using-wala