# intern26_se1_Sirisuk

Internship project at the Faculty of Computer Science & Mathematics, University of Passau (May–Aug 2026), focused on software analysis and security research. Java source code is parsed into AST/IR representations using the **WALA** framework, then analyzed to detect unsafe code patterns and vulnerabilities. Work is organized by phase, progressing from IR/call-graph exploration to a standalone CFG extraction and visualization tool.
https://github.com/wala/wala

## Project Structure

```
work-repository/
├── README.md
├── LICENSE
├── branch.md                                  # git branching/remote workflow notes
├── Sirisuk-Tharntham_Internship-Final-Presentation.pdf
├── phase_01/                                   # AST construction, call graphs, type hierarchy, points-to analysis
│   ├── _ConstructAllIRs.md
│   ├── _SourceDirCallGraph.md / _ScopeFileCallGraph.md
│   ├── _PDFTypeHierarchy.md / _PrintTypeHierarchy.md
│   ├── _CSReachingDefsDriver.md / _DemandPointsToDriver.md
│   ├── p01_questions.md
│   └── other/                                  # JDK 8/11/17/25 test notes, WALA manual
├── phase_02/                                   # analysis techniques (phase 2)
│   ├── techniques.md
│   └── other/
├── phase_03/                                   # analysis techniques (phase 3)
│   ├── techniques.md
│   └── other/
└── phase_4-5/
    └── cfgtool/                                 # Gradle/Java CFG extraction & visualization tool
        ├── src/main/java/com/sirisuk/           # Main, SimpleDecompile, SimpleVisualizer, NewGraphVisualizer, PrintPDFVisualze
        ├── src/main/resources/                  # exclusions, wala.properties, test JS files
        ├── BB_split_approach.md / DecompileCFG.md / manual.md / visualize-manual.md / vis-reading-instruction.md
        └── build.gradle.kts, gradlew, scope.txt  (build/, out/, .gradle/, .idea/ — build artifacts, omitted)
```
