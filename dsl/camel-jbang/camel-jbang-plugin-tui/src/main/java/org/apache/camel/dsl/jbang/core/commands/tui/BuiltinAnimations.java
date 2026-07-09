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

import java.util.ArrayList;
import java.util.List;

import dev.tamboui.style.Color;

/**
 * Pre-built animations that can be played via tui_animate with a name parameter. Avoids the AI having to generate large
 * frame payloads token by token.
 */
final class BuiltinAnimations {

    record Frame(long delayMs, List<DrawOverlay.DrawCell> cells) {
    }

    // Camel art variants for eye blink and tail wiggle
    private static final String[] CAMEL_OPEN = {
            " ,,__",
            "/o.  \\___/\\",
            "\\__/       \\",
            "   |   |   |",
            "   |   |   |~",
            "  (_) (_) (_)",
    };
    private static final String[] CAMEL_BLINK = {
            " ,,__",
            "/-.  \\___/\\",
            "\\__/       \\",
            "   |   |   |",
            "   |   |   |~",
            "  (_) (_) (_)",
    };
    private static final String[] CAMEL_TAIL_UP = {
            " ,,__",
            "/o.  \\___/\\",
            "\\__/       \\",
            "   |   |   |",
            "   |   |   |\\",
            "  (_) (_) (_)",
    };
    private static final String[] CAMEL_BLINK_TAIL_UP = {
            " ,,__",
            "/-.  \\___/\\",
            "\\__/       \\",
            "   |   |   |",
            "   |   |   |\\",
            "  (_) (_) (_)",
    };

    private BuiltinAnimations() {
    }

    static List<String> names() {
        return List.of("camel");
    }

    static List<Frame> get(String name) {
        return switch (name) {
            case "camel" -> camelLogo();
            default -> null;
        };
    }

    private static void addText(List<DrawOverlay.DrawCell> cells, int x, int y, String text, Color color) {
        cells.addAll(DrawOverlay.generateText(x, y, text, color));
    }

    private static void addCamel(List<DrawOverlay.DrawCell> cells, int x, int y, String[] art, int lines) {
        for (int i = 0; i < lines && i < art.length; i++) {
            addText(cells, x, y + i, art[i], Color.YELLOW);
        }
    }

    private static void addBigCamelText(List<DrawOverlay.DrawCell> cells, int x, int y) {
        addText(cells, x, y, " ██████╗  █████╗ ███╗   ███╗███████╗██╗", Color.YELLOW);
        addText(cells, x, y + 1, "██╔════╝ ██╔══██╗████╗ ████║██╔════╝██║", Color.YELLOW);
        addText(cells, x, y + 2, "██║      ███████║██╔████╔██║█████╗  ██║", Color.YELLOW);
        addText(cells, x, y + 3, "██║      ██╔══██║██║╚██╔╝██║██╔══╝  ██║", Color.YELLOW);
        addText(cells, x, y + 4, "╚██████╗ ██║  ██║██║ ╚═╝ ██║███████╗███████╗", Color.YELLOW);
        addText(cells, x, y + 5, " ╚═════╝ ╚═╝  ╚═╝╚═╝     ╚═╝╚══════╝╚══════╝", Color.YELLOW);
    }

    private static List<DrawOverlay.DrawCell> buildFinalFrame(int cx, int cy, String[] camelArt) {
        List<DrawOverlay.DrawCell> cells = new ArrayList<>();
        addText(cells, 52, 2, "A P A C H E", Color.WHITE);
        addCamel(cells, cx, cy, camelArt, camelArt.length);
        addBigCamelText(cells, 38, 14);
        addText(cells, 42, 22, "The Open Source Integration Framework", Color.WHITE);
        return cells;
    }

    private static List<Frame> camelLogo() {
        List<Frame> frames = new ArrayList<>();
        int cx = 57;
        int cy = 5;

        // Frame 1: Camel head (first 2 lines)
        List<DrawOverlay.DrawCell> f1 = new ArrayList<>();
        addCamel(f1, cx, cy, CAMEL_OPEN, 2);
        frames.add(new Frame(0, f1));

        // Frame 2: Camel body (first 4 lines)
        List<DrawOverlay.DrawCell> f2 = new ArrayList<>();
        addCamel(f2, cx, cy, CAMEL_OPEN, 4);
        frames.add(new Frame(400, f2));

        // Frame 3: Full camel (all 6 lines)
        List<DrawOverlay.DrawCell> f3 = new ArrayList<>();
        addCamel(f3, cx, cy, CAMEL_OPEN, 6);
        frames.add(new Frame(400, f3));

        // Frame 4: "APACHE" appears above
        List<DrawOverlay.DrawCell> f4 = new ArrayList<>();
        addText(f4, 52, 2, "A P A C H E", Color.WHITE);
        addCamel(f4, cx, cy, CAMEL_OPEN, 6);
        frames.add(new Frame(500, f4));

        // Frame 5: Large "CAMEL" text appears
        List<DrawOverlay.DrawCell> f5 = new ArrayList<>();
        addText(f5, 52, 2, "A P A C H E", Color.WHITE);
        addCamel(f5, cx, cy, CAMEL_OPEN, 6);
        addBigCamelText(f5, 38, 14);
        frames.add(new Frame(500, f5));

        // Frame 6: Tagline appears
        frames.add(new Frame(600, buildFinalFrame(cx, cy, CAMEL_OPEN)));

        // Frames 7-14: Eye blink + tail wiggle cycle (2 rounds)
        for (int round = 0; round < 2; round++) {
            frames.add(new Frame(500, buildFinalFrame(cx, cy, CAMEL_BLINK)));
            frames.add(new Frame(150, buildFinalFrame(cx, cy, CAMEL_OPEN)));
            frames.add(new Frame(300, buildFinalFrame(cx, cy, CAMEL_TAIL_UP)));
            frames.add(new Frame(200, buildFinalFrame(cx, cy, CAMEL_OPEN)));
        }

        // Final hold before auto-close
        frames.add(new Frame(2000, buildFinalFrame(cx, cy, CAMEL_OPEN)));

        return frames;
    }
}
