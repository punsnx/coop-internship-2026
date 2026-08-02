# Phase 5 — Dead-Store Detector

A static analysis tool, built on [WALA](https://github.com/wala/WALA), that
detects **dead stores** in Java code: variables, fields, and parameters that are
assigned a value which is never read.

This is the extended version of the Phase 4 detector. It adds unused fields and
parameters, nested-class support, and analysis across multiple files, and it
reports the source file of each finding.

## What it detects

- **Unused local variables** — assigned but never read.
- **Unused class fields** — written but never read anywhere in the analyzed code.
- **Unused method parameters** — declared but never used in the method body.

It handles nested classes to any depth and multiple methods per class, and it can
analyze a single file or an entire directory of files across several packages.

## Requirements

- **JDK 21** — must be a JDK, not a JRE (the tool invokes `javac` internally).
- Gradle (via the included wrapper).

## Usage

Analyze a single file:

```bash
./gradlew -q run --args="path/to/Main.java"
```

Analyze a whole directory (all `.java` files under it, across packages):

```bash
./gradlew -q run --args="path/to/project"
```

Add `--debug` to print WALA's intermediate representation and the internal
analysis state.

## Output

Each dead store is reported with its category, name, source line, and file,
grouped by category (Field, Parameter, Variable) and sorted by line:

```
Dead store detected:
  Field: neverRead, Line: 4 at "model/Data.java"
  Parameter: myPar1, Line: 8 at "com/example/Main.java"
  Variable: dead, Line: 4 at "Main.java"
```

If nothing is found, it prints `No dead stores detected.`

File paths are given relative to the package root (for example
`model/Data.java`). A nested class is reported under its outer class's file,
since that is where its source lives.

## How it works

1. **Compile** the input (a file, or every `.java` file under a directory) into a
   temporary directory, using `javac -g` to preserve variable names and line
   numbers.
2. **Build** WALA's class hierarchy and, for each application method, its SSA
   intermediate representation.
3. **Detect** the three kinds of dead store: locals and parameters, using WALA's `DefUse` information;
   and fields, by matching writes (`putstatic` / `putfield`) against reads
    (`getstatic` / `getfield`) across all methods.
4. **Report** the findings, sorted and with source locations.

Because all input files are compiled into one scope, a field or method defined in
one file and used in another is correctly recognized as live.

## Test Cases

Each `tests/` subdirectory contains an input and its expected output.

| Test | Scenario |
|------|----------|
| test1–test3 | Basic local dead stores |
| test4 | Branches, a loop, an unused field and parameter |
| test5 | Two locals sharing a literal — a known limitation |
| test6 | A class nested inside a nested class |
| test7 | A used field alongside an unused one |
| test8 | A static nested class |
| test9 | Two files in one directory (cross-file field use) |
| test10 | Files in separate packages (cross-package field use) |
| mohammad | The Phase 5 specification's own example |

## Limitations

The tool's known limitations — including a case involving two locals that share a
literal — are documented in [LIMITATIONS.md](LIMITATIONS.md).

## License

MIT — see [LICENSE](LICENSE).