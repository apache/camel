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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

final class AiSlashCommandRegistry {

    private static final String SEND_USAGE = "<endpoint> <message text | @file>";
    private final List<Descriptor> descriptors;
    private final Map<String, Descriptor> lookup;

    private AiSlashCommandRegistry(List<Descriptor> descriptors) {
        this.descriptors = List.copyOf(descriptors);
        Map<String, Descriptor> commands = new LinkedHashMap<>();
        for (Descriptor descriptor : descriptors) {
            commands.put(descriptor.name().toLowerCase(Locale.ROOT), descriptor);
            for (String alias : descriptor.aliases()) {
                commands.put(alias.toLowerCase(Locale.ROOT), descriptor);
            }
        }
        lookup = Collections.unmodifiableMap(commands);
    }

    static AiSlashCommandRegistry defaults() {
        List<Descriptor> commands = new ArrayList<>();
        commands.add(new Descriptor(
                "help", List.of("h"), "Show available slash commands", null, false,
                (context, arguments) -> CommandResult.system(AiSlashCommandRegistry.defaults().helpText())));
        commands.add(new Descriptor(
                "provider", List.of("p"), "Switch the AI provider", null, false,
                (context, arguments) -> {
                    context.openProviderSwitch();
                    return CommandResult.system("");
                }));
        commands.add(new Descriptor(
                "model", List.of("m"), "Show or switch the AI model", "<model>", false,
                AiSlashCommandRegistry::executeModel));
        commands.add(new Descriptor(
                "clear", List.of("c"), "Clear the conversation", null, false,
                (context, arguments) -> {
                    context.clearConversation();
                    return CommandResult.system("");
                }));
        commands.add(new Descriptor(
                "close", List.of(), "Close the AI panel", null, false,
                (context, arguments) -> {
                    context.closePanel();
                    return CommandResult.system("Closing AI panel");
                }));
        commands.add(new Descriptor(
                "exit", List.of("x"), "Exit the TUI", null, false,
                (context, arguments) -> {
                    context.requestExit();
                    return CommandResult.system("Exiting TUI");
                }));
        commands.add(new Descriptor(
                "quit", List.of("q"), "Exit the TUI", null, false,
                (context, arguments) -> {
                    context.requestExit();
                    return CommandResult.system("Exiting TUI");
                }));
        commands.add(new Descriptor(
                "run", List.of("r"), "<camel run args>", "<files...> [--dev] [--port=8080] [...]", true,
                (context, arguments) -> CommandResult.async(AiCliCommandExecutor.Request.run(arguments))));
        commands.add(new Descriptor(
                "infra", List.of("i"), "Manage Camel infrastructure", "<camel infra args>", true,
                (context, arguments) -> CommandResult.async(AiCliCommandExecutor.Request.infra(arguments))));
        commands.add(new Descriptor(
                "send", List.of("s"), "Send a message to an endpoint", SEND_USAGE, true,
                (context, arguments) -> CommandResult.async(
                        AiCliCommandExecutor.Request.send(context.selectedProcessName(), parseSend(arguments)))));
        return new AiSlashCommandRegistry(commands);
    }

    List<Descriptor> descriptors() {
        return descriptors;
    }

    Optional<Descriptor> lookup(String name) {
        if (name == null) {
            return Optional.empty();
        }
        String normalized = name.strip();
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return Optional.ofNullable(lookup.get(normalized.toLowerCase(Locale.ROOT)));
    }

    Optional<ParsedCommand> parse(String input) {
        if (input == null || !input.strip().startsWith("/")) {
            return Optional.empty();
        }
        String command = input.strip().substring(1);
        if (command.isBlank()) {
            return lookup("help").map(descriptor -> new ParsedCommand(descriptor, "", ""));
        }
        int separator = firstWhitespace(command);
        String rawName = separator < 0 ? command : command.substring(0, separator);
        String arguments = separator < 0 ? "" : command.substring(separator).trim();
        return lookup(rawName).map(descriptor -> new ParsedCommand(descriptor, rawName, arguments));
    }

    CommandResult execute(String input, AiSlashCommandContext context) {
        if (input == null || !input.strip().startsWith("/")) {
            return CommandResult.error("Not a slash command");
        }
        String command = input.strip();
        Optional<ParsedCommand> parsed = parse(command);
        if (parsed.isEmpty()) {
            String name = command.length() > 1 ? command.substring(1).split("\\s+", 2)[0] : "";
            return CommandResult.error("Unknown command: /" + name + ". Type /help for available commands.");
        }
        ParsedCommand value = parsed.get();
        try {
            return value.descriptor().executor().execute(context, value.arguments());
        } catch (IllegalArgumentException e) {
            return CommandResult.error(e.getMessage());
        }
    }

