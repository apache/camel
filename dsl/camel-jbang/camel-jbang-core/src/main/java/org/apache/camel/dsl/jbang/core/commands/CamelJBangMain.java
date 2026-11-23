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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.impl.parser.ArgfileExpander;
import org.aesh.command.internal.InternalCommandMetadataRegistry;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.metadata.MetadataProviderRegistry;
import org.aesh.command.option.Option;
import org.aesh.command.parser.CommandLineParserException;
import org.aesh.command.validator.OptionValidatorException;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.action.*;
import org.apache.camel.dsl.jbang.core.commands.catalog.CatalogCommand;
import org.apache.camel.dsl.jbang.core.commands.catalog.CatalogComponent;
import org.apache.camel.dsl.jbang.core.commands.catalog.CatalogDataFormat;
import org.apache.camel.dsl.jbang.core.commands.catalog.CatalogDevConsole;
import org.apache.camel.dsl.jbang.core.commands.catalog.CatalogDoc;
import org.apache.camel.dsl.jbang.core.commands.catalog.CatalogKamelet;
import org.apache.camel.dsl.jbang.core.commands.catalog.CatalogLanguage;
import org.apache.camel.dsl.jbang.core.commands.catalog.CatalogOther;
import org.apache.camel.dsl.jbang.core.commands.catalog.CatalogTransformer;
import org.apache.camel.dsl.jbang.core.commands.config.ConfigCommand;
import org.apache.camel.dsl.jbang.core.commands.config.ConfigGet;
import org.apache.camel.dsl.jbang.core.commands.config.ConfigList;
import org.apache.camel.dsl.jbang.core.commands.config.ConfigSet;
import org.apache.camel.dsl.jbang.core.commands.config.ConfigUnset;
import org.apache.camel.dsl.jbang.core.commands.exceptionhandler.CliExecutionExceptionHandler;
import org.apache.camel.dsl.jbang.core.commands.exceptionhandler.MissingPluginParameterExceptionHandler;
import org.apache.camel.dsl.jbang.core.commands.infra.InfraCommand;
import org.apache.camel.dsl.jbang.core.commands.infra.InfraGet;
import org.apache.camel.dsl.jbang.core.commands.infra.InfraList;
import org.apache.camel.dsl.jbang.core.commands.infra.InfraLog;
import org.apache.camel.dsl.jbang.core.commands.infra.InfraPs;
import org.apache.camel.dsl.jbang.core.commands.infra.InfraRestart;
import org.apache.camel.dsl.jbang.core.commands.infra.InfraRun;
import org.apache.camel.dsl.jbang.core.commands.infra.InfraStop;
import org.apache.camel.dsl.jbang.core.commands.plugin.PluginAdd;
import org.apache.camel.dsl.jbang.core.commands.plugin.PluginCommand;
import org.apache.camel.dsl.jbang.core.commands.plugin.PluginDelete;
import org.apache.camel.dsl.jbang.core.commands.plugin.PluginGet;
import org.apache.camel.dsl.jbang.core.commands.plugin.PluginList;
import org.apache.camel.dsl.jbang.core.commands.process.*;
import org.apache.camel.dsl.jbang.core.commands.update.UpdateCommand;
import org.apache.camel.dsl.jbang.core.commands.update.UpdateList;
import org.apache.camel.dsl.jbang.core.commands.update.UpdateRun;
import org.apache.camel.dsl.jbang.core.commands.version.VersionCommand;
import org.apache.camel.dsl.jbang.core.commands.version.VersionGet;
import org.apache.camel.dsl.jbang.core.commands.version.VersionList;
import org.apache.camel.dsl.jbang.core.commands.version.VersionSet;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.CommandMetadataRegistry;
import org.apache.camel.dsl.jbang.core.common.CommandTree;
import org.apache.camel.dsl.jbang.core.common.EnvironmentHelper;
import org.apache.camel.dsl.jbang.core.common.JsonOutput;
import org.apache.camel.dsl.jbang.core.common.Plugin;
import org.apache.camel.dsl.jbang.core.common.PluginHelper;
import org.apache.camel.dsl.jbang.core.common.PluginType;
import org.apache.camel.dsl.jbang.run.common.CliLogging;
import org.apache.camel.dsl.jbang.run.common.Printer;
import org.apache.camel.util.json.JsonObject;

@CommandDefinition(name = "camel", description = "Apache Camel CLI", generateHelp = true, sortOptions = true,
                   footer = {
                           "Exit Codes:",
                           "    0   Success",
                           "    1   Failed: the command ran, but the outcome is negative",
                           "    2   Usage error: invalid option or argument",
                           "    3   Not found: no such integration, route or service",
                           "   66   File not found: no such file or directory",
                           "   70   Internal error: an unexpected exception",
                           "  124   Timed out: no reply from the integration in time" })
public class CamelJBangMain implements Command<CommandInvocation> {
    // the commands that use the other commands of the CLI, besides the ones that use the plugins (which may add to any
    // command, see PluginHelper.isPluginConsumingCommand): the shell and the completion script
    private static final Set<String> FULL_COMMANDS = Set.of("shell", "completion");

