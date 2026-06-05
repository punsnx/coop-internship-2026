# JDK 25 Testing

---

## Prerequisites

| Dependencies | Version   |
|--------------|-----------|
| JDK          | 25        |
| Gradle       | latest    |
| ...          | {version} |

---

## Program Testing 

### Setup Steps
1. **Instruction1**
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

### Driver `namme`

- **Test command**

- **Expected output**

---

### Driver `namme`

- **Test command**

- **Expected output**

---
