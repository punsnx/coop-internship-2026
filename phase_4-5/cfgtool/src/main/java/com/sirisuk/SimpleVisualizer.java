package com.sirisuk;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ssa.*;
import com.ibm.wala.types.ClassLoaderReference;

import java.util.Iterator;

public class SimpleVisualizer {

    public static void printCFG(CallGraph cg) {
        for (CGNode node : cg) {
            IR ir = node.getIR();
            if (!isAppMethod(node) || ir == null) continue;

            System.out.println("\n=== " + node.getMethod().getSignature() + " ===");

            SSACFG cfg = ir.getControlFlowGraph();
            for (ISSABasicBlock bb : cfg) {
                printBasicBlock(bb, cfg);
            }
        }
    }

    static void printBasicBlock(ISSABasicBlock bb, SSACFG cfg) {
        System.out.println("BB" + bb.getNumber() + getBlockType(bb));

        Iterator<SSAPhiInstruction> phis = bb.iteratePhis();
        while (phis.hasNext()) {
            System.out.println("    " + phis.next());
        }
        for (SSAInstruction inst : bb) {
            if (inst != null) System.out.println("    " + inst);
        }

        System.out.print("  -> ");
        Iterator<ISSABasicBlock> succs = cfg.getSuccNodes(bb);
        while (succs.hasNext()) {
            System.out.print("BB" + succs.next().getNumber() + " ");
        }
        System.out.println();
    }

    private static String getBlockType(ISSABasicBlock bb) {
        if (bb.isEntryBlock()) return "  [ENTRY]";
        if (bb.isExitBlock()) return "  [EXIT]";
        if (bb.isCatchBlock()) return "  [CATCH]";
        return "";
    }

    static boolean isAppMethod(CGNode node) {
        return node.getMethod().getDeclaringClass()
                .getClassLoader().getReference()
                .equals(ClassLoaderReference.Application);
    }

    public static void printAppCallgraph(CallGraph cg){
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
