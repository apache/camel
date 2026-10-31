/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.dsl.jbang.core.commands;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.ai.YamlLoadFailureReport;
import org.apache.camel.dsl.jbang.core.commands.exceptionhandler.CliExecutionExceptionHandler;
import org.apache.camel.dsl.jbang.core.common.BackgroundLog;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.EnvironmentHelper;
import org.apache.camel.dsl.jbang.core.common.ExampleHelper;
import org.apache.camel.dsl.jbang.core.common.JavaVersionCompletionCandidates;
import org.apache.camel.dsl.jbang.core.common.LauncherHelper;
import org.apache.camel.dsl.jbang.core.common.LoggingLevelCompletionCandidates;
import org.apache.camel.dsl.jbang.core.common.ProfileCompletionCandidates;
import org.apache.camel.dsl.jbang.core.common.RunRuntimeCompletionCandidates;
import org.apache.camel.dsl.jbang.core.common.RunRuntimeTypeConverter;
import org.apache.camel.dsl.jbang.core.common.TerminalWidthHelper;
import org.apache.camel.dsl.jbang.run.ConfiguredRun;
import org.apache.camel.dsl.jbang.run.ProjectRunner;
import org.apache.camel.dsl.jbang.run.RunContext;
import org.apache.camel.dsl.jbang.run.RunPipeline;
import org.apache.camel.dsl.jbang.run.RunProfileProperties;
import org.apache.camel.dsl.jbang.run.RunRequest;
import org.apache.camel.dsl.jbang.run.RunSettings;
import org.apache.camel.dsl.jbang.run.RunningIntegration;
import org.apache.camel.dsl.jbang.run.common.ExportHelper;
import org.apache.camel.dsl.jbang.run.common.Printer;
import org.apache.camel.dsl.jbang.run.common.RuntimeType;
import org.apache.camel.dsl.jbang.run.common.RuntimeUtil;
import org.apache.camel.dsl.jbang.run.common.TemplateHelper;
import org.apache.camel.dsl.jbang.run.common.VersionHelper;
import org.apache.camel.main.KameletMain;
import org.apache.camel.main.util.SuggestSimilarHelper;
import org.apache.camel.spi.BacklogDebugger;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import static org.apache.camel.dsl.jbang.core.common.CamelCommandHelper.extractState;
import static org.apache.camel.dsl.jbang.run.common.CamelJBangConstants.*;

@Command(name = "run", description = "Run as local Camel integration", sortOptions = false, showDefaultValues = true,
         footer = {
                 "%nExamples:",
                 "  camel run hello.java",
                 "  camel run hello.yaml",
                 "  camel run *",
                 "  camel run hello.java --dev",
                 "  camel run hello.java --port=8080",
                 "  camel run https://gist.github.com/user/123456",
                 "  camel run --example",
                 "  camel run --example=timer-log" })
public class Run extends CamelCommand {

    public static final String RUN_SETTINGS_FILE = ExportHelper.RUN_SETTINGS_FILE;
    public static final String RUN_PLATFORM_DIR = ProjectRunner.RUN_PLATFORM_DIR;

    private static final String CLIPBOARD_GENERATED_FILE = "generated-clipboard";
    private static final Pattern PACKAGE_PATTERN = Pattern.compile(
            "^\\s*package\\s+([a-zA-Z][.\\w]*)\\s*;.*$", Pattern.MULTILINE);

    private static final Pattern CLASS_PATTERN = Pattern.compile(
            "^\\s*public class\\s+([a-zA-Z0-9]*)[\\s+|;].*$", Pattern.MULTILINE);

    protected Path exportBaseDir;
    // the working directory, which is deleted and created again when running
    Path workDir = CommandLineHelper.getWorkDir();
    boolean scriptRun;
    boolean transformRun;
    boolean transformMessageRun;
    boolean debugRun;

    // package-private for RunPreviousLogTest
    Path logFile;
    public long spawnPid;

    private Printer quietPrinter;
    private boolean mcpStdioMode;

    @Parameters(description = "The Camel file(s) to run. If no files specified then application.properties is used as source for which files to run.",
                arity = "0..9", paramLabel = "<files>", parameterConsumer = FilesConsumer.class)
    Path[] filePaths; // Defined only for file path completion; the field never used

    public List<String> files = new ArrayList<>();

    @Option(names = { "--runtime" },
            completionCandidates = RunRuntimeCompletionCandidates.class,
            defaultValue = "jbang",
            converter = RunRuntimeTypeConverter.class,
            description = "Runtime (${COMPLETION-CANDIDATES}). The jbang runtime runs in-process in the Camel CLI JVM"
                          + " which is fast and intended for prototyping. The other runtimes export to a temporary project"
                          + " and run in a separate JVM via Maven, similar to a production deployment.")
    RuntimeType runtime = RuntimeType.jbang;

    @Option(names = { "--source-dir" },
            description = "Source directory for dynamically loading Camel file(s) to run. When using this, then files cannot be specified at the same time.")
    String sourceDir;

    @Option(names = { "--data-dir" },
            description = "Folders under the source directory (comma separated) that hold data the routes read or write, not sources: in dev mode a change in them does not reload the routes (such as inbox,archive).")
    String dataDir;

    @Option(names = { "--resource-dir", "--resource-dirs" },
            description = "Additional resource directories to include on the classpath (recursive, preserving structure)",
            split = ",")
    List<String> resourceDirs = new ArrayList<>();

    @Option(names = { "--background" }, defaultValue = "false", description = "Run in the background")
    public boolean background;

    @Option(names = { "--background-wait" }, defaultValue = "60s", converter = BackgroundWait.Converter.class,
            parameterConsumer = BackgroundWait.Consumer.class,
            description = "How long to wait for the integration in the background to start, before returning, in millis"
                          + " or as a duration such as 2m (true waits 60 seconds, false does not wait)")
    public long backgroundWait = BackgroundWait.DEFAULT;

    @Option(names = { "--empty" }, defaultValue = "false", description = "Run an empty Camel without loading source files")
    public boolean empty;

    @CommandLine.Option(names = {
            "--java-version",
            "--java" }, completionCandidates = JavaVersionCompletionCandidates.class,
                        description = "Java version (${COMPLETION-CANDIDATES})", defaultValue = "21")
    protected String javaVersion = "21";

    @Option(names = { "--camel-version" }, description = "To run using a different Camel version than the default version.")
    String camelVersion;

    @Option(names = { "--camel-spring-boot-version" },
            description = "To run using a different Camel Spring Boot version than the default version.")
    String camelSpringBootVersion;

    @Option(names = { "--kamelets-version" },
            description = "Apache Camel Kamelets version (auto-detected from classpath if not set)")
    String kameletsVersion;

    @CommandLine.Mixin
    QuarkusPlatformMixin quarkusPlatform = new QuarkusPlatformMixin();

    @Option(names = { "--spring-boot-version" }, description = "Spring Boot version",
            defaultValue = RuntimeType.SPRING_BOOT_VERSION)
    String springBootVersion = RuntimeType.SPRING_BOOT_VERSION;

    @Option(names = { "--profile" }, scope = CommandLine.ScopeType.INHERIT, defaultValue = "dev",
            completionCandidates = ProfileCompletionCandidates.class,
            description = "Profile to run (${COMPLETION-CANDIDATES}).")
    String profile = "dev";

    @Option(names = { "--dep", "--dependency" }, description = "Add additional dependencies",
            split = ",")
    List<String> dependencies = new ArrayList<>();

    @Option(names = { "--gav" }, description = "The Maven group:artifact:version (used during exporting)")
    String gav;

    @Option(names = { "--maven-settings" },
            description = "Optional location of Maven settings.xml file to configure servers, repositories, mirrors and proxies."
                          + " If set to \"false\", not even the default ~/.m2/settings.xml will be used.")
    String mavenSettings;

    @Option(names = { "--maven-settings-security" },
            description = "Optional location of Maven settings-security.xml file to decrypt settings.xml")
    String mavenSettingsSecurity;

    @Option(names = { "--maven-central-enabled" }, defaultValue = "true", arity = "0..1", fallbackValue = "true",
            description = "Whether downloading JARs from Maven Central repository is enabled")
    boolean mavenCentralEnabled = true;

