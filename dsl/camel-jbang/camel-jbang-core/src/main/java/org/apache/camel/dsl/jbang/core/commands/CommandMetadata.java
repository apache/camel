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

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.aesh.command.CommandDefinition;
import org.aesh.command.impl.internal.ProcessedCommand;
import org.aesh.command.impl.internal.ProcessedOption;
import org.aesh.command.option.Mixin;
import org.aesh.command.option.Option;
import org.aesh.command.option.OptionList;
import org.aesh.command.option.OptionVisibility;
import org.apache.camel.dsl.jbang.core.common.CommandTree;
import org.apache.camel.dsl.jbang.core.common.Plugin;

/**
 * The commands of the CLI as plain data, read from the command model the CLI runs: the camel-package-maven-plugin
 * writes the metadata (and so the documentation) of the commands from it. Only this class knows how the CLI defines its
 * commands.
 * <p/>
 * A command is a map with {@code name}, {@code description}, {@code hidden}, {@code deprecated}, {@code sourceClass},
 * {@code options} and {@code subcommands}. An option is a map with {@code names} (comma separated),
 * {@code description}, {@code defaultValue}, {@code javaType}, {@code required}, {@code hidden}, {@code deprecated},
 * {@code paramLabel} and {@code completionCandidates}. Descriptions and default values are as written in the
 * annotations, with their placeholders, such as {@code ${camel-version}}.
 */
public final class CommandMetadata {

    private CommandMetadata() {
    }

    /**
     * The commands of the CLI, without the commands of the plugins.
     */
    public static List<Map<String, Object>> commands() throws Exception {
        return commands(new CamelJBangMain().createCommandLine(null));
    }

    /**
     * The commands that a plugin adds to the CLI.
     *
     * @param pluginClass the class of the plugin
     */
    public static List<Map<String, Object>> pluginCommands(Class<?> pluginClass) throws Exception {
        Plugin plugin = (Plugin) pluginClass.getDeclaredConstructor().newInstance();
        CamelJBangMain main = new CamelJBangMain();
        CommandTree commandLine = new CommandTree(main);
        plugin.customize(commandLine, main);
        return commands(commandLine);
    }

    private static List<Map<String, Object>> commands(CommandTree parent) throws Exception {
        List<Map<String, Object>> answer = new ArrayList<>();
        Set<CommandTree> added = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Map.Entry<String, CommandTree> entry : parent.getSubcommands().entrySet()) {
            if (added.add(entry.getValue())) {
                answer.add(command(entry.getKey(), entry.getValue()));
            }
        }
        return answer;
    }

    private static Map<String, Object> command(String name, CommandTree tree) throws Exception {
        Object command = tree.getCommand();
        CommandDefinition annotation = CommandTree.definitionOf(command.getClass());
        ProcessedCommand<?, ?> processed = tree.processedCommand();
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("name", name);
        answer.put("description", annotation != null ? annotation.description() : processed.description());
        // aesh has no hidden commands
        answer.put("hidden", false);
        answer.put("deprecated", command.getClass().isAnnotationPresent(Deprecated.class));
        answer.put("sourceClass", command.getClass().getCanonicalName());
        List<Map<String, Object>> options = new ArrayList<>();
        for (ProcessedOption option : processed.getOptions()) {
            options.add(option(command, option));
        }
        answer.put("options", options);
        answer.put("subcommands", commands(tree));
        return answer;
    }

    private static Map<String, Object> option(Object command, ProcessedOption option) {
        Field field = field(command, option);
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("names", names(option));
        String description = option.description() != null ? option.description() : "";
        answer.put("description", description);
        List<String> defaults = rawDefaultValues(field, option);
        answer.put("defaultValue", defaults == null || defaults.isEmpty() ? null : String.join(",", defaults));
        // the help option that aesh generates is a Boolean without a field
        answer.put("javaType", isGeneratedHelp(option)
                ? "boolean"
                : (field != null ? field.getType() : option.type()).getCanonicalName());
        answer.put("required", option.isRequired());
        answer.put("hidden", option.getVisibility() == OptionVisibility.HIDDEN);
        answer.put("deprecated", field != null && field.isAnnotationPresent(Deprecated.class));
        // aesh does not read the argument of @Option into its model: the label from the annotation
        String label = field != null && field.getAnnotation(Option.class) instanceof Option o
                ? o.argument()
                : option.getArgument();
        answer.put("paramLabel", label == null || label.isEmpty() ? null : label);
        if (description.contains("${COMPLETION-CANDIDATES}")) {
            List<String> candidates = new ArrayList<>();
            if (option.completer() instanceof Iterable<?> iterable) {
                iterable.forEach(c -> candidates.add(String.valueOf(c)));
            } else if (option.hasAllowedValues()) {
                candidates.addAll(option.getAllowedValues());
            }
            if (!candidates.isEmpty()) {
                answer.put("completionCandidates", candidates);
            }
        }
        return answer;
    }

    /**
     * The names as the CLI declares them: the long names, then the short name; the help option that aesh generates as
     * {@code -h,--help}.
     */
    private static String names(ProcessedOption option) {
        List<String> names = new ArrayList<>();
        boolean help = isGeneratedHelp(option);
        if (help && option.shortName() != null) {
            names.add("-" + option.shortName());
        }
        names.add("--" + option.name());
        for (String alias : option.getAliases()) {
            names.add("--" + alias);
        }
        if (!help && option.shortName() != null) {
            names.add("-" + option.shortName());
        }
        return String.join(",", names);
    }

    /**
     * The default value as written in the annotation, with its placeholders: aesh fills in a variable such as
     * {@code ${camel-version}} from the system properties of the JVM that builds the documentation (or drops it), and
     * keeps the value as written to itself.
     */
    private static List<String> rawDefaultValues(Field field, ProcessedOption option) {
        if (field != null) {
            if (field.getAnnotation(Option.class) instanceof Option o) {
                return List.of(o.defaultValue());
            } else if (field.getAnnotation(OptionList.class) instanceof OptionList o) {
                return List.of(o.defaultValue());
            }
        }
        return option.getDefaultValues();
    }

    private static boolean isGeneratedHelp(ProcessedOption option) {
        return "generatedHelp".equals(option.getFieldName());
    }

    /**
     * The field of the option: in the class of the command (or a super class), or in the class of one of its mixins,
     * also a mixin of a mixin.
     */
    private static Field field(Object command, ProcessedOption option) {
        if (option.getFieldName() == null) {
            return null;
        }
        return findOptionField(command.getClass(), option.getFieldName());
    }

    private static Field findOptionField(Class<?> type, String name) {
        Field answer = findField(type, name);
        if (answer != null) {
            return answer;
        }
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field mixin : c.getDeclaredFields()) {
                if (mixin.isAnnotationPresent(Mixin.class)) {
                    answer = findOptionField(mixin.getType(), name);
                    if (answer != null) {
                        return answer;
                    }
                }
            }
        }
        return null;
    }

    private static Field findField(Class<?> type, String name) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                // look in the super class
            }
        }
        return null;
    }
}
