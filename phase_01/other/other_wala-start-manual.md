# WALA-start Developer Manual


 This manual documents everything needed to understand, set up, and run the WALA-start example programs.

---

## Table of Contents

- [Project Structure](#project-structure)
- [Drivers Reference](#drivers-reference)
- [Prerequisites](#prerequisites)
- [Loading Java Base Library](#loading-java-base-library)
- [How to Run](#how-to-run)
- [Running Parameters and Switching](#running-parameters-and-switching)
- [Scope File Format](#scope-file-format)
- [Configuration Files](#configuration-files)
- [Further Understanding: Analyzing a Different Target](#further-understanding-analyzing-a-different-target)
- [How It All Works](#how-it-all-works)
- [Our Troubleshooting](#our-troubleshooting)

---

## Project Structure

> **Owner: Sirisuk**

```
WALA-start/
├── build.gradle.kts          ← Gradle build config
├── settings.gradle.kts       ← Gradle project name
├── gradlew / gradlew.bat     ← Gradle wrapper (no install needed)
├── run_analysis.sh           ← main entry point: builds stdlib cache + runs a driver
├── scope.txt                 ← auto-generated WALA scope file
├── src/
│   └── main/
│       ├── java/com/ibm/wala/examples/
│       │   ├── drivers/          ← runnable analysis entry points
│       │   ├── analysis/         ← supporting analysis implementations
│       │   ├── analysisscope/    ← AnalysisScope setup example
│       │   └── util/             ← shared helpers
│       └── resources/
│           ├── wala.properties   ← WALA config (stdlib path, output dir)
│           ├── Exclusions.txt    ← class patterns WALA should skip
│           └── test-files/
│               └── fibo.js       ← sample JS target
└── out/                          ← generated output (PDF, dot, log)
```

---

## Drivers Reference

> **Owner: Sirisuk**

### drivers/ — Java Analysis

| File | Input | What it does |
|------|-------|--------------|
| `ScopeFileCallGraph.java` | scope file + `-mainClass` | Call graph from `.class` bytecode via scope file |
| `SourceDirCallGraph.java` | `-sourceDir` + `-mainClass` | Call graph from `.java` source — uses ECJ in-memory |
| `SourceDirCallGraphJavac.java` | `-sourceDir` + `-mainClass` | Same as above but uses `javac` instead of ECJ |
| `PrintTypeHierarchy.java` | classpath | Prints full class hierarchy tree to terminal |
| `PDFTypeHierarchy.java` | `-classpath` | Renders class hierarchy as a PDF graph diagram |
| `ConstructAllIRs.java` | scope file | Builds SSA IR for every method — validates scope is correct |
| `CSReachingDefsDriver.java` | scope file + class name | Context-sensitive reaching definitions dataflow |
| `DemandPointsToDriver.java` | scope file | Demand-driven points-to analysis — what each pointer may reference |

### analysis/ — Supporting Implementations

| File | What it does |
|------|--------------|
| `SimpleThreadEscapeAnalysis.java` | Detects objects that escape their creating thread |
| `dataflow/ContextInsensitiveReachingDefs.java` | Intraprocedural reaching defs — no call context |
| `dataflow/ContextSensitiveReachingDefs.java` | Interprocedural reaching defs with call context |
| `dataflow/IntraprocReachingDefs.java` | Basic intraprocedural reaching defs (simplest form) |

### analysisscope/ and util/

| File | What it does |
|------|--------------|
| `analysisscope/AnalysisScopeExample.java` | Example: how to build an `AnalysisScope` programmatically |
| `util/ExampleUtil.java` | Shared helpers — adds default class exclusions, builds scope from classpath |

### Resources and Root Configs

| File | Purpose |
|------|---------|
| `src/main/resources/wala.properties` | `java_runtime_dir` (stdlib JAR dir) and `output_dir` for PDF drivers |
| `src/main/resources/Exclusions.txt` | Regex patterns for JDK internals / GUI libs WALA should skip |
| `build.gradle.kts` | JDK 21 toolchain, WALA 1.7.2 deps, ECJ 3.36.0 pin |
| `scope.txt` | Auto-generated: `Primordial,Java,stdlib,none` + `Application` entry |

---

## Prerequisites

> **Owner: Prawit**

| Requirement | Detail |
|-------------|--------|
| Java 21 | Must be set as `$JAVA_HOME`. Check: `echo $JAVA_HOME` |
| Gradle | Included via `./gradlew` wrapper — no install needed |
| `unzip` | Standard macOS/Linux tool — pre-installed |
| Graphviz | Required by `PDFTypeHierarchy` to render the type hierarchy as a PDF. Install: `brew install graphviz`. Verify: `which dot` |
| Python 3 | Required by `run.py` runner script on some setups. Check: `python3 --version` |

Set JAVA_HOME if not already set:

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home
```

---

## Loading Java Base Library

> **Owner: Napongtorn**

When working with WALA (Whole Program Analysis Library), you often need to include and configure the Java base library, which serves as a foundation for standard classes provided by Oracle's JDK. This section explains how to properly load the Java base library into your WALA setup:

### Understanding the Importance of the Java Base Library

The Java base library (`java.base`) contains essential classes and functionalities that are used across many Java programs, such as `java.lang.*`, `java.util.*`, and others. Including this library in your WALA analysis ensures that the standard libraries are available for use in all class files within the scope you define.

### Steps to Load the Java Base Library in WALA

1. **Set Up Your Environment**: Ensure that your environment is correctly configured to include the Java base library. This might involve setting up properties or environment variables to point to the correct directory where the JAR file of `java.base` is located.

2. **Configuration via Properties File**: Use a configuration file (e.g., `wala.properties`) within your WALA project to specify the path to the Java base library. This can be done by setting the property `java_runtime_dir` to the directory where the JAR file is stored. For example:
### Additional Context on WALA’s Role in the Java Ecosystem

Beyond its role as a static analysis tool, WALA (Whole Program Analysis Library) plays a pivotal part in bridging the gap between compiled bytecode and runtime execution. This section delves into how WALA contributes to the broader Java ecosystem by enabling advanced analyses that are best performed at the bytecode level:

- **Independent of Runtime**: WALA operates independently of JVM runtime, allowing for detailed analysis without executing the code, which is particularly valuable in scenarios where performance or security restrictions prevent full execution.

- **Flexibility and Extensibility**: As a flexible tool, WALA can be extended to analyze various dimensions beyond just bytecode reading and model construction. It might support additional features like type inference, dependency graph generation, or even dynamic analysis based on runtime behavior that is not easily accessible through source code alone.

- **Cross-Cutting Applications**: The insights provided by WALA are not limited to software development but can be used for applications such as software optimization, bug tracking across different versions of a program, and in educational environments where understanding the underlying bytecode structure aids learning outcomes.

By operating at this unique intersection between compilation and runtime execution, WALA significantly enhances the depth and breadth of analyses possible within the Java environment, providing valuable insights that are integral to modern software engineering practices.

---

## How to Run

> **Owner: Napongtorn**

0. **Install JDK 21**
   ```bash
   brew install oracle-jdk@21 && echo "export JAVA_HOME=/path/to/java_home" >> ~/.bashrc
   ```

1. **Clone and build the project:**
   ```bash
   git clone https://github.com/wala/WALA-start.git
   cd WALA-start
   export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home
   ./gradlew compileJava
   ```

2. **Compile the sample target application:**
   ```bash
   javac -d target/classes \
     src/main/java/com/example/*.java
   ```

3. **Build the WALA stdlib cache (one-time setup):**

   Java 9+ no longer ships `rt.jar`; WALA requires a JAR of the standard library. Extract it once from `java.base.jmod`:
   ```bash
   mkdir -p /tmp/wala-stdlib/x
   unzip -q "$JAVA_HOME/jmods/java.base.jmod" -d /tmp/wala-stdlib/x || true
   "$JAVA_HOME/bin/jar" cf /tmp/wala-stdlib/java.base.jar \
       -C /tmp/wala-stdlib/x/classes .
   rm -rf /tmp/wala-stdlib/x
   ```
   This only needs to run once. Delete `/tmp/wala-stdlib` to force a rebuild (e.g. after changing JDK).

4. **Set the environment variable for WALA:**

   `src/main/resources/wala.properties` must point to the stdlib cache directory:
   ```properties
   java_runtime_dir=/tmp/wala-stdlib
   ```

5. **Create `scope.txt`** pointing to the compiled target:
   ```
   Primordial,Java,stdlib,none
   Application,Java,binaryDir,/absolute/path/to/buildpath
   ```
   Scope file format: `Loader,Language,type,path`
  - `Primordial` — Java standard library
  - `Application` — your code
  - `binaryDir` — directory of `.class` files; use `classFile` for a single file

6. **Run the driver:**
   ```bash
   ./gradlew run \
   # Choose the driver you want to run
     -PmainClass=com.ibm.wala.examples.drivers.ScopeFileCallGraph \ 
     --args="driver parameters"
   ```

---

## Running Parameters and Switching

> **Owner: Napongtorn**

### Parameters for each drivers

| Driver | Input |
|--------|-------|
| `SourceDirCallGraph` | Java **source** directory (`-sourceDir`) + class name (`-mainClass`) |
| `ScopeFileCallGraph` | Scope file (`-scopeFile`) + class name (`-mainClass`) |
| `PrintTypeHierarchy` | Classpath (`.class` file or JAR) |
| `PDFTypeHierarchy` | Classpath (`-classpath`) |
| `ConstructAllIRs` | Scope file |
| `CSReachingDefsDriver` | Scope file + class name |
| `DemandPointsToDriver` | Scope file |

```bash
# --- ScopeFileCallGraph ---
"$SCRIPT_DIR/gradlew" -p "$SCRIPT_DIR" run \
  -PmainClass=com.ibm.wala.examples.drivers.ScopeFileCallGraph \
  --args="-scopeFile $SCOPE_FILE -mainClass LAnalysisClass"
```

---

## Scope File Format

> **Owner: Napongtorn**

`scope.txt` is a plain text file with one entry per line:

```
Loader,Language,type,path
```

| Field | Values | Detail |
|-------|--------|-------|
| Loader | `Primordial` (stdlib), `Application` (your code), `Extension` (libraries) | refers to Java ClassLoader for locating and loading the class bytes |
| Language | `Java` | specify the programming language used when generating the bytecode |
| type | `stdlib`, `classFile`, `jarFile`, `binaryDir` | describe the nature of the resources being loaded |
| path | file path, or `none` for `stdlib` (uses running JVM) | specify the location where the JVM can find the tool |

Example `scope.txt`:

```
Primordial,Java,stdlib,none
Application,Java,classFile,/path/to/AnalysisClass.class
```

---

## Configuration Files

> **Owner: Napongtorn**

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

## Further Understanding: Analyzing a Different Target

> **Owner: Pichaphop**

To analyze a target beyond the standard Java library, WALA needs the full type context of that target — meaning all external dependencies must be provided so it can resolve types and build the class hierarchy. In this example, we use WALA to analyze itself.

### WALA Self-Analysis Notes

#### 1. Extra JARs Required

`SourceDirCallGraph.java` imports WALA framework types that don't live in `java.base.jar`. Copy them into `/tmp/wala-stdlib/` once:

```bash
if [ ! -f /tmp/wala-stdlib/.libs-ready ]; then
    find ~/.gradle/caches/modules-2/files-2.1/com.ibm.wala \
        -name "*.jar" ! -name "*-sources.jar" ! -name "*-javadoc.jar" \
        -exec cp {} /tmp/wala-stdlib/ \;
    find ~/.gradle/caches/modules-2/files-2.1/org.eclipse.jdt \
        -name "*.jar" ! -name "*-sources.jar" ! -name "*-javadoc.jar" \
        -exec cp {} /tmp/wala-stdlib/ \;
    touch /tmp/wala-stdlib/.libs-ready
fi
```

`WalaProperties.getJarsInDirectory()` picks them up automatically — no config change needed.

---

#### 2. Two Args That Must Both Be Correct

##### `-sourceDir` → point to `.java` files, not `.class` files

```
src/main/java/          ✓  ECJ parses .java source
build/classes/java/main ✗  ECJ cannot parse bytecode
```

#### `-mainClass` → use WALA's internal type name format

Prefix `L`, replace `.` with `/`:

```
package com.ibm.wala.examples.drivers;
→ Lcom/ibm/wala/examples/drivers/SourceDirCallGraph  ✓

LSourceDirCallGraph  ✗  (no package → class not found)
```

---

### 3. `ExpressionMethodReference` Fix

WALA 1.7.2's JDT translator doesn't handle method references. Replace with a lambda:

```java
// Before — crashes WALA's translator
options.getSSAOptions().setDefaultValues(SymbolTable::getDefaultValue);

// After — semantically identical, works fine
options.getSSAOptions().setDefaultValues((symtab, vn) -> symtab.getDefaultValue(vn));
```

**File:** `src/main/java/com/ibm/wala/examples/drivers/SourceDirCallGraph.java`, line 102.

---

## How It All Works
### Java Compilation Pipeline

### 1. Overview — Java Execution Stack

```
.java source
    │  javac (compiler)           ← platform-independent compilation
    ▼
.class (JVM bytecode)             ← stack-based instruction set; portable
    │
    │  ◄── WALA operates here ──►  reads bytecode as data, never executes it
    │
    │  JVM (interpreter + JIT)    ← Just-In-Time compilation at runtime
    ▼
native machine code               ← CPU-specific; actually executed
```

**Compiler principle:** `javac` performs lexing → parsing → type-checking → code generation, producing platform-independent bytecode. The JVM then applies JIT to hot code paths at runtime. WALA intercepts between these two phases — it reasons statically about what the bytecode *would* do without running it.

---

### 2. Two Analysis Paths in WALA

WALA supports two entry points depending on whether you have source or compiled bytecode. Both converge at SSA IR.

```
Source-based (SourceDirCallGraph):        Bytecode-based (ScopeFileCallGraph):
  .java                                     .class
    │  ECJ (compiler, in-memory)              │  Shrike bytecode reader
    ▼                                         ▼
  CAst IR  (tree, source path only)         ClassHierarchy  ← no CAst step
    │                                         │
    ▼                                         ▼
  ClassHierarchy                           SSA IR (per method, on demand)
    │                                         │
    ▼                                         ▼
  SSA IR → CallGraph → Analysis           CallGraph → Analysis
```

---

### 3. Intermediate Representations

Two distinct IRs exist at different stages — commonly confused but separate artifacts.

| | CAst IR | SSA IR |
|---|---|---|
| **Full name** | Common Abstract Syntax Tree | Static Single Assignment form |
| **Shape** | Nested tree (CAstNode hierarchy) | Flat basic blocks + 3-address instructions |
| **Scope** | Whole-program | Per-method (built on demand) |
| **When built** | During ECJ/Rhino parsing | `cache.getIR(method, context)` |
| **Path** | Source-based only | Both source and bytecode paths |

**SSA rule:** every variable is assigned exactly once; each re-assignment gets a new version number. At control-flow join points, a **φ (phi) function** merges versions.

```java
// Original
x = 1;
x = x + 2;
if (...) x = 5;

// SSA form
x₁ = 1
x₂ = x₁ + 2
x₃ = 5
x₄ = φ(x₂, x₃)   ← x₄ is x₂ or x₃ depending on which branch ran
```

Phi nodes make dataflow (e.g. reaching definitions, points-to) simple graph traversals instead of iterative fixpoints.

---

### 4. ClassHierarchy — Between CAst IR and SSA IR

CHA is **not** an IR — it is a type index. It knows that classes and methods *exist* with their signatures, but contains **no method bodies**.

```
IClassHierarchy {
  IClass[Primordial, Ljava/lang/Object]   ← root of everything
    └── IClass[Application, Lcom/sirisuk/AnalysisClass]
          methods: [up(I)J, down(I)J, run()V]   // signatures only — no bodies
}
```

SSA IR is built separately, lazily, per method from bytecode via `cache.getIR(method, context)`.

---

### 5. rt.jar → .jmod (Java 8 → Java 9+)

WALA must resolve every type in your code — including `java.lang.Object`, the root of all class hierarchies. Without the standard library, CHA construction fails immediately:

```
ClassHierarchyException: failed to load root <Primordial,Ljava/lang/Object>
```

| Era | Format | Layout |
|-----|--------|--------|
| Java ≤ 8 | `rt.jar` (~60 MB monolithic JAR) | `jre/lib/rt.jar` — entire stdlib in one file |
| Java 9+ | `.jmod` files (~70 named modules) | `jmods/java.base.jmod`, `java.sql.jmod`, … |

**Why Java moved:** `rt.jar` had no encapsulation (internal `sun.*` APIs were public), caused slow startup (full scan every time), and couldn't be updated incrementally. **Project Jigsaw** (Java 9) split it into modules with explicit `exports`/`requires` declarations.

**Why WALA can't read `.jmod` directly:** `WalaProperties.getJarsInDirectory()` scans for `*.jar` only, and `new JarFile()` rejects `.jmod`'s non-standard 4-byte magic header.

**Workaround — extraction shim:**
```bash
# Unzip the .jmod (it's a ZIP with a 4-byte custom header — || true ignores header warning)
unzip -q "$JAVA_HOME/jmods/java.base.jmod" -d /tmp/wala-stdlib/x || true
# Repack classes/ as a standard JAR
jar cf /tmp/wala-stdlib/java.base.jar -C /tmp/wala-stdlib/x/classes .
```

The content is identical to what `rt.jar` contained for the base module — only the container format changed. Run once; delete `/tmp/wala-stdlib` to force a rebuild.

---

### 6. WALA's Full Pipeline (Summary)

```
.java / .class
    │ ECJ (source) or Shrike (bytecode)
    ▼
CAst IR (source path only) / direct bytecode (bytecode path)
    │
    ▼
AnalysisScope      ← declares what to load: Primordial (stdlib) + Application (your code)
    │
    ▼
ClassHierarchy     ← type graph: IClass + IMethod signatures, no bodies
    │ cache.getIR() — lazy, per method
    ▼
SSA IR             ← basic blocks, vN value numbers, φ nodes
    │ call graph builder (0-CFA / 0-1-CFA / n-CFA)
    ▼
CallGraph          ← CGNode[method + context] with call edges; each node holds its SSA IR
    │ dataflow engine
    ▼
Analysis result    ← reaching defs, points-to sets, security vulnerabilities, etc.
```

Each step enriches the model: CHA knows **what exists**, IR knows **what happens inside**, CallGraph knows **who calls whom**, and analysis layers answer specific security or correctness questions on top of all three.

---

## Our Troubleshooting

> **Owner: Prawit**

### Setup & Environment

### `JAVA_HOME must be set`

**Symptom:**
```
JAVA_HOME must be set
```
**Cause:** `run_analysis.sh` script checks for `$JAVA_HOME` before doing anything else. If it is not exported in your shell environment, the script exits immediately.

**Fix:**
```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home
```
Add this line to your `~/.zshrc` or `~/.bashrc` to make it permanent.

---

### `J2SE_DIR not set`

**Symptom:**
```
J2SE_DIR not set
```
**Cause:** `WalaProperties` class could not find `wala.properties` on the classpath. This file must exist at `src/main/resources/wala.properties` so Gradle puts it on the classpath automatically.

**Fix:** Verify the file exists:
```bash
cat src/main/resources/wala.properties
```
It should contain:
```properties
java_runtime_dir=/tmp/wala-stdlib
output_dir=./out
```
If the file is missing, recreate it with the content above.

---

### Script stops silently after `"Building WALA stdlib cache..."`

**Symptom:** The script prints `"Building WALA stdlib cache (one-time)..."` and then stops with no error message.

**Cause:** `unzip` exits with a non-zero code when it encounters the 4-byte magic header in `.jmod` files (which are ZIP archives with a non-standard header). The shell's default `set -e` behaviour treats this as a failure and halts the script.

**Fix:** The `unzip` line in `run_analysis.sh` must end with `|| true`:
```bash
unzip -q "$JDK_HOME/jmods/java.base.jmod" -d /tmp/wala-stdlib/x || true
```
This tells the shell to ignore the non-zero exit from `unzip` and continue.

---

### Class Hierarchy & Stdlib

### `ClassHierarchyException: failed to load root Ljava/lang/Object`

**Symptom:**
```
ClassHierarchyException: failed to load root <Primordial,Ljava/lang/Object>
```
**Cause:** WALA could not find any usable JARs in `java_runtime_dir` (`/tmp/wala-stdlib`). This happens when the stdlib cache is missing, empty, or corrupted.

**Fix:** Delete the cache and let the script rebuild it:
```bash
rm -rf /tmp/wala-stdlib
./run_analysis.sh
```

---

### `NoSuchMethodError: Scanner.getNextToken`

**Symptom:**
```
java.lang.NoSuchMethodError: org.eclipse.jdt.core.compiler.InvalidInputException Scanner.getNextToken()
```
**Cause:** Gradle resolved a newer version of ECJ (`org.eclipse.jdt:ecj`) that is incompatible with the version of `jdt.core` WALA depends on.

**Fix:** Confirm `build.gradle.kts` pins ECJ to `3.36.0`:
```kotlin
configurations.all {
  resolutionStrategy {
    force("org.eclipse.jdt:ecj:3.36.0")
  }
}
```
After editing, re-run with these commands:
```bash
./gradlew build
./run_analysis.sh
```

---

### SourceDirCallGraph — Source & Entry Point Errors

### `could not resolve <Source, LClassName, main>`

**Symptom:**
```
could not resolve < Source, LAnalysisClass, main >
```
or
```
UNREACHABLE from any entry point
```

**Cause:** One or both of the following:
1. `-sourceDir` is pointing at a directory of `.class` files instead of `.java` source files. ECJ is a source parser and cannot read bytecode
2. `-mainClass` is missing the full package path. So WALA needs the JVM internal name including the package

**Fix:**
- `-sourceDir` must point at the root of your `.java` source tree:
  ```
  src/main/java/           ← this is correct
  build/classes/java/main  ← this is incorrect (bytecode, not source)
  ```
- `-mainClass` must use the full WALA internal type name. Read the `package` declaration from the `.java` file:
  ```
  package com.ibm.wala.examples.drivers;
  class SourceDirCallGraph { ... }
  ```
  Becomes:
  ```
  Lcom/ibm/wala/examples/drivers/SourceDirCallGraph
  ```
  Rule: prefix `L`, replace `.` with `/`, no `.class` suffix.

---

### `UnimplementedError: Unhandled JDT node type ExpressionMethodReference`

**Symptom:**
```
com.ibm.wala.util.debug.UnimplementedError:
  Unhandled JDT node type org.eclipse.jdt.core.dom.ExpressionMethodReference
```
**Cause:** WALA 1.7.2's `JDTJava2CAstTranslator` does not implement handling for Java 8 `::` method reference syntax. This is a missing feature in the translator — not a dependency or version problem.

**Affected line** in `SourceDirCallGraph.java`:
```java
// Line 102, unsupported by WALA 1.7.2 source translator
options.getSSAOptions().setDefaultValues(SymbolTable::getDefaultValue);
```

**Fix:** Replace the method reference with an equivalent lambda:
```java
// Semantically identical, works with WALA's translator
options.getSSAOptions().setDefaultValues((symtab, vn) -> symtab.getDefaultValue(vn));
```

**File changed:** `src/main/java/com/ibm/wala/examples/drivers/SourceDirCallGraph.java` line 102.