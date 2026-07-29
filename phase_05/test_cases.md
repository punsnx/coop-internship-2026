# Complex Dead Store Analysis — Test Cases & Results

Four scenarios exercising the `ComplexDeadStoreDriver`, which extends dead store
detection to nested/inner classes, static/instance fields, `<clinit>`/`<init>`,
multiple methods, method parameters, and control flow (branching, switch, loops)
within a single class hierarchy.

## 01 — Nested Classes, Fields, and Parameters

Basic case: a private inner class, a static class field, unused method
parameters, and an unused local — spread across three methods and two classes.

```java
public class Main {
    private class MyNestedClass {
        public int myNestedField = 12;
        private void myNestedMethod(int myPar1) {
            System.out.println("Method in the nested class.");
        }
    }
    private static int myField = 13;

    public static void main(String[] args) {
        int myVar = 14;
        int myVar2 = 23;
        myMethod(myVar);
    }

    private static void myMethod(int myPar2) {
        System.out.println("Method in the class.");
    }
}
```

```text
Dead Store Analysis Report
Input: scopeFile.txt
Exclude Parameters: false
-------------------------------------------------------------------------
Class: Main
  Method: <clinit>
    Line 8  | 'myField      ' | Unused field (never read)
  Found 1 dead store(s) in <clinit>
  Method: main
    Line 12 | 'myVar2       ' | Unused computation: myVar2 = 23
    Line 11 | 'args         ' | Unused parameter
  Found 2 dead store(s) in main
  Method: myMethod
    Line 17 | 'myPar2       ' | Unused parameter
  Found 1 dead store(s) in myMethod
-------------------------------------------------------------------------
Class: Main$MyNestedClass
  Method: <init>
    Line 3  | 'myNestedField' | Unused field (never read)
  Found 1 dead store(s) in <init>
  Method: myNestedMethod
    Line 5  | 'myPar1       ' | Unused parameter
  Found 1 dead store(s) in myNestedMethod
=========================================================================
Total: 6 dead store(s) found across 5 method(s) in 2 class(es)
=========================================================================
```

## 02 — Instance Field and Unused Computation

Minimal two-method class: an unused instance field initialized in `<init>`,
and a computed local (`deadCalculation`) whose result is never read.

```java
public class MathUtils {
    private double pi = 3.14159; // Dead store (never read)

    public int calculateArea(int width, int height, int unusedDepth) {
        int area = width * height;
        int deadCalculation = area * 2; // Dead store
        return area;
    }

    public void logResults(String result) {
        String prefix = "Result: ";
        System.out.println(prefix + result);
    }
}
```

```text
Dead Store Analysis Report
Input: scopeFile.txt
Exclude Parameters: false
-------------------------------------------------------------------------
Class: MathUtils
  Method: <init>
    Line 2  | 'pi           ' | Unused field (never read)
  Found 1 dead store(s) in <init>
  Method: calculateArea
    Line 6  | 'deadCalculation' | Unused computation: 8 = binaryop(mul) 6 , 7
    Line 5  | 'unusedDepth  ' | Unused parameter
  Found 2 dead store(s) in calculateArea
=========================================================================
Total: 3 dead store(s) found across 2 method(s) in 1 class(es)
=========================================================================
```

## 03 — Constructors, Switch-Free Branching, and Multi-Level Nesting

Larger case combining: static and instance fields, an overloaded-style
constructor with local overwrites, a business method with branch-local dead
stores, a control-flow helper method , a static nested class, and an inner member class.

