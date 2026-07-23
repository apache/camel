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
            registerKey(commands, descriptor.name(), descriptor);
            for (String alias : descriptor.aliases()) {
                registerKey(commands, alias, descriptor);
            }
        }
        lookup = Collections.unmodifiableMap(commands);
    }

    private static void registerKey(Map<String, Descriptor> commands, String key, Descriptor descriptor) {
        String normalized = key.toLowerCase(Locale.ROOT);
        Descriptor existing = commands.putIfAbsent(normalized, descriptor);
        if (existing != null) {
            throw new IllegalStateException(
                    "Slash command name/alias '" + normalized + "' is already registered to /" + existing.name()
                                            + "; cannot also register it to /" + descriptor.name());
        }
    }

    static AiSlashCommandRegistry defaults() {
        List<Descriptor> commands = new ArrayList<>();
        // The /help executor closes over this local list rather than calling the static defaults() factory
        // recursively, so it reflects this registry's own commands instead of rebuilding a whole new one.
        commands.add(new Descriptor(
                "help", List.of("h"), "Show available slash commands", null,
                (context, arguments) -> CommandResult.system(buildHelpText(commands))));
        commands.add(new Descriptor(
                "provider", List.of("p"), "Switch the AI provider", null,
                (context, arguments) -> {
                    context.openProviderSwitch();
                    return CommandResult.system("");
                }));
        commands.add(new Descriptor(
                "model", List.of("m"), "Show or switch the AI model", "<model>",
                AiSlashCommandRegistry::executeModel));
        commands.add(new Descriptor(
                "clear", List.of("c"), "Clear the conversation", null,
                (context, arguments) -> {
                    context.clearConversation();
                    return CommandResult.system("");
                }));
        commands.add(new Descriptor(
                "close", List.of(), "Close the AI panel", null,
                (context, arguments) -> {
                    context.closePanel();
                    return CommandResult.system("Closing AI panel");
                }));
        commands.add(new Descriptor(
                "quit", List.of("q", "x", "exit"), "Exit the TUI", null,
                (context, arguments) -> {
                    context.requestExit();
                    return CommandResult.system("Exiting TUI");
                }));
        commands.add(new Descriptor(
                "run", List.of("r"), "<camel run args>",
                "<files...|--example=name> [--name=...] [--dev] [--port=8080] [...]",
                (context, arguments) -> CommandResult.system(context.launchDetached(LaunchSpec.forRun(arguments)))));
        commands.add(new Descriptor(
                "infra", List.of("i"), "Manage Camel infrastructure", "<camel infra args>",
                (context, arguments) -> {
                    if (isInfraRun(arguments)) {
                        return CommandResult.system(context.launchDetached(LaunchSpec.forInfra(arguments)));
                    }
                    return CommandResult.async(AiCliCommandExecutor.Request.infra(arguments));
                }));
        commands.add(new Descriptor(
                "send", List.of("s"), "Send a message to an endpoint", SEND_USAGE,
                (context, arguments) -> CommandResult.async(
                        AiCliCommandExecutor.Request.send(context.selectedProcessName(), parseSend(arguments)))));
        return new AiSlashCommandRegistry(commands);
    }

    static AiSlashCommandRegistry forTesting(List<Descriptor> descriptors) {
        return new AiSlashCommandRegistry(descriptors);
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
        } catch (RuntimeException e) {
            // Catches both the IllegalArgumentException that /send's argument parsing throws for bad input
            // and any other unexpected failure from an executor, so a misbehaving command reports an error
            // instead of propagating uncaught into the TUI's event loop.
            return CommandResult.error(e.getMessage());
        }
    }

    String helpText() {
        return buildHelpText(descriptors);
    }

    private static String buildHelpText(List<Descriptor> items) {
        int width = commandColumnWidth(items);
        StringBuilder text = new StringBuilder("Available commands:\n");
        for (Descriptor descriptor : items) {
            text.append(formatAlignedLine(descriptor, width)).append('\n');
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

    static String commandLabel(Descriptor descriptor) {
        return helpUsage(descriptor);
    }

    static String descriptionLabel(Descriptor descriptor) {
        String description = descriptor.description();
        if (description.startsWith("<")) {
            return "";
        }
        return description;
    }

    static int commandColumnWidth(List<Descriptor> items) {
        return items.stream().mapToInt(descriptor -> commandLabel(descriptor).length()).max().orElse(0);
    }

    static String formatAlignedLine(Descriptor descriptor, int width) {
        String command = commandLabel(descriptor);
        String description = descriptionLabel(descriptor);
        if (description.isEmpty()) {
            return command;
        }
        int padding = Math.max(2, width - command.length() + 2);
        return command + " ".repeat(padding) + description;
    }

    private static String helpUsage(Descriptor descriptor) {
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

    private static boolean isInfraRun(String arguments) {
        List<String> tokens = AiCliCommandExecutor.Request.splitRawTail(arguments);
        return !tokens.isEmpty() && "run".equalsIgnoreCase(tokens.get(0));
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
            if (!context.switchModel(arguments)) {
                return CommandResult.error("No LLM client available. Press Ctrl+P to pick a provider.");
            }
            return CommandResult.system("Switched model to " + arguments);
        }
        return CommandResult.listModels();
    }

    private static int firstWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * A {@code description} starting with {@code "<"} is treated as an inline usage string rather than prose: it
     * replaces {@code placeholder} in {@code /help} output and suppresses the description column (see
     * {@link #descriptionLabel(Descriptor)} / {@code helpUsage}). Use this for commands whose full argument grammar is
     * documented via {@code placeholder} but is too long to also show as a separate description (see {@code run},
     * {@code infra}, {@code send}).
     */
    record Descriptor(String name, List<String> aliases, String description, String placeholder, Executor executor) {
        String usage() {
            return placeholder == null || placeholder.isBlank() ? "/" + name : "/" + name + " " + placeholder;
        }
    }

    record ParsedCommand(Descriptor descriptor, String rawName, String arguments) {
    }

    record SendCommand(String endpoint, String body, boolean fileBody) {
    }

    /**
     * A long-running command to launch as a detached background process. {@code camelArgs} is the full argument vector
     * passed to the {@code camel} CLI (e.g. {@code [run, --example=..., --name=..., --logging-color=true]}).
     * {@code exampleName} is non-null when the launch targets a catalog example, so the panel can start any infra
     * services the example requires before launching.
     */
    record LaunchSpec(String displayName, List<String> camelArgs, String exampleName) {

        LaunchSpec {
            camelArgs = List.copyOf(camelArgs);
        }

        static LaunchSpec forRun(String arguments) {
            List<String> tokens = AiCliCommandExecutor.Request.splitRawTail(arguments);
            List<String> args = new ArrayList<>();
            args.add("run");
            args.addAll(tokens);
            String exampleName = extractOption(tokens, "--example");
            if (exampleName != null && exampleName.contains("/")
                    && tokens.stream().noneMatch(token -> token.startsWith("--name"))) {
                args.add("--name=" + TuiHelper.stripCategory(exampleName));
            }
            if (tokens.stream().noneMatch(token -> token.startsWith("--logging-color"))) {
                args.add("--logging-color=true");
            }
            String displayName = exampleName != null
                    ? exampleName
                    : (tokens.isEmpty() ? "run" : String.join(" ", tokens));
            return new LaunchSpec(displayName, args, exampleName);
        }

        static LaunchSpec forInfra(String arguments) {
            List<String> tokens = AiCliCommandExecutor.Request.splitRawTail(arguments);
            List<String> args = new ArrayList<>();
            args.add("infra");
            args.addAll(tokens);
            String displayName = ("infra " + String.join(" ", tokens)).strip();
            return new LaunchSpec(displayName, args, null);
        }

        /**
         * Returns the value of {@code --option=value} or {@code --option value} from the given tokens, or {@code null}
         * when the option is absent.
         */
        private static String extractOption(List<String> tokens, String option) {
            String inline = option + "=";
            for (int i = 0; i < tokens.size(); i++) {
                String token = tokens.get(i);
                if (token.startsWith(inline)) {
                    return token.substring(inline.length());
                }
                if (token.equals(option) && i + 1 < tokens.size()) {
                    return tokens.get(i + 1);
                }
            }
            return null;
        }
    }

    interface Executor {
        CommandResult execute(AiSlashCommandContext context, String arguments);
    }

    record CommandResult(AiRole role, String text, AiCliCommandExecutor.Request cliRequest, boolean modelListing) {
        static CommandResult system(String text) {
            return new CommandResult(AiRole.SYSTEM, text, null, false);
        }

        static CommandResult error(String text) {
            return new CommandResult(AiRole.ERROR, text, null, false);
        }

        static CommandResult async(AiCliCommandExecutor.Request request) {
            return new CommandResult(AiRole.SYSTEM, "Running " + request.displayText(), request, false);
        }

        /**
         * Signals the panel to fetch the available models off the event thread, since model discovery reaches a
         * blocking network call that must not freeze rendering and input. The panel formats the result via
         * {@link AiSlashCommandRegistry#formatModelListing(String, List)}.
         */
        static CommandResult listModels() {
            return new CommandResult(AiRole.SYSTEM, null, null, true);
        }
    }

    static String formatModelListing(String currentModel, List<String> models) {
        if (models.isEmpty()) {
            return "Current model: " + currentModel;
        }
        StringBuilder text = new StringBuilder("Current model: ").append(currentModel).append("\nAvailable models:");
        for (String model : models) {
            text.append("\n- ").append(model);
        }
        return text.toString();
    }
}
