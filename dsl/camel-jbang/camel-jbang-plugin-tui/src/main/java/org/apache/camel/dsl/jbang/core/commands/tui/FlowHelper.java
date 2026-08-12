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
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import dev.tamboui.layout.Rect;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.CharWidth;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.block.Title;
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.sparkline.DualSparkline;

final class FlowHelper {

    static final int MAX_CHART_POINTS = 300;

    private FlowHelper() {
    }

    static void renderFlowPanel(Frame frame, Rect area, long inTotal, long outTotal, String label) {
        int w = Math.max(10, area.width() - 2);

        String name = label != null ? label : "INTEGRATION";
        if (CharWidth.of(name) > 20) {
            name = CharWidth.truncateWithEllipsis(name, 20, CharWidth.TruncatePosition.END);
        }
        String box = "[ " + name + " ]";
        int boxLen = CharWidth.of(box);

        int sideLen = Math.max(4, (w - boxLen - 2) / 2);
        String arm = "─".repeat(Math.max(1, sideLen - 1));
        String arrowStr = arm + TuiIcons.POINTER;

        String inStr = String.valueOf(inTotal);
        String outStr = String.valueOf(outTotal);

        int inPad = Math.max(0, sideLen - inStr.length());
        int centerGap = boxLen + 2;
        int outPad = Math.max(0, sideLen - outStr.length());

        int inLabelPad = (sideLen - 2) / 2;
        int outLabelPad = (sideLen - 3) / 2;
        String inLabelStr = " ".repeat(inLabelPad) + "in" + " ".repeat(sideLen - inLabelPad - 2);
        String outLabelStr = " ".repeat(outLabelPad) + "out";

        Style inStyle = Theme.success();
        Style outStyle = Style.EMPTY.fg(Theme.accent());
        Style dimStyle = Style.EMPTY.dim();

        List<Line> flowLines = new ArrayList<>();
        flowLines.add(Line.from(Span.raw("")));
        flowLines.add(Line.from(Span.raw("")));
        flowLines.add(Line.from(
                Span.styled(" ".repeat(inPad) + inStr, inTotal > 0 ? inStyle : dimStyle),
                Span.raw(" ".repeat(centerGap)),
                Span.styled(outStr + " ".repeat(outPad), outTotal > 0 ? outStyle : dimStyle)));
        flowLines.add(Line.from(
                Span.styled(arrowStr, inStyle),
                Span.raw(" "),
                Span.styled(box, Theme.label().bold()),
                Span.raw(" "),
                Span.styled(arrowStr, outStyle)));
        flowLines.add(Line.from(
                Span.styled(inLabelStr, inStyle.dim()),
                Span.raw(" ".repeat(centerGap)),
                Span.styled(outLabelStr, outStyle.dim())));

        frame.renderWidget(Paragraph.builder()
                .text(dev.tamboui.text.Text.from(flowLines))
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).title(" Flow ").build())
                .build(), area);
    }

    static void renderThroughputChart(
            Frame frame, Rect area, LinkedList<Long> inHist, LinkedList<Long> outHist) {
        renderThroughputChart(frame, area, inHist, outHist, null);
    }

    static int computeRenderPoints(Rect area) {
        return Math.max(20, (Math.min(MAX_CHART_POINTS, area.width() - 6) / 20) * 20);
    }

    static void renderThroughputChart(
            Frame frame, Rect area, LinkedList<Long> inHist, LinkedList<Long> outHist, String chartLabel) {
        renderThroughputChart(frame, area, inHist, outHist, chartLabel, computeRenderPoints(area));
    }

    static void renderThroughputChart(
            Frame frame, Rect area, LinkedList<Long> inHist, LinkedList<Long> outHist,
            String chartLabel, int renderPoints) {
        long[] inArr = new long[renderPoints];
        long[] outArr = new long[renderPoints];
        for (int i = 0; i < renderPoints; i++) {
            int idx = inHist.size() - renderPoints + i;
            if (idx >= 0) {
                inArr[i] = unbox(inHist.get(idx));
            }
            idx = outHist.size() - renderPoints + i;
            if (idx >= 0) {
                outArr[i] = unbox(outHist.get(idx));
            }
        }
        long curIn = inArr[renderPoints - 1];
        long curOut = outArr[renderPoints - 1];
        // scale down from internal precision (rate * THROUGHPUT_SCALE) to actual msg/s for y-axis
        for (int i = 0; i < renderPoints; i++) {
            inArr[i] = Math.round((double) inArr[i] / MetricsCollector.THROUGHPUT_SCALE);
            outArr[i] = Math.round((double) outArr[i] / MetricsCollector.THROUGHPUT_SCALE);
        }

        List<Span> titleSpans = new ArrayList<>();
        if (chartLabel != null) {
            String label = chartLabel;
            if (CharWidth.of(label) > 30) {
                label = CharWidth.truncateWithEllipsis(label, 30, CharWidth.TruncatePosition.END);
            }
            titleSpans.add(Span.raw(" ["));
            titleSpans.add(Span.styled(label, Theme.label().bold()));
            titleSpans.add(Span.raw("] "));
        }
        if (chartLabel == null) {
            titleSpans.add(Span.raw(" "));
        }
        titleSpans.add(Span.styled("▬", Theme.success()));
        titleSpans.add(Span.raw(String.format(" in:%-4s ", MetricsCollector.formatThroughput(curIn))));
        titleSpans.add(Span.styled("▬", Style.EMPTY.fg(Theme.accent())));
        titleSpans.add(Span.raw(String.format(" out:%-4s msg/s ", MetricsCollector.formatThroughput(curOut))));

        frame.renderWidget(DualSparkline.builder()
                .topData(inArr)
                .bottomData(outArr)
                .topStyle(Theme.success())
                .bottomStyle(Style.EMPTY.fg(Theme.accent()))
                .showYAxis(true)
                .xLabels("-" + renderPoints + "s", "-" + (renderPoints * 3 / 4) + "s",
                        "-" + (renderPoints / 2) + "s", "-" + (renderPoints / 4) + "s", "now")
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                        .title(Title.from(Line.from(titleSpans.toArray(Span[]::new)))).build())
                .build(), area);
    }

    static void renderPayloadSizeChart(
            Frame frame, Rect area, LinkedList<Long> inHist, LinkedList<Long> outHist) {
        renderPayloadSizeChart(frame, area, inHist, outHist, computeRenderPoints(area));
    }

    static void renderPayloadSizeChart(
            Frame frame, Rect area, LinkedList<Long> inHist, LinkedList<Long> outHist, int renderPoints) {
        long[] inArr = new long[renderPoints];
        long[] outArr = new long[renderPoints];
        for (int i = 0; i < renderPoints; i++) {
            int idx = inHist.size() - renderPoints + i;
            if (idx >= 0) {
                inArr[i] = unbox(inHist.get(idx));
            }
            idx = outHist.size() - renderPoints + i;
            if (idx >= 0) {
                outArr[i] = unbox(outHist.get(idx));
            }
        }
        long curIn = inArr[renderPoints - 1];
        long curOut = outArr[renderPoints - 1];

        Line chartTitle = Line.from(
                Span.raw(" "),
                Span.styled("▬", Theme.label()),
                Span.raw(String.format(" in:%-8s ", sizeToString(curIn))),
                Span.styled("▬", Theme.notice()),
                Span.raw(String.format(" out:%-8s avg body ", sizeToString(curOut))));

        frame.renderWidget(DualSparkline.builder()
                .topData(inArr)
                .bottomData(outArr)
                .topStyle(Theme.label())
                .bottomStyle(Theme.notice())
                .showYAxis(true)
                .xLabels("-" + renderPoints + "s", "-" + (renderPoints * 3 / 4) + "s",
                        "-" + (renderPoints / 2) + "s", "-" + (renderPoints / 4) + "s", "now")
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                        .title(Title.from(chartTitle)).build())
                .build(), area);
    }

    static String sizeToString(long size) {
        if (size < 0) {
            return "-";
        }
        if (size == 0) {
            return "0 B";
        }
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format(Locale.US, "%.1f KB", size / 1024.0);
        } else {
            return String.format(Locale.US, "%.1f MB", size / (1024.0 * 1024.0));
        }
    }

    private static long unbox(Long value) {
        return value != null ? value : 0L;
    }
}
