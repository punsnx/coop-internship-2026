# WALA-start — Developer Manual

> Watson Libraries for Analysis (WALA) is a static analysis framework for Java and JavaScript programs.
> This project is a Gradle-based starter that wires up WALA's example drivers so you can run analysis against any Java target.

---

## Project Structure

```
WALA-start/
├── build.gradle.kts              # Gradle build — dependencies, toolchain (Java 21), ECJ version pin
├── run_analysis.sh               # Main entry point — build stdlib cache + run a driver
├── scope.txt                     # Auto-generated WALA scope file (used by class-file drivers)
├── src/main/resources/
│   └── wala.properties           # Tells WALA where to find the Java stdlib JAR
└── src/main/java/com/ibm/wala/examples/
    ├── drivers/                  # Runnable analysis entry points (one per analysis type)
    └── analysis/                 # Supporting analysis implementations (dataflow, thread escape)

../fibo/                          # Example target application
├── src/AnalysisClass.java        # Java source — used by SourceDirCallGraph
└── out-class/AnalysisClass.class # Compiled class — used by class-file drivers
```

---

## Prerequisites

| Requirement | Detail |
|-------------|--------|
| Java 21 | Must be set as `$JAVA_HOME`. Check: `echo $JAVA_HOME` |
| Gradle | Included via `./gradlew` wrapper — no install needed |
| `unzip` | Standard macOS/Linux tool — pre-installed |

**Set JAVA_HOME if not already set:**

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home
```

---

## One-Time Setup

The first run of `run_analysis.sh` automatically extracts `java.base.jmod` from your JDK into `/tmp/wala-stdlib/java.base.jar`. WALA needs this to bootstrap its class hierarchy (`java.lang.Object`).

> Java 9+ removed `rt.jar` — the standard library now lives in module files (`.jmod`).
> WALA's legacy API still expects a JAR, so the script builds one from `java.base.jmod` once and caches it.

You will see this message on the first run only:

```
Building WALA stdlib cache (one-time)...
```

To force a rebuild (e.g. after changing JDK):

```bash
rm -rf /tmp/wala-stdlib
./run_analysis.sh
```

---

## How to Run

```bash
./run_analysis.sh
```

What the script does, step by step:

1. **Validates** the compiled target (`fibo/out-class/AnalysisClass.class`) exists
2. **Builds** a scope file (`scope.txt`) pointing `Primordial → stdlib` and `Application → AnalysisClass.class`
3. **Builds** `/tmp/wala-stdlib/java.base.jar` from your JDK's `java.base.jmod` (first run only)
4. **Runs** `SourceDirCallGraph` via Gradle against `fibo/src/`

Output is printed to terminal — call graph node count, edge count, and timing.

---

## Drivers Reference

All drivers live in `src/main/java/com/ibm/wala/examples/drivers/`.
Run any of them by passing `-PmainClass=com.ibm.wala.examples.drivers.<DriverName>`.

| Driver | Input | Output | What it does |
|--------|-------|--------|--------------|
| `SourceDirCallGraph` | Java **source** directory (`-sourceDir`) + class name (`-mainClass`) | Terminal: call graph stats | Parses `.java` files with ECJ, builds full call graph from a `main()` entrypoint |
| `ScopeFileCallGraph` | Scope file (`-scopeFile`) + class name (`-mainClass`) | Terminal: call graph stats | Builds call graph from compiled `.class` files using a scope file |
| `PrintTypeHierarchy` | Classpath (`.class` file or JAR) | Terminal: class hierarchy tree | Prints the full class hierarchy — useful for understanding inheritance |
| `PDFTypeHierarchy` | Classpath (`-classpath`) | PDF file in `output_dir` | Same as PrintTypeHierarchy but renders as a PDF graph diagram |
| `ConstructAllIRs` | Scope file | Terminal: warnings/stats | Builds WALA IR (SSA form) for every method — good for verifying scope is correct |
| `CSReachingDefsDriver` | Scope file + class name | Terminal: reaching definitions | Context-sensitive dataflow: tracks which definitions reach each use |
| `DemandPointsToDriver` | Scope file | Terminal: points-to sets | Demand-driven points-to analysis — shows what objects each pointer may refer to |

### How to switch driver in `run_analysis.sh`

Uncomment the block for the driver you want and comment out `SourceDirCallGraph`.
Each block is labelled with `# --- DriverName ---`.

Example — switch to `ScopeFileCallGraph`:

```bash
# --- ScopeFileCallGraph ---
"$SCRIPT_DIR/gradlew" -p "$SCRIPT_DIR" run \
  -PmainClass=com.ibm.wala.examples.drivers.ScopeFileCallGraph \
  --args="-scopeFile $SCOPE_FILE -mainClass LAnalysisClass"
```

---

## Key WALA Concepts

### Analysis Pipeline

Every WALA analysis follows this sequence:

```
AnalysisScope  →  ClassHierarchy (CHA)  →  CallGraph  →  Analysis
    (what to      (all classes and         (who calls    (dataflow,
     analyze)      their relations)         whom)         points-to...)
```

