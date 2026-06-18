# Program Representation Graphs in WALA (Draft)

---

## Table of Contents

1. [Control Flow Graph (CFG)](#1-control-flow-graph-cfg)
2. [Data Flow Graph (DFG)](#2-data-flow-graph-dfg)
3. [Call Graph](#3-call-graph)
4. [Program Dependence Graph (PDG)](#4-program-dependence-graph-pdg)
5. [System Dependence Graph (SDG)](#5-system-dependence-graph-sdg)
6. [Control Dependence Graph (CDG)](#6-control-dependence-graph-cdg)
7. [Static Single Assignment (SSA) Form](#7-static-single-assignment-ssa-form)
8. [Abstract Syntax Tree (AST)](#8-abstract-syntax-tree-ast)
9. [Interprocedural Control Flow Graph (ICFG)](#9-interprocedural-control-flow-graph-icfg)
10. [Heap Graph](#10-heap-graph)

---

## 1. Control Flow Graph (CFG)

**WALA Class:** `ControlFlowGraph` (interface), `SSACFG` (primary implementation)

- Javadoc: https://wala.github.io/javadoc/com/ibm/wala/cfg/ControlFlowGraph.html
- Javadoc: https://wala.github.io/javadoc/com/ibm/wala/ssa/SSACFG.html
- GitHub: https://github.com/wala/WALA/blob/master/core/src/main/java/com/ibm/wala/cfg/ControlFlowGraph.java
- GitHub: https://github.com/wala/WALA/blob/master/core/src/main/java/com/ibm/wala/ssa/SSACFG.java

---

## 2. Data Flow Graph (DFG)

**WALA Class:** WALA has no class named `DataFlowGraph`. Data flow is modeled through the Killdall dataflow framework (`IKilldallFramework` / `BasicFramework`) operating over a CFG, and through explicit flow graphs in the demand pointer analysis (e.g. `DemandPointerFlowGraph`).

- Javadoc: https://wala.github.io/javadoc/com/ibm/wala/dataflow/graph/BasicFramework.html
- Javadoc: https://wala.github.io/javadoc/com/ibm/wala/dataflow/graph/IKilldallFramework.html
- GitHub: https://github.com/wala/WALA/blob/master/core/src/main/java/com/ibm/wala/dataflow/graph/BasicFramework.java
- GitHub: https://github.com/wala/WALA/blob/master/core/src/main/java/com/ibm/wala/dataflow/graph/IKilldallFramework.java

---

## 3. Call Graph

**WALA Class:** `CallGraph` (interface), `BasicCallGraph` / `ExplicitCallGraph` (implementations)

- Javadoc: https://wala.github.io/javadoc/com/ibm/wala/ipa/callgraph/CallGraph.html
- Javadoc: https://wala.github.io/javadoc/com/ibm/wala/ipa/callgraph/impl/BasicCallGraph.html
- GitHub: https://github.com/wala/WALA/blob/master/core/src/main/java/com/ibm/wala/ipa/callgraph/CallGraph.java
- GitHub: https://github.com/wala/WALA/blob/master/core/src/main/java/com/ibm/wala/ipa/callgraph/impl/BasicCallGraph.java

---

## 4. Program Dependence Graph (PDG)

**WALA Class:** `PDG` (package `com.ibm.wala.ipa.slicer`)

- Javadoc: https://wala.github.io/javadoc/com/ibm/wala/ipa/slicer/PDG.html
- GitHub: https://github.com/wala/WALA/blob/master/core/src/main/java/com/ibm/wala/ipa/slicer/PDG.java

---

## 5. System Dependence Graph (SDG)

**WALA Class:** `SDG` (package `com.ibm.wala.ipa.slicer`)

- Javadoc: https://wala.github.io/javadoc/com/ibm/wala/ipa/slicer/SDG.html
- GitHub: https://github.com/wala/WALA/blob/master/core/src/main/java/com/ibm/wala/ipa/slicer/SDG.java

---

## 6. Control Dependence Graph (CDG)

**WALA Class:** `ControlDependenceGraph` (package `com.ibm.wala.cfg.cdg`)

- Javadoc: https://wala.github.io/javadoc/com/ibm/wala/cfg/cdg/ControlDependenceGraph.html
- GitHub: https://github.com/wala/WALA/blob/master/core/src/main/java/com/ibm/wala/cfg/cdg/ControlDependenceGraph.java

---

## 7. Static Single Assignment (SSA) Form

**WALA Class:** `IR` (the SSA intermediate representation), `SSACFG` (the CFG over SSA form). Phi nodes are represented by `SSAPhiInstruction`.

- Javadoc: https://wala.github.io/javadoc/com/ibm/wala/ssa/IR.html
- Javadoc: https://wala.github.io/javadoc/com/ibm/wala/ssa/SSACFG.html
- GitHub: https://github.com/wala/WALA/blob/master/core/src/main/java/com/ibm/wala/ssa/IR.java
- GitHub: https://github.com/wala/WALA/blob/master/core/src/main/java/com/ibm/wala/ssa/SSACFG.java

---

## 8. Abstract Syntax Tree (AST)

**WALA Class:** WALA's bytecode analysis works on the SSA `IR`, not an AST. For source-level frontends, WALA uses the CAst (Common AST) framework, with `CAstNode` as the core tree node.

- Javadoc: https://wala.github.io/javadoc/com/ibm/wala/cast/tree/CAstNode.html
- GitHub: https://github.com/wala/WALA/blob/master/cast/src/main/java/com/ibm/wala/cast/tree/CAstNode.java

---

## 9. Interprocedural Control Flow Graph (ICFG)

**WALA Class:** `InterproceduralCFG`, `ExplodedInterproceduralCFG` (package `com.ibm.wala.ipa.cfg`)

- Javadoc: https://wala.github.io/javadoc/com/ibm/wala/ipa/cfg/InterproceduralCFG.html
- Javadoc: https://wala.github.io/javadoc/com/ibm/wala/ipa/cfg/ExplodedInterproceduralCFG.html
- GitHub: https://github.com/wala/WALA/blob/master/core/src/main/java/com/ibm/wala/ipa/cfg/InterproceduralCFG.java
- GitHub: https://github.com/wala/WALA/blob/master/core/src/main/java/com/ibm/wala/ipa/cfg/ExplodedInterproceduralCFG.java

---

## 10. Heap Graph

**WALA Class:** `HeapGraph` (interface, `com.ibm.wala.ipa.callgraph.propagation`), `BasicHeapGraph` (implementation, `com.ibm.wala.analysis.pointers`)

- Javadoc: https://wala.github.io/javadoc/com/ibm/wala/ipa/callgraph/propagation/HeapGraph.html
- Javadoc: https://wala.github.io/javadoc/com/ibm/wala/analysis/pointers/BasicHeapGraph.html
- GitHub: https://github.com/wala/WALA/blob/master/core/src/main/java/com/ibm/wala/ipa/callgraph/propagation/HeapGraph.java
- GitHub: https://github.com/wala/WALA/blob/master/core/src/main/java/com/ibm/wala/analysis/pointers/BasicHeapGraph.java

---

## References

- WALA Javadoc (full API): https://wala.github.io/javadoc/
- WALA GitHub repository: https://github.com/wala/WALA
- WALA-start (example drivers): https://github.com/wala/WALA-start
- WALA Wiki - Intermediate Representation (IR): https://github.com/wala/WALA/wiki/Intermediate-Representation-(IR)
- WALA Wiki - Slicer (PDG/SDG usage): https://github.com/wala/WALA/wiki/Slicer