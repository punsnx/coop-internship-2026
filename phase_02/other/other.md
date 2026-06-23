# Compiler & Static Analysis Techniques Reference

A categorized overview of data-flow analysis, abstract interpretation, and interprocedural analysis techniques used in compilers, IDEs, and static analysis tools.

---

## I. Classic Bitvector Data-Flow Analysis (Intraprocedural)

These techniques operate inside a single function using bitvector frameworks to solve local optimizations. All four are genuinely *optimization-enabling* — they tell the compiler it's safe to delete, hoist, or merge code.

### 1. Available Expressions
- **Summary:** Tracks expressions that have been calculated along all paths and not modified before reaching a program point.
- **Compiler/IDE Purpose:** Used for Common Subexpression Elimination (CSE) to prevent redundant calculations.
- **Production Use:** Used heavily by GCC and LLVM.
- **References:**
    - Wikipedia: Available Expression: https://en.wikipedia.org/wiki/Available_expression
    - Wikipedia: Common Subexpression Elimination: https://en.wikipedia.org/wiki/Common_subexpression_elimination

### 2. Reaching Definitions
- **Summary:** Determines which specific variable assignments (definitions) can mathematically reach a given program point without being overwritten.
- **Compiler/IDE Purpose:** Enables constant/variable propagation and provides the foundation for building SSA (Static Single Assignment) forms.
- **Production Use:** Core component of modern production optimization pipelines.
- **References:**
    - Cornell University CS4120 Notes: https://www.cs.cornell.edu/courses/cs4120/2023sp/notes.html?id=reachdef
    - Saarland University SPA Slides: https://compilers.cs.uni-saarland.de/teaching/spa/2014/slides/ReachingDefinitions.pdf

### 3. Very Busy Expressions
- **Summary:** Tracks expressions that are guaranteed to be evaluated along all future execution paths before any of their operands are modified.
- **Compiler/IDE Purpose:** Enables Code Hoisting (moving identical computations out of different code branches into a shared parent block to decrease binary size).
- **Production Use:** Implemented in GCC's global optimization passes.
- **References:**
    - Harvard University CS252 Lecture Slides: https://groups.seas.harvard.edu/courses/cs252/2011sp/slides/Lec02-Dataflow.pdf

### 4. Live Variables
- **Summary:** Determines whether the current value held by a variable will be read along any future path before it is overwritten.
- **Compiler/IDE Purpose:** Crucial for Dead Code Elimination (DCE) and Register Allocation (deciding which variables can share the same physical CPU registers).
- **Production Use:** Fundamental optimization pass in LLVM, GCC, and JIT compilers.
- **References:**
    - Harvard University CS252 Lecture Slides: https://groups.seas.harvard.edu/courses/cs252/2011sp/slides/Lec02-Dataflow.pdf

---

## II. Semantic Safety Checks

This technique reuses bitvector/reaching-definitions machinery, but it serves a different purpose than the optimizations in Section I: it rejects programs rather than transforming them. It's a soundness gate baked into a language specification, not a speed-up.

### 5. Definite Assignment Analysis
- **Summary:** A specialized variant of reaching definitions that checks if every possible execution path initializes a variable before a read attempt occurs.
- **Compiler/IDE Purpose:** Semantic verification and type safety checking. It throws a compile-time error if an uninitialized memory read is possible.
- **Production Use:** Required by the Java Language Specification (`javac` implements it); C# has an equivalent definite-assignment rule in the language spec, implemented by Roslyn.
- **References:**
    - The Java Language Specification (Chapter 16): https://docs.oracle.com/javase/specs/jls/se21/html/jls-16.html
    - Wikipedia: Definite Assignment Analysis: https://en.wikipedia.org/wiki/Definite_assignment_analysis

---

## III. Structural Graph & Evaluation Frameworks

These techniques alter or simplify how a control flow graph (CFG) is traversed and computed, mainly to speed up convergence of data-flow equations.

### 6. Region-Based Analysis
- **Summary:** A hierarchical approach that divides a Control Flow Graph (CFG) into nested structures (regions) to solve data-flow equations locally and pass summaries upward, bypassing traditional global iterations.
- **Compiler/IDE Purpose:** Speeds up data-flow convergence over structured code layouts.
- **Production Use:** Found historically in complex Fortran/C high-performance compilers and modern loop-nest optimization passes.
- **References:**
    - https://www.geeksforgeeks.org/compiler-design/region-based-analysis/
    - https://www.cs.cmu.edu/afs/cs/academic/class/15745-s16/www/lectures/L13-Region-Analysis.pdf