    private static CommandTree commandLine;

    /**
     * The aesh metadata of the commands of the CLI, which the aesh annotation processor generates: the packages of all
     * the commands of camel-jbang-core. Export is extended by KubernetesExport, which has options of its own (and its
     * super classes have no metadata, so they are not looked up).
     */
    static final CommandMetadataRegistry COMMAND_METADATA = new CommandMetadataRegistry(
            CamelJBangMain.class.getClassLoader(),
            Set.of("org.apache.camel.dsl.jbang.core.commands",
                    "org.apache.camel.dsl.jbang.core.commands.action",
                    "org.apache.camel.dsl.jbang.core.commands.catalog",
                    "org.apache.camel.dsl.jbang.core.commands.config",
                    "org.apache.camel.dsl.jbang.core.commands.infra",
                    "org.apache.camel.dsl.jbang.core.commands.plugin",
                    "org.apache.camel.dsl.jbang.core.commands.process",
                    "org.apache.camel.dsl.jbang.core.commands.update",
                    "org.apache.camel.dsl.jbang.core.commands.version"),
            Set.of("org.apache.camel.dsl.jbang.core.commands.Export",
                    "org.apache.camel.dsl.jbang.core.commands.ExportBaseCommand",
                    "org.apache.camel.dsl.jbang.core.commands.CamelCommand"));

    @Option(name = "version", shortName = 'V', hasValue = false, description = "Print version information and exit")
    boolean versionRequested;

    private Printer out = new Printer.SystemOutPrinter();

    private boolean discoverPlugins = true;

    // the plugins whose commands were added, to add them to a new command line too
    private final List<Plugin> plugins = new CopyOnWriteArrayList<>();

    /**
     * The main method of the CLI that JBang runs (camel-jbang-main/dist/CamelJBang.java).
     */
    public static void run(String... args) {
        // before anything creates a logger
        CliLogging.install();
        run(new CamelJBangMain(), args);
    }

    public static void run(CamelJBangMain main, String... args) {
        main.execute(args);
    }

    /**
     * Tells aesh to use the metadata of the commands that is generated at compile time, and not to look through every
     * JAR on the class path for other registries of it.
     */
    public static void useCommandMetadata() {
        MetadataProviderRegistry.useOnly(new InternalCommandMetadataRegistry(), COMMAND_METADATA);
    }

    public void execute(String... args) {
        // set pid as system property as logging ${sys:pid} needs to be resolved on windows
        try {
            long pid = ProcessHandle.current().pid();
            System.setProperty("pid", Long.toString(pid));
        } catch (Exception e) {
            // ignore
        }

        useCommandMetadata();

        args = expandArgumentFiles(args);
        if (args == null) {
            quit(CliExitCode.USAGE);
            return;
        }

        // the CLI creates only the command that the command line names, as creating all of them (and loading their
        // classes) costs more than most commands take to run
        String only = onlyCommand(args);
        commandLine = createCommandLine(only);
        boolean pluginCommand = false;
        if (only != null && !commandLine.getSubcommands().containsKey(only)) {
            // the command of a plugin needs none of the commands of the CLI, anything else (such as a typo, for which
            // aesh suggests other commands) needs all of them
            pluginCommand = isPluginCommand(only);
            if (!pluginCommand) {
                commandLine = createCommandLine(null);
            }
        }

        postAddCommands(commandLine, args);

        if (discoverPlugins && PluginHelper.shouldDiscoverPlugins(commandLine, args)) {
            try {
                PluginHelper.addPlugins(commandLine, this, args);
                PluginHelper.installOnFirstUse(commandLine, this, args);
            } catch (CliException e) {
                // a plugin that cannot be loaded
                out.printErr(e.getMessage());
                quit(e.getExitCode());
                return;
            }
        }
        if (pluginCommand && !commandLine.getSubcommands().containsKey(only)) {
            // no plugin added the command: all the commands of the CLI, for the commands that aesh suggests
            commandLine = createCommandLine(null);
            postAddCommands(commandLine, args);
        }
        withErrorHandling(commandLine);

        CommandLineHelper.migrateLegacyUserConfig(out);
        CommandLineHelper.augmentWithUserConfiguration(commandLine, out);
        preExecute(commandLine, args);
        int exitCode = run(commandLine, args);
        if (isHelpRequest(args) && EnvironmentHelper.isInteractiveTerminal()) {
            printAvailablePlugins();
        }
        postExecute(commandLine, args, exitCode);
        quit(exitCode);
    }

    /**
     * Replaces an argument {@code @file} with the arguments in that file, as picocli did: one or more per line, quotes
     * group words, a line that starts with {@code #} is a comment, and {@code @@} is a literal {@code @}. This is done
     * before anything reads the command line, so the command it names is created, and a command that runs the CLI again
     * (such as {@code camel run --background}) has the arguments themselves.
     *
     * @param  args the command line
     * @return      the command line with the arguments of the files, or {@code null} when a file cannot be read (the
     *              error is printed)
     */
    String[] expandArgumentFiles(String... args) {
        try {
            return ArgfileExpander.expand(args);
        } catch (CommandLineParserException e) {
            out.printErr(e.getMessage());
            if (JsonOutput.isRequested(args)) {
                JsonOutput.printError(out, CliExitCode.USAGE, e.getMessage());
            }
            return null;
        }
    }

