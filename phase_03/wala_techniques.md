# Techniques Implemented in WALA

---

## IFDS and IDE Graph Reachability

**Located In :** `com.ibm.wala.dataflow.IFDS`

**Class :** `TabulationSolver.java`

**Usage in WALA :** track Summary Edges which allow reusing without recalculating. WALA use these to solve context-sensitive problems that can be represented as graph.

(See [WALA TabulationSolver Javadoc](https://wala.github.io/javadoc/com/ibm/wala/dataflow/IFDS/TabulationSolver.html) and [WALA TabulationSolver GitHub](https://github.com/wala/WALA/blob/master/core/src/main/java/com/ibm/wala/dataflow/IFDS/TabulationSolver.java))

## Flow-Insensitive Pointer Analysis

**Located In :** `com.ibm.wala.ipa.callgraph.propagation.cfa`

**Class :** `ZeroXCFABuilder.java`

**Usage in WALA :** WALA uses it to build a scalable control-flow graph approximation by tracking what abstract heap objects variable pointers can point to.

(See [WALA ZeroXCFABuilder Javadoc](https://wala.github.io/javadoc/com/ibm/wala/ipa/callgraph/propagation/cfa/ZeroXCFABuilder.html) and [WALA ZeroXCFABuilder GitHub](https://github.com/wala/WALA/blob/master/core/src/main/java/com/ibm/wala/ipa/callgraph/propagation/cfa/ZeroXCFABuilder.java))

## Reaching Definition 

**Located In :** `com.ibm.wala.examples.analysis.dataflow`

**Class :** `ContextInsensitiveReachingDefs.java`

**Usage in WALA :** WALA use this to demonstrate how to build dataflow solvers within WALA.

(See [WALA ContextInsensitiveReachingDefs Javadoc](https://wala.github.io/javadoc/com/ibm/wala/examples/analysis/dataflow/ContextInsensitiveReachingDefs.html) and [WALA ContextInsensitiveReachingDefs GitHub](https://github.com/wala/WALA/blob/master/core/src/main/java/com/ibm/wala/examples/analysis/dataflow/ContextInsensitiveReachingDefs.java))

# Techniques Not Implemented in WALA

---

## Compositional Shape Analysis

## Equality Saturation

## CFG Skeleton Relaxation