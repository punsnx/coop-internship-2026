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

## Project Structure

> **Owner: Stamp**

---

## Drivers Reference

> **Owner: Stamp**

---

## Prerequisites

> **Owner: Stamp**

---

## Loading Java Base Library

> **Owner: Bus**
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


---

## How to Run

> **Owner: Bus**

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


---

## Key WALA Concepts

> **Owner: Stamp**


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

> **Owner: Pao**