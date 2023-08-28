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
import org.apache.camel.dsl.jbang.core.commands.action.CamelAction;
import org.apache.camel.dsl.jbang.core.commands.action.CamelGCAction;
import org.apache.camel.dsl.jbang.core.commands.action.CamelLogAction;
import org.apache.camel.dsl.jbang.core.commands.action.CamelReloadAction;
import org.apache.camel.dsl.jbang.core.commands.action.CamelResetStatsAction;
import org.apache.camel.dsl.jbang.core.commands.action.CamelRouteDumpAction;
import org.apache.camel.dsl.jbang.core.commands.action.CamelRouteStartAction;
import org.apache.camel.dsl.jbang.core.commands.action.CamelRouteStopAction;
import org.apache.camel.dsl.jbang.core.commands.action.CamelSendAction;
import org.apache.camel.dsl.jbang.core.commands.action.CamelSourceAction;
import org.apache.camel.dsl.jbang.core.commands.action.CamelSourceTop;
import org.apache.camel.dsl.jbang.core.commands.action.CamelStubAction;
import org.apache.camel.dsl.jbang.core.commands.action.CamelThreadDump;
import org.apache.camel.dsl.jbang.core.commands.action.CamelTraceAction;
import org.apache.camel.dsl.jbang.core.commands.action.LoggerAction;
import org.apache.camel.dsl.jbang.core.commands.action.RouteControllerAction;
import org.apache.camel.dsl.jbang.core.commands.catalog.CatalogCommand;
import org.apache.camel.dsl.jbang.core.commands.catalog.CatalogComponent;
import org.apache.camel.dsl.jbang.core.commands.catalog.CatalogDataFormat;
import org.apache.camel.dsl.jbang.core.commands.catalog.CatalogDoc;
import org.apache.camel.dsl.jbang.core.commands.catalog.CatalogKamelet;
import org.apache.camel.dsl.jbang.core.commands.catalog.CatalogLanguage;
import org.apache.camel.dsl.jbang.core.commands.catalog.CatalogOther;
import org.apache.camel.dsl.jbang.core.commands.config.ConfigCommand;
import org.apache.camel.dsl.jbang.core.commands.config.ConfigGet;
import org.apache.camel.dsl.jbang.core.commands.config.ConfigList;
import org.apache.camel.dsl.jbang.core.commands.config.ConfigSet;
import org.apache.camel.dsl.jbang.core.commands.config.ConfigUnset;
import org.apache.camel.dsl.jbang.core.commands.process.CamelContextStatus;
import org.apache.camel.dsl.jbang.core.commands.process.CamelContextTop;
import org.apache.camel.dsl.jbang.core.commands.process.CamelCount;
import org.apache.camel.dsl.jbang.core.commands.process.CamelProcessorStatus;
import org.apache.camel.dsl.jbang.core.commands.process.CamelProcessorTop;
import org.apache.camel.dsl.jbang.core.commands.process.CamelRouteStatus;
import org.apache.camel.dsl.jbang.core.commands.process.CamelRouteTop;
import org.apache.camel.dsl.jbang.core.commands.process.CamelStatus;
import org.apache.camel.dsl.jbang.core.commands.process.CamelTop;
import org.apache.camel.dsl.jbang.core.commands.process.Hawtio;
import org.apache.camel.dsl.jbang.core.commands.process.Jolokia;
import org.apache.camel.dsl.jbang.core.commands.process.ListBlocked;
import org.apache.camel.dsl.jbang.core.commands.process.ListCircuitBreaker;
import org.apache.camel.dsl.jbang.core.commands.process.ListEndpoint;
import org.apache.camel.dsl.jbang.core.commands.process.ListEvent;
import org.apache.camel.dsl.jbang.core.commands.process.ListHealth;
import org.apache.camel.dsl.jbang.core.commands.process.ListInflight;
import org.apache.camel.dsl.jbang.core.commands.process.ListMetric;
import org.apache.camel.dsl.jbang.core.commands.process.ListProcess;
import org.apache.camel.dsl.jbang.core.commands.process.ListService;
import org.apache.camel.dsl.jbang.core.commands.process.ListVault;
import org.apache.camel.dsl.jbang.core.commands.process.StopProcess;
import org.apache.camel.dsl.jbang.core.commands.version.VersionCommand;
import org.apache.camel.dsl.jbang.core.commands.version.VersionGet;
import org.apache.camel.dsl.jbang.core.commands.version.VersionList;
import org.apache.camel.dsl.jbang.core.commands.version.VersionSet;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "camel", description = "Apache Camel CLI", mixinStandardHelpOptions = true)
public class CamelJBangMain implements Callable<Integer> {
    private static CommandLine commandLine;

