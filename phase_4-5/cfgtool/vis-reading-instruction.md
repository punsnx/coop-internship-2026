# CFG Visualization Reading Guide

> How to read `out/output.pdf` / `out/fibo_cfg.dot` from `./test.sh`.
> All examples are taken directly from your fibo graph.

---

## Why It Looks More Complex Than the Source

Three reasons (from WALA wiki — *Intermediate Representation (IR)*):

| Reason | What happens |
|--------|-------------|
| **Exception edges** | WALA treats every call as a *Potentially Excepting Instruction (PEI)* — so every call-containing BB gets an extra arrow to EXIT |
| **BB split at calls** | WALA ends a BB at every call, so one source line with 2 calls → multiple BBs |
| **String `+` → `invokedynamic`** | `"foo" + n` compiles to `StringConcatFactory.makeConcatWithConstants` via JVM indirect dispatch |

---

## Outer Structure — Clusters

Each dashed box = **one method**. Label = full JVM method signature.

```
com.sirisuk.AnalysisClass.up(I)J
                            ↑  ↑
                     param: I=int   return: J=long
```

**JVM type descriptors:** `I`=int, `J`=long, `V`=void, `[J`=long[], `Ljava/lang/String;`=String

Your 5 clusters:

| Cluster | Source method | Note |
|---------|--------------|------|
| `cluster_0` | `Main.main(String[])` | entry point |
| `cluster_1` | `AnalysisClass.run()` | main logic |
| `cluster_2` | `AnalysisClass.<clinit>()` | auto-generated static initializer for `static long[] memo` |
| `cluster_3` | `AnalysisClass.up(int)` | bottom-up DP |
| `cluster_4` | `AnalysisClass.down(int)` | top-down memoization |

---

## Inside a Basic Block

A **basic block** = straight-line instructions, no branches except at the very end.

```
BB7
─────────────────
16 = phi  15,9
conditional branch(gt, to iindex=43) 16,1
```

| Token | Meaning |
|-------|---------|
| `BB7` | Block ID — WALA's internal number, **not** a source line |
| `[ENTRY]` / `[EXIT]` | Method start / end marker |
| `[CATCH]` | Exception handler block |

**Colors:**

| Color | Block type |
|-------|-----------|
| Light blue | ENTRY |
| Light green | EXIT |
| Light red | CATCH (exception handler) |
| Off-white | Normal |

---

## SSA Value Numbers — The Numbers in Instructions

Every computed value gets a **unique integer ID** (WALA wiki: *"variables have unique identifiers, value numbers v1, v2…"*).

```
9 = invokestatic < Application, Lcom/sirisuk/AnalysisClass, up(I)J > 2 @19 exception:8
↑                                                                       ↑  ↑            ↑
result=9                                                          arg=2  @bytecode:19   throws→BB8
(return value of up())                                            (n=10)
```

**Assignment conventions (from WALA wiki):**
- Static method params: `v1`=first param, `v2`=second, …
- Non-static method: `v1`=`this`, `v2`=first param, …
- Constants don't get explicit assignment — access them via `IR.getSymbolTable().getConstantValue(vN)`

**Rule:** number **before** `=` is the output; numbers **after** the instruction keyword are inputs.

---

## Instruction Reference

### Notation used in patterns

| Symbol | Means |
|--------|-------|
| `N` | result value number (output, left of `=`) |
| `A`, `B` | input value numbers (operands) |
| `obj` | receiver object value number (instance method) |
| `arr` | array reference value number |
| `idx` | array index value number |
| `val` | value being stored |
| `@X` | bytecode instruction index in the `.class` file |
| `iindex=X` | jump target bytecode index |
| `exception:E` | if instruction throws → jump to BB**E** |

---

### All Instructions at a Glance

