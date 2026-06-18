# Static Analysis Technique use in compilers

---

##  Compositional Shape Analysis

### How it works

Uses Bi-Abductive Inference to make Traditional Shape Analysis be able to scale through million lines of  code.

### Source(s)

https://www.cs.ox.ac.uk/people/hongseok.yang/paper/jacm11-biabduction-webversion.pdf

---

## Equality Saturation

### How it works

Represent the program as an equivalence graph and then rewrites until the graph is saturated.

### Source(s)

https://arxiv.org/pdf/2505.09363

---

## IFDS and IDE Graph Reachability

### How it works

Represent distributive flow function as bipartite graphs then it construct a structure where reachability from start node also represent a dataflow

### Source(s)

https://pages.cs.wisc.edu/~fischer/cs701.f14/popl95.pdf

---

## CFG Skeleton Relaxation

### How it works

isolates the impactful sequence and left the mathematical portion be projected on the equivalence graph

### Source(s)

https://dl.acm.org/doi/pdf/10.1145/3795883

---

## 

### How it works

### Source(s)

---