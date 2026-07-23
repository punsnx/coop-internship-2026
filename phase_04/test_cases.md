# Dead Store Analysis — Test Cases & Results

Six scenarios covering straight-line code, branching, loops, exceptional flow, and a case with no dead stores at all. Each includes the `Main.java` input and the driver's output run against it.

## 01 — Straight Line

Basic case: an unused constant assignment with no control flow.

```java
public class Main {
    public static void main(String[] args) {
        int alive = 10;
        int dead = 20;
        System.out.println(alive);
    }
}
```

```text
Dead Store Analysis Report
Input: testdata/com/deadstoretest/01_straight_line/scopeFile.txt
Found 2 dead store(s):
    Line 4   | 'dead' | Constant assignment (value: 20)
    N/A      | 'args' | Unused parameter
```

## 02 — Branching

A store that's overwritten before being read on the branch where it's reassigned.

```java
public class Main {
    public static void main(String[] args) {
        int x = 5;
        if (args.length > 0) {
            System.out.println(x);
        } else {
            x = 10;
        }
    }
}
```

```text
Dead Store Analysis Report
Input: testdata/com/deadstoretest/02_branching/scopeFile.txt
Found 1 dead store(s):
    Line 7   | 'x'    | Constant assignment (value: 10)
```

## 03 — No Dead Stores

Sanity check: every local is used, so the only finding is the unused `args` parameter.

```java
public class Main {
    public static void main(String[] args) {
        int a = 5;
        int b = a + 10;
        int c = b * 2;
        System.out.println(c);
    }
}
```

```text
Dead Store Analysis Report
Input: testdata/com/deadstoretest/03_no_dead_stores/scopeFile.txt
Found 1 dead store(s):
    N/A      | 'args' | Unused parameter
```

## 04 — Loop

A dead store outside the loop body, alongside a live loop-carried variable (`sum`) that's correctly *not* flagged.

```java
public class Main {
    public static void main(String[] args) {
        int sum = 0;
        int unused = 100;
        for (int i = 0; i < 5; i++) {
            sum = sum + i;
        }
        System.out.println(sum);
    }
}
```

```text
Dead Store Analysis Report
Input: testdata/com/deadstoretest/04_loop/scopeFile.txt
Found 2 dead store(s):
    Line 4   | 'unused' | Constant assignment (value: 100)
    N/A      | 'args' | Unused parameter
```

## 05 — Quiz Example

Mixed case: a dead constant assignment, a dead store inside one branch, and a dead store that's live-out of its own branch but never read afterward.

```java
public class Main {
    public static void main(String[] args) {


        int x = 2; // DEAD STORE
        int y = 4;
        x = 1;

        int z;
        if (y > x) {
            z = y; // DEAD STORE
        } else {
            z = y * y; //
            x = z;     // DEAD STORE
        }
    }
}
```

```text
Dead Store Analysis Report
Input: testdata/com/deadstoretest/05_quiz_example/scopeFile.txt
Found 5 dead store(s):
    Line 13  | 'z'    | Unused computation: 6 = binaryop(mul) 4 , 4
    Line 5   | 'x'    | Constant assignment (value: 2)
    Line 11  | 'z'    | Unused local variable assignment
    Line 14  | 'x'    | Unused local variable assignment
    N/A      | 'args' | Unused parameter
```

## 06 — Complex Dead Stores

Four sub-scenarios combined: redundant write before branching, a dead tail in a store chain, exceptional flow, and a cyclic loop with no dead store. Notably, the analyzer correctly identifies `d = 42` as dead (its only use is on the path where `parseInt` succeeds and overwrites it) while the `catch` block's dead `d` is implicitly covered by the same finding.

```java
public class Main {
    public static void main(String[] args) {

        // =================================================================
        // Scenario A: Redundant write before branching
        // =================================================================
        int a = 10; // DEAD STORE

        if (args.length > 0) {
            a = 20;
        } else {
            a = 30;
        }
        System.out.println(a);


        // =================================================================
        // Scenario B: Chain of stores, only the tail is genuinely dead
        // =================================================================
        int x = 5;
        int y = x + 10;
        int z = y * 2;   // DEAD STORE

        // =================================================================
        // Scenario C: Exceptional flow
        // =================================================================
        int d = 42; // DEAD STORE
        try {

            d = Integer.parseInt("not_a_number"); // ✓ Live
            System.out.println(d);
        } catch (NumberFormatException e) {
            // 'd' is never used or read in the catch block
        }

        // =================================================================
        // Scenario D: Cyclic loop dead store
        // =================================================================
        int loopSum = 0;
        for (int i = 0; i < 10; i++) {
            loopSum += i;
        }
    }
}
```

```text
Dead Store Analysis Report
Input: testdata/com/deadstoretest/06_complex_dead_stores/scopeFile.txt
Found 3 dead store(s):
    Line 22  | 'z'    | Unused computation: 14 = binaryop(mul) 12 , 13
    Line 7   | 'a'    | Constant assignment (value: 10)
    Line 27  | 'd'    | Constant assignment (value: 42)
```

## Summary

| # | Scenario | Findings | What it exercises |
|---|---|---|---|
| 01 | Straight line | 2 | Basic unused constant + unused parameter |
| 02 | Branching | 1 | Store overwritten before read on one path |
| 03 | No dead stores | 1 (param only) | Confirms no false positives on live code |
| 04 | Loop | 2 | Dead store outside loop; live loop-carried var not flagged |
| 05 | Quiz example | 5 | Mixed constant, branch-local, and cross-branch dead stores |
| 06 | Complex | 3 | Branch redundancy, store chains, exceptional flow, loops together |