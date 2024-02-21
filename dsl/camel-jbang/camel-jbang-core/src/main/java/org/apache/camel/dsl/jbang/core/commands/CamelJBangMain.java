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
import org.apache.camel.dsl.jbang.core.commands.catalog.CatalogCommand;
import org.apache.camel.dsl.jbang.core.commands.catalog.CatalogComponent;
import org.apache.camel.dsl.jbang.core.commands.catalog.CatalogDataFormat;
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
import org.apache.camel.dsl.jbang.core.commands.plugin.PluginAdd;
import org.apache.camel.dsl.jbang.core.commands.plugin.PluginCommand;
import org.apache.camel.dsl.jbang.core.commands.plugin.PluginDelete;
import org.apache.camel.dsl.jbang.core.commands.plugin.PluginGet;
import org.apache.camel.dsl.jbang.core.commands.process.*;
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

    public static void run(String... args) {
        run(new CamelJBangMain(), args);
    }

    public static void run(CamelJBangMain main, String... args) {
        // set pid as system property as logging ${sys:pid} needs to be resolved on windows
        try {
            long pid = ProcessHandle.current().pid();
            System.setProperty("pid", Long.toString(pid));
        } catch (Exception e) {
            // ignore
        }

        commandLine = new CommandLine(main)
                .addSubcommand("init", new CommandLine(new Init(main)))
                .addSubcommand("run", new CommandLine(new Run(main)))
                .addSubcommand("debug", new CommandLine(new Debug(main)))
                .addSubcommand("log", new CommandLine(new CamelLogAction(main)))
                .addSubcommand("ps", new CommandLine(new ListProcess(main)))
                .addSubcommand("stop", new CommandLine(new StopProcess(main)))
                .addSubcommand("trace", new CommandLine(new CamelTraceAction(main)))
                .addSubcommand("transform", new CommandLine(new TransformCommand(main))
                        .addSubcommand("route", new CommandLine(new TransformRoute(main)))
                        .addSubcommand("message", new CommandLine(new TransformMessageAction(main))))
                .addSubcommand("get", new CommandLine(new CamelStatus(main))
                        .addSubcommand("context", new CommandLine(new CamelContextStatus(main)))
                        .addSubcommand("route", new CommandLine(new CamelRouteStatus(main)))
                        .addSubcommand("processor", new CommandLine(new CamelProcessorStatus(main)))
                        .addSubcommand("count", new CommandLine(new CamelCount(main)))
                        .addSubcommand("health", new CommandLine(new ListHealth(main)))
                        .addSubcommand("variable", new CommandLine(new ListVariable(main)))
                        .addSubcommand("consumer", new CommandLine(new ListConsumer(main)))
                        .addSubcommand("endpoint", new CommandLine(new ListEndpoint(main)))
                        .addSubcommand("event", new CommandLine(new ListEvent(main)))
                        .addSubcommand("inflight", new CommandLine(new ListInflight(main)))
                        .addSubcommand("blocked", new CommandLine(new ListBlocked(main)))
                        .addSubcommand("route-controller", new CommandLine(new RouteControllerAction(main)))
                        .addSubcommand("transformer", new CommandLine(new ListTransformer(main)))
                        .addSubcommand("circuit-breaker", new CommandLine(new ListCircuitBreaker(main)))
                        .addSubcommand("metric", new CommandLine(new ListMetric(main)))
                        .addSubcommand("service", new CommandLine(new ListService(main)))
                        .addSubcommand("source", new CommandLine(new CamelSourceAction(main)))
                        .addSubcommand("route-dump", new CommandLine(new CamelRouteDumpAction(main)))
                        .addSubcommand("startup-recorder", new CommandLine(new CamelStartupRecorderAction(main)))
                        .addSubcommand("vault", new CommandLine(new ListVault(main))))
                .addSubcommand("top", new CommandLine(new CamelTop(main))
                        .addSubcommand("context", new CommandLine(new CamelContextTop(main)))
                        .addSubcommand("route", new CommandLine(new CamelRouteTop(main)))
                        .addSubcommand("processor", new CommandLine(new CamelProcessorTop(main)))
                        .addSubcommand("source", new CommandLine(new CamelSourceTop(main))))
                .addSubcommand("cmd", new CommandLine(new CamelAction(main))
                        .addSubcommand("start-route", new CommandLine(new CamelRouteStartAction(main)))
                        .addSubcommand("stop-route", new CommandLine(new CamelRouteStopAction(main)))
                        .addSubcommand("reset-stats", new CommandLine(new CamelResetStatsAction(main)))
                        .addSubcommand("reload", new CommandLine(new CamelReloadAction(main)))
                        .addSubcommand("send", new CommandLine(new CamelSendAction(main)))
                        .addSubcommand("stub", new CommandLine(new CamelStubAction(main)))
                        .addSubcommand("thread-dump", new CommandLine(new CamelThreadDump(main)))
                        .addSubcommand("logger", new CommandLine(new LoggerAction(main)))
                        .addSubcommand("gc", new CommandLine(new CamelGCAction(main))))
                .addSubcommand("dependency", new CommandLine(new DependencyCommand(main))
                        .addSubcommand("list", new CommandLine(new DependencyList(main)))
                        .addSubcommand("copy", new CommandLine(new DependencyCopy(main)))
                        .addSubcommand("update", new CommandLine(new DependencyUpdate(main))))
                .addSubcommand("generate", new CommandLine(new CodeGenerator(main))
                        .addSubcommand("rest", new CommandLine(new CodeRestGenerator(main))))
                .addSubcommand("sbom", new CommandLine(new SBOMGenerator(main)))
                .addSubcommand("catalog", new CommandLine(new CatalogCommand(main))
                        .addSubcommand("component", new CommandLine(new CatalogComponent(main)))
                        .addSubcommand("dataformat", new CommandLine(new CatalogDataFormat(main)))
                        .addSubcommand("language", new CommandLine(new CatalogLanguage(main)))
                        .addSubcommand("transformer", new CommandLine(new CatalogTransformer(main)))
                        .addSubcommand("other", new CommandLine(new CatalogOther(main)))
                        .addSubcommand("kamelet", new CommandLine(new CatalogKamelet(main))))
                .addSubcommand("doc", new CommandLine(new CatalogDoc(main)))
                .addSubcommand("jolokia", new CommandLine(new Jolokia(main)))
                .addSubcommand("hawtio", new CommandLine(new Hawtio(main)))
                .addSubcommand("bind", new CommandLine(new Bind(main)))
                .addSubcommand("script", new CommandLine(new Script(main)))
                .addSubcommand("export", new CommandLine(new Export(main)))
                .addSubcommand("completion", new CommandLine(new Complete(main)))
                .addSubcommand("config", new CommandLine(new ConfigCommand(main))
                        .addSubcommand("list", new CommandLine(new ConfigList(main)))
                        .addSubcommand("get", new CommandLine(new ConfigGet(main)))
                        .addSubcommand("unset", new CommandLine(new ConfigUnset(main)))
                        .addSubcommand("set", new CommandLine(new ConfigSet(main))))
                .addSubcommand("plugin", new CommandLine(new PluginCommand(main))
                        .addSubcommand("get", new CommandLine(new PluginGet(main)))
                        .addSubcommand("add", new CommandLine(new PluginAdd(main)))
                        .addSubcommand("delete", new CommandLine(new PluginDelete(main))))
                .addSubcommand("version", new CommandLine(new VersionCommand(main))
                        .addSubcommand("get", new CommandLine(new VersionGet(main)))
                        .addSubcommand("set", new CommandLine(new VersionSet(main)))
                        .addSubcommand("list", new CommandLine(new VersionList(main))));

        PluginHelper.addPlugins(commandLine, main);

        commandLine.getCommandSpec().versionProvider(() -> {
            CamelCatalog catalog = new DefaultCamelCatalog();
            String v = catalog.getCatalogVersion();
            return new String[] { v };
        });

        CommandLineHelper.augmentWithUserConfiguration(commandLine, args);
        int exitCode = commandLine.execute(args);
        main.quit(exitCode);
    }

    /**
     * Finish this main with given exit code. By default, uses system exit to terminate. Subclasses may want to
     * overwrite this exit behavior e.g. during unit tests.
     *
     * @param exitCode
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

    /**
     * Uses this printer for writing command output.
     *
     * @param out to use with this main.
     */
    public CamelJBangMain withPrinter(Printer out) {
        this.out = out;
        return this;
    }

}
