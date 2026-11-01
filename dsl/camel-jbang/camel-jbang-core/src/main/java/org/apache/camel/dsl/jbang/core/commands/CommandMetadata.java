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

import java.lang.reflect.AnnotatedElement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.dsl.jbang.core.common.Plugin;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;

/**
 * The commands of the CLI as plain data, read from the command model the CLI runs: the camel-package-maven-plugin
 * writes the metadata (and so the documentation) of the commands from it. Only this class knows how the CLI defines its
 * commands.
 * <p/>
 * A command is a map with {@code name}, {@code description}, {@code hidden}, {@code deprecated}, {@code sourceClass},
 * {@code options} and {@code subcommands}. An option is a map with {@code names} (comma separated),
 * {@code description}, {@code defaultValue}, {@code javaType}, {@code required}, {@code hidden}, {@code deprecated},
 * {@code paramLabel} and {@code completionCandidates}. Descriptions and default values are as written in the
 * annotations, with their placeholders: picocli would fill in a variable such as {@code ${camel-version}} from the
 * system properties of the JVM that builds the documentation.
 */
public final class CommandMetadata {

    private CommandMetadata() {
    }

    /**
     * The commands of the CLI, without the commands of the plugins.
     */
    public static List<Map<String, Object>> commands() {
        return commands(new CamelJBangMain().createCommandLine(null));
    }

    /**
     * The commands that a plugin adds to the CLI.
     *
     * @param pluginClass the class of the plugin
     */
    public static List<Map<String, Object>> pluginCommands(Class<?> pluginClass) throws ReflectiveOperationException {
        Plugin plugin = (Plugin) pluginClass.getDeclaredConstructor().newInstance();
        CamelJBangMain main = new CamelJBangMain();
        CommandLine commandLine = new CommandLine(main);
        plugin.customize(commandLine, main);
        return commands(commandLine);
    }

    private static List<Map<String, Object>> commands(CommandLine parent) {
        List<Map<String, Object>> answer = new ArrayList<>();
        Set<CommandLine> added = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Map.Entry<String, CommandLine> entry : parent.getSubcommands().entrySet()) {
            // a command is in the map under the name it was added with, and after that under its aliases
            if (added.add(entry.getValue())) {
                answer.add(command(entry.getKey(), entry.getValue()));
            }
        }
        return answer;
    }

    private static Map<String, Object> command(String name, CommandLine commandLine) {
        CommandSpec spec = commandLine.getCommandSpec();
        Object command = spec.userObject();
        CommandLine.Command annotation = command.getClass().getAnnotation(CommandLine.Command.class);
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("name", name);
        answer.put("description",
                String.join(" ", annotation != null ? annotation.description() : spec.usageMessage().description()));
        answer.put("hidden", spec.usageMessage().hidden());
        answer.put("deprecated", command.getClass().isAnnotationPresent(Deprecated.class));
        answer.put("sourceClass", command.getClass().getCanonicalName());
        List<Map<String, Object>> options = new ArrayList<>();
        for (OptionSpec option : spec.options()) {
            // an option of a parent command for its sub-commands (scope = INHERIT) belongs to the parent
            if (!option.inherited()) {
                options.add(option(option));
            }
        }
        answer.put("options", options);
        answer.put("subcommands", commands(commandLine));
        return answer;
    }

    private static Map<String, Object> option(OptionSpec option) {
        CommandLine.Option annotation = option.userObject() instanceof AnnotatedElement element
                ? element.getAnnotation(CommandLine.Option.class) : null;
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("names", String.join(",", option.names()));
        String description = String.join(" ", annotation != null ? annotation.description() : option.description());
        answer.put("description", description);
        answer.put("defaultValue", annotation != null ? defaultValue(annotation) : option.defaultValue());
        answer.put("javaType", option.type().getCanonicalName());
        answer.put("required", option.required());
        answer.put("hidden", option.hidden());
        answer.put("deprecated", option.userObject() instanceof AnnotatedElement element
                && element.isAnnotationPresent(Deprecated.class));
        // the label picocli makes up for an option without one is not documented
        answer.put("paramLabel", annotation != null && !annotation.paramLabel().isEmpty() ? annotation.paramLabel() : null);
        if (description.contains("${COMPLETION-CANDIDATES}") && option.completionCandidates() != null) {
            List<String> candidates = new ArrayList<>();
            option.completionCandidates().forEach(candidates::add);
            answer.put("completionCandidates", candidates);
        }
        return answer;
    }

    private static String defaultValue(CommandLine.Option annotation) {
        // how picocli marks an option without a default value
        return "__no_default_value__".equals(annotation.defaultValue()) ? null : annotation.defaultValue();
    }
}