    @Option(names = { "--maven-apache-snapshot-enabled" }, defaultValue = "true", arity = "0..1", fallbackValue = "true",
            description = "Whether downloading JARs from ASF Maven Snapshot repository is enabled")
    boolean mavenApacheSnapshotEnabled = true;

    @CommandLine.Mixin
    public MavenResolverMixin mavenResolver = new MavenResolverMixin();

    @Option(names = { "--package-scan-jars" }, defaultValue = "false",
            description = "Whether to automatic package scan JARs for custom Spring or Quarkus beans making them available for Camel CLI")
    boolean packageScanJars;

    @CommandLine.ArgGroup(validate = false, heading = "%nLogging Options:%n")
    LoggingOptions loggingOptions = new LoggingOptions();

    @CommandLine.ArgGroup(validate = false, heading = "%nDebug Options:%n")
    DebugOptions debugOptions = new DebugOptions();

    @CommandLine.ArgGroup(validate = false, heading = "%nExecution Limit Options:%n")
    public ExecutionLimitOptions executionLimitOptions = new ExecutionLimitOptions();

    @CommandLine.ArgGroup(validate = false, heading = "%nServer Options:%n")
    ServerOptions serverOptions = new ServerOptions();

    @Option(names = { "--jvm-debug" }, parameterConsumer = DebugConsumer.class, paramLabel = "<true|false|port>",
            description = "To enable JVM remote debugging on port 4004 by default. The supported values are true to " +
                          "enable the remote debugging, false to disable the remote debugging or a number to use a custom port")
    int jvmDebugPort;

    @Option(names = { "--jvm-args" },
            description = "Additional JVM arguments (e.g. -Xmx256m -Xms128m)")
    String jvmArgs;

    @Option(names = { "--name" }, defaultValue = "CamelJBang",
            description = "The name of the Camel application. By default the name of the directory when running a whole directory"
                          + " (--source-dir, or a directory), else the name of the first file")
    String name;

    @Option(names = { "--exclude" }, description = "Exclude files by name or pattern")
    List<String> excludes = new ArrayList<>();

    // Logging, execution limit, debug, and server options are defined in their respective @ArgGroup inner classes below

    @Option(names = { "--reload", "--dev" },
            description = "Enables dev mode (live reload when source files are updated and saved)")
    boolean dev;

    // trace and backlogTrace options are in DebugOptions @ArgGroup

    @Option(names = { "--properties" },
            description = "comma separated list of properties file" +
                          " (ex. /path/to/file.properties,/path/to/other.properties")
    public String propertiesFiles;

    // the profile properties files that are loaded (application.properties and application-<profile>.properties)
    final Set<Path> profilePropertiesFiles = new HashSet<>();

    @Option(names = { "--prop", "--property" }, description = "Additional properties (override existing)")
    public String[] property;

    @Option(names = { "--stub" }, description = "Stubs all the matching endpoint uri with the given component name or pattern."
                                                + " Multiple names can be separated by comma."
                                                + " (all = stub all endpoints, remote = stub only remote components).")
    String stub;

    @Option(names = { "--chaos" }, defaultValue = "false",
            description = "Add camel-chaos, to turn on faults while running (camel cmd chaos-add); on with profile dev,"
                          + " never with prod. Not with Spring Boot or Quarkus")
    boolean chaos;

    // jfr and jfrProfile options are in DebugOptions @ArgGroup

    @Option(names = { "--local-kamelet-dir" },
            description = "Local directory (or github link) for loading Kamelets (takes precedence). Multiple directories can be specified separated by comma.")
    String localKameletDir;

    // port, managementPort, console, health, metrics, observe options are in ServerOptions @ArgGroup

    @Option(names = { "--modeline" }, defaultValue = "true", arity = "0..1", fallbackValue = "true",
            description = "Whether to support JBang style //DEPS to specify additional dependencies")
    boolean modeline = true;

    @Option(names = { "--open-api" }, description = "Adds an OpenAPI spec from the given file (json or yaml file)")
    String openapi;

    @Option(names = { "--code" }, description = "Run the given text or file as Java DSL routes")
    String code;

    @Option(names = { "--verbose" }, defaultValue = "false",
            description = "Verbose output of startup activity (dependency resolution and downloading")
    boolean verbose;

    @Option(names = { "--ignore-loading-error" }, defaultValue = "false",
            description = "Whether to ignore route loading and compilation errors (use this with care!)")
    protected boolean ignoreLoadingError;

    @Option(names = { "--lazy-bean" }, defaultValue = "false",
            description = "Whether to use lazy bean initialization (can help with complex classloading issues)")
    protected boolean lazyBean;

    @Option(names = { "--prompt" }, defaultValue = "false",
            description = "Allow user to type in required parameters in prompt if not present in application")
    boolean prompt;

    @Option(names = { "--skip-plugins" }, defaultValue = "false",
            description = "Skip resolving plugin dependencies")
    boolean skipPlugins;

    @Option(names = { "--example" },
            description = "Run an example by name (timer-log or route/aggregator), list the groups when no name is given, or list one group by its name (route).",
            arity = "0..1", fallbackValue = "")
    String example;

    public Run(CamelJBangMain main) {
        super(main);
    }

    @Override
    public boolean disarrangeLogging() {
        // every runtime except the in-process jbang runtime spawns a separate JVM
        return RuntimeType.jbang != runtime;
    }

    @Override
    public Integer doCall() throws Exception {
        if (prompt) {
            // asks for the missing properties on the terminal
            requireInteractiveTerminal("camel run --prompt");
        }
        // handle --example
        if (example != null && example.isEmpty()) {
            return listExamples(null);
        }
        if (example != null && ExampleHelper.isGroup(example)) {
            // a group name lists the group, an example name runs the example
            return listExamples(example);
        }
        if (example != null) {
            return runExample();
        }

        checkFilesExist();
        printConfigurationValues("Running integration with the following configuration:");
        // run
        return run();
    }

    /**
     * Fails a local file that does not exist with exit code 3, before anything is prepared or printed. Remote files
     * (such as github: and https:) and wildcards are checked when they are read.
     */
    void checkFilesExist() {
        if (files == null) {
            return;
        }
        for (String file : files) {
            String path = file.startsWith("file:") ? file.substring(5) : file;
            String scheme = getScheme(path);
            if (scheme != null && scheme.length() > 1 || path.contains("*") || path.contains("?")) {
                // a single letter is a drive on Windows
                continue;
            }
            boolean exists;
            try {
                exists = Files.exists(Paths.get(path));
            } catch (InvalidPathException e) {
                exists = false;
            }
            if (!exists) {
                throw CliException.notFound("File does not exist: " + path);
            }
        }
    }

    private int listExamples(String group) {
        List<JsonObject> catalog = ExampleHelper.loadCatalog();
        if (catalog.isEmpty()) {
            printer().printErr("No example catalog found.");
            return 1;
        }
        Map<String, List<JsonObject>> groups = ExampleHelper.groupByLevel(catalog);
        if (group == null) {
            return listGroups(catalog, groups);
        }
        List<JsonObject> entries = groups.get(group);
        if (entries == null || entries.isEmpty()) {
            printer().printErr("No examples in group: " + group);
            return 1;
        }
        return listGroup(group, entries);
    }

    /**
     * The overview: every group of the ladder with its introduction and the names of its examples.
     */
    private int listGroups(List<JsonObject> catalog, Map<String, List<JsonObject>> groups) {
        int width = listingWidth();
        printer().println("Examples: " + catalog.size() + " in " + groups.size()
                          + " groups, a ladder to read in order or jump into");
        printer().println();
        int n = 0;
        for (Map.Entry<String, List<JsonObject>> g : groups.entrySet()) {
            n++;
            String level = g.getKey();
            String title = ExampleHelper.getGroupTitle(level) + " (" + g.getValue().size() + ")";
            printer().printf("%2d  %-31s %s%n", n, title, "camel run --example=" + level);
            for (String line : ExampleHelper.wrap(ExampleHelper.getGroupIntro(level), width - 4)) {
                printer().println("    " + line);
            }
            List<String> names = new ArrayList<>();
            for (JsonObject e : g.getValue()) {
                names.add(ExampleHelper.getShortName(e));
            }
            for (String line : ExampleHelper.wrap(String.join(", ", names), width - 4)) {
                printer().println("    " + line);
            }
            printer().println();
        }
        printer().println("Usage: camel run --example=<group>        what the examples of a group show and teach");
        printer().println("       camel run --example=<name>         run an example, by its name or group/name");
        printer().println("       camel run --example=<name> --dev   run it with live reload");
        return 0;
    }

