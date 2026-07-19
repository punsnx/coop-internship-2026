# Dead Store Detector

A static analysis tool that detects dead stores in Java source files.
A dead store is a variable that is assigned a value but never read before
being reassigned or the method ends.

## Requirements

- JDK 21

## How to Run

```bash
./gradlew run --args="path/to/Input.java"
```

## Example

```bash
./gradlew run --args="test1/Main.java"
```

## Test Cases

- `test1/` — Basic dead store
- `test2/` — No dead stores
- `test3/` — Reassignment dead store
- `test4/` — Complex assignment dead store with if-else, loop, and methods
- `test5/` — Basic dead store but same values across variables
