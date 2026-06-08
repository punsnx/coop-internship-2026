# CSReachingDefsDriver

---

## Table of Contents

- [Functionality](#functionality)
- [Input & Output](#input--output)
- [Run Instructions](#run-instructions)
   - [Prerequisites](#prerequisites)
   - [Steps](#steps)
   - [Sample Input](#sample-input)
   - [Expected Output](#expected-output)
- [Reproducibility](#reproducibility)

---

## Functionality

`CSReachingDefsDriver` is a WALA driver that reads the bytecode of a Java application and performs an **interprocedural, context-sensitive dataflow analysis**. It computes reaching definitions across the entire application call graph using a tabulation solver. This analysis helps in understanding how values flow through different parts of the program based on method calls and variable uses, which is particularly useful for debugging and static analyses.

---

## Input & Output

* **Input:**
    - The file path to a compiled Java bytecode file (`.class` or `.jar`).
    - A scope file specifying the classpath and other settings if needed.

* **Output:**
    - A Call Graph representation of the environment where each node represents a method or function, and edges represent calls between these methods.
    - Statistics about the call graph such as number of nodes, edges, methods, and bytecode bytes.
---

## Run Instructions

### Prerequisites

1. **Java 21** — must be set as `$JAVA_HOME`. Verify: `echo $JAVA_HOME`
2. **Gradle wrapper** — bundled as `./gradlew` (no install needed)
3. **Target compiled to `.class` files** — bytecode of the Java program to analyze

### Dependencies (`build.gradle.kts`)

| Artifact                             | Version |
|--------------------------------------|----|
| `com.ibm.wala:com.ibm.wala.core`     | 1.7.2 |
| `com.ibm.wala:com.ibm.wala.util`     | 1.7.2 |
| `com.ibm.wala:com.ibm.wala.dataflow` | 1.7.2 |
| Java Version (tested)                | 21 |

### Steps to Run

0. **Install JDK 21**
   ```bash
   brew install oracle-jdk@21 && echo "export JAVA_HOME=/path/to/java_home" >> ~/.bashrc

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
     -PmainClass=com.ibm.wala.examples.drivers.ScopeFileCallGraph \
     --args="-scopeFile scope.txt -mainClass Lcom/example/Main"
   ```

   **NOTE:** For full automates steps 3–6 script: (run it at WALA-start project root directory)

   > **test.sh**
   > ```bash
   >
   > #!/bin/bash
   > if [ -d /tmp/wala-stdlib/ ]; then rm -rf /tmp/wala-stdlib* ;fi
   >
   > SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
   >
   > #JAVA_HOME="/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home"
   > JAVA_VERSION=$("$JAVA_HOME/bin/java" --version | awk 'NR==1{split($2,a,"."); print a[1]}')
   > sed -i '' "s/JavaLanguageVersion\.of(\([0-9]*\.*\)*)/JavaLanguageVersion.of($JAVA_VERSION)/" "$SCRIPT_DIR/build.gradle.kts"
   > echo "=== build.gradle.kts toolchain set to Java $JAVA_VERSION ==="
   >
   > "$SCRIPT_DIR/gradlew" -p "$SCRIPT_DIR" --stop
   > "$SCRIPT_DIR/gradlew" -p "$SCRIPT_DIR" --version | grep JVM
   > "$SCRIPT_DIR/gradlew" -p "$SCRIPT_DIR" clean compileJava
   >
   > BUILD_DIR="$(cd "$SCRIPT_DIR" && realpath "/absolute/path/to/buildpath")"
   > # --- WALA properties ---
   > mkdir -p src/main/resources/
   > echo "java_runtime_dir=/tmp/wala-stdlib" > src/main/resources/wala.properties
   >
   > # --- ScopeFile ---
   > SCOPE_FILE="$SCRIPT_DIR/scope.txt"
   >
   > {
   > echo "Primordial,Java,stdlib,none"
   > echo "Application,Java,binaryDir,$BUILD_DIR"
   > } > "$SCOPE_FILE"
   > 
   > JDK_HOME="${JAVA_HOME:?JAVA_HOME must be set}"
   > 
   > if [ ! -f /tmp/wala-stdlib/java.base.jar ]; then
   > echo "Building WALA stdlib cache (one-time)..."
   > mkdir -p /tmp/wala-stdlib/x
   > unzip -q "$JDK_HOME/jmods/java.base.jmod" -d /tmp/wala-stdlib/x || true
   > "$JDK_HOME/bin/jar" cf /tmp/wala-stdlib/java.base.jar \
   > -C /tmp/wala-stdlib/x/classes .
   > rm -rf /tmp/wala-stdlib/x
   > fi
   > 
   > # Test script below this section
   > ## --- CSReachingDefsDriver ---
   > "$SCRIPT_DIR/gradlew" -p "$SCRIPT_DIR" run \
   >   -PmainClass=com.ibm.wala.examples.drivers.CSReachingDefsDriver \
   >   --args="-scopeFile $SCOPE_FILE -mainClass LAnalysisClass"
   >  
   > ```
   ***JAVA_HOME*** uncomment if you have a different JDK version.

   ***Replace*** `BUILD_DIR` for your build_class_path and `-mainClass Lcom/example/Main` for the entrypoint for your application.

### Class name format

WALA uses JVM internal names. Prefix `L`, replace `.` with `/`:

| Java name                | WALA `-mainClass` argument |
|--------------------------|----------------------------|
| `Main` (default package) | `LMain`                    |
| `com.napongtorn.Main`    | `Lcom/napongtorn/Main`     |

---

### Program Code

**`CSReachingDefsDriver.java`**

```java
public class CSReachingDefsDriver {
  public static void main(String[] args)
      throws IOException,
          ClassHierarchyException,
          IllegalArgumentException,
          CallGraphBuilderCancelException {
    long start = System.currentTimeMillis();
    Properties p = CommandLine.parse(args);
    String scopeFile = p.getProperty("scopeFile");
    if (scopeFile == null) {
      throw new IllegalArgumentException("must specify scope file");
    }
    String mainClass = p.getProperty("mainClass");
    if (mainClass == null) {
      throw new IllegalArgumentException("must specify main class");
    }
    AnalysisScope scope =
        AnalysisScopeReader.instance.readJavaScope(
            scopeFile, null, CSReachingDefsDriver.class.getClassLoader());
    ExampleUtil.addDefaultExclusions(scope);
    IClassHierarchy cha = ClassHierarchyFactory.make(scope);
    System.out.println(cha.getNumberOfClasses() + " classes");
    System.out.println(Warnings.asString());
    Warnings.clear();
    AnalysisOptions options = new AnalysisOptions();
    Iterable<Entrypoint> entrypoints = Util.makeMainEntrypoints(cha, mainClass);
    options.setEntrypoints(entrypoints);
    // you can dial down reflection handling if you like
    options.setReflectionOptions(ReflectionOptions.NONE);
    AnalysisCache cache = new AnalysisCacheImpl();
    // other builders can be constructed with different Util methods
    CallGraphBuilder builder = Util.makeZeroOneContainerCFABuilder(options, cache, cha);
    //	    CallGraphBuilder builder = Util.makeNCFABuilder(2, options, cache, cha, scope);
    //	    CallGraphBuilder builder = Util.makeVanillaNCFABuilder(2, options, cache, cha, scope);
    System.out.println("building call graph...");
    CallGraph cg = builder.makeCallGraph(options, null);
    //	    System.out.println(cg);
    long end = System.currentTimeMillis();
    System.out.println("done");
    System.out.println("took " + (end - start) + "ms");
    System.out.println(CallGraphStats.getStats(cg));

    ContextSensitiveReachingDefs reachingDefs = new ContextSensitiveReachingDefs(cg, cache);
    TabulationResult<BasicBlockInContext<IExplodedBasicBlock>, CGNode, Pair<CGNode, Integer>>
        result = reachingDefs.analyze();
    ISupergraph<BasicBlockInContext<IExplodedBasicBlock>, CGNode> supergraph =
        reachingDefs.getSupergraph();
  }
}
```

### Example Input Code:
**`Main.java`**
```java
package com.sirisuk;

public class Main {
    public static void main(String[] args) {
        AnalysisClass.run();
    }
}
```

**`AnalysisClass.java`**
```java
package com.sirisuk;

public class AnalysisClass {

    public static long up(int n) {
        if (n <= 1) return n;
        long[] dp = new long[n + 1];
        dp[0] = 0; dp[1] = 1;
        for (int i = 2; i <= n; i++) dp[i] = dp[i - 1] + dp[i - 2];
        return dp[n];
    }

    private static long[] memo = new long[100];

    public static long down(int n) {
        if (n <= 1) return n;
        if (memo[n] != 0) return memo[n];
        memo[n] = down(n - 1) + down(n - 2);
        return memo[n];
    }

    public static void run() {
        int n = 10;
        System.out.println("Fibonacci DP Demo (n = " + n + ")");
        System.out.println("Bottom-up : " + up(n));
        System.out.println("Top-down  : " + down(n));
        System.out.println("\nSequence (0.." + n + "):");
        for (int i = 0; i <= n; i++) {
            System.out.print(up(i) + (i < n ? " " : "\n"));
        }
    }
}
```
**`wala.properties`**
```
java_runtime_dir=/tmp/wala-stdlib
```

**`scope.txt`**
```
Primordial,Java,stdlib,none
Application,Java,binaryDir,/Users/sirisuk/Desktop/SKDEV/CS/y4s1/internship/test-app/wala/fibo/target/classes
```

### Example Output:
```text
1. [Moderate] class com.ibm.wala.ipa.cha.ClassHierarchy$ClassExclusion : <Primordial,Lapple/security/AppleProvider$ProviderService> No superclass found for <Primordial,Lapple/security/AppleProvider$ProviderService> Superclass name Ljava/security/Provider$Service
2. [Moderate] class com.ibm.wala.ipa.cha.ClassHierarchy$ClassExclusion : <Primordial,Lapple/security/AppleProvider> No superclass found for <Primordial,Lapple/security/AppleProvider> Superclass name Ljava/security/Provider
3. [Moderate] class com.ibm.wala.ipa.cha.ClassHierarchy$ClassExclusion : <Primordial,Lapple/security/KeychainStore> No superclass found for <Primordial,Lapple/security/KeychainStore> Superclass name Ljava/security/KeyStoreSpi
4. [Moderate] class com.ibm.wala.ipa.cha.ClassHierarchy$ClassExclusion : <Primordial,Lcom/apple/laf/AquaButtonCheckBoxUI> No superclass found for <Primordial,Lcom/apple/laf/AquaButtonUI> Superclass name Ljavax/swing/plaf/basic/BasicButtonUI

building call graph...
done
took 3758ms
Call graph stats:
  Nodes: 7840
  Edges: 52220
  Methods: 5378
  Bytecode Bytes: 386601
```
---

## Reproducibility

### Result
✅ PASS

### Observations

I followed instructions with two corrections:

1. **Step 6 typo** — the run command listed `ScopeFileCallGraph` instead of `CSReachingDefsDriver`. Used the correct class name from the script block instead.
2. **`-mainClass` argument** — `LAnalysisClass` is missing the package path and causes an `UnimplementedError`. Changed to `Lcom/sirisuk/Main` which has the actual `main()` method.

With these corrections the driver ran successfully and produced the expected output structure.

### Difference from Reference

| Item | Reference | My Run |
|------|----------------|--------|
| Build result | ✅ SUCCESS | ✅ SUCCESS |
| `building call graph...done` | ✅ | ✅ |
| Nodes | 7840 | 8040 |
| Edges | 52220 | 40016 |
| Methods | 5378 | 5843 |
| Bytecode Bytes | 386601 | 411825 |
| Time | 3758ms | 2462ms |

The call graph statistics differ from the reference, which is expected, the numbers vary depending on the exact input classes, JDK version, and machine. The structure and behavior of the driver match the reference fully.

---