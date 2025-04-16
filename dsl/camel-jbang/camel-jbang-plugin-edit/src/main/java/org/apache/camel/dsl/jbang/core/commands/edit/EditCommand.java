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
package org.apache.camel.dsl.jbang.core.commands.edit;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Stack;
import java.util.function.Supplier;

import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.jline.builtins.Nano;
import org.jline.builtins.Options;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import picocli.CommandLine;

@CommandLine.Command(name = "edit", description = "Edit Camel with suggestions and diagnostics")
public class EditCommand extends CamelCommand {

    @CommandLine.Parameters(description = "Name of file", arity = "1",
                            paramLabel = "<file>", parameterConsumer = FileConsumer.class)
    private Path filePath; // Defined only for file path completion; the field never used
    private String file;

    public EditCommand(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        Supplier<Path> workDir = () -> Paths.get(System.getProperty("user.dir"));
        try (Terminal terminal = TerminalBuilder.builder().build()) {
            String[] argv = new String[] { file };
            Options opt = Options.compile(Nano.usage()).parse(argv);
            if (opt.isSet("help")) {
                throw new Options.HelpException(opt.usage());
            } else {
                Path currentDir = workDir.get();
                CamelNanoLspEditor edit
                        = new CamelNanoLspEditor(terminal, currentDir, opt, null);
                edit.open(opt.args());
                edit.run();
            }
        }
        return 0;
    }

    static class FileConsumer extends ParameterConsumer<EditCommand> {
        @Override
        protected void doConsumeParameters(Stack<String> args, EditCommand cmd) {
            cmd.file = args.pop();
        }
    }
}
