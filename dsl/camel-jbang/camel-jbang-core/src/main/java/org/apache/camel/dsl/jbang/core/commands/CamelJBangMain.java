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
import org.apache.camel.dsl.jbang.core.commands.action.CamelReloadAction;
import org.apache.camel.dsl.jbang.core.commands.action.CamelResetStatsAction;
import org.apache.camel.dsl.jbang.core.commands.action.CamelRouteStartAction;
import org.apache.camel.dsl.jbang.core.commands.action.CamelRouteStopAction;
import org.apache.camel.dsl.jbang.core.commands.action.CamelSourceTop;
import org.apache.camel.dsl.jbang.core.commands.action.CamelThreadDump;
import org.apache.camel.dsl.jbang.core.commands.process.CamelContextStatus;
import org.apache.camel.dsl.jbang.core.commands.process.CamelContextTop;
import org.apache.camel.dsl.jbang.core.commands.process.CamelProcessorStatus;
import org.apache.camel.dsl.jbang.core.commands.process.CamelProcessorTop;
import org.apache.camel.dsl.jbang.core.commands.process.CamelRouteStatus;
import org.apache.camel.dsl.jbang.core.commands.process.CamelRouteTop;
import org.apache.camel.dsl.jbang.core.commands.process.CamelStatus;
import org.apache.camel.dsl.jbang.core.commands.process.CamelTop;
import org.apache.camel.dsl.jbang.core.commands.process.Hawtio;
import org.apache.camel.dsl.jbang.core.commands.process.Jolokia;
import org.apache.camel.dsl.jbang.core.commands.process.ListProcess;
import org.apache.camel.dsl.jbang.core.commands.process.ListVault;
import org.apache.camel.dsl.jbang.core.commands.process.StopProcess;
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
                .addSubcommand("ps", new CommandLine(new ListProcess(main)))
                .addSubcommand("stop", new CommandLine(new StopProcess(main)))
                .addSubcommand("get", new CommandLine(new CamelStatus(main))
                        .addSubcommand("context", new CommandLine(new CamelContextStatus(main)))
                        .addSubcommand("route", new CommandLine(new CamelRouteStatus(main)))
                        .addSubcommand("processor", new CommandLine(new CamelProcessorStatus(main)))
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
                        .addSubcommand("thread-dump", new CommandLine(new CamelThreadDump(main)))
                        .addSubcommand("gc", new CommandLine(new CamelGCAction(main))))
                .addSubcommand("generate", new CommandLine(new CodeGenerator(main))
                        .addSubcommand("rest", new CommandLine(new CodeRestGenerator(main))))
                .addSubcommand("jolokia", new CommandLine(new Jolokia(main)))
                .addSubcommand("hawtio", new CommandLine(new Hawtio(main)))
                .addSubcommand("bind", new CommandLine(new Bind(main)))
                .addSubcommand("pipe", new CommandLine(new Pipe(main)))
                .addSubcommand("export", new CommandLine(new Export(main)));

        commandLine.getCommandSpec().versionProvider(() -> {
            CamelCatalog catalog = new DefaultCamelCatalog();
            String v = catalog.getCatalogVersion();
            return new String[] { v };
        });

        int exitCode = commandLine.execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        commandLine.execute("--help");
        return 0;
    }

}
