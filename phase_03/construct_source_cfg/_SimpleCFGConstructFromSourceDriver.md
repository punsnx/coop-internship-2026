# SimpleCFGConstructFromSourceDriver

## Functionality

This driver reconstructs a source-level CFG by mapping WALA SSA instructions back to their originating source lines. It builds IR directly from Java source (Main.java, via WALA's AST-based frontend) and analyzes only the main method.
## Input & Output

| | |
|---|---|
| **Input** | Path to a `Main.java` source file |
| **Output** | Source-level CFG of `main` in `Main.java`, as `.dot` and `.pdf` files |

The driver produces three CFG variants:

1. **SSA CFG** — raw WALA IR instructions per basic block
2. **Source CFG** — basic blocks annotated with mapped source lines
3. **Source Pruned CFG** — source-annotated CFG with exception edges removed (via `ExceptionPrunedCFG`)

## Program Code

`SimpleCFGConstructFromSourceDriver.java`

```java
package com.ibm.wala.examples.drivers;

import com.ibm.wala.cast.ir.ssa.AstIRFactory;
import com.ibm.wala.cast.java.ipa.callgraph.JavaSourceAnalysisScope;
import com.ibm.wala.cast.java.translator.jdt.ecj.ECJClassLoaderFactory;
import com.ibm.wala.cast.loader.AstMethod;
import com.ibm.wala.cast.tree.CAstSourcePositionMap;
import com.ibm.wala.classLoader.ClassLoaderFactory;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.SourceFileModule;
import com.ibm.wala.core.java11.JrtModule;
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

public class SimpleCFGConstructFromSourceDriver {

    protected static ClassLoaderFactory getLoaderFactory(AnalysisScope scope){
        return new ECJClassLoaderFactory(scope.getExclusions());
    }

    public static void main(String[] args) throws IOException, ClassHierarchyException, InterruptedException {
        String mainFilePath = args[0]; // e.g. "testdata/com/cfgtest/Main.java"

        // 1: Setup IR
        AnalysisScope scope = new JavaSourceAnalysisScope();
        scope.addToScope(ClassLoaderReference.Primordial, new JrtModule("java.base"));

        File srcFile = new File(mainFilePath);
        scope.addToScope(JavaSourceAnalysisScope.SOURCE, new SourceFileModule(srcFile, srcFile.getName(), null));

        IClassHierarchy cha = ClassHierarchyFactory.make(scope, getLoaderFactory(scope));

        for (IClass c : cha) {
            if (c.getClassLoader().getReference().equals(JavaSourceAnalysisScope.SOURCE)) {
                System.out.println(c.getName());
            }
        }

        IAnalysisCacheView cache = new AnalysisCacheImpl(AstIRFactory.makeDefaultFactory());
        TypeReference tRef = TypeReference.findOrCreate(JavaSourceAnalysisScope.SOURCE, "Lcom/cfgtest/Main");
        IClass klass = cha.lookupClass(tRef);
        IMethod method = klass.getMethod(Selector.make("main([Ljava/lang/String;)V"));
        IR ir = cache.getIR(method, Everywhere.EVERYWHERE);
        System.out.println(ir);


        // 2: Visualized SSA CFG
        String dotText = toDot(ir);
        Path dotFile = Path.of("out-class/cfg-out/SSA_CFG.dot");
        Files.createDirectories(dotFile.getParent());
        Files.writeString(dotFile, dotText);

        ProcessBuilder pb = new ProcessBuilder("dot", "-Tpdf", "out-class/cfg-out/SSA_CFG.dot", "-o", "out-class/cfg-out/SSA_CFG.pdf");
        pb.inheritIO();
        Process process = pb.start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            System.out.println("dot command failed with exit code " + exitCode);
        } else {
            System.out.println("SSA CFG PDF written to out-class/cfg-out/SSA_CFG.pdf");
        }

        // 3: Visualized Source CFG
        List<String> sourceLines = Files.readAllLines(srcFile.toPath());
        String dotTextSource = toDotSource(ir, sourceLines, method);
        Path dotFileSource = Path.of("out-class/cfg-out/Source_CFG.dot");
        Files.createDirectories(dotFileSource.getParent());
        Files.writeString(dotFileSource, dotTextSource);

        ProcessBuilder pbs = new ProcessBuilder("dot", "-Tpdf", "out-class/cfg-out/Source_CFG.dot", "-o", "out-class/cfg-out/Source_CFG.pdf");
        pbs.inheritIO();
        Process process1 = pbs.start();
        exitCode = process1.waitFor();
        if (exitCode != 0) {
            System.out.println("dot command failed with exit code " + exitCode);
        } else {
            System.out.println("Source CFG PDF written to out-class/cfg-out/Source_CFG.pdf");
        }

        // 4: Visualized Source Pruned CFG
        String dotTextPrunedSource = toDotPrunedSource(ir, sourceLines, method);
        Path dotFilePrunedSource = Path.of("out-class/cfg-out/Source_Pruned_CFG.dot");
        Files.createDirectories(dotFilePrunedSource.getParent());
        Files.writeString(dotFilePrunedSource, dotTextPrunedSource);

        ProcessBuilder pbsp = new ProcessBuilder("dot", "-Tpdf", "out-class/cfg-out/Source_Pruned_CFG.dot", "-o", "out-class/cfg-out/Source_Pruned_CFG.pdf");
        pbsp.inheritIO();
        Process process2 = pbsp.start();
        exitCode = process2.waitFor();
        if (exitCode != 0) {
            System.out.println("dot command failed with exit code " + exitCode);
        } else {
            System.out.println("Source CFG PDF written to out-class/cfg-out/Source_Pruned_CFG.pdf");
        }
    }

    // Helper function to create Dot file (For SSA CFG)
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

        // Control B: Color-code Normal vs Exceptional Edges
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

    // Helper function to create Dot file (For Source CFG)
    private static String toDotSource(IR ir, List<String> sourceLines, IMethod method) {
        SSACFG cfg = ir.getControlFlowGraph();
        StringBuilder sb = new StringBuilder();
        sb.append("digraph CFG {\n");
        sb.append("  node [shape=box, fontname=\"monospace\"];\n");

        for (ISSABasicBlock bb : cfg) {
            String label = blockLabelSource(bb, ir, sourceLines, method);
            sb.append("  BB").append(bb.getNumber())
                    .append(" [label=\"").append(label).append("\"];\n");
        }

        // Control B: Color-code Normal vs Exceptional Edges
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
            String label = blockLabelSource(bb, ir, sourceLines, method);
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
    private static String blockLabelSource(ISSABasicBlock bb, IR ir, List<String> sourceLines, IMethod method) {
        AstMethod astMethod = (AstMethod) method;
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
            String text = getSourceLineText(astMethod, i, sourceLines);
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

    // Helper function to get source code line
    private static String getSourceLineText(AstMethod astMethod, int instructionIndex, List<String> sourceLines) {
        try {
            CAstSourcePositionMap.Position pos = astMethod.getSourcePosition(instructionIndex);
            if (pos == null) return null;
            int line = pos.getFirstLine();
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
./gradlew run -PmainClass=com.ibm.wala.examples.drivers.SimpleCFGConstructFromSourceDriver --args="path/to/Main.java"
```

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
  BB3 [label="BB3\lif(z >= 11){\l"];
  BB4 [label="BB4\lSystem.out.println(\"I'm so small and cute\");\l"];
  BB5 [label="BB5\lx = 3;\ly = 4;\lint sum = 0;\lfor(int i = 0; i < 5; i++){\l"];
  BB6 [label="BB6\lfor(int i = 0; i < 5; i++){\l"];
  BB7 [label="BB7\lsum = sum + i;\lfor(int i = 0; i < 5; i++){\l"];
  BB8 [label="BB8\lSystem.out.println(sum);\l"];
  BB9 [label="BB9\l"];
  BB0 -> BB1;
  BB1 -> BB4;
  BB1 -> BB2;
  BB2 -> BB3;
  BB2 -> BB9;
  BB3 -> BB5;
  BB4 -> BB5;
  BB4 -> BB9;
  BB5 -> BB6;
  BB6 -> BB8;
  BB6 -> BB7;
  BB7 -> BB6;
  BB8 -> BB9;
}
```

`Source_CFG.pdf`

![Source-level CFG](phase_03/other/assets/Source_CFG.png)