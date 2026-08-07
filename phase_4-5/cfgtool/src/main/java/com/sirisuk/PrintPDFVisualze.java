package com.sirisuk;

import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.viz.DotUtil;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Renders NewGraphVisualizer's merged CFG as a real vertex/edge Graphviz diagram PDF
// (one box per merged basic-block group, arrows for its edges - matching the look of
// program/CfgDotRenderer's decompiled_output.pdf), by parsing NewGraphVisualizer's
// console text back into groups and edges. NewGraphVisualizer exposes no structured
// API (and is never touched here) - only its printed text, so this class depends on
// nothing but its column-based printing convention: an unindented line starts a
// group's label, a 4-space-indented line is body content, a 2-space "-> "-prefixed
// line is that group's outgoing edges.
public class PrintPDFVisualze {

    private static final Pattern METHOD_HEADER = Pattern.compile("^=== (.+) ===$");

    // One successor label in an edge line, with an optional leading "T:"/"F:" tag
    // (NewGraphVisualizer marks which side of a conditional branch an edge is).
    // The label itself may be a multi-block "BB3+BB4+BB5" chain plus an optional
    // "  [ROLE]" suffix - this lets one "  -> T:label1 F:label2 " edge line be split
    // back into its individual tagged successors.
    private static final Pattern EDGE_TOKEN =
            Pattern.compile("(?:(T|F):)?(BB\\d+(?:\\+BB\\d+)*(?:  \\[\\w+])?)");

    private record Successor(String label, String tag) {}

    private record Group(String label, List<String> body, List<Successor> successors) {}

    private record Method(String signature, List<Group> groups) {}

    public static void printPDF(CallGraph cg, String sourceDir) throws IOException, WalaException {
        String captured = captureConsoleOutput(cg, sourceDir);
        List<Method> methods = parse(captured);

        new File("out").mkdirs();
        DotUtil.setOutputType(DotUtil.DotOutputType.PDF);

        StringBuilder dot = new StringBuilder();
        dot.append("digraph \"MergedCFG\" {\n  rankdir=TB;\n  node [shape=box, fontname=\"Courier\", fontsize=9];\n\n");
        for (int m = 0; m < methods.size(); m++) {
            appendMethodCluster(dot, m, methods.get(m));
        }
        dot.append("}\n");

        Files.writeString(Paths.get("out/newgraph_cfg.dot"), dot.toString());
        DotUtil.spawnDot("dot", "out/newgraph_cfg.pdf", new File("out/newgraph_cfg.dot"));
        System.out.println("Merged CFG PDF -> out/newgraph_cfg.pdf");
    }