    /**
     * One group in full: each example with what you will see when it runs, what it needs and what it teaches.
     */
    private int listGroup(String level, List<JsonObject> entries) {
        int width = listingWidth();
        String title = ExampleHelper.getGroupTitle(level) + ":";
        printer().println(title);
        printer().println("=".repeat(title.length()));
        for (String line : ExampleHelper.wrap(ExampleHelper.getGroupIntro(level), width - 2)) {
            printer().println("  " + line);
        }
        // "  " + four icons of two columns each + " " + the name column + " ", as the printf below lays it out
        int iconCols = 8;
        int nameCols = 28;
        int indent = 2 + iconCols + 1 + nameCols + 1;
        String pad = " ".repeat(indent);
        for (JsonObject entry : entries) {
            printer().println();
            StringBuilder icons = new StringBuilder();
            icons.append(ExampleHelper.isBundled(entry) ? "📦" : "🌐");
            icons.append(ExampleHelper.requiresDocker(entry) ? "🐳" : "  ");
            icons.append(ExampleHelper.hasCitrusTests(entry) ? "🍋" : "  ");
            icons.append(ExampleHelper.isCiSkip(entry) ? "🤖" : "  ");
            List<String> desc = ExampleHelper.wrap(entry.getStringOrDefault("description", ""), width - indent);
            printer().printf("  %s %-28s %s%n", icons, ExampleHelper.getShortName(entry), desc.isEmpty() ? "" : desc.get(0));
            for (int i = 1; i < desc.size(); i++) {
                printer().println(pad + desc.get(i));
            }
            List<String> infra = ExampleHelper.getInfraServices(entry);
            if (!infra.isEmpty()) {
                printer().println(pad + "needs: camel infra run " + String.join(" ", infra));
            }
            String needs = ExampleHelper.getNeeds(entry);
            if (needs != null) {
                for (String line : ExampleHelper.wrap("needs: " + needs, width - indent)) {
                    printer().println(pad + line);
                }
            }
            String teaches = ExampleHelper.getTeachesSummary(entry);
            for (String line : ExampleHelper.wrap(teaches, width - indent)) {
                printer().println(pad + line);
            }
        }
        printer().println();
        printer().println(
                "  📦 = bundled (works offline)  🌐 = online (fetched from GitHub)  🐳 = requires Docker  🍋 = Citrus tests  🤖 = needs a local model");
        printer().println();
        printer().println("Usage: camel run --example=<name>");
        printer().println("       camel run --example=<name> --dev");
        printer().println("       camel run --example              the groups");
        return 0;
    }

    private static int listingWidth() {
        int width = TerminalWidthHelper.getTerminalWidth();
        if (width <= 0) {
            width = 120;
        }
        return Math.min(width, 160);
    }

    private int runExample() throws Exception {
        List<JsonObject> catalog = ExampleHelper.loadCatalog();
        if (!example.contains("/")) {
            List<JsonObject> same = ExampleHelper.findExamplesByShortName(catalog, example);
            if (same.size() > 1) {
                List<String> names = new ArrayList<>();
                for (JsonObject e : same) {
                    names.add(e.getString("name"));
                }
                printer().printErr("Ambiguous example: " + example + ". Use one of: " + String.join(", ", names));
                return 1;
            }
        }
        JsonObject entry = ExampleHelper.findExample(catalog, example);

        if (entry == null) {
            List<String> names = ExampleHelper.getExampleNames(catalog);
            List<String> suggestions = SuggestSimilarHelper.didYouMean(names, example);
            if (!suggestions.isEmpty()) {
                printer().printErr("Unknown example: " + example + ". Did you mean? " + String.join(", ", suggestions));
            } else {
                printer().printErr("Unknown example: " + example);
            }
            printer().printErr("Run 'camel run --example' to see available examples.");
            return 1;
        }

        if (ExampleHelper.isBundled(entry)) {
            return runBundledExample(entry);
        } else {
            return runGithubExample(entry);
        }
    }

    private int runBundledExample(JsonObject entry) throws Exception {
        // an example run in the background outlives this JVM, so its files must stay (CAMEL-25425)
        Path tempDir = ExampleHelper.extractBundledExample(entry, !background);
        return runExampleIn(entry, tempDir);
    }

    private int runGithubExample(JsonObject entry) throws Exception {
        String eName = entry.getString("name");
        printer().println("Fetching example from GitHub: " + eName);
        if (ExampleHelper.requiresDocker(entry)) {
            printer().println("Note: this example requires Docker/Podman");
        }

        Path tempDir;
        try {
            tempDir = ExampleHelper.downloadGithubExample(entry, !background);
        } catch (Exception e) {
            printer().printErr("Failed to fetch example from GitHub: " + e.getMessage());
            printer().printErr("This example requires an internet connection.");
            return 1;
        }
        return runExampleIn(entry, tempDir);
    }

    /**
     * Runs the example in its folder. A JVM cannot change its working directory, so the example runs in a JVM of its
     * own started in that folder: its routes read and write files relative to it (file:orders, out/), wherever camel
     * run is started (CAMEL-25236). Exporting and transforming do not run the routes, and stay in this JVM.
     */
    private int runExampleIn(JsonObject entry, Path dir) throws Exception {
        String eName = entry.getString("name");
        printer().println("Running example: " + eName);
        String needs = ExampleHelper.getNeeds(entry);
        if (needs != null) {
            // what the example needs that the run does not do (a model to pull, a key to set): the user does it
            printer().println("Needs: " + needs);
        }
        if (transformRun || spec == null) {
            for (String f : ExampleHelper.getFiles(entry)) {
                files.add(dir.resolve(f).toString());
            }
            if ("CamelJBang".equals(name)) {
                name = ExampleHelper.getShortName(entry);
            }
            // use the folder as base so run() loads the example's application.properties
            exportBaseDir = dir;
            return run();
        }

        printer().println("Example folder: " + dir + " (the files it reads and writes are there)");
        List<String> cmds = ExampleHelper.runArgs(spec.commandLine().getParseResult().originalArgs(), entry);
        RunHelper.addCamelCLICommand(cmds);
        if (verbose) {
            printer().println(String.join(" ", cmds));
        }
        ProcessBuilder pb = new ProcessBuilder(cmds);
        pb.directory(dir.toFile());
        pb.inheritIO(); // run in foreground (with IO so logs are visible)
        Process p = pb.start();
        this.spawnPid = p.pid();
        // the example stops with this JVM (Ctrl+C reaches both, a kill of this one only this one)
        Thread hook = new Thread(p::destroy, "CamelExampleStop");
        Runtime.getRuntime().addShutdownHook(hook);
        try {
            return p.waitFor();
        } finally {
            try {
                Runtime.getRuntime().removeShutdownHook(hook);
            } catch (IllegalStateException e) {
                // shutting down already
            }
        }
    }

    public Integer runTransform(boolean ignoreLoadingError) throws Exception {
        // just boot silently and exit
        this.transformRun = true;
        this.ignoreLoadingError = ignoreLoadingError;
        this.name = "transform";
        return run();
    }

    public Integer runTransformMessage(String camelVersion, MavenResolverMixin mavenResolver) throws Exception {
        // just boot silently an empty camel in the background and exit
        this.transformMessageRun = true;
        this.background = true;
        this.camelVersion = camelVersion;
        this.mavenResolver = mavenResolver;
        this.empty = true;
        this.ignoreLoadingError = true;
        this.name = "transform";
        return run();
    }

    protected Integer runScript(String file) throws Exception {
        this.files.add(file);
        this.scriptRun = true;
        return run();
    }

    protected Integer runDebug() throws Exception {
        this.debugRun = true;
        return run();
    }

    private boolean isDebugMode() {
        return jvmDebugPort > 0 || debugOptions.openTelemetryAgent
                || (jvmArgs != null && !jvmArgs.isBlank());
    }