    /**
     * The command that a command line runs, when the CLI needs to create only that command (of the CLI, or of a
     * plugin): not for {@code camel} and a command line that starts with an option (such as {@code --help}), which list
     * all commands, or the commands that use the other commands ({@link #FULL_COMMANDS}) or the plugins
     * ({@link PluginHelper#isPluginConsumingCommand(String)}).
     *
     * @param  args the command line
     * @return      the name of the command, or {@code null} to create all commands
     */
    static String onlyCommand(String... args) {
        if (args == null || args.length == 0) {
            return null;
        }
        String target = args[0];
        if (target == null || target.isBlank() || target.startsWith("-") || FULL_COMMANDS.contains(target)
                || PluginHelper.isPluginConsumingCommand(target)) {
            return null;
        }
        return target;
    }

    /**
     * Whether the command line has only the command of the CLI that it runs (see {@link #onlyCommand(String...)}), and
     * not all the commands, so the commands of the plugins are not needed either.
     *
     * @param  commandLine the command line
     * @param  args        the arguments it runs
     * @return             {@code true} when it has only that command
     */
    public static boolean hasOnlyCommand(CommandTree commandLine, String... args) {
        String only = onlyCommand(args);
        return only != null && commandLine.getSubcommands().size() == 1 && commandLine.getSubcommands().containsKey(only);
    }

    /**
     * The command of the plugin that the command line runs, when the CLI created none of its own commands for it (the
     * command is not a command of the CLI, but of a plugin, see {@link #isPluginCommand(String)}), so the command line
     * needs only that plugin.
     *
     * @param  commandLine the command line
     * @param  args        the arguments it runs
     * @return             the name of the command, or {@code null} when the command line needs all plugins
     */
    public static String onlyPluginCommand(CommandTree commandLine, String... args) {
        String only = onlyCommand(args);
        return only != null && commandLine.getSubcommands().isEmpty() ? only : null;
    }

    /**
     * Whether the command is the command of a plugin, which needs none of the commands of the CLI.
     *
     * @param  command the name of the command
     * @return         {@code true} for the command of a plugin that the CLI can add
     */
    protected boolean isPluginCommand(String command) {
        return discoverPlugins && PluginHelper.isPluginCommand(command);
    }

    private static boolean shouldCreate(String only, String name) {
        return only == null || only.equals(name);
    }

