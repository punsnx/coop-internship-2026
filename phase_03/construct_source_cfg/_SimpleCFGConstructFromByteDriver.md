# SimpleCFGConstructFromByteDriver

## Functionality

This driver maps WALA IR (SSA) instructions back to source lines via **bytecode indices** rather than AST source positions, then constructs a source-level, exception-pruned CFG. It builds IR from a compiled class using WALA's standard bytecode analysis scope, and generates the CFG for the `main` method only.

Unlike the source-based driver, source-line lookup here goes through two steps: `IBytecodeMethod.getBytecodeIndex()` maps an SSA instruction index to a bytecode offset, then `IMethod.getLineNumber()` maps that offset to a source line number.

## Input & Output

| | |
|---|---|
| **Input** | Path to a WALA analysis scope file, and path to the corresponding `Main.java` source file |
| **Output** | Source-level, exception-pruned CFG of `main` in `Main.java`, as `.dot` and `.pdf` files |

This driver produces only the **Source Pruned CFG** (exception edges removed via `ExceptionPrunedCFG`) — it does not emit the raw SSA CFG or the unpruned source CFG.

## Program Code

`SimpleCFGConstructFromByteDriver.java`

```java
package com.ibm.wala.examples.drivers;

import com.ibm.wala.classLoader.IBytecodeMethod;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.core.util.config.AnalysisScopeReader;
import com.ibm.wala.ipa.callgraph.AnalysisCacheImpl;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.IAnalysisCacheView;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.ipa.cfg.ExceptionPrunedCFG;
import com.ibm.wala.ipa.cfg.PrunedCFG;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSACFG;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.Selector;
import com.ibm.wala.types.TypeReference;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

public class SimpleCFGConstructFromByteDriver {

    public static void main(String[] args) throws IOException, ClassHierarchyException, InterruptedException {
        String scopeFile = args[0];
        String srcFilePath = args[1]; // e.g. "testdata/com/cfgtest/Main.java"

        // --- 1. Set up scope and build IR ---
        AnalysisScope scope =
                AnalysisScopeReader.instance.readJavaScope(
                        scopeFile,
                        new File(Objects.requireNonNull(
                                SimpleCFGConstructFromByteDriver.class.getClassLoader()
                                        .getResource("Exclusions.txt")).getFile()),
                        SimpleCFGConstructFromByteDriver.class.getClassLoader());

        IClassHierarchy cha = ClassHierarchyFactory.make(scope);

        // Debug: list classes in the application scope
        for (IClass c : cha) {
            if (c.getClassLoader().getReference().equals(ClassLoaderReference.Application)) {
                System.out.println(c.getName());
            }
        }

        IAnalysisCacheView cache = new AnalysisCacheImpl();
        TypeReference tRef = TypeReference.findOrCreate(ClassLoaderReference.Application, "Lcom/cfgtest/Main");
        IClass klass = cha.lookupClass(tRef);
        if (klass == null) {
            throw new IllegalStateException("Could not find class for " + tRef);
        }

        IMethod method = klass.getMethod(Selector.make("main([Ljava/lang/String;)V"));
        IR ir = cache.getIR(method, Everywhere.EVERYWHERE);
        System.out.println(ir);

        // --- 2. Visualize Source Pruned CFG ---
        List<String> sourceLines = Files.readAllLines(Path.of(srcFilePath));
        String dotTextPrunedSource = toDotPrunedSource(ir, sourceLines, method);
        Path dotFilePrunedSource = Path.of("out-class/cfg-out/Source_Pruned_CFG_From_Byte.dot");
        Files.createDirectories(dotFilePrunedSource.getParent());
        Files.writeString(dotFilePrunedSource, dotTextPrunedSource);

        ProcessBuilder pb = new ProcessBuilder("dot", "-Tpdf",
                "out-class/cfg-out/Source_Pruned_CFG_From_Byte.dot",
                "-o", "out-class/cfg-out/Source_Pruned_CFG_From_Byte.pdf");
        pb.inheritIO();
        Process process = pb.start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            System.out.println("dot command failed with exit code " + exitCode);
        } else {
            System.out.println("Source CFG PDF written to out-class/cfg-out/Source_Pruned_CFG_From_Byte.pdf");
        }
    }

    // ---------- Source Pruned CFG ----------

    private static String toDotPrunedSource(IR ir, List<String> sourceLines, IMethod method) {
        SSACFG cfg = ir.getControlFlowGraph();
        PrunedCFG<SSAInstruction, ISSABasicBlock> prunedCfg = ExceptionPrunedCFG.make(cfg);
        StringBuilder sb = new StringBuilder();
        sb.append("digraph CFG {\n");
        sb.append("  node [shape=box, fontname=\"monospace\"];\n");

        for (ISSABasicBlock bb : prunedCfg) {
            String label = blockLabelSource(bb, sourceLines, method);
            sb.append("  BB").append(bb.getNumber())
                    .append(" [label=\"").append(label).append("\"];\n");
        }

        for (ISSABasicBlock bb : prunedCfg) {
            for (Iterator<ISSABasicBlock> succs = prunedCfg.getSuccNodes(bb); succs.hasNext(); ) {
                ISSABasicBlock succ = succs.next();
                sb.append("  BB").append(bb.getNumber())
                        .append(" -> BB").append(succ.getNumber()).append(";\n");
            }
        }

        sb.append("}\n");
        return sb.toString();
    }

    /** Maps each instruction in a basic block to its source line, deduplicating consecutive repeats. */
    private static String blockLabelSource(ISSABasicBlock bb, List<String> sourceLines, IMethod method) {
        StringBuilder sb = new StringBuilder();
        sb.append("BB").append(bb.getNumber()).append("\\l");

        int first = bb.getFirstInstructionIndex();
        int last = bb.getLastInstructionIndex();
        String lastLine = null; // dedupe: multiple SSA instructions often share one source line

        for (int i = first; i <= last; i++) {
            String text = getSourceLineText(method, i, sourceLines);
            if (text == null) continue;          // no line mapping for this instruction
            if (text.equals(lastLine)) continue;  // duplicate of previous line
            sb.append(text.replace("\"", "\\\"")).append("\\l");
            lastLine = text;
        }
        return sb.toString();
    }

    /** Maps an SSA instruction index back to source text via bytecode index -> line number. */
    private static String getSourceLineText(IMethod method, int instructionIndex, List<String> sourceLines) {
        try {
            IBytecodeMethod bytecodeMethod = (IBytecodeMethod) method;
            int bytecodeIndex = bytecodeMethod.getBytecodeIndex(instructionIndex);
            int line = method.getLineNumber(bytecodeIndex);
            if (line < 1 || line > sourceLines.size()) return null;
            return sourceLines.get(line - 1).trim();
        } catch (Exception e) {
            return null; // no line mapping available for this instruction
        }
    }
}
```

