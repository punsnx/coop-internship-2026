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
   export JAVA_HOME=/Users/prawit/Library/Java/JavaVirtualMachines/ms-11.0.31/Contents/Home
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

   Java 9+ no longer ships `rt.jar` anymore. So WALA requires a JAR of the standard library. Extract it once from `java.base.jmod`
   ```bash
   mkdir -p /tmp/wala-stdlib/x
   unzip -q "$JAVA_HOME/jmods/java.base.jmod" -d /tmp/wala-stdlib/x || true
   "$JAVA_HOME/bin/jar" cf /tmp/wala-stdlib/java.base.jar \
       -C /tmp/wala-stdlib/x/classes .
   rm -rf /tmp/wala-stdlib/x
   ```
   This only needs to run once. Delete `/tmp/wala-stdlib` to force a rebuild.

4. **Set the environment variable for WALA:**

   `src/main/resources/wala.properties` must point to the stdlib cache directory:
   ```properties
   java_runtime_dir=/tmp/wala-stdlib
   ```

5. **Create `scope.txt`** pointing to the compiled target:
   ```
   Primordial,Java,stdlib,none
   Application,Java,binaryDir,/absolute/path/to/targets/classes
   ```
   Scope file format: `Loader,Language,type,path`
   - `Primordial` — Java standard library
   - `Application` — your code
   - `binaryDir` — directory of `.class` files

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

- Result: ❌ FAIL on JDK 11.0.31 (Microsoft, arm64)
- Cause: Gradle 9.x requires JVM 17 or later to run. JDK 11 cannot start the Gradle daemon regardless of the target Java version set in `build.gradle.kts`.
- Fix: Use JDK 17 or higher to run Gradle. The `java.toolchain.languageVersion` in `build.gradle.kts` controls the compilation target, but the JVM running Gradle itself must be 17+.