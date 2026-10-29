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
import java.util.ArrayList;
import java.util.List;

import dev.tamboui.style.Style;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;

/**
 * The routes of the project, shown in the Source pane of {@link SourceTab} before a file is opened.
 */
final class SourceEmptyLines {

    private SourceEmptyLines() {
    }

    /**
     * What the Source pane shows before a file is opened: the routes of the project and where they are, so a Maven
     * project (whose routes are folders deep) shows what it holds and how to open one.
     */
    static List<Line> of(List<SourceTab.RouteEntry> routes, Path root) {
        List<Line> lines = new ArrayList<>();
        lines.add(Line.from(Span.raw("")));
        if (routes.isEmpty()) {
            lines.add(Line.from(Span.styled("   Select a file and press Enter to view source", Style.EMPTY.dim())));
            return lines;
        }
        lines.add(Line.from(Span.styled("   " + routes.size() + (routes.size() == 1 ? " route" : " routes")
                                        + " in this project",
                Theme.label())));
        lines.add(Line.from(Span.raw("")));
        int idWidth = 4;
        for (SourceTab.RouteEntry re : routes) {
            idWidth = Math.max(idWidth, label(re).length());
        }
        idWidth = Math.min(idWidth, 30);
        for (SourceTab.RouteEntry re : routes) {
            String where = re.filePath();
            if (root != null && where != null) {
                try {
                    where = root.relativize(Path.of(where)).toString();
                } catch (IllegalArgumentException e) {
                    // not under the project: keep the full path
                }
            }
            lines.add(Line.from(
                    Span.styled("   " + String.format("%-" + idWidth + "s", TuiHelper.truncate(label(re), idWidth)),
                            Theme.info()),
                    Span.styled("  " + where + ":" + re.fromLine(), Style.EMPTY.dim())));
        }
        lines.add(Line.from(Span.raw("")));
        lines.add(Line.from(
                Span.styled("   ", Style.EMPTY),
                Span.styled(" g ", Theme.hintKey()),
                Span.styled(" opens a route, or select a file and press Enter", Style.EMPTY.dim())));
        return lines;
    }

    private static String label(SourceTab.RouteEntry re) {
        if (re.routeId() != null && !re.routeId().isBlank()) {
            return re.routeId();
        }
        return re.fromUri() != null ? re.fromUri() : "";
    }
}
