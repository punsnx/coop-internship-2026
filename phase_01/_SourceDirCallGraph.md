# SourceDirCallGraph

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

`SourceDirCallGraph` is a WALA driver that builds a Call Graph based on a directory and entry point provided by the user. The source directory must contain Java source code (.java files), as this driver uses ECJ (Eclipse Compiler for Java) which cannot parse bytecode directly.

---

## Input & Output

* **Input:**
    - A directory path containing Java source code files (`-sourceDir`)
    - A class name for the entry point (`-mainClass`: the driver will use the main() method of the provided class)
* **Output:**
    - A Call Graph (default: 0-1-CFA) and its statistics

---
## Run Instructions

### Prerequisites

1. **Java 21** — must be set as `$JAVA_HOME`. Verify: `echo $JAVA_HOME`
2. **Gradle wrapper** — bundled as `./gradlew` (no install needed)
3. **Target compiled to `.class` files** — bytecode of the Java program to analyze

### Dependencies (`build.gradle.kts`)

| Artifact                           | Version |
|------------------------------------|----|
| `com.ibm.wala:com.ibm.wala.core`   | 1.7.2 |
| `com.ibm.wala:com.ibm.wala.util`   | 1.7.2 |
| `com.ibm.wala:com.ibm.wala.shrike` | 1.7.2 |
| Java Version (tested)              | 21 |

### Steps

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
2. **Configure build.gradle.kts:**
    ```kotlin
        java.toolchain.languageVersion = JavaLanguageVersion.of(21)   // use system JDK 21
        
        configurations.all {
          resolutionStrategy {
            force("org.eclipse.jdt:ecj:3.36.0")   // pin ECJ to match jdt.core version
          }
        }
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

5. **Run the driver:**
   ```bash
   ./gradlew run \
     -PmainClass=com.ibm.wala.examples.drivers.SourceDirCallGraph \
     --args="-sourceDir directory/path -mainClass Lcom/example/Main"
   ```  
    **NOTE:** For full automated steps 3–5 script: (run it at WALA-start project root directory)
   
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
   > # --- SourceDirCallGraph ---
   > "$SCRIPT_DIR/gradlew" -p "$SCRIPT_DIR" run \
   > -PmainClass=com.ibm.wala.examples.drivers.SourceDirCallGraph \
   > --args="-sourceDir $BUILD_DIR -mainClass Lcom/example/Main"
   >  
   > ```
   ***JAVA_HOME*** uncomment if you have a different JDK version.

   ***Replace*** `BUILD_DIR` for your build_class_path and `-mainClass Lcom/example/Main` for the entrypoint for your application.
### Class name format

WALA uses JVM internal names. Prefix `L`, replace `.` with `/`:

| Java name | WALA `-mainClass` argument |
|-----------|--------------------------|
| `Main` (default package) | `LMain` |
| `com.example.Main` | `Lcom/example/Main` |

---

### Program Code

