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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.camel.util.HomeHelper;
import org.jline.builtins.InteractiveCommandGroup;
import org.jline.builtins.PosixCommandGroup;
import org.jline.picocli.PicocliCommandRegistry;
import org.jline.reader.LineReader;
import org.jline.shell.impl.DefaultAliasManager;
import picocli.CommandLine;

@CommandLine.Command(name = "shell",
                     description = "Interactive Camel JBang shell.",
                     footer = "Press Ctrl-C to exit.")
public class Shell extends CamelCommand {

    public Shell(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        PicocliCommandRegistry registry = new PicocliCommandRegistry(CamelJBangMain.getCommandLine());

        String homeDir = HomeHelper.resolveHomeDir();
        Path history = Paths.get(homeDir, ".camel-jbang-history");

        // Alias persistence: aliases are stored in ~/.camel-jbang-aliases
        Path aliasFile = Paths.get(homeDir, ".camel-jbang-aliases");
        DefaultAliasManager aliasManager = new DefaultAliasManager(aliasFile);

        // Init script: if ~/.camel-jbang-init exists, it will be executed on shell startup
        Path initScript = Paths.get(homeDir, ".camel-jbang-init");

        org.jline.shell.ShellBuilder builder = org.jline.shell.Shell.builder()
                .prompt("camel> ")
                .groups(registry, new PosixCommandGroup(), new InteractiveCommandGroup())
                .historyFile(history)
                .historyCommands(true)
                .helpCommands(true)
                .variableCommands(true)
                .commandHighlighter(true)
                .aliasManager(aliasManager)
                // scriptCommands(true) requires a scriptRunner to be set;
                // omitting for now until a DefaultScriptRunner is available
                .variable(LineReader.LIST_MAX, 50)
                .option(LineReader.Option.GROUP_PERSIST, true);

        if (Files.exists(initScript)) {
            builder.initScript(initScript.toFile());
        }

        try (org.jline.shell.Shell shell = builder.build()) {
            shell.run();
        }
        return 0;
    }
}
