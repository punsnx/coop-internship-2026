# PDFTypeHierarchy

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

`PDFTypeHierarchy` builds a class hierarchy from compiled Java bytecode and renders it as a
visual diagram in a form of PDF file. It uses Graphviz (`dot`) to convert the hierarchy into a directed graph,
where edges point from parent classes/interfaces to their subtypes, then opens the
result in the system's default PDF viewer. Only classes loaded by the Application class loader
are shown (JDK internals are pruned out.)

---

## Input & Output

- **Input:** A classpath pointing to a directory of compiled `.class` file or `.jar` file, via the `-classpath` flag.
- **Output:** A PDF file (written to a system temporarily directory) that opens automatically,
  showing each application class as a node with directed edges from parent to its subtype.

---

## Run Instructions

### Prerequisites

1. **Java version 21** — verify with `java -version`
2. **Graphviz** — required to render the graph to PDF. Install on macOS with:
   ```
   brew install graphviz
   ```
   Verify with:
   ```
   which dot
   ```
   or install a package on Windows directly via https://graphviz.org/download/

### Steps

1. Clone and enter the repository:
   ```
   git clone https://github.com/wala/WALA-start.git
   cd WALA-start
   ```

2. Set Java version to 21 in `build.gradle.kts`:
   ```kotlin
   java.toolchain.languageVersion = JavaLanguageVersion.of(21)
   ```

3. Fix code formatting and build:
   ```
   ./gradlew spotlessApply
   ./gradlew build
   ```

4. Compile the sample input into `targets/classes/`:
   ```
   mkdir -p targets/classes
   javac -d targets/classes targets/classes/Main.java targets/classes/AnalysisClass.java
   ```

5. Run `PDFTypeHierarchy`:
   ```
   ./gradlew run \
     -PmainClass=com.ibm.wala.examples.drivers.PDFTypeHierarchy \
     --args="-classpath targets/classes"
   ```

6. A PDF file will open automatically in your system's default PDF viewer.

---

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

### Expected Output

A PDF diagram opens showing the inheritance chain for application classes only:

```
        <Primordial,Ljava/lang/Object>
                 |              |
                 ▼              ▼
<Application,                <Application,
 Lcom/sirisuk/                Lcom/sirisuk/
 AnalysisClass>               Main>
```

Both `Main` and `AnalysisClass` have no explicit superclass, so they both inherit directly
from `java.lang.Object` — the root of all Java classes. The diagram has exactly 3 nodes and
2 edges.

> **Note:** The PDF is written to a temporary file (e.g. `/tmp/out1228629807.pdf`)
> and opened automatically. The exact filename changes on every run.
 
---

## Reproducibility


✅ PASS

### Difference from Reference
✅ Matches reference behavior.
[View logs](https://github.com/punsnx/coop-internship-2026/blob/56f5ed0f417941e58eb9a887bab6ca6332421f3f/phase_01/other/assets/sirisuk/test-logs/PDFTypeHierarchy.log)

---