**`SourceDirCallGraph.java`**
```java

package com.ibm.wala.examples.drivers;

import com.ibm.wala.cast.ir.ssa.AstIRFactory;
import com.ibm.wala.cast.java.client.impl.ZeroOneContainerCFABuilderFactory;
import com.ibm.wala.cast.java.ipa.callgraph.JavaSourceAnalysisScope;
import com.ibm.wala.cast.java.translator.jdt.ecj.ECJClassLoaderFactory;
import com.ibm.wala.classLoader.ClassLoaderFactory;
import com.ibm.wala.classLoader.SourceDirectoryTreeModule;
import com.ibm.wala.classLoader.SourceFileModule;
import com.ibm.wala.core.util.warnings.Warnings;
import com.ibm.wala.ipa.callgraph.AnalysisCacheImpl;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisOptions.ReflectionOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilder;
import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.callgraph.CallGraphStats;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.IAnalysisCacheView;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.properties.WalaProperties;
import com.ibm.wala.ssa.SymbolTable;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.io.CommandLine;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;
import java.util.jar.JarFile;
import com.ibm.wala.core.java11.JrtModule;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.classLoader.CallSiteReference;
import java.util.Set;

/**
 * Driver that constructs a call graph for an application specified as a directory of source code.
 * Example of using the JDT front-end based on ECJ. Useful for getting some code to copy-paste.
 */
public class SourceDirCallGraph {

  @FunctionalInterface
  public interface Processor {
    public void process(CallGraph CG, CallGraphBuilder<?> builder, long time);
  }

  /**
   * Usage: SourceDirCallGraph -sourceDir file_path -mainClass class_name
   *
   * <p>If given -mainClass, uses main() method of class_name as entrypoint. Class name should start
   * with an 'L'.
   *
   * <p>Example args: -sourceDir /tmp/srcTest -mainClass LFoo
   */
  public static void main(String[] args)
      throws CallGraphBuilderCancelException, IOException, WalaException {
    System.out.println(Arrays.toString(args));
    new SourceDirCallGraph()
        .doit(
            args,
            (cg, builder, time) -> {
              System.out.println("done");
              System.out.println("took " + time + "ms");
              System.out.println(CallGraphStats.getStats(cg));

            });
  }

  protected ClassLoaderFactory getLoaderFactory(AnalysisScope scope) {
    return new ECJClassLoaderFactory(scope.getExclusions());
  }

  public void doit(String[] args, Processor processor)
      throws CallGraphBuilderCancelException, IOException, WalaException {
    long start = System.currentTimeMillis();
    Properties p = CommandLine.parse(args);
    String sourceDir = p.getProperty("sourceDir");
    String mainClass = p.getProperty("mainClass");
    String appPackage = p.getProperty("appPackage", derivePackageFromMainClass(mainClass));

    AnalysisScope scope = new JavaSourceAnalysisScope();
    // add standard libraries to scope
    // Old way
    // String[] stdlibs = WalaProperties.getJ2SEJarFiles();
    //   for (String stdlib : stdlibs) {
    //     scope.addToScope(ClassLoaderReference.Primordial, new JarFile(stdlib));
    //   }
    // New way
      scope.addToScope(ClassLoaderReference.Primordial, new JrtModule("java.base"));
    // add the source directory
    File root = new File(sourceDir);
    if (root.isDirectory()) {
      scope.addToScope(JavaSourceAnalysisScope.SOURCE, new SourceDirectoryTreeModule(root));
    } else {
      String srcFileName = sourceDir.substring(sourceDir.lastIndexOf(File.separator) + 1);
      assert root.exists() : "couldn't find " + sourceDir;
      scope.addToScope(
          JavaSourceAnalysisScope.SOURCE, new SourceFileModule(root, srcFileName, null));
    }

    // build the class hierarchy
    IClassHierarchy cha = ClassHierarchyFactory.make(scope, getLoaderFactory(scope));
    System.out.println(cha.getNumberOfClasses() + " classes");
    System.out.println(Warnings.asString());
    Warnings.clear();
    AnalysisOptions options = new AnalysisOptions();
    Iterable<Entrypoint> entrypoints = getEntrypoints(mainClass, cha);
    options.setEntrypoints(entrypoints);
    options.getSSAOptions().setDefaultValues(SymbolTable::getDefaultValue);
    // you can dial down reflection handling if you like
    options.setReflectionOptions(ReflectionOptions.NONE);
    IAnalysisCacheView cache =
        new AnalysisCacheImpl(AstIRFactory.makeDefaultFactory(), options.getSSAOptions());
    // CallGraphBuilder builder = new ZeroCFABuilderFactory().make(options, cache,
    // cha, scope,
    // false);
    CallGraphBuilder<?> builder = new ZeroOneContainerCFABuilderFactory().make(options, cache, cha);
    System.out.println("building call graph...");
    CallGraph cg = builder.makeCallGraph(options, null);
    long end = System.currentTimeMillis();

    processor.process(cg, builder, end - start);
    printCallGraph(cg, appPackage);

  }

  protected Iterable<Entrypoint> getEntrypoints(String mainClass, IClassHierarchy cha) {
    return Util.makeMainEntrypoints(JavaSourceAnalysisScope.SOURCE, cha, new String[] {mainClass});
  }
  // Visualize Call graph
  private static void printCallGraph(CallGraph cg , String appPackage) {
    System.out.println("\n===== CALL GRAPH =====");

    for (CGNode node : cg) {
      // Only show app classes
      if (!isMyAppCode(node, appPackage)) continue;

      System.out.println("\nMETHOD: " + prettyMethod(node));

      for (CallSiteReference site :
              (Iterable<CallSiteReference>) () -> node.iterateCallSites()) {

        Set<CGNode> targets = cg.getPossibleTargets(node, site);

        for (CGNode target : targets) {
          // Only show targets that are app code
          if (!isMyAppCode(target, appPackage)) continue;
          System.out.println("  --> calls: " + prettyMethod(target));
        }
      }
    }
    System.out.println("\n===== END =====");
  }
  //Helpers:
  // Check app code
  private static boolean isMyAppCode(CGNode node, String appPackage) {
    String className = node.getMethod().getDeclaringClass()
            .getName().toString();
    return className.startsWith(appPackage);
  }

  // turn a CGNode into a readable "ClassName.methodName()" string
  private static String prettyMethod(CGNode node) {
    String className = node.getMethod().getDeclaringClass()
            .getName().toString();
    // strip the leading L and replace / with .
    className = className.substring(1).replace("/", ".");
    String methodName = node.getMethod().getName().toString();
    return className + "." + methodName + "()";
  }

  // Extract mainClass argument to create package variable (use for filtering app class)
  private static String derivePackageFromMainClass(String mainClass) {
    int lastSlash = mainClass.lastIndexOf('/');
    if (lastSlash != -1) {
      return mainClass.substring(0, lastSlash + 1);
    }
    // No package (default package), match the class directly
    return mainClass;
  }

}


```

### Sample Input

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

---

### Expected Output

```text

6401 classes

building call graph...
done
took 163760ms
Call graph stats:
  Nodes: 28972
  Edges: 2181664
  Methods: 13333
  Bytecode Bytes: 841902


===== CALL GRAPH =====

METHOD: com.sirisuk.Main.main()
  --> calls: com.sirisuk.AnalysisClass.run()

METHOD: com.sirisuk.AnalysisClass.run()
  --> calls: com.sirisuk.AnalysisClass.up()
  --> calls: com.sirisuk.AnalysisClass.down()
  --> calls: com.sirisuk.AnalysisClass.up()

METHOD: com.sirisuk.AnalysisClass.<clinit>()

METHOD: com.sirisuk.AnalysisClass.up()

METHOD: com.sirisuk.AnalysisClass.down()
  --> calls: com.sirisuk.AnalysisClass.down()
  --> calls: com.sirisuk.AnalysisClass.down()

===== END =====

```
---

## Reproducibility

I was able to run the driver successfully

---