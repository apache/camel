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
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "camel", description = "Apache Camel CLI", mixinStandardHelpOptions = true)
public class CamelJBangMain implements Callable<Integer> {
    private static CommandLine commandLine;

    @CommandLine.Option(names = { "--profile" }, scope = CommandLine.ScopeType.INHERIT, defaultValue = "application",
                        description = "Profile")
    private String profile;

    public static void run(String... args) {
        CamelJBangMain main = new CamelJBangMain();
        commandLine = new CommandLine(main)
                .addSubcommand("run", new CommandLine(new Run(main)))
                .addSubcommand("init", new CommandLine(new Init(main)))
                .addSubcommand("bind", new CommandLine(new Bind(main)))
                .addSubcommand("pipe", new CommandLine(new Pipe(main)))
                .addSubcommand("package", new CommandLine(new Package(main))
                        .addSubcommand("uber-jar", new CommandLine(new UberJar(main))))
                .addSubcommand("generate", new CommandLine(new CodeGenerator(main))
                        .addSubcommand("rest", new CommandLine(new CodeRestGenerator(main))))
                .addSubcommand("build", new CommandLine(new Build(main))
                        .addSubcommand("manifests", new CommandLine(new Manifest(main)))
                        .addSubcommand("image", new CommandLine(new Image(main))))
                .addSubcommand("export", new CommandLine(new Export(main))
                        .addSubcommand("spring-boot", new CommandLine(new ExportSpringBoot(main)))
                        .addSubcommand("quarkus", new CommandLine(new ExportQuarkus(main))))
                .addSubcommand("deploy", new CommandLine(new Deploy(main)))
                .addSubcommand("undeploy", new CommandLine(new Undeploy(main)));
        /* // TODO: do not show commands that are deprecated and to be either removed or reworked
                .addSubcommand("search", new CommandLine(new Search())
                        .addSubcommand("kamelets", new SearchKamelets())
                        .addSubcommand("components", new SearchComponents())
                        .addSubcommand("languages", new SearchLanguages())
                        .addSubcommand("others", new SearchOthers()))
        */

        commandLine.getCommandSpec().versionProvider(() -> {
            CamelCatalog catalog = new DefaultCamelCatalog();
            String v = catalog.getCatalogVersion();
            return new String[] { v };
        });

        String profile = ProfileHelper.getProfile(args);
        ProfileHelper.augmentWithProperties(commandLine, profile, args);
        int exitCode = commandLine.execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        commandLine.execute("--help");
        return 0;
    }

    public String getProfile() {
        return profile;
    }
}
