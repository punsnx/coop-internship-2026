# JDK 25 Testing

---

## Prerequisites

| Dependencies | Version |
|--------------|---------|
| JDK          | 25.0.3  |
| Gradle       | latest  |
| graphviz     | latest  |

---

## Program Testing 

### Setup Steps (MacOS)

0. **Install JDK 25**
   ```bash
   brew install oracle-jdk@25
   ```

1. **Clone and build the project:**
   ```bash
   git clone https://github.com/wala/WALA-start.git
   cd WALA-start
   export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home
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
```bash
echo Hello World
```

### Automating script

```bash
  >  #!/bin/bash
  >  if [ -d /tmp/wala-stdlib/ ]; then rm -rf /tmp/wala-stdlib* ;fi
  > 
  >  SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  > 
  >  JAVA_HOME="/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home"
  >  JAVA_VERSION=$("$JAVA_HOME/bin/java" --version | awk 'NR==1{split($2,a,"."); print a[1]}')
  >  sed -i '' "s/JavaLanguageVersion\.of(\([0-9]*\.*\)*)/JavaLanguageVersion.of($JAVA_VERSION)/" "$SCRIPT_DIR/build.gradle.kts"
  >  echo "=== build.gradle.kts toolchain set to Java $JAVA_VERSION ==="
  > 
  >  "$SCRIPT_DIR/gradlew" -p "$SCRIPT_DIR" --stop
  >  "$SCRIPT_DIR/gradlew" -p "$SCRIPT_DIR" --version | grep JVM
  >  "$SCRIPT_DIR/gradlew" -p "$SCRIPT_DIR" clean compileJava
  > 
  >  BUILD_DIR="$(cd "$SCRIPT_DIR" && realpath "/absolute/path/to/build_dir")"
  >  SOURCE_DIR="$(cd "$SCRIPT_DIR" && realpath "/absolute/path/to/source_dir")"
  > 
  >  # --- WALA properties ---
  >  mkdir -p src/main/resources/
  >  echo "java_runtime_dir=/tmp/wala-stdlib" > src/main/resources/wala.properties
  > 
  >  # --- ScopeFile ---
  >  SCOPE_FILE="$SCRIPT_DIR/scope.txt"
  > 
  >  {
  > echo "Primordial,Java,stdlib,none"
  > echo "Application,Java,binaryDir,$BUILD_DIR"
  > } > "$SCOPE_FILE"
  > 
  > # --- Java Stdlib ---
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
```
### Sample input

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
Application,Java,binaryDir,/absolute/path/to/build_dir
```

---

### Driver `DemandPointsToDriver`

#### Test Command
```bash
 #--- DemandPointsToDriver ---
"$SCRIPT_DIR/gradlew" -p "$SCRIPT_DIR" run \
  -PmainClass=com.ibm.wala.examples.drivers.DemandPointsToDriver \
  --args="$TARGET"
```

#### Expected Output
```bash
BUILD SUCCESSFUL in 1s
```

---

### Driver `ScopeFileCallGraph`

#### Test Command

```bash
    # --- ScopeFileCallGraph ---
    "$SCRIPT_DIR/gradlew" -p "$SCRIPT_DIR" run \
    -PmainClass=com.ibm.wala.examples.drivers.ScopeFileCallGraph \
    --args="-scopeFile $SCOPE_FILE -mainClass Lcom/example/Main"
     
```

#### Expected Output

```text

took 2618ms
Call graph stats:
  Nodes: 8269
  Edges: 41059
  Methods: 5841
  Bytecode Bytes: 411139


=== Application Call Graph ===

[com.sirisuk.Main.main([Ljava/lang/String;)V]
  --> [app] com.sirisuk.AnalysisClass.run()V

[com.sirisuk.AnalysisClass.run()V]
  --> [app] com.sirisuk.AnalysisClass.up(I)J
  --> [app] com.sirisuk.AnalysisClass.down(I)J
  --> [lib] java.io.PrintStream.print(Ljava/lang/String;)V
  --> [lib] java.io.PrintStream.println(Ljava/lang/String;)V

[com.sirisuk.AnalysisClass.<clinit>()V]

[com.sirisuk.AnalysisClass.up(I)J]

[com.sirisuk.AnalysisClass.down(I)J]
  --> [app] com.sirisuk.AnalysisClass.down(I)J

```
---


