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

import java.util.concurrent.Callable;

import org.apache.camel.catalog.CamelCatalog;
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
import org.apache.camel.dsl.jbang.core.commands.exceptionhandler.MissingPluginParameterExceptionHandler;
import org.apache.camel.dsl.jbang.core.commands.infra.InfraCommand;
import org.apache.camel.dsl.jbang.core.commands.infra.InfraGet;
import org.apache.camel.dsl.jbang.core.commands.infra.InfraList;
import org.apache.camel.dsl.jbang.core.commands.infra.InfraLog;
import org.apache.camel.dsl.jbang.core.commands.infra.InfraPs;
import org.apache.camel.dsl.jbang.core.commands.infra.InfraRun;
import org.apache.camel.dsl.jbang.core.commands.infra.InfraStop;
import org.apache.camel.dsl.jbang.core.commands.plugin.PluginAdd;
import org.apache.camel.dsl.jbang.core.commands.plugin.PluginCommand;
import org.apache.camel.dsl.jbang.core.commands.plugin.PluginDelete;
import org.apache.camel.dsl.jbang.core.commands.plugin.PluginGet;
import org.apache.camel.dsl.jbang.core.commands.process.*;
import org.apache.camel.dsl.jbang.core.commands.update.UpdateCommand;
import org.apache.camel.dsl.jbang.core.commands.update.UpdateList;
import org.apache.camel.dsl.jbang.core.commands.update.UpdateRun;
import org.apache.camel.dsl.jbang.core.commands.version.VersionCommand;
import org.apache.camel.dsl.jbang.core.commands.version.VersionGet;
import org.apache.camel.dsl.jbang.core.commands.version.VersionList;
import org.apache.camel.dsl.jbang.core.commands.version.VersionSet;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.PluginHelper;
import org.apache.camel.dsl.jbang.core.common.Printer;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "camel", description = "Apache Camel CLI", mixinStandardHelpOptions = true)
public class CamelJBangMain implements Callable<Integer> {
    private static CommandLine commandLine;

    private Printer out = new Printer.SystemOutPrinter();

    private boolean discoverPlugins = true;

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