**AnalysisScope** — declares what code to load:
- `Primordial` loader → Java standard library (stdlib)
- `Application` loader → your code

**ClassHierarchy (CHA)** — resolves all types, methods, and inheritance. Built from the scope. Required before any call graph or dataflow analysis.

**CallGraph** — a graph where each node is a method and each edge is a call site. WALA offers several algorithms:
- `0-CFA` — fast, context-insensitive
- `0-1-CFA` (ZeroOneContainerCFA) — more precise, handles containers
- `n-CFA` — context-sensitive, expensive

### Class Name Format

WALA uses JVM internal class name format throughout:

| Java name | WALA name |
|-----------|-----------|
| `AnalysisClass` | `LAnalysisClass` |
| `java.lang.String` | `Ljava/lang/String` |
| `com.example.Foo` | `Lcom/example/Foo` |

Rule: prefix `L`, replace `.` with `/`, no `.class` suffix.

### Scope File Format

`scope.txt` is a plain text file, one entry per line:

```
Loader,Language,type,path
```

| Field | Values |
|-------|--------|
| Loader | `Primordial` (stdlib), `Application` (your code), `Extension` (libraries) |
| Language | `Java` |
| type | `stdlib`, `classFile`, `jarFile`, `binaryDir` |
| path | file path, or `none` for `stdlib` (uses running JVM) |

Example `scope.txt`:

```
Primordial,Java,stdlib,none
Application,Java,classFile,/path/to/AnalysisClass.class
```

---

## Configuration Files

### `src/main/resources/wala.properties`

```properties
java_runtime_dir=/tmp/wala-stdlib   # directory scanned for *.jar files (must exist)
output_dir=./out                    # used by PDF-generating drivers
```

This file is loaded by WALA's `WalaProperties` class at startup via the classloader.
Gradle automatically puts `src/main/resources/` on the classpath.

### `build.gradle.kts` — notable settings

```kotlin
java.toolchain.languageVersion = JavaLanguageVersion.of(21)   // use system JDK 21

configurations.all {
  resolutionStrategy {
    force("org.eclipse.jdt:ecj:3.36.0")   // pin ECJ to match jdt.core version
  }
}
```

The ECJ pin prevents a `NoSuchMethodError` caused by Gradle upgrading ECJ to an incompatible version.

---

## Analyzing a Different Target

### Source-based analysis (SourceDirCallGraph)

1. Point `-sourceDir` at a directory containing `.java` files
2. Set `-mainClass` to the WALA class name of the entry point (e.g. `LMyApp`)

```bash
--args="-sourceDir /path/to/your/src -mainClass LMyApp"
```

### Class-file-based analysis (ScopeFileCallGraph)

1. Update `scope.txt` to include your `.class` file or JAR:

```bash
{
  echo "Primordial,Java,stdlib,none"
  echo "Application,Java,classFile,/path/to/MyApp.class"
} > "$SCOPE_FILE"
```

2. Run `ScopeFileCallGraph` with `-mainClass LMyApp`

---

## Troubleshooting

| Error | Cause | Fix |
|-------|-------|-----|
| `J2SE_DIR not set` | `wala.properties` not on classpath | Check `src/main/resources/wala.properties` exists |
| `failed to load root Ljava/lang/Object` | `java_runtime_dir` has no usable JARs | Delete `/tmp/wala-stdlib` and re-run to rebuild cache |
| `NoSuchMethodError: Scanner.getNextToken` | ECJ version conflict | Confirm `force("org.eclipse.jdt:ecj:3.36.0")` is in `build.gradle.kts` |
| Script stops after "Building..." | `unzip` non-zero exit from jmod header warning | `|| true` must follow the `unzip` line in the script |
| `JAVA_HOME must be set` | `$JAVA_HOME` env var not exported | `export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home` |

---

## How It All Works — Deep Dive

### Which JVM runs when you execute `run_analysis.sh`

There are **two separate JVM processes** — one that runs your analysis code, and the target program which is never executed at all:

```
./run_analysis.sh
    └── ./gradlew run
            └── JVM process (Java 21)        ← runs SourceDirCallGraph.main()
                    │                           this is YOUR analysis code
                    │  WALA loads & inspects
                    ▼
          fibo/AnalysisClass.class            ← read as raw data (bytecode)
                                                never executed
```

- The **Gradle JVM** runs the WALA driver. It uses your system JDK 21 because `build.gradle.kts` sets `java.toolchain.languageVersion = 21`.
- **`AnalysisClass`** is the subject of analysis — WALA reads its bytecode as structured data and builds a model of it. No line of `AnalysisClass` ever executes.

---

### Java Compilation Pipeline

Understanding where WALA fits in the Java execution lifecycle:

```
AnalysisClass.java
        │
        │  javac                    ← compilation (platform-independent)
        ▼
AnalysisClass.class                 ← JVM bytecode (stack-based instruction set)
        │
        │  ◄── WALA operates here ──►
        │      reads bytecode as data, builds models
        │
        │  JVM (runtime)            ← interpretation + JIT compilation
        ▼
  native machine code               ← CPU-specific, executed
```

