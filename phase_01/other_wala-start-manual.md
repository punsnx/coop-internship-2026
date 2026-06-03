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

---

## How to Run

> **Owner: Bus**



---

## Running Parameters and Switching

> **Owner: Bus**


---

## Scope File Format

> **Owner: Bus**


---

## Configuration Files

> **Owner: Bus**


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