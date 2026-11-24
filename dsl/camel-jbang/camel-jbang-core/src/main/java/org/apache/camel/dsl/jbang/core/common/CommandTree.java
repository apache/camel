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
package org.apache.camel.dsl.jbang.core.common;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.aesh.command.AeshCommandRuntimeBuilder;
import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.CommandRuntime;
import org.aesh.command.DefaultValueProvider;
import org.aesh.command.Execution;
import org.aesh.command.HelpFeature;
import org.aesh.command.container.CommandContainer;
import org.aesh.command.impl.container.AeshCommandContainer;
import org.aesh.command.impl.container.AeshCommandContainerBuilder;
import org.aesh.command.impl.internal.ProcessedCommand;
import org.aesh.command.impl.parser.CommandLineParser;
import org.aesh.command.impl.registry.AeshCommandRegistryBuilder;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.metadata.CommandMetadataProvider;
import org.aesh.command.metadata.MetadataProviderRegistry;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Arguments;
import org.aesh.command.option.Mixin;
import org.aesh.command.option.Option;
import org.aesh.command.option.OptionGroup;
import org.aesh.command.option.OptionList;
import org.aesh.command.registry.CommandRegistry;
import org.aesh.complete.AeshCompleteOperation;
import org.aesh.terminal.formatting.TerminalString;
import org.aesh.terminal.tty.TtyDetect;

/**
 * The commands of the CLI and their sub-commands, as the CLI and its plugins create them: a command object for every
 * command. The tree runs a command line with aesh.
 */
public final class CommandTree {

    // the tree of every command object, for the defaults of its options (camel.<command path>.<option>)
    private static final Map<Object, CommandTree> TREES = Collections.synchronizedMap(new WeakHashMap<>());

    // created when first needed, when the tree was made with a factory (see lazy)
    private Command<CommandInvocation> command;
    private final Supplier<?> factory;
    private final Map<String, CommandTree> subcommands = new LinkedHashMap<>();
    // the name the command is added under, which can differ from the name of its CommandDefinition (aesh #706)
    private String name;
    // the aliases the command is added under, else those of its CommandDefinition
    private String[] aliases;
    private CommandTree parent;
    private DefaultValueProvider defaultValueProvider;
    private String[] args;
    // the group of the command in the help of its parent, and its place there (aesh lists the groups in the order of
    // their first command)
    private String helpGroup;
    // where aesh prints what it prints itself, such as the help of --help (default: System.out)
    private Consumer<String> output;
    private int helpOrder = Integer.MAX_VALUE;

    @SuppressWarnings("unchecked")
    public CommandTree(Object command) {
        this.command = (Command<CommandInvocation>) command;
        this.factory = null;
        TREES.put(command, this);
    }

    private CommandTree(Supplier<?> factory) {
        this.factory = factory;
    }

    /**
     * A tree whose command the factory creates when it is first needed: when the command line runs it, or aesh lists it
     * (such as in the help of its parent). For the sub-commands of a command with many (camel get, camel cmd): a
     * command line runs one of them, and creating the others (loading their classes) costs more than running most of
     * them. Add it under a name, as {@link #getName()} would otherwise create the command for the name of its
     * {@link CommandDefinition}.
     *
     * @param factory creates the command
     */
    public static CommandTree lazy(Supplier<?> factory) {
        return new CommandTree(factory);
    }

    /**
     * The tree of the command object, or {@code null} when it is not in one.
     */
    public static CommandTree of(Object command) {
        return command != null ? TREES.get(command) : null;
    }

    /**
     * Adds a sub-command under the given name, which can differ from the name of its {@link CommandDefinition}.
     */
    public CommandTree addSubcommand(String name, CommandTree subcommand) {
        subcommand.name = name;
        subcommand.parent = this;
        subcommands.put(name, subcommand);
        return this;
    }

    /**
     * Adds a sub-command under the given name and aliases. The sub-commands are in the map under the name, and after
     * that under each alias.
     */
    public CommandTree addSubcommand(String name, CommandTree subcommand, String... aliases) {
        addSubcommand(name, subcommand);
        subcommand.aliases = aliases;
        for (String alias : aliases) {
            subcommands.put(alias, subcommand);
        }
        return this;
    }

