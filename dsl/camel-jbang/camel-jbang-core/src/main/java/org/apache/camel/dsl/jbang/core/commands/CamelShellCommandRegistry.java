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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.camel.dsl.jbang.core.common.RuntimeUtil;
import org.jline.picocli.PicocliCommandRegistry;
import org.jline.reader.Completer;
import org.jline.shell.Command;
import org.jline.shell.CommandDescription;
import picocli.CommandLine;

/**
 * The Camel commands of the shell ({@code camel shell}, and the shell panel of the monitor). The prompt is
 * {@code camel>}, so commands are typed without the prefix, but the docs and the help say {@code camel ps}: a leading
 * {@code camel} is accepted, and the rest of the line runs as the command it names.
 * <p/>
 * A command runs while it holds {@link CliRunner#LOCK}, as camel ask and the AI panel of the monitor run commands in
 * this JVM too, and the commands share the printer of the CLI.
 */
public class CamelShellCommandRegistry extends PicocliCommandRegistry {

    static final String PREFIX = "camel";

    private final Command prefix = new Command() {
        @Override
        public String name() {
            return PREFIX;
        }

        @Override
        public String description() {
            return "Runs the command that follows (camel ps is ps)";
        }

        @Override
        // org.jline.shell.CommandSession, not the CommandSession this registry inherits from CommandRegistry
        public Object execute(org.jline.shell.CommandSession session, String[] args) throws Exception {
            if (args.length == 0) {
                session.out().println("Type a command, with or without camel: ps, or camel ps");
                return null;
            }
            Command target = PREFIX.equals(args[0]) ? null : CamelShellCommandRegistry.super.command(args[0]);
            if (target == null) {
                session.err().println("Unknown command: " + args[0]);
                return null;
            }
            return target.execute(session, Arrays.copyOfRange(args, 1, args.length));
        }
    };

    public CamelShellCommandRegistry(CommandLine commandLine) {
        // TODO: replace with new PicocliCommandRegistry(commandLine, "Camel") when JLine merges #1947
        super(commandLine);
    }

    @Override
    public String name() {
        return "Camel";
    }

    @Override
    public Collection<Command> commands() {
        List<Command> answer = new ArrayList<>();
        for (Command command : super.commands()) {
            answer.add(locked(command));
        }
        answer.add(locked(prefix));
        return answer;
    }

    @Override
    public Command command(String name) {
        Command command = PREFIX.equals(name) ? prefix : super.command(name);
        return command != null ? locked(command) : null;
    }

    /**
     * The command, which runs while it holds the lock of the commands in this JVM.
     */
    static Command locked(Command command) {
        return new Command() {
            @Override
            public String name() {
                return command.name();
            }

            @Override
            public List<String> aliases() {
                return command.aliases();
            }

            @Override
            public String description() {
                return command.description();
            }

            @Override
            public CommandDescription describe(List<String> args) {
                return command.describe(args);
            }

            @Override
            public Object execute(org.jline.shell.CommandSession session, String[] args) throws Exception {
                synchronized (CliRunner.LOCK) {
                    RuntimeUtil.newCommand();
                    return command.execute(session, args);
                }
            }

            @Override
            public List<Completer> completers() {
                return command.completers();
            }

            @Override
            public Completer completer() {
                return command.completer();
            }

            @Override
            public Map<String, Command> subcommands() {
                return command.subcommands();
            }
        };
    }

    @Override
    public boolean hasCommand(String name) {
        return PREFIX.equals(name) || super.hasCommand(name);
    }
}
