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

import org.jline.shell.CommandSession;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The shell's prompt is camel>, but the docs say camel ps: a leading camel runs the rest of the line.
 */
class CamelShellCommandRegistryTest {

    @CommandLine.Command(name = "root", subcommands = { Greet.class })
    static class Root {
    }

    @CommandLine.Command(name = "greet")
    static class Greet implements Runnable {
        static int runs;

        @Override
        public void run() {
            runs++;
        }
    }

    @Test
    void aCommandRunsOnACommandLineThatWasNeverParsed() throws Exception {
        // the shell panel of camel tui builds the commands with CamelJBangMain.newCommandLine(), whose root is never
        // parsed; picocli asks each parent of the command it runs whether help was requested (CAMEL-26863)
        CamelShellCommandRegistry registry = new CamelShellCommandRegistry(new CommandLine(new Root()));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int before = Greet.runs;

        registry.command("greet").execute(session(out), new String[0]);
        registry.command("camel").execute(session(out), new String[] { "greet" });

        assertThat(Greet.runs).isEqualTo(before + 2);
        assertThat(out.toString(StandardCharsets.UTF_8)).doesNotContain("NullPointerException");
    }

    @Test
    void theParseResultOfTheCommandLineThatRanTheShellIsKept() {
        // camel shell hands over the command line that ran it
        CommandLine commandLine = new CommandLine(new Root());
        commandLine.parseArgs("greet");

        new CamelShellCommandRegistry(commandLine);

        assertThat(commandLine.getParseResult().originalArgs()).containsExactly("greet");
    }

    @Test
    void camelIsACommandOfTheShell() {
        CamelShellCommandRegistry registry = new CamelShellCommandRegistry(new CommandLine(new Root()));

        assertThat(registry.hasCommand("camel")).isTrue();
        assertThat(registry.hasCommand("greet")).isTrue();
        assertThat(registry.commands()).anyMatch(c -> "camel".equals(c.name()));
    }

    @Test
    void anUnknownCommandAfterCamelIsSaidSo() throws Exception {
        CamelShellCommandRegistry registry = new CamelShellCommandRegistry(new CommandLine(new Root()));
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        registry.command("camel").execute(session(out), new String[] { "nope" });

        assertThat(out.toString(StandardCharsets.UTF_8)).contains("Unknown command: nope");
    }

    @Test
    void aBareCamelSaysHowToTypeACommand() throws Exception {
        CamelShellCommandRegistry registry = new CamelShellCommandRegistry(new CommandLine(new Root()));
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        registry.command("camel").execute(session(out), new String[0]);

        assertThat(out.toString(StandardCharsets.UTF_8)).contains("with or without camel");
    }

    private static CommandSession session(ByteArrayOutputStream out) {
        PrintStream ps = new PrintStream(out, true, StandardCharsets.UTF_8);
        return new CommandSession(null, System.in, ps, ps);
    }
}
