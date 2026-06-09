# JDK 11 Testing

---

## Prerequisites

| Dependencies | Version  |
|--------------|----------|
| JDK          | 11.0.31  |
| Gradle       | latest   |
| graphviz     | latest   |

---

## Program Testing

### Setup Steps (MacOS)

0. **Install JDK 11**
   ```bash
   brew install --cask temurin@11
   ```

1. **Clone and build the project:**
   ```bash
   git clone https://github.com/wala/WALA-start.git
   cd WALA-start
   export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-11.jdk/Contents/Home
   sed -i '' 's/JavaLanguageVersion.of([0-9]*)/JavaLanguageVersion.of(11)/' build.gradle.kts
   ./gradlew --stop && ./gradlew build
   ```

2. **Compile the sample target application:**
   ```bash
   mkdir -p targets/classes
   javac -d targets/classes \
     targets/classes/Main.java \
     targets/classes/AnalysisClass.java
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
  >  JAVA_HOME="/Library/Java/JavaVirtualMachines/<your-jdk>/Contents/Home"
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

### Driver `PDFTypeHierarchy`

#### Test Command
```bash
./gradlew run \
  -PmainClass=com.ibm.wala.examples.drivers.PDFTypeHierarchy \
  --args="-classpath targets/classes"
```

#### Expected Output

```
FAILURE: Build failed with an exception.

* What went wrong:
Gradle requires JVM 17 or later to run. Your build is currently configured to use JVM 11.
```