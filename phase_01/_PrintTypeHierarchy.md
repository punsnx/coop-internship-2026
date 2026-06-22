# PrintTypeHierarchy

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

`PrintTypeHierarchy` is a WALA driver designed to analyze and display (or print) the type hierarchy of a Java application based on its compiled bytecode. This involves generating data about class relationships and inheritance from parsed .class files and presenting this information in a structured format that helps users understand the architecture and structure of the software.

---

## Input & Output

* **Input:**
    - The file path to a compiled Java bytecode file (`.class` or `.jar`).
* **Output:**
    - Prints the type hierarchy to standard output, explicitly showing direct inheritance relationships between classes.

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
| `com.ibm.wala:com.ibm.wala.ipa`      | 1.7.2 |
| Java Version (tested)                | 21 |

### Steps to Run

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
     -PmainClass=com.ibm.wala.examples.drivers.PrintTypeHierarchy \
     --args="path/to/target"
   ```

   **NOTE:** For full automated steps 3–6 script: (run it at WALA-start project root directory)

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
   > # Target bytecode
   > TARGET="$(cd "$SCRIPT_DIR" && realpath "../fibo/out-class/AnalysisClass.class")"
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
   > # --- PrintTypeHierarchy ---
   > "$SCRIPT_DIR/gradlew" -p "$SCRIPT_DIR" run \
   > -PmainClass=com.ibm.wala.examples.drivers.PrintTypeHierarchy \
   >  --args="$TARGET"
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

**`PrintTypeHierarchy.java`**

```java
public class PrintTypeHierarchy {

    public static void main(String[] args) throws IOException, ClassHierarchyException {
        String classpath = args[0];
        AnalysisScope scope = AnalysisScopeReader.instance.makeJavaBinaryAnalysisScope(classpath, null);
        ClassHierarchy cha = ClassHierarchyFactory.make(scope);
        System.out.println(cha);
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

---

### Example Output

```text
<Primordial,Lcom/ibm/wala/model/SyntheticFactory>:
<Primordial,Lcom/ibm/wala/model/java/lang/System>:
<Primordial,Lcom/ibm/wala/model/java/lang/Thread>:
<Primordial,Lcom/ibm/wala/model/java/lang/reflect/Array>:
<Application,LAnalysisClass>:
```

---

## Reproducibility
I was able to reproduce by using the script, but I needed to change path at this line
```
   TARGET="$(cd "$SCRIPT_DIR" && realpath "../fibo/out-class/AnalysisClass.class")"
   to
   TARGET="$(cd "$SCRIPT_DIR" && realpath "../fibo/target/classes/com/sirisuk/AnalysisClass.class")"

```
to be able to do it.

I also change arg to ../fibo/target/classes/com/sirisuk/, and able to run
which confirm that it can point to directory as well, not only for just one class.
---