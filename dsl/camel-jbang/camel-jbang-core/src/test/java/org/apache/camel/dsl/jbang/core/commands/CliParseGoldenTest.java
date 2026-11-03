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

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.EnvironmentHelper;
import org.apache.camel.dsl.jbang.run.common.HawtioVersion;
import org.apache.camel.dsl.jbang.run.common.RuntimeType;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.StringHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Model.PositionalParamSpec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * Golden test of what the Camel CLI makes of a command line: the values of the fields of the commands once the command
 * line is parsed (not run). It pins the behaviour of the command-line parser, so that a change of the parser (such as
 * picocli to aesh, CAMEL-23810) shows every command line it parses differently.
 * <p>
 * The cases and their expected values are in src/test/resources/golden/cli-parse.txt:
 * <ul>
 * <li>{@code > args} is a case: the command line, without {@code camel};</li>
 * <li>the first case of a command gives it no options, and lists the value of every field of the command (the
 * defaults); the other cases list only the fields whose value differs from the defaults;</li>
 * <li>{@code error} is a command line the parser rejects, and the comment after it is the message of picocli, which is
 * not compared;</li>
 * <li>{@code @config} is the user configuration (camel config set) of the cases after it.</li>
 * </ul>
 * A field that the file does not list (a new option) is not compared, so a new option does not fail the test; a field
 * that is gone, or a value that changed, does. Only the parsing is specific to picocli
 * ({@link #parse(CommandLine, List)}); the fields are read from the command objects by the names of their annotations.
 * Run with -Dcamel.golden.update=true to write the cases again from the commands of the CLI and their options (with the
 * cases of a new option), and review the difference.
 */
class CliParseGoldenTest extends CamelCommandBaseTestSupport {

    private static final Path GOLDEN = Path.of("src/test/resources/golden/cli-parse.txt");
    private static final boolean UPDATE = Boolean.getBoolean("camel.golden.update");

    // the annotations of the fields that the command line sets, of picocli and of aesh
    private static final Set<String> OPTION_ANNOTATIONS
            = Set.of("Option", "Parameters", "Argument", "Arguments", "OptionList", "OptionGroup");
    private static final Set<String> GROUP_ANNOTATIONS = Set.of("Mixin", "ArgGroup");
    // the fields that the parameter consumers set, which have no annotation
    private static final Set<String> CONSUMED_FIELDS = Set.of("file", "files");

    // a case of a command line with the user configuration of the case
    record Case(String config, List<String> args) {
    }

    // the cases of hand, for what the generated cases do not cover: the parameter consumers, values that are not
    // options, several values, and the user configuration
    private static final List<Case> MORE = List.of(
            more("", "run", "a.yaml", "b.yaml"),
            more("", "run", "a.yaml", "--dev", "b.yaml"),
            more("", "run", "--jvm-debug", "a.yaml"),
            more("", "run", "--jvm-debug", "5006", "a.yaml"),
            more("", "run", "--jvm-debug=false", "a.yaml"),
            more("", "run", "--background-wait", "a.yaml"),
            more("", "run", "--background-wait", "30s", "a.yaml"),
            more("", "run", "--prop", "a=1", "--prop", "b=2"),
            more("", "run", "--dep", "camel:kafka", "--dep", "camel:http"),
            more("", "run", "--dep=camel:kafka,camel:http"),
            more("", "export", "a.yaml", "b.yaml", "--gav=com.acme:demo:1.0"),
            more("", "export", "--maven-wrapper"),
            more("", "export", "--maven-wrapper=false"),
            more("", "log", "--timestamp", "orders"),
            more("", "init", "a.yaml"),
            more("", "config", "set", "--global", "foo=bar"),
            more("", "config", "set", "--global=false", "foo=bar"),
            more("", "run", "--unknown-option"),
            more("", "no-such-command"),
            more("logging-level=debug", "run"),
            more("logging-level=debug", "run", "--logging-level=warn"),
            more("camel.export.maven-wrapper=false", "export"),
            more("camel.export.maven-wrapper=false", "export", "--maven-wrapper"),
            more("camel.run.dev=true", "run"),
            more("camel.run.dev=true", "run", "--dev=false"),
            more("camel.run.dev=true", "export"),
            more("name=foo", "plugin", "add", "bar"));

    private final Map<String, String> camelProperties = new TreeMap<>();

    @BeforeEach
    void clearCamelSystemProperties() throws Exception {
        // the camel system properties of the JVM, which the commands read: the cases run without them
        for (String key : System.getProperties().stringPropertyNames()) {
            if (key.startsWith("camel.") && !key.equals("camel.golden.update")) {
                camelProperties.put(key, System.getProperty(key));
                System.clearProperty(key);
            }
        }
        useTestHomeDir();
    }

    @AfterEach
    void restoreCamelSystemProperties() {
        camelProperties.forEach(System::setProperty);
    }

    @Test
    void theCommandLinesParseAsBefore() throws Exception {
        // the default of --logging-color of camel run comes from the environment (FORCE_COLOR), and is off in CI and in
        // the tests, which have no console
        assumeFalse(EnvironmentHelper.isColorEnabled(), "the colors of the logging are on (FORCE_COLOR)");
        if (UPDATE) {
            Files.writeString(GOLDEN, render(generate(), null), StandardCharsets.UTF_8);
            return;
        }

        List<String> golden = Files.readAllLines(GOLDEN, StandardCharsets.UTF_8);
        Map<String, List<String>> expected = results(golden);
        Map<String, List<String>> actual = results(List.of(render(read(golden), expected).split("\n")));

        List<String> differences = new ArrayList<>();
        expected.forEach((key, lines) -> {
            List<String> got = actual.get(key);
            if (!lines.equals(got)) {
                differences.add(key + "\n  expected: " + lines + "\n  actual:   " + got);
            }
        });
        assertThat(differences)
                .as("command lines that parse differently than in %s (run with -Dcamel.golden.update=true to update)",
                        GOLDEN)
                .isEmpty();
    }

    // the cases: the defaults and the options of every command, and the cases of hand
    private List<Case> generate() throws Exception {
        List<Case> cases = new ArrayList<>();
        CommandLine root = newCommandLine(null);
        Map<String, CommandLine> commands = new TreeMap<>();
        collect(root, "", commands, new IdentityHashMap<>());
        for (Map.Entry<String, CommandLine> command : commands.entrySet()) {
            CommandSpec spec = command.getValue().getCommandSpec();
            // the command with the values it requires
            List<String> base = new ArrayList<>(List.of(command.getKey().split(" ")));
            for (PositionalParamSpec positional : spec.positionalParameters()) {
                for (int i = 0; i < positional.arity().min(); i++) {
                    base.add(sample(positional.type(), positional.auxiliaryTypes(), null, 0));
                }
            }
            List<OptionSpec> options = spec.options().stream()
                    .filter(o -> !o.usageHelp() && !o.versionHelp() && !o.inherited())
                    .sorted((a, b) -> a.longestName().compareTo(b.longestName()))
                    .toList();
            for (OptionSpec option : options) {
                if (option.required()) {
                    base.add(option.longestName());
                    base.add(sample(option.type(), option.auxiliaryTypes(), option.completionCandidates(), 0));
                }
            }
            cases.add(new Case("", base));
            for (OptionSpec option : options) {
                if (option.required()) {
                    continue;
                }
                for (List<String> given : optionCases(option)) {
                    List<String> args = new ArrayList<>(base);
                    args.addAll(given);
                    cases.add(new Case("", args));
                }
            }
        }
        cases.addAll(MORE);
        return new ArrayList<>(new LinkedHashSet<>(cases));
    }

    // the commands of the CLI by the names they are added with (which is not always the name of the command), once
    // each: an alias is the same command
    private static void collect(
            CommandLine commandLine, String prefix, Map<String, CommandLine> commands, Map<CommandLine, Boolean> seen) {
        for (Map.Entry<String, CommandLine> sub : commandLine.getSubcommands().entrySet()) {
            if (seen.put(sub.getValue(), Boolean.TRUE) == null) {
                String path = prefix + sub.getKey();
                commands.put(path, sub.getValue());
                collect(sub.getValue(), path + " ", commands, seen);
            }
        }
    }

    // a boolean is given without a value and with false, an option with an optional value without a value and with
    // one, and any other option with a value (two values when it takes more than one)
    private static List<List<String>> optionCases(OptionSpec option) {
        String name = option.longestName();
        Class<?> type = option.type();
        if (type == boolean.class || type == Boolean.class) {
            return List.of(List.of(name), List.of(name + "=false"));
        }
        String value = sample(type, option.auxiliaryTypes(), option.completionCandidates(), 0);
        if (option.arity().min() == 0) {
            return List.of(List.of(name), List.of(name + "=" + value));
        }
        if (option.isMultiValue()) {
            String other = sample(type, option.auxiliaryTypes(), option.completionCandidates(), 1);
            return List.of(List.of(name, value, name, other));
        }
        return List.of(List.of(name, value));
    }

    // a value of the type: the first (or second) value that completion offers, else one that converts to the type
    private static String sample(Class<?> type, Class<?>[] auxiliaryTypes, Iterable<String> candidates, int index) {
        if (candidates != null) {
            try {
                Iterator<String> it = candidates.iterator();
                for (int i = 0; it.hasNext(); i++) {
                    String candidate = it.next();
                    if (i == index) {
                        return candidate;
                    }
                }
            } catch (Exception e) {
                // no completion
            }
        }
        Class<?> element = type;
        if ((Collection.class.isAssignableFrom(type) || type.isArray()) && auxiliaryTypes.length > 0) {
            element = auxiliaryTypes[0];
        }
        if (Map.class.isAssignableFrom(type)) {
            return index == 0 ? "k=v" : "k2=v2";
        }
        if (element.isEnum()) {
            Object[] constants = element.getEnumConstants();
            return constants[Math.min(index, constants.length - 1)].toString();
        }
        if (element == int.class || element == Integer.class || element == long.class || element == Long.class
                || element == short.class || element == Short.class) {
            return index == 0 ? "7" : "8";
        }
        if (element == double.class || element == Double.class || element == float.class || element == Float.class) {
            return index == 0 ? "1.5" : "2.5";
        }
        return index == 0 ? "v" : "w";
    }

    // the golden file: the cases and what they parse to, with only the fields of the expected file when it is given
    private String render(List<Case> cases, Map<String, List<String>> expected) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("# What the Camel CLI parses from a command line, see CliParseGoldenTest.\n");
        sb.append("# Written with -Dcamel.golden.update=true, from the commands of the CLI and their options.\n");
        String config = null;
        Map<String, Map<String, String>> defaults = new LinkedHashMap<>();
        Map<String, Set<String>> known = new LinkedHashMap<>();
        for (Case c : cases) {
            if (!c.config().equals(config)) {
                config = c.config();
                sb.append("\n@config").append(config.isEmpty() ? "" : " " + config).append('\n');
                UserConfigHelper.createUserConfig(config.replace(' ', '\n'));
                defaults.clear();
                known.clear();
            }
            sb.append("> ").append(String.join(" ", c.args())).append('\n');
            // new commands for every case, as picocli does not reset every field when it parses again (an option of an
            // ArgGroup without a default keeps its value)
            Parsed parsed = parse(newCommandLine(c.args().get(0)), c.args());
            if (parsed.error() != null) {
                sb.append("  error\n");
                sb.append("  # ").append(parsed.error().replace('\n', ' ')).append('\n');
                continue;
            }
            Map<String, String> fields = parsed.fields();
            Map<String, String> base = defaults.get(parsed.path());
            if (base == null) {
                defaults.put(parsed.path(), fields);
                List<String> lines = expected != null ? expected.get(key(config, c.args())) : null;
                if (lines != null) {
                    known.put(parsed.path(),
                            lines.stream().map(line -> StringHelper.before(line, "=", line)).collect(Collectors.toSet()));
                }
                Set<String> names = known.get(parsed.path());
                fields.forEach((k, v) -> {
                    if (names == null || names.contains(k)) {
                        sb.append("  ").append(k).append('=').append(v).append('\n');
                    }
                });
                continue;
            }
            Set<String> names = known.get(parsed.path());
            boolean same = true;
            for (Map.Entry<String, String> e : fields.entrySet()) {
                if (names != null && !names.contains(e.getKey())) {
                    continue;
                }
                if (!e.getValue().equals(base.get(e.getKey()))) {
                    sb.append("  ").append(e.getKey()).append('=').append(e.getValue()).append('\n');
                    same = false;
                }
            }
            if (same) {
                sb.append("  (defaults)\n");
            }
        }
        return sb.toString();
    }

    private static List<Case> read(List<String> lines) {
        List<Case> cases = new ArrayList<>();
        String config = "";
        for (String line : lines) {
            if (line.startsWith("@config")) {
                config = line.substring("@config".length()).trim();
            } else if (line.startsWith("> ")) {
                cases.add(new Case(config, List.of(line.substring(2).split(" "))));
            }
        }
        return cases;
    }

    // the result of every case, by its configuration and command line, without the comments
    private static Map<String, List<String>> results(List<String> lines) {
        Map<String, List<String>> results = new LinkedHashMap<>();
        String config = "";
        List<String> current = null;
        for (String line : lines) {
            if (line.startsWith("@config")) {
                config = line.substring("@config".length()).trim();
            } else if (line.startsWith("> ")) {
                current = new ArrayList<>();
                results.put(key(config, List.of(line.substring(2).split(" "))), current);
            } else if (current != null && line.startsWith("  ") && !line.startsWith("  # ")) {
                current.add(line.trim());
            }
        }
        return results;
    }

    private static String key(String config, List<String> args) {
        return "[" + config + "] " + String.join(" ", args);
    }

    private static Case more(String config, String... args) {
        return new Case(config, List.of(args));
    }

    // a command line of the named command of the CLI (or all of them), with the user configuration, as the CLI creates
    // it for a command line
    private CommandLine newCommandLine(String only) {
        CommandLine commandLine = new CamelJBangMain().withPrinter(printer).createCommandLine(only);
        CommandLineHelper.augmentWithUserConfiguration(commandLine);
        return commandLine;
    }

    record Parsed(String path, Map<String, String> fields, String error) {
    }

    // parses the command line (picocli), and reads the fields of the commands that it names
    private Parsed parse(CommandLine root, List<String> args) {
        CommandLine.ParseResult result;
        try {
            result = root.parseArgs(args.toArray(new String[0]));
        } catch (CommandLine.ParameterException e) {
            return new Parsed(null, null, e.getMessage());
        }
        List<String> path = new ArrayList<>();
        Map<String, String> fields = new TreeMap<>();
        while (result.hasSubcommand()) {
            result = result.subcommand();
            path.add(result.commandSpec().name());
            String prefix = result.hasSubcommand() ? result.commandSpec().name() + ":" : "";
            readFields(result.commandSpec().userObject(), prefix, fields);
        }
        return new Parsed(String.join(" ", path), fields, null);
    }

    // the fields of the command that the command line sets, by name (and the fields of its mixins)
    private void readFields(Object command, String prefix, Map<String, String> fields) {
        for (Class<?> c = command.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                Set<String> annotations = Arrays.stream(field.getAnnotations())
                        .map(Annotation::annotationType).map(Class::getSimpleName).collect(Collectors.toSet());
                boolean option = annotations.stream().anyMatch(OPTION_ANNOTATIONS::contains)
                        || CONSUMED_FIELDS.contains(field.getName());
                boolean group = annotations.stream().anyMatch(GROUP_ANNOTATIONS::contains);
                if (!option && !group) {
                    continue;
                }
                Object value;
                try {
                    field.setAccessible(true);
                    value = field.get(command);
                } catch (Exception e) {
                    value = "<" + e.getClass().getSimpleName() + ">";
                }
                String name = prefix + field.getName();
                if (group && value != null) {
                    readFields(value, name + ".", fields);
                } else {
                    fields.put(name, format(value));
                }
            }
        }
    }

    private String format(Object value) {
        if (value == null) {
            return "null";
        }
        // the versions that the build sets, by name, so that an upgrade does not change the expected values
        if (RuntimeType.SPRING_BOOT_VERSION.equals(value)) {
            return "${spring-boot-version}";
        }
        if (HawtioVersion.HAWTIO_VERSION.equals(value)) {
            return "${hawtio-version}";
        }
        if (value instanceof CharSequence || value instanceof Path || value instanceof File) {
            return '"' + normalize(value.toString()) + '"';
        }
        if (value instanceof Map<?, ?> map) {
            return new TreeMap<>(
                    map.entrySet().stream().collect(
                            Collectors.toMap(e -> format(e.getKey()), e -> format(e.getValue()), (a, b) -> b)))
                    .toString();
        }
        if (value instanceof Set<?> set) {
            return set.stream().map(this::format).sorted().toList().toString();
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(this::format).toList().toString();
        }
        if (value.getClass().isArray()) {
            List<String> list = new ArrayList<>();
            for (int i = 0; i < Array.getLength(value); i++) {
                list.add(format(Array.get(value, i)));
            }
            return list.toString();
        }
        if (value instanceof Enum<?> e) {
            return e.name();
        }
        return normalize(value.toString());
    }

    // the directories of the machine as names, so the values are the same on every machine
    private static String normalize(String value) {
        String home = FileUtil.normalizePath(System.getProperty("user.home"));
        String cwd = FileUtil.normalizePath(Path.of("").toAbsolutePath().toString());
        return value.replace(cwd, "${cwd}").replace(home, "${user.home}");
    }
}
