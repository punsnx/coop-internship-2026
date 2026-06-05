# CSReachingDefsDriver

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

`CSReachingDefsDriver` reads the bytecode and perform an **interprocedural, context-sensitive dataflow analysis**. It computes accross the entire application call graph using a tabulation solver.

---

## Input & Output

* **Input:** The file path to a compiled Java bytecode file (`.class` or `.jar`).
* **Output:** A Call Graph representation of the environment.

---

## Run Instructions

### Prerequisites

1. **Java version 21** — verify with `java -version`
2. **Python 3** — used by the project's `run.py` runner script.

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
   ./gradlew build
   ```

4. Prepare a sample input, compile a Java file:
   ```
   javac -d path/to/java
   ```

5. Run `CSReachingDefsDriver`:
   ```
   python3 run.py com.ibm.wala.examples.drivers.CSReachingDefsDriver -scopeFile path/to/scope -mainClass LAnalysisClass
   ```
   
### Sample Input
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

### Expected Output

```text
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

## Reproducibility

---