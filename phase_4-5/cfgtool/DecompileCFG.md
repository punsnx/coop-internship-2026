# How DecompileCFG Works

> Techniques from Java, WALA, and decompilation, walked through in the order the
> program actually executes them.
> Code: `src/main/java/com/ibm/wala/examples/program/{DecompileCFG,CallGraphFactory,
> SourceLineResolver,CfgDotRenderer}.java`. For how to *read* the rendered graph
> itself (block types, edge styles, SSA instruction syntax), see
> `vis-reading-instruction.md` — this document is about how the tool that produces
> that graph is built, not how to read its output.

## What it does

`DecompileCFG` takes a compiled Java program (via a WALA scope file) and a path to its
`.java` sources, builds its call graph, and renders one PDF: a control-flow graph
where every basic block shows both WALA's raw SSA instructions **and** the real
source line(s) that produced them. It answers "what does this block of bytecode-level
instructions correspond to in the code I actually wrote?" without attempting to
reconstruct full Java syntax.

## Pipeline overview

```
CLI args
   │
   ▼
DecompileCFG.Args (parse + validate)
   │
   ▼
CallGraphFactory.build(scopeFile, mainClass, entryClass)
   │  scope → class hierarchy → entrypoints → call graph
   ▼
CallGraph ────────────┐
                       ▼
        new SourceLineResolver(sourceDir)
                       │
                       ▼
        CfgDotRenderer.render(cg, resolver, dotPath, pdfPath)
                       │
                       ▼
          out/decompiled_cfg.dot  +  out/decompiled_output.pdf
```

This is literally `DecompileCFG.main`'s body — four calls, each one a pipeline stage
that lives in its own file.

---

## Stage 1 — CLI parsing & validation

**File:** `DecompileCFG.java`, the `Args` record.

```java
private record Args(String scopeFile, String mainClass, String entryClass, String sourceDir) {
  Args {
    if (mainClass != null && entryClass != null) throw new IllegalArgumentException(...);
    if (scopeFile == null) throw new IllegalArgumentException(...);
    if (sourceDir == null) throw new IllegalArgumentException(...);
  }
}
```

**Java technique — records with a compact constructor.** A `record` is Java's way of
saying "this is just data, nothing more" — it gets `equals`/`hashCode`/`toString` and
accessors for free. The *compact constructor* (the `Args { ... }` block with no
parameter list) runs before the fields are even assigned, so it's the idiomatic place
to validate a data class's invariants once, at construction time, rather than
scattering `if` checks through every method that later touches an `Args`. This is the
same pattern used for `SourceLineResolver.ResolvedLine` later in the pipeline.

This stage has no WALA or decompilation content — it's pure "is the command line
well-formed" gatekeeping before any analysis work starts.

---

## Stage 2 — Scope loading

**File:** `CallGraphFactory.java`, `loadScope`.

**WALA concept — `AnalysisScope`.** WALA doesn't analyze "a class" in isolation; it
analyzes a *scope* — the full universe of code reachable from your program, split into
class loaders (`Primordial` = JDK/bootstrap classes, `Application` = your code,
`Extension`/`Synthetic` for other cases). The scope file (`scope.txt` in this repo) is
a plain-text description of that universe: which directories/jars belong to which
loader. `loadScope` reads it via `AnalysisScopeReader.readJavaScope`, then applies
`Exclusions.txt` — a pattern list telling WALA which JDK classes to skip analyzing in
detail (this keeps analysis tractable; you rarely need WALA to model `sun.*` internals
line-by-line).

This is the standard first step of *any* WALA-based tool — every driver in this repo
(`ScopeFileCallGraph`, `SourceDirCallGraph`, and this one) starts here.

---

## Stage 3 — Class hierarchy analysis (CHA)

**File:** `CallGraphFactory.java`, `buildClassHierarchy`.

