# Program Representation Graphs

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

A map of all possible execution paths through a program. Nodes are basic blocks, and edges represent possible transfers of control between blocks.

**Used by:** GCC, LLVM, WALA, virtually every compiler and static analysis tool

**Reference:** Allen, F. E. (1970). *Control flow analysis.* ACM SIGPLAN Notices. https://dl.acm.org/doi/10.1145/390013.808479

---

## 2. Data Flow Graph (DFG)

A graph that traces how values flow between operations. Nodes are operations or variables, and edges represent data dependencies — this value feeds into that operation.

**Used by:** LLVM, GCC, Intel compilers (for parallelization)

**Reference:** Aho, A. V., Lam, M. S., Sethi, R., & Ullman, J. D. (2006). *Compilers: Principles, Techniques, and Tools* (2nd ed.). https://www.pearson.com/en-us/subject-catalog/p/compilers-principles-techniques-and-tools/P200000003268

---

## 3. Call Graph

A map of which methods call which other methods. Nodes are methods, and edges represent calls between them.

**Used by:** WALA, LLVM, GCC, IntelliJ IDEA, Eclipse

**Reference:** Grove, D., & Chambers, C. (2001). *A framework for call graph construction algorithms.* ACM TOPLAS. https://dl.acm.org/doi/10.1145/504709.504710

---

## 4. Program Dependence Graph (PDG)

A graph that captures both control dependencies (this statement only runs because of that condition) and data dependencies (this statement uses a value produced by that statement) within a single method.

**Used by:** WALA, Joana, research tools for program slicing and debugging

**Reference:** Ferrante, J., Ottenstein, K. J., & Warren, J. D. (1987). *The program dependence graph and its use in optimization.* ACM TOPLAS. https://dl.acm.org/doi/10.1145/24039.24041

---

## 5. System Dependence Graph (SDG)

An extension of the PDG that connects the dependence graphs of multiple methods together through call and return edges, enabling interprocedural analysis.

**Used by:** Joana (information flow security), WALA

**Reference:** Horwitz, S., Reps, T., & Binkley, D. (1988). *Interprocedural slicing using dependence graphs.* ACM PLDI. https://dl.acm.org/doi/10.1145/53990.54003

---

## 6. Control Dependence Graph (CDG)

A graph that captures only control dependencies — which statements only execute because of which conditions. A subset of the PDG focusing purely on conditional control.

**Used by:** GCC, LLVM, WALA

**Reference:** Ferrante, J., Ottenstein, K. J., & Warren, J. D. (1987). *The program dependence graph and its use in optimization.* ACM TOPLAS. https://dl.acm.org/doi/10.1145/24039.24041

---

## 7. Static Single Assignment (SSA) Form

A program representation where every variable is assigned exactly once. Each new assignment creates a new version of the variable, and φ (phi) functions merge versions at control flow join points.

**Used by:** LLVM (core IR format), GCC, WALA, Java HotSpot JIT

**Reference:** Cytron, R., Ferrante, J., Rosen, B. K., Wegman, M. N., & Zadeck, F. K. (1991). *Efficiently computing static single assignment form and the control dependence graph.* ACM TOPLAS. https://dl.acm.org/doi/10.1145/115372.115320

---

## 8. Abstract Syntax Tree (AST)

A tree representation of the grammatical structure of source code. Nodes are operations or values, and edges represent the nesting structure of the program. Syntactic details like parentheses and semicolons are omitted.

**Used by:** Every compiler and IDE such as GCC, LLVM, IntelliJ IDEA, Eclipse, TypeScript compiler

**Reference:** Aho, A. V., Lam, M. S., Sethi, R., & Ullman, J. D. (2006). *Compilers: Principles, Techniques, and Tools* (2nd ed.). https://www.pearson.com/en-us/subject-catalog/p/compilers-principles-techniques-and-tools/P200000003268

---

## 9. Interprocedural Control Flow Graph (ICFG)

An extension of the CFG that connects execution paths across multiple methods using call edges (into called methods) and return edges (back to the caller).

**Used by:** WALA, Soot, LLVM

**Reference:** Reps, T., Horwitz, S., & Sagiv, M. (1995). *Precise interprocedural dataflow analysis via graph reachability.* ACM POPL. https://dl.acm.org/doi/10.1145/199448.199462

---

## 10. Heap Graph

A map of connections between objects in memory. Nodes are objects allocated on the heap, and edges represent references from one object to another.

**Used by:** WALA, Java garbage collector, memory profilers (YourKit, JProfiler)

**Reference:** Sagiv, M., Reps, T., & Wilhelm, R. (2002). *Parametric shape analysis via 3-valued logic.* ACM TOPLAS. https://dl.acm.org/doi/10.1145/570886.570890