    /**
     * Whether a directory given as the only file (camel run . or camel run dirName) runs as --source-dir. A Maven
     * project keeps running its files, and so do the Spring Boot and Quarkus runtimes, which export the files to run
     * them.
     */
    boolean runsDirectoryAsSourceDir(Path dir) {
        return RunPipeline.runsDirectoryAsSourceDir(runtime, dir);
    }

    private int run() throws Exception {
        if (!empty && !files.isEmpty() && sourceDir != null) {
            // cannot have both files and source dir at the same time
            throw CliException.usage("Cannot specify both file(s) and source-dir at the same time.");
        }

        RunRequest request = toRunRequest(exportBaseDir != null ? exportBaseDir : Path.of("."));
        // special if user type: camel run . or camel run dirName
        Path dir = RunPipeline.detectDirectory(request);
        if (dir != null) {
            // the directory has the application properties of a run in this JVM (a run with Maven exports its files)
            request.baseDir = dir;
        }
        // auto-detect runtime from pom.xml before dispatch (an existing Maven project cannot run in-process)
        RunPipeline.detectRuntime(request);
        sourceDir = request.sourceDir;
        runtime = request.runtime;

        if (RuntimeType.quarkus == runtime) {
            return runQuarkus();
        } else if (RuntimeType.springBoot == runtime) {
            return runSpringBoot();
        } else if (RuntimeType.main == runtime) {
            return runCamelMain();
        }

        // route code as option: the code, or an existing java file
        String codeName = "CodeRoute";
        boolean codeFile = false;
        if (!empty && code != null) {
            Path codePath = Paths.get(code);
            if (Files.isRegularFile(codePath) && Files.exists(codePath)) {
                // must be a java file
                boolean java = codePath.getFileName().toString().endsWith(".java");
                if (!java) {
                    printer().printErr("Only java source files is accepted when using --code parameter");
                    return 1;
                }
                code = Files.readString(codePath);
                codeName = FileUtil.onlyName(codePath.getFileName().toString());
                codeFile = true;
            }
        }
        final String name = codeName;
        final boolean file = codeFile;

        RunContext context = runContext();
        context.withPreparedListener((prepared, properties, mcpStdio) -> {
            // the printer of this command is quiet with MCP over stdio
            mcpStdioMode = mcpStdio;
            context.withPrinter(printer());
            applyProfileProperties(prepared, properties);
            configureLogging(properties);
            prepared.logFile = logFile;
            if (!empty && code != null) {
                // store code in temporary file, in the work directory of the run, and use it as first file
                prepared.files.add(0, loadFromCode(code, name, file));
            }
        });
        ConfiguredRun configured = new ProjectRunner(context).configure(request);
        // the run completes the name, the files, the stub, the properties files and the ports
        fromRunRequest(request);
        if (configured.getExitCode() != 0) {
            return configured.getExitCode();
        }
        KameletMain main = configured.getMain();

        // Block --camel-version when running from camel-launcher
        if (camelVersion != null && LauncherHelper.isRunningFromLauncher()) {
            printer().printErr("The --camel-version option is not supported when running from camel-launcher.");
            printer().printDiagnostic("To use a different Camel version, either:");
            printer().printDiagnostic("  1. Use a different camel-launcher JAR version");
            printer().printDiagnostic("  2. Install Camel CLI via JBang: jbang app install camel@apache/camel");
            return CliExitCode.USAGE;
        }

        // if we have a specific camel version then make sure we really need to switch
        if (camelVersion != null) {
            CamelCatalog catalog = new DefaultCamelCatalog();
            String v = catalog.getCatalogVersion();
            if (camelVersion.equals(v)) {
                // same version, so we use current
                camelVersion = null;
            }
        }

        // okay we have validated all input and are ready to run
        if (camelVersion != null || isDebugMode()) {
            // TODO: debug camel specific version
            boolean custom = false;
            if (camelVersion != null) {
                // run in another JVM with different camel version (foreground or background)
                custom = camelVersion.contains("-") && !camelVersion.endsWith("-SNAPSHOT");
                if (custom) {
                    // regular camel versions can also be a milestone or release candidate
                    custom = !camelVersion.matches(".*-(RC|M)\\d$");
                }
            }
            if (custom) {
                // custom camel distribution
                return runCustomCamelVersion(main);
            } else {
                // apache camel distribution or remote debug enabled
                return runCamelVersion(main);
            }
        } else if (debugRun) {
            // spawn new JVM to debug in background
            return runDebug(main);
        } else if (background) {
            // spawn new JVM to run in background
            return runBackground(main);
        } else {
            // run default in current JVM with same camel version
            return runKameletMain(configured);
        }
    }

    protected void addDependencies(String... deps) {
        var depsArray = Optional.ofNullable(deps).orElse(new String[0]);
        var depsList = Arrays.stream(depsArray).filter(tok -> !tok.isEmpty()).toList();
        dependencies.addAll(depsList);
    }

    boolean jfrEnabled() {
        return toRunRequest(exportBaseDir).jfrEnabled();
    }

    String jfrFileName() {
        return toRunRequest(exportBaseDir).jfrFileName();
    }

    protected int runQuarkus() throws Exception {
        if (background) {
            throw CliException.usage("Run Camel Quarkus with --background is not supported");
        }
        if (chaos) {
            // the run would have to add camel-chaos to the exported Quarkus project and switch it on there (follow-up)
            throw CliException.usage("Run Camel Quarkus with --chaos is not supported (use --runtime=jbang or main)");
        }
        return runWithMaven();
    }

    protected int runSpringBoot() throws Exception {
        if (background) {
            throw CliException.usage("Run Camel Spring Boot with --background is not supported");
        }
        if (chaos) {
            // the run would have to add camel-chaos to the exported Spring Boot project and switch it on there
            // (follow-up)
            throw CliException.usage("Run Camel Spring Boot with --chaos is not supported (use --runtime=jbang or main)");
        }
        return runWithMaven();
    }

    protected int runCamelMain() throws Exception {
        if (background) {
            throw CliException.usage("Run Camel Main with --background is not supported (use --runtime=jbang)");
        }

        boolean existingProject = files != null && files.size() == 1 && files.get(0).endsWith("pom.xml");
        if (!existingProject) {
            if (code != null) {
                printer().printErr("Run Camel Main with --code is not supported (use --runtime=jbang)");
                return 1;
            }
            if (dev && sourceDir == null) {
                warnDataDirWithoutSourceDir();
            }
        }

        return runWithMaven();
    }

    /**
     * Runs with Maven (Camel Main, Quarkus or Spring Boot) through {@link ProjectRunner}, in the foreground.
     */
    private int runWithMaven() throws Exception {
        RunRequest request = toRunRequest(exportBaseDir);
        ExportBaseCommand.applySystemProperties(request);
        RunningIntegration integration = new ProjectRunner(runContext()).start(request);
        this.spawnPid = integration.pid();
        // wait for the application to exit as we run in foreground
        return integration.waitFor();
    }

    /**
     * The context of a run: the printer, the plugins, the dependency resolver, the exit codes and the reports of the
     * CLI, and the hooks of this command in a run in this JVM (the initial properties, the clipboard). Tests that check
     * what a run with Maven does set a process starter, so they run neither Maven nor the application.
     */
    protected RunContext runContext() {
        return CliExportContext.configure(new RunContext(), getMain(), printer(), false)
                .withFailureExitCode(new CliExecutionExceptionHandler(getMain())::getExitCode)
                // a YAML route file that did not load in dev mode: the validator's report (CAMEL-24851)
                .withReloadFailureListener(YamlLoadFailureReport::logIfYamlLoadFailure)
                .withInitialProperties(this::doAddInitialProperty)
                .withClipboard(this::loadFromClipboard)
                .withDataDirWithoutSourceDir(this::warnDataDirWithoutSourceDir);
    }

    /**
     * --data-dir only changes what dev mode watches in a source directory: without one only the given files are
     * watched, and their data folders are not, so the option has nothing to leave out. Said when it is given on the
     * command line; camel.jbang.dataDir in an application.properties is not, as an example that declares its data
     * folders also runs with its files (camel run --example).
     */
    private void warnDataDirWithoutSourceDir() {
        if (dataDir != null && spec != null && spec.commandLine() != null
                && spec.commandLine().getParseResult() != null
                && spec.commandLine().getParseResult().hasMatchedOption("--data-dir")) {
            printer().printWarn("--data-dir=" + dataDir + " has no effect without --source-dir: dev mode then only"
                                + " watches the given files");
        }
    }