    /**
     * The group of this command in the help of its parent, and its place in the list of the sub-commands there.
     */
    public void setHelpGroup(String helpGroup, int helpOrder) {
        this.helpGroup = helpGroup;
        this.helpOrder = helpOrder;
    }

    /**
     * Where aesh prints what it prints itself when it runs the command, such as the help of {@code --help}: the printer
     * of the CLI.
     */
    public CommandTree setOutput(Consumer<String> output) {
        this.output = output;
        return this;
    }

    public Map<String, CommandTree> getSubcommands() {
        return Collections.unmodifiableMap(subcommands);
    }

    @SuppressWarnings("unchecked")
    public Command<CommandInvocation> getCommand() {
        if (command == null) {
            command = (Command<CommandInvocation>) factory.get();
            TREES.put(command, this);
        }
        return command;
    }

    public CommandTree getParent() {
        return parent;
    }

    public String getName() {
        return name != null ? name : nameOf(getCommand());
    }

    /**
     * The names of the commands from the root to this one, such as {@code camel get route}.
     */
    public String getQualifiedName() {
        return parent != null ? parent.getQualifiedName() + " " + getName() : getName();
    }

    /**
     * The defaults of the options that are not given (the user configuration, camel config set).
     */
    public void setDefaultValueProvider(DefaultValueProvider defaultValueProvider) {
        this.defaultValueProvider = defaultValueProvider;
    }

    public DefaultValueProvider getDefaultValueProvider() {
        return parent != null ? parent.getDefaultValueProvider() : defaultValueProvider;
    }

    /**
     * Runs the command line: the arguments after the name of the root command.
     *
     * @return the result, with the exit code of the command
     */
    /**
     * The arguments of the command line that the root of the tree runs.
     */
    public List<String> getArgs() {
        return parent != null ? parent.getArgs() : args != null ? List.of(args) : null;
    }

    public CommandResult execute(String... args) throws Exception {
        this.args = args;
        AeshCommandRuntimeBuilder<CommandInvocation> builder = AeshCommandRuntimeBuilder.<CommandInvocation> builder()
                .commandRegistry(registry(args));
        if (output != null) {
            builder.shell(new OutputShell(output));
        }
        CommandRuntime<CommandInvocation> runtime = builder.build();
        try {
            return runtime.executeCommand(getName(), args);
        } finally {
            runtime.close();
        }
    }

    /**
     * Parses the command line into the fields of the commands, without running them: the arguments after the name of
     * the root command.
     */
    public void parse(String... args) throws Exception {
        this.args = args;
        CommandRuntime<CommandInvocation> runtime = AeshCommandRuntimeBuilder.<CommandInvocation> builder()
                .commandRegistry(registry(args))
                .build();
        try {
            for (Execution<CommandInvocation> execution : runtime.buildExecutor(getName(), args).getExecutions()) {
                execution.populateCommand();
            }
        } finally {
            runtime.close();
        }
    }

    /**
     * The completion of a command line, as aesh completes it at Tab: the command line starts with the name of the root
     * command, such as {@code camel get route --so}.
     *
     * @return the candidates for the word at the cursor, and the offset in the command line where that word starts
     */
    public Completion complete(String line, int cursor) throws Exception {
        CommandRuntime<CommandInvocation> runtime = AeshCommandRuntimeBuilder.<CommandInvocation> builder()
                .commandRegistry(registry(null))
                .build();
        try {
            AeshCompleteOperation operation = new AeshCompleteOperation(line, cursor);
            runtime.complete(operation);
            List<String> candidates = new ArrayList<>();
            for (TerminalString candidate : operation.getCompletionCandidates()) {
                candidates.add(candidate.getCharacters());
            }
            return new Completion(candidates, operation.getOffset(), operation.isAppendSeparator());
        } finally {
            runtime.close();
        }
    }

    /**
     * The candidates of a completion, the offset of the word they complete, and whether a space follows a single one.
     */
    public record Completion(List<String> candidates, int offset, boolean appendSeparator) {
    }

