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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.common.EnvironmentHelper;
import org.apache.camel.dsl.jbang.run.common.HawtioVersion;
import org.apache.camel.dsl.jbang.run.common.RuntimeType;
import org.apache.camel.dsl.jbang.run.common.StringPrinter;
import org.apache.camel.util.FileUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * Guards what the CLI prints for {@code --help} and for a command line that is wrong, so a change of the command line
 * parser (such as the port from picocli to aesh, CAMEL-23810) shows every difference to the user.
 * <p/>
 * The expected output is in src/test/resources/golden/cli-help.txt: the help of {@code camel} and of every command
 * (without colors, at the width of 80 columns that the CLI uses), and for each wrong command line its exit code, stderr
 * and stdout. The directories of the machine and the versions that the build sets are written as names, so the file is
 * the same on every machine and after an upgrade.
 * <p/>
 * Run with -Dcamel.golden.update=true to write the file again, and check the difference before committing it.
 */
class CliHelpGoldenTest extends CamelCommandBaseTestSupport {

    private static final Path GOLDEN = Path.of("src/test/resources/golden/cli-help.txt");
    private static final boolean UPDATE = Boolean.getBoolean("camel.golden.update");
    private static final String CAMEL_VERSION = new DefaultCamelCatalog().getCatalogVersion();

    // wrong command lines, and what the CLI tells about them
    private static final List<List<String>> ERRORS = List.of(
            List.of("no-such-command"),
            List.of("run", "--no-such-option"),
            List.of("get", "route", "--sort"),
            List.of("version", "list", "--from-date=yesterday"),
            List.of("catalog", "component", "--sort=nope", "--json"),
            List.of("ps", "--no-such-option", "--json"),
            List.of("config", "get", "no-such-key"),
            List.of("stop", "no-such-integration"),
            List.of("run", "no-such-file.yaml"));

    private final Map<String, String> camelProperties = new TreeMap<>();