The `.class` file is **bytecode** — a compact, platform-independent instruction set. The JVM interprets or JIT-compiles it to native code at runtime. WALA sits between compilation and execution, reasoning about the bytecode without ever running it.

#### SSA — the internal representation WALA uses

Before analyzing, WALA converts bytecode into **SSA form (Static Single Assignment)**. The rule: every variable is assigned exactly once, with a unique version per assignment. This makes data flow trivially traceable.

```java
// Original source
x = 1;
x = x + 2;
if (...) x = 5;

// SSA form
x₁ = 1
x₂ = x₁ + 2
x₃ = 5
x₄ = φ(x₂, x₃)   ← φ-function: "x₄ is either x₂ or x₃ depending on which branch ran"
```

The φ-function (phi) merges values at control flow join points. With SSA, dataflow questions like "which definition of `x` reaches this use?" become simple graph traversals instead of iterative fixpoints.

---

### `rt.jar` — What It Was and Why WALA Needed It

`rt.jar` (Runtime JAR) was the entire Java standard library packaged as a single file, shipped with every JDK from Java 1.0 through Java 8:

```
JDK 8 layout:
  jre/
    lib/
      rt.jar        ← java.lang, java.util, java.io, java.nio ... (~60 MB)
      charsets.jar
      jce.jar
```

**Why WALA cannot analyze without it:**

To build the class hierarchy, WALA must resolve every type referenced anywhere in your code. The moment your code references `System.out.println()`, WALA needs to know:
- What is `System`? (a class in `java.lang`)
- What is `PrintStream`? (its type)
- What does `PrintStream` inherit from? (→ `FilterOutputStream` → `OutputStream` → `Object`)
- What is `java.lang.Object`? — the **root of all class hierarchies**

Without `rt.jar`, the class hierarchy has no root. `ClassHierarchy` construction throws immediately:

```
ClassHierarchyException: failed to load root <Primordial,Ljava/lang/Object>
```

WALA's `WalaProperties.getJ2SEJarFiles()` was designed to scan a directory for `*.jar` files and add each one to the `Primordial` scope — exactly to solve this problem.

---

### Why Java Moved to `.jmod` and Why We Extract Back to `.jar`

#### Problems with `rt.jar`

| Problem | Detail |
|---------|--------|
| Monolithic | One 60 MB blob — always fully loaded even if you only use 1% of it |
| No encapsulation | Internal APIs (`sun.*`, `com.sun.*`) were accessible to anyone |
| Slow startup | ClassLoader had to scan the entire JAR to find any class |
| No versioning | You couldn't update `java.sql` independently of `java.lang` |

#### Java 9 Module System (Project Jigsaw)

Java 9 split `rt.jar` into ~70 named modules, each in its own `.jmod` file:

```
JDK 9+ layout:
  jmods/
    java.base.jmod      ← java.lang, java.util, java.io, java.nio, java.net ...
    java.sql.jmod
    java.desktop.jmod
    java.logging.jmod
    ...
```

Each module explicitly declares:
- `exports` — which packages are public API
- `requires` — which other modules it depends on
- `opens` — which packages allow deep reflection

Internal packages (`sun.*`) are no longer accessible without explicit flags.

#### What is inside a `.jmod` file

`.jmod` files are ZIP archives with a custom 4-byte magic header (which is why `unzip` warns "4 extra bytes at beginning"). Their internal structure:

```
java.base.jmod (ZIP structure)
├── classes/
│   ├── java/lang/Object.class
│   ├── java/lang/String.class
│   ├── java/util/List.class
│   └── ...
├── lib/
│   └── (native libraries: .dylib / .so)
├── conf/
│   └── (config files)
└── module-info.class
```

#### Why WALA's API cannot open `.jmod` directly

WALA's `WalaProperties.getJarsInDirectory()` scans for `*.jar` files and opens each with `new JarFile(path)`:

```java
// Inside WalaProperties.java
Collection<File> col = FileUtil.listFiles(dir, ".*\\.jar$", true);  // ← only *.jar
for (File jarFile : col) {
    scope.addToScope(ClassLoaderReference.Primordial, new JarFile(jarFile));
}
```

`.jmod` files are excluded by the `*.jar` filter. Even if you pointed it at a `.jmod` directly, `new JarFile()` would reject the non-standard magic header.

#### The extraction shim in `run_analysis.sh`

The script bridges the gap by converting `java.base.jmod` into a normal JAR once:

```bash
# Step 1: unzip the .jmod (it's a ZIP — the 4-byte header is harmless with || true)
unzip -q "$JDK_HOME/jmods/java.base.jmod" -d /tmp/wala-stdlib/x || true

# Step 2: repack the classes/ tree as a standard .jar
"$JDK_HOME/bin/jar" cf /tmp/wala-stdlib/java.base.jar \
    -C /tmp/wala-stdlib/x/classes .

# Result: /tmp/wala-stdlib/java.base.jar
# ├── java/lang/Object.class   ← WALA can now find this
# ├── java/lang/String.class
# └── ...
```

