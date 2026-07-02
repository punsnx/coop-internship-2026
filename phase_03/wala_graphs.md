# Graphs Implemented in WALA

---

## System Dependence Graph

**Located In :** `com.ibm.wala.ipa.slicer`

**Class :** `SDG.java`

(See [WALA SDG Javadoc](https://wala.github.io/javadoc/com/ibm/wala/ipa/slicer/SDG.html) and [WALA SDG GitHub](https://github.com/wala/WALA/blob/master/core/src/main/java/com/ibm/wala/ipa/slicer/SDG.java))

## Program Dependence Graph

**Located In :** `com.ibm.wala.ipa.slicer`

**Class :** `PDG.java`

**Usage in WALA :** Extract IR from SSA form Java bytecode.

used by System Dependence Graph

(See [WALA PDG Javadoc](https://wala.github.io/javadoc/com/ibm/wala/ipa/slicer/PDG.html) and [WALA PDG GitHub](https://github.com/wala/WALA/blob/master/core/src/main/java/com/ibm/wala/ipa/slicer/PDG.java))

## Points-To Graph

**Located In :** `com.ibm.wala.ipa.callgraph.propagation`

**Class :** `PointerAnalysis.java`

**Usage in WALA :** Identify unsafe memory area

(See [WALA PointerAnalysis Javadoc](https://wala.github.io/javadoc/com/ibm/wala/ipa/callgraph/propagation/PointerAnalysis.html) and [WALA PointerAnalysis GitHub](https://github.com/wala/WALA/blob/master/core/src/main/java/com/ibm/wala/ipa/callgraph/propagation/PointerAnalysis.java))

# Graphs Not Implemented in WALA

---

## Value Dependence Graph

show how value changes in each step of the program and tell the compiler the possible value to help in memory optimization.


> But WALA uses Static Single Assignment (SSA) form IR, which already models value flow and dependencies

## Code Property Graph

stitch multiple graph into one multi-layered graph to help finding vulnerability more easily.

> But the graph used to create a code property graph is already implemented into WALA (Abstract Syntax Tree, Control Flow Graph and Program Dependence Graph)

## Evaluation Order Graph

show the sequence in which each node is evaluated first to last to help.

> WALA already capture evaluation orders during the conversion into Control Flow Graphs