### 7. Structural Analysis
- **Summary:** An advanced, pattern-matching implementation of Region-Based Analysis that matches high-level control constructs (if-then-else, while, switch) to collapse sections of a CFG.
- **Compiler/IDE Purpose:** Used for control-flow simplification and code restructuring.
- **Production Use:** Heavily leveraged by Decompilers (like Ghidra and Hex-Rays) to translate assembly blocks back into structured high-level code.
- **References:**
    - https://www.cs.princeton.edu/courses/archive/spr04/cos598C/lectures/10-Structural.pdf
    - https://cris.tau.ac.il/en/publications/structural-analysis-a-new-approach-to-flow-analysis-in-optimizing/

---

## IV. Abstract Interpretation & Numeric Domains

Techniques relying on formal mathematical approximations to verify properties like signs, bounds, and variable relations.

### 8. Interval Analysis
- **Summary:** A mathematical loop-centric version of region-based analysis that isolates irreducible single-entry loops ("intervals") inside a CFG.
- **Compiler/IDE Purpose:** Simplifies complex loop blocks so widening/narrowing abstractions can be applied soundly and efficiently.
- **Production Use:** Frequently used in abstract interpreters and static verification frameworks.
- **References:**
    - Aarhus University Static Program Analysis Note (PDF): https://cs.au.dk/~amoeller/spa/5-widening-and-narrowing.pdf
    - Wikipedia: Control-flow Analysis: https://en.wikipedia.org/wiki/Control-flow_analysis

### 9. Sign Analysis
- **Summary:** An abstract interpretation domain that tracks the positive, negative, or zero state of numerical variables instead of their precise values.
- **Compiler/IDE Purpose:** Used to safely eliminate branch conditions (e.g., proving `x > 0` is true) or catch array out-of-bounds indexing.
- **Production Use:** Embedded inside code-auditing tools and linter engines.
- **References:**
    - https://www.youtube.com/watch?v=zNtM1ZhLxQU&list=PLamk8lFsMyPXrUIQm5naAQ08aK2ctv6gE&index=8

### 10. Constant Propagation
- **Summary:** Statically identifies whether a variable maintains a singular, unvarying constant value throughout execution and pushes that value forward into expressions.
- **Compiler/IDE Purpose:** Paired with Constant Folding to resolve arithmetic computations at compile-time and safely remove dead branches.
- **Production Use:** Extensively used via Sparse Conditional Constant Propagation (SCCP) in LLVM and GCC.
- **References:**
    - Wikipedia: Constant Propagation: https://www.google.com/search?q=https://en.wikipedia.org/wiki/Constant_folding%23Constant_propagation

### 11. Relational Analysis
- **Summary:** Rather than analyzing variables in complete isolation, this tracks the active mathematical relationships between variables (such as `x < y`).
- **Compiler/IDE Purpose:** Resolves complex nested dependencies and array bounds logic.
- **Production Use:** Implemented in mission-critical safety analyzers like Astrée and Facebook Infer to verify software correctness.
- **References:**
    - Wikipedia: Abstract Interpretation (Numerical Domains): https://www.google.com/search?q=https://en.wikipedia.org/wiki/Abstract_interpretation%23Numerical_abstract_domains

---

## V. Pointer, Alias, & Heap Analysis

These methods evaluate the program's memory behavior to trace data routing and object shapes.

### 12. Pointer Analysis (Points-to Analysis)
- **Summary:** Determines the full set of memory locations or objects that a pointer variable can potentially point to at runtime.
- **Compiler/IDE Purpose:** Fundamental requirement for constructing call graphs in object-oriented programs and eliminating unsafe pointer reads.
- **Production Use:** Present in all production-grade compiler optimization layers.
- **References:**
    - Wikipedia: Pointer Analysis: https://en.wikipedia.org/wiki/Pointer_analysis
    - Harvard University CS252 Lecture Slides: https://groups.seas.harvard.edu/courses/cs252/2011sp/slides/Lec06-PointerAnalysis.pdf

### 13. Alias Analysis
- **Summary:** Checks whether two different named pointer variables refer to the exact same physical memory address space at a specific execution point.
- **Compiler/IDE Purpose:** Crucial for allowing memory operations to bypass each other safely during register storage optimization.
- **Production Use:** Extensively used in LLVM's AliasAnalysis engine.
- **References:**
    - Wikipedia: Alias Analysis: https://en.wikipedia.org/wiki/Alias_analysis

