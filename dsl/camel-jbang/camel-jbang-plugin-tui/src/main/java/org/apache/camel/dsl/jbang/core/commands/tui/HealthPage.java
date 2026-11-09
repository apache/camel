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

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import dev.tamboui.layout.Rect;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.CharWidth;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.paragraph.Paragraph;
import org.apache.camel.util.TimeUtils;

/**
 * The Health tab as a page: when every check is UP, that it is all good in one look, how long, and which checks Camel
 * runs on its own; when one is not, the checks that are not first, what that means for the integration, and the other
 * checks folded into one line. Under both, whether it was ready and live second by second, so a check that went DOWN
 * and came back is seen.
 */
final class HealthPage {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private HealthPage() {
    }

    static void render(Frame frame, Rect area, IntegrationInfo info, HealthHistory history, long now) {
        List<HealthCheckInfo> checks = info.healthChecks;
        List<HealthCheckInfo> notUp = checks.stream().filter(hc -> !"UP".equals(hc.state)).toList();
        boolean notReady = Boolean.FALSE.equals(info.healthReady);
        boolean notLive = Boolean.FALSE.equals(info.healthLive);
        String title = notLive ? " Health ─ ✗ NOT LIVE " : notReady ? " Health ─ ✗ NOT READY " : " Health ";
        Block block = Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                .borderStyle(notUp.isEmpty() ? Style.EMPTY : Theme.error()).title(title).build();
        int width = Math.max(0, area.width() - 2);

        List<Line> lines = new ArrayList<>();
        lines.add(Line.from(Span.raw("")));
        if (checks.isEmpty()) {
            lines.add(center(width, Span.styled("No health checks registered", Theme.muted())));
        } else if (notUp.isEmpty()) {
            allGood(lines, info, width, now);
        } else {
            somethingWrong(lines, info, notUp, notReady, notLive, now);
        }
        lines.add(Line.from(Span.raw("")));
        strip(lines, info, history, width, now);
        frame.renderWidget(Paragraph.builder().text(Text.from(lines)).block(block).build(), area);
    }

    /** All UP: a thumbs up, since when, how often checked, and what is checked. */
    private static void allGood(List<Line> lines, IntegrationInfo info, int width, long now) {
        List<HealthCheckInfo> checks = info.healthChecks;
        lines.add(center(width, Span.styled("👍  All good", Theme.success().bold())));
        lines.add(Line.from(Span.raw("")));
        lines.add(center(width,
                Span.styled("ready ", Theme.muted()), mark(info.healthReady),
                Span.styled("    live ", Theme.muted()), mark(info.healthLive),
                Span.styled("    " + checks.size() + (checks.size() == 1 ? " check UP" : " checks UP"), Theme.muted())));
        long since = checks.stream().mapToLong(hc -> hc.since).filter(s -> s > 0).min().orElse(0);
        long checked = checks.stream().mapToLong(hc -> hc.invocations).max().orElse(0);
        long failures = checks.stream().mapToLong(hc -> hc.failures).sum();
        long lastFailure = checks.stream().mapToLong(hc -> hc.lastFailure).max().orElse(0);
        List<String> facts = new ArrayList<>();
        if (since > 0) {
            facts.add("up since " + TIME.format(Instant.ofEpochMilli(since)) + " (" + ago(since, now) + ")");
        }
        if (checked > 0) {
            facts.add("checked " + checked + " times");
        }
        facts.add(failures == 0
                ? "never failed"
                : failures + (failures == 1 ? " failure" : " failures")
                  + (lastFailure > 0 ? ", the last " + ago(lastFailure, now) + " ago" : ""));
        lines.add(center(width, Span.styled(String.join(" · ", facts), Theme.muted())));
        lines.add(Line.from(Span.raw("")));
        String camel = builtInSummary(checks);
        if (!camel.isEmpty()) {
            lines.add(Line.from(Span.styled("   Camel checks on its own: ", Theme.muted()), Span.raw(camel)));
        }
        List<HealthCheckInfo> own = checks.stream().filter(hc -> !isBuiltIn(hc)).toList();
        List<Span> yours = new ArrayList<>();
        yours.add(Span.styled("   Your checks: ", Theme.muted()));
        if (own.isEmpty()) {
            yours.add(Span.styled("none", Theme.muted()));
        }
        for (int i = 0; i < own.size(); i++) {
            yours.add(Span.raw(i == 0 ? "" : " · "));
            yours.add(Span.styled(TuiIcons.HEALTH_UP + " " + own.get(i).name, Theme.success()));
        }
        lines.add(Line.from(yours));
    }