```java
public class OrderProcessor {

    // --- Class Fields ---
    private static String DEFAULT_CURRENCY = "USD";
    private static int UNUSED_MAX_RETRIES = 5;       // DEAD STORE: Unused static field

    private String processorId;
    private double taxRate = 0.05;                  // DEAD STORE: Unused instance field
    private boolean isDebugMode;

    // --- Constructor ---
    public OrderProcessor(String processorId, boolean isDebugMode, int unusedTimeout) { // DEAD STORE: unusedTimeout parameter
        this.processorId = processorId;
        this.isDebugMode = isDebugMode;

        int initCheck = 1;                          // DEAD STORE: Local variable assigned but never read
        int status = 0;                             // DEAD STORE: Overwritten before any read
        status = 200;
        if (this.isDebugMode) {
            System.out.println("Initialized " + DEFAULT_CURRENCY + " processor: " + this.processorId + " | Status: " + status);
        }
    }

    // --- Main Business Method ---
    public double processOrder(double baseAmount, int itemCount, String promoCode, boolean isVIP) { // DEAD STORE: promoCode parameter
        double subtotal = baseAmount * itemCount;

        int tempDiscountCode = 999;                 // DEAD STORE: Overwritten without being read
        tempDiscountCode = 100;

        double discount = 0.0; // DEAD STORE
        if (isVIP) {
            discount = subtotal * (tempDiscountCode / 1000.0);
        } else {
            discount = subtotal * 0.05;
        }

        double total = subtotal - discount;

        int unusedChecksum = (int) total ^ 42;      // DEAD STORE: Local variable assigned, never read

        return total;
    }

    // --- Helper Method with Control Flow ---
    public void validateInventory(String[] itemIds, int maxLimit) {
        int validCount = 0;
        int shadowCounter = 0;

        for (String id : itemIds) {
            if (id != null) {
                validCount++;
            }
            shadowCounter++;
        }

        System.out.println("Valid items count: " + validCount);
    }

    // --- Static Nested Class ---
    public static class AuditLogger {
        private String logFile = "audit.log";       // DEAD STORE: Unused instance field
        private static int LOG_LEVEL = 1;

        public void writeLog(String message, String unusedCategory) { // DEAD STORE: unusedCategory parameter
            int logCode = 101;
            System.out.println("[" + LOG_LEVEL + "] Code " + logCode + ": " + message);
        }
    }

    // --- Inner Member Class ---
    public class DiscountCalculator {
        private double customMultiplier;
        private String unusedRuleName = "DEFAULT";  // DEAD STORE: Unused instance field

        public DiscountCalculator(double customMultiplier) {
            this.customMultiplier = customMultiplier;
        }

        public double calculate(double price) {
            int unusedStep = 10;                    // DEAD STORE: Local variable assigned, never read
            double result = price * customMultiplier;
            return result;
        }
    }
}
```

```text
Dead Store Analysis Report
Input: scopeFile.txt
Exclude Parameters: false
-------------------------------------------------------------------------
Class: OrderProcessor
  Method: <clinit>
    Line 5  | 'UNUSED_MAX_RETRIES' | Unused field (never read)
  Found 1 dead store(s) in <clinit>
  Method: <init>
    Line 8  | 'taxRate      ' | Unused field (never read)
    Line 16 | 'initCheck    ' | Unused computation: initCheck = 1
    Line 17 | 'status       ' | Unused computation: status = 0
    Line 12 | 'unusedTimeout' | Unused parameter
  Found 4 dead store(s) in <init>
  Method: processOrder
    Line 40 | 'unusedChecksum' | Unused computation: 23 = binaryop(xor) 21 , 22
    Line 28 | 'tempDiscountCode' | Unused computation: tempDiscountCode = 999
    Line 31 | 'discount     ' | Unused computation: discount = 0.0
    Line 26 | 'promoCode    ' | Unused parameter
  Found 4 dead store(s) in processOrder
  Method: validateInventory
    Line 47 | 'maxLimit     ' | Unused parameter
  Found 1 dead store(s) in validateInventory
-------------------------------------------------------------------------
Class: OrderProcessor$AuditLogger
  Method: <init>
    Line 62 | 'logFile      ' | Unused field (never read)
  Found 1 dead store(s) in <init>
  Method: writeLog
    Line 66 | 'unusedCategory' | Unused parameter
  Found 1 dead store(s) in writeLog
-------------------------------------------------------------------------
Class: OrderProcessor$DiscountCalculator
  Method: <init>
    Line 74 | 'unusedRuleName' | Unused field (never read)
  Found 1 dead store(s) in <init>
  Method: calculate
    Line 81 | 'unusedStep   ' | Unused computation: unusedStep = 10
  Found 1 dead store(s) in calculate
=========================================================================
Total: 14 dead store(s) found across 8 method(s) in 3 class(es)
=========================================================================
```


