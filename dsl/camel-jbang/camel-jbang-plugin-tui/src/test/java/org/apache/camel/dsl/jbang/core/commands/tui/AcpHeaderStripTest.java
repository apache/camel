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

import java.nio.file.Path;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.terminal.Frame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcpHeaderStripTest {

    private static AcpHeaderStrip.Model model(int commands) {
        return new AcpHeaderStrip.Model(
                "IBM Bob (ACP)", "◆", Color.rgb(0x0F, 0x62, 0xFE),
                "bob-shell 2.0.2", "58b65aea-1234", Path.of(System.getProperty("user.home"), "Work", "camel"), commands);
    }

    @Test
    void glyphModeRendersGlyphAndMetadata() {
        AcpHeaderStrip strip = new AcpHeaderStrip();
        Rect area = new Rect(0, 0, 100, AcpHeaderStrip.ROWS);
        Buffer buffer = Buffer.empty(area);
        strip.render(Frame.forTesting(buffer), area, model(24));
        String rendered = TuiTestHelper.bufferToString(buffer);
        assertTrue(rendered.contains("◆ IBM Bob (ACP) · bob-shell 2.0.2"), rendered);
        assertTrue(rendered.contains("session 58b65aea"), rendered);
        assertTrue(rendered.contains("~/Work/camel"), rendered);
        assertTrue(rendered.contains("24 commands"), rendered);
    }

    @Test
    void narrowAreaRendersNothing() {
        AcpHeaderStrip strip = new AcpHeaderStrip();
        Rect area = new Rect(0, 0, 19, AcpHeaderStrip.ROWS);
        Buffer buffer = Buffer.empty(area);
        strip.render(Frame.forTesting(buffer), area, model(3));
        assertTrue(TuiTestHelper.bufferToString(buffer).isBlank(), "nothing fits below 20 columns");
    }

    @Test
    void metaLineAndHomeRelativePath() {
        assertEquals("~/Work/camel", AcpHeaderStrip.homeRelative(Path.of(System.getProperty("user.home"), "Work", "camel")));
        assertEquals("/opt/x", AcpHeaderStrip.homeRelative(Path.of("/opt/x")));
        assertEquals("session 58b65aea · ~/Work/camel · 1 command · /agent: lists them", AcpHeaderStrip.metaLine(model(1)));
    }
}
