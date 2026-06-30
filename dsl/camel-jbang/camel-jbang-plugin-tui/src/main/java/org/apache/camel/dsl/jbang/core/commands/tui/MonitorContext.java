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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.tui.TuiRunner;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.block.Title;
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.table.Cell;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/**
 * Shared state and utilities accessible to all {@link MonitorTab} implementations.
 */
class MonitorContext {

    /** Small flat-orange camel for empty / no-selection states. */
    static final String[] SMALL_CAMEL = {
            " ,,__",
            "/o.  \\___/\\",
            "\\__/       \\",
            "   |   |   |",
            "   |   |   |~",
            "  (_) (_) (_)",
    };

    final AtomicReference<List<IntegrationInfo>> data;
    final AtomicReference<List<InfraInfo>> infraData;
    TuiRunner runner;

    String selectedPid;
    String lastSelectedName;
    int shellPercent;

    MonitorContext(
                   AtomicReference<List<IntegrationInfo>> data,
                   AtomicReference<List<InfraInfo>> infraData) {
        this.data = data;
        this.infraData = infraData;
    }

    IntegrationInfo findSelectedIntegration() {
        String pid = selectedPid;
        if (pid == null) {
            return null;
        }
        return data.get().stream()
                .filter(i -> pid.equals(i.pid) && !i.vanishing)
                .findFirst().orElse(null);
    }

    InfraInfo findSelectedInfra() {
        String pid = selectedPid;
        if (pid == null) {
            return null;
        }
        return infraData.get().stream()
                .filter(i -> pid.equals(i.pid) && !i.vanishing)
                .findFirst().orElse(null);
    }

    boolean isInfraSelected() {
        return findSelectedInfra() != null;
    }

    String selectedName() {
        IntegrationInfo info = findSelectedIntegration();
        if (info != null) {
            return TuiHelper.truncate(info.name, 20);
        }
        InfraInfo infra = findSelectedInfra();
        if (infra != null) {
            return TuiHelper.truncate(infra.alias, 20);
        }
        return "?";
    }

    Path getActionFile(String pid) {
        return CommandLineHelper.getCamelDir().resolve(pid + "-action.json");
    }

    Path getOutputFile(String pid) {
        return CommandLineHelper.getCamelDir().resolve(pid + "-output.json");
    }

    Path getTraceFile(String pid) {
        return CommandLineHelper.getCamelDir().resolve(pid + "-trace.json");
    }

