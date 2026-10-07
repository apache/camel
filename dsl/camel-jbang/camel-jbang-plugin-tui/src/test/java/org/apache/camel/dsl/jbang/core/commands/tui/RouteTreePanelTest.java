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

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The route tree beside the source editor shows the route the cursor is in, and marks the step under the cursor.
 */
class RouteTreePanelTest {

    private static final List<String> SOURCE = List.of(
            "- route:",
            "    id: checkout",
            "    from:",
            "      uri: file:orders",
            "      steps:",
            "        - unmarshal:",
            "            json: {}",
            "        - choice:",
            "            when:",
            "              - simple: \"${body} == 'a'\"",
            "                steps:",
            "                  - log: a",
            "            otherwise:",
            "              steps:",
            "                - to: direct:other",
            "- route:",
            "    id: other",
            "    from:",
            "      uri: direct:other",
            "      steps:",
            "        - log: other");

    @Test
    void theRouteOfTheCursorWithTheStepUnderIt() {
        List<YamlRouteNodeScanner.NodeEntry> entries = YamlRouteNodeScanner.scanLines(SOURCE, "orders.camel.yaml");

        RouteTreePanel.RouteTree tree = RouteTreePanel.treeAt(entries, 11);

        assertThat(tree.routeId()).isEqualTo("checkout");
        assertThat(tree.nodes()).extracting(YamlRouteNodeScanner.NodeEntry::type).contains("unmarshal", "choice", "log");
        assertThat(tree.nodes().get(tree.current()).type()).isEqualTo("log");

        assertThat(RouteTreePanel.treeAt(entries, 20).routeId()).isEqualTo("other");
    }

    @Test
    void theEditorDrawsThePanelWhenItIsShown(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("orders.camel.yaml");
        Files.write(file, SOURCE);
        Theme.resetForTesting();
        SourceViewer viewer = new SourceViewer();
        viewer.loadFile(file);
        viewer.setRouteTreeShown(true);
        Rect area = new Rect(0, 0, 120, 30);
        Buffer buffer = Buffer.empty(area);

        viewer.render(Frame.forTesting(buffer), area);

        StringBuilder screen = new StringBuilder();
        for (int y = 0; y < 30; y++) {
            for (int x = 0; x < 120; x++) {
                screen.append(buffer.get(x, y).symbol());
            }
            screen.append('\n');
        }
        assertThat(screen.toString()).contains("Route: checkout");
    }
}
