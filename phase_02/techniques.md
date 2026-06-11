# Static Analysis Techniques

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

Determines the exact value of a variable at compile time when that value never changes, allowing the compiler to substitute it directly.

**Used by:** GCC, LLVM, Java HotSpot JIT

**Reference:** Wegman, M. N., & Zadeck, F. K. (1991). *Constant propagation with conditional branches.* ACM TOPLAS. https://dl.acm.org/doi/10.1145/103135.103136

---

## 2. Sign Analysis

Tracks whether a variable is positive, negative, or zero, without tracking the exact value. A simpler, cheaper alternative to constant propagation.

**Used by:** GCC, Clang/LLVM (as part of value range propagation)

**Reference:** Nielson, F., Nielson, H. R., & Hankin, C. (1999). *Principles of Program Analysis.* Springer. https://link.springer.com/book/10.1007/978-3-662-03811-6

---

## 3. Reaching Definitions

Tracks which assignments of a variable might have been the last one to set its value at a given point in the program.

**Used by:** GCC, LLVM, WALA (`CSReachingDefsDriver`)

**Reference:** Aho, A. V., Lam, M. S., Sethi, R., & Ullman, J. D. (2006). *Compilers: Principles, Techniques, and Tools* (2nd ed.), Chapter 9. https://www.pearson.com/en-us/subject-catalog/p/compilers-principles-techniques-and-tools/P200000003268

---

## 4. Live Variable Analysis

Determines which variables are still needed (live) at each point in the program. A variable is live if its value might be read in the future.

**Used by:** GCC, LLVM (register allocation), Java HotSpot

**Reference:** Aho, A. V., Lam, M. S., Sethi, R., & Ullman, J. D. (2006). *Compilers: Principles, Techniques, and Tools* (2nd ed.), Chapter 9. https://www.pearson.com/en-us/subject-catalog/p/compilers-principles-techniques-and-tools/P200000003268

---

## 5. Available Expressions

Tracks which expressions have already been computed and whose result is still valid (none of their variables have changed since). Enables common subexpression elimination.

**Used by:** GCC, LLVM

**Reference:** Aho, A. V., Lam, M. S., Sethi, R., & Ullman, J. D. (2006). *Compilers: Principles, Techniques, and Tools* (2nd ed.), Chapter 9. https://www.pearson.com/en-us/subject-catalog/p/compilers-principles-techniques-and-tools/P200000003268

---

## 6. Taint Analysis

Tracks untrusted data from where it enters the program (source) to where it might cause harm (sink), flagging cases where tainted data reaches a dangerous operation without being sanitized.

**Used by:** SonarQube, FindBugs/SpotBugs, Facebook Infer, WALA (`TaintV1`, `TaintV2`)

**Reference:** Schwartz, E. J., Avgerinos, T., & Brumley, D. (2010). *All You Ever Wanted to Know About Dynamic Taint Analysis and Forward Symbolic Execution (but might have been afraid to ask).* IEEE S&P. https://ieeexplore.ieee.org/document/5504796

---

## 7. Points-to Analysis

Tracks what objects a pointer or variable might be referring to at any given point in the program.

**Used by:** WALA (`DemandPointsToDriver`), LLVM, Soot

Reference: Andersen, L. O. (1994). Program Analysis and Specialization for the C Programming Language. PhD thesis, DIKU, University of Copenhagen (DIKU report 94/19). https://citeseerx.ist.psu.edu/document?repid=rep1&type=pdf&doi=b7efe971a34a0f2482e0b2520ffb31062dcdde62

---

## 8. Type Inference

Determines the type of a variable automatically from how it is used, without requiring explicit type declarations.

**Used by:** Kotlin compiler, TypeScript compiler, GHC (Haskell), Scala compiler

**Reference:** Milner, R. (1978). *A theory of type polymorphism in programming.* Journal of Computer and System Sciences. https://www.sciencedirect.com/science/article/pii/0022000078900144

---

## 9. Null Pointer Analysis

Detects variables that might be null when they are dereferenced, potentially causing a NullPointerException at runtime.

**Used by:** IntelliJ IDEA, SpotBugs, Facebook Infer, Kotlin compiler (built-in null safety)

**Reference:** Hovemeyer, D., & Pugh, W. (2004). *Finding bugs is easy.* ACM SIGPLAN Notices. https://dl.acm.org/doi/10.1145/1052883.1052895

---

## 10. Interval Analysis

Tracks the range of possible values a variable can hold at each point in the program.

**Used by:** GCC, LLVM, Astrée (safety-critical systems analyzer)

**Reference:** Cousot, P., & Cousot, R. (1977). *Abstract interpretation: a unified lattice model for static analysis of programs.* ACM POPL. https://dl.acm.org/doi/10.1145/512950.512973