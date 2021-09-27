package org.clyze.source.irfitter;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentSkipListSet;
import org.apache.commons.cli.*;
import org.clyze.source.irfitter.ir.IRState;
import org.clyze.source.irfitter.matcher.Aliaser;
import org.clyze.source.irfitter.source.Driver;
import org.clyze.source.irfitter.source.model.SourceFile;
import org.clyze.source.irfitter.ir.IRProcessor;
import org.clyze.utils.JHelper;

/** The main application class. */
public class Main {

    public static void main(String[] args) {
        run(args);
    }

    /**
     * Main entry point.
     * @param args    command-line arguments
     * @return        the result of the run
     */
    public static RunResult run(String[] args) {
        Options options = new Options();

        Option srcOpt = new Option("s", "source", true, "Sources (.zip/.jar file or directory).");
        srcOpt.setRequired(true);
        srcOpt.setArgName("PATH");
        srcOpt.setArgs(Option.UNLIMITED_VALUES);
        options.addOption(srcOpt);

        Option irOpt = new Option("i", "ir", true, "IR file, directory, or .class/.jar/.war/.apk/.dex file.");
        irOpt.setRequired(true);
        irOpt.setArgName("PATH");
        irOpt.setArgs(Option.UNLIMITED_VALUES);
        options.addOption(irOpt);

        Option outOpt = new Option("o", "out", true, "The output directory.");
        outOpt.setArgName("PATH");
        options.addOption(outOpt);

        Option dbOpt = new Option("f", "database", true, "The database directory.");
        dbOpt.setArgName("PATH");
        options.addOption(dbOpt);

        Option relOpt = new Option("r", "relation-variables", true, "Configure relation variable columns. Format: Relation.csv:0,1");
        relOpt.setArgName("REL_VARS");
        relOpt.setArgs(Option.UNLIMITED_VALUES);
        options.addOption(relOpt);

        Option platformOpt = new Option(null, "platform", false, "Use code as platform (skip method bodies).");
        platformOpt.setArgName("ARCHIVE");
        platformOpt.setArgs(Option.UNLIMITED_VALUES);
        options.addOption(platformOpt);

        Option debugOpt = new Option("d", "debug", false, "Enable debug mode.");
        options.addOption(debugOpt);

        Option sarifOpt = new Option(null, "translate-sarif", false, "Translate SARIF results.");
        options.addOption(sarifOpt);

        Option resolveVarsOpt = new Option(null, "resolve-variables", false, "Map variables from Doop facts to source variables.");
        options.addOption(resolveVarsOpt);

        Option resolveInvocationsOpt = new Option(null, "resolve-invocations", false, "Resolve invocation targets.");
        options.addOption(resolveInvocationsOpt);

        Option translateResultsOpt = new Option(null, "translate-results", false, "Translate Doop results to source results.");
        options.addOption(translateResultsOpt);

        Option jsonOpt = new Option("j", "json", false, "Enable JSON output.");
        options.addOption(jsonOpt);

        Option lossyOpt = new Option("l", "lossy", false, "Enable lossy heuristics.");
        options.addOption(lossyOpt);

        Option synthOpt = new Option(null, "synthesize-types", false, "Synthesize types from partial source/IR information.");
        options.addOption(synthOpt);

        Option matchIROpt = new Option(null, "match-ir", false, "Only generate metadata for source elements matching IR elements.");
        options.addOption(matchIROpt);

        Option versionOpt = new Option("v", "version", false, "Print version.");
        options.addOption(versionOpt);

        Option helpOpt = new Option("h", "help", false, "Print help.");
        options.addOption(helpOpt);

        if (args.length == 0) {
            printUsage(options);
            return null;
        }
        String version1 = "-" + versionOpt.getOpt();
        String version2 = "--" + versionOpt.getLongOpt();
        String help1 = "-" + helpOpt.getOpt();
        String help2 = "--" + helpOpt.getLongOpt();
        for (String arg : args)
            if (arg.equals(version1) || arg.equals(version2)) {
                System.out.println(JHelper.getVersionInfo(Main.class));
                return null;
            } else if (arg.equals(help1) || arg.equals(help2)) {
                printUsage(options);
                return null;
            }

        CommandLineParser parser = new GnuParser();
        try {
            CommandLine cli = parser.parse(options, args);
            boolean debug = cli.hasOption(debugOpt.getOpt());
            boolean sarif = cli.hasOption(sarifOpt.getLongOpt());
            boolean resolveVars = cli.hasOption(resolveVarsOpt.getLongOpt());
            boolean translateResults = cli.hasOption(translateResultsOpt.getLongOpt());
            String dbVal = cli.getOptionValue(dbOpt.getOpt());
            if (missingOption(cli, resolveVarsOpt, dbOpt) ||
                missingOption(cli, translateResultsOpt, dbOpt) ||
                missingOption(cli, sarifOpt, dbOpt) ||
                missingOption(cli, sarifOpt, outOpt))
                return null;
            boolean json = cli.hasOption(jsonOpt.getOpt());
            boolean out = cli.hasOption(outOpt.getOpt());
            if (json && !out) {
                System.err.println("ERROR: --" + jsonOpt.getLongOpt() + " requires -" + outOpt.getOpt() + "/--" + outOpt.getLongOpt());
                return null;
            }
            boolean synthesizeTypes = cli.hasOption(synthOpt.getLongOpt());
            boolean lossy = cli.hasOption(lossyOpt.getLongOpt());
            boolean resolveInvocations = cli.hasOption(resolveInvocationsOpt.getLongOpt());
            boolean matchIR = cli.hasOption(matchIROpt.getLongOpt());
            String[] irs = cli.getOptionValues(irOpt.getOpt());
            String[] platforms = cli.getOptionValues(platformOpt.getLongOpt());
            String[] srcs = cli.getOptionValues(srcOpt.getOpt());
            String[] relVars = cli.getOptionValues(relOpt.getOpt());

            // Process IR (such as Java bytecode).
            Set<String> vaIrMethods = new ConcurrentSkipListSet<>();
            IRState irState = new IRState();
            for (String i : irs)
                IRProcessor.processIR(irState, vaIrMethods, new File(i), debug, true);
            if (platforms != null)
                for (String p : platforms)
                    IRProcessor.processIR(irState, vaIrMethods, new File(p), debug, false);
            if (debug)
                System.out.println("IR vararg methods: " + vaIrMethods);
            irState.resolveLambdas(debug);

            File db = dbVal == null ? null : new File(dbVal);
            File outPath = out ? new File(cli.getOptionValue(outOpt.getOpt())) : null;
            Driver driver = new Driver(outPath, db, debug, vaIrMethods);

            // Process source code.
            Aliaser aliaser = new Aliaser(translateResults, debug, json);
            List<SourceFile> sources = new ArrayList<>();
            for (String s : srcs) {
                File srcFile = new File(s);
                if (!srcFile.exists()) {
                    System.err.println("ERROR: path does not exist: " + s);
                    continue;
                }
                sources.addAll(driver.readSources(srcFile, debug, synthesizeTypes, lossy, matchIR, aliaser));
            }

            // Match information between IR and sources.
            return driver.match(irState.irTypes, sources, json, sarif, resolveInvocations, resolveVars, translateResults, matchIR, aliaser, relVars);
        } catch (ParseException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static void printUsage(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.setWidth(100);
        formatter.printHelp("source-ir-fitter [OPTION]...", options);
    }

    private static boolean missingOption(CommandLine cli, Option opt, Option depOpt) {
        for (String optLabel : new String[] {opt.getLongOpt(), opt.getOpt()})
            if (optLabel != null)
                if (cli.hasOption(optLabel))
                    if (!cli.hasOption(depOpt.getOpt()) && !cli.hasOption(depOpt.getLongOpt())) {
                        StringJoiner sj = new StringJoiner("/");
                        if (depOpt.getOpt() != null)
                            sj.add("-" + depOpt.getOpt());
                        if (depOpt.getLongOpt() != null)
                            sj.add("--" + depOpt.getLongOpt());
                        System.err.println("ERROR: --" + opt.getLongOpt() + " requires " + sj);
                        return true;
                    }
        return false;
    }
}