    protected void doAddInitialProperty(KameletMain main) {
        // noop
    }

    /**
     * The request of the settings pipeline, with the options of this command; it shares the lists of the files and the
     * dependencies.
     */
    RunRequest toRunRequest(Path baseDir) {
        RunRequest request = new RunRequest();
        request.workDir = workDir;
        request.baseDir = baseDir;
        request.files = files;
        request.name = name;
        request.runtime = runtime;
        request.profile = profile;
        request.empty = empty;
        request.dependencies = dependencies;
        request.propertiesFiles = propertiesFiles;
        request.profilePropertiesFiles = profilePropertiesFiles;
        request.applicationProperties = property;
        request.excludes = excludes;
        request.resourceDirs = resourceDirs;
        request.sourceDir = sourceDir;
        request.dataDir = dataDir;
        request.localKameletDir = localKameletDir;
        request.dev = dev;
        request.stub = stub;
        request.chaos = chaos;
        request.trace = debugOptions.trace;
        request.backlogTrace = debugOptions.backlogTrace;
        request.modeline = modeline;
        request.ignoreLoadingError = ignoreLoadingError;
        request.lazyBean = lazyBean;
        request.prompt = prompt;
        request.gav = gav;
        request.openapi = openapi;
        request.verbose = verbose;
        request.loggingLevel = loggingOptions.loggingLevel;
        request.skipPlugins = skipPlugins;
        request.mavenResolver = mavenResolver.toResolver();
        request.mavenSettings = mavenSettings;
        request.mavenSettingsSecurity = mavenSettingsSecurity;
        request.mavenCentralEnabled = mavenCentralEnabled;
        request.mavenApacheSnapshotEnabled = mavenApacheSnapshotEnabled;
        request.packageScanJars = packageScanJars;
        request.camelVersion = camelVersion;
        request.springBootVersion = springBootVersion;
        request.camelSpringBootVersion = camelSpringBootVersion;
        request.quarkusPlatform = quarkusPlatform != null ? quarkusPlatform.toPlatform() : null;
        request.kameletsVersion = kameletsVersion;
        request.javaVersion = javaVersion;
        request.port = serverOptions.port;
        request.managementPort = serverOptions.managementPort;
        request.console = serverOptions.console;
        request.mcp = serverOptions.mcp;
        request.mcpStdio = serverOptions.mcpStdio;
        request.mcpTags = serverOptions.mcpTags;
        request.openapiUi = serverOptions.openapiUi;
        request.health = serverOptions.health;
        request.metrics = serverOptions.metrics;
        request.observe = serverOptions.observe;
        request.maxMessages = executionLimitOptions.maxMessages;
        request.maxSeconds = executionLimitOptions.maxSeconds;
        request.maxIdleSeconds = executionLimitOptions.maxIdleSeconds;
        request.jvmArgs = jvmArgs;
        request.jvmDebugPort = jvmDebugPort;
        request.jfr = debugOptions.jfr;
        request.jfrProfile = debugOptions.jfrProfile;
        request.openTelemetryAgent = debugOptions.openTelemetryAgent;
        request.openTelemetryAgentExport = debugOptions.openTelemetryAgentExport;
        request.debugRun = debugRun;
        request.transformRun = transformRun;
        request.transformMessageRun = transformMessageRun;
        request.scriptRun = scriptRun;
        request.logFile = logFile;
        request.keepPreviousLog = keepsPreviousLog();
        return request;
    }

    /**
     * Takes what the settings pipeline completed, which the run reads afterwards.
     */
    void fromRunRequest(RunRequest request) {
        files = request.files;
        name = request.name;
        stub = request.stub;
        propertiesFiles = request.propertiesFiles;
        serverOptions.port = request.port;
        serverOptions.managementPort = request.managementPort;
    }

    /**
     * Configures what only this command does with the application properties of the run: the logging, and the runs in
     * another JVM (in the background, with a debugger, camel debug, and the repositories of the Camel they run). The
     * run configured the request with the others ({@link RunProfileProperties#applyTo(RunRequest, Properties)}).
     */
    private void applyProfileProperties(RunRequest request, Properties answer) {
        if (answer != null) {
            // logging level/color may be configured in the properties file
            loggingOptions.loggingLevel = answer.getProperty("loggingLevel", loggingOptions.loggingLevel);
            loggingOptions.loggingColor
                    = "true"
                            .equals(answer.getProperty("loggingColor", loggingOptions.loggingColor ? "true" : "false"));
            loggingOptions.loggingJson
                    = "true"
                            .equals(answer.getProperty("loggingJson", loggingOptions.loggingJson ? "true" : "false"));
        }
        if (answer != null) {
            mavenResolver = MavenResolverMixin.of(answer, mavenResolver);
            // camel debug of a Quarkus project resolves the Quarkus Maven plugin of the platform
            if (quarkusPlatform == null) {
                quarkusPlatform = new QuarkusPlatformMixin();
            }
            quarkusPlatform = QuarkusPlatformMixin.of(answer, quarkusPlatform);
            background = "true".equals(answer.getProperty(BACKGROUND, background ? "true" : "false"));
            String wait = answer.getProperty(BACKGROUND_WAIT);
            if (wait != null) {
                backgroundWait = BackgroundWait.parse(wait);
            }
            jvmDebugPort = parseJvmDebugPort(answer.getProperty(JVM_DEBUG, Integer.toString(jvmDebugPort)));
        }
        // what this command reads itself to run in another JVM (with another Camel version)
        camelVersion = request.camelVersion;
        kameletsVersion = request.kameletsVersion;
        javaVersion = request.javaVersion;
    }

    /**
     * Parses the JVM debug port from the given value.
     * <p/>
     * The value can be {@code true} to indicate a default port which is {@code 4004}, {@code false} to indicate no
     * debug, or a number corresponding to a custom port.
     *
     * @param  value the value to parse.
     *
     * @return       the JVM debug port corresponding to the given value.
     */
    private static int parseJvmDebugPort(String value) {
        if (value == null) {
            return 0;
        } else if (value.equals("true")) {
            return 4004;
        } else if (value.equals("false")) {
            return 0;
        }
        return Integer.parseInt(value);
    }

    protected int runCamelVersion(KameletMain main) throws Exception {
        List<String> jbangArgs = createCamelVersionCommand();

        ProcessBuilder pb = new ProcessBuilder();
        pb.command(jbangArgs);

        if (verbose) {
            printer().println(String.join(" ", jbangArgs));
        }

        if (background) {
            return runBackgroundProcess(pb, "Camel Main");
        } else {
            pb.inheritIO(); // run in foreground (with IO so logs are visible)
            Process p = pb.start();
            this.spawnPid = p.pid();
            // wait for that process to exit as we run in foreground
            return p.waitFor();
        }
    }

