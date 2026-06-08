# WALA-start Developer Manual


> This manual documents everything needed to understand, set up, and run the WALA-start example programs.

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
- [Key WALA Concepts](#key-wala-concepts)
- [Java Compilation Pipeline](#java-compilation-pipeline)
- [How It All Works — Deep Dive](#how-it-all-works--deep-dive)
- [Java rt Deprecated (replace with jmods)](#java-rt-deprecated-replace-with-jmods)
- [Our Troubleshooting](#our-troubleshooting)

---

## Important WALA-start Project Structure
> **Owner: Stamp**



```
src/main/java/com/ibm/wala/examples/
│
├── /analysis                — Analysis technique algorithms (used in drivers)
│   ├── /dataflow
│   │   ├── ContextInsensitiveReachingDefs
│   │   ├── ContextSensitiveReachingDefs
│   │   └── IntraprocReachingDefs
│   └── SimpleThreadEscapeAnalysis
│
├── /analysisscope           — Examples of how to construct an analysis scope (2 ways)
│   └── AnalysisScopeExample
│
├── /drivers                 — Code examples to run input and get output
│   ├── BoundedJSCallGraphDriver
│   ├── ConstructAllIRs
│   ├── CSReachingDefsDriver
│   ├── DemandPointsToDriver
│   ├── FieldBasedJSCallGraphDriver
│   ├── JSCallGraphDriver
│   ├── PDFTypeHierarchy
│   ├── PrintTypeHierarchy
│   ├── ScopeFileCallGraph
│   └── SourceDirCallGraph
│
└── /util                    — Utility classes with helper methods for drivers
    └── ExampleUtil          — Adds default exclusions to analysis scope
                               (prevents scope from becoming too large or over-approximated)

build.gradle.kts             — Gradle build configuration and variables
```

---

## Drivers Reference

> **Owner: Stamp**

---

## Prerequisites

> **Owner: Stamp**

---

## Loading Java Base Library

> **Owner: Bus**

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

> **Owner: Bus**

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

> **Owner: Bus**

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

> **Owner: Bus**

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

> **Owner: Bus**

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

> **Owner: Stamp**
>



---

## Key WALA-start Concepts

> **Owner: Stamp**

Most driver examples are designed to create a Call Graph. Other examples focus on generating foundational structures, such as the Intermediate Representation (IR) in Static Single Assignment (SSA) form, or the Class Hierarchy (the relationships between classes). Additionally, some examples go further and implement complete static analyzers.

### The WALA Analysis Pipeline

```
AnalysisScope → ClassLoader → ClassHierarchy → AnalysisOptions → CallGraphBuilder → CallGraph → Analysis
```

| Step            | Description                                                                                                                                                 |
|-----------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------|
| AnalysisScope   | Defines the domain and boundaries of your analysis                                                                                                          |
| ClassLoader     | Loads the program's classes based on the defined scope                                                                                                      |
| ClassHierarchy  | Builds the structural relationships between the loaded classes                                                                                              |
| AnalysisOptions | Configures specific options for the analysis, such as defining entry points or setting initial values                                                       |
| CallGraphBuilder| Constructs the call graph. WALA allows you to choose from various algorithms depending on the required precision (e.g., CHA, RTA, 0-CFA, 0-1-CFA, or n-CFA)|
| CallGraph       | The resulting graphical structure representing the method calls within the program                                                                          |
| Analysis        | The final stage where specific, in-depth analyses are executed using the generated structures (e.g., Dataflow Analysis, Pointer Analysis)                   |
---

## Java Compilation Pipeline

> **Owner: Stamp**


---

## How It All Works — Deep Dive

> **Owner: Stamp**


---

## Java rt Deprecated (replace with jmods)

> **Owner: Stamp**


---

## Our Troubleshooting

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