| Instruction | Operands | Java equivalent |
|-------------|----------|----------------|
| `N = getstatic <Loader,Class,field,Type>` | N=result | `N = ClassName.field` |
| `putstatic <Loader,Class,field,Type> = A` | A=value to write | `ClassName.field = A` |
| `N = invokestatic <Loader,Class,method(sig)> A @X exception:E` | N=return, A=arg(s), @X=bytecode slot, E=exception BB | `N = ClassName.method(A)` |
| `invokevirtual <Loader,Class,method(sig)> obj,A @X exception:E` | obj=receiver, A=arg(s) | `obj.method(A)` |
| `[invokedynamic] N = invokestatic StringConcatFactory... A @X exception:E` | N=result string, A=value inserted | `N = "..." + A` |
| `N = binaryop(add) A, B` | N=result, A=left, B=right | `N = A + B` |
| `N = binaryop(sub) A, B` | N=result, A=left, B=right | `N = A - B` |
| `N = arrayload arr[idx]` | N=result, arr=array ref, idx=index | `N = arr[idx]` |
| `arraystore arr[idx] = val` | arr=array ref, idx=index, val=value | `arr[idx] = val` |
| `N = conversion(J) A` | N=result long, A=input int | `N = (long) A` |
| `N = new <Primordial,[J>@X` | N=array ref, X=allocation bytecode slot | `N = new long[...]` |
| `N = compare A, B opcode=cmp` | N=int result (-1/0/1), A,B=long or float values | used before `conditional branch(eq/ne)` |
| `N = phi A, B` | N=result, A=from pred-1, B=from pred-2 | merge at loop header or if-else join |
| `conditional branch(op, to iindex=X) A, B` | op=comparator, X=jump target, A,B=operands | `if (A op B) goto X` |
| `goto (from iindex=X to iindex=Y)` | X=current, Y=target | unconditional jump (loop back-edge) |
| `return` | — | `return;` |
| `return N` | N=value | `return N;` |

---

### Field Access

```
3 = getstatic < Application, Ljava/lang/System, out, <Application,Ljava/io/PrintStream> >
↑
N=3 → System.out stored in value 3

putstatic < Application, Lcom/sirisuk/AnalysisClass, memo, <Primordial,[J> > = 3
                                                                                ↑
                                                                           val=3 → memo = value3
```
`getstatic` = read static field; `putstatic` = write static field.

---

### Method Calls

```
9 = invokestatic < Application, Lcom/sirisuk/AnalysisClass, up(I)J > 2 @19 exception:8
↑                                                                      ↑
N=9 (return value)                                               A=2 (argument n)

invokevirtual < Application, Ljava/io/PrintStream, println(Ljava/lang/String;)V > 3,5 @12 exception:6
                                                                                   ↑ ↑
                                                                         obj=3 (PrintStream)  A=5 (string arg)
```
- `invokestatic` — static method, no receiver (`obj`)
- `invokevirtual` — instance method, first operand is the object (`obj`), rest are args

---

### String Concatenation

```
[invokedynamic] 5 = invokestatic < ..., makeConcatWithConstants(I)Ljava/lang/String; > 2 @7 exception:4
                ↑                                                                       ↑
               N=5 (result string)                                                 A=2 (n, the int)
```
Source: `"Fibonacci DP Demo (n = " + n` → Java compiles `+` on strings to this JVM factory call.

---

### Arithmetic

```
34 = binaryop(add) 35, 33
↑                  ↑   ↑
N=34 (new i)      A=35  B=33(constant 1)   →  i + 1   (the i++ in the for loop)

10 = binaryop(sub) 16, 3
↑                  ↑   ↑
N=10              A=16  B=3(constant 1)    →  i - 1
```

---

### Array Operations

```
11 = arrayload 5[10]
↑              ↑  ↑
N=11          arr=5  idx=10    →  dp[i-1]

arraystore 5[6] = 7
           ↑  ↑   ↑
         arr=5 idx=6 val=7    →  dp[0] = 0
```

---

### Type Conversion & Allocation

```
18 = conversion(J) 1
↑                  ↑
N=18              A=1 (param n, int)    →  (long) n    widening int→long

5 = new <Primordial,[J>@114
↑                       ↑
N=5 (array ref)     allocated at bytecode index 114    →  new long[n+1]
```

---

### Branches & Jumps

```
conditional branch(gt, to iindex=41) 35, 2
                   ↑         ↑       ↑   ↑
                  op=gt  jump to 41  A=35  B=2(n=10)    →  if (i > n) jump, else fall through

goto (from iindex=42 to iindex=21)    →  unconditional jump (loop back-edge)
```

**Condition operators:** `gt`>`>` · `ge`≥ · `lt`<`<` · `le`≤ · `eq`== · `ne`!=

