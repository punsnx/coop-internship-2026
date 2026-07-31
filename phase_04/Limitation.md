# Known Limitations

The tool uses WALA's `DefUse` over the SSA IR. Three limitations follow from
that choice. All were found by testing, and the `--debug` flag reproduces the
evidence for each.

Limitations 1 and 2 are both false negatives with the same underlying shape:
`DefUse` answers questions about SSA *value numbers*, but a dead store is a
question about *locals*. The fix in limitation 1 resolves both.

---

## 1. False negative (Two locals holding the same literal "test5")

**Status:** incorrect output.

```java
int a = 5;
int b = 5;
System.out.println(a);
```

Expected: `b` reported as a dead store at line 4.

Actual: `No dead stores detected.`

**Cause.** WALA's `SymbolTable` interns constants: every occurrence of `5` in a
method becomes one value number. Both `a` and `b` map to `v3`. The `--debug`
dump shows this directly on the `println` instruction:

```
6  invokevirtual ... println(I)V > v4,v3:#5 ... [3=[a, b]]
```

`[3=[a, b]]` means value number 3 carries two names. Since `println(a)` reads
`v3`, `DefUse.isUnused(v3)` is false, and `b` is invisible.

This is not a bug in the loop that can be patched. `isUnused(v)` answers "is
the constant 5 ever read?", but the question we need is "is the local `b` ever
read?". Those differ exactly when two locals hold the same literal.

**Severity.** `5` makes this look like a corner case, but it fires on `0` and
`1`, the most common literals in Java. Any dead store of `0` is invisible as
soon as `0` is read anywhere else in the same method:

```java
int deadZero = 0;      // not reported
int total = 0;
for (int i = 0; i < n; i++) { total = total + i; }
return total;          // reads 0 via total/i, so v(0) is "used"
```

Actual: `No dead stores detected.` Loop counters and zero-initialised
accumulators are ordinary code, so this misses dead stores routinely rather
than only when two locals coincidentally share a literal.

**Fix.** Key the analysis on locals rather than value numbers, by scanning the
Shrike bytecode: `StoreInstruction` / `LoadInstruction` expose `getVarIndex()`,
which identifies local slots unambiguously. Determining whether a local is read
later across branches and loops is backward live-variable analysis, which WALA
supports via `BasicFramework` (WALA has no ready-made `LiveVariables` class).

---

## 2. False negative (locals initialised with `new`)

**Status:** incorrect output.

```java
Object deadObj = new Object();   // not reported
String deadStr = "never read";   // reported correctly
```

Expected: both reported. Actual: only `deadStr`.

**Cause.** A constructor call reads the object it initialises. `--debug` shows
`v4` defined by the `new`, then consumed by the implicit `<init>`:

```
2   v4 = new <Application,Ljava/lang/Object>@3(line 5)
4   invokespecial < ..., Ljava/lang/Object, <init>()V > v4 @7 ...
v4 unused=false  def=4 = new <Application,Ljava/lang/Object>
```

That `invokespecial` is a genuine use of `v4`, so `DefUse.isUnused(v4)` is
false and the local can never be flagged, however it is used afterwards. This
is a separate root cause from limitation 1: interning conflates two locals,
whereas here a single local is kept alive by a compiler-inserted call.

It applies to every local initialised with `new`, so only primitives and
interned constants are reported today. Reference locals are effectively
unanalysed.

**Fix.** The same fix as limitation 1. Keying on bytecode stores asks whether
the local slot is loaded again, which the `<init>` call does not do — so the
constructor use stops masking the dead store.

---

## 3. Line numbers rely on a javac layout assumption "test4"

**Status:** correct on all current tests, including if/else and loops. Recorded
because it is an assumption, not an API guarantee.

WALA's SSA builder emits no instruction for a store to a local, so
`int dead = 69` produces no instruction to read a line number from — the
constant only exists in the `SymbolTable`. The line is instead recovered from
`nameIndex - 1`, where `nameIndex` is the first instruction index at which the
value carries a source name.

This works because javac opens a local's `LocalVariableTable` scope immediately
after its store, making the store the preceding instruction. The bytecode index
still resolves even though the SSA slot is null, which is what supplies the
line number.

Verified against test4 (if/else, loop, three methods): `x` is named at index 2
(line 13), and index 1 is the `istore` at line 12, which is correct.

This is inferred from javac's layout, not promised by the WALA API. Keying on
bytecode stores (fix above) would remove the assumption, since the store
instruction found *is* the line to report.

---

## 4. Stated scope

Per the phase assumptions, input is one file containing one class. Fields,
arrays, and multiple classes are not handled.