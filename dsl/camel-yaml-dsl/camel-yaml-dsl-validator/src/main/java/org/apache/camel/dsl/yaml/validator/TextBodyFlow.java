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
package org.apache.camel.dsl.yaml.validator;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.Error;
import com.networknt.schema.path.NodePath;
import org.apache.camel.util.MimeTypeHelper;

import static org.apache.camel.dsl.yaml.validator.RouteGraph.endpointOf;
import static org.apache.camel.dsl.yaml.validator.RouteGraph.normalize;
import static org.apache.camel.dsl.yaml.validator.RouteGraph.scheme;

/**
 * A field read on a body that is still text.
 * <p/>
 * Groovy {@code body.find { it.sku == ... }} or simple {@code ${body[sku]}} reads the fields of parsed data: a Map or a
 * List. When the step before left the payload as text - a {@code setBody} with {@code constant}, a {@code marshal}, the
 * file consumer, or a {@code direct:} route that ends with one of those - the read fails at runtime, Groovy with "No
 * such property: sku for class: java.lang.Byte". The walk follows the steps of each route in order, and into the
 * {@code direct:} and {@code seda:} routes it calls, and reports only where it is certain the body is text: anything it
 * does not know makes it say nothing (CAMEL-24844).
 */
final class TextBodyFlow {

    /** The Groovy methods that iterate a collection with a closure; on text they iterate the characters or bytes. */
    private static final Set<String> ITERATES = Set.of("find", "findAll", "each", "eachWithIndex", "collect",
            "collectEntries", "any", "every", "grep", "count", "sum", "findResult", "groupBy", "sort", "max", "min");

    /** The methods of a Map, which text does not have. */
    private static final Set<String> MAP_METHODS = Set.of("get", "getAt", "containsKey", "keySet", "values",
            "entrySet");

    /** What can be read from text without parsing it: a property of String, or a Groovy property of text. */
    private static final Set<String> TEXT_PROPERTIES = Set.of("bytes", "text", "class", "empty", "blank", "length",
            "size", "toString", "trim", "lines", "chars", "hashCode");

    /** What the file consumer's GenericFile has besides: ${body.fileName} reads the file, not its content. */
    private static final Set<String> FILE_PROPERTIES = Set.of("file", "fileName", "fileNameOnly", "fileLength",
            "lastModified", "charset", "extendedAttributes", "body", "parent", "absoluteFilePath", "relativeFilePath",
            "absolute", "endpointPath", "directory", "copyFromAbsoluteFilePath", "lastOffsetValue", "fileSeparator",
            "binding");

    /** body.x, body?.x, message.body.x, followed by what comes after the name. */
    private static final Pattern GROOVY_PROPERTY = Pattern.compile("(?<![\\w$])body\\s*\\??\\.\\s*([A-Za-z_]\\w*)\\s*([{(])?");

    /** body['x'] or body["x"]. */
    private static final Pattern GROOVY_KEY = Pattern.compile("(?<![\\w$])body\\s*\\[\\s*['\"]");

    /** ${body.x} or ${body?.x}, not a method call. */
    private static final Pattern SIMPLE_PROPERTY = Pattern.compile("\\$\\{body\\??\\.([A-Za-z_]\\w*)(?!\\w|\\s*\\()");

    /** ${body[x]} where x is a key, not an index. */
    private static final Pattern SIMPLE_KEY = Pattern.compile("\\$\\{body\\[\\s*['\"]?[A-Za-z_]");

    /** Steps whose own steps run on the same body, so a read inside them sees the body at the step. */
    private static final Set<String> SAME_BODY_BLOCKS = Set.of("choice", "when", "otherwise", "filter", "doTry",
            "doCatch", "doFinally");

    /** Endpoints that leave the body as it is. */
    private static final Set<String> KEEPS_THE_BODY = Set.of("log", "mock");

    private TextBodyFlow() {
    }

    /** What is known about the body at a step: text, and where it came from, or nothing (null). */
    record Text(String origin, String format, boolean file) {

        Text(String origin, String format) {
            this(origin, format, false);
        }

        /** The names that can be read from this body without parsing it. */
        Set<String> readable() {
            if (!file) {
                return TEXT_PROPERTIES;
            }
            Set<String> answer = new HashSet<>(TEXT_PROPERTIES);
            answer.addAll(FILE_PROPERTIES);
            return answer;
        }
    }

