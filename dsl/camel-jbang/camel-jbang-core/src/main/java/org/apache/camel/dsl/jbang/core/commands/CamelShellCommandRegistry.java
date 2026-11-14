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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.aesh.command.CommandDefinition;
import org.aesh.command.impl.internal.ProcessedCommand;
import org.aesh.command.impl.internal.ProcessedOption;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.dsl.jbang.core.common.CommandTree;
import org.apache.camel.dsl.jbang.run.common.RuntimeUtil;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.shell.ArgumentDescription;
import org.jline.shell.Command;
import org.jline.shell.CommandDescription;
import org.jline.shell.CommandGroup;
import org.jline.utils.AttributedString;

/**
 * The commands of the Camel CLI in the JLine shell of {@code camel shell}: their names and descriptions from the
 * command tree, their tooltips from the model that aesh makes of them, and their completion from aesh.
 */
public class CamelShellCommandRegistry implements CommandGroup {

    static final String PREFIX = "camel";

    private final CommandTree commandLine;

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
        public Object execute(org.jline.shell.CommandSession session, String[] args) throws Exception {
            if (args.length == 0) {
                session.out().println("Type a command, with or without camel: ps, or camel ps");
                return null;
            }
            Command target = PREFIX.equals(args[0]) ? null : CamelShellCommandRegistry.this.command(args[0]);
            if (target == null) {
                session.err().println("Unknown command: " + args[0]);
                return null;
            }
            return target.execute(session, Arrays.copyOfRange(args, 1, args.length));
        }
    };

    public CamelShellCommandRegistry(CommandTree commandLine) {
        this.commandLine = commandLine;
    }

    @Override
    public String name() {
        return "Camel";
    }

    @Override
    public Collection<Command> commands() {
        List<Command> answer = new ArrayList<>();
        commandLine.getSubcommands().forEach((name, tree) -> answer.add(locked(new CliCommand(name, tree))));
        answer.add(locked(prefix));
        return answer;
    }

    @Override
    public Command command(String name) {
        if (PREFIX.equals(name)) {
            return locked(prefix);
        }
        CommandTree tree = commandLine.getSubcommands().get(name);
        return tree != null ? locked(new CliCommand(name, tree)) : null;
    }

    @Override
    public boolean hasCommand(String name) {
        return PREFIX.equals(name) || commandLine.getSubcommands().containsKey(name);
    }

    /**
     * A command of the CLI: it runs on a new command tree (new instances of the commands, as {@link CliRunner} does),
     * and prints to the terminal as it does outside the shell.
     */
    private final class CliCommand implements Command {

        private final String name;
        private final CommandTree tree;

        CliCommand(String name, CommandTree tree) {
            this.name = name;
            this.tree = tree;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public List<String> aliases() {
            CommandDefinition definition = CommandTree.definitionOf(tree.getCommand().getClass());
            return definition != null ? List.of(definition.aliases()) : List.of();
        }

        @Override
        public String description() {
            CommandDefinition definition = CommandTree.definitionOf(tree.getCommand().getClass());
            return definition != null ? definition.description() : "";
        }

        @Override
        public CommandDescription describe(List<String> args) {
            // the sub-command when the line names one, such as get route
            CommandTree target = tree;
            for (String arg : args) {
                CommandTree sub = target.getSubcommands().get(arg);
                if (sub == null) {
                    break;
                }
                target = sub;
            }
            try {
                ProcessedCommand<?, ?> processed = target.processedCommand();
                Map<String, List<AttributedString>> options = new LinkedHashMap<>();
                for (ProcessedOption option : processed.getOptions()) {
                    String key = "--" + option.name();
                    if (option.shortName() != null) {
                        key = "-" + option.shortName() + " " + key;
                    }
                    options.put(key, List.of(new AttributedString(nullToEmpty(option.description()))));
                }
                List<ArgumentDescription> arguments = new ArrayList<>();
                for (ProcessedOption argument : processed.getArgumentOptions()) {
                    arguments.add(new ArgumentDescription(
                            argument.getDisplayLabel(),
                            List.of(new AttributedString(nullToEmpty(argument.description())))));
                }
                if (processed.getArguments() != null) {
                    ProcessedOption argument = processed.getArguments();
                    arguments.add(new ArgumentDescription(
                            argument.getDisplayLabel(),
                            List.of(new AttributedString(nullToEmpty(argument.description())))));
                }
                return CommandDescription.builder()
                        .mainDescription(List.of(new AttributedString(nullToEmpty(processed.description()))))
                        .options(options)
                        .arguments(arguments)
                        .command(true)
                        .subcommand(target != tree)
                        .build();
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        public Object execute(org.jline.shell.CommandSession session, String[] args) throws Exception {
            CamelJBangMain main = (CamelJBangMain) commandLine.getCommand();
            CommandTree fresh = main.newCommandLine();
            String[] line = new String[args.length + 1];
            line[0] = name;
            System.arraycopy(args, 0, line, 1, args.length);
            line = main.expandArgumentFiles(line);
            if (line == null) {
                return CliExitCode.USAGE;
            }
            return main.run(fresh, line);
        }

        @Override
        public Completer completer() {
            return (reader, parsedLine, candidates) -> {
                // aesh completes the whole command line, from the name of the root command
                String typed = parsedLine.line().substring(0, parsedLine.cursor());
                String line = PREFIX + " " + typed;
                try {
                    CommandTree.Completion completion = commandLine.complete(line, line.length());
                    // aesh completes the text from its offset in the line, JLine replaces the word at the cursor: the
                    // part of the word before the offset stays, such as --runtime= of --runtime=spring-boot
                    int wordStart = line.length() - parsedLine.wordCursor();
                    String head = completion.offset() > wordStart ? line.substring(wordStart, completion.offset()) : "";
                    for (String candidate : completion.candidates()) {
                        String value = head + candidate;
                        candidates.add(new Candidate(
                                value, value, null, null, null, null,
                                completion.appendSeparator() && completion.candidates().size() == 1));
                    }
                } catch (Exception e) {
                    // no completion
                }
            };
        }

        @Override
        public Map<String, Command> subcommands() {
            Map<String, Command> answer = new LinkedHashMap<>();
            tree.getSubcommands().forEach((n, sub) -> answer.put(n, new CliCommand(n, sub)));
            return answer;
        }
    }

    private static String nullToEmpty(String text) {
        return text != null ? text : "";
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
}
