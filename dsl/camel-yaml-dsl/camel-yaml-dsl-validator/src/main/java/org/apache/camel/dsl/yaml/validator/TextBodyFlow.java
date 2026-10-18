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

import java.text.MessageFormat;
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
import com.networknt.schema.path.PathType;
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
 * <p/>
 * The other way round, parsed data where a step needs text: after {@code unmarshal: json} the body is a Map or a List,
 * and a {@code to: file}, an http producer that sends it with POST, or the reply of a {@code platform-http} route fails
 * at runtime, because no type converter turns a Map into the stream they write: "No type converter available to convert
 * from type: java.util.LinkedHashMap to the required type: java.io.InputStream".
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

    /** Steps that leave the body as it is. */
    private static final Set<String> KEEPING_STEPS = Set.of("setHeader", "setHeaders", "setProperty", "setVariable",
            "setVariables", "removeHeader", "removeHeaders", "removeProperty", "removeProperties", "removeVariable",
            "convertHeaderTo", "convertVariableTo", "validate", "log", "wireTap", "delay", "throttle", "sample", "stop");

    /** ${body}, ${body[lines]} or ${body.lines}: the body, or a field of it. */
    private static final Pattern THE_BODY = Pattern.compile("\\$\\{body(\\??\\.[A-Za-z_]\\w*|\\[[^\\]]+])*}");

    /** The data formats whose unmarshal makes a Map or a List of the text. */
    private static final Set<String> PARSES = Set.of("json", "jacksonXml", "yaml", "csv");

    /** The unmarshal options that make something else of the text: an object of a class, or an iterator. */
    private static final Set<String> NOT_A_MAP = Set.of("unmarshalType", "unmarshalTypeName", "lazyLoad");

    /** The Groovy collection methods whose result is data again: a List or a Map. */
    private static final Set<String> GIVES_DATA = Set.of("findAll", "collect", "collectEntries", "grep", "sort",
            "groupBy", "unique");

    /** The Groovy collection methods that pick an element, which is data when the closure reads its fields. */
    private static final Set<String> PICKS = Set.of("find", "max", "min");

    /** body.findAll {, the start of a collection method on the body. */
    private static final Pattern COLLECTION_CALL = Pattern.compile("body\\s*\\??\\.\\s*([A-Za-z_]\\w*)\\s*\\{");

    /** The parameter of a closure: x -> ... */
    private static final Pattern CLOSURE_PARAMETER = Pattern.compile("^\\s*([A-Za-z_]\\w*)\\s*->");

    /** The producers that write the body to a file. */
    private static final Set<String> WRITES_A_FILE = Set.of("file", "ftp", "ftps", "sftp");

    /**
     * The consumers that bring no HTTP control headers, so an http producer after them picks POST for a body: after an
     * HTTP consumer, the CamelHttpMethod header of the request picks the method instead.
     */
    private static final Set<String> NO_HTTP_HEADERS = Set.of("file", "ftp", "ftps", "sftp", "timer", "scheduler",
            "cron", "quartz");

    /** The options of the http producer: any other parameter goes into the query string, which makes the call a GET. */
    private static final Set<String> HTTP_OPTIONS = Set.of("httpMethod", "throwExceptionOnFailure", "bridgeEndpoint",
            "okStatusCodeRange", "followRedirects", "copyHeaders", "skipRequestHeaders", "skipResponseHeaders",
            "disableStreamCache", "authMethod", "authUsername", "authPassword", "authBearerToken",
            "sslContextParameters", "userAgent", "logHttpActivity", "lazyStartProducer", "useSystemProperties");

    /** The HTTP methods that send the body. */
    private static final Set<String> SENDS_A_BODY = Set.of("POST", "PUT", "PATCH");

    private TextBodyFlow() {
    }

    /** What is known about the body at a step: text or parsed data, and where it came from; or nothing (null). */
    sealed interface Body permits Text, Parsed {

        String origin();

        String format();
    }

    /** Parsed data: the Map or the List an unmarshal makes of the text. */
    record Parsed(String origin, String format) implements Body {
    }

    /** Text: the payload as it was read or written. */
    record Text(String origin, String format, boolean file) implements Body {

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
            String consumer = scheme(endpointOf(from));
            boolean post = NO_HTTP_HEADERS.contains(consumer) && !setsHttpHeaders(steps);
            Body end = walk(steps, consumerText(from), at, id, byFrom, errors, new HashSet<>(), post);
            // the body at the end of a platform-http route is the reply, written as a stream
            if ("platform-http".equals(consumer) && end instanceof Parsed parsed && steps != null && steps.isArray()
                    && !steps.isEmpty() && steps.get(steps.size() - 1).fieldNames().hasNext()) {
                int last = steps.size() - 1;
                errors.add(error(at.append(last).append(steps.get(last).fieldNames().next()), id,
                        new Need("the body at the end of the route is the reply to the HTTP request", "as the last step"),
                        parsed));
            }
        }
    }

    /**
     * Whether the steps set or read an HTTP control header (CamelHttpMethod, CamelHttpQuery...), which can make an http
     * producer call with GET and leave the body out.
     */
    private static boolean setsHttpHeaders(JsonNode steps) {
        return steps != null && steps.toString().toLowerCase(Locale.ROOT).contains("camelhttp");
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
     * text and the steps that need text where it is parsed data, and answers what is known at the end. A report is only
     * made when errors is not null: the routes a step calls are walked for their outcome alone, and report from their
     * own walk. post says an http producer here sends the body with POST: nothing in the route can pick another method.
     */
    private static Body walk(
            JsonNode steps, Body body, NodePath at, String routeId, Map<String, JsonNode> byFrom,
            List<Error> errors, Set<String> seen, boolean post) {
        if (steps == null || !steps.isArray()) {
            return body;
        }
        for (int j = 0; j < steps.size(); j++) {
            JsonNode step = steps.get(j);
            if (!step.isObject() || !step.fieldNames().hasNext()) {
                body = null;
                continue;
            }
            String name = step.fieldNames().next();
            JsonNode value = step.get(name);
            NodePath here = at.append(j).append(name);
            if (body != null && errors != null) {
                if (SAME_BODY_BLOCKS.contains(name)) {
                    walkBlock(value, body, here, routeId, byFrom, errors, seen, post);
                } else if (body instanceof Text text) {
                    String read = readIn(value, text.readable());
                    if (read != null) {
                        errors.add(error(here, routeId, read, text));
                    }
                } else if (body instanceof Parsed parsed) {
                    Need need = needsText(name, value, post);
                    Parsed element = "split".equals(name) ? splitElement(value, parsed) : null;
                    if (need != null) {
                        errors.add(error(here, routeId, need, parsed));
                    } else if (element != null) {
                        walk(value.get("steps"), element, here.append("steps"), routeId, byFrom, errors, seen, post);
                    }
                }
            }
            body = after(name, value, step, body, byFrom, seen);
        }
        return body;
    }

    /**
     * A choice, filter or doTry: its predicates read the body at the step, and so do its steps until one changes it.
     */
    private static void walkBlock(
            JsonNode node, Body body, NodePath at, String routeId, Map<String, JsonNode> byFrom,
            List<Error> errors, Set<String> seen, boolean post) {
        if (node == null) {
            return;
        }
        if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                walkBlock(node.get(i), body, at.append(i), routeId, byFrom, errors, seen, post);
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
                walk(child, body, at.append(key), routeId, byFrom, errors, seen, post);
            } else if (SAME_BODY_BLOCKS.contains(key) || child.isArray()) {
                walkBlock(child, body, at.append(key), routeId, byFrom, errors, seen, post);
            } else if (body instanceof Text text) {
                String read = readEntry(key, child, text.readable());
                if (read != null) {
                    errors.add(error(at.append(key), routeId, read, text));
                }
            }
        }
    }

    /** What is known about the body after a step. */
    private static Body after(
            String name, JsonNode value, JsonNode step, Body body, Map<String, JsonNode> byFrom, Set<String> seen) {
        switch (name) {
            case "setBody", "transform" -> {
                Text text = setBodyText(value);
                if (text != null || !(body instanceof Parsed parsed)) {
                    return text;
                }
                String call = keepsData(value);
                return call != null
                        ? new Parsed(parsed.origin() + ", and groovy " + call + " keeps it data", parsed.format())
                        : null;
            }
            case "unmarshal" -> {
                return unmarshalled(value);
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
                        ? new Text("convertBodyTo " + type + " makes it text", body != null ? body.format() : null)
                        : null;
            }
            case "to", "toD" -> {
                String uri = endpointOf(value);
                String scheme = scheme(uri);
                if (scheme != null && KEEPS_THE_BODY.contains(scheme)) {
                    return body;
                }
                JsonNode called = uri != null ? byFrom.get(normalize(uri)) : null;
                if (called == null || !"to".equals(name) || !seen.add(normalize(uri))) {
                    return null;
                }
                JsonNode from = from(called);
                JsonNode steps = from.has("steps") ? from.get("steps")
                        : called.has("route") ? called.get("route").get("steps") : null;
                Body end = walk(steps, body, new NodePath(PathType.JSON_POINTER), null, byFrom,
                        null, seen, false);
                seen.remove(normalize(uri));
                if (end instanceof Text t) {
                    return new Text(
                            "to: " + normalize(uri) + " returns it as text (" + t.origin() + ")", t.format(),
                            t.file());
                } else if (end instanceof Parsed p) {
                    return new Parsed("to: " + normalize(uri) + " returns it as data (" + p.origin() + ")", p.format());
                }
                return null;
            }
            default -> {
                return KEEPING_STEPS.contains(name) ? body : null;
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

    /** The body after an unmarshal: parsed data for the data formats that make a Map or a List of the text. */
    private static Parsed unmarshalled(JsonNode value) {
        if (value == null || !value.isObject() || !value.fieldNames().hasNext()) {
            return null;
        }
        String format = value.fieldNames().next();
        if (!PARSES.contains(format)) {
            return null;
        }
        JsonNode options = value.get(format);
        for (String option : NOT_A_MAP) {
            if (options != null && options.isObject() && options.has(option)) {
                return null;
            }
        }
        return new Parsed("unmarshal: " + format + " turns the text into data", format);
    }

    /**
     * A Groovy setBody whose result is data again: one collection method on the body and nothing after it, such as
     * body.findAll { ... }, or body.find { it.sku == ... }, which picks an element whose fields it reads. The call, or
     * null.
     */
    static String keepsData(JsonNode value) {
        JsonNode node = value != null && value.has("expression") ? value.get("expression") : value;
        String script = node != null && node.isObject() ? expressionText(node.get("groovy")) : null;
        if (script == null) {
            return null;
        }
        String s = script.trim();
        Matcher m = COLLECTION_CALL.matcher(s);
        if (!m.lookingAt()) {
            return null;
        }
        String method = m.group(1);
        int depth = 1;
        int close = m.end();
        for (; close < s.length() && depth > 0; close++) {
            char c = s.charAt(close);
            depth += c == '{' ? 1 : c == '}' ? -1 : 0;
        }
        if (depth != 0 || close != s.length()) {
            // the closure does not end the script: something is done with its result
            return null;
        }
        String closure = s.substring(m.end(), close - 1);
        Matcher p = CLOSURE_PARAMETER.matcher(closure);
        String element = p.find() ? p.group(1) : "it";
        boolean readsFields = Pattern.compile("(?<![\\w$])" + element + "\\s*(\\??\\.\\s*[A-Za-z_]|\\[)")
                .matcher(closure).find();
        return GIVES_DATA.contains(method) || PICKS.contains(method) && readsFields
                ? "body." + method + " { ... }" : null;
    }

    /**
     * The elements a split makes of parsed data: data, when it splits the body or a field of it with simple, and its
     * steps read fields of each element before they change it, as ${body[sku]} does. Otherwise null: a List may hold
     * text as well.
     */
    private static Parsed splitElement(JsonNode value, Parsed parsed) {
        JsonNode node = value != null && value.has("expression") ? value.get("expression") : value;
        String expression = node != null && node.isObject() ? expressionText(node.get("simple")) : null;
        JsonNode steps = value != null ? value.get("steps") : null;
        if (expression == null || !THE_BODY.matcher(expression.trim()).matches() || steps == null
                || !steps.isArray()) {
            return null;
        }
        for (JsonNode step : steps) {
            if (!step.isObject() || !step.fieldNames().hasNext()) {
                return null;
            }
            String name = step.fieldNames().next();
            if (readIn(step.get(name), TEXT_PROPERTIES) != null) {
                return new Parsed(
                        parsed.origin() + ", and split " + expression.trim() + " gives its elements as data",
                        parsed.format());
            }
            if (!KEEPING_STEPS.contains(name)) {
                return null;
            }
        }
        return null;
    }

    /** What a step that needs the body as text does with it, and what to write; or null. */
    private record Need(String what, String fix) {
    }

    /** A to: that needs the body as text: one that writes a file, or an http producer that sends it with POST. */
    private static Need needsText(String name, JsonNode value, boolean post) {
        if (!"to".equals(name) && !"toD".equals(name)) {
            return null;
        }
        String uri = endpointOf(value);
        String scheme = scheme(uri);
        if (scheme == null) {
            return null;
        }
        if (WRITES_A_FILE.contains(scheme)) {
            return new Need(name + ": " + scheme + " writes the body to a file", "before this step");
        }
        if (post && ("http".equals(scheme) || "https".equals(scheme)) && sendsTheBody(uri, value)) {
            return new Need(
                    name + ": " + scheme + " sends the body as the request (a message with a body is sent as a POST)",
                    "before this step, or set httpMethod: GET when the call sends no body");
        }
        return null;
    }

    /**
     * Whether an http producer sends the body: it picks POST for a body, unless the httpMethod option says otherwise,
     * or a query string (in the uri, or a parameter that is not an option of the producer) makes it a GET.
     */
    private static boolean sendsTheBody(String uri, JsonNode value) {
        if (uri.indexOf('?') >= 0) {
            return false;
        }
        JsonNode parameters = value != null && value.isObject() ? value.get("parameters") : null;
        if (parameters == null || !parameters.isObject()) {
            return true;
        }
        for (var it = parameters.fieldNames(); it.hasNext();) {
            if (!HTTP_OPTIONS.contains(it.next())) {
                return false;
            }
        }
        JsonNode method = parameters.get("httpMethod");
        return method == null || SENDS_A_BODY.contains(method.asText().toUpperCase(Locale.ROOT));
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
        if (m.find()) {
            int end = script.indexOf(']', m.start());
            return end > 0 ? script.substring(m.start(), end + 1) : m.group();
        }
        return null;
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
                .format(new MessageFormat("{0}"))
                .arguments((routeId != null ? "route " + routeId + ": " : "") + read
                           + (text.file()
                                   ? ", but the body here is the file as it was read (a GenericFile), not parsed data - "
                                   : ", but the body here is still text, not parsed data - ")
                           + text.origin() + ": " + fix)
                .build();
    }

    private static Error error(NodePath at, String routeId, Need need, Parsed parsed) {
        return Error.builder()
                .keyword("type")
                .instanceLocation(at)
                .messageKey("type")
                .format(new MessageFormat("{0}"))
                .arguments((routeId != null ? "route " + routeId + ": " : "") + need.what()
                           + ", but the body here is parsed data (a Map or a List), not text - " + parsed.origin()
                           + ": add marshal: " + parsed.format() + " " + need.fix())
                .build();
    }
}
