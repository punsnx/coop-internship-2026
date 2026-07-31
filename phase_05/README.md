# Dead Store Detector (Phase 5)

A static analysis tool that detects dead stores in a single Java source file
using [WALA](https://github.com/wala/WALA). It reports three kinds of dead
store:

- **Unused local variables** - assigned but never read.
- **Unused method parameters** - declared but never read in the body.
- **Unused class fields** - written but never read anywhere in the file.

The tool compiles the input file, builds WALA's SSA IR for each method, and
uses [`DefUse`](https://wala.github.io/javadoc/com/ibm/wala/ssa/DefUse.html) to
decide what is read.

## Requirements

- **JDK 21** — must be a JDK, not a JRE. The tool invokes `javac` internally to
  compile the input before analysing it.

## How to Run

```bash
./gradlew -q run --args="path/to/Main.java"
```

The input file must be named to match its public class (e.g. a `public class
Main` must live in `Main.java`), because the tool compiles it with `javac`.

### Example

```bash
./gradlew -q run --args="tests/mohammad/Main.java"
```

Output:

```
Dead store detected:
  Field: myNestedField, Line: 5
  Field: myField, Line: 12
  Parameter: myPar1, Line: 8
  Parameter: myPar2, Line: 21
  Variable: myVar2, Line: 15
```

Findings are grouped by category (Field, then Parameter, then Variable) and
sorted by line within each group. When nothing is found the tool prints
`No dead stores detected.`

Debug mode:

Add `--debug` to print the SSA IR, the unused-parameter map, and what `DefUse`
knows about every value number. Useful for understanding why something was or
was not reported.

```bash
./gradlew -q run --args="tests/mohammad/Main.java --debug"
```

## What the tool handles

- Nested classes to any depth (a class inside a nested class).
- Multiple methods per class.
- Unused locals, fields, and parameters, reported as dead stores.

It suppresses two things that are not source-level dead stores: the `args`
parameter of a `main(String[])` entry point, and the synthetic outer-class
references (`this$0`, `this$1`, ...) that javac adds to inner class
constructors.

## Test Cases

Each test directory holds the input (`Main.java`) and the tool's output
(`expected_*`).

| Test | Scenario | Result |
|------|----------|--------|
| `mohammad/` | The Phase 5 specification example | fields + parameters + local |
| `test1/` | Basic dead store | one local |
| `test2/` | No dead stores | none |
| `test3/` | Reassignment before use | one local |
| `test4/` | Branches, loop, unused field and parameter | field + parameter + locals |
| `test5/` | Two locals sharing a literal | known false negative |
| `test6/` | Class nested inside a nested class | fields + parameters + local |
| `test7/` | A used field alongside an unused one | only the unused field |
| `test8/` | Static nested class | field + parameter + local |

## Limitations

`test5` documents the one case the tool gets wrong: when two locals hold the
same literal, WALA's `SymbolTable` interns them into a single value number, so
`DefUse` cannot tell them apart. There are also two line-number caveats. See
[LIMITATIONS.md](LIMITATIONS.md) for the evidence, cause, and fix of each.

## Scope

Per the phase assumptions, the input is a single Java file.

## License

MIT — see [LICENSE](../LICENSE).