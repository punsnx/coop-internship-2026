# ScopeFileCallGraph

---

## Table of Contents

- [Functionality](#functionality)
- [Input & Output](#input--output)
- [Run Instructions](#run-instructions)
    - [Prerequisites](#prerequisites)
    - [Dependencies](#dependencies-buildgradlekts)
    - [Steps](#steps)
    - [Class Name Format](#class-name-format)
    - [Program Code](#program-code)
    - [Sample Input](#sample-input)
    - [Expected Output](#expected-output)
- [Reproducibility](#reproducibility)

---

## Functionality

`ScopeFileCallGraph` is a WALA driver that constructs an interprocedural call graph for a Java application by loading pre-compiled bytecode (`.class` files or a binary directory) declared in a scope file. It builds a class hierarchy over the declared scope, selects entrypoints from either a `main()` method or all public methods of a given class, then runs a 0-1-Container-CFA call graph algorithm. The driver prints call graph statistics and, for every application-layer method, lists each callee tagged as `[app]` (your code) or `[lib]` (standard library / framework).

---

## Input & Output

- **Input:** 
  - A WALA scope file (`-scopeFile`) is a plain text file with one `Loader,Language,type,path` entry per line.
  - Then combined with either `-mainClass` (JVM internal name of the class whose `main()` is the entrypoint) or `-entryClass` (all public methods used as entrypoints).
  - The scope file must contain at least a `Primordial,Java,stdlib,none` line and an `Application,Java,binaryDir,<path>` (or `classFile`) line pointing to the compiled bytecode.
- **Output:** 
  - `Class count`
  - `WALA warnings`
  - `call graph build time`
  - `call graph statistics` (nodes / edges / methods / bytecode bytes)
  - `Application Call Graph` section listing every application method with its direct callees (optional).

---

## Run Instructions

### Prerequisites

1. **Java 21** — must be set as `$JAVA_HOME`. Verify: `echo $JAVA_HOME`
2. **Gradle wrapper** — bundled as `./gradlew` (no install needed)
3. **Target compiled to `.class` files** — bytecode of the Java program to analyze

### Dependencies (`build.gradle.kts`)

| Artifact                           | Version |
|------------------------------------|----|
| `com.ibm.wala:com.ibm.wala.core`   | 1.7.2 |
| `com.ibm.wala:com.ibm.wala.util`   | 1.7.2 |
| `com.ibm.wala:com.ibm.wala.shrike` | 1.7.2 |
| Java Version (tested)              | 21 |

### Steps

0. **Install JDK 21**
   ```bash
   brew install oracle-jdk@21 && echo "export JAVA_HOME=/path/to/java_home" >> ~/.bashrc
   ```

1. **Clone and build the project:**
   ```bash
   git clone https://github.com/wala/WALA-start.git
   cd WALA-start
   export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home
   ./gradlew compileJava
   ```

2. **Compile the sample target application:**
   ```bash
   javac -d target/classes \
     src/main/java/com/example/*.java
   ```

3. **Build the WALA stdlib cache (one-time setup):**

   Java 9+ no longer ships `rt.jar`; WALA requires a JAR of the standard library. Extract it once from `java.base.jmod`:
   ```bash
   mkdir -p /tmp/wala-stdlib/x
   unzip -q "$JAVA_HOME/jmods/java.base.jmod" -d /tmp/wala-stdlib/x || true
   "$JAVA_HOME/bin/jar" cf /tmp/wala-stdlib/java.base.jar \
       -C /tmp/wala-stdlib/x/classes .
   rm -rf /tmp/wala-stdlib/x
   ```
   This only needs to run once. Delete `/tmp/wala-stdlib` to force a rebuild (e.g. after changing JDK).

4. **Set the environment variable for WALA:**

   `src/main/resources/wala.properties` must point to the stdlib cache directory:
   ```properties
   java_runtime_dir=/tmp/wala-stdlib
   ```

5. **Create `scope.txt`** pointing to the compiled target:
   ```
   Primordial,Java,stdlib,none
   Application,Java,binaryDir,/absolute/path/to/buildpath
   ```
   Scope file format: `Loader,Language,type,path`
   - `Primordial` — Java standard library
   - `Application` — your code
   - `binaryDir` — directory of `.class` files; use `classFile` for a single file

6. **Run the driver:**
   ```bash
   ./gradlew run \
     -PmainClass=com.ibm.wala.examples.drivers.ScopeFileCallGraph \
     --args="-scopeFile scope.txt -mainClass Lcom/example/Main"
   ```

   **NOTE:** For full automated steps 3–6 script: (run it at WALA-start project root directory)
   
   > **test.sh** 
   > ```bash
   >
   > #!/bin/bash
   > if [ -d /tmp/wala-stdlib/ ]; then rm -rf /tmp/wala-stdlib* ;fi
   >
   > SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
   >
   > #JAVA_HOME="/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home"
   > JAVA_VERSION=$("$JAVA_HOME/bin/java" --version | awk 'NR==1{split($2,a,"."); print a[1]}')
   > sed -i '' "s/JavaLanguageVersion\.of(\([0-9]*\.*\)*)/JavaLanguageVersion.of($JAVA_VERSION)/" "$SCRIPT_DIR/build.gradle.kts"
   > echo "=== build.gradle.kts toolchain set to Java $JAVA_VERSION ==="
   >
   > "$SCRIPT_DIR/gradlew" -p "$SCRIPT_DIR" --stop
   > "$SCRIPT_DIR/gradlew" -p "$SCRIPT_DIR" --version | grep JVM
   > "$SCRIPT_DIR/gradlew" -p "$SCRIPT_DIR" clean compileJava
   >
   > BUILD_DIR="$(cd "$SCRIPT_DIR" && realpath "/absolute/path/to/buildpath")"
   > # --- WALA properties ---
   > mkdir -p src/main/resources/
   > echo "java_runtime_dir=/tmp/wala-stdlib" > src/main/resources/wala.properties
   >
   > # --- ScopeFile ---
   > SCOPE_FILE="$SCRIPT_DIR/scope.txt"
   >
   > {
   > echo "Primordial,Java,stdlib,none"
   > echo "Application,Java,binaryDir,$BUILD_DIR"
   > } > "$SCOPE_FILE"
   > 
   > JDK_HOME="${JAVA_HOME:?JAVA_HOME must be set}"
   > 
   > if [ ! -f /tmp/wala-stdlib/java.base.jar ]; then
   > echo "Building WALA stdlib cache (one-time)..."
   > mkdir -p /tmp/wala-stdlib/x
   > unzip -q "$JDK_HOME/jmods/java.base.jmod" -d /tmp/wala-stdlib/x || true
   > "$JDK_HOME/bin/jar" cf /tmp/wala-stdlib/java.base.jar \
   > -C /tmp/wala-stdlib/x/classes .
   > rm -rf /tmp/wala-stdlib/x
   > fi
   > 
   > # Test script below this section
   > # --- ScopeFileCallGraph ---
   > "$SCRIPT_DIR/gradlew" -p "$SCRIPT_DIR" run \
   > -PmainClass=com.ibm.wala.examples.drivers.ScopeFileCallGraph \
   > --args="-scopeFile $SCOPE_FILE -mainClass Lcom/example/Main"
   >  
   > ```
   ***JAVA_HOME*** uncomment if you have a different JDK version.

   ***Replace*** `BUILD_DIR` for your build_class_path and `-mainClass Lcom/example/Main` for the entrypoint for your application.
### Class name format

WALA uses JVM internal names. Prefix `L`, replace `.` with `/`:

| Java name | WALA `-mainClass` argument |
|-----------|--------------------------|
| `Main` (default package) | `LMain` |
| `com.sirisuk.Main` | `Lcom/sirisuk/Main` |

---

### Program Code

**`ScopeFileCallGraph.java`**
```java

public class ScopeFileCallGraph {

  public static void main(String[] args)
      throws IOException, ClassHierarchyException, IllegalArgumentException, CancelException {
    long start = System.currentTimeMillis();
    Properties p = CommandLine.parse(args);
    String scopeFile = p.getProperty("scopeFile");
    String entryClass = p.getProperty("entryClass");
    String mainClass = p.getProperty("mainClass");
    if (mainClass != null && entryClass != null) {
      throw new IllegalArgumentException("only specify one of mainClass or entryClass");
    }
    AnalysisScope scope =
        AnalysisScopeReader.instance.readJavaScope(
            scopeFile, null, ScopeFileCallGraph.class.getClassLoader());
    // set exclusions.  we use these exclusions as standard for handling JDK 8
    ExampleUtil.addDefaultExclusions(scope);
    IClassHierarchy cha = ClassHierarchyFactory.make(scope);
    System.out.println(cha.getNumberOfClasses() + " classes");
    System.out.println(Warnings.asString());
    Warnings.clear();
    AnalysisOptions options = new AnalysisOptions();
    Iterable<Entrypoint> entrypoints =
        entryClass != null
            ? makePublicEntrypoints(cha, entryClass)
            : Util.makeMainEntrypoints(cha, mainClass);
    options.setEntrypoints(entrypoints);
    
    AnalysisCache cache = new AnalysisCacheImpl();
    // other builders can be constructed with different Util methods
    CallGraphBuilder<InstanceKey> builder =
        Util.makeZeroOneContainerCFABuilder(options, cache, cha);
    System.out.println("building call graph...");
    CallGraph cg = builder.makeCallGraph(options, null);

    long end = System.currentTimeMillis();
    System.out.println("done");
    System.out.println("took " + (end - start) + "ms");
    System.out.println(CallGraphStats.getStats(cg));

    //for optional app call graph printing
    printAppCallgraph(cg);
  }
    
  private static Iterable<Entrypoint> makePublicEntrypoints(
      IClassHierarchy cha, String entryClass) {
    Collection<Entrypoint> result = new ArrayList<>();
    IClass klass =
        cha.lookupClass(
            TypeReference.findOrCreate(
                ClassLoaderReference.Application,
                StringStuff.deployment2CanonicalTypeString(entryClass)));
    for (IMethod m : klass.getDeclaredMethods()) {
      if (m.isPublic()) {
        result.add(new DefaultEntrypoint(m, cha));
      }
    }
    return result;
  }
  //for optional app call graph printing
  private static void printAppCallgraph(CallGraph cg){
    System.out.println("\n=== Application Call Graph ===");
    for (CGNode caller : cg) {
      if (!caller.getMethod().getDeclaringClass()
              .getClassLoader().getReference()
              .equals(ClassLoaderReference.Application)) continue;

      System.out.println("\n[" + caller.getMethod().getSignature() + "]");
      Iterator<CGNode> succs = cg.getSuccNodes(caller);
      while (succs.hasNext()) {
        CGNode callee = succs.next();
        boolean isApp = callee.getMethod().getDeclaringClass()
                .getClassLoader().getReference()
                .equals(ClassLoaderReference.Application);
        String tag = isApp ? "[app]" : "[lib]";
        System.out.println("  --> " + tag + " " + callee.getMethod().getSignature());
      }
    }
  }
}


```

### Sample Input

**`Main.java`**
```java
package com.sirisuk;

public class Main {
    public static void main(String[] args) {
        AnalysisClass.run();
    }
}
```

**`AnalysisClass.java`**
```java
package com.sirisuk;

public class AnalysisClass {

    public static long up(int n) {
        if (n <= 1) return n;
        long[] dp = new long[n + 1];
        dp[0] = 0; dp[1] = 1;
        for (int i = 2; i <= n; i++) dp[i] = dp[i - 1] + dp[i - 2];
        return dp[n];
    }

    private static long[] memo = new long[100];

    public static long down(int n) {
        if (n <= 1) return n;
        if (memo[n] != 0) return memo[n];
        memo[n] = down(n - 1) + down(n - 2);
        return memo[n];
    }

    public static void run() {
        int n = 10;
        System.out.println("Fibonacci DP Demo (n = " + n + ")");
        System.out.println("Bottom-up : " + up(n));
        System.out.println("Top-down  : " + down(n));
        System.out.println("\nSequence (0.." + n + "):");
        for (int i = 0; i <= n; i++) {
            System.out.print(up(i) + (i < n ? " " : "\n"));
        }
    }
}
```

**`wala.properties`**
```
java_runtime_dir=/tmp/wala-stdlib
```

**`scope.txt`**
```
Primordial,Java,stdlib,none
Application,Java,binaryDir,/Users/sirisuk/Desktop/SKDEV/CS/y4s1/internship/test-app/wala/fibo/target/classes
```

---

### Expected Output

```text

took 2618ms
Call graph stats:
  Nodes: 8269
  Edges: 41059
  Methods: 5841
  Bytecode Bytes: 411139


=== Application Call Graph ===

[com.sirisuk.Main.main([Ljava/lang/String;)V]
  --> [app] com.sirisuk.AnalysisClass.run()V

[com.sirisuk.AnalysisClass.run()V]
  --> [app] com.sirisuk.AnalysisClass.up(I)J
  --> [app] com.sirisuk.AnalysisClass.down(I)J
  --> [lib] java.io.PrintStream.print(Ljava/lang/String;)V
  --> [lib] java.io.PrintStream.println(Ljava/lang/String;)V

[com.sirisuk.AnalysisClass.<clinit>()V]

[com.sirisuk.AnalysisClass.up(I)J]

[com.sirisuk.AnalysisClass.down(I)J]
  --> [app] com.sirisuk.AnalysisClass.down(I)J

```

- **Visualization** 
  - Control Flow Graph (CFG), Basic Block (BB) and Call Graph (CG) [View PDF](https://github.com/punsnx/coop-internship-2026/blob/8a106723616c2ea583a12b792f0d72630798bec9/phase_01/other/assets/global/visualize_ScopeFileCallGraph.pdf)
  - <details>
      <summary><label>Implementation</label> </summary>

      ```java
        
          private static void printVisualization(CallGraph cg) throws IOException, WalaException {
            new File("out").mkdirs();
            DotUtil.setOutputType(DotUtil.DotOutputType.PDF);
            
            // --- Collect unique app methods (deduplicate by signature) ---
            List<CGNode> methods = new ArrayList<>();
            Map<String, Integer> methodIndex = new HashMap<>();
            for (CGNode node : cg) {
                boolean isApp = node.getMethod().getDeclaringClass().getClassLoader()
                .getReference().equals(ClassLoaderReference.Application);
                if (!isApp || node.getIR() == null) continue;
                    String sig = node.getMethod().getSignature();
                if (!methodIndex.containsKey(sig)) {
                    methodIndex.put(sig, methods.size());
                    methods.add(node);
                }
            }
            
            // --- DOT graph header ---
            StringBuilder dot = new StringBuilder();
            dot.append("""
            digraph "FiboCFG" {
            compound=true;
            rankdir=TB;
            node [shape=box, fontname="Courier", fontsize=9];
            edge [fontsize=9];
            
                  """);
            
            Map<String, String> methodEntryNode = new HashMap<>();  // sig → entry BB node id
            
            // --- One cluster per method ---
            for (int m = 0; m < methods.size(); m++) {
            IR ir = methods.get(m).getIR();
            SSACFG cfg = ir.getControlFlowGraph();
            String sig = methods.get(m).getMethod().getSignature();
            String idPrefix = "m" + m + "_";       // unique node id prefix per method
            
        //        System.out.println("\n[BB Dump: " + sig + "]\n" + ir);
            
                dot.append("""
                      subgraph cluster_%d {
                        label="%s";
                        style=dashed; color=grey; fontsize=11; fontname="Helvetica-Bold";
            
                    """.formatted(m, dotLabel(sig)));
            
                // Basic block nodes
                for (ISSABasicBlock bb : cfg) {
                  String nodeId = idPrefix + "bb" + bb.getNumber();
                  if (bb.isEntryBlock()) methodEntryNode.put(sig, nodeId);
            
                  // Label: type header + separator + phi instructions + SSA instructions
                  StringBuilder label = new StringBuilder("BB").append(bb.getNumber());
                  if      (bb.isEntryBlock())  label.append("  [ENTRY]");
                  else if (bb.isExitBlock())   label.append("  [EXIT]");
                  else if (bb.isCatchBlock())  label.append("  [CATCH]");
                  label.append("\\l─────────────────\\l");
                  Iterator<SSAPhiInstruction> phis = bb.iteratePhis();
                  while (phis.hasNext()) label.append(dotLabel(phis.next().toString())).append("\\l");
                  for (SSAInstruction inst : bb) {
                    if (inst != null) label.append(dotLabel(inst.toString())).append("\\l");
                  }
            
                  // Fill color by block type
                  String fillColor = bb.isEntryBlock()  ? "#cce5ff"   // blue  = entry
                                   : bb.isExitBlock()   ? "#ccffcc"   // green = exit
                                   : bb.isCatchBlock()  ? "#ffdddd"   // red   = catch
                                   : "#f9f9f9";                       // grey  = normal
            
                  dot.append("    %s [label=\"%s\", style=filled, fillcolor=\"%s\"];\n"
                      .formatted(nodeId, label, fillColor));
                }
                dot.append("\n");
            
                // Control-flow edges within this method
                for (ISSABasicBlock bb : cfg) {
                  Iterator<ISSABasicBlock> successors = cfg.getSuccNodes(bb);
                  while (successors.hasNext())
                    dot.append("    %s -> %s;\n".formatted(
                        idPrefix + "bb" + bb.getNumber(),
                        idPrefix + "bb" + successors.next().getNumber()));
                }
                dot.append("  }\n\n");
              }
            
              // --- Cross-method call edges ---
              for (int m = 0; m < methods.size(); m++) {
                CGNode caller = methods.get(m);
                String callerSig = caller.getMethod().getSignature();
                String callerPrefix = "m" + m + "_";
            
                Iterator<CallSiteReference> callSites = caller.iterateCallSites();
                while (callSites.hasNext()) {
                  CallSiteReference site = callSites.next();
                  ISSABasicBlock[] callBBs = caller.getIR().getBasicBlocksForCall(site);
                  if (callBBs == null) continue;
            
                  for (CGNode target : cg.getPossibleTargets(caller, site)) {
                    String targetSig = target.getMethod().getSignature();
                    if (!methodIndex.containsKey(targetSig)) continue;
                    String targetEntry = methodEntryNode.get(targetSig);
                    if (targetEntry == null) continue;
            
                    // Distinguish recursive self-call from a regular cross-method call
                    boolean recursive = targetSig.equals(callerSig);
                    String edgeStyle = recursive ? "bold"      : "dashed";
                    String edgeColor = recursive ? "red"       : "blue";
                    String edgeLabel = recursive ? "recursive" : "calls";
            
                    for (ISSABasicBlock callBB : callBBs)
                      dot.append("  %s -> %s [style=%s, color=%s, label=\"%s\", constraint=false];\n"
                          .formatted(callerPrefix + "bb" + callBB.getNumber(),
                              targetEntry, edgeStyle, edgeColor, edgeLabel));
                  }
                }
              }
              dot.append("}\n");
            
              // --- Write DOT → PDF ---
              Files.writeString(Paths.get("out/fibo_cfg.dot"), dot);
              DotUtil.spawnDot("dot", "out/output.pdf", new File("out/fibo_cfg.dot"));
              System.out.println("CFG PDF -> out/output.pdf");
        }
        
        // Escape for DOT quoted strings: \ → \\, " → ', newline → \l (left-aligned line break)
        private static String dotLabel(String s) {
            return s.replace("\\", "\\\\").replace("\"", "'").replace("\n", "\\l");
        }
      ```

      </details>

---

## Reproducibility

### Result
⚠️ PARTIAL PASS

### Observations

I followed instructions with one correction — the `-mainClass` argument in step 6 uses `Lcom/example/Main` as a placeholder, which was replaced with the actual class path `Lcom/sirisuk/Main` to match the fibo sample input.

The build completed successfully and the call graph was constructed. However, the `=== Application Call Graph ===` section shown in expected output did not appear in my run. After checking the source, the extra printing code for the application call graph is present in this version but not in the cloned repository — it may not have been pushed yet.

### Difference from Reference

| Item | Reference | My Run |
|------|----------------|--------|
| Build result | ✅ SUCCESS | ✅ SUCCESS |
| `building call graph...done` | ✅ | ✅ |
| Call graph stats printed | ✅ | ✅ |
| `=== Application Call Graph ===` | ✅ Present | ❌ Not printed |
| Nodes | 8269 | 8280 |
| Edges | 41059 | 41097 |
| Methods | 5841 | 5847 |
| Bytecode Bytes | 411139 | 411731 |
| Time | 2618ms | 2782ms |

The call graph statistics differ slightly, which is expected across different machines and JDK minor versions. The application call graph section is missing because the extra printing code in this version has not been pushed to the shared repository yet. Once pushed, the output should match fully.

---
