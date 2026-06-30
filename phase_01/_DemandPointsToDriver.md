# DemandPointsToDriver

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
 
`DemandPointsToDriver` is a WALA driver that implements a **demand-driven points-to analysis** technique, meaning the driver can be modified to query specific variables the user is interested in. 
>Note that this driver requires a baseline call graph first, so it builds a simple call graph such as **CHA** (Class Hierarchy Analysis), and then performs the analysis.
 
---
 
## Input & Output
 
- **Input:**
  - A directory of classes containing the variable(s) of interest (scoped via class path)
- **Output:**
  - Points-to sets — shows what objects each pointer may refer to
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
5. **Run the driver:**
   ```bash
   ./gradlew run \
     -PmainClass=com.ibm.wala.examples.drivers.DemandPointsToDriver \
     --args="target/classes"
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
   > SCOPE_FILE="$SCRIPT_DIR/scope.txt"
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
   > # --- DemandPointsToDriver ---
   > "$SCRIPT_DIR/gradlew" -p "$SCRIPT_DIR" run \
   > -PmainClass=com.ibm.wala.examples.drivers.DemandPointsToDriver \
   > --args="$BUILD_DIR"
   >  
   > ```
   ***JAVA_HOME*** uncomment if you have a different JDK version.

   ***Replace*** `BUILD_DIR` for your build_class_path.

---

### Program Code

**`DemandPointsToDriver.java`**
```java

package com.ibm.wala.examples.drivers;

import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.Language;
import com.ibm.wala.core.util.config.AnalysisScopeReader;
import com.ibm.wala.demandpa.alg.DemandRefinementPointsTo;
import com.ibm.wala.demandpa.alg.refinepolicy.NeverRefineCGPolicy;
import com.ibm.wala.demandpa.alg.refinepolicy.NeverRefineFieldsPolicy;
import com.ibm.wala.demandpa.alg.refinepolicy.RefinementPolicyFactory;
import com.ibm.wala.demandpa.alg.refinepolicy.SinglePassRefinementPolicy;
import com.ibm.wala.demandpa.alg.statemachine.DummyStateMachine;
import com.ibm.wala.demandpa.alg.statemachine.StateMachineFactory;
import com.ibm.wala.demandpa.flowgraph.IFlowLabel;
import com.ibm.wala.demandpa.util.MemoryAccessMap;
import com.ibm.wala.demandpa.util.SimpleMemoryAccessMap;
import com.ibm.wala.ipa.callgraph.AnalysisCacheImpl;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.IAnalysisCacheView;
import com.ibm.wala.ipa.callgraph.cha.CHACallGraph;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.HeapModel;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerKey;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.collections.Pair;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;

/**
 * Example driver for using the demand-driven points-to analysis, {@link DemandRefinementPointsTo}
 */
public class DemandPointsToDriver {

  /**
   * Shows how to run the demand-driven points-to analysis. First and only command-line argument is
   * the classpath
   */
  public static void main(String[] args)
      throws IOException, ClassHierarchyException, CancelException {
    // Construct the AnalysisScope from the class path.
    String classpath = args[0];
    AnalysisScope scope = AnalysisScopeReader.instance.makeJavaBinaryAnalysisScope(classpath, null);
    // We need a baseline call graph.  Here we use a CHACallGraph based on a ClassHierarchy.
    ClassHierarchy cha = ClassHierarchyFactory.make(scope);
    CHACallGraph chaCG = new CHACallGraph(cha);
    chaCG.init(Util.makeMainEntrypoints(cha));
    AnalysisOptions options = new AnalysisOptions();
    IAnalysisCacheView cache = new AnalysisCacheImpl();
    // We also need a heap model to create InstanceKeys for allocation sites, etc.
    // Here we use a 0-1 CFA builder, which will give a heap abstraction similar to
    // context-insensitive Andersen's analysis
    HeapModel heapModel = Util.makeZeroOneCFABuilder(Language.JAVA, options, cache, cha);
    // The MemoryAccessMap helps the demand analysis find matching field reads and writes
    MemoryAccessMap mam = new SimpleMemoryAccessMap(chaCG, heapModel, false);
    // The StateMachineFactory helps in tracking additional states like calling contexts.
    // For context-insensitive analysis we use a DummyStateMachine.Factory
    StateMachineFactory<IFlowLabel> stateMachineFactory = new DummyStateMachine.Factory<>();
    DemandRefinementPointsTo drpt =
        DemandRefinementPointsTo.makeWithDefaultFlowGraph(
            chaCG, heapModel, mam, cha, options, stateMachineFactory);
    // The RefinementPolicyFactory determines how the analysis refines match edges (see PLDI'06
    // paper).  Here we use a policy that does not perform refinement and just uses a fixed budget
    // for a single pass
    RefinementPolicyFactory refinementPolicyFactory =
        new SinglePassRefinementPolicy.Factory(
            new NeverRefineFieldsPolicy(), new NeverRefineCGPolicy(), 1000);
    drpt.setRefinementPolicyFactory(refinementPolicyFactory);
    // We need some variables to query.  Here, we find calls to a method named "elementAt" inside
    // application code, and query the receiver at such calls.  Customize for your own needs.
    for (CGNode node : chaCG) {
      if (!node.getMethod()
          .getDeclaringClass()
          .getClassLoader()
          .getReference()
          .equals(ClassLoaderReference.Application)) {
        continue;
      }
      IR ir = node.getIR();
      if (ir == null) continue;
      Iterator<CallSiteReference> callSites = ir.iterateCallSites();
      while (callSites.hasNext()) {
        CallSiteReference site = callSites.next();
        //Change method from elementAt to down
        if (site.getDeclaredTarget().getName().toString().equals("down")) {
          System.out.println(site + " in " + node);
          SSAAbstractInvokeInstruction[] calls = ir.getCalls(site);
          PointerKey pk = heapModel.getPointerKeyForLocal(node, calls[0].getUse(0));
          Pair<DemandRefinementPointsTo.PointsToResult, Collection<InstanceKey>> pointsTo =
              drpt.getPointsTo(pk, k -> true);
          System.out.println("POINTS TO RESULT: " + pointsTo);
        }
      }
    }
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

invokestatic < Application, Lcom/sirisuk/AnalysisClass, down(I)J >@34 in Node: < Application, Lcom/sirisuk/AnalysisClass, run()V > Context: Everywhere
POINTS TO RESULT: [SUCCESS,[]]
invokestatic < Application, Lcom/sirisuk/AnalysisClass, down(I)J >@31 in Node: < Application, Lcom/sirisuk/AnalysisClass, down(I)J > Context: Everywhere
POINTS TO RESULT: [SUCCESS,[]]
invokestatic < Application, Lcom/sirisuk/AnalysisClass, down(I)J >@37 in Node: < Application, Lcom/sirisuk/AnalysisClass, down(I)J > Context: Everywhere
POINTS TO RESULT: [SUCCESS,[]]

```
---


## Reproducibility

I was able to run the driver successfully

---