## 04 — Static Initializer Chains, Switch Statement, and Deep Nesting

The most complex scenario: a class field re-assigned inside a static
initializer block, an overloaded-constructor call chain, a `switch` with a
`default` early return, a static nested class, an inner class, and a
doubly-nested inner class (inner-inside-inner).

```java
public class DataPipeline {

    // --- Static Initializers & Class Fields ---
    private static final String DEFAULT_NAME = "MainPipeline";
    private static int GLOBAL_FLAGS = 0;                       // DEAD STORE: Field initialized in <clinit> but never read
    private static double UNUSED_THRESHOLD = 0.95;             // DEAD STORE: Unused static field (never read)

    static {
        GLOBAL_FLAGS = 1;                                      // DEAD STORE: Re-assigned in static block, still never read
    }

    // --- Instance Fields ---
    private String pipelineName;
    private int bufferSize = 1024;                             // DEAD STORE: Unused instance field initialized in <init>

    // --- Overloaded Constructors ---
    public DataPipeline() {
        this(DEFAULT_NAME, 500);
    }

    public DataPipeline(String pipelineName, int unusedCapacity) { // DEAD STORE: Unused constructor parameter
        this.pipelineName = pipelineName;
        int setupCode = 404;                                      // DEAD STORE: Overwritten on next line before read
        setupCode = 200;
        System.out.println("Pipeline created: " + this.pipelineName + " with status " + setupCode);
    }

    // --- Execution Method with Control Flow ---
    public boolean execute(String[] inputBatch, int executionMode) {
        String stageStatus = "PENDING";                           // DEAD STORE: Overwritten in all switch paths before read
        int processedItems = 0;                                   // DEAD STORE: Overwritten in cases 1 & 2; bypassed on default exit
        int skippedItems = 0;                                     // DEAD STORE: Unused local variable

        switch (executionMode) {
            case 1:
                processedItems = inputBatch != null ? inputBatch.length : 0;
                stageStatus = "MODE_1_SUCCESS";
                break;
            case 2:
                processedItems = inputBatch != null ? inputBatch.length / 2 : 0;
                stageStatus = "MODE_2_SUCCESS";
                break;
            default:
                stageStatus = "MODE_UNKNOWN";                     // DEAD STORE: Assigned immediately before return false; never read
                return false;
        }

        System.out.println("Pipeline " + pipelineName + " finished: " + stageStatus + ", processed: " + processedItems);
        return true;
    }

    // --- Static Nested Class ---
    public static class FilterStage {
        private boolean enableFiltering = true;
        private int filterVersion = 1;                           // DEAD STORE: Unused instance field initialized in <init>

        public boolean applyFilter(String data, String unusedFilterKey) { // DEAD STORE: Unused method parameter
            if (!enableFiltering || data == null) {
                return false;
            }
            int dummyHeader = 0xFF;                              // DEAD STORE: Unused local variable
            return data.trim().length() > 0;
        }
    }

    // --- Multi-Level Inner Classes ---
    public class TransformationStage {
        private String transformType = "UPPERCASE";

        public String transform(String input, int unusedLevel) { // DEAD STORE: Unused method parameter
            String result = "";                                  // DEAD STORE: Overwritten in both 'if' and 'else' branches before read
            int stepCounter = 0;                                 // DEAD STORE: Overwritten on next line before read
            stepCounter = 1;

            if ("UPPERCASE".equalsIgnoreCase(transformType)) {
                result = input.toUpperCase() + "_" + stepCounter;
            } else {
                result = input.toLowerCase() + "_" + stepCounter;
            }

            return result;
        }

        // Deeply Nested Inner Class (Inner inside Inner)
        public class NestedStep {
            private int stepId = 42;                             // DEAD STORE: Unused instance field initialized in <init>

            public void executeStep(String stepName) {
                String localTag = "STEP_EXEC";                   // DEAD STORE: Unused local variable
                System.out.println("Executing nested step for: " + stepName);
            }
        }
    }
}
```