    /**
     * The command line with the commands of the CLI.
     *
     * @param only the name of the one command to create, or {@code null} for all commands
     */
    CommandTree createCommandLine(String only) {
        // the help of --help is printed with the printer of the CLI too
        CommandTree commandLine = new CommandTree(this).setOutput(text -> out.print(text));
        if (shouldCreate(only, "catalog")) {
            commandLine.addSubcommand("catalog", new CommandTree(cmd(new CatalogCommand()))
                    .addSubcommand("component", CommandTree.lazy(() -> cmd(new CatalogComponent())))
                    .addSubcommand("dataformat", CommandTree.lazy(() -> cmd(new CatalogDataFormat())))
                    .addSubcommand("dev-console", CommandTree.lazy(() -> cmd(new CatalogDevConsole())))
                    .addSubcommand("kamelet", CommandTree.lazy(() -> cmd(new CatalogKamelet())))
                    .addSubcommand("transformer", CommandTree.lazy(() -> cmd(new CatalogTransformer())))
                    .addSubcommand("language", CommandTree.lazy(() -> cmd(new CatalogLanguage())))
                    .addSubcommand("other", CommandTree.lazy(() -> cmd(new CatalogOther()))));
        }
        if (shouldCreate(only, "cmd")) {
            commandLine.addSubcommand("cmd", new CommandTree(cmd(new CamelAction()))
                    .addSubcommand("browse", CommandTree.lazy(() -> cmd(new CamelBrowseAction())))
                    .addSubcommand("chaos-add", CommandTree.lazy(() -> cmd(new CamelChaosAddAction())))
                    .addSubcommand("chaos-change", CommandTree.lazy(() -> cmd(new CamelChaosChangeAction())))
                    .addSubcommand("chaos-clear", CommandTree.lazy(() -> cmd(new CamelChaosClearAction())))
                    .addSubcommand("chaos-experiment",
                            CommandTree.lazy(() -> cmd(new CamelChaosExperimentAction())))
                    .addSubcommand("chaos-explore", CommandTree.lazy(() -> cmd(new ChaosExplore())))
                    .addSubcommand("chaos-explore-finish",
                            CommandTree.lazy(() -> cmd(new CamelChaosExploreFinishAction())))
                    .addSubcommand("chaos-explore-start",
                            CommandTree.lazy(() -> cmd(new CamelChaosExploreStartAction())))
                    .addSubcommand("chaos-load", CommandTree.lazy(() -> cmd(new CamelChaosLoadAction())))
                    .addSubcommand("chaos-remove", CommandTree.lazy(() -> cmd(new CamelChaosRemoveAction())))
                    .addSubcommand("chaos-suggest", CommandTree.lazy(() -> cmd(new CamelChaosSuggestAction())))
                    .addSubcommand("disable-processor",
                            CommandTree.lazy(() -> cmd(new CamelProcessorDisableAction())))
                    .addSubcommand("enable-processor",
                            CommandTree.lazy(() -> cmd(new CamelProcessorEnableAction())))
                    .addSubcommand("gc", CommandTree.lazy(() -> cmd(new CamelGCAction())))
                    .addSubcommand("heap-dump", CommandTree.lazy(() -> cmd(new CamelHeapDump())))
                    .addSubcommand("heap-histogram", CommandTree.lazy(() -> cmd(new CamelHeapHistogram())))
                    .addSubcommand("memory-leak", CommandTree.lazy(() -> cmd(new CamelMemoryLeak())))
                    .addSubcommand("load", CommandTree.lazy(() -> cmd(new CamelLoadAction())))
                    .addSubcommand("logger", CommandTree.lazy(() -> cmd(new LoggerAction())))
                    .addSubcommand("receive", CommandTree.lazy(() -> cmd(new CamelReceiveAction())))
                    .addSubcommand("reload", CommandTree.lazy(() -> cmd(new CamelReloadAction())))
                    .addSubcommand("reset-stats", CommandTree.lazy(() -> cmd(new CamelResetStatsAction())))
                    .addSubcommand("resume-route", CommandTree.lazy(() -> cmd(new CamelRouteResumeAction())))
                    .addSubcommand("route-diagram", CommandTree.lazy(() -> cmd(new CamelRouteDiagramAction())))
                    .addSubcommand("route-dump", CommandTree.lazy(() -> cmd(new CamelRouteDumpAction())))
                    .addSubcommand("route-structure",
                            CommandTree.lazy(() -> cmd(new CamelRouteStructureAction())))
                    .addSubcommand("route-topology", CommandTree.lazy(() -> cmd(new CamelRouteTopologyAction())))
                    .addSubcommand("send", CommandTree.lazy(() -> cmd(new CamelSendAction())))
                    .addSubcommand("sql", CommandTree.lazy(() -> cmd(new CamelSqlQueryAction())))
                    .addSubcommand("span", CommandTree.lazy(() -> cmd(new CamelSpanAction())))
                    .addSubcommand("start-group", CommandTree.lazy(() -> cmd(new CamelRouteGroupStartAction())))
                    .addSubcommand("start-route", CommandTree.lazy(() -> cmd(new CamelRouteStartAction())))
                    .addSubcommand("stop-group", CommandTree.lazy(() -> cmd(new CamelRouteGroupStopAction())))
                    .addSubcommand("stop-route", CommandTree.lazy(() -> cmd(new CamelRouteStopAction())))
                    .addSubcommand("stub", CommandTree.lazy(() -> cmd(new CamelStubAction())))
                    .addSubcommand("suspend-route", CommandTree.lazy(() -> cmd(new CamelRouteSuspendAction())))
                    .addSubcommand("thread-dump", CommandTree.lazy(() -> cmd(new CamelThreadDump()))));
        }
        if (shouldCreate(only, "config")) {
            commandLine.addSubcommand("config", new CommandTree(cmd(new ConfigCommand()))
                    .addSubcommand("get", CommandTree.lazy(() -> cmd(new ConfigGet())))
                    .addSubcommand("list", CommandTree.lazy(() -> cmd(new ConfigList())))
                    .addSubcommand("set", CommandTree.lazy(() -> cmd(new ConfigSet())))
                    .addSubcommand("unset", CommandTree.lazy(() -> cmd(new ConfigUnset()))));
        }
        if (shouldCreate(only, "completion")) {
            commandLine.addSubcommand("completion", new CommandTree(cmd(new Complete())));
        }
        if (shouldCreate(only, "doc")) {
            commandLine.addSubcommand("doc", new CommandTree(cmd(new CatalogDoc())));
        }
        if (shouldCreate(only, "debug")) {
            commandLine.addSubcommand("debug", new CommandTree(cmd(new Debug())));
        }
        if (shouldCreate(only, "dev")) {
            commandLine.addSubcommand("dev", new CommandTree(cmd(new Dev())));
        }
        if (shouldCreate(only, "dependency")) {
            commandLine.addSubcommand("dependency", new CommandTree(cmd(new DependencyCommand()))
                    .addSubcommand("audit", CommandTree.lazy(() -> cmd(new DependencyAudit())))
                    .addSubcommand("copy", CommandTree.lazy(() -> cmd(new DependencyCopy())))
                    .addSubcommand("list", CommandTree.lazy(() -> cmd(new DependencyList())))
                    .addSubcommand("outdated", CommandTree.lazy(() -> cmd(new DependencyOutdated())))
                    .addSubcommand("runtime", CommandTree.lazy(() -> cmd(new DependencyRuntime())))
                    .addSubcommand("update", CommandTree.lazy(() -> cmd(new DependencyUpdate()))));
        }
        if (shouldCreate(only, "dirty")) {
            commandLine.addSubcommand("dirty", new CommandTree(cmd(new Dirty())));
        }
        if (shouldCreate(only, "doctor")) {
            commandLine.addSubcommand("doctor", new CommandTree(cmd(new Doctor())));
        }
        if (shouldCreate(only, "eval")) {
            commandLine.addSubcommand("eval", new CommandTree(cmd(new EvalCommand()))
                    .addSubcommand("expression", CommandTree.lazy(() -> cmd(new EvalExpressionCommand()))));
        }
        if (shouldCreate(only, "export")) {
            commandLine.addSubcommand("export", new CommandTree(cmd(new Export())));
        }
        if (shouldCreate(only, "explain")) {
            commandLine.addSubcommand("explain", new CommandTree(cmd(new Explain())));
        }
        if (shouldCreate(only, "overview")) {
            commandLine.addSubcommand("overview", new CommandTree(cmd(new Overview())));
        }
        if (shouldCreate(only, "ask")) {
            commandLine.addSubcommand("ask", new CommandTree(cmd(new Ask())));
        }
        if (shouldCreate(only, "harden")) {
            commandLine.addSubcommand("harden", new CommandTree(cmd(new Harden())));
        }
        if (shouldCreate(only, "status")) {
            commandLine.addSubcommand("status", new CommandTree(cmd(new IntegrationStatus())));
        }
        if (shouldCreate(only, "get")) {
            commandLine.addSubcommand("get", new CommandTree(cmd(new CamelStatus()))
                    .addSubcommand("bean", CommandTree.lazy(() -> cmd(new CamelBeanDump())))
                    .addSubcommand("blocked", CommandTree.lazy(() -> cmd(new ListBlocked())))
                    .addSubcommand("chaos", CommandTree.lazy(() -> cmd(new ListChaos())))
                    .addSubcommand("circuit-breaker", CommandTree.lazy(() -> cmd(new ListCircuitBreaker())))
                    .addSubcommand("consumer", CommandTree.lazy(() -> cmd(new ListConsumer())))
                    .addSubcommand("context", CommandTree.lazy(() -> cmd(new CamelContextStatus())))
                    .addSubcommand("count", CommandTree.lazy(() -> cmd(new CamelCount())))
                    .addSubcommand("datasource", CommandTree.lazy(() -> cmd(new ListDataSource())))
                    .addSubcommand("endpoint", CommandTree.lazy(() -> cmd(new ListEndpoint())))
                    .addSubcommand("error", CommandTree.lazy(() -> cmd(new ListError())))
                    .addSubcommand("event", CommandTree.lazy(() -> cmd(new ListEvent())))
                    .addSubcommand("genai", CommandTree.lazy(() -> cmd(new CamelGenAiAction())))
                    .addSubcommand("groovy", CommandTree.lazy(() -> cmd(new ListGroovy())))
                    .addSubcommand("group", CommandTree.lazy(() -> cmd(new CamelRouteGroupStatus())))
                    .addSubcommand("health", CommandTree.lazy(() -> cmd(new ListHealth())))
                    .addSubcommand("history", CommandTree.lazy(() -> cmd(new CamelHistoryAction())))
                    .addSubcommand("inflight", CommandTree.lazy(() -> cmd(new ListInflight())))
                    .addSubcommand("internal-task", CommandTree.lazy(() -> cmd(new ListInternalTask())))
                    .addSubcommand("kafka", CommandTree.lazy(() -> cmd(new ListKafka())))
                    .addSubcommand("metric", CommandTree.lazy(() -> cmd(new ListMetric())))
                    .addSubcommand("platform-http", CommandTree.lazy(() -> cmd(new ListPlatformHttp())))
                    .addSubcommand("processor", CommandTree.lazy(() -> cmd(new CamelProcessorStatus())))
                    .addSubcommand("producer", CommandTree.lazy(() -> cmd(new ListProducer())))
                    .addSubcommand("properties", CommandTree.lazy(() -> cmd(new ListProperties())))
                    .addSubcommand("rest", CommandTree.lazy(() -> cmd(new ListRest())))
                    .addSubcommand("route", CommandTree.lazy(() -> cmd(new CamelRouteStatus())))
                    .addSubcommand("route-controller", CommandTree.lazy(() -> cmd(new RouteControllerAction())))
                    .addSubcommand("sanitizer", CommandTree.lazy(() -> cmd(new ListSanitizer())))
                    .addSubcommand("service", CommandTree.lazy(() -> cmd(new ListService())))
                    .addSubcommand("source", CommandTree.lazy(() -> cmd(new CamelSourceAction())))
                    .addSubcommand("sql-trace", CommandTree.lazy(() -> cmd(new ListSqlTrace())))
                    .addSubcommand("startup-recorder",
                            CommandTree.lazy(() -> cmd(new CamelStartupRecorderAction())))
                    .addSubcommand("timeline", CommandTree.lazy(() -> cmd(new ListTimeline())))
                    .addSubcommand("about", CommandTree.lazy(() -> cmd(new ListAbout())))
                    .addSubcommand("transformer", CommandTree.lazy(() -> cmd(new ListTransformer())))
                    .addSubcommand("variable", CommandTree.lazy(() -> cmd(new ListVariable())))
                    .addSubcommand("vault", CommandTree.lazy(() -> cmd(new ListVault()))));
        }
        if (shouldCreate(only, "hawtio")) {
            commandLine.addSubcommand("hawtio", new CommandTree(cmd(new Hawtio())));
        }
        if (shouldCreate(only, "infra")) {
            commandLine.addSubcommand("infra", new CommandTree(cmd(new InfraCommand()))
                    .addSubcommand("get", CommandTree.lazy(() -> cmd(new InfraGet())))
                    .addSubcommand("list", CommandTree.lazy(() -> cmd(new InfraList())))
                    .addSubcommand("log", CommandTree.lazy(() -> cmd(new InfraLog())))
                    .addSubcommand("ps", CommandTree.lazy(() -> cmd(new InfraPs())))
                    .addSubcommand("restart", CommandTree.lazy(() -> cmd(new InfraRestart())))
                    .addSubcommand("run", CommandTree.lazy(() -> cmd(new InfraRun())))
                    .addSubcommand("stop", CommandTree.lazy(() -> cmd(new InfraStop()))));
        }
        if (shouldCreate(only, "init")) {
            commandLine.addSubcommand("init", new CommandTree(cmd(new Init())));
        }
        if (shouldCreate(only, "jolokia")) {
            commandLine.addSubcommand("jolokia", new CommandTree(cmd(new Jolokia())));
        }
        if (shouldCreate(only, "log")) {
            commandLine.addSubcommand("log", new CommandTree(cmd(new CamelLogAction())));
        }
        if (shouldCreate(only, "nano")) {
            commandLine.addSubcommand("nano", new CommandTree(cmd(new Nano())));
        }
        if (shouldCreate(only, "plugin")) {
            commandLine.addSubcommand("plugin", new CommandTree(cmd(new PluginCommand()))
                    .addSubcommand("add", CommandTree.lazy(() -> cmd(new PluginAdd())))
                    .addSubcommand("delete", CommandTree.lazy(() -> cmd(new PluginDelete())))
                    .addSubcommand("get", CommandTree.lazy(() -> cmd(new PluginGet())))
                    .addSubcommand("list", CommandTree.lazy(() -> cmd(new PluginList()))));
        }
        if (shouldCreate(only, "ps")) {
            commandLine.addSubcommand("ps", new CommandTree(cmd(new ListProcess())));
        }
        if (shouldCreate(only, "restart")) {
            commandLine.addSubcommand("restart", new CommandTree(cmd(new RestartProcess())));
        }
        if (shouldCreate(only, "run")) {
            commandLine.addSubcommand("run", new CommandTree(cmd(new Run())));
        }
        if (shouldCreate(only, "sbom")) {
            commandLine.addSubcommand("sbom", new CommandTree(cmd(new SBOMGenerator())));
        }
        if (shouldCreate(only, "script")) {
            commandLine.addSubcommand("script", new CommandTree(cmd(new Script())));
        }
        if (shouldCreate(only, "semantic")) {
            commandLine.addSubcommand("semantic", new CommandTree(cmd(new CamelSemantic()))
                    .addSubcommand("get", CommandTree.lazy(() -> cmd(new SemanticGet())))
                    .addSubcommand("eval", CommandTree.lazy(() -> cmd(new SemanticEvaluate())))
                    .addSubcommand("audit", CommandTree.lazy(() -> cmd(new SemanticAudit())))
                    .addSubcommand("monitoring", CommandTree.lazy(() -> cmd(new SemanticMonitoring()))));
        }
        if (shouldCreate(only, "shell")) {
            commandLine.addSubcommand("shell", new CommandTree(cmd(new Shell())));
        }
        if (shouldCreate(only, "stop")) {
            commandLine.addSubcommand("stop", new CommandTree(cmd(new StopProcess())));
        }
        if (shouldCreate(only, "top")) {
            commandLine.addSubcommand("top", new CommandTree(cmd(new CamelTop()))
                    .addSubcommand("context", CommandTree.lazy(() -> cmd(new CamelContextTop())))
                    .addSubcommand("group", CommandTree.lazy(() -> cmd(new CamelRouteGroupTop())))
                    .addSubcommand("processor", CommandTree.lazy(() -> cmd(new CamelProcessorTop())))
                    .addSubcommand("route", CommandTree.lazy(() -> cmd(new CamelRouteTop())))
                    .addSubcommand("source", CommandTree.lazy(() -> cmd(new CamelSourceTop()))));
        }
        if (shouldCreate(only, "trace")) {
            commandLine.addSubcommand("trace", new CommandTree(cmd(new CamelTraceAction())));
        }
        if (shouldCreate(only, "transform")) {
            commandLine.addSubcommand("transform", new CommandTree(cmd(new TransformCommand()))
                    .addSubcommand("dataweave", CommandTree.lazy(() -> cmd(new TransformDataWeave())))
                    .addSubcommand("message", CommandTree.lazy(() -> cmd(new TransformMessageAction())))
                    .addSubcommand("route", CommandTree.lazy(() -> cmd(new TransformRoute()))));
        }
        if (shouldCreate(only, "update")) {
            commandLine.addSubcommand("update", new CommandTree(cmd(new UpdateCommand()))
                    .addSubcommand("list", CommandTree.lazy(() -> cmd(new UpdateList())))
                    .addSubcommand("run", CommandTree.lazy(() -> cmd(new UpdateRun()))));
        }
        if (shouldCreate(only, "version")) {
            commandLine.addSubcommand("version", new CommandTree(cmd(new VersionCommand()))
                    .addSubcommand("get", CommandTree.lazy(() -> cmd(new VersionGet())))
                    .addSubcommand("list", CommandTree.lazy(() -> cmd(new VersionList())))
                    .addSubcommand("set", CommandTree.lazy(() -> cmd(new VersionSet()))));
        }
        if (shouldCreate(only, "wrapper")) {
            commandLine.addSubcommand("wrapper", new CommandTree(cmd(new WrapperCommand())));
        }
        GroupedCommandHelpRenderer.apply(commandLine);
        return commandLine;
    }

