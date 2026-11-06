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

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.tamboui.style.Style;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.widgets.table.Cell;

/**
 * The exchanges a chaos rule faulted, so the Activity and Errors tabs mark them ({@code 🎲r1}): a failure made on
 * purpose is then not debugged as a real one. An error has the exchange property {@code CamelChaosRule} that
 * camel-chaos sets (CAMEL-26678). An activity entry has no properties, so the exchange ids of the recent faults of the
 * chaos report are kept as the status comes in, every second, the last ones per integration.
 */
final class ChaosMarks {

    /** The exchange property camel-chaos sets on an exchange a rule faulted, with the id of the rule. */
    static final String PROPERTY = "CamelChaosRule";

    // the faulted exchanges kept per integration: enough for the last minutes of activity
    static final int MAX = 2000;

    private final Map<String, Map<String, String>> byPid = new HashMap<>();

    /** Keeps the exchanges of the recent faults of the chaos report of the integration. */
    synchronized void record(IntegrationInfo info) {
        if (info == null || info.chaos == null || info.chaos.recentFaults.isEmpty()) {
            return;
        }
        Map<String, String> marks = byPid.computeIfAbsent(info.pid, k -> new LinkedHashMap<>(16, 0.75f, false) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                return size() > MAX;
            }
        });
        for (ChaosInfo.RecentFault f : info.chaos.recentFaults) {
            if (f.exchangeId() != null && f.rule() != null) {
                marks.put(f.exchangeId(), f.rule());
            }
        }
    }

    /** Forgets the marks of the integrations that are no longer running. */
    synchronized void retain(Collection<String> pids) {
        byPid.keySet().retainAll(pids);
    }

    synchronized String ruleOf(String pid, String exchangeId) {
        Map<String, String> marks = byPid.get(pid);
        return marks != null && exchangeId != null ? marks.get(exchangeId) : null;
    }

    /**
     * The rule that faulted an exchange: from its {@code CamelChaosRule} property when there are properties, else from
     * the recent faults seen; null when no rule did (or chaos is off).
     */
    static String ruleOf(MonitorContext ctx, IntegrationInfo info, String exchangeId, Map<String, Object> properties) {
        if (info == null || info.chaos == null) {
            return null;
        }
        if (properties != null && properties.get(PROPERTY) != null) {
            return String.valueOf(properties.get(PROPERTY));
        }
        return ctx != null && ctx.chaosMarks != null ? ctx.chaosMarks.ruleOf(info.pid, exchangeId) : null;
    }

    /** A cell with a text, and the mark of the rule after it when a rule faulted the exchange. */
    static Cell cell(String text, Style style, String rule) {
        Span span = Span.styled(text, style);
        return rule == null ? Cell.from(span) : Cell.from(Line.from(span, mark(rule)));
    }

    /** A cell with the mark of the rule before a text, when a rule faulted the exchange. */
    static Cell markedCell(String rule, String text) {
        return rule == null ? Cell.from(text) : Cell.from(Line.from(mark(rule), Span.raw(" " + text)));
    }

    /**
     * Adds the line that says a chaos rule faulted the exchange, with the rule, to the detail of the exchange; nothing
     * when no rule did.
     */
    static void addDetailLine(
            List<Line> lines, MonitorContext ctx, IntegrationInfo info, String exchangeId, Map<String, Object> properties) {
        String rule = ruleOf(ctx, info, exchangeId, properties);
        if (rule == null) {
            return;
        }
        ChaosInfo.Rule r = info.chaos.rule(rule);
        lines.add(Line.from(
                Span.styled(" Chaos:    ", Theme.muted()),
                Span.styled(TuiIcons.TAB_CHAOS + " injected by chaos rule " + rule, Theme.warning().bold()),
                Span.styled(r != null && r.text != null ? "  " + r.text : "", Theme.muted())));
    }

    private static Span mark(String rule) {
        return Span.styled(" " + TuiIcons.TAB_CHAOS + rule, Theme.warning());
    }
}
