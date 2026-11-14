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
import org.apache.camel.dsl.jbang.core.commands.bind.Bind;
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

    public static void run(String... args) {
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
        if (shouldCreate(only, "bind")) {
            commandLine.addSubcommand("bind", new CommandTree(new Bind(this)));
        }
        if (shouldCreate(only, "catalog")) {
            commandLine.addSubcommand("catalog", new CommandTree(new CatalogCommand(this))
                    .addSubcommand("component", new CommandTree(new CatalogComponent(this)))
                    .addSubcommand("dataformat", new CommandTree(new CatalogDataFormat(this)))
                    .addSubcommand("dev-console", new CommandTree(new CatalogDevConsole(this)))
                    .addSubcommand("kamelet", new CommandTree(new CatalogKamelet(this)))
                    .addSubcommand("transformer", new CommandTree(new CatalogTransformer(this)))
                    .addSubcommand("language", new CommandTree(new CatalogLanguage(this)))
                    .addSubcommand("other", new CommandTree(new CatalogOther(this))));
        }
        if (shouldCreate(only, "cmd")) {
            commandLine.addSubcommand("cmd", new CommandTree(new CamelAction(this))
                    .addSubcommand("browse", new CommandTree(new CamelBrowseAction(this)))
                    .addSubcommand("chaos-add", new CommandTree(new CamelChaosAddAction(this)))
                    .addSubcommand("chaos-change", new CommandTree(new CamelChaosChangeAction(this)))
                    .addSubcommand("chaos-clear", new CommandTree(new CamelChaosClearAction(this)))
                    .addSubcommand("chaos-experiment", new CommandTree(new CamelChaosExperimentAction(this)))
                    .addSubcommand("chaos-explore", new CommandTree(new ChaosExplore(this)))
                    .addSubcommand("chaos-explore-finish", new CommandTree(new CamelChaosExploreFinishAction(this)))
                    .addSubcommand("chaos-explore-start", new CommandTree(new CamelChaosExploreStartAction(this)))
                    .addSubcommand("chaos-load", new CommandTree(new CamelChaosLoadAction(this)))
                    .addSubcommand("chaos-remove", new CommandTree(new CamelChaosRemoveAction(this)))
                    .addSubcommand("chaos-suggest", new CommandTree(new CamelChaosSuggestAction(this)))
                    .addSubcommand("disable-processor", new CommandTree(new CamelProcessorDisableAction(this)))
                    .addSubcommand("enable-processor", new CommandTree(new CamelProcessorEnableAction(this)))
                    .addSubcommand("gc", new CommandTree(new CamelGCAction(this)))
                    .addSubcommand("heap-dump", new CommandTree(new CamelHeapDump(this)))
                    .addSubcommand("heap-histogram", new CommandTree(new CamelHeapHistogram(this)))
                    .addSubcommand("memory-leak", new CommandTree(new CamelMemoryLeak(this)))
                    .addSubcommand("load", new CommandTree(new CamelLoadAction(this)))
                    .addSubcommand("logger", new CommandTree(new LoggerAction(this)))
                    .addSubcommand("receive", new CommandTree(new CamelReceiveAction(this)))
                    .addSubcommand("reload", new CommandTree(new CamelReloadAction(this)))
                    .addSubcommand("reset-stats", new CommandTree(new CamelResetStatsAction(this)))
                    .addSubcommand("resume-route", new CommandTree(new CamelRouteResumeAction(this)))
                    .addSubcommand("route-diagram", new CommandTree(new CamelRouteDiagramAction(this)))
                    .addSubcommand("route-dump", new CommandTree(new CamelRouteDumpAction(this)))
                    .addSubcommand("route-structure", new CommandTree(new CamelRouteStructureAction(this)))
                    .addSubcommand("route-topology", new CommandTree(new CamelRouteTopologyAction(this)))
                    .addSubcommand("send", new CommandTree(new CamelSendAction(this)))
                    .addSubcommand("sql", new CommandTree(new CamelSqlQueryAction(this)))
                    .addSubcommand("span", new CommandTree(new CamelSpanAction(this)))
                    .addSubcommand("start-group", new CommandTree(new CamelRouteGroupStartAction(this)))
                    .addSubcommand("start-route", new CommandTree(new CamelRouteStartAction(this)))
                    .addSubcommand("stop-group", new CommandTree(new CamelRouteGroupStopAction(this)))
                    .addSubcommand("stop-route", new CommandTree(new CamelRouteStopAction(this)))
                    .addSubcommand("stub", new CommandTree(new CamelStubAction(this)))
                    .addSubcommand("suspend-route", new CommandTree(new CamelRouteSuspendAction(this)))
                    .addSubcommand("thread-dump", new CommandTree(new CamelThreadDump(this))));
        }
        if (shouldCreate(only, "config")) {
            commandLine.addSubcommand("config", new CommandTree(new ConfigCommand(this))
                    .addSubcommand("get", new CommandTree(new ConfigGet(this)))
                    .addSubcommand("list", new CommandTree(new ConfigList(this)))
                    .addSubcommand("set", new CommandTree(new ConfigSet(this)))
                    .addSubcommand("unset", new CommandTree(new ConfigUnset(this))));
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
                    .addSubcommand("copy", new CommandTree(new DependencyCopy(this)))
                    .addSubcommand("list", new CommandTree(new DependencyList(this)))
                    .addSubcommand("runtime", new CommandTree(new DependencyRuntime(this)))
                    .addSubcommand("update", new CommandTree(new DependencyUpdate(this))));
        }
        if (shouldCreate(only, "dirty")) {
            commandLine.addSubcommand("dirty", new CommandTree(new Dirty(this)));
        }
        if (shouldCreate(only, "doctor")) {
            commandLine.addSubcommand("doctor", new CommandTree(new Doctor(this)));
        }
        if (shouldCreate(only, "eval")) {
            commandLine.addSubcommand("eval", new CommandTree(new EvalCommand(this))
                    .addSubcommand("expression", new CommandTree(new EvalExpressionCommand(this))));
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
                    .addSubcommand("bean", new CommandTree(new CamelBeanDump(this)))
                    .addSubcommand("blocked", new CommandTree(new ListBlocked(this)))
                    .addSubcommand("chaos", new CommandTree(new ListChaos(this)))
                    .addSubcommand("circuit-breaker", new CommandTree(new ListCircuitBreaker(this)))
                    .addSubcommand("consumer", new CommandTree(new ListConsumer(this)))
                    .addSubcommand("context", new CommandTree(new CamelContextStatus(this)))
                    .addSubcommand("count", new CommandTree(new CamelCount(this)))
                    .addSubcommand("datasource", new CommandTree(new ListDataSource(this)))
                    .addSubcommand("endpoint", new CommandTree(new ListEndpoint(this)))
                    .addSubcommand("error", new CommandTree(new ListError(this)))
                    .addSubcommand("event", new CommandTree(new ListEvent(this)))
                    .addSubcommand("groovy", new CommandTree(new ListGroovy(this)))
                    .addSubcommand("group", new CommandTree(new CamelRouteGroupStatus(this)))
                    .addSubcommand("health", new CommandTree(new ListHealth(this)))
                    .addSubcommand("history", new CommandTree(new CamelHistoryAction(this)))
                    .addSubcommand("inflight", new CommandTree(new ListInflight(this)))
                    .addSubcommand("internal-task", new CommandTree(new ListInternalTask(this)))
                    .addSubcommand("kafka", new CommandTree(new ListKafka(this)))
                    .addSubcommand("metric", new CommandTree(new ListMetric(this)))
                    .addSubcommand("platform-http", new CommandTree(new ListPlatformHttp(this)))
                    .addSubcommand("processor", new CommandTree(new CamelProcessorStatus(this)))
                    .addSubcommand("producer", new CommandTree(new ListProducer(this)))
                    .addSubcommand("properties", new CommandTree(new ListProperties(this)))
                    .addSubcommand("rest", new CommandTree(new ListRest(this)))
                    .addSubcommand("route", new CommandTree(new CamelRouteStatus(this)))
                    .addSubcommand("route-controller", new CommandTree(new RouteControllerAction(this)))
                    .addSubcommand("sanitizer", new CommandTree(new ListSanitizer(this)))
                    .addSubcommand("service", new CommandTree(new ListService(this)))
                    .addSubcommand("source", new CommandTree(new CamelSourceAction(this)))
                    .addSubcommand("sql-trace", new CommandTree(new ListSqlTrace(this)))
                    .addSubcommand("startup-recorder", new CommandTree(new CamelStartupRecorderAction(this)))
                    .addSubcommand("timeline", new CommandTree(new ListTimeline(this)))
                    .addSubcommand("about", new CommandTree(new ListAbout(this)))
                    .addSubcommand("transformer", new CommandTree(new ListTransformer(this)))
                    .addSubcommand("variable", new CommandTree(new ListVariable(this)))
                    .addSubcommand("vault", new CommandTree(new ListVault(this))));
        }
        if (shouldCreate(only, "hawtio")) {
            commandLine.addSubcommand("hawtio", new CommandTree(new Hawtio(this)));
        }
        if (shouldCreate(only, "infra")) {
            commandLine.addSubcommand("infra", new CommandTree(new InfraCommand(this))
                    .addSubcommand("get", new CommandTree(new InfraGet(this)))
                    .addSubcommand("list", new CommandTree(new InfraList(this)))
                    .addSubcommand("log", new CommandTree(new InfraLog(this)))
                    .addSubcommand("ps", new CommandTree(new InfraPs(this)))
                    .addSubcommand("restart", new CommandTree(new InfraRestart(this)))
                    .addSubcommand("run", new CommandTree(new InfraRun(this)))
                    .addSubcommand("stop", new CommandTree(new InfraStop(this))));
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
                    .addSubcommand("add", new CommandTree(new PluginAdd(this)))
                    .addSubcommand("delete", new CommandTree(new PluginDelete(this)))
                    .addSubcommand("get", new CommandTree(new PluginGet(this)))
                    .addSubcommand("list", new CommandTree(new PluginList(this))));
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
                    .addSubcommand("get", new CommandTree(new SemanticGet(this)))
                    .addSubcommand("eval", new CommandTree(new SemanticEvaluate(this)))
                    .addSubcommand("audit", new CommandTree(new SemanticAudit(this)))
                    .addSubcommand("monitoring", new CommandTree(new SemanticMonitoring(this))));
        }
        if (shouldCreate(only, "shell")) {
            commandLine.addSubcommand("shell", new CommandTree(new Shell(this)));
        }
        if (shouldCreate(only, "stop")) {
            commandLine.addSubcommand("stop", new CommandTree(new StopProcess(this)));
        }
        if (shouldCreate(only, "top")) {
            commandLine.addSubcommand("top", new CommandTree(new CamelTop(this))
                    .addSubcommand("context", new CommandTree(new CamelContextTop(this)))
                    .addSubcommand("group", new CommandTree(new CamelRouteGroupTop(this)))
                    .addSubcommand("processor", new CommandTree(new CamelProcessorTop(this)))
                    .addSubcommand("route", new CommandTree(new CamelRouteTop(this)))
                    .addSubcommand("source", new CommandTree(new CamelSourceTop(this))));
        }
        if (shouldCreate(only, "trace")) {
            commandLine.addSubcommand("trace", new CommandTree(new CamelTraceAction(this)));
        }
        if (shouldCreate(only, "transform")) {
            commandLine.addSubcommand("transform", new CommandTree(new TransformCommand(this))
                    .addSubcommand("dataweave", new CommandTree(new TransformDataWeave(this)))
                    .addSubcommand("message", new CommandTree(new TransformMessageAction(this)))
                    .addSubcommand("route", new CommandTree(new TransformRoute(this))));
        }
        if (shouldCreate(only, "update")) {
            commandLine.addSubcommand("update", new CommandTree(new UpdateCommand(this))
                    .addSubcommand("list", new CommandTree(new UpdateList(this)))
                    .addSubcommand("run", new CommandTree(new UpdateRun(this))));
        }
        if (shouldCreate(only, "version")) {
            commandLine.addSubcommand("version", new CommandTree(new VersionCommand(this))
                    .addSubcommand("get", new CommandTree(new VersionGet(this)))
                    .addSubcommand("list", new CommandTree(new VersionList(this)))
                    .addSubcommand("set", new CommandTree(new VersionSet(this))));
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
     * Finish this main with given exit code. By default, uses system exit to terminate. Subclasses may want to
     * overwrite this exit behavior e.g. during unit tests.
     */
    public void quit(int exitCode) {
        System.exit(exitCode);
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
        // check if the plugin banner is disabled via global config
        boolean[] enabled = { true };
        CommandLineHelper.loadProperties(properties -> {
            String v = properties.getProperty("camel.jbang.plugin.banner");
            if ("false".equalsIgnoreCase(v)) {
                enabled[0] = false;
            }
        });
        if (!enabled[0]) {
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
