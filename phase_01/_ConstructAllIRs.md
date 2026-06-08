# ConstructAllIRs

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
 
`ConstructAllIRs` is a WALA driver that constructs all IRs in SSA form for every method in the class hierarchy provided by a scope file. The IR is **intraprocedural only** and will not show IRs of callee methods.
 
> This driver also uses the `ReferenceCleanser` class (managing soft references in cache) to improve runtime and memory usage.
 
---
 
## Input & Output
 
- **Input:**
  - A scope file pointing to bytecode files (`.class` files, **not** `.java` source files)
- **Output:**
  - IRs (stored in cache) and statistics
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
     -PmainClass=com.ibm.wala.examples.drivers.ConstructAllIRs \
     --args="path/to/scope.txt"
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
   > # --- ConstructAllIRs ---
   > "$SCRIPT_DIR/gradlew" -p "$SCRIPT_DIR" run \
   > -PmainClass=com.ibm.wala.examples.drivers.ConstructAllIRs \
   > --args="$SCOPE_FILE"
   >  
   > ```
   ***JAVA_HOME*** uncomment if you have a different JDK version.

   ***Replace*** `BUILD_DIR` for your build_class_path 

---

### Program Code

**`ConstructAllIRs.java`**
```java

/*******************************************************************************
 * Copyright (c) 2008 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.wala.examples.drivers;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.core.util.config.AnalysisScopeReader;
import com.ibm.wala.core.util.ref.ReferenceCleanser;
import com.ibm.wala.ipa.callgraph.AnalysisCacheImpl;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.IAnalysisCacheView;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.util.perf.Stopwatch;
import java.io.IOException;

/**
 * An analysis skeleton that simply constructs IRs for all methods in a class hierarchy. Illustrates
 * the use of {@link ReferenceCleanser} to improve running time / reduce memory usage.
 */
public class ConstructAllIRs{

  /** Should we periodically clear out soft reference caches in an attempt to help the GC? */
  private static final boolean PERIODIC_WIPE_SOFT_CACHES = true;

  /** Interval which defines the period to clear soft reference caches */
  private static final int WIPE_SOFT_CACHE_INTERVAL = 2500;

  /** Counter for wiping soft caches */
  private static int wipeCount = 0;

  /**
   * First command-line argument should be location of scope file for application to analyze
   *
   * @throws IOException
   * @throws ClassHierarchyException
   */
  public static void main(String[] args) throws IOException, ClassHierarchyException {
    String scopeFile = args[0];

    // measure running time
    Stopwatch s = new Stopwatch();
    s.start();
    AnalysisScope scope =
        AnalysisScopeReader.instance.readJavaScope(
            scopeFile, null, ConstructAllIRs.class.getClassLoader());

    // build a type hierarchy
    System.out.print("building class hierarchy...");
    ClassHierarchy cha = ClassHierarchyFactory.make(scope);
    System.out.println("done");

    // register class hierarchy and AnalysisCache with the reference cleanser, so that their soft
    // references are appropriately wiped
    ReferenceCleanser.registerClassHierarchy(cha);
    AnalysisOptions options = new AnalysisOptions();
    IAnalysisCacheView cache = new AnalysisCacheImpl(options.getSSAOptions());
    ReferenceCleanser.registerCache(cache);

    System.out.print("building IRs...");
    for (IClass klass : cha) {
      // filter only for target application
      if (!klass.getClassLoader().getReference().equals(ClassLoaderReference.Application)) {
        continue;
      }

      for (IMethod method : klass.getDeclaredMethods()) {
        wipeSoftCaches();
        // construct an IR; it will be cached
        IR ir = cache.getIR(method, Everywhere.EVERYWHERE);
        // print out IR
        if (ir != null) {
          System.out.println("=== IR for: " + method.getSignature() + " ===");
          System.out.println(ir);
        }
      }
    }
    System.out.println("done");
    s.stop();
    System.out.println("RUNNING TIME: " + s.getElapsedMillis());
  }