    // The jbang command that runs this integration in a new JVM.
    List<String> createCamelVersionCommand() {
        List<String> cmds;
        if (spec != null) {
            cmds = new ArrayList<>(spec.commandLine().getParseResult().originalArgs());
        } else {
            cmds = new ArrayList<>();
            cmds.add("run");
            if (transformMessageRun) {
                cmds.add("--empty");
            }
        }

        if (background) {
            cmds = BackgroundWait.withoutBackgroundOptions(cmds);
        }
        if (camelVersion != null) {
            cmds.remove("--camel-version=" + camelVersion);
        }
        if (kameletsVersion != null) {
            cmds.remove("--kamelets-version=" + kameletsVersion);
        }
        // need to use jbang command to specify camel version
        List<String> jbangArgs = new ArrayList<>();
        jbangArgs.add("jbang");
        jbangArgs.add("run");
        // without camel.jbang.version the new JVM runs the default version of the camel@apache/camel catalog
        // script, so name the running version when no other is requested (--jvm-debug, --jvm-args, ...)
        String version = camelVersion != null ? camelVersion : new DefaultCamelCatalog().getCatalogVersion();
        jbangArgs.add("-Dcamel.jbang.version=" + version);
        if (kameletsVersion != null) {
            if (camelVersion != null && VersionHelper.isLE(camelVersion, "4.16.0")) {
                jbangArgs.add("-Dcamel-kamelets.version=" + kameletsVersion);
            } else {
                cmds.add("--kamelets-version=" + kameletsVersion);
            }
        }
        // tooling may signal to run JMX debugger in suspended mode via JVM system property
        // which we must include in args as well
        String debugSuspend = System.getProperty(BacklogDebugger.SUSPEND_MODE_SYSTEM_PROP_NAME);
        if (debugSuspend != null) {
            jbangArgs.add("-D" + BacklogDebugger.SUSPEND_MODE_SYSTEM_PROP_NAME + "=" + debugSuspend);
        }
        if (jvmDebugPort > 0) {
            jbangArgs.add("--debug=" + jvmDebugPort); // jbang --debug=port
            cmds.removeIf(arg -> arg.startsWith("--jvm-debug"));
        }
        if (jvmArgs != null && !jvmArgs.isBlank()) {
            for (String arg : jvmArgs.trim().split("\\s+")) {
                jbangArgs.add("--java-options=" + arg);
            }
            cmds.removeIf(a -> a.startsWith("--jvm-args"));
        }
        if (debugOptions.openTelemetryAgent) {
            boolean otlpExport = "otlp".equals(debugOptions.openTelemetryAgentExport)
                    || "jaeger".equals(debugOptions.openTelemetryAgentExport);
            boolean observabilityExport = "observability".equals(debugOptions.openTelemetryAgentExport);
            jbangArgs.add("--javaagent=io.opentelemetry.javaagent:opentelemetry-javaagent:RELEASE");
            jbangArgs.add("-Dotel.metrics.exporter=none");
            jbangArgs.add("-Dotel.service.name=camel");
            cmds.removeIf(arg -> arg.startsWith("--open-telemetry-agent"));
            cmds.add("--dep=camel:opentelemetry2");
            cmds.add("--prop=camel.opentelemetry2.enabled=true");
            if (observabilityExport) {
                jbangArgs.add("-Dotel.exporter.otlp.protocol=http/protobuf");
                jbangArgs.add(
                        "-Dotel.exporter.otlp.traces.endpoint=http://localhost:10428/insert/opentelemetry/v1/traces");
                jbangArgs.add("-Dotel.logs.exporter=otlp");
                jbangArgs.add(
                        "-Dotel.exporter.otlp.logs.endpoint=http://localhost:9428/insert/opentelemetry/v1/logs");
                cmds.add("--prop=camel.opentelemetry2.exportTarget=observability");
            } else if (otlpExport) {
                jbangArgs.add("-Dotel.logs.exporter=none");
                jbangArgs.add("-Dotel.exporter.otlp.traces.endpoint=http://localhost:4318/v1/traces");
                cmds.add("--prop=camel.opentelemetry2.exportTarget=otlp");
            } else {
                jbangArgs.add("-Dotel.logs.exporter=none");
                int port = serverOptions.port > 0 ? serverOptions.port : 8080;
                jbangArgs.add("-Dotel.exporter.otlp.traces.endpoint=http://localhost:" + port + "/v1/traces");
                cmds.add("--dep=camel:platform-http-main");
                cmds.add("--dep=mvn:io.opentelemetry.proto:opentelemetry-proto:RELEASE");
                if (cmds.stream().noneMatch(a -> a.startsWith("--port"))) {
                    cmds.add("--port=" + port);
                }
            }
        }

        if (javaVersion != null) {
            jbangArgs.add("--java=" + javaVersion);
            // remove from cmds so it is not passed to the older Camel version
            // which may not recognize the --java alias
            cmds.removeIf(arg -> arg.startsWith("--java-version") || arg.startsWith("--java="));
        }
        if (mavenResolver.repos() != null) {
            jbangArgs.add("--repos=" + mavenResolver.repos());
        }
        jbangArgs.add("camel@apache/camel");
        jbangArgs.addAll(cmds);
        return jbangArgs;
    }

    protected int runBackground(KameletMain main) throws Exception {
        List<String> cmds;
        if (spec != null) {
            cmds = new ArrayList<>(spec.commandLine().getParseResult().originalArgs());
        } else {
            cmds = new ArrayList<>();
            cmds.add("run");
            if (transformMessageRun) {
                cmds.add("--empty");
            }
        }

        cmds = BackgroundWait.withoutBackgroundOptions(cmds);

        RunHelper.addCamelCLICommand(cmds);

        ProcessBuilder pb = new ProcessBuilder();
        pb.command(cmds);

        if (verbose) {
            printer().println(String.join(" ", cmds));
        }
        return runBackgroundProcess(pb, "Camel Main");
    }

    protected int runBackgroundProcess(ProcessBuilder pb, String kind) throws Exception {
        // the output goes to ~/.camel/<pid>-run.log, as nobody reads the output of a process in the background
        BackgroundLog.Started started = BackgroundLog.start(pb);
        Process p = started.process();
        this.spawnPid = p.pid();
        Path logPath = started.log();

        // the commands that run Camel for themselves (transform) keep quiet, and need no log afterwards
        boolean internal = transformRun || transformMessageRun;
        if (!internal) {
            printer().println(
                    "Running " + kind + ": " + name + " in background with PID: " + p.pid()
                              + (backgroundWait > 0 ? " (waiting to startup)" : ""));
            printer().println("Log: " + logPath);
        }
        try {
            if (backgroundWait <= 0) {
                return 0;
            }
            long deadline = System.currentTimeMillis() + backgroundWait;
            int state = 0; // state 5 is running
            while (p.isAlive() && System.currentTimeMillis() < deadline && state < 5) {
                JsonObject root = loadStatus(p.pid());
                if (root != null && root.get("context") instanceof JsonObject context) {
                    state = context.getIntegerOrDefault("phase", 0);
                }
                if (state < 5) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            if (!p.isAlive()) {
                int ec = p.exitValue();
                if (ec != 0) {
                    printer().printErr(kind + ": " + name + " startup failure");
                    printer().println("");
                    printer().print(BackgroundLog.read(logPath));
                }
                return ec;
            }
            if (state < 5) {
                // still starting: it is left running, and the log tells why it takes long
                String waited = backgroundWait < 1000 ? backgroundWait + "ms" : TimeUtils.printDuration(backgroundWait);
                throw CliException.timeout(kind + ": " + name + " (PID: " + p.pid() + ") is still starting after "
                                           + waited + "; the log is in " + logPath);
            }
            printer().println(kind + ": " + name + " (state: " + extractState(state) + ")");
            return 0;
        } finally {
            if (internal) {
                // as before, the output of these is only of use during startup
                Files.deleteIfExists(logPath);
            }
        }
    }

    protected int runDebug(KameletMain main) throws Exception {
        // to be implemented in Debug
        return 0;
    }

    protected int runCustomCamelVersion(KameletMain main) throws Exception {
        Map<String, Object> model = new HashMap<>();
        model.put("JavaVersion", "21");
        model.put("MavenRepositories", mavenResolver.repos() != null ? "//REPOS " + mavenResolver.repos() : "");

        // use custom distribution of camel
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("//DEPS org.apache.camel:camel-bom:%s@pom%n", camelVersion));
        sb.append(String.format("//DEPS org.apache.camel:camel-core:%s%n", camelVersion));
        sb.append(String.format("//DEPS org.apache.camel:camel-core-engine:%s%n", camelVersion));
        sb.append(String.format("//DEPS org.apache.camel:camel-main:%s%n", camelVersion));
        sb.append(String.format("//DEPS org.apache.camel:camel-java-joor-dsl:%s%n", camelVersion));
        sb.append(String.format("//DEPS org.apache.camel:camel-kamelet:%s%n", camelVersion));
        sb.append(String.format("//DEPS org.apache.camel:camel-kamelet-main:%s%n", camelVersion));
        if (VersionHelper.isGE(camelVersion, "3.19.0")) {
            sb.append(String.format("//DEPS org.apache.camel:camel-cli-connector:%s%n", camelVersion));
        }
        model.put("CamelDependencies", sb.toString());

        // use apache distribution of camel-jbang/github-resolver
        String v = camelVersion.substring(0, camelVersion.lastIndexOf('.'));
        sb = new StringBuilder();
        sb.append(String.format("//DEPS org.apache.camel:camel-jbang-core:%s%n", v));
        sb.append(String.format("//DEPS org.apache.camel:camel-resourceresolver-github:%s%n", v));
        model.put("CamelJBangDependencies", sb.toString());

        sb = new StringBuilder();
        sb.append(String.format("//DEPS org.apache.camel.kamelets:camel-kamelets:%s%n", kameletsVersion));
        model.put("CamelKameletsDependencies", sb.toString());

        String content = TemplateHelper.processTemplate("run-custom-camel-version.ftl", model);

        String fn = CommandLineHelper.CAMEL_JBANG_WORK_DIR + "/CustomCamelJBang.java";
        Files.writeString(Paths.get(fn), content);

        List<String> cmds;
        if (spec != null) {
            cmds = new ArrayList<>(spec.commandLine().getParseResult().originalArgs());
        } else {
            cmds = new ArrayList<>();
            cmds.add("run");
        }

        if (background) {
            cmds = BackgroundWait.withoutBackgroundOptions(cmds);
        }
        if (mavenResolver.repos() != null) {
            if (!VersionHelper.isGE(v, "3.18.1")) {
                // --repos is not supported in 3.18.0 or older, so remove
                cmds.remove("--repos=" + mavenResolver.repos());
            }
        }

        cmds.remove("--camel-version=" + camelVersion);
        // need to use jbang command to specify camel version
        List<String> jbangArgs = new ArrayList<>();
        jbangArgs.add("jbang");
        jbangArgs.add(CommandLineHelper.CAMEL_JBANG_WORK_DIR + "/CustomCamelJBang.java");

        jbangArgs.addAll(cmds);

        if (verbose) {
            printer().println(String.join(" ", jbangArgs));
        }

        ProcessBuilder pb = new ProcessBuilder();
        pb.command(jbangArgs);
        if (background) {
            return runBackgroundProcess(pb, "Camel Main");
        } else {
            pb.inheritIO(); // run in foreground (with IO so logs are visible)
            Process p = pb.start();
            this.spawnPid = p.pid();
            // wait for that process to exit as we run in foreground
            return p.waitFor();
        }
    }

