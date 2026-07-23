# Techniques Implementation — Tracing phase_02 into WALA and Other Tools

## Summary

| # | Technique | In WALA? | Real Implementation                                   |
|---|---|---|-------------------------------------------------------|
| 1 | Graph Register Allocation | No | HotSpot C2 (Chaitin-Briggs optimistic coloring)       |
| 2 | Strength Reduction | No | LLVM `LoopStrengthReduce` + `ScalarEvolution`         |
| 3 | Interval / Range Analysis | **Yes** — `com.ibm.wala.analysis.arraybounds` | -                                                     |
| 4 | May-Must Dual Analysis | No | -                                                     |                                                   |
| 5 | Integer Linear Programming (ILP) | No | LLVM Polly + isl                                      |
| 6 | Omega Test | No | Original Omega Project (Pugh, UMD); superseded by isl |

---

## Key Terms & Tools Reference

| Term | What It Is |
|------|-----------|
| **WALA** | IBM's Whole-Program Static Analysis framework for Java | 
| **HotSpot C2** | Oracle's server-tier JIT compiler for Java | 
| **LLVM** | Low-Level Virtual Machine compiler infrastructure | 
| **Polly** | LLVM's polyhedral loop optimizer | 
| **isl** | Integer Set Library; solves Presburger arithmetic | 
| **Chaitin-Briggs** | Graph-coloring register allocation algorithm |                                                   
| **Omega Test/Project** | Presburger arithmetic decision procedure (1990s) |                                       

---

## Table of Contents

- [1. Graph Register Allocation](#1-graph-register-allocation)
- [2. Strength Reduction](#2-strength-reduction)
- [3. Interval / Range Analysis](#3-interval--range-analysis)
- [4. May-Must Dual Analysis](#4-may-must-dual-analysis)
- [5. Integer Linear Programming (ILP)](#5-integer-linear-programming-ilp)
- [6. Omega Test](#6-omega-test)

---

## 1. Graph Register Allocation

**Briefly:** Assigns program variables to a fixed set of CPU registers by modeling variable interference as a graph and finding a valid **k-coloring** — no two variables that are live at the same time may share a register.

**In WALA?** No.
- The javadoc index for "register allocation," "graph coloring," or related keywords.

**Where it really lives — OpenJDK HotSpot C2 (server compiler):**
- Implements a **Chaitin-Briggs graph-coloring allocator** with **optimistic coloring**: build the interference graph, then attempt a k-coloring while deferring spill decisions as long as possible.

**References:**
- C2 architecture and Chaitin-Briggs algorithm [Wiki] — [OpenJDK HotSpot](https://wiki.openjdk.org/spaces/HotSpot/pages/11829273/C2+Register+Allocator+Notes)
- Not included in WALA [Javadoc] — [WALA index](https://wala.github.io/javadoc/index-all.html)

---

## 2. Strength Reduction

**Briefly:** Replaces an expensive operation (multiply, divide) inside a loop with a cheaper equivalent (add, shift) by exploiting how induction variables change between iterations.

**In WALA?** No.
- No "strength reduction" in the javadocs from WALA.

**Where it really lives — LLVM:**
- The **`LoopStrengthReduce` pass** works with `ScalarEvolution` to recognize induction expressions symbolically.
- Replaces loop-derived multiplies (like `i * 4` in array indexing) with cheaper forms:
  - scaled addressing modes, or
  - a running counter that increments by a constant each iteration.

**References:**
- LoopStrengthReduce implementation [Source code] — [LLVM Doxygen](https://llvm.org/doxygen/LoopStrengthReduce_8h_source.html)
- LLVM transform passes overview [Official documentation] — [LLVM docs](https://llvm.org/docs/Passes.html)
- Strength reduction technique explanation [Course blog] — [Cornell CS 6120](https://www.cs.cornell.edu/courses/cs6120/2019fa/blog/strength-reduction-pass-in-llvm/)

---

## 3. Interval / Range Analysis

**Briefly:** Uses abstract interpretation to track the possible value range `[lo, hi]` of each variable at each program point, so bounds-related properties can be decided without tracking exact values.

**In WALA?** Yes — a real win.
- Package: `com.ibm.wala.analysis.arraybounds`, implementing array bounds-checking via an **inequality graph**.
- Based directly on the *ABCD* paper (Bodík, Gupta & Sarkar, PLDI 2000).
- `ArrayBoundsGraphBuilder` builds the inequality graph from IR branch conditions; `ArrayOutOfBoundsAnalysis` solves it with a Bellman-Ford shortest-path approach over a hypergraph.

**References:**
- Array bounds analysis package [Javadoc] — [WALA package](https://wala.github.io/javadoc/com/ibm/wala/analysis/arraybounds/package-summary.html)
- ArrayOutOfBoundsAnalysis implementation [Javadoc] — [WALA class docs](https://wala.github.io/javadoc/com/ibm/wala/analysis/arraybounds/ArrayOutOfBoundsAnalysis.html)
- ABCD paper (range analysis foundation) [Academic paper] — [PLDI 2000](https://dl.acm.org/doi/10.1145/349299.349315)

---

## 4. May-Must Dual Analysis

**Briefly:** Tracks two complementary views of pointers: **may** (what *could* be true — conservative, catches bugs) and **must** (what's *definitely* true — strict, proves safety). Using both together gives you both bug-finding power and proof-of-correctness.

**In WALA?** No.

**References:**
- Original technique paper [Academic paper] — [Fink et al. ISSTA 2006](https://pages.cs.wisc.edu/~ramali/Papers/issta06.pdf)

---

## 5. Integer Linear Programming (ILP)

**Briefly:** Formulates a compiler optimization problem (scheduling, tiling, register allocation) as a system of linear constraints over integer variables and solves it for a provably optimal assignment, instead of relying on a greedy heuristic.

**In WALA?** No.
- No ILP solver or formulation anywhere in WALA.

**Where it really lives — Polly (LLVM's polyhedral loop optimizer):**
- Identifies a **SCoP \(Static Controll Part\)** (maximal affine loop region), models it as integer polyhedra.
- Delegates the scheduling problem to the **Integer Set Library (isl)** to solve the constraints.
- Produces schedules for fusion, tiling, and parallelization.

**References:**
- Polly optimizer overview [Official documentation] — [Polly project](https://polly.llvm.org/)

---

## 6. Omega Test

**Briefly:** A decision procedure for **Presburger arithmetic** (linear integer arithmetic) that determines whether a system of affine integer constraints has a solution — used to prove that two loop iterations touch disjoint data and can safely be reordered or parallelized.

**In WALA?** No.

**Where it really lives — the original Omega Project (William Pugh et al., University of Maryland):**

**References:**
- Omega Project home page [Project page] — [UMD Omega Project](https://www.cs.umd.edu/projects/omega/)
- Original Omega Test paper [Academic paper] — [Pugh CACM 1992](https://www.researchgate.net/publication/2772646_The_Omega_Test_a_fast_and_practical_integer_programming_algorithm_for_dependence_analysis)

---
