package org.clyze.source.irfitter;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import org.apache.commons.cli.*;
import org.clyze.source.irfitter.ir.model.IRType;
import org.clyze.source.irfitter.source.Driver;
import org.clyze.source.irfitter.source.model.SourceFile;
import org.clyze.source.irfitter.ir.IRProcessor;
import org.clyze.utils.VersionInfo;

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

        Option irOpt = new Option("i", "ir", true, "IR file, directory, or .class/.jar/.apk/.dex file.");
        irOpt.setRequired(true);
        irOpt.setArgName("PATH");
        irOpt.setArgs(Option.UNLIMITED_VALUES);
        options.addOption(irOpt);

        Option outOpt = new Option("o", "out", true, "The output directory.");
        outOpt.setArgName("PATH");
        options.addOption(outOpt);

        Option dbopt = new Option("f", "database", true, "The database directory.");
        dbopt.setArgName("PATH");
        options.addOption(dbopt);

        Option debugOpt = new Option("d", "debug", false, "Enable debug mode.");
        options.addOption(debugOpt);

        Option sarifOpt = new Option(null, "sarif", false, "Enable SARIF output.");
        options.addOption(sarifOpt);

        Option resolveVarsOpt = new Option(null, "resolve-variables", false, "Map variables from Doop facts to source variables.");
        options.addOption(resolveVarsOpt);

        Option translateResultsOpt = new Option(null, "translate-results", false, "Translate Doop results to source results.");
        options.addOption(translateResultsOpt);

        Option jsonOpt = new Option("j", "json", false, "Enable JSON output.");
        options.addOption(jsonOpt);

        Option lossyOpt = new Option("l", "lossy", false, "Enable lossy heuristics.");
        options.addOption(lossyOpt);

        Option synthOpt = new Option(null, "synthesize-types", false, "Synthesize types from partial source/IR information.");
        options.addOption(synthOpt);

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
                System.out.println(VersionInfo.getVersionInfo(Main.class));
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
            String dbVal = cli.getOptionValue(dbopt.getOpt());
            if (resolveVars && (dbVal == null)) {
                System.err.println("ERROR: --" + resolveVarsOpt.getLongOpt() + " requires -" + dbopt.getOpt() + "/--" + dbopt.getLongOpt());
                return null;
            } else if (translateResults && (dbVal == null)) {
                System.err.println("ERROR: --" + translateResultsOpt.getLongOpt() + " requires -" + dbopt.getOpt() + "/--" + dbopt.getLongOpt());
                return null;
            }
            boolean json = cli.hasOption(jsonOpt.getOpt());
            boolean out = cli.hasOption(outOpt.getOpt());
            if (json && !out) {
                System.err.println("ERROR: --" + jsonOpt.getLongOpt() + " requires -" + outOpt.getOpt() + "/--" + outOpt.getLongOpt());
                return null;
            }
            boolean synthesizeTypes = cli.hasOption(synthOpt.getLongOpt());
            boolean lossy = cli.hasOption(lossyOpt.getLongOpt());
            String[] irs = cli.getOptionValues(irOpt.getOpt());
            String[] srcs = cli.getOptionValues(srcOpt.getOpt());

            // Process IR (such as Java bytecode).
            Set<String> vaIrMethods = new ConcurrentSkipListSet<>();
            List<IRType> irTypes = new ArrayList<>();
            for (String i : irs)
                irTypes.addAll(IRProcessor.processIR(vaIrMethods, new File(i), debug));
            if (debug)
                System.out.println("IR vararg methods: " + vaIrMethods);

            File db = dbVal == null ? null : new File(dbVal);
            File outPath = out ? new File(cli.getOptionValue(outOpt.getOpt())) : null;
            Driver driver = new Driver(outPath, db, "1.0", false, debug, vaIrMethods);

            // Process source code.
            List<SourceFile> sources = new ArrayList<>();
            for (String s : srcs) {
                File srcFile = new File(s);
                if (!srcFile.exists()) {
                    System.err.println("ERROR: path does not exist: " + s);
                    continue;
                }
                sources.addAll(driver.readSources(srcFile, debug, synthesizeTypes, lossy));
            }

            // Match information between IR and sources.
            return driver.match(irTypes, sources, json, sarif, resolveVars, translateResults);
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
}