### 14. Shape Analysis
- **Summary:** An advanced heap-centric analysis that tracks dynamically allocated memory objects to identify the topological architecture (the "shape") of active runtime data structures (e.g., verifying a tree vs. a cyclic graph).
- **Compiler/IDE Purpose:** Validates complex structural transformations and guarantees the absence of dangling links or memory leak vulnerabilities.
- **Production Use:** Commonly seen in academic verification tools and industrial engines like Facebook Infer.
- **References:**
    - Wikipedia: Shape Analysis: https://en.wikipedia.org/wiki/Shape_analysis_(program_analysis)
    - University of Wisconsin–Madison Primer (PDF): https://research.cs.wisc.edu/wpis/papers/cc2000.pdf

### 15. Flow-Sensitive Pointer Analysis
- **Summary:** Computes distinct pointer memory allocations at individual program execution steps, tracking how the targets of pointers shift as lines of code progress.
- **Compiler/IDE Purpose:** Maximizes pointer evaluation precision at the cost of compilation time.
- **Production Use:** Selectively leveraged within optimization compilers and code security verifiers.
- **References:**
    - Hardekopf & Lin: *Flow-Sensitive Pointer Analysis* Research Paper (PDF): https://www.cs.utexas.edu/~lin/papers/cgo11.pdf

### 16. Object-Sensitive Points-to Analysis
- **Summary:** A context-sensitive approach that tracks object values based on their dynamic allocation sites or allocation contexts, mapping field behavior across specific instantiations.
- **Compiler/IDE Purpose:** Provides massive precision jumps for object-oriented languages like Java and C#.
- **Production Use:** Used inside massive static analysis frameworks like Soot and Wala.
- **References:**
    - Milanova Lecture Note on Object-Sensitivity (PDF): https://www.csa.iisc.ac.in/~deepakd/tpa-2016/milanova-lecture.pdf
    - https://www.youtube.com/watch?v=rajISmg7gag&list=PLamk8lFsMyPXrUIQm5naAQ08aK2ctv6gE&index=25

### 17. Access-Path Abstraction
- **Summary:** Tracks complex nested heap properties by building a structured sequence of field evaluations (e.g., `x.y.z`) to accurately pinpoint data pathways through deep object hierarchies.
- **Compiler/IDE Purpose:** Scales field-sensitive analysis safely over unbounded memory paths.
- **References:**
    - IEEE Xplore: Access-Path Abstraction Publication: https://ieeexplore.ieee.org/document/7372049

---

## VI. Interprocedural Analysis Pipeline

Call graph construction, context-sensitive propagation frameworks, and graph-reachability engines form one continuous pipeline: you build the call graph first, then propagate facts across it.

### Stage 1 — Call Graph Construction

#### 18. Call Graph Analysis
- **Summary:** Constructs a directional graph showing which functions or methods can invoke other routines during execution.
- **Sub-techniques:** Class Hierarchy Analysis (CHA), Rapid Type Analysis (RTA), Variable Type Analysis (VTA), and Declared Type Analysis (DTA).
- **Compiler/IDE Purpose:** Devirtualization (converting costly dynamic, virtual method lookups into fast, direct function calls so they can be inlined). Also the prerequisite substrate that Stage 2 frameworks propagate facts across.
- **Production Use:** Standard optimization strategy in modern JVM architectures and LLVM Link-Time Optimization (LTO).
- **References:**
    - https://www.youtube.com/watch?v=OWhsLszOAps

### Stage 2 — Propagation Frameworks

#### 19. VASCO
- **Summary:** A specialized context-sensitive interprocedural data-flow analysis framework that combines call-string tracking with value-graph summary methods.
- **Compiler/IDE Purpose:** Simplifies writing complex, custom interprocedural static analysis checks over the call graph built in Stage 1.
- **References:**
    - https://www.youtube.com/watch?v=TVb8Bmt9Lpg&list=PLamk8lFsMyPXrUIQm5naAQ08aK2ctv6gE&index=28

