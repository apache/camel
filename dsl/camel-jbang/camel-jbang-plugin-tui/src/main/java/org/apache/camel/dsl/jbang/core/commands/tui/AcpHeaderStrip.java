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

import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.widgets.paragraph.Paragraph;

/**
 * Two-row header shown under the AI panel title while an ACP session is open: the agent's coloured glyph, the preset
 * and agent labels, and a dimmed line with session, working directory and command count. ACP agents run headless and
 * never draw a start screen of their own.
 */
final class AcpHeaderStrip {

    static final int ROWS = 2;

    record Model(String presetLabel, String glyph, Color color, String agentLabel, String sessionId, Path cwd,
            int commandCount) {
    }

    void render(Frame frame, Rect area, Model model) {
        if (area.height() < ROWS || area.width() < 20) {
            return;
        }
        Style accent = Style.EMPTY.fg(model.color()).bold();
        String title = model.presetLabel() + " · " + model.agentLabel();
        Line first = Line.from(Span.styled(model.glyph() + " ", accent), Span.styled(title, accent));
        Line second = Line.from(Span.styled("  " + metaLine(model), Style.EMPTY.dim()));
        frame.renderWidget(Paragraph.from(first), new Rect(area.x(), area.y(), area.width(), 1));
        frame.renderWidget(Paragraph.from(second), new Rect(area.x(), area.y() + 1, area.width(), 1));
    }

    static String metaLine(Model model) {
        String session = model.sessionId() == null ? "?" : model.sessionId();
        if (session.length() > 8) {
            session = session.substring(0, 8);
        }
        String commands = model.commandCount() == 0
                ? "no commands yet"
                : model.commandCount() + (model.commandCount() == 1 ? " command" : " commands");
        return "session " + session + " · " + homeRelative(model.cwd()) + " · " + commands + " · /agent: lists them";
    }

    static String homeRelative(Path path) {
        if (path == null) {
            return "?";
        }
        String home = System.getProperty("user.home");
        String value = path.toAbsolutePath().toString();
        if (home != null && !home.isBlank() && value.startsWith(home)) {
            return "~" + value.substring(home.length());
        }
        return value;
    }

}
