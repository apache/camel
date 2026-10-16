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
package org.apache.camel.dsl.jbang.core.commands.action;

import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Stream;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.StringPrinter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * camel log: the lines of the log of an integration, without following it.
 */
@ExtendWith(MockitoExtension.class)
class CamelLogActionTest extends ActionCommandTestSupport {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    @Test
    void printsTheLogOfTheIntegration() throws Exception {
        writeLog(TEST_PID + ".log", line(-300, "Starting"), line(-60, "Slow"), line(-1, "Failed"));

        assertThat(callWithSingleProcess(command())).isZero();

        // one integration: no prefix with its name
        assertThat(lines()).hasSize(3).allMatch(l -> !l.contains("|"));
        assertThat(lines().get(2)).endsWith(": Failed");
    }

    @Test
    void tailPrintsTheLastLines() throws Exception {
        writeLog(TEST_PID + ".log", line(-300, "Starting"), line(-60, "Slow"), line(-1, "Failed"));

        assertThat(callWithSingleProcess(command("--tail=2"))).isZero();

        assertThat(lines()).hasSize(2);
        assertThat(lines().get(0)).endsWith(": Slow");
    }

    @Test
    void grepPrintsTheMatchingLines() throws Exception {
        writeLog(TEST_PID + ".log", line(-300, "Starting"), line(-60, "Slow"), line(-1, "Failed"));

        // ignoring case, and only the message (after ---) matches
        assertThat(callWithSingleProcess(command("--grep=FAILED"))).isZero();

        assertThat(lines()).hasSize(1);
        assertThat(lines().get(0)).endsWith(": Failed");
    }

    @Test
    void findHighlightsButPrintsAllLines() throws Exception {
        writeLog(TEST_PID + ".log", line(-300, "Starting"), line(-60, "Slow"), line(-1, "Failed"));

        assertThat(callWithSingleProcess(command("--find=slow"))).isZero();

        assertThat(lines()).hasSize(3);
        assertThat(printer.getStdout()).contains("\u001B[");
    }

    @Test
    void sinceInSecondsOrWithAUnit() throws Exception {
        writeLog(TEST_PID + ".log", line(-300, "Starting"), line(-60, "Slow"), line(-1, "Failed"));

        assertThat(callWithSingleProcess(command("--since=30"))).isZero();
        assertThat(lines()).hasSize(1);

        printer = new StringPrinter();
        assertThat(callWithSingleProcess(command("--since=2m"))).isZero();
        assertThat(lines()).hasSize(2);
    }

    @Test
    void startupPrintsUntilCamelHasStarted() throws Exception {
        writeLog(TEST_PID + ".log", line(-300, "Starting"),
                line(-299, "o.a.c.i.e.AbstractCamelContext",
                        "Apache Camel 4.24.0 (orders) started in 120ms (build:30ms init:10ms start:80ms)"),
                line(-1, "Failed"));

        assertThat(callWithSingleProcess(command("--startup"))).isZero();

        assertThat(lines()).hasSize(2);
        assertThat(lines().get(1)).contains("started in 120ms");
    }

    @Test
    void withoutTimestamp() throws Exception {
        String line = line(-1, "Failed");
        writeLog(TEST_PID + ".log", line);

        assertThat(callWithSingleProcess(command("--timestamp=false"))).isZero();

        assertThat(lines().get(0).trim()).startsWith("INFO").endsWith(": Failed");
        assertThat(lines().get(0)).doesNotContain(line.substring(0, 23));
    }

    @Test
    void prefixWithTheNameOfTheIntegration() throws Exception {
        writeLog(TEST_PID + ".log", line(-1, "Failed"));

        assertThat(callWithSingleProcess(command("--prefix=true"))).isZero();

        assertThat(lines().get(0)).startsWith("orders    | ");
    }

    @Test
    void logNamedAfterTheIntegration() throws Exception {
        // camel run --logging-file-name, or an older version of Camel
        writeLog("orders.log", line(-1, "Failed"));

        assertThat(callWithSingleProcess(command())).isZero();

        assertThat(lines()).hasSize(1);
    }

    private CamelLogAction command(String... args) throws Exception {
        writeStatusFile(TEST_PID, "orders");
        CamelLogAction command = new CamelLogAction(new CamelJBangMain().withPrinter(printer));
        CommandLine.populateCommand(command,
                Stream.concat(Stream.of("orders", "--follow=false", "--logging-color=false"), Stream.of(args))
                        .toArray(String[]::new));
        return command;
    }

    // without the highlighting of --find and --grep
    private List<String> lines() throws Exception {
        return printer.getLines().stream().map(l -> l.replaceAll("\u001B\\[[;\\d]*m", "")).toList();
    }

    private static void writeLog(String name, String... lines) throws Exception {
        Files.write(CommandLineHelper.getCamelDir().resolve(name), List.of(lines));
    }

    private static String line(long seconds, String message) {
        return line(seconds, "o.a.c.Test", message);
    }

    // the layout of the log of camel run
    private static String line(long seconds, String logger, String message) {
        return LocalDateTime.now().plusSeconds(seconds).format(TIMESTAMP) + "  INFO " + TEST_PID + " --- [main] " + logger
               + " : " + message;
    }
}