    static void check(JsonNode target, NodePath path, List<Error> errors) {
        if (target == null || !target.isArray()) {
            return;
        }
        Map<String, JsonNode> byFrom = new HashMap<>();
        for (JsonNode entry : target) {
            JsonNode from = from(entry);
            String uri = from != null ? endpointOf(from) : null;
            if (uri != null) {
                byFrom.put(normalize(uri), entry);
            }
        }
        for (int i = 0; i < target.size(); i++) {
            JsonNode entry = target.get(i);
            JsonNode from = from(entry);
            if (from == null) {
                continue;
            }
            NodePath at = path.append(i);
            JsonNode steps = from.get("steps");
            if (entry.has("route")) {
                at = at.append("route");
                if (steps == null) {
                    steps = entry.get("route").get("steps");
                    at = at.append("steps");
                } else {
                    at = at.append("from").append("steps");
                }
            } else {
                at = at.append("from").append("steps");
            }
            String id = entry.has("route") && entry.get("route").has("id") ? entry.get("route").get("id").asText() : null;
            walk(steps, consumerText(from), at, id, byFrom, errors, new HashSet<>());
        }
    }

    private static JsonNode from(JsonNode entry) {
        if (entry == null || !entry.isObject()) {
            return null;
        }
        JsonNode route = entry.get("route");
        return route != null ? route.get("from") : entry.get("from");
    }

    /** The body a consumer starts with: the file consumer reads a file, the text as it is; others are not known. */
    private static Text consumerText(JsonNode from) {
        String uri = endpointOf(from);
        if (!"file".equals(scheme(uri))) {
            return null;
        }
        JsonNode parameters = from.get("parameters");
        String format = null;
        for (String key : new String[] { "include", "antInclude", "fileName" }) {
            if (format == null && parameters != null && parameters.isObject() && parameters.has(key)) {
                // .*\.csv and *.csv end with the extension, as a file name does
                format = formatOf(parameters.get(key).asText().replace("\\", ""));
            }
        }
        return new Text("the file consumer gives it", format, true);
    }

    /**
     * Walks the steps in order with what is known about the body, reports the reads that need parsed data where it is
     * text, and answers what is known at the end. A report is only made when errors is not null: the routes a step
     * calls are walked for their outcome alone, and report from their own walk.
     */
    private static Text walk(
            JsonNode steps, Text text, NodePath at, String routeId, Map<String, JsonNode> byFrom,
            List<Error> errors, Set<String> seen) {
        if (steps == null || !steps.isArray()) {
            return text;
        }
        for (int j = 0; j < steps.size(); j++) {
            JsonNode step = steps.get(j);
            if (!step.isObject() || !step.fieldNames().hasNext()) {
                text = null;
                continue;
            }
            String name = step.fieldNames().next();
            JsonNode value = step.get(name);
            NodePath here = at.append(j).append(name);
            if (text != null && errors != null) {
                if (SAME_BODY_BLOCKS.contains(name)) {
                    walkBlock(value, text, here, routeId, byFrom, errors, seen);
                } else {
                    String read = readIn(value, text.readable());
                    if (read != null) {
                        errors.add(error(here, routeId, read, text));
                    }
                }
            }
            text = after(name, value, step, text, byFrom, seen);
        }
        return text;
    }

