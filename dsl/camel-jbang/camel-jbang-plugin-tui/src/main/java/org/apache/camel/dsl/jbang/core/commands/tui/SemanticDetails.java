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
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import dev.tamboui.layout.Rect;
import dev.tamboui.style.Overflow;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.paragraph.Paragraph;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.dsl.jbang.core.commands.tui.SemanticTab.objects;

/** Human-readable summaries of the published contract, shared by all three semantic views. */
final class SemanticDetails {
    private SemanticDetails() {
    }

    static JsonObject operation(JsonObject data, JsonObject definition) {
        if (definition == null) {
            return null;
        }
        return objects(data, "experts").stream()
                .filter(expert -> Objects.equals(expert.get("reference"), definition.get("expert")))
                .flatMap(expert -> objects(expert, "operations").stream())
                .filter(op -> Objects.equals(op.get("name"), definition.get("operation")))
                .findFirst().orElse(null);
    }

    static JsonObject contract(JsonObject operation) {
        return operation != null && operation.get("contract") instanceof JsonObject c ? c : new JsonObject();
    }

    static String inputTypes(JsonObject operation) {
        Object types = contract(operation).get("inputTypes");
        return types instanceof List<?> list ? list.stream().map(Object::toString).collect(Collectors.joining(" | ")) : "—";
    }

    static Line signature(JsonObject operation) {
        return Line.from(Span.styled(text(operation, "name"), Theme.title()),
                Span.raw("(" + inputTypes(operation) + ")"),
                Span.styled(" → " + text(operation, "resultType"), Theme.info()));
    }

    static List<String> scoreLevels(JsonObject operation, Object parameters) {
        if (operation == null || !"score".equals(operation.get("resultType"))
                || !(contract(operation).get("scoreLevelsParameter") instanceof String parameter) || parameter.isBlank()
                || !(parameters instanceof Map<?, ?> values)
                || !(values.get(parameter) instanceof List<?> levels) || levels.isEmpty()
                || levels.stream().anyMatch(level -> !(level instanceof String description) || description.isBlank())) {
            return List.of();
        }
        return levels.stream().map(String.class::cast).toList();
    }

    static void scoreRange(List<Line> lines, List<String> levels) {
        if (!levels.isEmpty()) {
            add(lines, "Effective range", "0 … " + (levels.size() - 1) + " · " + levels.size()
                                          + (levels.size() == 1 ? " level" : " levels"));
        }
    }

    private static void parameters(List<Line> lines, JsonObject definition) {
        if (definition == null || !(definition.get("parameters") instanceof Map<?, ?> map) || map.isEmpty()) {
            add(lines, "Parameters", "none · expert defaults");
            return;
        }
        lines.add(Line.from(Span.styled("Parameters", Theme.label())));
        map.forEach((name, value) -> parameter(lines, "  ", name + ": ", value));
    }

    private static void parameter(List<Line> lines, String indent, String label, Object value) {
        if (value instanceof Map<?, ?> map && !map.isEmpty()) {
            lines.add(Line.from(Span.styled(indent + label.stripTrailing(), Theme.label())));
            map.forEach((name, child) -> parameter(lines, indent + "  ", "• " + name + ": ", child));
        } else if (value instanceof List<?> list && !list.isEmpty()) {
            lines.add(Line.from(Span.styled(indent + label.stripTrailing(), Theme.label())));
            for (int i = 0; i < list.size(); i++) {
                parameter(lines, indent + "  ", "[" + i + "] ", list.get(i));
            }
        } else if (value instanceof String text) {
            // Put text under its label so wrapped prose keeps its place in the parameter tree.
            lines.add(Line.from(Span.styled(indent + label.stripTrailing(), Theme.label())));
            for (String part : text.split("\\R", -1)) {
                lines.add(Line.from(indent + "  " + (part.isEmpty() ? "\"\"" : part)));
            }
        } else {
            lines.add(Line.from(Span.styled(indent + label, Theme.label()), Span.raw(String.valueOf(value))));
        }
    }

