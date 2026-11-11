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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Supplier;

import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Argument;
import org.jline.builtins.ClasspathResourceUtil;
import org.jline.builtins.Commands;
import org.jline.builtins.ConfigurationPath;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

@CommandDefinition(name = "nano", description = "Nano editor to edit file", generateHelp = true, sortOptions = true,
                   footer = {
                           "Press Ctrl-X to exit." })
public class Nano extends CamelCommand {

    private Path filePath; // Defined only for file path completion; the field never used
    @Argument(required = true, description = "Name of file", paramLabel = "<file>")
    private String file;

    public Nano(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        requireInteractiveTerminal("camel nano");
        Supplier<Path> workDir = () -> Paths.get(System.getProperty("user.dir"));
        try (Terminal terminal = TerminalBuilder.builder().build()) {
            Path appConfig = ClasspathResourceUtil.getResourcePath("/nano/jnanorc", getClass()).getParent();
            ConfigurationPath configPath = new ConfigurationPath(appConfig, null);
            Commands.nano(terminal, System.out, System.err, workDir.get(), new String[] { file }, configPath);
        }
        return 0;
    }
}