    public static void run(String... args) {
        CamelJBangMain main = new CamelJBangMain();
        commandLine = new CommandLine(main)
                .addSubcommand("init", new CommandLine(new Init(main)))
                .addSubcommand("run", new CommandLine(new Run(main)))
                .addSubcommand("log", new CommandLine(new CamelLogAction(main)))
                .addSubcommand("ps", new CommandLine(new ListProcess(main)))
                .addSubcommand("stop", new CommandLine(new StopProcess(main)))
                .addSubcommand("trace", new CommandLine(new CamelTraceAction(main)))
                .addSubcommand("transform", new CommandLine(new Transform(main)))
                .addSubcommand("get", new CommandLine(new CamelStatus(main))
                        .addSubcommand("context", new CommandLine(new CamelContextStatus(main)))
                        .addSubcommand("route", new CommandLine(new CamelRouteStatus(main)))
                        .addSubcommand("processor", new CommandLine(new CamelProcessorStatus(main)))
                        .addSubcommand("count", new CommandLine(new CamelCount(main)))
                        .addSubcommand("health", new CommandLine(new ListHealth(main)))
                        .addSubcommand("endpoint", new CommandLine(new ListEndpoint(main)))
                        .addSubcommand("event", new CommandLine(new ListEvent(main)))
                        .addSubcommand("inflight", new CommandLine(new ListInflight(main)))
                        .addSubcommand("blocked", new CommandLine(new ListBlocked(main)))
                        .addSubcommand("route-controller", new CommandLine(new RouteControllerAction(main)))
                        .addSubcommand("circuit-breaker", new CommandLine(new ListCircuitBreaker(main)))
                        .addSubcommand("metric", new CommandLine(new ListMetric(main)))
                        .addSubcommand("service", new CommandLine(new ListService(main)))
                        .addSubcommand("source", new CommandLine(new CamelSourceAction(main)))
                        .addSubcommand("route-dump", new CommandLine(new CamelRouteDumpAction(main)))
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
                        .addSubcommand("copy", new CommandLine(new DependencyCopy(main))))
                .addSubcommand("generate", new CommandLine(new CodeGenerator(main))
                        .addSubcommand("rest", new CommandLine(new CodeRestGenerator(main))))
                .addSubcommand("catalog", new CommandLine(new CatalogCommand(main))
                        .addSubcommand("component", new CommandLine(new CatalogComponent(main)))
                        .addSubcommand("dataformat", new CommandLine(new CatalogDataFormat(main)))
                        .addSubcommand("language", new CommandLine(new CatalogLanguage(main)))
                        .addSubcommand("other", new CommandLine(new CatalogOther(main)))
                        .addSubcommand("kamelet", new CommandLine(new CatalogKamelet(main))))
                .addSubcommand("doc", new CommandLine(new CatalogDoc(main)))
                .addSubcommand("jolokia", new CommandLine(new Jolokia(main)))
                .addSubcommand("hawtio", new CommandLine(new Hawtio(main)))
                .addSubcommand("bind", new CommandLine(new Bind(main)))
                .addSubcommand("pipe", new CommandLine(new Pipe(main)))
                .addSubcommand("export", new CommandLine(new Export(main)))
                .addSubcommand("completion", new CommandLine(new Complete(main)))
                .addSubcommand("config", new CommandLine(new ConfigCommand(main))
                        .addSubcommand("list", new CommandLine(new ConfigList(main)))
                        .addSubcommand("get", new CommandLine(new ConfigGet(main)))
                        .addSubcommand("unset", new CommandLine(new ConfigUnset(main)))
                        .addSubcommand("set", new CommandLine(new ConfigSet(main))))
                .addSubcommand("version", new CommandLine(new VersionCommand(main))
                        .addSubcommand("get", new CommandLine(new VersionGet(main)))
                        .addSubcommand("set", new CommandLine(new VersionSet(main)))
                        .addSubcommand("list", new CommandLine(new VersionList(main))));

        commandLine.getCommandSpec().versionProvider(() -> {
            CamelCatalog catalog = new DefaultCamelCatalog();
            String v = catalog.getCatalogVersion();
            return new String[] { v };
        });

        CommandLineHelper.augmentWithUserConfiguration(commandLine, args);
        int exitCode = commandLine.execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        commandLine.execute("--help");
        return 0;
    }

}
