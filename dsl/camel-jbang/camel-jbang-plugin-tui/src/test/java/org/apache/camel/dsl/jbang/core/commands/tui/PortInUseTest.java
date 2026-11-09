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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two examples run from the TUI both serve HTTP on 8080: the second fails, and the hint says which one has the port.
 */
class PortInUseTest {

    private static final List<String> LOG = List.of(
            "2026-11-09 18:02:45.265  INFO 482849 --- [main] AbstractCamelContext : Apache Camel (openapi-server) is starting",
            "2026-11-09 18:02:45.684 ERROR 482849 --- [main] AbstractCamelContext : Error starting CamelContext"
                                                                                                                                + " (openapi-server) due to exception thrown: Failed to start Vert.x HttpServer on 0.0.0.0:8080,"
                                                                                                                                + " reason: java.net.BindException: Address already in use",
            "Caused by: java.net.BindException: Address already in use");

    @TempDir
    Path dir;

    @Test
    void thePortTheLogSaysIsInUse() {
        assertThat(PortInUse.port(LOG)).isEqualTo(8080);
        assertThat(PortInUse.port(List.of("Error: Connection refused to localhost:8080"))).isEqualTo(-1);
    }

    @Test
    void theHintNamesTheIntegrationThatServesOnThePort() throws Exception {
        IntegrationInfo client = integration("http-client", "100", null);
        // another one serves on 9090
        Path other = Files.createDirectory(dir.resolve("other"));
        Files.writeString(other.resolve("application.properties"), "camel.server.port = 9090\n");
        IntegrationInfo api = integration("stock-api", "200", other.toString());

        assertThat(PortInUse.owner(8080, List.of(api, client))).isSameAs(client);
        assertThat(PortInUse.owner(9090, List.of(api, client))).isSameAs(api);
        assertThat(text(PortInUse.hint(LOG, List.of(api, client))))
                .startsWith(" ✗ Port 8080 is in use: the running integration http-client (pid 100) serves HTTP on it.")
                .contains("camel.server.port=8081");
    }

    @Test
    void anotherProgramHasThePort() {
        // an integration without an HTTP server does not
        IntegrationInfo timer = integration("timer-log", "300", null);
        timer.endpoints.clear();

        assertThat(text(PortInUse.hint(LOG, List.of(timer)))).startsWith(" ✗ Port 8080 is in use by another program.");
        assertThat(PortInUse.hint(List.of("all good"), List.of(timer))).isEmpty();
    }

    private static IntegrationInfo integration(String name, String pid, String directory) {
        IntegrationInfo info = new IntegrationInfo();
        info.name = name;
        info.pid = pid;
        info.directory = directory;
        EndpointInfo ep = new EndpointInfo();
        ep.direction = "in";
        ep.component = "platform-http";
        info.endpoints.add(ep);
        return info;
    }

    private static String text(List<Line> lines) {
        StringBuilder sb = new StringBuilder();
        for (Line l : lines) {
            l.spans().stream().map(Span::content).forEach(sb::append);
            sb.append('\n');
        }
        return sb.toString();
    }
}
