# Techniques used in compiler

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

**What it is:** Assign program variables to CPU registers by modeling variable interference as a graph and finding a valid **k-coloring** (where **k** is the number of available registers).

**Key idea:** Build an interference graph where nodes are variables and edges connect variables that are live simultaneously. If two variables never live together, they can share a register (same color). The problem reduces to graph coloring. Common algorithms include **Greedy** (iterative assignment), **DSatur** (highest saturation degree first), and **RLF** (recursive largest first).

```
Example:
  x = 1        ┌─────────┐
  y = x + 2    │  x  y  z│ Interference Graph
  z = y + 3    │    \ | /│
  return z     │     \|/ │ If 3 registers available:
               │      w  │ color(x)=R1, color(y)=R2, color(z)=R1
               └─────────┘
```

<!-- placeholder: interference graph visualization — replace with phase_02/other/assets/register_allocation_graph.png -->

**When used:** Register allocation phase of code generation; essential for producing efficient machine code.

![IMAGE](/phase_02/other/assets/graph_register_allocation_01.png)

**References**
- [Graph Coloring for Register Allocation — YouTube Lecture (t=321s)](https://www.youtube.com/watch?v=K3mi2m7ccDQ&t=321s)

---

## 2. Strength Reduction

**What it is:** Replace expensive operations (multiplication, division) with cheaper ones (addition, bit shifts) by exploiting algebraic identities and loop structure.

**Key idea:** Common operations inside loops can be rewritten. For example, `i * 4` becomes `i << 2` (left shift by 2 = multiply by 4). Within induction variables, `i * k` in an inner loop can be replaced with an incremental update: instead of recalculating the product each iteration, maintain a running value that increments by `k` each step. This trades one multiply per iteration for one add.

**When used:** Loop optimization; reduces wall-clock time especially in tight loops with millions of iterations.

![IMAGE](/phase_02/other/assets/strength_reduction_01.png)
![IMAGE](/phase_02/other/assets/strength_reduction_02.png)
**References**
- [Strength Reduction in Compiler Optimization — DiVA Thesis (p.24)](https://www.diva-portal.org/smash/get/diva2:1940306/FULLTEXT01.pdf#page=24.38)
- [Strength Reduction — YouTube Lecture](https://www.youtube.com/watch?v=cVfIVV46FgY)

---

## 3. Interval / Range Analysis

**What it is:** Use **abstract interpretation** to track value bounds at each program point; compute the possible range `[lo, hi]` that each variable can hold.

**Key idea:** Instead of tracking exact values (infeasible for large programs), track interval ranges. For example, after `x = read_user_input()`, deduce `x ∈ [0, 100]` from input constraints. Propagate these bounds through assignments and branches. To ensure termination, use a **widening operator** (widen to infinity early) followed by **narrowing** (iteratively refine). This is a form of **abstract domain** lattice.

```
x = 1           x ∈ [1, 1]
y = x + 2       y ∈ [3, 3]
if (y < 10)     y ∈ [3, 9]   (refined in then-branch)
else            y ∈ [10, ∞]  (refined in else-branch)
```

<!-- placeholder: range analysis lattice diagram — replace with phase_02/other/assets/range_analysis_lattice.png -->

**When used:** Bounds checking elimination, loop bound prediction, security analysis (e.g., buffer overflow detection).

![IMAGE](/phase_02/other/assets/interval_analysis_01.png)
**References**
- [Static Program Analysis, Chapter 6.1 (p.87) — Anders Møller & Michael I. Schwartzbach](https://cs.au.dk/~amoeller/spa/spa.pdf#page=87.67)
- [Widening and Narrowing Operators — Verimag Course Notes](https://www-verimag.imag.fr/~mounier/Enseignement/Software_Security/Widening_Narrowing.pdf)

---

## 4. May-Must Dual Analysis

**What it is:** Run two parallel analyses — a **may** (over-approximation) and a **must** (under-approximation) — to find bugs and prove safety.

**Key idea:** A *may* analysis asks "what **could** happen?" (conservative, finds false positives but catches real bugs). A *must* analysis asks "what **must** happen?" (conservative, proves actual invariants). Together they form a **dual lattice**:
- **May lattice:** ⊆-ordered; false positives catch bugs
- **Must lattice:** ⊇-ordered; false negatives prove safety

Example: to detect null pointers, *may* tracks "could be null?" (catches dereferences), while *must* tracks "must be null?" (proves it's safe).

**When used in compiler optimization:**

**Dead Store Elimination** — a direct optimization use case:

```c
int x = compute();  // store A — potentially expensive operation
x = 10;             // store B — unconditionally overwrites x
use(x);             // only reads x = 10
```

- **Must-live analysis:** determines if x is *must-overwritten* after store A before any use → yes, store A is a **dead store** → safe to eliminate
- **May-live analysis:** determines if x *may* be live (conservative) → yes, prevents unsound removal

**Result:** Only when *must* proves "definitely dead" does the compiler delete store A. The *may* analysis ensures we never remove a store that could be needed.

![IMAGE](/phase_02/other/assets/may-must_dual_analysis_01.png)
**References**
- [Symbolic Execution with Relative Cost — Patrice Godefroid, POPL 2010](https://patricegodefroid.github.io/public_psfiles/popl2010.pdf)

---

## 5. Integer Linear Programming (ILP)

**What it is:** Formulate a compiler optimization problem as a system of linear constraints and integer variables, then solve it to find the provably **optimal** solution.

**Key idea:** Many compiler problems (scheduling, register allocation, loop tiling) can be written as:
```
maximize/minimize:  c₁x₁ + c₂x₂ + ... + cₙxₙ
subject to:         Ax ≤ b  (linear constraints)
                    x ∈ ℤⁿ  (integer variables)
```
An ILP solver finds the best assignment. Unlike greedy heuristics, ILP guarantees optimality but has exponential worst-case complexity.

**When used:** Optimal code generation (when compilation time is not critical), compiler design research, polyhedral optimizations.

![IMAGE](/phase_02/other/assets/ILP_01.png)
**References**
- [Integer Linear Programming in Optimization — Numdam](https://www.numdam.org/article/RO_1988__22_3_243_0.pdf)
- [Enabling Polyhedral Optimizations in LLVM, Grosser (p.43) — University of Passau Diploma Thesis](https://www.infosun.fim.uni-passau.de/cl/arbeiten/grosser-d.pdf#page=43.47)

---

## 6. Omega Test

**What it is:** A **decision procedure** for **Presburger arithmetic** (linear integer arithmetic). It tests whether a system of linear integer constraints has a solution.

**Key idea:** Given a system like:
```
2x + 3y ≤ 10
x - y = 1
x, y ∈ ℤ
```
the Omega Test decides: does a solution exist? If yes, it can extract solutions. This is used in loop parallelism analysis: prove two loop iterations are independent (no data dependence) by showing a constraint system has no solution.

**When used:** Loop dependence analysis (data dependence testing), parallelization safety checking, constraint-based program analysis.

![IMAGE](/phase_02/other/assets/omega_test_ilp_01.png)
**References**
- [Pugh's Omega Test — Carnegie Mellon Lecture Slides](https://www.cs.cmu.edu/~emc/spring06/home1_files/p4-pugh.pdf)
- [The Omega Test: A Fast and Practical Integer Programming Solver for Dependence Analysis — Pugh, CACM 1992](https://www.cmi.ac.in/~madhavan/courses/theorem-proving-2014/reading/Pugh-OmegaTest-CACM92.pdf)

---