    /** Not all UP: the checks that are not, with why and since when, what it means, and the rest in one line. */
    private static void somethingWrong(
            List<Line> lines, IntegrationInfo info, List<HealthCheckInfo> notUp, boolean notReady, boolean notLive,
            long now) {
        for (HealthCheckInfo hc : notUp) {
            boolean down = "DOWN".equals(hc.state);
            Style style = down ? Theme.error() : Theme.warning();
            List<Span> head = new ArrayList<>();
            head.add(Span.styled("  " + (down ? TuiIcons.HEALTH_DOWN : TuiIcons.HEALTH_WARN) + " ", style));
            head.add(Span.styled(hc.name != null ? hc.name : "", style.bold()));
            head.add(Span.styled("   " + hc.state, style));
            if (hc.since > 0) {
                head.add(Span.raw(" since " + TIME.format(Instant.ofEpochMilli(hc.since)) + " (" + ago(hc.since, now) + ")"));
            }
            head.add(Span.styled(" · " + kind(hc), Theme.muted()));
            lines.add(Line.from(head));
            if (hc.message != null && !hc.message.isBlank()) {
                lines.add(Line.from(Span.raw("      " + TuiHelper.fixControlChars(hc.message.lines().findFirst().orElse("")))));
            }
            List<String> facts = new ArrayList<>();
            if (hc.failures > 0) {
                facts.add("failed " + hc.failures + (hc.failures == 1 ? " time" : " times")
                          + (hc.invocations > 0 ? ", of " + hc.invocations + " checks" : ""));
            }
            if (hc.lastUp > 0) {
                facts.add("last up " + TIME.format(Instant.ofEpochMilli(hc.lastUp)));
            }
            if (!facts.isEmpty()) {
                lines.add(Line.from(Span.styled("      " + String.join(" · ", facts), Theme.muted())));
            }
            lines.add(Line.from(Span.raw("")));
        }
        if (notLive) {
            lines.add(Line.from(Span.styled("  ✗ not live", Theme.error().bold()),
                    Span.raw(" — Kubernetes would restart the integration")));
        } else if (notReady) {
            lines.add(Line.from(Span.styled("  live ", Theme.muted()), mark(info.healthLive),
                    Span.raw(" — the integration runs, but Kubernetes would take it out of the service until the checks"
                             + " above are UP again")));
        }
        int up = info.healthChecks.size() - notUp.size();
        if (up > 0) {
            lines.add(Line.from(Span.styled("  " + up + (up == 1 ? " other check UP" : " other checks UP"),
                    Theme.muted())));
        }
    }

    /** Ready and live, second by second over the last minutes, as the TUI read them. */
    private static void strip(List<Line> lines, IntegrationInfo info, HealthHistory history, int width, long now) {
        int label = 10;
        int seconds = Math.max(10, Math.min(HealthHistory.SECONDS, width - label - 24));
        long end = now / 1000;
        byte[] values = history.strip(info.pid, end, seconds);
        lines.add(stripLine("ready", values, HealthHistory.READY, label, downNote(values, HealthHistory.READY, end)));
        lines.add(stripLine("live", values, HealthHistory.LIVE, label, downNote(values, HealthHistory.LIVE, end)));
        String from = TIME.format(Instant.ofEpochSecond(end - seconds + 1));
        lines.add(Line.from(Span.styled(" ".repeat(label) + from
                                        + " ".repeat(Math.max(1, seconds - from.length() - 3)) + "now",
                Theme.muted())));
    }

