# Known Limitations

The tool builds WALA's SSA IR for each method and uses `DefUse` to decide what
is read. It reports three kinds of dead store: unused local variables, unused
method parameters, and unused class fields. The limitations below are the cases
where that approach is imprecise. Each was found by testing, and `--debug`
reproduces the proof for it.

There is one limitation that produces a wrong answer, and other two that only affect
the reported line number.

---

## 1. Two locals holding the same literal = missed dead store

**Status:** incorrect output. This is the only case that gives a wrong answer.

```java
int a = 5;
int b = 5;
System.out.println(a);
```

**Expected:** `b` reported as a dead store.

**Actual:** Nothing reported.

**Cause:** WALA's `SymbolTable` interns constants: every occurrence of `5` in a
method becomes a single value number. Both `a` and `b` map to it. The `--debug`
dump shows this on the `println` instruction as `[3=[a, b]]` which one value number
carrying two names. Because `println(a)` reads that value number, `DefUse`
reports it as used, and `b` is invisible.

This cannot be patched at the `DefUse` level. `DefUse` answers "is this value
number read?", but the question needed is "is the local `b` read?", and those
differ exactly when two locals share a literal.

**What a fix would require:** Key the analysis on locals instead of value
numbers, by reading the Shrike bytecode: `StoreInstruction` / `LoadInstruction`
expose `getVarIndex()`, which identifies local slots unambiguously. Deciding
whether a slot is read later across branches and loops is backward
live-variable analysis, which WALA supports through `BasicFramework` (there is
no ready-made `LiveVariables` class).

---

## 2. Parameter line numbers point at the first body line

**Status:** Correct method, imprecise line. Off by one or two lines.

An unused parameter is reported at the method's first executable line, not its
signature. In test6, `innerPar` is reported at line 9 (the `println`) though it
is declared on line 8.

**Cause:** This is not a `DefUse` issue. javac does not emit a
`LineNumberTable` entry for a method signature when the parameter generates no
code, so the signature line is simply not in the class file. The `--debug` dump
confirms it: every instruction and name in `innerMethod` maps to line 9 or 10,
and line 8 appears nowhere. No WALA call can return a line the class file does
not record.

The reported line always falls inside the correct method, so it still locates
the finding.

---

## 3. Local line numbers rely on a javac layout assumption

**Status:** Correct on all current tests, including if/else and loops. Recorded
because it is an assumption, not an API guarantee.

WALA's SSA builder emits no instruction for a store to a local, so
`int dead = 42` has no instruction to read a line from the constant exists
only in the `SymbolTable`. The line is recovered from `nameIndex - 1`, where
`nameIndex` is the first instruction at which the value carries a source name.
This works because javac opens a local's `LocalVariableTable` scope immediately
after its store, making the store the preceding instruction.

Keying on bytecode stores (the fix in section 1) would remove this assumption,
since the store instruction found would itself carry the line.

---

## Not limitations

For clarity, these were tested and work:

- **Nested classes to any depth:** "test6" nests a class inside a nested class;
  all three levels are analysed. The class-hierarchy walk handles `A$B$C`
  names transparently.
- **Synthetic parameters:** The outer-class references javac adds to inner
  class constructors (`this$0`, `this$1`, ...) are suppressed.
- **The `main` entry point:** The `args` parameter of `main(String[])` is
  suppressed, since the JVM contract forces it to exist.
- **Fields across methods:** A field written in one method and read in another
  is correctly seen as alive; a field written but never read is reported.
- **Multiple files and packages:** When given a directory, all `.java` files are
  compiled into one scope, so a field or method defined in one file and used in
  another (including across packages) is correctly seen as alive. Verified by
  `test9` (two files, one directory) and `test10` (two packages in
  subdirectories).

---

## Scope

The tool analyzes a single Java file or a directory of files compiled together as
one closed set. It does not resolve against external libraries or source outside
that set: a field or method used only by code not included in the input will be
reported as dead, because no use of it is visible. This is the expected behavior
for a self-contained program, but it means the tool is not suited to analyzing a
library in isolation, where much of the public API is used only by external
callers.