    /**
     * A new command line with new instances of the commands of the CLI, and of the commands of the plugins this main
     * has loaded. A command that runs on it starts from its defaults, not from what a run before left in its fields:
     * {@link CliRunner} runs each command on a new command line.
     */
    public CommandTree newCommandLine() {
        CommandTree answer = createCommandLine(null);
        postAddCommands(answer, new String[0]);
        for (Plugin plugin : plugins) {
            plugin.customize(answer, this);
        }
        withErrorHandling(answer);
        CommandLineHelper.augmentWithUserConfiguration(answer, out);
        return answer;
    }

    /**
     * Remembers a plugin whose commands were added to the command line, to add them to a new command line too (see
     * {@link #newCommandLine()}).
     */
    public void addPlugin(Plugin plugin) {
        if (!plugins.contains(plugin)) {
            plugins.add(plugin);
        }
    }

    /**
     * Installs the error handling of the Camel CLI on the command line and its sub-commands: an exception ends the
     * command with an {@code ERROR:} line on stderr, and an exit code from {@link CliExitCode}.
     *
     * @param  commandLine the command line
     * @return             the same command line
     */
    public CommandTree withErrorHandling(CommandTree commandLine) {
        // the commands handle their exceptions (CamelCommand), and execute(String...) the errors of the parser
        return commandLine;
    }