### Driver `CSReachingDefsDriver`

#### Test Command
```bash
# --- CSReachingDefsDriver ---
    "$SCRIPT_DIR/gradlew" -p "$SCRIPT_DIR" run \
      -PmainClass=com.ibm.wala.examples.drivers.CSReachingDefsDriver \
      --args="-scopeFile $SCOPE_FILE -mainClass LAnalysisClass"  
```

#### Expected Output
```bash
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

### Driver `PrintTypeHierachy`

#### Test Command
```bash
# --- PrintTypeHierarchy ---
   "$SCRIPT_DIR/gradlew" -p "$SCRIPT_DIR" run \
   -PmainClass=com.ibm.wala.examples.drivers.PrintTypeHierarchy \
     --args="$TARGET"
```

#### Expected Output
```bash
<Primordial,Lcom/ibm/wala/model/SyntheticFactory>:
<Primordial,Lcom/ibm/wala/model/java/lang/System>:
<Primordial,Lcom/ibm/wala/model/java/lang/Thread>:
<Primordial,Lcom/ibm/wala/model/java/lang/reflect/Array>:
<Application,LAnalysisClass>:
```

---

### Driver `SourceDirCallGraph`

#### Test Command
```bash
# --- SourceDirCallGraph ---
JDK_HOME="${JAVA_HOME:?JAVA_HOME must be set}"
if [ -d /tmp/wala-stdlib ]; then
  rm -rf /tmp/wala-stdlib
fi

if [ ! -f /tmp/wala-stdlib/java.base.jar ]; then
    echo "Building WALA stdlib cache (one-time)..."
    mkdir -p /tmp/wala-stdlib/x
    unzip -q "$JDK_HOME/jmods/java.base.jmod" -d /tmp/wala-stdlib/x || true
    "$JDK_HOME/bin/jar" cf /tmp/wala-stdlib/java.base.jar \
        -C /tmp/wala-stdlib/x/classes .
    rm -rf /tmp/wala-stdlib/x
fi

if [ ! -f /tmp/wala-stdlib/.libs-ready ]; then
    echo "Copying WALA + ECJ dependency JARs (one-time)..."
    find ~/.gradle/caches/modules-2/files-2.1/com.ibm.wala \
        -name "*.jar" ! -name "*-sources.jar" ! -name "*-javadoc.jar" \
        -exec cp {} /tmp/wala-stdlib/  \;
    find ~/.gradle/caches/modules-2/files-2.1/org.eclipse.jdt \
        -name "*.jar" ! -name "*-sources.jar" ! -name "*-javadoc.jar" \
        -exec cp {} /tmp/wala-stdlib/ \;
    touch /tmp/wala-stdlib/.libs-ready
fi

"$SCRIPT_DIR/gradlew" -p "$SCRIPT_DIR" run \
  -PmainClass=com.ibm.wala.examples.drivers.SourceDirCallGraph \
  --args="-sourceDir ../fibo/out-class -mainClass LAnalysisClass"
```

#### Expected Output
```bash
building call graph...
done
took 141480ms
Call graph stats:
  Nodes: 34173
  Edges: 2509500
  Methods: 13655
  Bytecode Bytes: 810349
```

---

### Driver `PDFTypeHierachy`

#### Test Command
```bash
# --- PDFTypeHierarchy ---
"$SCRIPT_DIR/gradlew" -p "$SCRIPT_DIR" run \
  -PmainClass=com.ibm.wala.examples.drivers.PDFTypeHierarchy \
  --args="-classpath $TARGET"
```

#### Expected Output

A PDF file opens automatically showing the type hierarchy of the application classes as a directed graph:

```
<Primordial,Ljava/lang/Object>
        |                    |
        ▼                    ▼
<Application,             <Application,
 Lcom/sirisuk/             Lcom/sirisuk/
 AnalysisClass>            Main>
```

---

### Driver `ConstructAllIRs`

#### Test Command
```bash
# --- ConstructAllIRs ---
"$SCRIPT_DIR/gradlew" -p "$SCRIPT_DIR" run \
  -PmainClass=com.ibm.wala.examples.drivers.ConstructAllIRs \
  --args="$SCOPE_FILE"
```

#### Expected Output
```bash
building class hierarchy...done
building IRs...done
RUNNING TIME: 7211
```