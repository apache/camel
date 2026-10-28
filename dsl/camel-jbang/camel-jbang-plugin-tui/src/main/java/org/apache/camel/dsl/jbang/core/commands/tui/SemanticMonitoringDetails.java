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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.*;

/** Readable current measurements and their observation context. */
final class SemanticMonitoringDetails {
    private SemanticMonitoringDetails() {
    }

    static Map<?, ?> map(Object value) {
        return value instanceof Map<?, ?> map ? map : Map.of();
    }

    static String display(Object value) {
        return value == null ? "not provided" : value.toString().replaceAll("[\\p{Cc}\\p{Cf}]", " ");
    }

    static String dimension(Map<?, ?> row, String key) {
        Object value = row.get(key);
        return display(value == null ? map(row.get("dimensions")).get(key) : value);
    }

    static String aggregation(Map<?, ?> row) {
        String declared = dimension(row, "aggregation");
        if (!"not provided".equals(declared)) {
            return declared;
        }
        if (!"not provided".equals(dimension(row, "definition"))) {
            return "definition";
        }
        return "not provided".equals(dimension(row, "expert")) ? "source" : "expert";
    }

    static String duration(Object nanos) {
        if (!(nanos instanceof Number number)) {
            return "not provided";
        }
        try {
            return new BigDecimal(number.toString()).movePointLeft(6).setScale(3, RoundingMode.HALF_UP).stripTrailingZeros()
                    .toPlainString()
                   + " ms";
        } catch (NumberFormatException invalid) {
            return "not provided";
        }
    }

    static String time(Object value) {
        if (value != null) {
            try {
                return DateTimeFormatter.ofPattern("dd MMM HH:mm:ss", Locale.ROOT).withZone(ZoneId.systemDefault())
                        .format(Instant.parse(value.toString()));
            } catch (RuntimeException invalid) {
                // Keep the supplied value when it is not an ISO timestamp.
            }
        }
        return display(value);
    }

    static List<Line> lines(JsonObject row, int width, boolean expanded) {
        List<Line> lines = new ArrayList<>();
        if (row == null) {
            return List.of(Line.from("No matching measurements. Adjust the view filters or check the source."));
        }
        lines.add(Line.from(Span.styled(display(row.get("name")), Theme.title()),
                Span.raw(" · " + display(row.get("unit")))));
        lines.addAll(hangingWrap(List.of(Line.from(Span.styled(display(row.get("meaning")), Theme.info()))), width));
        Map<?, ?> values = map(row.get("values"));
        List<Line> observations = new ArrayList<>();
        observations.add(heading(values.containsKey("completed") ? "Observed evaluations" : "Reported measurements"));
        add(observations, "Completed", values.get("completed"));
        add(observations, "Answers", values.get("answers"));
        add(observations, "Failed", values.get("failed"));
        add(observations, "Not executed", values.get("notExecuted"));
        add(observations, "Result unavailable", values.get("unavailable"));
        if (observations.size() == 1) {
            add(observations, "Samples", row.get("denominator"));
        }
        List<Line> timing = new ArrayList<>();
        timing.add(heading("Observation period and duration"));
        add(timing, "From", time(row.get("from")));
        add(timing, "Last observation", time(row.get("to")));
        if (values.get("meanDurationNanos") != null) {
            add(timing, "Mean duration", duration(values.get("meanDurationNanos")));
            add(timing, "Min / max", duration(values.get("minimumDurationNanos")) + " / "
                                     + duration(values.get("maximumDurationNanos")));
            add(timing, "Timed evaluations", values.get("durationCount"));
        }
        columns(lines, observations, timing, width);
        lines.add(Line.from(Span.styled("─".repeat(Math.max(0, width)), Theme.border())));
        List<Line> distribution = distribution(row, width >= 110 ? (width - 3) / 2 : width);
        List<Line> details = new ArrayList<>();
        details.add(heading("Source details"));
        Map<?, ?> dimensions = map(row.get("dimensions"));
        if ("definition".equals(aggregation(row))) {
            add(details, "Definition", dimension(row, "definition"));
        }
        add(details, "Expert", dimensions.get("expert"));
        add(details, "Provider", dimensions.get("provider"));
        add(details, "Operation", dimensions.get("operation"));
        add(details, "Population", dimensions.get("population"));
        add(details, "Model", dimensions.get("model"));
        add(details, "Revision", row.get("revision"));
        add(details, "Excluded", row.get("exclusions"));
        add(details, "Measurement loss", row.get("loss"));
        columns(lines, distribution, details, width);
        if (expanded) {
            lines.add(heading("Measurement context"));
            for (String field : List.of("source", "id", "epoch", "from", "to", "availability")) {
                add(lines, field, row.get(field));
            }
            dimensions.forEach((key, value) -> add(lines, display(key), value));
            map(row.get("values")).forEach((key, value) -> {
                if (!(value instanceof Map<?, ?>) && !(value instanceof List<?>)) {
                    add(lines, display(key), value);
                }
            });
        }
        return lines;
    }