## Run Instructions

```bash
./gradlew run -PmainClass=com.ibm.wala.examples.drivers.SimpleCFGConstructFromByteDriver --args="path/to/scope.txt path/to/Main.java"
```

- `args[0]` — path to a WALA analysis scope file (defines which classes/jars are in the application scope)
- `args[1]` — path to the `Main.java` source file, used only for line-text lookup once bytecode indices are mapped to line numbers

### Notes / Limitations

- Requires an `Exclusions.txt` resource to be present on the classpath; the driver loads it via `getClassLoader().getResource("Exclusions.txt")` and will throw an `NPE` (via `Objects.requireNonNull`) if it's missing.
- The target class is hardcoded to `Lcom/cfgtest/Main`; retargeting requires editing the `TypeReference.findOrCreate` call.
- Line mapping depends on debug info being present in the compiled class (i.e., compiled with `-g` or default `javac` line-number tables). If absent, `getLineNumber()` may return `-1`, causing `getSourceLineText` to return `null` for that instruction.

### Sample Input

`Main.java`

```java
public class Main {
    public static void main(String[] args){
        int x = 2;
        int y = 10;
        int z = x + y;
        if(z >= 11){
            System.out.println("I'm bigger and stronger");
        }
        else
            System.out.println("I'm so small and cute");
        x = 3;
        y = 4;

        int sum = 0;
        for(int i = 0; i < 5; i++){
            sum = sum + i;
        }
        System.out.println(sum);
    }
}
```

### Example Output

`Source_CFG.dot`

```text
digraph CFG {
  node [shape=box, fontname="monospace"];
  BB0 [label="BB0\l"];
  BB1 [label="BB1\lint x = 2;\lint y = 10;\lint z = x + y;\lif(z >= 11){\l"];
  BB2 [label="BB2\lSystem.out.println(\"I'm bigger and stronger\");\l"];
  BB3 [label="BB3\lSystem.out.println(\"I'm bigger and stronger\");\l"];
  BB4 [label="BB4\lSystem.out.println(\"I'm so small and cute\");\l"];
  BB5 [label="BB5\lx = 3;\ly = 4;\lint sum = 0;\lfor(int i = 0; i < 5; i++){\l"];
  BB6 [label="BB6\lfor(int i = 0; i < 5; i++){\l"];
  BB7 [label="BB7\lsum = sum + i;\lfor(int i = 0; i < 5; i++){\l"];
  BB8 [label="BB8\lSystem.out.println(sum);\l"];
  BB9 [label="BB9\l}\l"];
  BB10 [label="BB10\l"];
  BB0 -> BB1;
  BB1 -> BB4;
  BB1 -> BB2;
  BB2 -> BB3;
  BB3 -> BB5;
  BB4 -> BB5;
  BB5 -> BB6;
  BB6 -> BB8;
  BB6 -> BB7;
  BB7 -> BB6;
  BB8 -> BB9;
  BB9 -> BB10;
}
```

`Source_CFG.pdf`

![Source-level CFG](phase_03/other/assets/Source_Pruned_CFG_From_Byte.png)