    @BeforeEach
    void clearCamelSystemProperties() throws Exception {
        // the camel system properties of the JVM, which the commands read: the help is written without them
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
    void theHelpAndTheErrorsAreAsBefore() throws Exception {
        // the default of --logging-color of camel run comes from the environment (FORCE_COLOR), and is off in CI and in
        // the tests, which have no console
        assumeFalse(EnvironmentHelper.isColorEnabled(), "the colors of the logging are on (FORCE_COLOR)");
        Map<String, String> actual = sections();
        if (UPDATE) {
            StringBuilder sb = new StringBuilder();
            sb.append("# What the Camel CLI prints for --help and for a wrong command line, see CliHelpGoldenTest.\n");
            sb.append("# Written with -Dcamel.golden.update=true.\n");
            actual.forEach((title, text) -> sb.append('\n').append(title).append('\n').append(text));
            Files.writeString(GOLDEN, sb.toString(), StandardCharsets.UTF_8);
            return;
        }

        Map<String, String> expected = read(Files.readAllLines(GOLDEN, StandardCharsets.UTF_8));
        List<String> differences = new ArrayList<>();
        for (String title : union(expected.keySet(), actual.keySet())) {
            String want = expected.get(title);
            String got = actual.get(title);
            if (!Objects.equals(want, got)) {
                differences.add(title + "\n--- expected\n" + want + "--- actual\n" + got);
            }
        }
        assertThat(differences)
                .as("the help and errors that differ from %s (run with -Dcamel.golden.update=true to update)", GOLDEN)
                .isEmpty();
    }

    // the sections of the file by their title line: the help of each command, then each wrong command line
    private Map<String, String> sections() {
        Map<String, String> answer = new LinkedHashMap<>();
        CommandLine root = new CamelJBangMain().withPrinter(printer).createCommandLine(null);
        answer.put("== camel --help", normalize(root.getUsageMessage(CommandLine.Help.Ansi.OFF)));
        help(root, "camel", answer, Collections.newSetFromMap(new IdentityHashMap<>()));
        for (List<String> args : ERRORS) {
            answer.put("== camel " + String.join(" ", args), normalize(error(args)));
        }
        return answer;
    }

    // the help of the sub-commands, in the order of their names
    private static void help(CommandLine parent, String path, Map<String, String> answer, Set<CommandLine> seen) {
        Map<String, CommandLine> subcommands = new TreeMap<>();
        parent.getSubcommands().forEach((name, sub) -> {
            // a command is in the map under the name it was added with, and after that under its aliases
            if (seen.add(sub)) {
                subcommands.put(name, sub);
            }
        });
        subcommands.forEach((name, sub) -> {
            String subPath = path + " " + name;
            answer.put("== " + subPath + " --help", normalize(sub.getUsageMessage(CommandLine.Help.Ansi.OFF)));
            help(sub, subPath, answer, seen);
        });
    }

    // runs the command line as the CLI does, and gives its exit code, stderr and stdout: from the printer of the CLI,
    // and from System.err and System.out, where picocli prints a usage error
    private String error(List<String> args) {
        printer = new StringPrinter();
        TestMain main = new TestMain();
        main.withPrinter(printer);
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream oldErr = System.err;
        PrintStream oldOut = System.out;
        String oldAnsi = System.getProperty("picocli.ansi");
        System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
        System.setProperty("picocli.ansi", "false");
        try {
            main.execute(args.toArray(new String[0]));
        } finally {
            System.setErr(oldErr);
            System.setOut(oldOut);
            if (oldAnsi != null) {
                System.setProperty("picocli.ansi", oldAnsi);
            } else {
                System.clearProperty("picocli.ansi");
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("exit: ").append(main.exitCode).append('\n');
        appendStream(sb, "stderr", printer.getStderr() + err.toString(StandardCharsets.UTF_8));
        appendStream(sb, "stdout", printer.getStdout() + out.toString(StandardCharsets.UTF_8));
        return sb.toString();
    }

    private static void appendStream(StringBuilder sb, String name, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        sb.append(name).append(":\n");
        for (String line : text.strip().split("\\R")) {
            sb.append("  ").append(line).append('\n');
        }
    }

    // the directories of the machine and the versions that the build sets as names, and no trailing spaces
    private static String normalize(String text) {
        String home = FileUtil.normalizePath(System.getProperty("user.home"));
        String cwd = FileUtil.normalizePath(Path.of("").toAbsolutePath().toString());
        text = text.replace(cwd, "${cwd}").replace(home, "${user.home}")
                .replace(CAMEL_VERSION, "${camel-version}")
                .replace(RuntimeType.SPRING_BOOT_VERSION, "${spring-boot-version}")
                .replace(HawtioVersion.HAWTIO_VERSION, "${hawtio-version}");
        StringBuilder sb = new StringBuilder();
        for (String line : text.split("\\R", -1)) {
            sb.append(line.stripTrailing()).append('\n');
        }
        // one line break at the end
        return sb.toString().replaceAll("\n+$", "\n");
    }

    private static Map<String, String> read(List<String> lines) {
        Map<String, String> answer = new LinkedHashMap<>();
        String title = null;
        StringBuilder text = new StringBuilder();
        for (String line : lines) {
            if (line.startsWith("== camel")) {
                if (title != null) {
                    answer.put(title, text.toString().replaceAll("\n+$", "\n"));
                }
                title = line;
                text.setLength(0);
            } else if (title != null) {
                text.append(line).append('\n');
            }
        }
        if (title != null) {
            answer.put(title, text.toString().replaceAll("\n+$", "\n"));
        }
        return answer;
    }

    private static List<String> union(Set<String> a, Set<String> b) {
        List<String> answer = new ArrayList<>(a);
        b.stream().filter(t -> !a.contains(t)).forEach(answer::add);
        return answer;
    }

    private static class TestMain extends CamelJBangMain {
        int exitCode = -1;

        @Override
        public void quit(int exitCode) {
            this.exitCode = exitCode;
        }
    }
}