#### 20. IFDS / IDE Frameworks
- **Summary:** Graph-reachability engines that convert complex Interprocedural Data-Flow problems into clean graph paths. IFDS handles finite subset problems, while IDE extends it to larger numeric contexts.
- **Compiler/IDE Purpose:** Tracks complex global bugs (like taint analysis or resource leaks) accurately and concurrently across multi-threaded applications.
- **Production Use:** Core driving engine behind modern advanced IDE code inspectors.
- **References:**
    - Reps, Horwitz, & Sagiv Foundational Paper via ACM: https://dl.acm.org/doi/10.1145/199448.199462
    - https://www.youtube.com/watch?v=QkK79fT0TFU&list=PLamk8lFsMyPXrUIQm5naAQ08aK2ctv6gE&index=30
    - https://www.youtube.com/watch?v=0uMHX3UY9bg&list=PLamk8lFsMyPXrUIQm5naAQ08aK2ctv6gE&index=34

---

## VII. Downstream Applications (Consumers of Dependence Info)

Program Slicing isn't a propagation framework in the same family as IFDS/IDE or VASCO — it's a consumer of dependence information (often built from reaching definitions or a System Dependence Graph) used to serve a specific end-user task.

### 21. Program Slicing
- **Summary:** Simplifies programs by stripping out everything except the specific sequence of statements that directly influence a target variable's state (using a System Dependence Graph).
- **Sub-techniques:** Thin Slicing (ignores certain pointer dependencies to drastically scale processing speed).
- **Compiler/IDE Purpose:** Used extensively in automated debugging tools, code refactoring engines, and vulnerability discovery.
- **References:**
    - Wikipedia: Program Slicing: https://en.wikipedia.org/wiki/Program_slicing
    - https://www.youtube.com/watch?v=Jj4sDvY2c5M&list=PLBmY8PAxzwIEGtnJiucyGAnwWpxACE633&index=30
    - https://www.youtube.com/watch?v=MLaE9S0rl7E&list=PLBmY8PAxzwIEGtnJiucyGAnwWpxACE633&index=31

---

# Program Graphs Reference

A categorized overview of graph-based representations used in compiler optimization, static analysis, and program transformation tools.

---

## I. Intraprocedural Graphs (Within a Single Function)

These graph structures represent program behavior, dependencies, and code structure within the scope of a single function.

### 1. Control Flow Graph (CFG)
- **Summary:** A directed graph representing all possible execution paths through a single function. Nodes represent basic blocks (straight-line code without jumps), and edges represent control flow transfers (e.g., loops, conditional branches).
- **Compiler/IDE Purpose:** The foundational structure used for virtually all standard intraprocedural data-flow optimizations (e.g., Live Variables, Constant Propagation).
- **Production Use:** Used natively inside LLVM and GCC.
- **References:**
  - Wikipedia: Control Flow Graph: https://en.wikipedia.org/wiki/Control-flow_graph

### 2. Program Dependence Graph (PDG)
- **Summary:** A directed graph that explicitly separates data and control dependencies between individual statements or predicate expressions.
- **Node Types:** Individual statements or conditional predicates.
- **Edge Types:** Data-flow dependencies (a value computed in Node A is read by Node B) and control dependencies (the execution of Node B is determined by the boolean outcome of Node A).
- **Compiler/IDE Purpose:** Used for fine-grained program slicing, instruction scheduling, and auto-parallelization.
- **References:**
  - https://en.wikipedia.org/wiki/Program_dependence_graph
  - https://dl.acm.org/doi/10.1145/24039.24041
  - https://compilers.cs.uni-saarland.de/teaching/spa/2014/slides/ProgramDependenceGraph.pdf

### 3. Directed Acyclic Graph (DAG)
- **Summary:** A structural, loop-free graph representation of a basic block where leaves represent initial variable inputs and internal nodes represent mathematical operators.
- **Compiler/IDE Purpose:** Used during intermediate code generation to perform local Common Subexpression Elimination (CSE) and optimize statement order before translating to assembly.
- **Production Use:** Core component of LLVM's SelectionDAG phase during target instruction selection.
- **References:**
  - Wikipedia: Directed Acyclic Graph: https://en.wikipedia.org/wiki/Directed_acyclic_graph

---

## II. Interprocedural Graphs (Across Multiple Functions)

These graph structures represent dependencies, control flow, and data paths that span function boundaries across an entire program.

### 4. Call Graph (CG)
- **Summary:** A directed graph charting the caller-callee execution relationships across an entire program. Nodes represent functions/procedures, and edges represent a function call invocation.
- **Compiler/IDE Purpose:** Drives whole-program optimizations, dead function elimination, and devirtualization.
- **Production Use:** Heavily utilized in LLVM and GCC Link-Time Optimization (LTO) pipelines.
- **References:**
  - Wikipedia: Call Graph: https://en.wikipedia.org/wiki/Call_graph