    String helpText() {
        StringBuilder text = new StringBuilder("Available commands:\n\n");
        for (Descriptor descriptor : descriptors) {
            text.append(helpLine(descriptor)).append("\n\n");
        }
        return text.toString().strip();
    }

    List<Descriptor> completionsFor(String input) {
        if (input == null || !input.startsWith("/")) {
            return List.of();
        }
        String body = input.substring(1);
        int separator = firstWhitespace(body);
        if (separator >= 0) {
            if (!body.substring(separator).isBlank()) {
                return List.of();
            }
            String command = body.substring(0, separator);
            if (lookup(command).isPresent()) {
                return List.of();
            }
            return matchDescriptors(command);
        }
        return matchDescriptors(body);
    }

    private List<Descriptor> matchDescriptors(String prefix) {
        String needle = prefix.strip().toLowerCase(Locale.ROOT);
        return descriptors.stream().filter(descriptor -> matchesPrefix(descriptor, needle)).toList();
    }

    private static boolean matchesPrefix(Descriptor descriptor, String needle) {
        if (needle.isEmpty()) {
            return true;
        }
        if (descriptor.name().startsWith(needle)) {
            return true;
        }
        for (String alias : descriptor.aliases()) {
            if (alias.startsWith(needle)) {
                return true;
            }
        }
        return false;
    }

    private String helpLine(Descriptor descriptor) {
        String command = helpUsage(descriptor);
        String description = descriptor.description();
        if (description.startsWith("<")) {
            return command;
        }
        return command + " — " + description;
    }

    private String helpUsage(Descriptor descriptor) {
        if (descriptor.description().startsWith("<")) {
            return "/" + descriptor.name() + " " + descriptor.description();
        }
        return descriptor.usage();
    }

    Optional<String> placeholderFor(String input) {
        if (input == null || !input.startsWith("/")) {
            return Optional.empty();
        }
        int separator = firstWhitespace(input.substring(1));
        if (separator < 0 || !input.substring(1 + separator).isBlank()) {
            return Optional.empty();
        }
        String name = input.substring(1, 1 + separator);
        return lookup(name).map(Descriptor::placeholder).filter(value -> value != null && !value.isBlank());
    }

    static SendCommand parseSend(String arguments) {
        String value = arguments == null ? "" : arguments.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Missing endpoint. Usage: /send " + SEND_USAGE);
        }
        int separator = firstWhitespace(value);
        if (separator < 0) {
            throw new IllegalArgumentException("Missing body. Usage: /send " + SEND_USAGE);
        }
        String endpoint = value.substring(0, separator);
        String body = value.substring(separator).trim();
        if (body.isEmpty()) {
            throw new IllegalArgumentException("Missing body. Usage: /send " + SEND_USAGE);
        }
        boolean fileBody = body.startsWith("@") && body.length() > 1
                && body.chars().noneMatch(character -> Character.isWhitespace((char) character));
        return new SendCommand(endpoint, fileBody ? body.substring(1) : body, fileBody);
    }

    private static CommandResult executeModel(AiSlashCommandContext context, String arguments) {
        if (!arguments.isBlank()) {
            context.switchModel(arguments);
            return CommandResult.system("Switched model to " + arguments);
        }
        List<String> models = context.availableModels();
        if (models.isEmpty()) {
            return CommandResult.system("Current model: " + context.currentModel());
        }
        StringBuilder text = new StringBuilder("Current model: ").append(context.currentModel())
                .append("\nAvailable models:");
        for (String model : models) {
            text.append("\n- ").append(model);
        }
        return CommandResult.system(text.toString());
    }

    private static int firstWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    record Descriptor(String name, List<String> aliases, String description, String placeholder,
            boolean asynchronous, Executor executor) {
        String usage() {
            return placeholder == null || placeholder.isBlank() ? "/" + name : "/" + name + " " + placeholder;
        }
    }

    record ParsedCommand(Descriptor descriptor, String rawName, String arguments) {
    }

    record SendCommand(String endpoint, String body, boolean fileBody) {
    }

    interface Executor {
        CommandResult execute(AiSlashCommandContext context, String arguments);
    }

    record CommandResult(String role, String text, AiCliCommandExecutor.Request cliRequest) {
        static CommandResult system(String text) {
            return new CommandResult("system", text, null);
        }

        static CommandResult error(String text) {
            return new CommandResult("error", text, null);
        }

        static CommandResult async(AiCliCommandExecutor.Request request) {
            return new CommandResult("system", "Running " + request.displayText(), request);
        }
    }
}
