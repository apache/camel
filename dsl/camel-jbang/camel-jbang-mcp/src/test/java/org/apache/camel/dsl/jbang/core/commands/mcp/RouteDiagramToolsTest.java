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
package org.apache.camel.dsl.jbang.core.commands.mcp;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import io.quarkiverse.mcp.server.ToolCallException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RouteDiagramToolsTest {

    @Test
    void missingSourceFileThrows() {
        RouteDiagramTools tools = new RouteDiagramTools();

        assertThatThrownBy(() -> tools.camel_render_route_diagram(null, null, null, null, null, null, null, null, null))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining("sourceFile");
    }

    @Test
    void blankSourceFileThrows() {
        RouteDiagramTools tools = new RouteDiagramTools();

        assertThatThrownBy(() -> tools.camel_render_route_diagram("   ", null, null, null, null, null, null, null, null))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining("sourceFile");
    }

    @Test
    void nonExistingSourceFileThrows() {
        RouteDiagramTools tools = new RouteDiagramTools();

        assertThatThrownBy(() -> tools.camel_render_route_diagram(
                "/no/such/file.yaml", null, null, null, null, null, null, null, null))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining("does not exist");
    }

    @Test
    void nonExistingSourceFileWithAsciiThemeThrows() {
        RouteDiagramTools tools = new RouteDiagramTools();

        assertThatThrownBy(() -> tools.camel_render_route_diagram(
                "/no/such/file.yaml", null, "ascii", null, null, null, null, null, null))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining("does not exist");
    }

    @Test
    void nonExistingSourceFileWithUnicodeThemeThrows() {
        RouteDiagramTools tools = new RouteDiagramTools();

        assertThatThrownBy(() -> tools.camel_render_route_diagram(
                "/no/such/file.yaml", null, "unicode", null, null, null, null, null, null))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining("does not exist");
    }

    @Test
    void imageWithoutATheme(@TempDir Path dir) throws Exception {
        Path source = dir.resolve("orders.camel.yaml");
        Files.writeString(source, """
                - route:
                    id: orders
                    from:
                      uri: timer:orders
                      steps:
                        - setBody:
                            constant: hello
                        - to:
                            uri: log:orders
                """);
        Path png = dir.resolve("orders.png");
        RouteDiagramTools tools = new RouteDiagramTools();

        RouteDiagramTools.RouteDiagramResult result = tools.camel_render_route_diagram(
                source.toString(), png.toString(), null, null, null, null, null, null, null);

        assertThat(result.success()).isTrue();
        assertThat(result.outputFile()).isEqualTo(png.toAbsolutePath().toString());
        // a PNG image, not the text diagram
        assertThat(Files.readAllBytes(png)).startsWith(0x89, 'P', 'N', 'G');
        assertThat(result.asciiDiagram()).isNull();
    }

    @Test
    void theMessageSaysOnceWhereTheImageIs(@TempDir Path dir) throws Exception {
        Path png = dir.resolve("orders.png");

        RouteDiagramTools.RouteDiagramResult result = new RouteDiagramTools().camel_render_route_diagram(
                route(dir, "orders").toString(), png.toString(), null, null, null, null, null, null, null);

        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo("Diagram saved to: " + png.toAbsolutePath());
    }

    @Test
    void aTextDiagramLeavesNoFileBehind(@TempDir Path dir) throws Exception {
        Path source = route(dir, "orders");
        List<Path> before = tempDiagrams();

        RouteDiagramTools.RouteDiagramResult result = new RouteDiagramTools().camel_render_route_diagram(
                source.toString(), null, "ascii", null, null, null, null, null, null);

        assertThat(result.success()).isTrue();
        assertThat(result.asciiDiagram()).contains("orders");
        assertThat(result.outputFile()).isNull();
        assertThat(result.message()).doesNotContain("saved to");
        // CAMEL-26932: a temporary file was left for each call
        assertThat(tempDiagrams()).isEqualTo(before);
    }

    @Test
    void callsAtTheSameTime(@TempDir Path dir) throws Exception {
        // CAMEL-26932: the calls shared one work directory, which each deleted at its start, so of three calls at
        // once one failed with NoSuchFileException and one timed out
        List<String> routes = List.of("first", "second", "third");
        RouteDiagramTools tools = new RouteDiagramTools();
        ExecutorService executor = Executors.newFixedThreadPool(routes.size());
        try {
            List<Future<RouteDiagramTools.RouteDiagramResult>> calls = new ArrayList<>();
            for (String id : routes) {
                Path source = route(dir, id);
                calls.add(executor.submit(() -> tools.camel_render_route_diagram(
                        source.toString(), null, "ascii", null, null, null, null, null, null)));
            }
            for (int i = 0; i < routes.size(); i++) {
                RouteDiagramTools.RouteDiagramResult result = calls.get(i).get(60, TimeUnit.SECONDS);
                assertThat(result.success()).as(result.message()).isTrue();
                assertThat(result.asciiDiagram()).contains(routes.get(i));
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static Path route(Path dir, String id) throws Exception {
        Path source = dir.resolve(id + ".camel.yaml");
        Files.writeString(source, """
                - route:
                    id: %s
                    from:
                      uri: timer:%s
                      steps:
                        - to:
                            uri: log:%s
                """.formatted(id, id, id));
        return source;
    }

    private static List<Path> tempDiagrams() throws Exception {
        try (Stream<Path> files = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            return files.filter(f -> f.getFileName().toString().startsWith("camel-route-diagram-")).sorted().toList();
        }
    }
}