    /**
     * The registry of this command, with only the sub-commands that the command line runs (see
     * {@link #container(AeshCommandContainerBuilder, String[])}).
     *
     * @param args the arguments after the name of this command, or {@code null} for all sub-commands
     */
    private CommandRegistry<CommandInvocation> registry(String[] args) throws Exception {
        AeshCommandContainerBuilder<CommandInvocation> builder = newBuilder();
        DefaultValueProvider provider = getDefaultValueProvider();
        if (provider != null) {
            builder.setDefaultValueProvider(provider);
        }
        AeshCommandRegistryBuilder<CommandInvocation> registry = AeshCommandRegistryBuilder.builder();
        if (provider != null) {
            registry.defaultValueProvider(provider);
        }
        return registry.command(rootContainer(builder, args)).create();
    }

    /**
     * The parser of this command and its sub-commands, such as for the completion scripts that aesh generates.
     */
    public CommandLineParser<CommandInvocation> parser() throws Exception {
        AeshCommandContainerBuilder<CommandInvocation> builder = newBuilder();
        DefaultValueProvider provider = getDefaultValueProvider();
        if (provider != null) {
            builder.setDefaultValueProvider(provider);
        }
        return rootContainer(builder, null).getParser();
    }

    /**
     * The usage of this command, as at the top of its help, without colors: for a command line that cannot be parsed,
     * as picocli printed it.
     *
     * @return the lines from {@code Usage:} on, or {@code null}
     */
    public String synopsis() throws Exception {
        CommandTree root = this;
        List<String> path = new ArrayList<>();
        while (root.parent != null) {
            path.add(0, root.getName());
            root = root.parent;
        }
        CommandLineParser<CommandInvocation> parser = root.parser();
        for (String name : path) {
            parser = parser.getChildParser(name);
            if (parser == null) {
                return null;
            }
        }
        parser.updateAnsiMode(false);
        String help = parser.printHelp();
        int start = help.indexOf("Usage:");
        if (start < 0) {
            return null;
        }
        String sep = System.lineSeparator();
        int end = help.indexOf(sep + sep, start);
        return (end > 0 ? help.substring(start, end) : help.substring(start)).stripTrailing();
    }

    /**
     * The model that aesh makes of this command alone (its options and arguments, without its sub-commands), for the
     * metadata of the commands.
     */
    public ProcessedCommand<?, ?> processedCommand() throws Exception {
        checkMetadataOf(getCommand());
        return newBuilder().create(getCommand(), getName(), aliasesOf(getCommand()))
                .getParser().getProcessedCommand();
    }

    /**
     * The builder of the aesh model of the commands: the help shows the default values, and an option given twice is a
     * usage error, as on picocli.
     */
    private static AeshCommandContainerBuilder<CommandInvocation> newBuilder() {
        AeshCommandContainerBuilder<CommandInvocation> builder = new AeshCommandContainerBuilder<>();
        builder.setHelpDefaults(EnumSet.of(HelpFeature.SHOW_DEFAULT_VALUES, HelpFeature.FAIL_ON_DUPLICATE_OPTIONS));
        return builder;
    }

    /**
     * The container of this command and its sub-commands, with the colors of the help: as aesh decides in its runner,
     * no colors when stdout is not a terminal (such as a pipe or a file), or with NO_COLOR or CI set.
     */
    private CommandContainer<CommandInvocation> rootContainer(
            AeshCommandContainerBuilder<CommandInvocation> builder, String[] args)
            throws Exception {
        CommandContainer<CommandInvocation> container = container(builder, args);
        boolean colors = EnvironmentHelper.isColorEnabled()
                && (System.getenv("FORCE_COLOR") != null || TtyDetect.isStdoutTty());
        container.getParser().updateAnsiMode(colors);
        return container;
    }