    private static List<Line> distribution(Map<?, ?> row, int width) {
        List<Line> lines = new ArrayList<>();
        Map<?, ?> values = map(row.get("values"));
        Map<String, Object> distribution = counts(values.get("distribution"));
        if (!distribution.isEmpty()) {
            lines.add(heading("Returned answers · count"));
            distribution.forEach((label, count) -> lines.add(Line.from(truncate(label, Math.max(12, width - 26))
                                                                       + "  " + display(count))));
        } else if (values.get("histogramUpperBounds") instanceof List<?> bounds
                && values.get("histogramCounts") instanceof List<?> counts && counts.size() == bounds.size() + 1) {
            lines.add(heading("Score histogram · count"));
            for (int i = 0; i < counts.size(); i++) {
                String label = bounds.isEmpty() ? "All scores"
                        : i == 0 ? "≤ " + display(bounds.get(0))
                        : i == bounds.size() ? "> " + display(bounds.get(i - 1))
                        : "(" + display(bounds.get(i - 1)) + ", " + display(bounds.get(i)) + "]";
                lines.add(Line.from(label + "  " + display(counts.get(i))));
            }
            add(lines, "Min / max score", display(values.get("minimum")) + " / " + display(values.get("maximum")));
        } else {
            lines.add(heading("Reported measurement"));
            values.forEach((key, value) -> {
                if (!(value instanceof Map<?, ?>) && !(value instanceof List<?>)) {
                    add(lines, display(key), value);
                }
            });
        }
        add(lines, "Population", values.get("distributionMeaning"));
        return lines;
    }

    private static Map<String, Object> counts(Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (value instanceof List<?> rows) {
            for (Object row : rows) {
                Map<?, ?> item = map(row);
                if (item.get("label") != null) {
                    result.put(display(item.get("label")), item.get("count"));
                }
            }
        } else if (value instanceof Map<?, ?> rows) {
            rows.forEach((key, count) -> result.put(display(key), count));
        }
        return result;
    }

    private static Line heading(String title) {
        return Line.from(Span.styled(title, Theme.title()));
    }

    private static void add(List<Line> lines, String label, Object value) {
        if (value != null) {
            lines.add(Line.from(Span.styled(label + ": ", Theme.label()), Span.raw(display(value))));
        }
    }

    private static void columns(List<Line> target, List<Line> left, List<Line> right, int width) {
        if (width < 110) {
            target.addAll(hangingWrap(left, width));
            target.add(Line.empty());
            target.addAll(hangingWrap(right, width));
            return;
        }
        int column = (width - 3) / 2;
        List<Line> a = hangingWrap(left, column);
        List<Line> b = hangingWrap(right, width - column - 3);
        for (int i = 0; i < Math.max(a.size(), b.size()); i++) {
            Line line = i < a.size() ? a.get(i) : Line.empty();
            List<Span> spans = new ArrayList<>(line.spans());
            spans.add(Span.raw(" ".repeat(Math.max(0, column - line.width()))));
            spans.add(Span.styled(" │ ", Theme.border()));
            if (i < b.size()) {
                spans.addAll(b.get(i).spans());
            }
            target.add(Line.from(spans));
        }
    }
}