The content is **identical** to what was in `rt.jar` for the base module — only the container format changed.

#### The proper long-term fix (not implemented here)

Java 9+ provides a `jrt:` URI filesystem for accessing module classes directly:

```java
FileSystem jrtFs = FileSystems.getFileSystem(URI.create("jrt:/"));
Path objectClass = jrtFs.getPath("/modules/java.base/java/lang/Object.class");
```

Updating `SourceDirCallGraph` to use `JrtModule` instead of `WalaProperties.getJ2SEJarFiles()` would eliminate the need for the extraction entirely. The extraction shim in this project exists because the driver code is unchanged.

---

## Analyzing WALA's Own Source (Self-Analysis)

This section documents what was required to use `SourceDirCallGraph` to analyze
`SourceDirCallGraph.java` itself — a self-referential analysis that exposed several
layered issues beyond the basic fibo example.

---

### Why WALA needs more than `java.base` to analyze itself

`fibo/AnalysisClass.java` only uses `java.lang` — everything is in `java.base.jar`.

`SourceDirCallGraph.java` imports dozens of WALA framework types:

```java
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.cast.java.translator.jdt.ecj.ECJClassLoaderFactory;
// ... and many more
```

When ECJ parses `SourceDirCallGraph.java`, it must resolve every imported type. Those
types live in the WALA framework JARs — not in `java.base.jar`. Without them, ECJ can
parse the file syntactically but cannot resolve types, and WALA fails to build the
class hierarchy.

---

### The WALA + ECJ JAR copy block (`run_analysis.sh`)

A second `if` block was added after the `java.base.jar` extraction:

```bash
if [ ! -f /tmp/wala-stdlib/.libs-ready ]; then
    echo "Copying WALA + ECJ dependency JARs (one-time)..."
    find ~/.gradle/caches/modules-2/files-2.1/com.ibm.wala \
        -name "*.jar" ! -name "*-sources.jar" ! -name "*-javadoc.jar" \
        -exec cp {} /tmp/wala-stdlib/ \;
    find ~/.gradle/caches/modules-2/files-2.1/org.eclipse.jdt \
        -name "*.jar" ! -name "*-sources.jar" ! -name "*-javadoc.jar" \
        -exec cp {} /tmp/wala-stdlib/ \;
    touch /tmp/wala-stdlib/.libs-ready
fi
```

**How it works:**

- `~/.gradle/caches/modules-2/files-2.1/com.ibm.wala/` — Gradle's local cache of all
  downloaded WALA JARs (put there when the project first built)
- `~/.gradle/caches/modules-2/files-2.1/org.eclipse.jdt/` — ECJ and JDT JARs
- Source and javadoc JARs are excluded (`! -name "*-sources.jar"`) — they contain
  `.java` files not `.class` files and would confuse WALA
- `.libs-ready` is a **marker file** — presence means the copy already ran; delete it
  to force a re-copy

**Why no config change is needed:**

`WalaProperties.getJarsInDirectory()` already scans ALL `*.jar` files recursively:
```java
Collection<File> col = FileUtil.listFiles(dir, ".*\\.jar$", true);
```
Dropping more JARs into `/tmp/wala-stdlib/` is automatically picked up. `wala.properties`
still just points to `/tmp/wala-stdlib/`.

**To reset the cache:**
```bash
rm -rf /tmp/wala-stdlib && ./run_analysis.sh   # full reset
rm /tmp/wala-stdlib/.libs-ready && ./run_analysis.sh   # re-copy JARs only
```

---

### Two args that must both be correct simultaneously

Running `SourceDirCallGraph` against itself requires both of these to be right at the
same time. Each error looks different, making them easy to confuse.

#### `-sourceDir` must point to `.java` source files

`SourceDirCallGraph` uses ECJ — a **Java source parser**. It reads `.java` text files.

```
src/main/java/                          ← correct: .java files, ECJ can parse
build/classes/java/main/.../drivers/    ← wrong:   .class bytecode, ECJ cannot parse
```

Pointing at the build output directory causes ECJ to find no parseable files. Zero
source classes load into the SOURCE scope → the entrypoint class is never found.

#### `-mainClass` must use the full WALA internal type name

WALA's type name format: `L` prefix + package path with `/` separators (not `.`).

```
LSourceDirCallGraph
  → looks for class named SourceDirCallGraph with NO package
  → does not exist → method cannot be resolved → UNREACHABLE error

Lcom/ibm/wala/examples/drivers/SourceDirCallGraph
  → looks for com.ibm.wala.examples.drivers.SourceDirCallGraph
  → matches what ECJ loaded from the source file ✓
```

**Rule:** read the `package` declaration from the `.java` file, replace `.` with `/`,
prefix `L`.

```
package com.ibm.wala.examples.drivers;
         ↓
Lcom/ibm/wala/examples/drivers/SourceDirCallGraph
```

#### How they connect