    /**
     * Runs Camel in this JVM, which the run configured, and waits for it to end.
     */
    protected int runKameletMain(ConfiguredRun run) throws Exception {
        return run.start().waitFor();
    }

    /**
     * Whether the log of the run is kept as the previous log of the integration when the run ends (CAMEL-25784):
     * {@code camel run} on the command line, not a Camel that another command runs in its JVM (such as
     * {@code camel cmd send --local} and {@code camel transform}).
     */
    boolean keepsPreviousLog() {
        return spec != null && !transformRun && !transformMessageRun && !scriptRun;
    }

    private String loadFromCode(String code, String name, boolean file) throws IOException {
        String fn = CommandLineHelper.CAMEL_JBANG_WORK_DIR + "/" + name + ".java";
        if (!file) {
            // need to replace single quote as double quotes (from input string)
            code = code.replace("'", "\"");
            code = code.trim();
        }
        // ensure the code ends with semicolon to finish the java statement
        if (!code.endsWith(";")) {
            code = code + ";";
        }
        Map<String, Object> model = new HashMap<>();
        model.put("Name", name);
        model.put("Code", code);
        String content = TemplateHelper.processTemplate("code-java.ftl", model);
        Files.writeString(Paths.get(fn), content);
        return "file:" + fn;
    }

    private String loadFromClipboard(String file) throws UnsupportedFlavorException, IOException {
        // run from clipboard (not real file exists)
        String ext = FileUtil.onlyExt(file, true);
        if (ext == null || ext.isEmpty()) {
            throw new IllegalArgumentException(
                    "When running from clipboard, an extension is required to let Camel know what kind of file to use");
        }
        Clipboard c = Toolkit.getDefaultToolkit().getSystemClipboard();
        Object t = c.getData(DataFlavor.stringFlavor);
        if (t != null) {
            String fn = workDir + "/" + CLIPBOARD_GENERATED_FILE + "." + ext;
            if ("java".equals(ext)) {
                String fqn = determineClassName(t.toString());
                if (fqn == null) {
                    // wrap code in wrapper
                    return loadFromCode(t.toString(), "ClipboardRoute", true);
                }
                // drop package in file name
                String cn = fqn;
                if (fqn.contains(".")) {
                    cn = cn.substring(cn.lastIndexOf('.') + 1);
                }
                fn = cn + ".java";
            }
            Files.writeString(Paths.get(fn), t.toString());
            file = "file:" + fn;
        }
        return file;
    }

    private boolean isLoggingLevelFromCommandLine() {
        try {
            var parsed = spec != null && spec.commandLine() != null ? spec.commandLine().getParseResult() : null;
            return parsed != null && parsed.hasMatchedOption("--logging-level");
        } catch (Exception e) {
            return false;
        }
    }

    private void configureLogging(Properties prop) throws Exception {
        if (loggingOptions.logging) {
            // allow to configure individual logging levels in application.properties
            if (prop != null) {
                for (Object obj : prop.keySet()) {
                    String key = obj.toString();
                    String value = prop.getProperty(key);
                    if (key.startsWith("logging.level.")) {
                        key = key.substring(14);
                    } else if ("quarkus.log.level".equals(key)) {
                        key = "root";
                    } else if (key.startsWith("quarkus.log.category.")) {
                        key = key.substring(21);
                        if (key.endsWith(".level")) {
                            key = key.substring(0, key.length() - 6);
                        }
                    } else {
                        continue;
                    }
                    key = StringHelper.removeLeadingAndEndingQuotes(key);
                    if ("root".equalsIgnoreCase(key)) {
                        // logging.level.root (Spring Boot style) and quarkus.log.level are the root logging level,
                        // not a logger category (log4j allows one root logger only), so use it as the level unless
                        // --logging-level was given explicitly
                        if (!isLoggingLevelFromCommandLine()) {
                            loggingOptions.loggingLevel = value;
                        }
                        continue;
                    }
                    String line = key + "=" + value;
                    String line2 = key + " = " + value;
                    if (!loggingOptions.loggingCategory.contains(line)
                            && !loggingOptions.loggingCategory.contains(line2)) {
                        loggingOptions.loggingCategory.add(line);
                    }
                }
            }
            RuntimeUtil.configureLog(loggingOptions.loggingLevel, loggingOptions.loggingColor,
                    loggingOptions.loggingJson, scriptRun, false, loggingOptions.loggingConfigPath,
                    loggingOptions.loggingCategory, mcpStdioMode);
            writeSettings("loggingLevel", loggingOptions.loggingLevel);
            writeSettings("loggingColor", loggingOptions.loggingColor ? "true" : "false");
            writeSettings("loggingJson", loggingOptions.loggingJson ? "true" : "false");
            if (!scriptRun) {
                // remember log file
                String name = RuntimeUtil.getPid() + ".log";
                final Path logDir = CommandLineHelper.getCamelDir();
                Files.createDirectories(logDir); //make sure the parent dir exists
                logFile = logDir.resolve(name);
                try {
                    // Create an empty file that will be deleted on exit
                    Files.createFile(logFile);
                    logFile.toFile().deleteOnExit();
                } catch (IOException e) {
                    // Ignore
                }
            }
        } else {
            RuntimeUtil.configureLog("off", false, false, false, false, null, null);
            writeSettings("loggingLevel", "off");
        }
    }

    static final int MAX_RESOURCE_DIR_FILES = ExportHelper.MAX_RESOURCE_DIR_FILES;

    static Path validateResourceDir(String dir) {
        return ExportHelper.validateResourceDir(dir);
    }

    static List<Path> walkResourceDir(Path dirPath) throws IOException {
        return ExportHelper.walkResourceDir(dirPath);
    }

    private String openApiGeneratedFile() {
        return RunPipeline.openApiGeneratedFile(workDir);
    }

    private void writeSettings(String key, String value) {
        new RunSettings(workDir).write(key, value);
    }

    protected static void removeDir(Path directory) {
        RunPipeline.removeDir(directory);
    }

