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

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.run.common.Printer;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.AvailablePortFinder.Port;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

class MonitorStartupTest {

    @Test
    void webWithoutATerminalServesBrowsersOnly() {
        assertThat(MonitorStartup.browserOnly(true, false, false, false)).isTrue();
        assertThat(MonitorStartup.browserOnly(true, false, false, true)).as("a terminal to draw in").isFalse();
        assertThat(MonitorStartup.browserOnly(false, false, false, false)).as("no --web").isFalse();
        assertThat(MonitorStartup.browserOnly(true, true, false, false)).as("a browser session itself").isFalse();
        assertThat(MonitorStartup.browserOnly(true, false, true, false)).as("a recording").isFalse();
    }

    @Test
    void camelTuiForwardsDefaults() {
        TuiCommand command = new TuiCommand(new CamelJBangMain(), getClass().getClassLoader());
        new CommandLine(command).parseArgs("--web", "--defaults");
        assertThat(command.buildArgs()).containsExactly("--web", "--defaults");

        CamelMonitor monitor = new CamelMonitor(new CamelJBangMain(), getClass().getClassLoader());
        new CommandLine(monitor).parseArgs(command.buildArgs().toArray(String[]::new));
        assertThat(monitor.web).isTrue();
        assertThat(monitor.defaults).isTrue();
    }

    @Test
    void browserOnlyRefusesMcp() {
        TuiWebServer server = newServer(0);

        assertThatThrownBy(() -> MonitorStartup.serveBrowserOnly(server, 0, true, new ListPrinter()))
                .isInstanceOf(CliException.class)
                .hasMessageContaining("--mcp needs an interactive terminal");
    }

    @Test
    void browserOnlyServesUntilTheServerStops() throws Exception {
        try (Port reserved = AvailablePortFinder.find()) {
            int port = reserved.getPort();
            TuiWebServer server = newServer(port);
            ListPrinter printer = new ListPrinter();
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Thread serving = new Thread(() -> {
                try {
                    MonitorStartup.serveBrowserOnly(server, port, false, printer);
                } catch (Throwable e) {
                    failure.set(e);
                }
            });
            serving.start();

            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress("127.0.0.1", port), 500);
                }
            });
            assertThat(printer.lines).containsExactly(
                    "Camel TUI in the browser at http://127.0.0.1:" + port + "/ (Ctrl+C to stop)");
            assertThat(serving.isAlive()).as("waits while the server runs").isTrue();

            server.stop();
            serving.join(TimeUnit.SECONDS.toMillis(10));
            assertThat(serving.isAlive()).isFalse();
            assertThat(failure.get()).isNull();
        }
    }

    private static TuiWebServer newServer(int port) {
        return new TuiWebServer(
                port, new CamelJBangMain(), Thread.currentThread().getContextClassLoader(), null, 200, "dark",
                terminal -> {
                });
    }

    private static final class ListPrinter implements Printer {

        private final List<String> lines = new CopyOnWriteArrayList<>();

        @Override
        public void println() {
            lines.add("");
        }

        @Override
        public void println(String line) {
            lines.add(line);
        }

        @Override
        public void print(String output) {
            lines.add(output);
        }

        @Override
        public void printf(String format, Object... args) {
            lines.add(String.format(format, args));
        }
    }
}