  private static void wipeSoftCaches() {
    if (PERIODIC_WIPE_SOFT_CACHES) {
      wipeCount++;
      if (wipeCount >= WIPE_SOFT_CACHE_INTERVAL) {
        wipeCount = 0;
        ReferenceCleanser.clearSoftCaches();
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
**`scope.txt`**
```
Primordial,Java,stdlib,none
Primordial,Java,stdlib,none
Application,Java,binaryDir,/Users/stamp/Uni-Ku-4/fibo/target/classes
```

---

### Expected Output

```text

> Task :run
building class hierarchy...done
building IRs...=== IR for: com.sirisuk.AnalysisClass.<init>()V ===
< Application, Lcom/sirisuk/AnalysisClass, <init>()V >
CFG:
BB0[-1..-2]
    -> BB1
BB1[0..1]
    -> BB2
    -> BB3
BB2[2..2]
    -> BB3
BB3[-1..-2]
Instructions:
BB0
BB1
1   invokespecial < Application, Ljava/lang/Object, <init>()V > v1 @1 exception:v3(line 3) [1=[this]]
BB2
2   return                                   (line 3)
BB3

=== IR for: com.sirisuk.AnalysisClass.up(I)J ===
< Application, Lcom/sirisuk/AnalysisClass, up(I)J >
CFG:
BB0[-1..-2]
    -> BB1
BB1[0..2]
    -> BB3
    -> BB2
BB2[3..5]
    -> BB14
BB3[6..9]
    -> BB4
    -> BB14
BB4[10..14]
    -> BB5
    -> BB14
BB5[15..18]
    -> BB6
    -> BB14
BB6[19..20]
    -> BB7
BB7[21..23]
    -> BB12
    -> BB8
BB8[24..30]
    -> BB9
    -> BB14
BB9[31..35]
    -> BB10
    -> BB14
BB10[36..37]
    -> BB11
    -> BB14
BB11[38..42]
    -> BB7
BB12[43..45]
    -> BB13
    -> BB14
BB13[46..46]
    -> BB14
BB14[-1..-2]
Instructions:
BB0
BB1
2   conditional branch(gt, to iindex=6) v1,v3:#1(line 7) [1=[n]]
BB2
4   v18 = conversion(J) v1                   (line 7) [1=[n]]
5   return v18                               (line 7)
BB3
8   v4 = binaryop(add) v1 , v3:#1            (line 8) [1=[n]]
9   v5 = new <Primordial,[J>@11v4            (line 8)
BB4
14   arraystore v5[v6:#0] = v7:#0            (line 9) [5=[dp]]
BB5
18   arraystore v5[v3:#1] = v8:#1            (line 10) [5=[dp]]
BB6
BB7
           v16 = phi  v15,v9:#2
23   conditional branch(gt, to iindex=43) v16,v1(line 11) [16=[i]1=[n]]
BB8
29   v10 = binaryop(sub) v16 , v3:#1         (line 12) [16=[i]]
30   v11 = arrayload v5[v10]                 (line 12) [5=[dp]]
BB9
34   v12 = binaryop(sub) v16 , v9:#2         (line 12) [16=[i]]
35   v13 = arrayload v5[v12]                 (line 12) [5=[dp]]
BB10
36   v14 = binaryop(add) v11 , v13           (line 12)
37   arraystore v5[v16] = v14                (line 12) [5=[dp]16=[i]]
BB11
40   v15 = binaryop(add) v16 , v3:#1         (line 11) [16=[i]]
42   goto (from iindex= 42 to iindex = 21)   (line 11)
BB12
45   v17 = arrayload v5[v1]                  (line 14) [5=[dp]1=[n]]
BB13
46   return v17                              (line 14)
BB14

=== IR for: com.sirisuk.AnalysisClass.down(I)J ===
< Application, Lcom/sirisuk/AnalysisClass, down(I)J >
CFG:
BB0[-1..-2]
    -> BB1
BB1[0..2]
    -> BB3
    -> BB2
BB2[3..5]
    -> BB12
BB3[6..8]
    -> BB4
    -> BB12
BB4[9..12]
    -> BB7
    -> BB5
BB5[13..15]
    -> BB6
    -> BB12
BB6[16..16]
    -> BB12
BB7[17..22]
    -> BB8
    -> BB12
BB8[23..26]
    -> BB9
    -> BB12
BB9[27..28]
    -> BB10
    -> BB12
BB10[29..31]
    -> BB11
    -> BB12
BB11[32..32]
    -> BB12
BB12[-1..-2]
Instructions:
BB0
BB1
2   conditional branch(gt, to iindex=6) v1,v3:#1(line 21) [1=[n]]
BB2
4   v22 = conversion(J) v1                   (line 21) [1=[n]]
5   return v22                               (line 21)
BB3
6   v4 = getstatic < Application, Lcom/sirisuk/AnalysisClass, memo, <Primordial,[J> >(line 22)
8   v5 = arrayload v4[v1]                    (line 22) [1=[n]]
BB4
10   v7 = compare v5,v6:#0 opcode=cmp        (line 22)
12   conditional branch(eq, to iindex=17) v7,v8:#0(line 22)
BB5
13   v20 = getstatic < Application, Lcom/sirisuk/AnalysisClass, memo, <Primordial,[J> >(line 22)
15   v21 = arrayload v20[v1]                 (line 22) [1=[n]]
BB6
16   return v21                              (line 22)
BB7
17   v9 = getstatic < Application, Lcom/sirisuk/AnalysisClass, memo, <Primordial,[J> >(line 23)
21   v10 = binaryop(sub) v1 , v3:#1          (line 23) [1=[n]]
22   v12 = invokestatic < Application, Lcom/sirisuk/AnalysisClass, down(I)J > v10 @31 exception:v11(line 23)
BB8
25   v14 = binaryop(sub) v1 , v13:#2         (line 23) [1=[n]]
26   v16 = invokestatic < Application, Lcom/sirisuk/AnalysisClass, down(I)J > v14 @37 exception:v15(line 23)
BB9
27   v17 = binaryop(add) v12 , v16           (line 23)
28   arraystore v9[v1] = v17                 (line 23) [1=[n]]
BB10
29   v18 = getstatic < Application, Lcom/sirisuk/AnalysisClass, memo, <Primordial,[J> >(line 24)
31   v19 = arrayload v18[v1]                 (line 24) [1=[n]]
BB11
32   return v19                              (line 24)
BB12

=== IR for: com.sirisuk.AnalysisClass.run()V ===
< Application, Lcom/sirisuk/AnalysisClass, run()V >
CFG:
BB0[-1..-2]
    -> BB1
BB1[0..4]
    -> BB2
    -> BB21
BB2[5..5]
    -> BB3
    -> BB21
BB3[6..8]
    -> BB4
    -> BB21
BB4[9..9]
    -> BB5
    -> BB21
BB5[10..10]
    -> BB6
    -> BB21
BB6[11..13]
    -> BB7
    -> BB21
BB7[14..14]
    -> BB8
    -> BB21
BB8[15..15]
    -> BB9
    -> BB21
BB9[16..18]
    -> BB10
    -> BB21
BB10[19..19]
    -> BB11
    -> BB21
BB11[20..21]
    -> BB12
BB12[22..24]
    -> BB20
    -> BB13
BB13[25..27]
    -> BB14
    -> BB21
BB14[28..30]
    -> BB16
    -> BB15
BB15[31..32]
    -> BB17
BB16[33..33]
    -> BB17
BB17[34..34]
    -> BB18
    -> BB21
BB18[35..35]
    -> BB19
    -> BB21
BB19[36..40]
    -> BB12
BB20[41..41]
    -> BB21
BB21[-1..-2]
Instructions:
BB0
BB1
2   v3 = getstatic < Application, Ljava/lang/System, out, <Application,Ljava/io/PrintStream> >(line 30)
4   [invokedynamic] v5 = invokestatic < Application, Ljava/lang/invoke/StringConcatFactory, makeConcatWithConstants(I)Ljava/lang/String; > v2:#10 @7 exception:v4(line 30) [2=[n]]
BB2
5   invokevirtual < Application, Ljava/io/PrintStream, println(Ljava/lang/String;)V > v3,v5 @12 exception:v6(line 30)
BB3
6   v7 = getstatic < Application, Ljava/lang/System, out, <Application,Ljava/io/PrintStream> >(line 31)
8   v9 = invokestatic < Application, Lcom/sirisuk/AnalysisClass, up(I)J > v2:#10 @19 exception:v8(line 31) [2=[n]]
BB4
9   [invokedynamic] v11 = invokestatic < Application, Ljava/lang/invoke/StringConcatFactory, makeConcatWithConstants(J)Ljava/lang/String; > v9 @22 exception:v10(line 31)
BB5
10   invokevirtual < Application, Ljava/io/PrintStream, println(Ljava/lang/String;)V > v7,v11 @27 exception:v12(line 31)
BB6
11   v13 = getstatic < Application, Ljava/lang/System, out, <Application,Ljava/io/PrintStream> >(line 32)
13   v15 = invokestatic < Application, Lcom/sirisuk/AnalysisClass, down(I)J > v2:#10 @34 exception:v14(line 32) [2=[n]]
BB7
14   [invokedynamic] v17 = invokestatic < Application, Ljava/lang/invoke/StringConcatFactory, makeConcatWithConstants(J)Ljava/lang/String; > v15 @37 exception:v16(line 32)
BB8
15   invokevirtual < Application, Ljava/io/PrintStream, println(Ljava/lang/String;)V > v13,v17 @42 exception:v18(line 32)
BB9
16   v19 = getstatic < Application, Ljava/lang/System, out, <Application,Ljava/io/PrintStream> >(line 34)
18   [invokedynamic] v21 = invokestatic < Application, Ljava/lang/invoke/StringConcatFactory, makeConcatWithConstants(I)Ljava/lang/String; > v2:#10 @49 exception:v20(line 34) [2=[n]]
BB10
19   invokevirtual < Application, Ljava/io/PrintStream, println(Ljava/lang/String;)V > v19,v21 @54 exception:v22(line 34)
BB11
BB12
           v35 = phi  v34,v23:#0
24   conditional branch(gt, to iindex=41) v35,v2:#10(line 35) [35=[i]2=[n]]
BB13
25   v24 = getstatic < Application, Ljava/lang/System, out, <Application,Ljava/io/PrintStream> >(line 36)
27   v26 = invokestatic < Application, Lcom/sirisuk/AnalysisClass, up(I)J > v35 @68 exception:v25(line 36) [35=[i]]
BB14
30   conditional branch(ge, to iindex=33) v35,v2:#10(line 36) [35=[i]2=[n]]
BB15
32   goto (from iindex= 32 to iindex = 34)   (line 36)
BB16
BB17
           v29 = phi  v27:# ,v28:#

34   [invokedynamic] v31 = invokestatic < Application, Ljava/lang/invoke/StringConcatFactory, makeConcatWithConstants(JLjava/lang/String;)Ljava/lang/String; > v26,v29 @83 exception:v30(line 36)
BB18
35   invokevirtual < Application, Ljava/io/PrintStream, print(Ljava/lang/String;)V > v24,v31 @88 exception:v32(line 36)
BB19
38   v34 = binaryop(add) v35 , v33:#1        (line 35) [35=[i]]
40   goto (from iindex= 40 to iindex = 22)   (line 35)
BB20
41   return                                  (line 38)
BB21

=== IR for: com.sirisuk.AnalysisClass.<clinit>()V ===
< Application, Lcom/sirisuk/AnalysisClass, <clinit>()V >
CFG:
BB0[-1..-2]
    -> BB1
BB1[0..1]
    -> BB2
    -> BB3
BB2[2..3]
    -> BB3
BB3[-1..-2]
Instructions:
BB0
BB1
1   v3 = new <Primordial,[J>@2v2:#100        (line 18)
BB2
2   putstatic < Application, Lcom/sirisuk/AnalysisClass, memo, <Primordial,[J> > = v3(line 18)
3   return                                   (line 18)
BB3

=== IR for: com.sirisuk.Main.<init>()V ===
< Application, Lcom/sirisuk/Main, <init>()V >
CFG:
BB0[-1..-2]
    -> BB1
BB1[0..1]
    -> BB2
    -> BB3
BB2[2..2]
    -> BB3
BB3[-1..-2]
Instructions:
BB0
BB1
1   invokespecial < Application, Ljava/lang/Object, <init>()V > v1 @1 exception:v3(line 5) [1=[this]]
BB2
2   return                                   (line 5)
BB3

=== IR for: com.sirisuk.Main.main([Ljava/lang/String;)V ===
< Application, Lcom/sirisuk/Main, main([Ljava/lang/String;)V >
CFG:
BB0[-1..-2]
    -> BB1
BB1[0..0]
    -> BB2
    -> BB3
BB2[1..1]
    -> BB3
BB3[-1..-2]
Instructions:
BB0
BB1
0   invokestatic < Application, Lcom/sirisuk/AnalysisClass, run()V > @0 exception:v3(line 7)
BB2
1   return                                   (line 8)
BB3

done
RUNNING TIME: 308

```
---

## Reproducibility


### Result
⚠️ PARTIAL PASS

### Observations

I followed instructions with one adjustment — `JAVA_HOME` was set to `/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home` as documented, and the build completed successfully.

The core output matched:

```
building class hierarchy...done
building IRs...done
RUNNING TIME: 5171
```

However, the `=== IR for: ===` blocks shown in expected output did not appear in my run. After checking the local source file, the `System.out.println(ir)` line present in his version is not in the cloned repository, it may not have been pushed yet.

Running time also differed (5171ms vs 308ms in the reference), which is expected across different machines.

### Difference from Reference

| Item | Reference | My Run |
|------|------------------|--------|
| Build result | ✅ SUCCESS | ✅ SUCCESS |
| `building class hierarchy...done` | ✅ | ✅ |
| `building IRs...done` | ✅ | ✅ |
| IR blocks printed | ✅ Present | ❌ Not printed |
| Running time | 308ms | 5171ms |

The IR blocks are missing because the version of `ConstructAllIRs.java` in the repository does not include the `System.out.println(ir)` line shown in document. This is likely a code change that has not been pushed to the shared repository yet. Once pushed, the output should match fully.

---