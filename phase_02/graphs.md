# Graphs used in compilers

---

## Program Dependence Graph

### How It Works

PDG show control and dependency for each statement in a program and is used in optimization

### Source(s)

https://web.eecs.umich.edu/~mahlke/courses/583f23/reading/ferrante_toplas_87.pdf

---

## Value Dependence Graph

### How it works

represents as a value flow system, nodes represent operations and edges as value transfer

### Source(s)

https://homes.cs.washington.edu/~mernst/pubs/vdg-popl94.pdf

---

## Code Property Graph

### How it works

combine 3 graphs (AST, CFG, PDG) which allow security analysts to construct queries

### Source(s)

https://fraunhofer-aisec.github.io/cpg/

---

## Evaluation Order Graph

### How it works

this graph tell the exact sequence of expression evaluations required by a language specification

### Source(s)

https://www.geeksforgeeks.org/compiler-design/evaluation-order-for-sdd/

---