    /**
     * Runs the command line with the error handling of the CLI.
     *
     * @param  commandLine the commands
     * @param  args        the arguments
     * @return             the exit code
     */
    public int run(CommandTree commandLine, String... args) {
        try {
            return commandLine.execute(args).getResultValue();
        } catch (CommandLineParserException | OptionValidatorException e) {
            return new MissingPluginParameterExceptionHandler(this).handleParseException(e, args, commandLine);
        } catch (Exception | Error e) {
            // a command handles its own exceptions, so this is aesh, or an error (such as a class missing at runtime)
            return new CliExecutionExceptionHandler(this).handle(e, null);
        }
    }

    /**
     * Whether the command only parses its command line, and does not run (for the tests of the parsing).
     *
     * @param  command the command, with the values of the command line in its fields
     * @return         {@code true} to not run it
     */
    public boolean parseOnly(CamelCommand command) {
        return false;
    }

    /**
     * Called after default commands has been added
     */
    public void postAddCommands(CommandTree commandLine, String[] args) {
        // noop
    }

    /**
     * The plugins that this CLI has built in (the Camel launcher embeds some), and that contribute to the given
     * command, such as the test plugin, whose exporter adds the Citrus tests to camel export. The plugins of the plugin
     * configuration are not among them.
     *
     * @param  target the command, such as run or export
     * @return        the plugins, none by default
     */
    public Collection<Plugin> getEmbeddedPlugins(String target) {
        return List.of();
    }