```text
Dead Store Analysis Report
Input: scopeFile.txt
Exclude Parameters: false
-------------------------------------------------------------------------
Class: DataPipeline
  Method: <clinit>
    Line 5  | 'GLOBAL_FLAGS ' | Unused field (never read)
    Line 6  | 'UNUSED_THRESHOLD' | Unused field (never read)
    Line 9  | 'GLOBAL_FLAGS ' | Unused field (never read)
  Found 3 dead store(s) in <clinit>
  Method: <init>
    Line 14 | 'bufferSize   ' | Unused field (never read)
    Line 23 | 'setupCode    ' | Unused computation: setupCode = 404
    Line 21 | 'unusedCapacity' | Unused parameter
  Found 3 dead store(s) in <init>
  Method: execute
    Line 30 | 'stageStatus  ' | Unused computation: stageStatus = PENDING
    Line 31 | 'processedItems' | Unused computation: processedItems = 0
    Line 32 | 'skippedItems ' | Unused computation: skippedItems = 0
    Line 44 | 'stageStatus  ' | Unused computation: stageStatus = MODE_UNKNOWN
  Found 4 dead store(s) in execute
-------------------------------------------------------------------------
Class: DataPipeline$FilterStage
  Method: <init>
    Line 55 | 'filterVersion' | Unused field (never read)
  Found 1 dead store(s) in <init>
  Method: applyFilter
    Line 61 | 'dummyHeader  ' | Unused computation: dummyHeader = 255
    Line 58 | 'unusedFilterKey' | Unused parameter
  Found 2 dead store(s) in applyFilter
-------------------------------------------------------------------------
Class: DataPipeline$TransformationStage
  Method: transform
    Line 71 | 'result       ' | Unused computation: result = 
    Line 72 | 'stepCounter  ' | Unused computation: stepCounter = 0
    Line 71 | 'unusedLevel  ' | Unused parameter
  Found 3 dead store(s) in transform
-------------------------------------------------------------------------
Class: DataPipeline$TransformationStage$NestedStep
  Method: <init>
    Line 86 | 'stepId       ' | Unused field (never read)
  Found 1 dead store(s) in <init>
  Method: executeStep
    Line 89 | 'localTag     ' | Unused computation: localTag = STEP_EXEC
  Found 1 dead store(s) in executeStep
=========================================================================
Total: 18 dead store(s) found across 8 method(s) in 4 class(es)
=========================================================================
```

**Notable finding:** `GLOBAL_FLAGS` is flagged twice — once for its inline
initializer (`= 0`) and once for the re-assignment inside the `static {}`
block (`= 1`). Both writes are dead independently since the field is never
read anywhere in the class; the analyzer does not collapse them into a single
finding.

## Summary

| # | Scenario | Class file | Findings | What it exercises |
|---|---|---|---|---|
| 01 | Nested classes, fields, params | `Main.java` | 6 across 5 methods / 2 classes | Private inner class, static field in `<clinit>`, unused param + local |
| 02 | Instance field, computation | `MathUtils.java` | 3 across 2 methods / 1 class | Unused `<init>`-set field, unused computed local, unused param |
| 03 | Constructors, branching, multi-nesting | `OrderProcessor.java` | 14 across 8 methods / 3 classes | Overloaded ctor, branch-local dead stores, static nested + inner class, loop-carried var correctly unflagged |
| 04 | Static init chains, switch, deep nesting | `DataPipeline.java` | 18 across 8 methods / 4 classes | `<clinit>`/static block double-write, ctor chaining, `switch`/`default` early return, inner-inside-inner class |

**Field vs. local reporting:** across all four scenarios, field-level dead
stores are reported under the owning `<clinit>` (static fields) or `<init>`
(instance fields) rather than at their declaration site, reflecting where
WALA's IR places the actual store instruction. Local-variable and parameter
findings retain source line numbers as in the earlier (non-complex) driver.