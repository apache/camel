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
import org.apache.camel.dsl.jbang.core.commands.process.CamelStatus;
import org.apache.camel.dsl.jbang.core.commands.process.Hawtio;
import org.apache.camel.dsl.jbang.core.commands.process.Jolokia;
import org.apache.camel.dsl.jbang.core.commands.process.ListProcess;
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
                .addSubcommand("status", new CommandLine(new CamelStatus(main)))
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