    private static String determineClassName(String content) {
        Matcher matcher = PACKAGE_PATTERN.matcher(content);
        String pn = matcher.find() ? matcher.group(1) : null;

        matcher = CLASS_PATTERN.matcher(content);
        String cn = matcher.find() ? matcher.group(1) : null;

        String fqn;
        if (pn != null) {
            fqn = pn + "." + cn;
        } else {
            fqn = cn;
        }
        return fqn;
    }

    static class LoggingOptions {
        @Option(names = { "--logging" }, defaultValue = "true", arity = "0..1", fallbackValue = "true",
                description = "Can be used to turn off logging")
        boolean logging = true;

        @Option(names = { "--logging-level" }, completionCandidates = LoggingLevelCompletionCandidates.class,
                defaultValue = "info", description = "Logging level (${COMPLETION-CANDIDATES})")
        String loggingLevel;

        @Option(names = { "--logging-color" }, arity = "0..1", fallbackValue = "true",
                description = "Use colored logging. Default is auto-detected based on NO_COLOR, CI, FORCE_COLOR environment variables and terminal capabilities")
        boolean loggingColor = EnvironmentHelper.isColorEnabled();

        @Option(names = { "--logging-json" }, defaultValue = "false", description = "Use JSON logging (ECS Layout)")
        boolean loggingJson;

        @Option(names = { "--logging-config-path" }, description = "Path to file with custom logging configuration")
        String loggingConfigPath;

        @Option(names = { "--logging-category" },
                description = "Used for individual logging levels (ex: org.apache.kafka=DEBUG)")
        List<String> loggingCategory = new ArrayList<>();
    }

    static class DebugOptions {
        @Option(names = { "--jfr" }, defaultValue = "false",
                description = "Enables Java Flight Recorder, saving a recording to <name>.jfr on exit. "
                              + "Automatically adds JFR support for the runtime and turns on the Camel runtime "
                              + "instrumentation events (route, processor, exchange, etc.).")
        boolean jfr;

        @Option(names = { "--jfr-profile" },
                description = "Java Flight Recorder settings profile to use (such as default or profile). Implies --jfr.")
        String jfrProfile;

        @Option(names = { "--trace" }, defaultValue = "false",
                description = "Enables trace logging of the routed messages")
        boolean trace;

        @Option(names = { "--backlog-trace" }, defaultValue = "false",
                description = "Enables backlog tracing of the routed messages")
        boolean backlogTrace;

        @Option(names = { "--open-telemetry-agent" }, defaultValue = "false",
                description = "Enable OpenTelemetry Java Agent for auto-instrumentation of third-party libraries (HTTP clients, JDBC, Kafka clients, gRPC, etc.). "
                              + "Traces are shown in the TUI Spans tab with Camel spans in cyan and 3rd-party agent spans in magenta.")
        boolean openTelemetryAgent;

        @Option(names = { "--open-telemetry-agent-export" }, defaultValue = "tui",
                description = "Where to export OpenTelemetry Agent telemetry: tui (embedded receiver in TUI), "
                              + "otlp (external OTLP collector for traces), or observability (full stack with traces and logs). "
                              + "With otlp, start a trace backend first (e.g. 'camel infra run jaeger'). "
                              + "With observability, start the stack via 'camel infra run observability'. "
                              + "The value 'jaeger' is deprecated and works as an alias for 'otlp'.")
        String openTelemetryAgentExport = "tui";
    }

    public static class ExecutionLimitOptions {
        @Option(names = { "--max-messages" }, defaultValue = "0",
                description = "Max number of messages to process before stopping")
        int maxMessages;

        @Option(names = { "--max-seconds" }, defaultValue = "0", description = "Max seconds to run before stopping")
        public int maxSeconds;

        @Option(names = { "--max-idle-seconds" }, defaultValue = "0",
                description = "For how long time in seconds Camel can be idle before stopping")
        int maxIdleSeconds;
    }

    static class ServerOptions {
        @Option(names = { "--port" },
                description = "Embeds a local HTTP server on this port (port 8080 by default; use 0 to dynamic assign a free random port number)")
        int port = -1;

        @Option(names = { "--management-port" },
                description = "To use a dedicated port for HTTP management (use 0 to dynamic assign a free random port number)")
        int managementPort = -1;

        @Option(names = { "--console" }, defaultValue = "false",
                description = "Developer console on the local HTTP server (port 8080 by default): /q/dev with the jbang"
                              + " and Camel Main runtimes, /actuator/camel with Spring Boot, and /q/camel/dev-console"
                              + " with Quarkus")
        boolean console;

        @Option(names = { "--mcp" }, defaultValue = "false",
                description = "Embed dev/diagnostics MCP tools on the local HTTP management server (/mcp by default). "
                              + "When management shares the main HTTP port, MCP is served on the main server bind address. "
                              + "Also binds the management server to 127.0.0.1 (affecting health/metrics when --observe is used).")
        boolean mcp;

        @Option(names = { "--mcp-stdio" }, defaultValue = "false",
                description = "Expose tagged ai-tool routes as MCP tools over process stdin/stdout for IDE subprocess "
                              + "integration (no HTTP port). Requires --mcp-tags or camel.server.mcp-tags. "
                              + "Logging and startup summaries are routed to stderr so stdout carries MCP protocol only.")
        boolean mcpStdio;

        @Option(names = { "--mcp-tags" },
                description = "Comma-separated ai-tool tags to expose on the ai-tool MCP server (maps to "
                              + "camel.server.mcp-tags). Use with --mcp-stdio or when camel.server.mcp-enabled is "
                              + "configured for HTTP transport. Not used by --mcp (dev/diagnostics MCP).")
        String mcpTags;

        @Option(names = { "--openapi-ui" }, defaultValue = "false",
                description = "Swagger UI for REST OpenAPI at /q/openapi (OpenAPI document at /q/openapi.json; port 8080 by default)")
        boolean openapiUi;

        @Deprecated
        @Option(names = { "--health" }, defaultValue = "false",
                description = "Deprecated: use --observe instead. Health check at /q/health on local HTTP server (port 8080 by default)")
        boolean health;

        @Deprecated
        @Option(names = { "--metrics" }, defaultValue = "false",
                description = "Deprecated: use --observe instead. Metrics (Micrometer and Prometheus) at /q/metrics on local HTTP server (port 8080 by default)")
        boolean metrics;

        @Option(names = { "--observe" }, defaultValue = "false",
                description = "Enable observability services (health, metrics, dev console, and lightweight Camel-only tracing in the TUI Spans tab)")
        boolean observe;
    }

    static class FilesConsumer extends ParameterConsumer<Run> {
        @Override
        protected void doConsumeParameters(Stack<String> args, Run cmd) {
            String arg = args.pop();
            cmd.files.add(arg);
        }
    }

    static class DebugConsumer extends ParameterConsumer<Run> {
        private static final Pattern DEBUG_ARG_VALUE_PATTERN = Pattern.compile("\\d+|true|false");

        @Override
        protected void doConsumeParameters(Stack<String> args, Run cmd) {
            String arg = args.isEmpty() ? "" : args.peek();
            if (DEBUG_ARG_VALUE_PATTERN.asPredicate().test(arg)) {
                // The value matches with the expected format so let's assume that it is a debug argument value
                args.pop();
            } else {
                // Here we assume that the value is not a debug argument value so let's simply enable the debug mode
                arg = "true";
            }
            cmd.jvmDebugPort = parseJvmDebugPort(arg);
        }

        @Override
        protected boolean failIfEmptyArgs() {
            return false;
        }
    }

    private JsonObject loadStatus(long pid) {
        try {
            Path p = getStatusFile(Long.toString(pid));
            if (Files.exists(p)) {
                try (InputStream is = Files.newInputStream(p)) {
                    String text = IOHelper.loadText(is);
                    return (JsonObject) Jsoner.deserialize(text);
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    @Override
    protected Printer printer() {
        if (mcpStdioMode) {
            // MCP stdio mode must not write diagnostics to stdout
            if (quietPrinter == null) {
                quietPrinter = new Printer.QuietPrinter(super.printer());
            }

            CommandHelper.setPrinter(quietPrinter);
            return quietPrinter;
        }

        return super.printer();
    }

}