```
ECJ parses src/main/java
    └── loads < Source, Lcom/ibm/wala/examples/drivers/SourceDirCallGraph >

makeMainEntrypoints(SOURCE, cha, "Lcom/ibm/wala/examples/drivers/SourceDirCallGraph")
    └── cha.lookupClass(< Source, Lcom/.../SourceDirCallGraph >) → found ✓
    └── cha.resolveMethod(main([Ljava/lang/String;)V)           → found ✓
    └── entrypoint created ✓ → call graph builds ✓
```

If either arg is wrong, this chain breaks at a different point, producing a different
error message.

---

### The `ExpressionMethodReference` fix

After fixing the two args, WALA's JDT-to-CAst translator threw:

```
UnimplementedError: Unhandled JDT node type org.eclipse.jdt.core.dom.ExpressionMethodReference
```

**Cause:** `SourceDirCallGraph.java` line 102 uses a Java 8 method reference:
```java
options.getSSAOptions().setDefaultValues(SymbolTable::getDefaultValue);
```

WALA 1.7.2's `JDTJava2CAstTranslator` does not implement handling for `ExpressionMethodReference`
AST nodes. Adding JARs cannot fix this — it is a missing feature in the translator.

**Fix:** replace the method reference with an equivalent lambda.

`SSAOptions.DefaultValues` is a functional interface:
```java
interface DefaultValues {
    int getDefaultValue(SymbolTable symtab, int valueNumber);
}
```

`SymbolTable::getDefaultValue` is an unbound instance method reference, equivalent to:

```java
// Before (unsupported by WALA 1.7.2 source translator)
options.getSSAOptions().setDefaultValues(SymbolTable::getDefaultValue);

// After (semantically identical, works with WALA's translator)
options.getSSAOptions().setDefaultValues((symtab, vn) -> symtab.getDefaultValue(vn));
```

**File changed:** `src/main/java/com/ibm/wala/examples/drivers/SourceDirCallGraph.java` line 102.

---

### Updated Troubleshooting

Additional rows for the errors encountered during self-analysis:

| Error | Cause | Fix |
|-------|-------|-----|
| `UnimplementedError: ExpressionMethodReference` | WALA 1.7.2 source translator does not handle `::` method references | Replace `Class::method` with `(a, b) -> a.method(b)` in the source file |
| `could not resolve < Source, LFoo, main >` | `-mainClass` missing package path, OR `-sourceDir` points to `.class` files | Use full path `Lcom/pkg/ClassName`; point `-sourceDir` at `.java` source root |

---

## JavaScript Analysis

