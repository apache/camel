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

import java.util.List;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import org.apache.camel.dsl.jbang.core.commands.tui.DiagramLevelBar.Level;
import org.apache.camel.dsl.jbang.core.commands.tui.DiagramLevelBar.Segment;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Diagram tab's level bar (CAMEL-25147): every level shown, the current one marked, the path in the labels, and a
 * click goes to a level unless it does not apply.
 */
class DiagramLevelBarTest {

    @Test
    void showsAllLevelsAndTheCurrentOne() {
        DiagramLevelBar bar = new DiagramLevelBar();
        Rect area = new Rect(0, 0, 160, 1);
        Buffer buffer = Buffer.empty(area);
        bar.render(Frame.forTesting(buffer), area, List.of(
                new Segment(Level.ARCHITECTURE, "Architecture", true),
                new Segment(Level.TOPOLOGY, "Topology: Order intake", true),
                new Segment(Level.ROUTE, "Route", false)), Level.TOPOLOGY,
                List.of(new DiagramLevelBar.Toggle("b", "view", "business"),
                        new DiagramLevelBar.Toggle("e", "external", "edges"),
                        new DiagramLevelBar.Toggle("m", "metrics", "off")));
        String line = TuiTestHelper.bufferToString(buffer);
        assertTrue(line.contains("◇ Architecture"), line);
        assertTrue(line.indexOf(" v ") >= 0 && line.indexOf(" v ") < line.indexOf("Architecture"),
                "the key that moves through the levels comes first: " + line);
        assertTrue(line.contains("◆ Topology: Order intake"), "the current level: " + line);
        assertTrue(line.contains("◇ Route"), line);
        // the view settings of the level, with their state, beside the diagram
        assertTrue(line.contains(" b  view: business "), "a mode is named: " + line);
        assertTrue(line.contains(" e  external: edges "), line);
        assertTrue(line.contains(" m  metrics \u25cb"), line);

        int capabilities = line.indexOf("Architecture");
        assertEquals(Level.ARCHITECTURE, bar.hit(capabilities, 0));
        assertEquals(Level.TOPOLOGY, bar.hit(line.indexOf("Order intake"), 0));
        assertNull(bar.hit(line.indexOf("Route"), 0), "a level that does not apply");
        assertNull(bar.hit(capabilities, 1), "another row");
    }
}
