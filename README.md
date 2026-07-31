# SE1 Internship 2026 — Static Analysis with WALA

Guest researcher internship at the Chair of Software Engineering I, University of
Passau, carried out as part of an inaugural research exchange between the
Department of Computer Science, Kasetsart University and the University of Passau.

**Author:** Prawit Pongpipat

**Supervisors:** Prof. Dr.-Ing. Christian Hammer, Dr. Mohammad Rezaalipour (day-to-day)

**Duration:** 15 May – 11 August 2026

## Overview

This repository contains the deliverables of a five-phase internship built around
[WALA (T.J. Watson Libraries for Analysis)](https://github.com/wala/WALA), an
open-source framework for the static analysis of Java. The first three phases
build familiarity with WALA; the final two develop a static analysis tool of my
own — a **dead-store detector** for Java.

A dead store is a variable, field, or parameter that is assigned a value which is
never used. The detector finds them and reports each with its source location.

## Contents

| Phase | Folder | Description |
|-------|--------|-------------|
| 1 | `phase_01/` | Running and documenting WALA's example analyses; JDK compatibility and reproducibility testing |
| 2 | `phase_02/` | A survey of static analysis techniques and program representation graphs |
| 3 | `phase_03/` | Mapping those techniques and graphs to their implementations in WALA |
| 4 | `phase_04/` | The dead-store detector (local variables), with an interprocedural cascade |
| 5 | `phase_05/` | Extended detector: unused fields, parameters, and nested classes |

Each phase folder contains its own deliverables and, where applicable, a README,
test cases, and expected outputs.

## The Dead-Store Detector (Phases 4–5)

The tool compiles a Java source file, builds WALA's SSA intermediate
representation, and uses def-use information to find values that are assigned but
never read. It reports three kinds of dead store — unused local variables, unused
class fields, and unused method parameters — across nested classes and multiple
methods.

See `phase_05/` for the latest version, its usage instructions, test cases, and a
documented account of its limitations (`LIMITATIONS.md`).

## Requirements

- JDK 21 (a JDK, not a JRE — the tool invokes `javac` internally)
- Gradle (via the included wrapper)

## License

The dead-store detector is released under the MIT License; see the `LICENSE` file
in the relevant phase folder.