    /**
     * A choice, filter or doTry: its predicates read the body at the step, and so do its steps until one changes it.
     */
    private static void walkBlock(
            JsonNode node, Text text, NodePath at, String routeId, Map<String, JsonNode> byFrom,
            List<Error> errors, Set<String> seen) {
        if (node == null) {
            return;
        }
        if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                walkBlock(node.get(i), text, at.append(i), routeId, byFrom, errors, seen);
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }
        for (var it = node.fieldNames(); it.hasNext();) {
            String key = it.next();
            JsonNode child = node.get(key);
            if ("steps".equals(key)) {
                walk(child, text, at.append(key), routeId, byFrom, errors, seen);
            } else if (SAME_BODY_BLOCKS.contains(key) || child.isArray()) {
                walkBlock(child, text, at.append(key), routeId, byFrom, errors, seen);
            } else {
                String read = readEntry(key, child, text.readable());
                if (read != null) {
                    errors.add(error(at.append(key), routeId, read, text));
                }
            }
        }
    }

    /** What is known about the body after a step. */
    private static Text after(
            String name, JsonNode value, JsonNode step, Text text, Map<String, JsonNode> byFrom, Set<String> seen) {
        switch (name) {
            case "setBody", "transform" -> {
                return setBodyText(value);
            }
            case "marshal" -> {
                String format = value != null && value.isObject() && value.fieldNames().hasNext()
                        ? value.fieldNames().next() : null;
                return new Text(
                        "marshal" + (format != null ? ": " + format : "")
                                + " writes data as text (marshal turns data into text, unmarshal turns text into data)",
                        format);
            }
            case "convertBodyTo" -> {
                String type = value != null && value.isObject() && value.has("type") ? value.get("type").asText()
                        : value != null && value.isTextual() ? value.asText() : "";
                return "String".equals(type) || "java.lang.String".equals(type) || "byte[]".equals(type)
                        ? new Text("convertBodyTo " + type + " makes it text", text != null ? text.format() : null)
                        : null;
            }
            case "to", "toD" -> {
                String uri = endpointOf(value);
                String scheme = scheme(uri);
                if (scheme != null && KEEPS_THE_BODY.contains(scheme)) {
                    return text;
                }
                JsonNode called = uri != null ? byFrom.get(normalize(uri)) : null;
                if (called == null || !"to".equals(name) || !seen.add(normalize(uri))) {
                    return null;
                }
                JsonNode from = from(called);
                JsonNode steps = from.has("steps") ? from.get("steps")
                        : called.has("route") ? called.get("route").get("steps") : null;
                Text end = walk(steps, text, new NodePath(com.networknt.schema.path.PathType.JSON_POINTER), null, byFrom,
                        null, seen);
                seen.remove(normalize(uri));
                return end == null
                        ? null
                        : new Text(
                                "to: " + normalize(uri) + " returns it as text (" + end.origin() + ")", end.format(),
                                end.file());
            }
            case "setHeader", "setHeaders", "setProperty", "setVariable", "removeHeader", "removeHeaders",
                    "removeProperty", "removeProperties", "log", "wireTap", "delay", "throttle", "stop" -> {
                return text;
            }
            default -> {
                return null;
            }
        }
    }

    /**
     * The body after a setBody: text for a constant, and for a simple template that is more than one ${...}; not known
     * for any other language, which may return data.
     */
    private static Text setBodyText(JsonNode value) {
        JsonNode node = value != null && value.has("expression") ? value.get("expression") : value;
        if (node == null || !node.isObject() || !node.fieldNames().hasNext()) {
            return null;
        }
        String language = node.fieldNames().next();
        String expression = expressionText(node.get(language));
        if (expression == null) {
            return null;
        }
        if ("constant".equals(language)) {
            String origin = expression.startsWith("resource:")
                    ? "setBody with constant: " + expression + " loads the file as text"
                    : "setBody with constant sets text";
            return new Text(origin, formatOf(expression));
        }
        if ("simple".equals(language)) {
            String trimmed = expression.trim();
            boolean single = trimmed.startsWith("${") && trimmed.endsWith("}") && trimmed.indexOf("${", 2) < 0;
            return single ? null : new Text("setBody with a simple template sets text", formatOf(trimmed));
        }
        return null;
    }

    /** The text of an expression, short form or with expression: inside. */
    private static String expressionText(JsonNode node) {
        if (node == null) {
            return null;
        }
        if (node.isValueNode()) {
            return node.asText();
        }
        JsonNode expression = node.get("expression");
        return expression != null && expression.isValueNode() ? expression.asText() : null;
    }

    /** A Groovy or simple expression in this node, not in nested steps, that reads fields; what it reads, or null. */
    private static String readIn(JsonNode node, Set<String> readable) {
        if (node == null) {
            return null;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                String found = readIn(child, readable);
                if (found != null) {
                    return found;
                }
            }
            return null;
        }
        if (!node.isObject()) {
            return null;
        }
        for (var it = node.fieldNames(); it.hasNext();) {
            String key = it.next();
            if ("steps".equals(key)) {
                continue;
            }
            String found = readEntry(key, node.get(key), readable);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** One entry of a node: a Groovy or simple expression that reads fields, or anything nested that does. */
    private static String readEntry(String key, JsonNode child, Set<String> readable) {
        if ("groovy".equals(key)) {
            String found = groovyRead(expressionText(child), readable);
            return found != null ? "groovy reads fields of the body (" + found + ")" : null;
        } else if ("simple".equals(key)) {
            String found = simpleRead(expressionText(child), readable);
            return found != null ? "simple reads a field of the body (" + found + ")" : null;
        }
        return readIn(child, readable);
    }

    static String groovyRead(String script, Set<String> readable) {
        if (script == null) {
            return null;
        }
        Matcher m = GROOVY_PROPERTY.matcher(script);
        while (m.find()) {
            String name = m.group(1);
            String next = m.group(2);
            if ("{".equals(next) && ITERATES.contains(name)
                    || "(".equals(next) && MAP_METHODS.contains(name)
                    || next == null && !readable.contains(name)) {
                return m.group().trim() + ("{".equals(next) ? " ... }" : "");
            }
        }
        m = GROOVY_KEY.matcher(script);
        return m.find()
                ? script.substring(m.start(), Math.min(script.length(), script.indexOf(']', m.start()) + 1))
                : null;
    }

    static String simpleRead(String template, Set<String> readable) {
        if (template == null) {
            return null;
        }
        Matcher m = SIMPLE_PROPERTY.matcher(template);
        while (m.find()) {
            if (!readable.contains(m.group(1))) {
                return m.group() + "}";
            }
        }
        m = SIMPLE_KEY.matcher(template);
        if (m.find()) {
            int end = template.indexOf('}', m.start());
            return end > 0 ? template.substring(m.start(), end + 1) : m.group();
        }
        return null;
    }

    /**
     * The data format a name points at: a data format such as json, a file name or resource (by its extension, the way
     * {@link MimeTypeHelper} maps it to a content type), or the first character of the text.
     */
    static String formatOf(String hint) {
        if (hint == null) {
            return null;
        }
        String t = hint.trim();
        String format = formatOfMimeType(t);
        if (format == null && t.indexOf('.') >= 0 && t.indexOf(' ') < 0) {
            format = formatOfMimeType(MimeTypeHelper.probeMimeType(t));
        }
        if (format == null) {
            if (t.startsWith("{") || t.startsWith("[")) {
                format = "json";
            } else if (t.startsWith("<")) {
                format = "jacksonXml";
            }
        }
        return format;
    }

    /** The data format of a content type, or of a data format name: json, csv, yaml or jacksonXml. */
    private static String formatOfMimeType(String type) {
        if (type == null) {
            return null;
        }
        String s = type.toLowerCase(Locale.ROOT);
        if (s.equals("json") || s.endsWith("/json") || s.endsWith("+json")) {
            return "json";
        } else if (s.equals("csv") || s.endsWith("/csv") || s.endsWith("/tab-separated-values")) {
            return "csv";
        } else if (s.equals("yaml") || s.endsWith("/yaml") || s.endsWith("/x-yaml")) {
            return "yaml";
        } else if (s.equals("jacksonxml") || s.endsWith("/xml") || s.endsWith("+xml")) {
            return "jacksonXml";
        } else if (s.equals("ical") || s.endsWith("/calendar")) {
            return "ical";
        }
        return null;
    }

    private static Error error(NodePath at, String routeId, String read, Text text) {
        String format = text.format();
        String fix;
        if (format == null) {
            fix = "unmarshal it first with the data format of the payload (json, jacksonXml, csv, ...) to read its fields";
        } else if (text.origin().startsWith("marshal")) {
            fix = "to read its fields, unmarshal: " + format + " is the step, not marshal";
        } else {
            fix = "add unmarshal: " + format + " before this step to read its fields";
        }
        return Error.builder()
                .keyword("type")
                .instanceLocation(at)
                .messageKey("type")
                .format(new java.text.MessageFormat("{0}"))
                .arguments((routeId != null ? "route " + routeId + ": " : "") + read
                           + (text.file()
                                   ? ", but the body here is the file as it was read (a GenericFile), not parsed data - "
                                   : ", but the body here is still text, not parsed data - ")
                           + text.origin() + ": " + fix)
                .build();
    }
}
