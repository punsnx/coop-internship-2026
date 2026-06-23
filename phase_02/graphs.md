# Graphs used in compiler

## 1. Control Flow Graph (CFG)
- **Summary:** A directed graph representing all possible execution paths through a single function. Nodes represent basic blocks (straight-line code without jumps), and edges represent control flow transfers (e.g., loops, conditional branches).
- **References:**
    - Wikipedia: Control Flow Graph: https://en.wikipedia.org/wiki/Control-flow_graph

## 2. Program Dependence Graph (PDG)
- **Summary:** A directed graph that explicitly separates data and control dependencies between individual statements or predicate expressions.
- **References:**
    - https://en.wikipedia.org/wiki/Program_dependence_graph
    - https://dl.acm.org/doi/10.1145/24039.24041
    - https://compilers.cs.uni-saarland.de/teaching/spa/2014/slides/ProgramDependenceGraph.pdf

## 3. Directed Acyclic Graph (DAG)
- **Summary:** A structural, loop-free graph representation of a basic block where leaves represent initial variable inputs and internal nodes represent mathematical operators.
- **References:**
    - Wikipedia: Directed Acyclic Graph: https://en.wikipedia.org/wiki/Directed_acyclic_graph

    
## 4. Call Graph (CG)
- **Summary:** A directed graph charting the caller-callee execution relationships across an entire program. Nodes represent functions/procedures, and edges represent a function call invocation.
- **References:**
    - Wikipedia: Call Graph: https://en.wikipedia.org/wiki/Call_graph

## 5. Inter-procedural Control Flow Graph (ICFG) / Supergraph
- **Summary:** Built by combining individual intraprocedural CFGs via a global Call Graph. Every function call site is linked to the target function's entry block using call edges, and the function's exit is mapped back via return edges.
- **References:**
    - https://www.youtube.com/watch?v=l5YCSjJCgAQ&list=PLamk8lFsMyPXrUIQm5naAQ08aK2ctv6gE&index=21

## 6. System Dependence Graph (SDG)
- **Summary:** An interprocedural extension of the Program Dependence Graph (PDG). It maps data and control dependencies globally across function boundaries by connecting call sites directly to parameter bindings and function returns.
- **References:**
    - https://www.sciencedirect.com/science/article/pii/S2352220824000701#br0230

## 7. Exploded Supergraph
- **Summary:** An expanded, massive variant of the ICFG used specifically within graph-reachability frameworks. It clones the nodes of a supergraph across an additional abstract dimension representing individual data-flow facts (e.g., variable states).
- **References:**
    - https://www.youtube.com/watch?v=QkK79fT0TFU&list=PLamk8lFsMyPXrUIQm5naAQ08aK2ctv6gE&index=30

## 8. Pointer-Assignment Graph (PAG)
- **Summary:** A specialized graph capturing point-to memory flows. Nodes represent pointer variables, allocation sites, or fields, while edges dictate the assignment of memory addresses or pointer copying.
- **References:**
    - https://www.youtube.com/watch?v=pdzvqxub470&list=PLamk8lFsMyPXrUIQm5naAQ08aK2ctv6gE&index=15

## 9. Type Propagation Graph
- **Summary:** A flow-insensitive graph where nodes represent variables or fields and edges indicate assignments. It propagates possible concrete runtime types across the program graph to approximate dynamic object types.
- **References:**
    - https://web.cs.ucla.edu/~palsberg/tba/papers/sundaresan-et-al-oopsla00.pdf (VTA, DTA call graph)
    - https://www.youtube.com/watch?v=JbumzZOnSOE&list=PLamk8lFsMyPXrUIQm5naAQ08aK2ctv6gE&index=14

## 10. Code Property Graph (CPG)
- **Summary:** A modern, unified graph structure that merges three distinct classic graphs (the Abstract Syntax Tree, the Control Flow Graph, and the Program Dependence Graph) into a single interconnected joint schema.
- **References:**
    - https://en.wikipedia.org/wiki/Code_property_graph

## 11. Graph Database (GDB)
- **Summary:** A persistable, production-grade storage system (such as Neo4j) used to physically host massive Code Property Graphs or complete System Dependence Graphs on disk.
- **References:**
    - Wikipedia: Graph Database: https://en.wikipedia.org/wiki/Graph_database
    - https://richardg.users.greyc.fr/publis/Dauprat-All_2022.pdf