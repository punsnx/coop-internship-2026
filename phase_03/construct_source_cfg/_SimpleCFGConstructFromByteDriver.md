# SimpleCFGConstructFromByteDriver

## Functionality

This driver maps WALA IR (SSA) instructions back to source lines via **bytecode indices** rather than AST source positions, then constructs a source-level, exception-pruned CFG. It builds IR from a compiled class using WALA's standard bytecode analysis scope, and generates the CFG for the `main` method only.

Unlike the source-based driver, source-line lookup here goes through two steps: `IBytecodeMethod.getBytecodeIndex()` maps an SSA instruction index to a bytecode offset, then `IMethod.getLineNumber()` maps that offset to a source line number.

## Input & Output

| | |
|---|---|
| **Input** | Path to a WALA analysis scope file, and path to the corresponding `Main.java` source file |
| **Output** | Source-level, exception-pruned CFG of `main` in `Main.java`, as `.dot` and `.pdf` files |

The driver produces three CFG variants:

1. **SSA CFG** — raw WALA IR instructions per basic block
2. **Source CFG** — basic blocks annotated with mapped source lines
3. **Source Pruned CFG** — source-annotated CFG with exception edges removed (via `ExceptionPrunedCFG`)

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

        // Set up scope
        AnalysisScope scope =
                AnalysisScopeReader.instance.readJavaScope(
                        scopeFile,
                        new File(Objects.requireNonNull(
                                SimpleCFGConstructFromByteDriver.class.getClassLoader()
                                        .getResource("Exclusions.txt")).getFile()),
                        SimpleCFGConstructFromByteDriver.class.getClassLoader());

        IClassHierarchy cha = ClassHierarchyFactory.make(scope);

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

        // =========================================================================
        // 1: Raw SSA CFG (Pure WALA IR Instructions from Bytecode)
        // =========================================================================
        String dotTextSSA = toDot(ir);
        Path dotFileSSA = Path.of("out-class/cfg-out/SSA_CFG_From_Byte.dot");
        Files.createDirectories(dotFileSSA.getParent());
        Files.writeString(dotFileSSA, dotTextSSA);

        ProcessBuilder pbSSA = new ProcessBuilder("dot", "-Tpdf", "out-class/cfg-out/SSA_CFG_From_Byte.dot", "-o", "out-class/cfg-out/SSA_CFG_From_Byte.pdf");
        pbSSA.inheritIO();
        Process processSSA = pbSSA.start();
        int exitCodeSSA = processSSA.waitFor();
        if (exitCodeSSA != 0) {
            System.out.println("dot command failed for raw SSA CFG with exit code " + exitCodeSSA);
        } else {
            System.out.println("Raw SSA CFG PDF written to out-class/cfg-out/SSA_CFG_From_Byte.pdf");
        }

        List<String> sourceLines = Files.readAllLines(Path.of(srcFilePath));

        // =========================================================================
        // 2: Raw / Unpruned Source CFG (Bytecode Mapping)
        // =========================================================================
        String dotTextSource = toDotSource(ir, sourceLines, method);
        Path dotFileSource = Path.of("out-class/cfg-out/Source_CFG_From_Byte.dot");
        Files.createDirectories(dotFileSource.getParent());
        Files.writeString(dotFileSource, dotTextSource);

        ProcessBuilder pbSource = new ProcessBuilder("dot", "-Tpdf", "out-class/cfg-out/Source_CFG_From_Byte.dot", "-o", "out-class/cfg-out/Source_CFG_From_Byte.pdf");
        pbSource.inheritIO();
        Process processSource = pbSource.start();
        int exitCode = processSource.waitFor();
        if (exitCode != 0) {
            System.out.println("dot command failed for raw source CFG with exit code " + exitCode);
        } else {
            System.out.println("Raw Source CFG PDF written to out-class/cfg-out/Source_CFG_From_Byte.pdf");
        }

        // =========================================================================
        // 3: Pruned Source CFG (Bytecode Mapping)
        // =========================================================================
        String dotTextPrunedSource = toDotPrunedSource(ir, sourceLines, method);
        Path dotFilePrunedSource = Path.of("out-class/cfg-out/Source_Pruned_CFG_From_Byte.dot");
        Files.createDirectories(dotFilePrunedSource.getParent());
        Files.writeString(dotFilePrunedSource, dotTextPrunedSource);

        ProcessBuilder pbPruned = new ProcessBuilder("dot", "-Tpdf", "out-class/cfg-out/Source_Pruned_CFG_From_Byte.dot", "-o", "out-class/cfg-out/Source_Pruned_CFG_From_Byte.pdf");
        pbPruned.inheritIO();
        Process processPruned = pbPruned.start();
        exitCode = processPruned.waitFor();
        if (exitCode != 0) {
            System.out.println("dot command failed for pruned source CFG with exit code " + exitCode);
        } else {
            System.out.println("Pruned Source CFG PDF written to out-class/cfg-out/Source_Pruned_CFG_From_Byte.pdf");
        }
    }

    // Helper function to create Dot file for Raw WALA SSA IR
    private static String toDot(IR ir) {
        SSACFG cfg = ir.getControlFlowGraph();
        StringBuilder sb = new StringBuilder();
        sb.append("digraph CFG {\n");
        sb.append("  node [shape=box, fontname=\"monospace\"];\n");

        for (ISSABasicBlock bb : cfg) {
            String label = blockLabel(bb, ir);
            sb.append("  BB").append(bb.getNumber())
                    .append(" [label=\"").append(label).append("\"];\n");
        }

        // Control B: Color-code Normal (Black Solid) vs Exceptional (Red Dashed) Edges
        for (ISSABasicBlock bb : cfg) {
            for (ISSABasicBlock succ : cfg.getNormalSuccessors(bb)) {
                sb.append("  BB").append(bb.getNumber())
                        .append(" -> BB").append(succ.getNumber()).append(";\n");
            }
            for (ISSABasicBlock succ : cfg.getExceptionalSuccessors(bb)) {
                sb.append("  BB").append(bb.getNumber())
                        .append(" -> BB").append(succ.getNumber())
                        .append(" [style=dashed, color=red];\n");
            }
        }

        sb.append("}\n");
        return sb.toString();
    }

    private static String blockLabel(ISSABasicBlock bb, IR ir) {
        StringBuilder sb = new StringBuilder();
        sb.append("BB").append(bb.getNumber());
        if (bb.isEntryBlock()) sb.append(" (ENTRY)");
        else if (bb.isExitBlock()) sb.append(" (EXIT)");
        sb.append("\\l");

        for (Iterator<SSAInstruction> it = bb.iterator(); it.hasNext(); ) {
            SSAInstruction inst = it.next();
            String text = inst.toString().replace("\"", "\\\"");
            sb.append(text).append("\\l");
        }
        return sb.toString();
    }

    // Helper function to create Dot file (For Raw/Unpruned Source CFG)
    private static String toDotSource(IR ir, List<String> sourceLines, IMethod method) {
        SSACFG cfg = ir.getControlFlowGraph();
        StringBuilder sb = new StringBuilder();
        sb.append("digraph CFG {\n");
        sb.append("  node [shape=box, fontname=\"monospace\"];\n");

        for (ISSABasicBlock bb : cfg) {
            String label = blockLabelSource(bb, sourceLines, method);
            sb.append("  BB").append(bb.getNumber())
                    .append(" [label=\"").append(label).append("\"];\n");
        }

        // Control B: Color-code Normal (Black Solid) vs Exceptional (Red Dashed) Edges
        for (ISSABasicBlock bb : cfg) {
            for (ISSABasicBlock succ : cfg.getNormalSuccessors(bb)) {
                sb.append("  BB").append(bb.getNumber())
                        .append(" -> BB").append(succ.getNumber()).append(";\n");
            }
            for (ISSABasicBlock succ : cfg.getExceptionalSuccessors(bb)) {
                sb.append("  BB").append(bb.getNumber())
                        .append(" -> BB").append(succ.getNumber())
                        .append(" [style=dashed, color=red];\n");
            }
        }

        sb.append("}\n");
        return sb.toString();
    }

    // Helper function to create Dot file (For Source Pruned CFG)
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
                if (isOnlyExceptional(cfg, bb, succ)) {
                    sb.append("  BB").append(bb.getNumber())
                            .append(" -> BB").append(succ.getNumber())
                            .append(" [style=dashed, color=red];\n");
                } else {
                    sb.append("  BB").append(bb.getNumber())
                            .append(" -> BB").append(succ.getNumber()).append(";\n");
                }
            }
        }

        sb.append("}\n");
        return sb.toString();
    }

    // Helper function to create instructions in basic block (For Source CFG)
    private static String blockLabelSource(ISSABasicBlock bb, List<String> sourceLines, IMethod method) {
        StringBuilder sb = new StringBuilder();
        sb.append("BB").append(bb.getNumber());

        // Control A: Mark ENTRY and EXIT basic blocks explicitly
        if (bb.isEntryBlock()) {
            sb.append(" (ENTRY)");
        } else if (bb.isExitBlock()) {
            sb.append(" (EXIT)");
        }
        sb.append("\\l");

        int first = bb.getFirstInstructionIndex();
        int last = bb.getLastInstructionIndex();
        String lastLine = null;
        int linesPrinted = 0;

        for (int i = first; i <= last; i++) {
            String text = getSourceLineText(method, i, sourceLines);
            if (text == null) continue;
            if (text.equals(lastLine)) continue;
            sb.append(text.replace("\"", "\\\"")).append("\\l");
            lastLine = text;
            linesPrinted++;
        }

        // Control A: Explicitly tag empty non-entry/exit blocks (synthetic/compiler code)
        boolean hasInstructions = first <= last;
        if (linesPrinted == 0 && !bb.isEntryBlock() && !bb.isExitBlock() && hasInstructions) {
            sb.append("  <compiler/synthetic code>\\l");
        }

        return sb.toString();
    }

    // Helper function to get source code line by mapping back from byte code
    private static String getSourceLineText(IMethod method, int instructionIndex, List<String> sourceLines) {
        try {
            IBytecodeMethod bytecodeMethod = (IBytecodeMethod) method;
            int bytecodeIndex = bytecodeMethod.getBytecodeIndex(instructionIndex);
            int line = method.getLineNumber(bytecodeIndex);
            if (line < 1 || line > sourceLines.size()) return null;
            // Control C: Prepend explicit line numbers "line n : "
            return "line " + line + " : " + sourceLines.get(line - 1).trim();
        } catch (Exception e) {
            return null;
        }
    }

    // Helper to check if an edge is strictly exceptional
    private static boolean isOnlyExceptional(SSACFG cfg, ISSABasicBlock src, ISSABasicBlock dst) {
        return !cfg.getNormalSuccessors(src).contains(dst) && cfg.getExceptionalSuccessors(src).contains(dst);
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