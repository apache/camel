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
}