    /**
     * The container of this command, with the containers of its sub-commands. When the arguments start with the name of
     * a sub-command (camel get route), only that one: aesh reads the annotations of each command it gets, which for the
     * 40 sub-commands of camel get or camel cmd costs more than running most of them. Otherwise (no arguments, an
     * option such as --help, or a name that is not a sub-command, for which aesh suggests the sub-commands) all.
     *
     * @param args the arguments after the name of this command, or {@code null} for all sub-commands
     */
    private CommandContainer<CommandInvocation> container(
            AeshCommandContainerBuilder<CommandInvocation> builder, String[] args)
            throws Exception {
        checkMetadataOf(getCommand());
        CommandContainer<CommandInvocation> container
                = builder.create(getCommand(), getName(), aliases != null ? aliases : aliasesOf(getCommand()));
        if (helpGroup != null) {
            container.getParser().getProcessedCommand().setHelpGroup(helpGroup);
        }
        if (getCommand() instanceof ParsedCommandAware aware) {
            aware.setProcessedCommand(container.getParser().getProcessedCommand(), this);
        }
        CommandTree only = args != null && args.length > 0 && args[0] != null && !args[0].startsWith("-")
                ? subcommands.get(args[0]) : null;
        if (only != null) {
            ((AeshCommandContainer<CommandInvocation>) container)
                    .addChild(only.container(builder, Arrays.copyOfRange(args, 1, args.length)));
            return container;
        }
        // once each, also when added under aliases
        List<CommandTree> children = new ArrayList<>(new LinkedHashSet<>(subcommands.values()));
        children.sort(Comparator.comparingInt(c -> c.helpOrder));
        for (CommandTree sub : children) {
            ((AeshCommandContainer<CommandInvocation>) container).addChild(sub.container(builder, null));
        }
        return container;
    }

    private static String[] aliasesOf(Object command) {
        CommandMetadataProvider<?> provider = providerOf(command);
        if (provider != null) {
            return provider.commandAliases();
        }
        CommandDefinition definition = definitionOf(command.getClass());
        return definition != null ? definition.aliases() : new String[0];
    }

    private static String nameOf(Object command) {
        CommandMetadataProvider<?> provider = providerOf(command);
        if (provider != null) {
            return provider.commandName();
        }
        CommandDefinition definition = definitionOf(command.getClass());
        return definition != null ? definition.name() : null;
    }

    /**
     * Fails when aesh would use the generated metadata of a super class for the command (aesh looks up the class, and
     * then its super classes), while the command has options of its own, which aesh would then not know: such as a
     * plugin command that extends a command of the CLI. See {@link CommandMetadataRegistry}.
     */
    public static void checkMetadataOf(Object command) {
        CommandMetadataProvider<?> provider = providerOf(command);
        if (provider == null || provider.commandType() == command.getClass()) {
            return;
        }
        for (Class<?> c = command.getClass(); c != null && c != provider.commandType(); c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (isOption(field)) {
                    throw new IllegalStateException(
                            "The command " + command.getClass().getName() + " has options of its own (" + field.getName()
                                                    + "), but no generated aesh metadata, so aesh would use the metadata of "
                                                    + provider.commandType().getName()
                                                    + ", which does not have them: add the class of the command it extends to"
                                                    + " the excluded classes of the CommandMetadataRegistry");
                }
            }
        }
    }

    private static boolean isOption(Field field) {
        // a field without annotations first: the aesh annotation classes are then not loaded for it
        if (field.getDeclaredAnnotations().length == 0) {
            return false;
        }
        return field.isAnnotationPresent(Option.class) || field.isAnnotationPresent(OptionList.class)
                || field.isAnnotationPresent(OptionGroup.class) || field.isAnnotationPresent(Argument.class)
                || field.isAnnotationPresent(Arguments.class) || field.isAnnotationPresent(Mixin.class);
    }

    /**
     * The aesh metadata of the command, generated at compile time (see {@link CommandMetadataRegistry}): reading the
     * annotation instead creates a proxy class at run time.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static CommandMetadataProvider<?> providerOf(Object command) {
        return command instanceof Command ? MetadataProviderRegistry.getProvider((Class) command.getClass()) : null;
    }

    /**
     * The CommandDefinition of the command class, or of the class it extends (such as a sub class in a test): it is not
     * an inherited annotation.
     */
    public static CommandDefinition definitionOf(Class<?> type) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            CommandDefinition definition = c.getAnnotation(CommandDefinition.class);
            if (definition != null) {
                return definition;
            }
        }
        return null;
    }

    /**
     * A command that wants the model of its options once aesh has processed them.
     */
    public interface ParsedCommandAware {
        void setProcessedCommand(ProcessedCommand<?, ?> processedCommand, CommandTree tree);
    }
}