    // Redirects System.out to a buffer for the duration of the call, so the only visible
    // effect of printPDF() is the PDF (+ the one confirmation line above) - not a second
    // console dump. Restored in finally even if printMergedCFG throws.
    private static String captureConsoleOutput(CallGraph cg, String sourceDir) {
        PrintStream original = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(buffer));
            NewGraphVisualizer.printMergedCFG(cg, sourceDir);
        } finally {
            System.setOut(original);
        }
        return buffer.toString();
    }

    // Splits the captured text into one Method (signature + ordered groups) per
    // "=== signature ===" section, using only indentation to tell a group's label,
    // body, and edge line apart.
    private static List<Method> parse(String captured) {
        List<Method> methods = new ArrayList<>();
        List<Group> currentGroups = null;
        String signature = null;
        String label = null;
        List<String> body = null;

        for (String line : captured.split("\n", -1)) {
            if (line.isBlank()) continue;

            Matcher header = METHOD_HEADER.matcher(line.strip());
            if (header.matches()) {
                if (signature != null) methods.add(new Method(signature, currentGroups));
                signature = header.group(1);
                currentGroups = new ArrayList<>();
                label = null;
                body = null;
                continue;
            }
            if (currentGroups == null) continue; // stray output before any method header

            if (line.startsWith("  -> ")) {
                if (label == null) continue; // no open group to close - ignore
                List<Successor> successors = new ArrayList<>();
                Matcher tok = EDGE_TOKEN.matcher(line);
                while (tok.find()) successors.add(new Successor(tok.group(2), tok.group(1)));
                currentGroups.add(new Group(label, body, successors));
                label = null;
                body = null;
            } else if (!line.startsWith(" ")) {
                label = line.strip();
                body = new ArrayList<>();
            } else if (body != null) {
                body.add(line.strip());
            }
        }
        if (signature != null) methods.add(new Method(signature, currentGroups));
        return methods;
    }

    // One DOT subgraph cluster per method: one node per parsed group, edges from each
    // group's successor labels resolved against that same method's id map (so labels
    // that repeat across methods, e.g. "BB0  [ENTRY]", never cross-connect).
    private static void appendMethodCluster(StringBuilder dot, int m, Method method) {
        dot.append("  subgraph cluster_%d {\n    label=\"%s\";\n    style=dashed; color=grey; fontsize=11; fontname=\"Helvetica-Bold\";\n\n"
                .formatted(m, dotEscape(method.signature())));

        Map<String, String> idOf = new LinkedHashMap<>();
        int n = 0;
        for (Group g : method.groups()) idOf.put(g.label(), "m" + m + "_n" + (n++));

        for (Group g : method.groups()) {
            dot.append("    %s [label=\"%s\", style=filled, fillcolor=\"%s\"];\n"
                    .formatted(idOf.get(g.label()), buildNodeLabel(g), fillColorFor(g.label())));
        }
        dot.append("\n");

        for (Group g : method.groups()) {
            String from = idOf.get(g.label());
            for (Successor s : g.successors()) {
                String to = idOf.get(s.label());
                if (to == null) continue;
                // Color the edge line itself (not just its label's font) so the true/false
                // path stays traceable end-to-end even where it crosses or converges near
                // another edge (e.g. a loop's back-edge into its own condition node);
                // decorate=true draws a connector from the label to its own edge path,
                // the standard Graphviz fix for "which edge does this label belong to"
                // ambiguity when edges pass close together.
                String edgeLabel = s.tag() != null
                        ? " [label=\"%s\", fontcolor=\"%s\", color=\"%s\", decorate=true]"
                            .formatted(s.tag(), tagColor(s.tag()), tagColor(s.tag()))
                        : "";
                dot.append("    %s -> %s%s;\n".formatted(from, to, edgeLabel));
            }
        }
        dot.append("  }\n\n");
    }

    private static String tagColor(String tag) {
        return tag.equals("T") ? "#0a7d20" : "#a30000";
    }

    // Node label: the group's own header line, then a separator, then its body lines -
    // same "\l" left-justified line-break convention CfgDotRenderer.buildBlockLabel uses.
    private static String buildNodeLabel(Group g) {
        StringBuilder label = new StringBuilder(dotEscape(g.label())).append("\\l");
        if (!g.body().isEmpty()) {
            label.append("─────────────────\\l");
            for (String line : g.body()) label.append(dotEscape(line)).append("\\l");
        }
        return label.toString();
    }

    // Fill color by role: ENTRY/EXIT/CATCH match vis-reading-instruction.md's existing
    // legend; any control-keyword role (IF/ELSEIF/ELSE/WHILE/FOR incl. its FOR_INIT/
    // FOR_COND/FOR_STEP sub-roles/DO) gets the same yellow "hub" color the user's own
    // NFA diagram used for control-flow states. "[FOR" (no closing bracket) matches
    // "[FOR]" and any "[FOR_*]" sub-role alike.
    private static String fillColorFor(String label) {
        if (label.contains("[ENTRY]")) return "#cce5ff";
        if (label.contains("[EXIT]")) return "#ccffcc";
        if (label.contains("[CATCH]")) return "#ffdddd";
        if (label.contains("[IF]") || label.contains("[ELSEIF]") || label.contains("[ELSE]")
                || label.contains("[WHILE]") || label.contains("[FOR") || label.contains("[DO]")) {
            return "#ffe17a";
        }
        return "#f9f9f9";
    }

    // Escape for DOT quoted strings: \ -> \\, " -> ', newline -> \l (left-aligned line break)
    private static String dotEscape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "'").replace("\n", "\\l");
    }
}