> `conditional branch` always has **two** outgoing arrows from its BB:
> - **true path** → jumps to `iindex=X`
> - **false path** → falls through to the next BB (solid arrow going down)

---

### Phi Node

```
16 = phi  15, 9
↑          ↑   ↑
N=16     from pred-A  from pred-B
```

Phi only appears at **join points** (loop header, after if-else).
WALA wiki: *"phi statements stored on BasicBlocks, execute at block beginning"* — that's why they're printed first in a BB and iterated separately via `bb.iteratePhis()`.

Example from `up()` BB7:
```
16 = phi  15, 9
          ↑    ↑
     i+1 (BB11: binaryop)    i=2 (initial, set before loop)
→  "value 16 is the current loop variable i, whichever path arrived here"
```

---

### Return

```
return        →  return; (void)
return 17     →  return dp[n]; (value 17 = arrayload result)
```

---

## Edges (Arrows)

| Style | Meaning |
|-------|---------|
| Solid black → | Normal control flow |
| Two solid black → from same BB | Conditional branch: one arrow per outcome |
| Dashed blue → `calls` | Inter-method call (crosses cluster boundary) |
| Bold red → `recursive` | Self-recursive call |
| Any → `[EXIT]` | Exception escape — fired by `exception:E` in PEI instructions |

**Why every call BB has an → EXIT:**
WALA wiki — *"CFG represents exceptional edges; assumes all PEIs might throw; no optimization to eliminate infeasible exceptional paths."*
Use `PrunedCFG` if you want a view without exception edges.

---

## Loop Pattern

Source:
```java
for (int i = 0; i <= n; i++) { ... }
```

CFG signature:
```
BB (before loop) → BB12 [loop header]
                         ↑         ↓ (condition false = exit)
                    BB19 (i++)   BB20 (return)
                    ↑               ↓
                BB13–BB18 (body)
```

BB12 always has:
1. `phi` — merges initial `i=0` and updated `i+1`
2. `conditional branch` — the loop exit condition

---

## Recursion Pattern

Source: `memo[n] = down(n-1) + down(n-2);`

```
BB7: invokestatic down(n-1)  →  red bold arrow → BB0 (ENTRY of down)
BB8: invokestatic down(n-2)  →  red bold arrow → BB0 (ENTRY of down)
```

Both arrows point to the same ENTRY because WALA's context-insensitive analysis merges all calls to `down` into one node.

---

## Quick Reference

```
Cluster (dashed box)   = one method
BB N                   = basic block #N  (not a source line)
Light blue             = ENTRY · Light green = EXIT · Light red = CATCH

Value numbers:
  N = result (left of =)       9 = invokestatic ... → result stored in 9
  A, B = inputs (after opcode) binaryop(add) 35, 33 → 35+33
  @X = bytecode slot           @19 = position in .class file
  exception:E = throw → BBE   exception:8 = if throws, go to BB8

Arrows:
  solid black   = flow       two out of BB = branch (true/false)
  dashed blue   = call       bold red      = recursive
  → EXIT        = exception path (every PEI gets one)

Key patterns:
  phi A, B           = join point (loop header or if-else merge)
  conditional branch = if / for / while condition  (always 2 outgoing arrows)
  goto               = loop back-edge
  [invokedynamic]    = string + concat
```

---

## WALA Wiki References

| Topic | Wiki page |
|-------|-----------|
| IR structure, value numbers, phi, `iteratePhis()`, `PrunedCFG` | [Intermediate Representation (IR)](https://github.com/wala/WALA/wiki/Intermediate-Representation-(IR)) |
| `< Loader, Class, method >` naming, `TypeReference` vs `IClass` | [Naming Java Entities](https://github.com/wala/WALA/wiki/Naming-Java-Entities) |
| Map value numbers back to source names via `IR.getLocalNames()` | [Mapping to source code](https://github.com/wala/WALA/wiki/Mapping-to-source-code) |
| CHA → CallGraph → IR pipeline overview | [Technical Overview](https://github.com/wala/WALA/wiki/Technical-Overview) |
| Call graph construction algorithms (0-CFA, 0-1-CFA) | [Call Graph Construction](https://github.com/wala/WALA/wiki/Call-Graph-and-CAst-Call-Graph-Details) |