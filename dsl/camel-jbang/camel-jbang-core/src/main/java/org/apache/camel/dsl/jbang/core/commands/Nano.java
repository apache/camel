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
import java.util.Stack;
import java.util.function.Supplier;

import org.jline.builtins.Commands;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import picocli.CommandLine;

@CommandLine.Command(name = "nano",
                     description = "Nano editor to edit file",
                     footer = "Press Ctrl-X to exit.")
public class Nano extends CamelCommand {

    @CommandLine.Parameters(description = "Name of file", arity = "1",
                            paramLabel = "<file>", parameterConsumer = FileConsumer.class)
    private Path filePath; // Defined only for file path completion; the field never used
    private String file;

    public Nano(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        Supplier<Path> workDir = () -> Paths.get(System.getProperty("user.dir"));
        try (Terminal terminal = TerminalBuilder.builder().build()) {
            Commands.nano(terminal, System.out, System.err, workDir.get(), new String[] { file }, null);
        }
        return 0;
    }

    static class FileConsumer extends ParameterConsumer<Nano> {
        @Override
        protected void doConsumeParameters(Stack<String> args, Nano cmd) {
            cmd.file = args.pop();
        }
    }
}
