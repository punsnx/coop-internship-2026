package com.sirisuk;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.core.util.config.AnalysisScopeReader;
import com.ibm.wala.core.util.strings.StringStuff;
import com.ibm.wala.core.util.warnings.Warnings;
import com.ibm.wala.examples.drivers.ScopeFileCallGraph;
import com.ibm.wala.ipa.callgraph.*;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.config.PatternsFilter;
import com.ibm.wala.util.io.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;


public class Main {

    public static String scopeFile;
    public static String mainClass;
    public static String entryClass;
    public static String sourceDir;


    public static AnalysisScope readScope(String[] args) throws IOException {
        Properties p = CommandLine.parse(args);
        scopeFile = p.getProperty("scopeFile");
        entryClass = p.getProperty("entryClass");
        mainClass = p.getProperty("mainClass");
        sourceDir = p.getProperty("sourceDir");
        if (mainClass != null && entryClass != null) {
            throw new IllegalArgumentException("only specify one of mainClass or entryClass");
        }
        AnalysisScope scope = AnalysisScopeReader.instance.readJavaScope(
                scopeFile, null, ScopeFileCallGraph.class.getClassLoader()
        );
        return scope;
    }

    public static void setExclusion(AnalysisScope scope) throws IOException {
        List<String> patterns = Files.readAllLines(Paths.get("src/main/resources/Exclusions.txt"));
        scope.setExclusions(new PatternsFilter(patterns.stream()));
        // set exclusions.  we use these exclusions as standard for handling JDK 8
//    ExampleUtil.addDefaultExclusions(scope);
    }
    private static Iterable<Entrypoint> makePublicEntrypoints(
            IClassHierarchy cha, String entryClass) {
        Collection<Entrypoint> result = new ArrayList<>();
        IClass klass = cha.lookupClass(
                TypeReference.findOrCreate(
                        ClassLoaderReference.Application,
                        StringStuff.deployment2CanonicalTypeString(entryClass))
        );
        for (IMethod m : klass.getDeclaredMethods()) {
            if (m.isPublic()) {
                result.add(new DefaultEntrypoint(m, cha));
            }
        }
        return result;
    }
    public static AnalysisOptions setAnalysisOptions(IClassHierarchy cha) {
        AnalysisOptions options = new AnalysisOptions();
        Iterable<Entrypoint> entrypoints =
                entryClass != null
                        ? makePublicEntrypoints(cha, entryClass)
                        : Util.makeMainEntrypoints(cha, mainClass);
        options.setEntrypoints(entrypoints);
        // For a CHA call graph
        //    CHACallGraph CG = new CHACallGraph(cha);
        //    CG.init(entrypoints);
        // For other call graphs
        // you can dial down reflection handling if you like
        //    options.setReflectionOptions(ReflectionOptions.NONE);
        return options;
    }

    public static CallGraph buildCallGraph(AnalysisOptions options, AnalysisCache cache, IClassHierarchy cha) throws CallGraphBuilderCancelException {
        // other builders can be constructed with different Util methods
        CallGraphBuilder<InstanceKey> builder =
                Util.makeZeroOneContainerCFABuilder(options, cache, cha);
        //    CallGraphBuilder<InstanceKey> builder  = Util.makeZeroCFABuilder(Language.JAVA, options,
        // cache, cha);
        //    CallGraphBuilder builder = Util.makeNCFABuilder(2, options, cache, cha, scope);
        //    CallGraphBuilder builder = Util.makeVanillaNCFABuilder(2, options, cache, cha, scope);
        //    CallGraphBuilder builder = Util.makeVanillaNCFABuilder(2, options, cache, cha, scope);
        System.out.println("building call graph...");
        return builder.makeCallGraph(options, null);
    }


    public static void main(String[] args) throws IOException, CallGraphBuilderCancelException, WalaException {
        long start = System.currentTimeMillis();

        AnalysisScope scope = readScope(args);
        setExclusion(scope);
        IClassHierarchy cha = ClassHierarchyFactory.make(scope);

        System.out.println(cha.getNumberOfClasses() + " classes");
//        System.out.println(Warnings.asString());
        Warnings.clear();

        AnalysisOptions options = setAnalysisOptions(cha);
        AnalysisCache cache = new AnalysisCacheImpl();
        CallGraph cg = buildCallGraph(options, cache, cha);

//        SimpleVisualizer.printAppCallgraph(cg);
//        SimpleVisualizer.printCFG(cg);
        NewGraphVisualizer.printMergedCFG(cg, sourceDir);
        PrintPDFVisualze.printPDF(cg, sourceDir);
//        GraphVisualizer.printMergedCFG(cg, sourceDir);
        long end = System.currentTimeMillis();
        System.out.println("done");
        System.out.println("took " + (end - start) + "ms");
        System.out.println(CallGraphStats.getStats(cg));

    }
}
