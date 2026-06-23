# Techniques used in compiler

## 1. Available Expressions 
- **Summary:** Tracks expressions that have been calculated along all paths and not modified before reaching a program point.
- **References:**
  - https://en.wikipedia.org/wiki/Available_expression
  - https://en.wikipedia.org/wiki/Common_subexpression_elimination

## 2. Reaching definitions
- **Summary:** Determines which specific variable assignments (definitions) can mathematically reach a given program point without being overwritten.
- **References:**
  - https://www.cs.cornell.edu/courses/cs4120/2023sp/notes.html?id=reachdef
  - https://compilers.cs.uni-saarland.de/teaching/spa/2014/slides/ReachingDefinitions.pdf


## 3. Very busy expression
- **Summary:** Tracks expressions that are guaranteed to be evaluated along all future execution paths before any of their operands are modified.
- **References:**
  - https://groups.seas.harvard.edu/courses/cs252/2011sp/slides/Lec02-Dataflow.pdf

## 4. Live variables
- **Summary:** Determines whether the current value held by a variable will be read along any future path before it is overwritten.
- **References:**
  - https://groups.seas.harvard.edu/courses/cs252/2011sp/slides/Lec02-Dataflow.pdf

## 5. Definite Assignment Analysis
- **Summary:** A specialized variant of reaching definitions that checks if every possible execution path initializes a variable before a read attempt occurs.
- **References:**
  - https://docs.oracle.com/javase/specs/jls/se21/html/jls-16.html
  - https://en.wikipedia.org/wiki/Definite_assignment_analysis

## 6. Region-Based Analysis
- **Summary:** A hierarchical approach that divides a Control Flow Graph (CFG) into nested structures (regions) to solve data-flow equations locally and pass summaries upward, bypassing traditional global iterations.
- **References:**
  - https://www.geeksforgeeks.org/compiler-design/region-based-analysis/
  - https://www.cs.cmu.edu/afs/cs/academic/class/15745-s16/www/lectures/L13-Region-Analysis.pdf


## 7. Structural Analysis
- **Summary:** An advanced, pattern-matching implementation of Region-Based Analysis that matches high-level control constructs (if-then-else, while, switch) to collapse sections of a CFG.
- **References:**
  - https://www.cs.princeton.edu/courses/archive/spr04/cos598C/lectures/10-Structural.pdf
  - https://cris.tau.ac.il/en/publications/structural-analysis-a-new-approach-to-flow-analysis-in-optimizing/

## 8. Shape Analysis
- **Summary:** An advanced heap-centric analysis that tracks dynamically allocated memory objects to identify the topological architecture (the "shape") of active runtime data structures (e.g., verifying a tree vs. a cyclic graph).
- **References:**
  - https://en.wikipedia.org/wiki/Shape_analysis_(program_analysis)
  - https://research.cs.wisc.edu/wpis/papers/cc2000.pdf

## 9. Point-to analysis
- **Summary:** Determines the full set of memory locations or objects that a pointer variable can potentially point to at runtime.
- **References:**
  - https://en.wikipedia.org/wiki/Pointer_analysis

## 10. Alias analysis
- **Summary:** Checks whether two different named pointer variables refer to the exact same physical memory address space at a specific execution point.
- **References:**
  - https://en.wikipedia.org/wiki/Alias_analysis

## 11. Call graph analysis
- **Summary:** Constructs a directional graph showing which functions or methods can invoke other routines during execution.
- **Sub-techniques:** Class Hierarchy Analysis (CHA), Rapid Type Analysis (RTA), Variable Type Analysis (VTA), and Declared Type Analysis (DTA).
- **References:**
  - https://www.youtube.com/watch?v=OWhsLszOAps

## 12. Sign Analysis
- **Summary:** An abstract interpretation domain that tracks the positive, negative, or zero state of numerical variables instead of their precise values.
- **References:**
  - https://www.youtube.com/watch?v=zNtM1ZhLxQU&list=PLamk8lFsMyPXrUIQm5naAQ08aK2ctv6gE&index=8

## 13. Constant Propagation
- **Summary:** Statically identifies whether a variable maintains a singular, unvarying constant value throughout execution and pushes that value forward into expressions.
- **References:**
  - https://www.google.com/search?q=https://en.wikipedia.org/wiki/Constant_folding%23Constant_propagation

## 14. Interval Analysis
- **Summary:** Compute the sound lower bound and upper bound of integers.
- **References:**
  - https://cs.au.dk/~amoeller/spa/5-widening-and-narrowing.pdf
  - https://en.wikipedia.org/wiki/Control-flow_analysis

## 15. VASCO
- **Summary:** A specialized context-sensitive interprocedural data-flow analysis framework that combines call-string tracking with value-graph summary methods.
- **References:**
  - https://www.youtube.com/watch?v=TVb8Bmt9Lpg&list=PLamk8lFsMyPXrUIQm5naAQ08aK2ctv6gE&index=28

## 16. IFDS/IDE
- **Summary:** Graph-reachability engines that convert complex Interprocedural Data-Flow problems into clean graph paths. IFDS handles finite subset problems, while IDE extends it to larger numeric contexts.
- **References:**
  - https://dl.acm.org/doi/10.1145/199448.199462
  - https://www.youtube.com/watch?v=QkK79fT0TFU&list=PLamk8lFsMyPXrUIQm5naAQ08aK2ctv6gE&index=30
  - https://www.youtube.com/watch?v=0uMHX3UY9bg&list=PLamk8lFsMyPXrUIQm5naAQ08aK2ctv6gE&index=34


## 17. Program Slicing
- **Summary:** Simplifies programs by stripping out everything except the specific sequence of statements that directly influence a target variable's state (using a System Dependence Graph).
- **References:**
  - https://en.wikipedia.org/wiki/Program_slicing
  - https://www.youtube.com/watch?v=Jj4sDvY2c5M&list=PLBmY8PAxzwIEGtnJiucyGAnwWpxACE633&index=30

### Thin Slicing
- **Summary:** Ignores certain pointer dependencies to drastically scale processing speed.
- **References:**
  - https://www.youtube.com/watch?v=MLaE9S0rl7E&list=PLBmY8PAxzwIEGtnJiucyGAnwWpxACE633&index=31

# Other interesting techniques

## 18. Access-Path Abstraction: Scaling Field-Sensitive Data-Flow Analysis With Unbounded Access Paths
- **References:**
  - https://ieeexplore.ieee.org/document/7372049

## 19. Flow-Sensitive Pointer Analysis
- **References:**
  - https://www.cs.utexas.edu/~lin/papers/cgo11.pdf

## 20. Object-sensitive points-to analysis for Java
- **References:**
  - https://www.csa.iisc.ac.in/~deepakd/tpa-2016/milanova-lecture.pdf

## 21. Demand-driven Analyses
- **References:**
  - https://www.youtube.com/watch?v=znRMEXCDcrc&list=PLamk8lFsMyPVfDVLbD_ofhTiOoHP3x7lZ&index=12

## 22. Self-adaptive static analysis
- **References:**
    - https://bodden.de/pubs/bodden18selfadaptive.pdf

## 23. Interprocedural type propagation for object-oriented languages
- **References:**
  - https://link.springer.com/chapter/10.1007/3-540-55253-7_19