    static String evidence(JsonObject operation) {
        JsonObject contract = contract(operation);
        List<String> supported = new ArrayList<>();
        if (contract.getBooleanOrDefault("probability", false)) {
            supported.add("probability");
        }
        if (contract.getBooleanOrDefault("probabilities", false)) {
            supported.add("probabilities by label");
        }
        if (contract.getBooleanOrDefault("confidence", false)) {
            supported.add("confidence");
        }
        return supported.isEmpty() ? "value only" : String.join(" · ", supported) + " (when provided)";
    }

    static List<Line> definition(JsonObject data, JsonObject definition) {
        if (definition == null) {
            return List.of(Line.from("Select a definition"));
        }
        List<Line> lines = new ArrayList<>();
        lines.add(Line.from(Span.styled(text(definition, "name"), Theme.title())));
        lines.add(Line.empty());
        add(lines, "Expert", text(definition, "expert"));
        add(lines, "Operation", text(definition, "operation"));
        lines.add(Line.empty());
        add(lines, "State", text(definition, "state"));
        lines.add(Line.empty());
        parameters(lines, definition);
        lines.add(Line.empty());
        lines.addAll(result(operation(data, definition), definition.get("parameters")));
        unresolved(lines, definition);
        return lines;
    }

    static void renderDefinition(Frame frame, Rect area, JsonObject data, JsonObject definition, Block block, int scroll) {
        frame.renderWidget(block, area);
        Rect inner = inset(block.inner(area));
        if (inner.width() < 96 || definition == null) {
            paragraph(frame, inner, definition(data, definition), scroll);
            return;
        }
        Line heading = Line.from(Span.styled(text(definition, "name"), Theme.title()),
                Span.styled(" · uses ", Theme.muted()), Span.raw(text(definition, "expert")),
                Span.styled(" / ", Theme.muted()), Span.raw(text(definition, "operation")),
                Span.styled("   →   " + text(definition, "resultType"), Theme.info()));
        paragraph(frame, new Rect(inner.x(), inner.y(), inner.width(), 1), List.of(heading), 0);
        int width = (inner.width() - 4) / 2;
        int y = inner.y() + 3;
        int height = Math.max(0, inner.height() - 3);
        List<Line> source = new ArrayList<>();
        source.add(Line.from(Span.styled("Declaration", Theme.label())));
        source.add(Line.empty());
        add(source, "State", text(definition, "state"));
        source.add(Line.empty());
        parameters(source, definition);
        source.add(Line.empty());
        JsonObject op = operation(data, definition);

        unresolved(source, definition);
        paragraph(frame, new Rect(inner.x(), y, width, height), source, scroll);
        List<Line> result = new ArrayList<>();
        result.add(Line.from(Span.styled("Operation contract", Theme.label())));
        result.add(Line.empty());
        add(result, "Input", inputTypes(op));
        add(result, "Accepts", text(contract(op), "inputRequirements"));
        result.add(Line.empty());
        result.addAll(result(op, definition.get("parameters")));
        paragraph(frame, new Rect(inner.x() + width + 4, y, inner.width() - width - 4, height), result, scroll);
    }

    private static List<Line> result(JsonObject op, Object parameters) {
        List<Line> lines = new ArrayList<>();
        if (op == null) {
            lines.add(Line.from(Span.styled("Contract unavailable", Theme.warning())));
            return lines;
        }
        lines.add(Line.from(Span.styled("Result: ", Theme.label()), Span.styled(text(op, "resultType"), Theme.info())));
        scoreRange(lines, scoreLevels(op, parameters));
        lines.add(Line.empty());
        lines.add(Line.from(text(contract(op), "resultMeaning")));
        lines.add(Line.empty());
        add(lines, "Evidence", evidence(op));
        addIfPresent(lines, contract(op), "probabilityMeaning", "Probability");
        addIfPresent(lines, contract(op), "confidenceMeaning", "Confidence");
        addBounds(lines, contract(op));
        return lines;
    }