**WALA concept — `IClassHierarchy`.** Before WALA can figure out *what calls what*
(the call graph), it needs to know the type structure of the program: which classes
extend/implement which, method resolution order, etc. This is Class Hierarchy
Analysis (CHA) — a classical whole-program static analysis technique, and the
foundation everything downstream depends on. `ClassHierarchyFactory.make(scope)`
builds it from the scope constructed in Stage 2.

---

## Stage 4 — Entrypoint resolution

**File:** `CallGraphFactory.java`, `resolveEntrypoints` / `makePublicEntrypoints`.

**WALA concept — `Entrypoint`.** A whole-program call graph has to start somewhere.
WALA's `Entrypoint` abstraction represents "a method the analysis should treat as
externally invoked" — typically `main`, but a library might have many. This tool
supports two discovery strategies, controlled by mutually-exclusive CLI flags (already
validated as mutually exclusive back in Stage 1):
- `-mainClass` → `Util.makeMainEntrypoints` — the conventional `public static void
  main(String[])`.
- `-entryClass` → `makePublicEntrypoints`, a small helper that wraps every `public`
  method declared directly on a class in a `DefaultEntrypoint` — useful for analyzing
  a library with no single `main`.

---

## Stage 5 — Call graph construction

**File:** `CallGraphFactory.java`, `buildCallGraph`.

**WALA concept — `CallGraphBuilder` and context sensitivity.** This is where WALA
actually computes *who calls whom*. Building a call graph for an object-oriented
language is non-trivial because of virtual dispatch — a call to `list.add(x)` might
resolve to any implementation of `add` depending on `list`'s runtime type, which is
exactly what points-to analysis exists to approximate.

