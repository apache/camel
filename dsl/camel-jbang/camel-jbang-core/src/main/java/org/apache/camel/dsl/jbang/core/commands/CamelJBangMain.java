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
import org.aesh.command.invocation.CommandInvocation;
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

    public void execute(String... args) {
        // set pid as system property as logging ${sys:pid} needs to be resolved on windows
        try {
            long pid = ProcessHandle.current().pid();
            System.setProperty("pid", Long.toString(pid));
        } catch (Exception e) {
            // ignore
        }

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
            commandLine.addSubcommand("catalog", new CommandTree(new CatalogCommand(this))
                    .addSubcommand("component", CommandTree.lazy(() -> new CatalogComponent(this)))
                    .addSubcommand("dataformat", CommandTree.lazy(() -> new CatalogDataFormat(this)))
                    .addSubcommand("dev-console", CommandTree.lazy(() -> new CatalogDevConsole(this)))
                    .addSubcommand("kamelet", CommandTree.lazy(() -> new CatalogKamelet(this)))
                    .addSubcommand("transformer", CommandTree.lazy(() -> new CatalogTransformer(this)))
                    .addSubcommand("language", CommandTree.lazy(() -> new CatalogLanguage(this)))
                    .addSubcommand("other", CommandTree.lazy(() -> new CatalogOther(this))));
        }
        if (shouldCreate(only, "cmd")) {
            commandLine.addSubcommand("cmd", new CommandTree(new CamelAction(this))
                    .addSubcommand("browse", CommandTree.lazy(() -> new CamelBrowseAction(this)))
                    .addSubcommand("chaos-add", CommandTree.lazy(() -> new CamelChaosAddAction(this)))
                    .addSubcommand("chaos-change", CommandTree.lazy(() -> new CamelChaosChangeAction(this)))
                    .addSubcommand("chaos-clear", CommandTree.lazy(() -> new CamelChaosClearAction(this)))
                    .addSubcommand("chaos-experiment", CommandTree.lazy(() -> new CamelChaosExperimentAction(this)))
                    .addSubcommand("chaos-explore", CommandTree.lazy(() -> new ChaosExplore(this)))
                    .addSubcommand("chaos-explore-finish", CommandTree.lazy(() -> new CamelChaosExploreFinishAction(this)))
                    .addSubcommand("chaos-explore-start", CommandTree.lazy(() -> new CamelChaosExploreStartAction(this)))
                    .addSubcommand("chaos-load", CommandTree.lazy(() -> new CamelChaosLoadAction(this)))
                    .addSubcommand("chaos-remove", CommandTree.lazy(() -> new CamelChaosRemoveAction(this)))
                    .addSubcommand("chaos-suggest", CommandTree.lazy(() -> new CamelChaosSuggestAction(this)))
                    .addSubcommand("disable-processor", CommandTree.lazy(() -> new CamelProcessorDisableAction(this)))
                    .addSubcommand("enable-processor", CommandTree.lazy(() -> new CamelProcessorEnableAction(this)))
                    .addSubcommand("gc", CommandTree.lazy(() -> new CamelGCAction(this)))
                    .addSubcommand("heap-dump", CommandTree.lazy(() -> new CamelHeapDump(this)))
                    .addSubcommand("heap-histogram", CommandTree.lazy(() -> new CamelHeapHistogram(this)))
                    .addSubcommand("memory-leak", CommandTree.lazy(() -> new CamelMemoryLeak(this)))
                    .addSubcommand("load", CommandTree.lazy(() -> new CamelLoadAction(this)))
                    .addSubcommand("logger", CommandTree.lazy(() -> new LoggerAction(this)))
                    .addSubcommand("receive", CommandTree.lazy(() -> new CamelReceiveAction(this)))
                    .addSubcommand("reload", CommandTree.lazy(() -> new CamelReloadAction(this)))
                    .addSubcommand("reset-stats", CommandTree.lazy(() -> new CamelResetStatsAction(this)))
                    .addSubcommand("resume-route", CommandTree.lazy(() -> new CamelRouteResumeAction(this)))
                    .addSubcommand("route-diagram", CommandTree.lazy(() -> new CamelRouteDiagramAction(this)))
                    .addSubcommand("route-dump", CommandTree.lazy(() -> new CamelRouteDumpAction(this)))
                    .addSubcommand("route-structure", CommandTree.lazy(() -> new CamelRouteStructureAction(this)))
                    .addSubcommand("route-topology", CommandTree.lazy(() -> new CamelRouteTopologyAction(this)))
                    .addSubcommand("send", CommandTree.lazy(() -> new CamelSendAction(this)))
                    .addSubcommand("sql", CommandTree.lazy(() -> new CamelSqlQueryAction(this)))
                    .addSubcommand("span", CommandTree.lazy(() -> new CamelSpanAction(this)))
                    .addSubcommand("start-group", CommandTree.lazy(() -> new CamelRouteGroupStartAction(this)))
                    .addSubcommand("start-route", CommandTree.lazy(() -> new CamelRouteStartAction(this)))
                    .addSubcommand("stop-group", CommandTree.lazy(() -> new CamelRouteGroupStopAction(this)))
                    .addSubcommand("stop-route", CommandTree.lazy(() -> new CamelRouteStopAction(this)))
                    .addSubcommand("stub", CommandTree.lazy(() -> new CamelStubAction(this)))
                    .addSubcommand("suspend-route", CommandTree.lazy(() -> new CamelRouteSuspendAction(this)))
                    .addSubcommand("thread-dump", CommandTree.lazy(() -> new CamelThreadDump(this))));
        }
        if (shouldCreate(only, "config")) {
            commandLine.addSubcommand("config", new CommandTree(new ConfigCommand(this))
                    .addSubcommand("get", CommandTree.lazy(() -> new ConfigGet(this)))
                    .addSubcommand("list", CommandTree.lazy(() -> new ConfigList(this)))
                    .addSubcommand("set", CommandTree.lazy(() -> new ConfigSet(this)))
                    .addSubcommand("unset", CommandTree.lazy(() -> new ConfigUnset(this))));
        }
        if (shouldCreate(only, "completion")) {
            commandLine.addSubcommand("completion", new CommandTree(new Complete(this)));
        }
        if (shouldCreate(only, "doc")) {
            commandLine.addSubcommand("doc", new CommandTree(new CatalogDoc(this)));
        }
        if (shouldCreate(only, "debug")) {
            commandLine.addSubcommand("debug", new CommandTree(new Debug(this)));
        }
        if (shouldCreate(only, "dev")) {
            commandLine.addSubcommand("dev", new CommandTree(new Dev(this)));
        }
        if (shouldCreate(only, "dependency")) {
            commandLine.addSubcommand("dependency", new CommandTree(new DependencyCommand(this))
                    .addSubcommand("copy", CommandTree.lazy(() -> new DependencyCopy(this)))
                    .addSubcommand("list", CommandTree.lazy(() -> new DependencyList(this)))
                    .addSubcommand("runtime", CommandTree.lazy(() -> new DependencyRuntime(this)))
                    .addSubcommand("update", CommandTree.lazy(() -> new DependencyUpdate(this))));
        }
        if (shouldCreate(only, "dirty")) {
            commandLine.addSubcommand("dirty", new CommandTree(new Dirty(this)));
        }
        if (shouldCreate(only, "doctor")) {
            commandLine.addSubcommand("doctor", new CommandTree(new Doctor(this)));
        }
        if (shouldCreate(only, "eval")) {
            commandLine.addSubcommand("eval", new CommandTree(new EvalCommand(this))
                    .addSubcommand("expression", CommandTree.lazy(() -> new EvalExpressionCommand(this))));
        }
        if (shouldCreate(only, "export")) {
            commandLine.addSubcommand("export", new CommandTree(new Export(this)));
        }
        if (shouldCreate(only, "explain")) {
            commandLine.addSubcommand("explain", new CommandTree(new Explain(this)));
        }
        if (shouldCreate(only, "overview")) {
            commandLine.addSubcommand("overview", new CommandTree(new Overview(this)));
        }
        if (shouldCreate(only, "ask")) {
            commandLine.addSubcommand("ask", new CommandTree(new Ask(this)));
        }
        if (shouldCreate(only, "harden")) {
            commandLine.addSubcommand("harden", new CommandTree(new Harden(this)));
        }
        if (shouldCreate(only, "status")) {
            commandLine.addSubcommand("status", new CommandTree(new IntegrationStatus(this)));
        }
        if (shouldCreate(only, "get")) {
            commandLine.addSubcommand("get", new CommandTree(new CamelStatus(this))
                    .addSubcommand("bean", CommandTree.lazy(() -> new CamelBeanDump(this)))
                    .addSubcommand("blocked", CommandTree.lazy(() -> new ListBlocked(this)))
                    .addSubcommand("chaos", CommandTree.lazy(() -> new ListChaos(this)))
                    .addSubcommand("circuit-breaker", CommandTree.lazy(() -> new ListCircuitBreaker(this)))
                    .addSubcommand("consumer", CommandTree.lazy(() -> new ListConsumer(this)))
                    .addSubcommand("context", CommandTree.lazy(() -> new CamelContextStatus(this)))
                    .addSubcommand("count", CommandTree.lazy(() -> new CamelCount(this)))
                    .addSubcommand("datasource", CommandTree.lazy(() -> new ListDataSource(this)))
                    .addSubcommand("endpoint", CommandTree.lazy(() -> new ListEndpoint(this)))
                    .addSubcommand("error", CommandTree.lazy(() -> new ListError(this)))
                    .addSubcommand("event", CommandTree.lazy(() -> new ListEvent(this)))
                    .addSubcommand("groovy", CommandTree.lazy(() -> new ListGroovy(this)))
                    .addSubcommand("group", CommandTree.lazy(() -> new CamelRouteGroupStatus(this)))
                    .addSubcommand("health", CommandTree.lazy(() -> new ListHealth(this)))
                    .addSubcommand("history", CommandTree.lazy(() -> new CamelHistoryAction(this)))
                    .addSubcommand("inflight", CommandTree.lazy(() -> new ListInflight(this)))
                    .addSubcommand("internal-task", CommandTree.lazy(() -> new ListInternalTask(this)))
                    .addSubcommand("kafka", CommandTree.lazy(() -> new ListKafka(this)))
                    .addSubcommand("metric", CommandTree.lazy(() -> new ListMetric(this)))
                    .addSubcommand("platform-http", CommandTree.lazy(() -> new ListPlatformHttp(this)))
                    .addSubcommand("processor", CommandTree.lazy(() -> new CamelProcessorStatus(this)))
                    .addSubcommand("producer", CommandTree.lazy(() -> new ListProducer(this)))
                    .addSubcommand("properties", CommandTree.lazy(() -> new ListProperties(this)))
                    .addSubcommand("rest", CommandTree.lazy(() -> new ListRest(this)))
                    .addSubcommand("route", CommandTree.lazy(() -> new CamelRouteStatus(this)))
                    .addSubcommand("route-controller", CommandTree.lazy(() -> new RouteControllerAction(this)))
                    .addSubcommand("sanitizer", CommandTree.lazy(() -> new ListSanitizer(this)))
                    .addSubcommand("service", CommandTree.lazy(() -> new ListService(this)))
                    .addSubcommand("source", CommandTree.lazy(() -> new CamelSourceAction(this)))
                    .addSubcommand("sql-trace", CommandTree.lazy(() -> new ListSqlTrace(this)))
                    .addSubcommand("startup-recorder", CommandTree.lazy(() -> new CamelStartupRecorderAction(this)))
                    .addSubcommand("timeline", CommandTree.lazy(() -> new ListTimeline(this)))
                    .addSubcommand("about", CommandTree.lazy(() -> new ListAbout(this)))
                    .addSubcommand("transformer", CommandTree.lazy(() -> new ListTransformer(this)))
                    .addSubcommand("variable", CommandTree.lazy(() -> new ListVariable(this)))
                    .addSubcommand("vault", CommandTree.lazy(() -> new ListVault(this))));
        }
        if (shouldCreate(only, "hawtio")) {
            commandLine.addSubcommand("hawtio", new CommandTree(new Hawtio(this)));
        }
        if (shouldCreate(only, "infra")) {
            commandLine.addSubcommand("infra", new CommandTree(new InfraCommand(this))
                    .addSubcommand("get", CommandTree.lazy(() -> new InfraGet(this)))
                    .addSubcommand("list", CommandTree.lazy(() -> new InfraList(this)))
                    .addSubcommand("log", CommandTree.lazy(() -> new InfraLog(this)))
                    .addSubcommand("ps", CommandTree.lazy(() -> new InfraPs(this)))
                    .addSubcommand("restart", CommandTree.lazy(() -> new InfraRestart(this)))
                    .addSubcommand("run", CommandTree.lazy(() -> new InfraRun(this)))
                    .addSubcommand("stop", CommandTree.lazy(() -> new InfraStop(this))));
        }
        if (shouldCreate(only, "init")) {
            commandLine.addSubcommand("init", new CommandTree(new Init(this)));
        }
        if (shouldCreate(only, "jolokia")) {
            commandLine.addSubcommand("jolokia", new CommandTree(new Jolokia(this)));
        }
        if (shouldCreate(only, "log")) {
            commandLine.addSubcommand("log", new CommandTree(new CamelLogAction(this)));
        }
        if (shouldCreate(only, "nano")) {
            commandLine.addSubcommand("nano", new CommandTree(new Nano(this)));
        }
        if (shouldCreate(only, "plugin")) {
            commandLine.addSubcommand("plugin", new CommandTree(new PluginCommand(this))
                    .addSubcommand("add", CommandTree.lazy(() -> new PluginAdd(this)))
                    .addSubcommand("delete", CommandTree.lazy(() -> new PluginDelete(this)))
                    .addSubcommand("get", CommandTree.lazy(() -> new PluginGet(this)))
                    .addSubcommand("list", CommandTree.lazy(() -> new PluginList(this))));
        }
        if (shouldCreate(only, "ps")) {
            commandLine.addSubcommand("ps", new CommandTree(new ListProcess(this)));
        }
        if (shouldCreate(only, "restart")) {
            commandLine.addSubcommand("restart", new CommandTree(new RestartProcess(this)));
        }
        if (shouldCreate(only, "run")) {
            commandLine.addSubcommand("run", new CommandTree(new Run(this)));
        }
        if (shouldCreate(only, "sbom")) {
            commandLine.addSubcommand("sbom", new CommandTree(new SBOMGenerator(this)));
        }
        if (shouldCreate(only, "script")) {
            commandLine.addSubcommand("script", new CommandTree(new Script(this)));
        }
        if (shouldCreate(only, "semantic")) {
            commandLine.addSubcommand("semantic", new CommandTree(new CamelSemantic(this))
                    .addSubcommand("get", CommandTree.lazy(() -> new SemanticGet(this)))
                    .addSubcommand("eval", CommandTree.lazy(() -> new SemanticEvaluate(this)))
                    .addSubcommand("audit", CommandTree.lazy(() -> new SemanticAudit(this)))
                    .addSubcommand("monitoring", CommandTree.lazy(() -> new SemanticMonitoring(this))));
        }
        if (shouldCreate(only, "shell")) {
            commandLine.addSubcommand("shell", new CommandTree(new Shell(this)));
        }
        if (shouldCreate(only, "stop")) {
            commandLine.addSubcommand("stop", new CommandTree(new StopProcess(this)));
        }
        if (shouldCreate(only, "top")) {
            commandLine.addSubcommand("top", new CommandTree(new CamelTop(this))
                    .addSubcommand("context", CommandTree.lazy(() -> new CamelContextTop(this)))
                    .addSubcommand("group", CommandTree.lazy(() -> new CamelRouteGroupTop(this)))
                    .addSubcommand("processor", CommandTree.lazy(() -> new CamelProcessorTop(this)))
                    .addSubcommand("route", CommandTree.lazy(() -> new CamelRouteTop(this)))
                    .addSubcommand("source", CommandTree.lazy(() -> new CamelSourceTop(this))));
        }
        if (shouldCreate(only, "trace")) {
            commandLine.addSubcommand("trace", new CommandTree(new CamelTraceAction(this)));
        }
        if (shouldCreate(only, "transform")) {
            commandLine.addSubcommand("transform", new CommandTree(new TransformCommand(this))
                    .addSubcommand("dataweave", CommandTree.lazy(() -> new TransformDataWeave(this)))
                    .addSubcommand("message", CommandTree.lazy(() -> new TransformMessageAction(this)))
                    .addSubcommand("route", CommandTree.lazy(() -> new TransformRoute(this))));
        }
        if (shouldCreate(only, "update")) {
            commandLine.addSubcommand("update", new CommandTree(new UpdateCommand(this))
                    .addSubcommand("list", CommandTree.lazy(() -> new UpdateList(this)))
                    .addSubcommand("run", CommandTree.lazy(() -> new UpdateRun(this))));
        }
        if (shouldCreate(only, "version")) {
            commandLine.addSubcommand("version", new CommandTree(new VersionCommand(this))
                    .addSubcommand("get", CommandTree.lazy(() -> new VersionGet(this)))
                    .addSubcommand("list", CommandTree.lazy(() -> new VersionList(this)))
                    .addSubcommand("set", CommandTree.lazy(() -> new VersionSet(this))));
        }
        if (shouldCreate(only, "wrapper")) {
            commandLine.addSubcommand("wrapper", new CommandTree(new WrapperCommand(this)));
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

    public static CommandTree getCommandLine() {
        return commandLine;
    }
}