    static JsonObject pollJsonResponse(Path outputFile, long timeout) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeout) {
            try {
                Thread.sleep(100);
                if (Files.exists(outputFile) && outputFile.toFile().length() > 0) {
                    String text = Files.readString(outputFile);
                    return (JsonObject) Jsoner.deserialize(text);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } catch (Exception e) {
                // ignore
            }
        }
        return null;
    }

    static void hint(List<Span> spans, String key, String label) {
        spans.add(Span.styled(" " + key + " ", Theme.hintKey()));
        spans.add(Span.raw(" " + label + "  "));
    }

    static void hintLast(List<Span> spans, String key, String label) {
        spans.add(Span.styled(" " + key + " ", Theme.hintKey()));
        spans.add(Span.raw(" " + label));
    }

    static void renderNoSelection(Frame frame, Rect area) {
        List<Line> lines = new ArrayList<>();
        lines.add(Line.from(Span.raw("")));
        for (String row : SMALL_CAMEL) {
            lines.add(Line.from(Span.styled("   " + row, Style.EMPTY.fg(Theme.accent()))));
        }
        lines.add(Line.from(Span.raw("")));
        List<Span> hintSpans = new ArrayList<>();
        hintSpans.add(Span.raw("   No integration selected.  "));
        hint(hintSpans, "1", "Overview");
        hint(hintSpans, "?", "Help");
        lines.add(Line.from(hintSpans));

        frame.renderWidget(
                Paragraph.builder()
                        .text(Text.from(lines))
                        .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                                .title(Title.from(Line.from(
                                        Span.styled(" No integration selected ", Theme.title()))))
                                .build())
                        .build(),
                area);
    }

    static String truncate(String s, int max) {
        return TuiHelper.truncate(s, max);
    }

    static Cell rightCell(String text, int width) {
        return Cell.from(String.format("%" + width + "s", text));
    }

    static Cell rightCell(String text, int width, Style style) {
        return Cell.from(Span.styled(String.format("%" + width + "s", text), style));
    }

    static Cell centerCell(String text, int width) {
        int len = text.length();
        int padding = Math.max(0, width - len);
        int leftPad = padding / 2;
        return Cell.from(" ".repeat(leftPad) + text);
    }

    static Cell centerCell(String text, int width, Style style) {
        int len = text.length();
        int padding = Math.max(0, width - len);
        int leftPad = padding / 2;
        return Cell.from(Span.styled(" ".repeat(leftPad) + text, style));
    }

    static String sortLabel(String label, String column, String currentSort, boolean reversed) {
        return currentSort.equals(column) ? label + (reversed ? "▲" : "▼") : label;
    }

    static Style sortStyle(String column, String currentSort) {
        return currentSort.equals(column)
                ? Style.EMPTY.fg(Color.YELLOW).bold()
                : Style.EMPTY.bold();
    }

    static String formatSinceLast(IntegrationInfo info) {
        return formatSinceLast(info.sinceLastStarted, info.sinceLastCompleted, info.sinceLastFailed);
    }

    static String formatSinceLast(String started, String completed, String failed) {
        StringBuilder sb = new StringBuilder();
        if (started != null) {
            sb.append(started);
        }
        if (completed != null) {
            if (!sb.isEmpty()) {
                sb.append('/');
            }
            sb.append(completed);
        }
        if (failed != null) {
            if (!sb.isEmpty()) {
                sb.append('/');
            }
            sb.append(failed);
        }
        return sb.toString();
    }

    static String formatSinceLastRoute(RouteInfo route) {
        return formatSinceLast(route.sinceLastStarted, route.sinceLastCompleted, route.sinceLastFailed);
    }

    static String formatLoad(String l1, String l5, String l15) {
        String s1 = l1 != null && !"0.00".equals(l1) ? l1 : "0";
        String s5 = l5 != null && !"0.00".equals(l5) ? l5 : "0";
        String s15 = l15 != null && !"0.00".equals(l15) ? l15 : "0";
        return s1 + "/" + s5 + "/" + s15;
    }

    static String formatMemory(long used, long max) {
        if (used <= 0) {
            return "";
        }
        String u = formatBytes(used);
        if (max > 0) {
            return u + "/" + formatBytes(max);
        }
        return u;
    }

    static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + "B";
        }
        if (bytes < 1024 * 1024) {
            return (bytes / 1024) + "K";
        }
        return (bytes / (1024 * 1024)) + "M";
    }

    static String formatThreads(int count, int peak) {
        if (count <= 0) {
            return "";
        }
        return count + "/" + peak;
    }

    static Style topTimeStyle(long ms) {
        if (ms >= 1000) {
            return Style.EMPTY.fg(Color.LIGHT_RED).bold();
        } else if (ms >= 100) {
            return Style.EMPTY.fg(Color.YELLOW);
        }
        return Style.EMPTY;
    }

    static Style topDeltaStyle(long delta) {
        if (delta > 0) {
            return Style.EMPTY.fg(Color.LIGHT_RED);
        } else if (delta < 0) {
            return Style.EMPTY.fg(Color.GREEN);
        }
        return Style.EMPTY;
    }

    static String buildBar(long value, long maxValue, int maxWidth) {
        if (value <= 0 || maxValue <= 0) {
            return "";
        }
        int len = (int) Math.round((double) value / maxValue * maxWidth);
        len = Math.max(len > 0 ? 1 : 0, Math.min(len, maxWidth));
        return "█".repeat(len);
    }

    static int compareStr(String a, String b) {
        if (a == null && b == null)
            return 0;
        if (a == null)
            return 1;
        if (b == null)
            return -1;
        return a.compareToIgnoreCase(b);
    }
}
