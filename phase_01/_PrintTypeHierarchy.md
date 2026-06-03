# PrintTypeHierarchy

---

## Functionality

`PrintTypeHierarchy` analyzes the execution environment to build a comprehensive class hierarchy. It explicitly denotes direct inheritance relationships using the format:
> `ParentClass : ChildClass`

---

## Input & Output

* **Input:** The file path to a compiled Java bytecode file (`.class` or `.jar`).
* **Output:** An `IClassHierarchy` representation of the environment.

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

5. Run `PrintTypeHierachy`:
   ```
   python3 run.py com.ibm.wala.examples.drivers.PrintTypeHierachy path/to/class
   ```

### **Example Input Code:**
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

### **Example Output:**

```text
<Primordial,Lcom/ibm/wala/model/SyntheticFactory>:
<Primordial,Lcom/ibm/wala/model/java/lang/System>:
<Primordial,Lcom/ibm/wala/model/java/lang/Thread>:
<Primordial,Lcom/ibm/wala/model/java/lang/reflect/Array>:
<Application,LAnalysisClass>:
```