    /**
     * Called just before the command line is executed
     */
    public void preExecute(CommandTree commandLine, String[] args) {
        // noop
    }

    /**
     * Called just after the command line is executed
     */
    public void postExecute(CommandTree commandLine, String[] args, int exitCode) {
        // noop
    }

    /**
     * Finish this main with given exit code. By default, uses system exit to terminate, except for exit code 0 when no
     * other thread keeps the JVM running: then this returns, and the JVM ends when the main method returns. Subclasses
     * may want to overwrite this exit behavior e.g. during unit tests.
     */
    public void quit(int exitCode) {
        if (exitCode == CliExitCode.OK
                && !hasOtherNonDaemonThread(Thread.currentThread(), Thread.getAllStackTraces().keySet())) {
            // The JVM then ends with exit code 0 and runs the shutdown hooks, as System.exit(0) does. Since JDK 21,
            // System.exit logs the exit through a System.Logger, and the first System.Logger makes the JDK look for a
            // LoggerFinder in every JAR of the class path: about 50 ms with the 340 JARs of the distribution.
            return;
        }
        System.exit(exitCode);
    }

    /**
     * Whether another thread than the given one keeps the JVM running (a live thread that is not a daemon).
     */
    static boolean hasOtherNonDaemonThread(Thread current, Collection<Thread> threads) {
        for (Thread thread : threads) {
            if (thread != current && thread.isAlive() && !thread.isDaemon()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public CommandResult execute(CommandInvocation invocation) {
        if (versionRequested) {
            out.println(new DefaultCamelCatalog().getCatalogVersion());
        } else {
            out.println(invocation.getHelpInfo());
        }
        return CommandResult.SUCCESS;
    }

    // camel and camel --help, which list all commands
    private static boolean isHelpRequest(String[] args) {
        return args == null || args.length == 0 || "--help".equals(args[0]) || "-h".equals(args[0]);
    }

    void printAvailablePlugins() {
        // check if the plugin banner is disabled via the user configuration
        String banner = CommandLineHelper.loadUserConfiguration(getOut()).getProperty("camel.jbang.plugin.banner");
        if ("false".equalsIgnoreCase(banner)) {
            return;
        }

        Set<String> installed = new HashSet<>();
        try {
            JsonObject config = PluginHelper.getPluginConfig();
            if (config != null) {
                JsonObject plugins = config.getMap("plugins");
                if (plugins != null) {
                    installed.addAll(plugins.keySet());
                }
            }
        } catch (Exception e) {
            // corrupt plugin config — don't crash the help/banner output
            PluginHelper.warnCorruptPluginConfig(out, e);
        }
        // also consider already registered subcommands as installed
        Set<String> registered = commandLine.getSubcommands().keySet();

        List<String[]> rows = new ArrayList<>();
        for (PluginType pt : PluginType.values()) {
            if (!installed.contains(pt.getName()) && !registered.contains(pt.getCommand())) {
                rows.add(new String[] { pt.getCommand(), pt.getDescription() });
            }
        }
        for (JsonObject kp : PluginHelper.loadKnownPlugins()) {
            String name = kp.getString("name");
            String command = kp.getStringOrDefault("command", name);
            if (!installed.contains(name) && !registered.contains(command)
                    && PluginType.findByName(name).isEmpty()) {
                rows.add(new String[] { command, kp.getStringOrDefault("description", "") });
            }
        }

        if (!rows.isEmpty()) {
            // not part of the help: the diagnostics channel (stderr) keeps it out of a captured help text
            int maxCmd = rows.stream().mapToInt(r -> r[0].length()).max().orElse(0);
            out.printDiagnostic("");
            out.printDiagnostic("Plugins (not installed):");
            for (String[] row : rows) {
                out.printDiagnostic(String.format("  %-" + maxCmd + "s   %s", row[0], row[1]));
            }
            out.printDiagnostic("");
            out.printDiagnostic("Tip: Install with: camel plugin add <name>");
            out.printDiagnostic("     Bundled plugins are auto-installed on first use.");
            out.printDiagnostic("     Turn off: camel config set camel.jbang.plugin.banner=false");
        }
    }

    /**
     * Gets the main output printer to write command output.
     *
     * @return the printer.
     */
    public Printer getOut() {
        return out;
    }

    /**
     * Sets the main output printer.
     *
     * @param out the printer to use for command output.
     */
    public void setOut(Printer out) {
        this.out = out;
    }

    public boolean isDiscoverPlugins() {
        return discoverPlugins;
    }

    /**
     * Should custom plugins be discovered and activated
     */
    public void setDiscoverPlugins(boolean discoverPlugins) {
        this.discoverPlugins = discoverPlugins;
    }

    /**
     * Uses this printer for writing command output.
     *
     * @param out to use with this main.
     */
    public CamelJBangMain withPrinter(Printer out) {
        this.out = out;
        return this;
    }

    /**
     * Sets this main on a new command, such as {@code main.command(new Run())}. The commands have a no-arg constructor,
     * which the generated aesh metadata of the commands needs, so the main is set after the command is created.
     *
     * @param  command the new command
     * @return         the command
     */
    public <T extends CamelCommand> T command(T command) {
        command.setCamelMain(this);
        return command;
    }

    /**
     * Sets this main on a new command of the command line. It takes and returns an Object, unlike
     * {@link #command(CamelCommand)}: the JVM then does not load the class of every command to verify this class (each
     * command class is loaded only when the command is created).
     */
    private Object cmd(Object command) {
        ((CamelCommand) command).setCamelMain(this);
        return command;
    }

    public static CommandTree getCommandLine() {
        return commandLine;
    }
}