        commandLine = new CommandLine(this)
                .addSubcommand("bind", new CommandLine(new Bind(this)))
                .addSubcommand("catalog", new CommandLine(new CatalogCommand(this))
                        .addSubcommand("component", new CommandLine(new CatalogComponent(this)))
                        .addSubcommand("dataformat", new CommandLine(new CatalogDataFormat(this)))
                        .addSubcommand("dev-console", new CommandLine(new CatalogDevConsole(this)))
                        .addSubcommand("kamelet", new CommandLine(new CatalogKamelet(this)))
                        .addSubcommand("transformer", new CommandLine(new CatalogTransformer(this)))
                        .addSubcommand("language", new CommandLine(new CatalogLanguage(this)))
                        .addSubcommand("other", new CommandLine(new CatalogOther(this))))
                .addSubcommand("cmd", new CommandLine(new CamelAction(this))
                        .addSubcommand("browse", new CommandLine(new CamelBrowseAction(this)))
                        .addSubcommand("disable-processor", new CommandLine(new CamelProcessorDisableAction(this)))
                        .addSubcommand("enable-processor", new CommandLine(new CamelProcessorEnableAction(this)))
                        .addSubcommand("gc", new CommandLine(new CamelGCAction(this)))
                        .addSubcommand("load", new CommandLine(new CamelLoadAction(this)))
                        .addSubcommand("logger", new CommandLine(new LoggerAction(this)))
                        .addSubcommand("receive", new CommandLine(new CamelReceiveAction(this)))
                        .addSubcommand("reload", new CommandLine(new CamelReloadAction(this)))
                        .addSubcommand("reset-stats", new CommandLine(new CamelResetStatsAction(this)))
                        .addSubcommand("resume-route", new CommandLine(new CamelRouteResumeAction(this)))
                        .addSubcommand("route-diagram", new CommandLine(new CamelRouteDiagramAction(this)))
                        .addSubcommand("route-structure", new CommandLine(new CamelRouteStructureAction(this)))
                        .addSubcommand("send", new CommandLine(new CamelSendAction(this)))
                        .addSubcommand("start-group", new CommandLine(new CamelRouteGroupStartAction(this)))
                        .addSubcommand("start-route", new CommandLine(new CamelRouteStartAction(this)))
                        .addSubcommand("stop-group", new CommandLine(new CamelRouteGroupStopAction(this)))
                        .addSubcommand("stop-route", new CommandLine(new CamelRouteStopAction(this)))
                        .addSubcommand("stub", new CommandLine(new CamelStubAction(this)))
                        .addSubcommand("suspend-route", new CommandLine(new CamelRouteSuspendAction(this)))
                        .addSubcommand("thread-dump", new CommandLine(new CamelThreadDump(this))))
                .addSubcommand("config", new CommandLine(new ConfigCommand(this))
                        .addSubcommand("get", new CommandLine(new ConfigGet(this)))
                        .addSubcommand("list", new CommandLine(new ConfigList(this)))
                        .addSubcommand("set", new CommandLine(new ConfigSet(this)))
                        .addSubcommand("unset", new CommandLine(new ConfigUnset(this))))
                .addSubcommand("completion", new CommandLine(new Complete(this)))
                .addSubcommand("doc", new CommandLine(new CatalogDoc(this)))
                .addSubcommand("debug", new CommandLine(new Debug(this)))
                .addSubcommand("dependency", new CommandLine(new DependencyCommand(this))
                        .addSubcommand("copy", new CommandLine(new DependencyCopy(this)))
                        .addSubcommand("list", new CommandLine(new DependencyList(this)))
                        .addSubcommand("runtime", new CommandLine(new DependencyRuntime(this)))
                        .addSubcommand("update", new CommandLine(new DependencyUpdate(this))))
                .addSubcommand("dirty", new CommandLine(new Dirty(this)))
                .addSubcommand("eval", new CommandLine(new EvalCommand(this))
                        .addSubcommand("expression", new CommandLine(new EvalExpressionCommand(this))))
                .addSubcommand("export", new CommandLine(new Export(this)))
                .addSubcommand("explain", new CommandLine(new Explain(this)))
                .addSubcommand("harden", new CommandLine(new Harden(this)))
                .addSubcommand("get", new CommandLine(new CamelStatus(this))
                        .addSubcommand("bean", new CommandLine(new CamelBeanDump(this)))
                        .addSubcommand("blocked", new CommandLine(new ListBlocked(this)))
                        .addSubcommand("circuit-breaker", new CommandLine(new ListCircuitBreaker(this)))
                        .addSubcommand("consumer", new CommandLine(new ListConsumer(this)))
                        .addSubcommand("context", new CommandLine(new CamelContextStatus(this)))
                        .addSubcommand("count", new CommandLine(new CamelCount(this)))
                        .addSubcommand("endpoint", new CommandLine(new ListEndpoint(this)))
                        .addSubcommand("event", new CommandLine(new ListEvent(this)))
                        .addSubcommand("groovy", new CommandLine(new ListGroovy(this)))
                        .addSubcommand("group", new CommandLine(new CamelRouteGroupStatus(this)))
                        .addSubcommand("health", new CommandLine(new ListHealth(this)))
                        .addSubcommand("history", new CommandLine(new CamelHistoryAction(this)))
                        .addSubcommand("inflight", new CommandLine(new ListInflight(this)))
                        .addSubcommand("internal-task", new CommandLine(new ListInternalTask(this)))
                        .addSubcommand("kafka", new CommandLine(new ListKafka(this)))
                        .addSubcommand("metric", new CommandLine(new ListMetric(this)))
                        .addSubcommand("platform-http", new CommandLine(new ListPlatformHttp(this)))
                        .addSubcommand("processor", new CommandLine(new CamelProcessorStatus(this)))
                        .addSubcommand("producer", new CommandLine(new ListProducer(this)))
                        .addSubcommand("properties", new CommandLine(new ListProperties(this)))
                        .addSubcommand("rest", new CommandLine(new ListRest(this)))
                        .addSubcommand("route", new CommandLine(new CamelRouteStatus(this)))
                        .addSubcommand("route-controller", new CommandLine(new RouteControllerAction(this)))
                        .addSubcommand("route-dump", new CommandLine(new CamelRouteDumpAction(this)))
                        .addSubcommand("service", new CommandLine(new ListService(this)))
                        .addSubcommand("source", new CommandLine(new CamelSourceAction(this)))
                        .addSubcommand("startup-recorder", new CommandLine(new CamelStartupRecorderAction(this)))
                        .addSubcommand("transformer", new CommandLine(new ListTransformer(this)))
                        .addSubcommand("variable", new CommandLine(new ListVariable(this)))
                        .addSubcommand("vault", new CommandLine(new ListVault(this))))
                .addSubcommand("hawtio", new CommandLine(new Hawtio(this)))
                .addSubcommand("infra", new CommandLine(new InfraCommand(this))
                        .addSubcommand("get", new CommandLine(new InfraGet(this)))
                        .addSubcommand("list", new CommandLine(new InfraList(this)))
                        .addSubcommand("log", new CommandLine(new InfraLog(this)))
                        .addSubcommand("ps", new CommandLine(new InfraPs(this)))
                        .addSubcommand("run", new CommandLine(new InfraRun(this)))
                        .addSubcommand("stop", new CommandLine(new InfraStop(this))))
                .addSubcommand("init", new CommandLine(new Init(this)))
                .addSubcommand("jolokia", new CommandLine(new Jolokia(this)))
                .addSubcommand("log", new CommandLine(new CamelLogAction(this)))
                .addSubcommand("nano", new CommandLine(new Nano(this)))
                .addSubcommand("plugin", new CommandLine(new PluginCommand(this))
                        .addSubcommand("add", new CommandLine(new PluginAdd(this)))
                        .addSubcommand("delete", new CommandLine(new PluginDelete(this)))
                        .addSubcommand("get", new CommandLine(new PluginGet(this))))
                .addSubcommand("ps", new CommandLine(new ListProcess(this)))
                .addSubcommand("run", new CommandLine(new Run(this)))
                .addSubcommand("sbom", new CommandLine(new SBOMGenerator(this)))
                .addSubcommand("script", new CommandLine(new Script(this)))
                .addSubcommand("shell", new CommandLine(new Shell(this)))
                .addSubcommand("stop", new CommandLine(new StopProcess(this)))
                .addSubcommand("top", new CommandLine(new CamelTop(this))
                        .addSubcommand("context", new CommandLine(new CamelContextTop(this)))
                        .addSubcommand("group", new CommandLine(new CamelRouteGroupTop(this)))
                        .addSubcommand("processor", new CommandLine(new CamelProcessorTop(this)))
                        .addSubcommand("route", new CommandLine(new CamelRouteTop(this)))
                        .addSubcommand("source", new CommandLine(new CamelSourceTop(this))))
                .addSubcommand("trace", new CommandLine(new CamelTraceAction(this)))
                .addSubcommand("transform", new CommandLine(new TransformCommand(this))
                        .addSubcommand("dataweave", new CommandLine(new TransformDataWeave(this)))
                        .addSubcommand("message", new CommandLine(new TransformMessageAction(this)))
                        .addSubcommand("route", new CommandLine(new TransformRoute(this))))
                .addSubcommand("update", new CommandLine(new UpdateCommand(this))
                        .addSubcommand("list", new CommandLine(new UpdateList(this)))
                        .addSubcommand("run", new CommandLine(new UpdateRun(this))))
                .addSubcommand("version", new CommandLine(new VersionCommand(this))
                        .addSubcommand("get", new CommandLine(new VersionGet(this)))
                        .addSubcommand("list", new CommandLine(new VersionList(this)))
                        .addSubcommand("set", new CommandLine(new VersionSet(this))))
                .addSubcommand("wrapper", new CommandLine(new WrapperCommand(this)))
                .setParameterExceptionHandler(new MissingPluginParameterExceptionHandler());

        postAddCommands(commandLine, args);

        if (discoverPlugins) {
            PluginHelper.addPlugins(commandLine, this, args);
        }

        commandLine.getCommandSpec().versionProvider(() -> {
            CamelCatalog catalog = new DefaultCamelCatalog();
            String v = catalog.getCatalogVersion();
            return new String[] { v };
        });

        CommandLineHelper.augmentWithUserConfiguration(commandLine);
        preExecute(commandLine, args);
        int exitCode = commandLine.execute(args);
        postExecute(commandLine, args, exitCode);
        quit(exitCode);
    }

    /**
     * Called after default commands has been added
     */
    public void postAddCommands(CommandLine commandLine, String[] args) {
        // noop
    }

    /**
     * Called just before the command line is executed
     */
    public void preExecute(CommandLine commandLine, String[] args) {
        // noop
    }

    /**
     * Called just after the command line is executed
     */
    public void postExecute(CommandLine commandLine, String[] args, int exitCode) {
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
    public Integer call() throws Exception {
        commandLine.execute("--help");
        return 0;
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

    public static CommandLine getCommandLine() {
        return commandLine;
    }
}
