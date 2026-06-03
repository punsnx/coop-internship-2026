# PDFTypeHierarchy

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

3. **Python 3** — used by the project's `run.py` runner script.

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

4. Prepare a sample input, compile a Java file into `targets/classes/`:
   ```
   javac -d targets/classes targets/src/Bank.java
   ```

5. Run `PDFTypeHierarchy`:
   ```
   python3 run.py com.ibm.wala.examples.drivers.PDFTypeHierarchy -classpath targets/classes
   ```

6. Finally, a PDF file will open automatically in your system's default PDF viewer.

---

### Sample Input

**Source file:** `targets/src/Bank.java`

```java
public class Bank {
    private double balance;

    public Bank(double initialBalance) {
        this.balance = initialBalance;
    }

    public void deposit(double amount) {
        balance += amount;
    }

    public void withdraw(double amount) {
        if (amount <= balance) balance -= amount;
    }

    public double getBalance() {
        return balance;
    }

    public static void main(String[] args) {
        Bank b = new Bank(1000);
        b.deposit(500);
        b.withdraw(200);
        System.out.println("Balance: " + b.getBalance());
    }
}
```

### Expected Output

A PDF diagram opens showing the inheritance chain for application classes only:

```
<Primordial,Ljava/lang/Object>
        |
        ▼
<Application,LBank>
```

`Bank` has no explicit superclass, so it inherits directly from `java.lang.Object` which is
the root of all Java classes. The diagram has exactly two nodes and one edge.

> **Note:** This PDF output is written to a temporary file (e.g. `/tmp/out1228629807.pdf`)
> and opened automatically. The exact filename changes on every run.
