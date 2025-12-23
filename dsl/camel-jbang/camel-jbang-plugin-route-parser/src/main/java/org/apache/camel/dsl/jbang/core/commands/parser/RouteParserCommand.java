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
package org.apache.camel.dsl.jbang.core.commands.parser;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.CommandHelper;
import org.apache.camel.parser.RouteBuilderParser;
import org.apache.camel.parser.model.CamelNodeDetails;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.StopWatch;
import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.AnsiConsole;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import picocli.CommandLine;

@CommandLine.Command(name = "route-parser", description = "Parses Java route and dumps route structure")
public class RouteParserCommand extends CamelCommand {

    @CommandLine.Parameters(description = { "The Camel RouteBuilder Java source files to parse." },
                            arity = "1..9",
                            paramLabel = "<files>",
                            parameterConsumer = FilesConsumer.class)
    Path[] filePaths;
    List<String> files = new ArrayList<>();

    @CommandLine.Option(names = { "--raw" },
                        description = "To output raw without metadata")
    boolean raw;

    @CommandLine.Option(names = { "--watch" },
                        description = "Execute periodically and showing output fullscreen")
    boolean watch;

    private CommandHelper.ReadConsoleTask waitUserTask;

    public RouteParserCommand(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        int exit;
        final AtomicBoolean running = new AtomicBoolean(true);
        if (watch) {
            Thread t = new Thread(() -> {
                waitUserTask = new CommandHelper.ReadConsoleTask(() -> running.set(false));
                waitUserTask.run();
            }, "WaitForUser");
            t.start();
            do {
                exit = doWatchCall();
                if (exit == 0) {
                    // use 2-sec delay in watch mode
                    try {
                        StopWatch watch = new StopWatch();
                        while (running.get() && watch.taken() < 2000) {
                            Thread.sleep(100);
                        }
                    } catch (Exception e) {
                        running.set(false);
                    }
                }
            } while (exit == 0 && running.get());
        } else {
            exit = doWatchCall();
        }
        return exit;
    }

    protected Integer doWatchCall() throws Exception {
        for (String file : files) {
            Integer code = doDumpFile(file);
            if (code != 0) {
                return code;
            }
        }
        return 0;
    }

    protected Integer doDumpFile(String file) throws Exception {
        File f = new File(file);
        if (!f.exists() || !f.isFile()) {
            printer().printErr("File not found: " + file);
            return -1;
        }
        String ext = FileUtil.onlyExt(file);
        if (!"java".equals(ext)) {
            printer().printErr("Only .java source file is supported");
            return -1;
        }
        JavaClassSource clazz = (JavaClassSource) Roaster.parse(f);
        String fqn = clazz.getQualifiedName();
        fqn = fqn.replace('.', '/');
        fqn = fqn + ".java";
        List<CamelNodeDetails> list = RouteBuilderParser.parseRouteBuilderTree(clazz, fqn, true);
        if (watch) {
            clearScreen();
        }
        for (var route : list) {
            printer().println();
            if (!raw) {
                printer().printf("Source: %s%n", route.getFileName());
                printer().println("--------------------------------------------------------------------------------");
            }
            String tree = route.dump(0);
            printer().println(tree);
            printer().println();
        }
        return 0;
    }

    protected void clearScreen() {
        AnsiConsole.out().print(Ansi.ansi().eraseScreen().cursor(1, 1));
    }

    static class FilesConsumer extends CamelCommand.ParameterConsumer<RouteParserCommand> {
        protected void doConsumeParameters(Stack<String> args, RouteParserCommand cmd) {
            String arg = args.pop();
            cmd.files.add(arg);
        }
    }

}
