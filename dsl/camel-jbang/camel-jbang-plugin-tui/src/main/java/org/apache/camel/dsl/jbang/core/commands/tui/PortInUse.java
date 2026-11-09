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

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.tamboui.text.Line;
import dev.tamboui.text.Span;

/**
 * Why an integration did not start when its HTTP server could not bind its port, and who has it: two examples run from
 * the TUI both serve on 8080, and the second fails with a stack trace that says "Address already in use" somewhere in
 * its log. The hint names the running integration that serves HTTP on that port, and the way out.
 */
final class PortInUse {

    // the host and port the server tried: "on 0.0.0.0:8080", "/127.0.0.1:8443", "localhost:9090"
    private static final Pattern PORT
            = Pattern.compile("(?:\\d{1,3}(?:\\.\\d{1,3}){3}|localhost|\\[[0-9a-fA-F:]*]|[\\w.-]+\\.\\w+):(\\d{2,5})\\b");
    // the consumers that serve HTTP on the port of the integration
    private static final Set<String> HTTP_SERVERS = Set.of("platform-http", "rest", "rest-openapi", "rest-api");
    private static final int DEFAULT_PORT = 8080;

    private PortInUse() {
    }

    /** The port the log says is in use, or -1 when it does not say so. */
    static int port(List<String> logLines) {
        for (String line : logLines) {
            if (line.contains("Address already in use")) {
                Matcher m = PORT.matcher(line);
                if (m.find()) {
                    return Integer.parseInt(m.group(1));
                }
            }
        }
        return -1;
    }

    /** The lines of the hint for the failure log of the integration, or none when it did not fail on a port. */
    static List<Line> hint(List<String> logLines, List<IntegrationInfo> running) {
        int port = port(logLines);
        if (port < 0) {
            return List.of();
        }
        List<Line> lines = new ArrayList<>();
        IntegrationInfo owner = owner(port, running);
        if (owner != null) {
            lines.add(Line.from(Span.styled(" ✗ Port " + port + " is in use: ", Theme.error().bold()),
                    Span.raw("the running integration "), Span.styled(owner.name, Theme.label().bold()),
                    Span.raw(" (pid " + owner.pid + ") serves HTTP on it.")));
        } else {
            lines.add(Line.from(Span.styled(" ✗ Port " + port + " is in use", Theme.error().bold()),
                    Span.raw(" by another program.")));
        }
        lines.add(Line.from(Span.raw("   Stop " + (owner != null ? "it" : "that program")
                                     + " first, or give this integration another port: camel.server.port="
                                     + (port + 1) + " in its application.properties.")));
        lines.add(Line.from(Span.raw("")));
        return lines;
    }

    /** The running integration that serves HTTP on the port, as its application.properties configures it. */
    static IntegrationInfo owner(int port, List<IntegrationInfo> running) {
        for (IntegrationInfo info : running) {
            if (info.phantom || info.vanishing || !servesHttp(info)) {
                continue;
            }
            if (configuredPort(info.directory) == port) {
                return info;
            }
        }
        return null;
    }

    private static boolean servesHttp(IntegrationInfo info) {
        return info.endpoints.stream().anyMatch(ep -> "in".equals(ep.direction) && HTTP_SERVERS.contains(ep.component));
    }

    /** camel.server.port of the application.properties in the directory, else the port Camel serves on by default. */
    static int configuredPort(String directory) {
        if (directory == null) {
            return DEFAULT_PORT;
        }
        Path file = Path.of(directory, "application.properties");
        if (!Files.isRegularFile(file)) {
            return DEFAULT_PORT;
        }
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(file)) {
            p.load(r);
            return Integer.parseInt(p.getProperty("camel.server.port", String.valueOf(DEFAULT_PORT)).trim());
        } catch (IOException | NumberFormatException e) {
            return DEFAULT_PORT;
        }
    }
}
