# CFG Visualization Manual

> Configuration reference for the Control Flow Graph renderer in
> `ScopeFileCallGraph.vizAndPrintCFGs()`.
> All values map directly to Graphviz DOT attributes.

---

## Output Files

| File | Description |
|------|-------------|
| `out/output.pdf` | Combined CFG PDF — one graph containing all app methods |
| `out/fibo_cfg.dot` | Raw DOT source (intermediate, inspect or edit by hand) |
| `run.log` | Appended SSA IR basic-block text dump per method |

To regenerate: `./test.sh`

---

## Graph-Level Config

```dot
digraph "FiboCFG" {
  compound=true;        // required for cross-cluster edges to render
  rankdir=TB;           // layout direction: top → bottom (TB), alternatives: LR, BT, RL
  node [shape=box, fontname="Courier", fontsize=9];
  edge [fontsize=9];
}
```

| Attribute | Value | Effect |
|-----------|-------|--------|
| `compound` | `true` | Allows edges to visually cross subgraph cluster boundaries |
| `rankdir` | `TB` | Top-to-bottom layout; change to `LR` for left-to-right |
| `node.shape` | `box` | Rectangular nodes for all basic blocks |
| `node.fontname` | `Courier` | Monospace font — keeps SSA instructions aligned |
| `node.fontsize` | `9` | Default node label font size (pt) |
| `edge.fontsize` | `9` | Edge label font size (pt) |

---

## Method Cluster (Subgraph) Config

Each application method is wrapped in a `subgraph cluster_N {}`.

```dot
subgraph cluster_0 {
  label="com/sirisuk/AnalysisClass.down(I)J";
  style=dashed;
  color=grey;
  fontsize=11;
  fontname="Helvetica-Bold";
}
```

| Attribute | Value | Effect |
|-----------|-------|--------|
| `label` | method signature | Cluster title shown above the group |
| `style` | `dashed` | Dashed border — transparent fill so inter-cluster arrows are not hidden |
| `color` | `grey` | Border colour |
| `fontsize` | `11` | Cluster label font size (pt) |
| `fontname` | `Helvetica-Bold` | Cluster label font |

> **Why `style=dashed` not `style=filled`:** a filled background (`lightgrey`) occludes
> arrows that enter or exit the cluster. Dashed gives a visible boundary without blocking edges.

---

## Basic Block Node Config

Each basic block is a node inside its method's cluster.

```dot
m0_bb3 [label="BB3\l─────────────────\l..instructions..\l",
         style=filled,
         fillcolor="#cce5ff"];
```

### Node Label Format

```
BB{N}  [ENTRY|EXIT|CATCH]        ← block number + type tag
─────────────────                ← separator
φ(v4 = phi v2, v3)               ← phi instructions (if any)
v5 = v1 <= v2                    ← SSA instructions, one per line
if v5 goto BB5                   ← branch instruction (last in block)
```

Lines end with `\l` (DOT left-aligned newline). Characters `"` are replaced with `'`
and `\` is doubled to avoid breaking the DOT string.

### Fill Colours by Block Type

| Block type | `fillcolor` | Hex |
|-----------|-------------|-----|
| Entry block | light blue | `#cce5ff` |
| Exit block | light green | `#ccffcc` |
| Catch block | light red | `#ffdddd` |
| Normal block | off-white | `#f9f9f9` |

To change a colour, edit the ternary in `vizAndPrintCFGs`:
```java
String fill =
    bb.isEntryBlock() ? "#cce5ff" : bb.isExitBlock() ? "#ccffcc" : bb.isCatchBlock() ? "#ffdddd" : "#f9f9f9";
```

---

## Edge Config

### Intra-method CFG edges (control flow within one method)

```dot
m1_bb2 -> m1_bb4;
```

| Attribute | Value | Meaning |
|-----------|-------|---------|
| style | *(default solid)* | Normal control-flow edge |
| color | *(default black)* | |
| label | *(none)* | |

These are drawn inside the cluster and follow the SSACFG successor relation.

### Inter-method call edges

```dot
m0_bb3 -> m1_bb0 [style=dashed, color=blue,  label="calls",     constraint=false];
m1_bb4 -> m1_bb0 [style=bold,   color=red,   label="recursive", constraint=false];
```

| Case | `style` | `color` | `label` |
|------|---------|---------|---------|
| Call to a different method | `dashed` | `blue` | `calls` |
| Self-recursive call | `bold` | `red` | `recursive` |

`constraint=false` — tells Graphviz not to use these edges to influence the rank layout,
so they don't distort the top-to-bottom ordering of the intra-method flow.

> **Note:** `ltail`/`lhead` compound-edge clipping is intentionally **not used** here.
> Those attributes cause Graphviz to silently drop self-recursive edges (same `ltail` and
> `lhead` cluster). Direct node-to-node edges are more reliable.

---

## Graphviz CLI Equivalent

The DOT file is rendered via `DotUtil.spawnDot`, which runs:

```bash
dot -Tpdf -o out/output.pdf out/fibo_cfg.dot
```

To re-render manually after editing `out/fibo_cfg.dot`:

```bash
dot -Tpdf -o out/output.pdf out/fibo_cfg.dot
```

To render as SVG instead:

```bash
dot -Tsvg -o out/output.svg out/fibo_cfg.dot
```

---

## Changing the Output Format

In `vizAndPrintCFGs`, change the `DotOutputType` and output file extension:

```java
DotUtil.setOutputType(DotUtil.DotOutputType.PDF);   // PDF (default)
// DotUtil.setOutputType(DotUtil.DotOutputType.SVG); // SVG
// DotUtil.setOutputType(DotUtil.DotOutputType.PS);  // PostScript
// DotUtil.setOutputType(DotUtil.DotOutputType.EPS); // EPS

String pdfPath = "out/output.pdf";  // change extension to match type
```

---

## Changing Layout Direction

Edit the `rankdir` line in the DOT builder:

```java
dot.append("  rankdir=TB;\n");  // TB = top→bottom
                                // LR = left→right (wider, good for deep methods)
                                // BT = bottom→top
                                // RL = right→left
```

---

## Filtering Which Methods Are Visualized

By default, all `Application` loader classes are included. To restrict to specific classes,
add a check after the loader filter in `vizAndPrintCFGs`:

```java
// Example: only include AnalysisClass methods
if (!node.getMethod().getDeclaringClass().getName().toString()
        .contains("AnalysisClass")) continue;
```