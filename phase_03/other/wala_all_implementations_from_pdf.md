# WALA PDF Tutorial Notes

## WALA Data Structures

- **Fixpoint Dataflow Solvers**
    - Dataflow System
        - KildallFramework
- **Graphs and Algorithms**
    - Graph Representations (e.g. numbered, labeled)
    - Generic Graph Operations
        - BFS
        - DFS
        - SCC
    - Graph Algorithms
        - Dominators
        - NumberedDominators
        - DominanceFrontiers
- **Bit Sets**
    - Bit Set Representations

## Intermediate Representation

- IR Factories
- IR Structure (as instruction lines and CFG)
    - Pi Nodes
        - SSAPiNodePolicy
- Instruction Type
    - SSAInstruction
- IR Utilities
    - DefUse
    - CDG
    - Type Inference
- IR Source
    - CAst Source Map
    - Bytecode Map
    - Local Names

## Scopes and Class Hierarchies

## Interprocedural Dataflow Analysis

- **Tabulation-Based Analysis** (Functional approach)
    - Overview
        - Tabulation Problem
            - TabulationDomain
            - IFlowFunctionMap
            - ISuperGraph
                - ICFGSupergraph
                - SDGSupergraph
            - Seeds
                - `TabulationProblem.initialSeeds()`

      Tabulation Problem → TabulationSolver → TabulationResult

- Partially Balanced Problems (e.g. ContextSensitiveReachingDefs, Slicer)
- Debugging Your Analysis
    - IFDSExplorer
- Deep Dive
    - Reaching Defs

## Call Graphs / Pointer Analysis

- **Call Graph Builder**
    - Overview
        - AnalysisOptions
            - Entrypoints
            - Reflection
        - Heap Model (controls abstraction of pointers and object instances)
            - InstanceKey: abstraction of an object
            - PointerKey: abstraction of a pointer
        - Context Selector (gives context to use for the callee method at some call site)
            - Context examples
                - The default context (Everywhere)
                - A call string (CallStringContext)
                - Receiver object (ReceiverInstanceContext)
            - ContextSelector examples
                - nCFAContextSelector: n-level call strings
                - ContainerContextSelector: object sensitivity for containers

      CallGraphBuilder → Call Graph / Pointer Analysis

- **Built-In Algorithms** (Grove and Chambers, TOPLAS 2001)
    - RTA
    - 0-CFA: context-insensitive, class-based heap
    - 0-1-CFA: context-insensitive, allocation-site-based heap
    - 0-1-Container-CFA: 0-1-CFA with object-sensitive containers
- Performance Tips
    - Use AnalysisScope exclusions
    - Analyze older libraries
    - Tune context-sensitivity policy
- Code Modeling
- Refinement-Based Points-To Analysis (DemandRefinementPointsTo)

## Slicing

- Overview
    - Statement (identifies a node in the System Dependence Graph, SDG)
        - NormalStatement
        - ParamCaller, ParamCallee
        - NormalReturnCaller, NormalReturnCallee
        - HeapParamCaller, HeapParamCallee, etc.
    - DataDependenceOptions
    - ControlDependenceOptions
    - CallGraph
    - PointerAnalysis

  `Slicer.compute{Forward|Backward}Slice()` → `Collection<Statement>`

- Thin Slicing
    - Just "top-level" data dependencies (see Sridharan-Fink-Bodik, PLDI '07)
    - For context-sensitive thin slicing, use `Slicer` with `DataDependenceOptions.NO_BASE_PTRS` and `ControlDependenceOptions.NONE`
    - For efficient context-insensitive thin slicing, use the `CISlicer` class
- Performance Tips
    - Some configurations do not scale to large programs
    - Run with minimum dependencies needed
    - Apply pointer analysis scalability tips

## Instrumenting Bytecodes With Shrike

## Front Ends / CAst

- WALA Front End
- WALA Bytecode Front End
- Shrike IR Construction
- Shrike Reader
- WALA Source Code Front End
- CAst IR Generation
- JavaScript Instruction Generation
- Instruction Generation
- Control Flow Graph Creation
- Source Position Mapping
- SSA Conversion