WALA offers a family of call-graph algorithms trading precision for speed, from
context-insensitive 0-CFA up through k-CFA variants. This tool uses
`Util.makeZeroOneContainerCFABuilder` — a 0-1-CFA-with-containers builder: 0-1-CFA
gives one level of context sensitivity (roughly, "which call site allocated this
object"), and the "containers" refinement adds smarter modeling of collection types
(`List`, `Map`, etc.) so container contents aren't smeared together into one
imprecise blob. `AnalysisCache`/`AnalysisOptions` are just the builder's required
configuration objects; `builder.makeCallGraph(options, null)` runs the fixpoint
computation and returns the finished `CallGraph`.

---

## Stage 6 — IR, SSA form, and the CFG

**Files:** used throughout `CfgDotRenderer.java` and `SourceLineResolver.java`, via
`CGNode.getIR()`.

**WALA concept — the SSA intermediate representation.** This is the conceptual bridge
between "call graph" and "decompilation," so it's worth calling out on its own. Java
bytecode is a *stack machine* program — instructions push/pop an operand stack, which
is compact for the JVM but awkward to analyze (the same logical value flows through
different stack slots depending on control-flow path). WALA's `IR` class converts each
method's bytecode into **SSA (Static Single Assignment) form**: every value gets a
unique "value number" assigned exactly once, and where control-flow paths merge, a
special `SSAPhiInstruction` ("phi node") picks which incoming value to use. This is
itself a real decompilation technique — WALA is *already partially decompiling*
bytecode into a form much closer to source-level reasoning, just without turning it
back into Java syntax.

The `SSACFG` is this method's control-flow graph — a sequence of `ISSABasicBlock`s
(straight-line instruction runs with no internal branches) connected by edges. Each
`ISSABasicBlock` exposes `getFirstInstructionIndex()`/`getLastInstructionIndex()`,
`iteratePhis()`, and iteration over its `SSAInstruction`s — exactly what
`CfgDotRenderer.buildBlockLabel` walks to build each node's label, and what
`SourceLineResolver.resolve` walks to find the block's source lines (see Stage 7).

---

## Stage 7 — Bytecode-index → source-line resolution

**File:** `SourceLineResolver.java`.

This is the stage that gives the tool its name and is the most decompilation-adjacent
piece of custom logic here, so it gets its own contrast section below. Mechanically:

```java
int bcIndex = bytecodeMethod.getBytecodeIndex(instructionIndex);   // SSA iindex → bytecode offset
int line    = method.getLineNumber(bcIndex);                       // bytecode offset → source line
```

**WALA concept — debug info.** `IBytecodeMethod.getBytecodeIndex` maps an SSA
instruction's index back to its position in the original `.class` file's bytecode
array (the same numbers `javap -c` prints). `IMethod.getLineNumber` then looks that
offset up in the class file's `LineNumberTable` — an optional but, for `javac`-built
classes, normally-present piece of debug metadata that maps bytecode offsets to
source line numbers. If a class was compiled without debug info, this returns `-1` and
the block gracefully falls back to `"source: unavailable"`.

**Java techniques:**
- `OptionalInt` as `lineNumberFor`'s return type — makes "this instruction has no
  resolvable line" an explicit, checked case in the type system instead of a sentinel
  `-1` leaking through call sites.
- `Map.computeIfAbsent(klass, this::loadSourceLines)` — a method reference used as a
  cache-population function, so each class's `.java` file is read from disk at most
  once regardless of how many blocks/instructions reference it.
- Deliberately broad `catch (Exception e)` around both the bytecode-index and
  line-number lookups — WALA's own APIs can throw checked (`InvalidClassFileException`)
  and unchecked exceptions here depending on the class file's condition; both are
  treated identically as "no line data for this instruction," never as a reason to
  abort the whole render.

**Decompilation angle — mapping, not reconstruction.** This is *not* what a real
decompiler does (see the dedicated section below) — it doesn't rebuild anything. It
looks up ground truth that the compiler already recorded. That's what makes it exact
and comparatively small to implement, at the cost of only working when source and
debug info are both available — a tradeoff a general-purpose decompiler doesn't get to
make, since it's usually invoked precisely when the source *isn't* available.

---

## Stage 8 — DOT/PDF rendering

**File:** `CfgDotRenderer.java`.

**WALA concept — `DotUtil`.** WALA doesn't draw graphs itself; it emits
[Graphviz](https://graphviz.org/) DOT source and shells out to the external `dot`
binary to rasterize it. `DotUtil.spawnDot("dot", pdfPath, dotFile)` is that bridge.
`render` builds one `subgraph cluster` per method (via `appendMethodCluster`), each
containing one DOT node per basic block (`buildBlockLabel`, combining SSA instructions
from Stage 6 with resolved source lines from Stage 7), intra-method edges from
`SSACFG.getSuccNodes`, and finally cross-method call edges (`appendCallEdges`, using
`CallGraph.getPossibleTargets` from Stage 5's call graph — bold red for recursion,
dashed blue for a regular call).

**Java technique — text blocks.** The DOT header and per-cluster boilerplate use
Java 15+ text blocks (`"""..."""`) instead of manually concatenated/escaped strings,
combined with `String.formatted(...)` for interpolation — considerably more readable
than the equivalent `+`-chain for a template with this many literal `"` and newline
characters.

---

## Reverse compilation: what this tool does vs. what a real decompiler does

A tool like **CFR**, **Procyon**, or **JD** turns a `.class` file back into
*compilable-looking Java*, with no source ever required. That's a substantially
harder problem than what `DecompileCFG` solves, and it's worth being explicit about
why, since "decompile" is in this tool's name but it does something narrower.

A real decompiler has to, roughly:

1. **Disassemble** — parse the raw bytecode into an instruction stream (WALA does
   this for you, and further lifts it to SSA form, as covered in Stage 6).
2. **Simulate the operand stack** — reconstruct, for every instruction, what
   expression is currently "on the stack," since bytecode has no notion of
   expressions, only stack pushes/pops.
3. **Rebuild expression trees** — fold sequences of stack operations back into
   nested Java expressions (`a + b * c` instead of `push a; push b; push c; mul;
   add`).
4. **Structure control flow** — this is the hard part. Bytecode only has
   conditional/unconditional jumps; a decompiler has to infer `if`/`else`, `for`,
   `while`, `switch`, and `try`/`catch` purely from the CFG's shape, typically via
   **dominator-tree analysis** (which blocks *must* execute before a given block) and
   **back-edge detection** (an edge from a block to one of its own dominators signals
   a loop). Irreducible control flow (arising from some optimized or obfuscated
   bytecode) can defeat this entirely.
5. **Infer types and rename variables** — bytecode local slots are just numbered
   registers with no names; a decompiler has to infer types from usage and invent
   readable names (or fall back to `var1`, `var2`, ...).

`DecompileCFG` does **none of steps 2–5**. It stops at WALA's SSA IR (already a
partial decompilation, per Stage 6) and, instead of structurally reconstructing
anything, performs **exact debug-info lookup** (Stage 7) to show the real source line
a block came from — when that source is available. This means:

- It's **exact**, not approximate — no risk of a decompiler mis-structuring a loop or
  producing semantically-equivalent-but-different-looking code.
- It's **much smaller to build and reason about** — no dominator/back-edge analysis,
  no expression-tree rebuilding, no type inference.
- It **requires the `.java` source to exist alongside the compiled class** — a
  constraint a real decompiler is specifically designed *not* to have, since
  decompilers exist for the case where source is unavailable.

For this tool's actual purpose — a static-analysis *researcher* correlating WALA's
internal SSA/CFG representation with the source they already have in front of them —
exact line mapping is the right tool for the job. Full structural decompilation would
be solving a different (and much bigger) problem than the one this tool needs to
solve.

---

## Java techniques, summarized

| Technique | Where | Why it's used here |
|---|---|---|
| `record` + compact constructor | `DecompileCFG.Args`, `SourceLineResolver.ResolvedLine` | Validate/represent immutable data once, at construction |
| `instanceof` type check | `SourceLineResolver.resolve` (`!(method instanceof IBytecodeMethod)`) | Guard against methods with no bytecode (e.g. abstract) before casting |
| Text blocks (`"""..."""`) | `CfgDotRenderer` DOT templates | Readable multi-line string literals instead of `+`-chains |
| `OptionalInt` | `SourceLineResolver.lineNumberFor` | Make "no line resolved" an explicit, typed outcome |
| Method reference + `computeIfAbsent` | `SourceLineResolver.resolve` (`this::loadSourceLines`) | Cache each class's source lines, read from disk once |
| Static factory / pipeline methods | `CallGraphFactory.build`, `CfgDotRenderer.render` | Each file exposes one static entry point that internally calls its own small, single-purpose helpers |
| Package-private classes | `CallGraphFactory`, `SourceLineResolver`, `CfgDotRenderer` | Only `DecompileCFG` is `public` — the rest are implementation detail of this one driver |

## WALA concepts, summarized

| Concept | Type/API | Role in the pipeline |
|---|---|---|
| Scope | `AnalysisScope` | Defines the universe of code to analyze and its class loaders |
| Class Hierarchy Analysis | `IClassHierarchy` | Type structure everything else depends on |
| Entrypoints | `Entrypoint`, `DefaultEntrypoint` | Where whole-program analysis starts |
| Call graph construction | `CallGraphBuilder` (0-1-CFA + containers) | Resolves virtual dispatch into a concrete call graph |
| Call graph | `CallGraph`, `CGNode` | The result: methods and their call edges |
| SSA intermediate representation | `IR`, `SSACFG`, `ISSABasicBlock`, `SSAInstruction`, `SSAPhiInstruction` | Bytecode lifted to an analyzable, partially-decompiled form |
| Bytecode/debug-info mapping | `IBytecodeMethod`, `IMethod.getLineNumber` | Exact SSA-instruction → source-line correlation |
| Visualization | `DotUtil` | Bridges WALA data structures to Graphviz DOT/PDF |