    private static Line stripLine(String name, byte[] values, byte bit, int label, String note) {
        List<Span> spans = new ArrayList<>();
        spans.add(Span.styled(String.format("%-" + label + "s", "   " + name), Theme.label()));
        for (byte v : values) {
            if (v == HealthHistory.UNKNOWN) {
                spans.add(Span.raw(" "));
            } else {
                spans.add((v & bit) != 0 ? Span.styled("█", Theme.success()) : Span.styled("█", Theme.error()));
            }
        }
        if (note != null) {
            spans.add(Span.styled("  " + note, Theme.warning()));
        }
        return Line.from(spans);
    }

    /** How long it was down in the strip and when it last was, when it was: a flap the page would not show. */
    static String downNote(byte[] values, byte bit, long end) {
        int down = 0;
        int last = -1;
        for (int i = 0; i < values.length; i++) {
            if (values[i] != HealthHistory.UNKNOWN && (values[i] & bit) == 0) {
                down++;
                last = i;
            }
        }
        if (down == 0) {
            return null;
        }
        long at = end - values.length + 1 + last;
        return "down " + down + "s, the last at " + TIME.format(Instant.ofEpochSecond(at));
    }

    /** Whether a check is one Camel runs on its own: the context, the routes, their consumers and producers. */
    static boolean isBuiltIn(HealthCheckInfo hc) {
        String n = hc.name;
        if (n == null || !"camel".equals(hc.group)) {
            return false;
        }
        return n.equals("context") || n.equals("route-controller") || n.equals("security-policy")
                || n.equals("routes") || n.equals("consumers") || n.equals("producers")
                || n.startsWith("route:") || n.startsWith("consumer:") || n.startsWith("producer:");
    }

    /** The checks Camel runs on its own, in words: the context, the route controller, 2 routes and 2 consumers. */
    static String builtInSummary(List<HealthCheckInfo> checks) {
        List<String> parts = new ArrayList<>();
        int routes = 0;
        int consumers = 0;
        int producers = 0;
        for (HealthCheckInfo hc : checks) {
            if (!isBuiltIn(hc)) {
                continue;
            }
            String n = hc.name;
            if (n.startsWith("route:") || n.equals("routes")) {
                routes++;
            } else if (n.startsWith("consumer:") || n.equals("consumers")) {
                consumers++;
            } else if (n.startsWith("producer:") || n.equals("producers")) {
                producers++;
            } else {
                parts.add("the " + n.replace('-', ' '));
            }
        }
        if (routes > 0) {
            parts.add(routes + (routes == 1 ? " route" : " routes"));
        }
        if (consumers > 0) {
            parts.add(consumers + (consumers == 1 ? " consumer" : " consumers"));
        }
        if (producers > 0) {
            parts.add(producers + (producers == 1 ? " producer" : " producers"));
        }
        if (parts.size() <= 1) {
            return String.join("", parts);
        }
        return String.join(", ", parts.subList(0, parts.size() - 1)) + " and " + parts.get(parts.size() - 1);
    }

    private static String kind(HealthCheckInfo hc) {
        return hc.readiness && hc.liveness ? "readiness and liveness" : hc.liveness ? "liveness" : "readiness";
    }

    private static Span mark(Boolean ok) {
        if (ok == null) {
            return Span.styled("?", Theme.muted());
        }
        return ok ? Span.styled("✔", Theme.success()) : Span.styled("✗", Theme.error());
    }

    private static String ago(long time, long now) {
        return TimeUtils.printDuration(Math.max(0, now - time), false);
    }

    private static Line center(int width, Span... spans) {
        int w = 0;
        for (Span s : spans) {
            w += CharWidth.of(s.content());
        }
        List<Span> all = new ArrayList<>();
        all.add(Span.raw(" ".repeat(Math.max(0, (width - w) / 2))));
        all.addAll(List.of(spans));
        return Line.from(all);
    }
}