### 5. Inter-procedural Control Flow Graph (ICFG) / Supergraph
- **Summary:** Built by combining individual intraprocedural CFGs via a global Call Graph. Every function call site is linked to the target function's entry block using call edges, and the function's exit is mapped back via return edges.
- **Compiler/IDE Purpose:** Serves as the base landscape for context-sensitive interprocedural analysis frameworks (like VASCO).
- **References:**
  - https://www.youtube.com/watch?v=l5YCSjJCgAQ&list=PLamk8lFsMyPXrUIQm5naAQ08aK2ctv6gE&index=21

### 6. System Dependence Graph (SDG)
- **Summary:** An interprocedural extension of the Program Dependence Graph (PDG). It maps data and control dependencies globally across function boundaries by connecting call sites directly to parameter bindings and function returns.
- **Compiler/IDE Purpose:** The absolute standard graph structure required to perform global interprocedural Program Slicing.
- **References:**
  - https://www.sciencedirect.com/science/article/pii/S2352220824000701#br0230

### 7. Exploded Supergraph
- **Summary:** An expanded, massive variant of the ICFG used specifically within graph-reachability frameworks. It clones the nodes of a supergraph across an additional abstract dimension representing individual data-flow facts (e.g., variable states).
- **Compiler/IDE Purpose:** Converts complex interprocedural data-flow equations into simple, rapid, parallelizable path-finding (reachability) queries.
- **Production Use:** The core graph engine driving the IFDS/IDE frameworks.
- **References:**
  - https://www.youtube.com/watch?v=QkK79fT0TFU&list=PLamk8lFsMyPXrUIQm5naAQ08aK2ctv6gE&index=30

---

## III. Pointer & Type Analysis Graphs

These graph structures capture memory-aliasing behavior and type information to enable precise interprocedural analysis of heap and pointer operations.

### 8. Pointer-Assignment Graph (PAG)
- **Summary:** A specialized graph capturing point-to memory flows. Nodes represent pointer variables, allocation sites, or fields, while edges dictate the assignment of memory addresses or pointer copying.
- **Compiler/IDE Purpose:** The foundational data structure used to compute subset-based (Andersen-style) or unification-based (Steensgaard-style) Points-to Sets.
- **Production Use:** Leveraged inside the SPARK pointer analysis framework for Java.
- **References:**
  - https://www.youtube.com/watch?v=pdzvqxub470&list=PLamk8lFsMyPXrUIQm5naAQ08aK2ctv6gE&index=15

### 9. Type Propagation Graph
- **Summary:** A flow-insensitive graph where nodes represent variables or fields and edges indicate assignments. It propagates possible concrete runtime types across the program graph to approximate dynamic object types.
- **Compiler/IDE Purpose:** Scales up interprocedural Call Graph construction by refining type scopes to facilitate massive virtual-method devirtualization.
- **References:**
  - https://web.cs.ucla.edu/~palsberg/tba/papers/sundaresan-et-al-oopsla00.pdf (VTA, DTA call graph)
  - https://www.youtube.com/watch?v=JbumzZOnSOE&list=PLamk8lFsMyPXrUIQm5naAQ08aK2ctv6gE&index=14

---

## IV. Multi-Layered & Database Representations

These structures unify multiple classical graphs or persist graph data at scale for querying and analysis.

### 10. Code Property Graph (CPG)
- **Summary:** A modern, unified graph structure that merges three distinct classic graphs (the Abstract Syntax Tree, the Control Flow Graph, and the Program Dependence Graph) into a single interconnected joint schema.
- **Compiler/IDE Purpose:** Allows deep code layouts, data paths, and semantic patterns to be queried simultaneously.
- **Production Use:** The standard architecture used by contemporary application security platforms (like Joern) for tracking structural vulnerabilities and writing taint-analysis rules.
- **References:**
  - https://en.wikipedia.org/wiki/Code_property_graph

### 11. Graph Database (GDB)
- **Summary:** A persistable, production-grade storage system (such as Neo4j) used to physically host massive Code Property Graphs or complete System Dependence Graphs on disk.
- **Compiler/IDE Purpose:** Enables developers and security researchers to write structural syntax and vulnerability queries using industry-standard graph database languages (like Cypher) rather than parsing raw source trees.
- **References:**
  - Wikipedia: Graph Database: https://en.wikipedia.org/wiki/Graph_database
  - https://richardg.users.greyc.fr/publis/Dauprat-All_2022.pdf