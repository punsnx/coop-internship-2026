# Graphs Implemented in WALA

---

## System Dependence Graph

**Located In :** `com.ibm.wala.ipa.slicer`

**Class :** `SDG.java`

(See [WALA SDG Javadoc](https://wala.github.io/javadoc/com/ibm/wala/ipa/slicer/SDG.html) and [WALA SDG GitHub](https://github.com/wala/WALA/blob/master/core/src/main/java/com/ibm/wala/ipa/slicer/SDG.java))

## Program Dependence Graph

**Located In :** `com.ibm.wala.ipa.slicer`

**Class :** `PDG.java`

used by System Dependence Graph

(See [WALA PDG Javadoc](https://wala.github.io/javadoc/com/ibm/wala/ipa/slicer/PDG.html) and [WALA PDG GitHub](https://github.com/wala/WALA/blob/master/core/src/main/java/com/ibm/wala/ipa/slicer/PDG.java))

## Points-To Graph

**Located In :** `com.ibm.wala.ipa.callgraph.propagation`

**Class :** `PointerAnalysis.java`

(See [WALA PointerAnalysis Javadoc](https://wala.github.io/javadoc/com/ibm/wala/ipa/callgraph/propagation/PointerAnalysis.html) and [WALA PointerAnalysis GitHub](https://github.com/wala/WALA/blob/master/core/src/main/java/com/ibm/wala/ipa/callgraph/propagation/PointerAnalysis.java))

# Graphs Not Implemented in WALA

---

## Value Dependence Graph

> But WALA uses Static Single Assignment (SSA) form IR, which already models value flow and dependencies

## Code Property Graph

> But the graph used to create a code property graph is already implemented into WALA (Abstract Syntax Tree, Control Flow Graph and Program Dependence Graph)

## Evaluation Order Graph

> WALA already capture evaluation orders during the conversion into Control Flow Graphs