WALA supports JavaScript analysis using **Rhino** (Mozilla's JS engine) as the parser.
The three JS drivers work on plain `.js` files — no scope file, no class hierarchy step.

---

### Test File

```
src/main/resources/test-files/fibo.js
```

The test file implements Fibonacci DP in two ways (bottom-up and top-down memoization),
mirroring the Java fibo target. It uses `print()` for output:

```javascript
print("Bottom-up : " + fiboUp(n));   // correct for Rhino
// console.log(...)                  // wrong — not available in Rhino
```

**Why `print()` not `console.log`:** WALA's JS analysis runs the file through Rhino's
engine model, not a browser or Node.js. Rhino exposes `print()` as its global output
function. `console` is a browser/Node API that Rhino does not provide.

**To use a different JS target:** update `TARGET_JS` in `run_analysis.sh`:
```bash
TARGET_JS="$(cd "$SCRIPT_DIR" && realpath "src/main/resources/test-files/your-file.js")"
```

---

### Three JS Drivers

All three live in `src/main/java/com/ibm/wala/examples/drivers/`.

| Driver | Algorithm | Input | Output | Best for |
|--------|-----------|-------|--------|----------|
| `JSCallGraphDriver` | Propagation-based (0-1-CFA) | path to `.js` file | call graph stats | Most precise; use when accuracy matters |
| `FieldBasedJSCallGraphDriver` | Field-based optimistic worklist | path to `.js` file | stats + JSON | Faster; serializes graph to JSON |
| `BoundedJSCallGraphDriver` | Field-based with indirection bound | path to `.js` file + integer bound | stats + JSON | Tunable precision vs speed trade-off |

#### `JSCallGraphDriver`
```java
JSCallGraphUtil.setTranslatorFactory(new CAstRhinoTranslatorFactory());
CallGraph CG = JSCallGraphBuilderUtil.makeScriptCG(directory, filename);
```
Runs full propagation-based call graph construction (similar to 0-1-CFA for Java).
Most precise but slowest. Output: call graph stats only.

#### `FieldBasedJSCallGraphDriver`
```java
FieldBasedCGUtil f = new FieldBasedCGUtil(new CAstRhinoTranslatorFactory());
CallGraphResult results = f.buildScriptCG(url, BuilderType.OPTIMISTIC_WORKLIST, null, false);
System.out.println(new CallGraph2JSON().serialize(CG));
```
Uses a **field-based** algorithm — models call targets through object field assignments
instead of full pointer analysis. `OPTIMISTIC_WORKLIST` assumes unknown call targets are
safe (optimistic), iterating via a worklist until stable.
Output: stats + full call graph serialized to **JSON**.

#### `BoundedJSCallGraphDriver`
```java
f.buildScriptDirBoundedCG(scriptDir, new NullProgressMonitor(), false, bound);
```
Same field-based algorithm but limits the number of indirections followed per call site
to `bound`. `bound = 0` means only direct calls; higher values follow more indirect paths.
Input is a **directory** (not a single file) + an integer.
Output: stats + JSON.

---

### How to Run Each JS Driver

The JS section in `run_analysis.sh` (lines 93–108) has all three blocks ready — just
uncomment the one you want:

```bash
# =============== JS Analysis ===============

# --- JSCallGraphDriver ---
"$SCRIPT_DIR/gradlew" -p "$SCRIPT_DIR" run \
  -PmainClass=com.ibm.wala.examples.drivers.JSCallGraphDriver \
  --args="$TARGET_JS"

# --- FieldBasedJSCallGraphDriver ---
"$SCRIPT_DIR/gradlew" -p "$SCRIPT_DIR" run \
  -PmainClass=com.ibm.wala.examples.drivers.FieldBasedJSCallGraphDriver \
  --args="$TARGET_JS"

# --- BoundedJSCallGraphDriver ---
"$SCRIPT_DIR/gradlew" -p "$SCRIPT_DIR" run \
  -PmainClass=com.ibm.wala.examples.drivers.BoundedJSCallGraphDriver \
  --args="$TARGET_JS 0"
  # NOTE: integer is the indirection bound (0 = direct calls only)
```

`TARGET_JS` is resolved at the top of the script:
```bash
TARGET_JS="$(cd "$SCRIPT_DIR" && realpath "src/main/resources/test-files/fibo.js")"
```

---

### How the JS Pipeline Differs from Java

```
Java pipeline:                    JS pipeline:
  .java                             .js
    │  ECJ (compiler)                 │  Rhino (JS engine model)
    ▼                                 ▼
  CAst IR  ──────────────────────  CAst IR   ← same intermediate representation
    │                                 │
    ▼                                 ▼
  ClassHierarchy                   (skipped — JS has no static type hierarchy)
    │                                 │
    ▼                                 ▼
  CallGraph                        CallGraph
```

Both Java and JS paths produce the **same CAst IR** (Common Abstract Syntax Tree).
This is WALA's language-neutral intermediate form — it is the unification point that
lets WALA use the same analysis algorithms for both languages.

Key differences:
- **No ClassHierarchy for JS** — JavaScript is dynamically typed; there are no declared
  class relationships to resolve before building the call graph
- **Parser: Rhino vs ECJ** — `CAstRhinoTranslatorFactory` wires Rhino as the parser;
  `ECJClassLoaderFactory` wires ECJ for Java
- **No scope file for JS** — the JS drivers take a file path directly; there is no
  Primordial/Application split because JS has no notion of a standard library in the
  same sense
- **JSON output** — field-based JS drivers emit the call graph as JSON via
  `CallGraph2JSON`, useful for downstream tooling or visualization

---

## Pipeline Deep Dive — Data Forms & Clarifications

This section documents what the data actually looks like at each step of the Java analysis
pipeline, and clarifies concepts that are commonly confused. All examples use
`AnalysisClass.java` (the project's own `fibo` target) as the running subject.

---

### Two Java Pipelines

There are two distinct paths depending on whether you start from source or compiled bytecode.
Both converge at SSA IR.

```
Source-based (SourceDirCallGraph):
  .java
    │  ECJ (Java compiler, in-memory)
    ▼
  CAst IR        ← language-neutral AST tree, whole-program
    │  CHA builder
    ▼
  ClassHierarchy ← IClass / IMethod (signatures only, no bodies)
    │  cache.getIR(method, context)
    ▼
  SSA IR         ← flat basic blocks + value numbers, per method
    │  call graph builder
    ▼
  CallGraph      ← CGNode graph with call edges
    │  dataflow engine
    ▼
  Analysis       ← reaching defs, points-to, etc.

Bytecode-based (ScopeFileCallGraph / ConstructAllIRs):
  .class
    │  Shrike bytecode reader (no CAst step)
    ▼
  ClassHierarchy ← same IClass / IMethod structure
    │  cache.getIR(method, context)
    ▼
  SSA IR         ← same SSA form, different internal class (ShrikeIR vs AstIR)
    │
    ▼
  CallGraph → Analysis
```

**CAst IR only exists in the source-based path.** Bytecode-based analysis skips it entirely
and goes directly from `.class` to ClassHierarchy and then SSA IR.

---

### CAst IR vs SSA IR — They Are Different Things

These two terms are often conflated. They are separate artifacts at separate stages.

| | CAst IR | SSA IR |
|---|---|---|
| **Full name** | Common Abstract Syntax Tree | Static Single Assignment form |
| **Shape** | Nested tree (CAstNode hierarchy) | Flat basic blocks + 3-address instructions |
| **Purpose** | Language-neutral translation layer from source | Per-method form used for dataflow / analysis |
| **When built** | During ECJ or Rhino parsing (source path only) | On demand via `cache.getIR(method, context)` |
| **Scope** | Whole-program | Per-method |
| **Has method bodies?** | Yes — source AST | Yes — SSA instructions |
| **Path** | Source-based only | Both source-based and bytecode-based |

**CHA sits between CAst IR and SSA IR.** CHA knows that methods exist and their signatures —
it does not contain method bodies. IR is built from bytecode (or CAst-derived bytecode) on
demand when analysis needs to inspect a method.

---

### What ConstructAllIRs Actually Does

`ConstructAllIRs` does **not** convert CHA to IR. CHA and IR are independent artifacts.
The driver uses CHA as an **index** to enumerate every method, then triggers IR construction
for each one from bytecode.

```java
ClassHierarchy cha = ClassHierarchyFactory.make(scope);  // build the index
IAnalysisCacheView cache = new AnalysisCacheImpl(...);

for (IClass klass : cha) {                               // CHA as catalog
  for (IMethod method : klass.getDeclaredMethods()) {
    wipeSoftCaches();
    cache.getIR(method, Everywhere.EVERYWHERE);          // bytecode → SSA IR
  }
}
```

- `for (IClass klass : cha)` — CHA provides the class roster; no IR involved
- `cache.getIR(method, Everywhere.EVERYWHERE)` — reads bytecode, produces SSA IR, caches it
- `Everywhere.EVERYWHERE` — context-insensitive: one IR per method regardless of call site
- `ReferenceCleanser.wipeSoftCaches()` — periodically clears soft-reference caches to prevent
  OOM when processing large codebases; the IR cache uses soft references so the GC can reclaim
  them under pressure

---

### Concrete Data at Each Step

Using `AnalysisClass.down(int n)` as the running example throughout.

#### Step 0 — Source file (raw text)

```java
private static long down(int n) {
    if (n <= 1) return n;
    if (memo[n] != 0) return memo[n];
    memo[n] = down(n - 1) + down(n - 2);
    return memo[n];
}
```

#### Step 1 — CAst IR (source-based path only)

ECJ parses the source into a nested `CAstNode` tree. Shape is a tree, not instructions.

```
CAstNode[FUNCTION "down"]
 └── CAstNode[BLOCK_STMT]
      ├── CAstNode[IF_STMT]                      // if (n <= 1)
      │    ├── CAstNode[BINARY_EXPR <=]
      │    │    ├── CAstNode[VAR "n"]
      │    │    └── CAstNode[CONSTANT 1]
      │    └── CAstNode[RETURN]
      │         └── CAstNode[VAR "n"]
      │
      ├── CAstNode[IF_STMT]                      // if (memo[n] != 0)
      │    ├── CAstNode[BINARY_EXPR !=]
      │    │    ├── CAstNode[ARRAY_REF "memo" VAR"n"]
      │    │    └── CAstNode[CONSTANT 0L]
      │    └── CAstNode[RETURN]
      │         └── CAstNode[ARRAY_REF "memo" VAR"n"]
      │
      ├── CAstNode[ASSIGN]                       // memo[n] = down(n-1) + down(n-2)
      │    ├── CAstNode[ARRAY_REF "memo" VAR"n"]
      │    └── CAstNode[BINARY_EXPR +]
      │         ├── CAstNode[CALL "down" [BINARY_EXPR[-] VAR"n" CONST 1]]
      │         └── CAstNode[CALL "down" [BINARY_EXPR[-] VAR"n" CONST 2]]
      │
      └── CAstNode[RETURN]
           └── CAstNode[ARRAY_REF "memo" VAR"n"]
```

Bytecode-based path skips this step entirely.

#### Step 2 — AnalysisScope

A configuration object — declares what code to load, not a code transformation.

```
AnalysisScope {
  Loader[Primordial] → /tmp/wala-stdlib/java.base.jar
                       (java.lang.Object, java.util.*, java.io.*, ...)
  Loader[Application] → fibo/out-class/AnalysisClass.class
  exclusions: PatternsFilter([java/awt/.*, javax/swing/.*, ...])
}
```

#### Step 3 — ClassHierarchy

A type graph of `IClass` nodes. **No method bodies** — signatures and type metadata only.

```
IClassHierarchy {
  IClass[Primordial, Ljava/lang/Object]          ← root of everything
    └── IClass[Application, Lcom/sirisuk/AnalysisClass]
          classLoader : Application
          superclass  : Ljava/lang/Object
          interfaces  : []
          isAbstract  : false

          fields: [
            IField { name: "memo", type: [J, static: true }   // long[]
          ]

          methods: [
            IMethod { sig: "up(I)J",   static: true,  public: true  }
            IMethod { sig: "down(I)J", static: true,  public: false }
            IMethod { sig: "run()V",   static: true,  public: true  }
          ]
          // no method bodies here — IR is built separately on demand
}
```

#### Step 4 — SSA IR (per method, built on demand)

Produced by `cache.getIR(method, Everywhere.EVERYWHERE)`. Flat basic blocks; every value
number (`vN`) is defined exactly once. Phi (φ) nodes merge values at control-flow joins.

```
IR for: Lcom/sirisuk/AnalysisClass.down(I)J
SymbolTable:
  v1 = param[0]   (n, type int)
  v2 = constant 1
  v3 = constant 0L
  v4 = constant 2

BB0 (entry)
  s0: v5 = int_cmp_le  v1, v2          // n <= 1 ?
  s1: conditional_branch v5 → BB2      // yes → return n
      goto BB1

BB1
  s2: v6 = getstatic  [J  Lcom/sirisuk/AnalysisClass.memo
  s3: v7 = arrayload  long  v6[v1]     // memo[n]
  s4: v8 = long_cmp_ne  v7, v3         // memo[n] != 0 ?
  s5: conditional_branch v8 → BB3      // yes → return memo[n] early
      goto BB4

BB4 (compute and store)
  s6:  v9  = sub  int   v1, v2         // n - 1
  s7:  v10 = invoke <AnalysisClass.down(I)J>  v9    // down(n-1)
  s8:  v11 = sub  int   v1, v4         // n - 2
  s9:  v12 = invoke <AnalysisClass.down(I)J>  v11   // down(n-2)
  s10: v13 = add  long  v10, v12       // sum
  s11: v14 = getstatic  [J  Lcom/sirisuk/AnalysisClass.memo
  s12: arraystore  long  v14[v1] = v13 // memo[n] = sum
  s13: v15 = getstatic  [J  Lcom/sirisuk/AnalysisClass.memo
  s14: v16 = arrayload  long  v15[v1]
  s15: return  long  v16

BB2 (return n as long)
  s16: v17 = int2long  v1
  s17: return  long  v17

BB3 (early return memo[n])
  s18: v18 = getstatic  [J  Lcom/sirisuk/AnalysisClass.memo
  s19: v19 = arrayload  long  v18[v1]
  s20: return  long  v19
```

#### Step 5 — CallGraph

A directed graph of `CGNode` objects. Each node wraps a method + calling context and holds
a reference to its SSA IR from Step 4.

```
CallGraph {
  CGNode[0]  FAKE_ROOT
    ──→  CGNode[1]  Main.main([Ljava/lang/String;)V

  CGNode[1]  Lcom/sirisuk/Main.main
    call site → CGNode[2]  AnalysisClass.run()V

  CGNode[2]  Lcom/sirisuk/AnalysisClass.run()V
    call site → CGNode[3]  AnalysisClass.up(I)J
    call site → CGNode[4]  AnalysisClass.down(I)J
    call site → CGNode[5]  PrintStream.println(...)  [Primordial/lib]
    call site → CGNode[3]  AnalysisClass.up(I)J      (loop call)

  CGNode[3]  Lcom/sirisuk/AnalysisClass.up(I)J
    (no outgoing application calls)

  CGNode[4]  Lcom/sirisuk/AnalysisClass.down(I)J
    call site → CGNode[4]  AnalysisClass.down(I)J    ← self-recursive edge

  Each CGNode exposes:
    .getMethod()  → IMethod  (from CHA, Step 3)
    .getIR()      → IR       (SSA form, Step 4)
    .getContext() → Everywhere / CallStringContext
}
```

#### Step 6 — Analysis output (example: reaching definitions)

Built by the dataflow engine operating over the IR inside each `CGNode`. Data form: a map
from program point to the set of (variable, defining instruction) pairs that reach it.

```
CSReachingDefs for: AnalysisClass.down(I)J

  BB4, s7 [v10 = invoke down(v9)]:
    reaching defs of v9  ← s6: v9 = sub v1, v2   (defined in BB4)
    reaching defs of v1  ← entry param[0]

  BB4, s9 [v12 = invoke down(v11)]:
    reaching defs of v11 ← s8: v11 = sub v1, v4

  BB4, s10 [v13 = add v10, v12]:
    reaching defs of v10 ← s7: v10 = invoke down(v9)
    reaching defs of v12 ← s9: v12 = invoke down(v11)
```

---

### Master Summary

```
.java text
    │ ECJ (source path)
    ▼
CAst tree              ← nested CAstNode, whole-program, source path only
    │ CHA builder (both paths)
    ▼
IClassHierarchy        ← type graph: IClass + IMethod signatures, no bodies
    │ cache.getIR()    ← lazy, per method; reads bytecode or CAst-derived bytecode
    ▼
SSA IR (per method)    ← basic blocks, vN value numbers, phi nodes
    │ call graph builder
    ▼
CallGraph (CGNode[])   ← method nodes + call edges; each node holds its SSA IR
    │ dataflow engine
    ▼
Analysis result        ← reaching defs, points-to sets, thread escape, etc.
```

Each step enriches the model: CHA knows **what exists**, IR knows **what happens inside**,
CallGraph knows **who calls whom**, and analysis layers answer specific security or
correctness questions on top of all three.
