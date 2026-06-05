### PrintTypeHierarchy

---

## Functionality

`PrintTypeHierarchy` is a WALA driver that constructs and prints the type hierarchy of a Java application based on the compiled bytecode

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
     -PmainClass=com.ibm.wala.examples.drivers.PrintTypeHierachy \
     --args="path/to/target"
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
   > # Target bytecode
   >TARGET="$(cd "$SCRIPT_DIR" && realpath "../fibo/out-class/AnalysisClass.class")"
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
   >"$SCRIPT_DIR/gradlew" -p "$SCRIPT_DIR" run \
   >-PmainClass=com.ibm.wala.examples.drivers.PrintTypeHierarchy \
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

**`PrintTypeHierachy.java`**

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
```java
public class AnalysisClass {
    public AnalysisClass() {
    }

    public static void main(String[] var0) {
        byte var1 = 10;
        System.out.println("Printing first " + var1 + " Fibonacci numbers:");
        printFibonacci(var1);
    }

    public static void printFibonacci(int var0) {
        int var1 = 0;
        int var2 = 1;

        for(int var3 = 0; var3 < var0; ++var3) {
            System.out.print(var1 + " ");
            int var4 = var1 + var2;
            var1 = var2;
            var2 = var4;
        }

        System.out.println();
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