    static List<Line> expert(JsonObject data, JsonObject expert, JsonObject operation) {
        if (expert == null) {
            return List.of(Line.from("Select an expert"));
        }
        List<Line> lines = new ArrayList<>();
        lines.add(Line.from(Span.styled(text(expert, "description"), Theme.title())));
        lines.add(Line.from(Span.styled(text(expert, "provider") + " · " + text(expert, "artifactId"), Theme.muted())));
        lines.add(Line.empty());
        if (operation != null) {
            lines.add(signature(operation));
            JsonObject c = contract(operation);
            add(lines, "Input", text(c, "inputRequirements"));
            add(lines, "Result", text(c, "resultMeaning"));
            add(lines, "Evidence", evidence(operation));
            addBounds(lines, c);
            if (c.get("scoreLevelsParameter") instanceof String parameter && !parameter.isBlank()) {
                add(lines, "Score levels", parameter + " · index 0 to last level; fractional scores allowed");
            }
            List<JsonObject> parameters = objects(c, "parameters");
            lines.add(Line.empty());
            if (parameters.isEmpty()) {
                add(lines, "Parameters", "none");
            } else {
                lines.add(Line.from(Span.styled("Parameters", Theme.label())));
                for (JsonObject p : parameters) {
                    add(lines, text(p, "name"), parameterSummary(p));
                    if (p.get("description") != null && !text(p, "description").isBlank()) {
                        lines.add(Line.from(Span.styled("  " + text(p, "description"), Theme.muted())));
                    }
                }
            }
        } else {
            lines.add(Line.from("No published operations"));
        }
        unresolved(lines, expert);
        return lines;
    }

    private static String parameterSummary(JsonObject p) {
        List<String> parts = new ArrayList<>();
        String type = text(p, "type");
        if (p.getBooleanOrDefault("integer", false)) {
            type = "Integer";
        }
        if (("List".equals(type) || "Map".equals(type)) && p.get("itemType") != null && !"Object".equals(p.get("itemType"))) {
            type += "<" + text(p, "itemType") + ">";
        }
        parts.add(type);
        if (p.get("minimum") != null || p.get("maximum") != null) {
            parts.add(text(p, "minimum") + " … " + text(p, "maximum"));
        }
        if (p.get("values") instanceof List<?> values && !values.isEmpty()) {
            parts.add(values.stream().map(Object::toString).collect(Collectors.joining(" | ")));
        }
        if (p.get("minSize") instanceof Number min && min.intValue() > 0) {
            parts.add("min size " + min);
        }
        if (p.get("maxSize") instanceof Number max && max.intValue() < Integer.MAX_VALUE) {
            parts.add("max size " + max);
        }
        parts.add(p.getBooleanOrDefault("required", false) ? "required" : "optional");
        if (!p.getBooleanOrDefault("required", false) && p.get("omission") != null && !text(p, "omission").isBlank()) {
            parts.add(text(p, "omission"));
        }
        return String.join(" · ", parts);
    }

    private static void addBounds(List<Line> lines, JsonObject contract) {
        if (contract.get("labels") instanceof List<?> labels && !labels.isEmpty()) {
            add(lines, "Labels", labels.stream().map(Object::toString).collect(Collectors.joining(" · ")));
        }
        if (contract.get("minimum") != null || contract.get("maximum") != null) {
            add(lines, "Supported range", text(contract, "minimum") + " … " + text(contract, "maximum"));
        }
    }

    private static void addIfPresent(List<Line> lines, JsonObject value, String key, String label) {
        if (value.get(key) != null && !text(value, key).isBlank()) {
            add(lines, label, text(value, key));
        }
    }

    private static void unresolved(List<Line> lines, JsonObject value) {
        if (value != null && value.get("error") != null) {
            lines.add(Line.empty());
            lines.add(Line.from(Span.styled(text(value, "error"), Theme.error())));
        }
    }

    static void add(List<Line> lines, String label, String value) {
        lines.add(Line.from(Span.styled(label + ": ", Theme.label()), Span.raw(value)));
    }

    static Rect inset(Rect area) {
        return new Rect(
                area.x() + Math.min(2, area.width()), area.y() + Math.min(1, area.height()),
                Math.max(0, area.width() - 4), Math.max(0, area.height() - 2));
    }

    static void paragraph(Frame frame, Rect area, List<Line> lines, int scroll) {
        frame.renderWidget(Paragraph.builder().text(Text.from(TuiHelper.hangingWrap(lines, area.width())))
                .overflow(Overflow.WRAP_WORD).scroll(scroll).build(),
                area);
    }

    static String text(JsonObject value, String key) {
        return value == null || value.get(key) == null ? "—" : value.get(key).toString();
    }
}
