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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.EnumSet;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.image.capability.TerminalImageCapabilities;
import dev.tamboui.image.capability.TerminalImageProtocol;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.terminal.Frame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcpHeaderStripTest {

    private static final TerminalImageCapabilities KITTY
            = TerminalImageCapabilities.withSupport(EnumSet.of(TerminalImageProtocol.KITTY, TerminalImageProtocol.HALF_BLOCK));
    private static final TerminalImageCapabilities TEXT_ONLY
            = TerminalImageCapabilities.withSupport(EnumSet.of(TerminalImageProtocol.HALF_BLOCK));

    private static AcpHeaderStrip.Model model(int commands) {
        return new AcpHeaderStrip.Model(
                "IBM Bob (ACP)", "◆", Color.rgb(0x0F, 0x62, 0xFE), "bob",
                "bob-shell 2.0.2", "58b65aea-1234", Path.of(System.getProperty("user.home"), "Work", "camel"), commands);
    }

    @Test
    void logosFollowTheModeAndTheTerminal() {
        assertTrue(new AcpHeaderStrip(KITTY).logosEnabled(AcpHeaderStrip.LogoMode.AUTO));
        assertFalse(new AcpHeaderStrip(TEXT_ONLY).logosEnabled(AcpHeaderStrip.LogoMode.AUTO));
        assertTrue(new AcpHeaderStrip(TEXT_ONLY).logosEnabled(AcpHeaderStrip.LogoMode.ON));
        assertFalse(new AcpHeaderStrip(KITTY).logosEnabled(AcpHeaderStrip.LogoMode.OFF));
        assertEquals(AcpHeaderStrip.LogoMode.AUTO, AcpHeaderStrip.LogoMode.parse(null));
        assertEquals(AcpHeaderStrip.LogoMode.OFF, AcpHeaderStrip.LogoMode.parse("off"));
        assertEquals(AcpHeaderStrip.LogoMode.AUTO, AcpHeaderStrip.LogoMode.parse("nonsense"));
    }

    @Test
    void everyPresetLogoLoadsAndUnknownOnesAreNull() {
        AcpHeaderStrip strip = new AcpHeaderStrip(KITTY);
        for (String logo : new String[] { "claude", "codex", "bob", "qwen", "opencode", "dsh" }) {
            assertNotNull(strip.logoFor(logo), logo);
        }
        assertNull(strip.logoFor("nope"));
        assertNull(strip.logoFor(null));
    }

    @Test
    void glyphModeRendersGlyphAndMetadata() {
        AcpHeaderStrip strip = new AcpHeaderStrip(TEXT_ONLY);
        Rect area = new Rect(0, 0, 100, AcpHeaderStrip.ROWS);
        Buffer buffer = Buffer.empty(area);
        strip.render(Frame.forTesting(buffer), area, model(24), AcpHeaderStrip.LogoMode.AUTO);
        String rendered = TuiTestHelper.bufferToString(buffer);
        assertTrue(rendered.contains("◆ IBM Bob (ACP) · bob-shell 2.0.2"), rendered);
        assertTrue(rendered.contains("session 58b65aea"), rendered);
        assertTrue(rendered.contains("~/Work/camel"), rendered);
        assertTrue(rendered.contains("24 commands"), rendered);
        assertNull(strip.lastLogoRectForTesting());
    }

    @Test
    void logoModeReservesTheLogoColumnsAndKeepsTheText() {
        AcpHeaderStrip strip = new AcpHeaderStrip(KITTY);
        Rect area = new Rect(0, 0, 100, AcpHeaderStrip.ROWS);
        Buffer buffer = Buffer.empty(area);
        strip.render(Frame.forTesting(buffer), area, model(0), AcpHeaderStrip.LogoMode.AUTO);
        String rendered = TuiTestHelper.bufferToString(buffer);
        assertTrue(rendered.contains("IBM Bob (ACP) · bob-shell 2.0.2"), rendered);
        assertFalse(rendered.contains("◆"), "no glyph when the logo is drawn");
        assertTrue(rendered.contains("no commands yet"), rendered);
        assertNotNull(strip.lastLogoRectForTesting());
        assertEquals(0, strip.lastLogoRectForTesting().x());
    }

    @Test
    void kittyCommandsAreQuietAndIdentified() {
        String transmit = AcpHeaderStrip.kittyTransmit(4242, new byte[] { 1, 2, 3 });
        assertTrue(transmit.startsWith("\033_Ga=t,f=100,t=d,i=4242,q=2,m=0;"), transmit);
        assertTrue(transmit.endsWith("\033\\"), transmit);
        String big = AcpHeaderStrip.kittyTransmit(7, new byte[9000]);
        assertTrue(big.startsWith("\033_Ga=t,f=100,t=d,i=7,q=2,m=1;"), big);
        assertEquals(3, big.split("\033_G", -1).length - 1, "9000 bytes base64 split into three 4096-character chunks");
        assertTrue(big.contains(",m=1;") && big.lastIndexOf("m=0;") > big.lastIndexOf("m=1;"));
        assertEquals("\033[3;2H\033_Ga=p,i=4242,p=1,c=5,r=2,C=1,q=2\033\\",
                AcpHeaderStrip.kittyPlace(4242, new Rect(1, 2, 5, 2)));
        assertEquals("\033_Ga=d,d=i,i=4242,q=2\033\\", AcpHeaderStrip.kittyDelete(4242));
        assertEquals(AcpHeaderStrip.kittyImageId("claude"), AcpHeaderStrip.kittyImageId("claude"));
        assertTrue(AcpHeaderStrip.kittyImageId("claude") != AcpHeaderStrip.kittyImageId("codex"));
        assertTrue(AcpHeaderStrip.kittyImageId("bob") > 0);
    }

    @Test
    void kittyLogoIsUploadedOnceAndPlacedEveryFrame() {
        AcpHeaderStrip strip = new AcpHeaderStrip(KITTY);
        Rect area = new Rect(0, 0, 100, AcpHeaderStrip.ROWS);
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        strip.renderForTesting(area, Buffer.empty(area), raw, model(3), AcpHeaderStrip.LogoMode.AUTO);
        String first = raw.toString(StandardCharsets.US_ASCII);
        assertTrue(first.contains("a=t,f=100,t=d,i=" + AcpHeaderStrip.kittyImageId("bob")), first);
        assertTrue(first.endsWith(AcpHeaderStrip.kittyPlace(AcpHeaderStrip.kittyImageId("bob"), new Rect(0, 0, 5, 2))), first);
        raw.reset();
        strip.renderForTesting(area, Buffer.empty(area), raw, model(3), AcpHeaderStrip.LogoMode.AUTO);
        String second = raw.toString(StandardCharsets.US_ASCII);
        assertFalse(second.contains("a=t,"), "no second upload");
        assertEquals(AcpHeaderStrip.kittyPlace(AcpHeaderStrip.kittyImageId("bob"), new Rect(0, 0, 5, 2)), second);
        assertTrue(strip.hasPlacementForTesting());
        raw.reset();
        strip.hideForTesting(raw);
        assertEquals(AcpHeaderStrip.kittyDelete(AcpHeaderStrip.kittyImageId("bob")), raw.toString(StandardCharsets.US_ASCII));
        assertFalse(strip.hasPlacementForTesting());
        raw.reset();
        strip.hideForTesting(raw);
        assertEquals("", raw.toString(StandardCharsets.US_ASCII), "hide is a no-op without a placement");
    }

    @Test
    void uploadIsRetriedAfterAWriteFailure() {
        AcpHeaderStrip strip = new AcpHeaderStrip(KITTY);
        Rect area = new Rect(0, 0, 100, AcpHeaderStrip.ROWS);
        OutputStream broken = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("terminal gone");
            }
        };
        strip.renderForTesting(area, Buffer.empty(area), broken, model(1), AcpHeaderStrip.LogoMode.AUTO);
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        strip.renderForTesting(area, Buffer.empty(area), raw, model(1), AcpHeaderStrip.LogoMode.AUTO);
        String second = raw.toString(StandardCharsets.US_ASCII);
        assertTrue(second.contains("a=t,f=100,t=d,i="), "the upload is retried after a failed write");
        assertTrue(second.endsWith(AcpHeaderStrip.kittyPlace(AcpHeaderStrip.kittyImageId("bob"), new Rect(0, 0, 5, 2))),
                second);
    }

    @Test
    void logoBytesAreTheFullPng() throws Exception {
        AcpHeaderStrip strip = new AcpHeaderStrip(KITTY);
        byte[] png = strip.logoBytes("bob");
        assertNotNull(png);
        assertTrue(png.length > 1000);
        assertEquals((byte) 0x89, png[0]);
        assertNull(strip.logoBytes("nope"));
    }

    @Test
    void metaLineAndHomeRelativePath() {
        assertEquals("~/Work/camel", AcpHeaderStrip.homeRelative(Path.of(System.getProperty("user.home"), "Work", "camel")));
        assertEquals("/opt/x", AcpHeaderStrip.homeRelative(Path.of("/opt/x")));
        assertEquals("session 58b65aea · ~/Work/camel · 1 command · /agent: lists them", AcpHeaderStrip.metaLine(model(1)));
    }
}
