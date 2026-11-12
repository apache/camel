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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.Error;

/**
 * The hints the validator adds to the schema library's errors so they say what to write: one table per stage of the
 * pipeline in {@link YamlValidator}, each row a keyword, a location pattern, a condition and the text. The first row
 * that matches an error rewrites it; an error no row matches is kept as is.
 */
final class SchemaHints {

    /**
     * What a row's condition and text see: the error and the pieces every row would otherwise recompute. root is the
     * document the location points into, or null when it is not known.
     */
    record Match(Error error, String location, String name, String message, String unknown, YamlValidator validator,
            JsonNode root) {

        static Match of(Error error, YamlValidator validator) {
            return of(error, validator, null);
        }

        static Match of(Error error, YamlValidator validator, JsonNode root) {
            String location = String.valueOf(error.getInstanceLocation());
            String message = error.getMessage();
            return new Match(
                    error, location, location.substring(location.lastIndexOf('/') + 1), message,
                    message != null && "additionalProperties".equals(error.getKeyword())
                            ? between(message, "property '", "'") : null,
                    validator, root);
        }

        /** The key before the last segment: the EIP a when/0 or doCatch/0 item belongs to. */
        String parentName() {
            String parent = location.substring(0, location.lastIndexOf('/'));
            return parent.substring(parent.lastIndexOf('/') + 1);
        }

        /** The node that holds the error's node, such as the setVariable of its expression:, or null. */
        JsonNode parentNode() {
            int slash = location.lastIndexOf('/');
            JsonNode parent = root != null && location.startsWith("/") ? root.at(location.substring(0, slash)) : null;
            return parent == null || parent.isMissingNode() ? null : parent;
        }

        boolean nameIsIndex() {
            return name.matches("\\d+");
        }

        boolean locationEndsWith(String suffix) {
            return location.endsWith(suffix);
        }

        String schemaLocation() {
            return String.valueOf(error.getSchemaLocation());
        }
    }

    /**
     * A row of a table. The error's keyword and location select the row, the condition refines it, and the text is
     * either appended to the error's message in parentheses, or replaces it.
     */
    record Hint(String keyword, Pattern location, Predicate<Match> when, Function<Match, String> text, String outKeyword,
            String messageKey, boolean append) {

        boolean matches(Match m) {
            return keyword.equals(m.error().getKeyword())
                    && (location == null || location.matcher(m.location()).matches())
                    && when.test(m);
        }

        Error rewrite(Match m) {
            String hint = text.apply(m);
            // the message is not a MessageFormat pattern (it contains braces), so pass it as the single argument
            return Error.builder()
                    .keyword(outKeyword)
                    .instanceLocation(m.error().getInstanceLocation())
                    .messageKey(messageKey)
                    .format(new MessageFormat("{0}"))
                    .arguments(append ? m.message() + " (" + hint + ")" : hint)
                    .build();
        }
    }

    /** A row that keeps the error's message and appends the hint in parentheses. */
    static Hint append(String keyword, String location, Predicate<Match> when, Function<Match, String> text) {
        return new Hint(keyword, location == null ? null : Pattern.compile(location), when, text, keyword, keyword, true);
    }

    /** A row that replaces the error's message with the hint, reported under the given keyword and message key. */
    static Hint replace(
            String keyword, String location, Predicate<Match> when, Function<Match, String> text, String outKeyword,
            String messageKey) {
        return new Hint(
                keyword, location == null ? null : Pattern.compile(location), when, text, outKeyword, messageKey,
                false);
    }

    /** An additionalProperties row: the unknown property name is what the condition and the text are about. */
    static Hint unknownProperty(String location, Predicate<Match> when, Function<Match, String> text) {
        return append("additionalProperties", location, m -> m.unknown() != null && when.test(m), text);
    }

    private static final Predicate<Match> ANY = m -> true;

    /** {sku} when the node is the map YAML reads an unquoted {sku} as (one key, no value); null otherwise. */
    static String unquotedBraces(Match m) {
        JsonNode instance = m.error().getInstanceNode();
        if (instance == null || !instance.isObject() || instance.size() != 1) {
            return null;
        }
        String key = instance.fieldNames().next();
        return instance.get(key).isNull() ? "{" + key + "}" : null;
    }

    /** A value that names a script file by its scheme, as resource: loads it. */
    private static final Pattern SCRIPT_FILE = Pattern.compile("(file|classpath|https?):.+", Pattern.DOTALL);

    /**
     * Where the text of a language goes, for the text written under another key (groovy: {script: "..."}). A script
     * file under resource: (groovy: {resource: "file:x.groovy"}) is loaded with the resource: prefix, which the value
     * often lacks (CAMEL-25748): the expression "file:x.groovy" would run as code.
     */
    static String languageTextHint(String language, String property, String value) {
        String key = property.toLowerCase(Locale.ROOT);
        String text = value.strip();
        boolean scriptFile = !text.startsWith("resource:")
                && (key.contains("resource") || (key.startsWith("script") && SCRIPT_FILE.matcher(text).matches()));
        if (scriptFile) {
            return "a " + language + " script file is loaded in expression: with the resource: prefix, write " + language
                   + ": {expression: \"resource:" + text.replace("\"", "\\\"") + "\"}";
        }
        return "the " + language + " text goes in expression:, write " + language + ": {expression: \""
               + value.replace("\"", "\\\"") + "\"}";
    }

    /**
     * json written as the language of an expression: json is the data format that writes a Map or a List as JSON. A
     * JSON text is text, set with constant, or with simple when it has ${...}; the lines to write hold the text that
     * was written under json:, when there is one.
     */
    static String jsonAsALanguageHint(Match m) {
        JsonNode instance = m.error().getInstanceNode();
        JsonNode json = instance != null ? instance.get("json") : null;
        JsonNode value = json == null || json.isValueNode() ? json : json.get("expression");
        String text = value != null && value.isValueNode() && !value.isNull() ? value.asText() : "{\"status\": \"ok\"}";
        String language = text.contains("${") ? "simple" : "constant";
        return "json is a data format, not a language (marshal: json writes a Map or a List as JSON). Set a JSON text"
               + " with " + language + (language.equals("simple") ? ", as it has ${...}" : "")
               + ", and set the header Content-Type to application/json if the caller needs it. Write json: and the"
               + " lines under it as:\n" + language + ":\n  expression: " + YamlValidator.yamlQuoted(text);
    }

    /** The keys a model writes the file under, in file: {filePath: stock.json}. */
    private static final List<String> FILE_KEYS = List.of(
            "filePath", "path", "fileName", "file", "name", "resource", "uri", "expression");

    /** The EIPs whose expression is the uri of an endpoint: there the text of a file would be the uri. */
    private static final Set<String> ENDPOINT_EIPS
            = Set.of("pollEnrich", "enrich", "recipientList", "routingSlip", "dynamicRouter");

    /**
     * Why a pollEnrich of a file has these options and a timeout: noop=true alone makes the endpoint idempotent, so a
     * second poll of the same file finds nothing, and pollEnrich waits for ever by default (timeout -1).
     */
    private static final String POLL_FILE_OPTIONS = "idempotent=false reads the file again for the next message (noop=true"
                                                    + " alone reads each file once), and the timeout ends the wait when there"
                                                    + " is no file";

    /**
     * file written as the language of an expression, to read a file: there is no such language. A data file the route
     * needs, such as a lookup table, is read with the constant language and resource:file:, as the usage of the file
     * component says. The lines to write hold the file that was written under file:, when there is one; a file name
     * with ${...} in it is read with pollEnrich, as constant does not evaluate it. In pollEnrich, enrich and the
     * routing EIPs the expression is the uri of an endpoint: the text of the file would be the uri there, and a missing
     * file would stop the route from starting.
     */
    static String fileAsALanguageHint(Match m) {
        boolean expression = m.locationEndsWith("/expression");
        String eip = expression ? m.parentName() : m.name();
        JsonNode instance = m.error().getInstanceNode();
        JsonNode file = instance != null ? instance.get("file") : null;
        String path = null;
        if (file != null && file.isValueNode() && !file.isNull()) {
            path = file.asText();
        } else if (file != null && file.isObject()) {
            for (String key : FILE_KEYS) {
                JsonNode value = file.get(key);
                if (value != null && value.isValueNode() && !value.isNull() && !value.asText().isBlank()) {
                    path = value.asText();
                    break;
                }
            }
        }
        String location = path != null ? path.strip() : "data.json";
        location = location.startsWith("resource:") ? location.substring("resource:".length()) : location;
        location = location.startsWith("file:") || location.startsWith("classpath:") ? location : "file:" + location;
        if (ENDPOINT_EIPS.contains(eip)) {
            return fileInAnEndpointHint(eip, location);
        }
        String hint = "file is not a language. To read a data file the route needs, such as a lookup table, use the"
                      + " constant language with resource:file: (the value is the text of the file).";
        if (location.startsWith("file:") && location.contains("${")) {
            JsonNode node = expression ? m.parentNode() : instance;
            String name = node != null && node.path("name").isTextual() ? node.path("name").asText() : null;
            String poll = "pollEnrich: {expression: {simple: {expression: \"" + pollUri(location) + "\"}}, timeout: 5000";
            String read;
            if ("setBody".equals(eip) || "transform".equals(eip)) {
                // the pollEnrich sets the body, as setBody does
                read = "Write this " + eip + " as: " + poll + "}. " + POLL_FILE_OPTIONS + " (the body is then null).";
            } else {
                // variableReceive keeps the body, as setVariable does
                read = ("setVariable".equals(eip) && name != null
                        ? "Write this setVariable as: " + poll + ", variableReceive: " + name + "}."
                        : "Read the file in a step before this one: " + poll + ", variableReceive: data}, and use"
                          + " ${variable.data} here.")
                       + " variableReceive keeps the body as it is, " + POLL_FILE_OPTIONS + " (the variable is then null).";
            }
            return hint + " A file name with ${...} is read with pollEnrich instead, as constant does not evaluate it. "
                   + read;
        }
        String constant = "constant:\n  expression: " + YamlValidator.yamlQuoted("resource:" + location);
        return hint + " Write file: and the lines under it as:\n"
               + (expression ? constant : "expression:\n" + constant.indent(2).stripTrailing());
    }

    /**
     * file as the language of the expression of pollEnrich, enrich or a routing EIP, where the expression is the uri of
     * an endpoint: pollEnrich reads a file with a file: uri, enrich and the routing EIPs send to the uri.
     */
    private static String fileInAnEndpointHint(String eip, String location) {
        String hint = "file is not a language. ";
        boolean file = location.startsWith("file:");
        String language = location.contains("${") ? "simple" : "constant";
        return switch (eip) {
            case "pollEnrich" -> hint + "The expression of pollEnrich is the uri of the endpoint to poll, such as a file:"
                                 + " uri with fileName."
                                 + (file
                                         ? " Set timeout: 5000 on the pollEnrich too: " + POLL_FILE_OPTIONS + ". Write"
                                           + " file: and the lines under it as:\n" + language + ":\n  expression: "
                                           + YamlValidator.yamlQuoted(pollUri(location))
                                         : "");
            case "enrich" -> hint + "The expression of enrich is the uri of the endpoint to call, and a file: uri there"
                             + " writes the message to a file."
                             + (file
                                     ? " To read a file, use pollEnrich in place of enrich: pollEnrich: {expression: {"
                                       + language + ": {expression: \"" + pollUri(location) + "\"}}, timeout: 5000}. "
                                       + POLL_FILE_OPTIONS + "."
                                     : "");
            case "dynamicRouter" -> hint + "The expression of dynamicRouter gives the uri of the next endpoint each time"
                                    + " it is called, and null when the routing is done: a file: uri there writes the"
                                    + " message to a file, and does not read one.";
            default -> hint + "The expression of " + eip + " gives the uris of the endpoints to send to, as text"
                       + " (constant, or simple when it has ${...}): a file: uri there writes the message to a file, and"
                       + " does not read one.";
        };
    }

    /**
     * The file: uri that a pollEnrich reads the file with each time, such as file:orders?fileName=${header.id}.json.
     */
    private static String pollUri(String location) {
        String name = location.substring("file:".length());
        int slash = name.lastIndexOf('/');
        String dir = slash > 0 ? name.substring(0, slash) : slash == 0 ? "/" : ".";
        return "file:" + dir + "?fileName=" + name.substring(slash + 1) + "&noop=true&idempotent=false";
    }

    /** The text of a scalar property of the error's node, or the fallback when it is not a scalar. */
    private static String valueOf(Match m, String property, String fallback) {
        JsonNode instance = m.error().getInstanceNode();
        JsonNode value = instance != null ? instance.get(property) : null;
        return value != null && value.isValueNode() && !value.isNull() ? value.asText() : fallback;
    }

    /**
     * The language of the error's node that is written in its short form (jsonpath: "$[...]") next to the unknown
     * property, when that property is one of the language's own options (resultType), or null.
     */
    private static String shortFormLanguage(Match m) {
        JsonNode instance = m.error().getInstanceNode();
        if (instance == null || !instance.isObject() || instance.has("expression")) {
            return null;
        }
        for (String language : m.validator().languageKeys()) {
            if (instance.path(language).isTextual() && m.validator().languageOptions(language).contains(m.unknown())) {
                return language;
            }
        }
        return null;
    }

    /** The EIP names a model writes for the exchange properties, and the ones Camel has. */
    private static final Map<String, String> EXCHANGE_PROPERTY_EIPS = Map.of(
            "setExchangeProperty", "setProperty", "setExchangeProperties", "setProperties",
            "removeExchangeProperty", "removeProperty", "removeExchangeProperties", "removeProperties",
            "setExchangeVariable", "setVariable", "setExchangeVariables", "setVariables");

    private static final Set<String> ROUTE_ERROR_HANDLER_KINDS
            = Set.of("noErrorHandler", "deadLetterChannel", "defaultErrorHandler", "springTransactionErrorHandler",
                    "jtaTransactionErrorHandler", "refErrorHandler");

    /** A handler longer than this is shown with its kind only. */
    private static final int MAX_HANDLER_TEXT = 200;

    /**
     * The places of an errorHandler: written under from:, with the handler as written: under the route, at the level of
     * from: (that route only), or a top-level item (every route). A - from: item has no place for it, so the route is
     * written under - route:.
     */
    static String errorHandlerUnderFrom(Match m) {
        JsonNode from = m.error().getInstanceNode();
        JsonNode written = from != null ? from.get("errorHandler") : null;
        String handler = errorHandlerText(written, m.validator());
        String example = "route: {errorHandler: " + handler + ", from: {uri: ..., steps: [...]}}";
        // the handler as written, copied as the text to write, had no kind (r28 of the local-model benchmark,
        // connect-service-jms-request-reply step 4): the model wrote it so, and was refused for it next
        String kind = written != null && written.isObject() && !handler.equals(errorHandlerText(written))
                ? ", and it takes the kind of handler as its key, with the options under the kind" : "";
        return "errorHandler: is a key of the route, not of from:" + kind + ". For this route only, "
               + (m.location().endsWith("/route/from")
                       ? "write it under the route at the same level as from:, as in " + example
                       : "write the route as - route: with errorHandler: at the same level as from:, as in - " + example)
               + ". For every route, write it as a top-level item instead: - errorHandler: " + handler;
    }

    /** An error handler as one line to copy, {noErrorHandler: {}}; its kind only when it is long. */
    static String errorHandlerText(JsonNode handler) {
        return errorHandlerText(handler, null);
    }

    /**
     * As {@link #errorHandlerText(JsonNode)}; with the validator, a handler whose options are not under its kind gets
     * the form the schema takes: {redeliveryPolicy: {...}} is {defaultErrorHandler: {redeliveryPolicy: {...}}}, with a
     * deadLetterUri the kind is deadLetterChannel, and {deadLetterChannel: "jms:queue:dead", redeliveryPolicy: {...}}
     * is {deadLetterChannel: {deadLetterUri: "jms:queue:dead", redeliveryPolicy: {...}}}. The kind alone, {kind:
     * {...}}, when the options are not all options of the kind.
     */
    static String errorHandlerText(JsonNode handler, YamlValidator validator) {
        if (handler == null || !handler.isObject() || handler.isEmpty()) {
            return handler != null && handler.isTextual() && ROUTE_ERROR_HANDLER_KINDS.contains(handler.asText())
                    ? "{" + handler.asText() + ": {}}" : "{noErrorHandler: {}}";
        }
        List<String> kinds = new ArrayList<>();
        handler.fieldNames().forEachRemaining(k -> {
            if (ROUTE_ERROR_HANDLER_KINDS.contains(k)) {
                kinds.add(k);
            }
        });
        boolean asTheSchemaTakesIt = kinds.size() == 1 && handler.size() == 1 && handler.get(kinds.get(0)).isObject();
        if (validator != null && kinds.size() <= 1 && !asTheSchemaTakesIt && !kinds.contains("refErrorHandler")) {
            String kind = !kinds.isEmpty() ? kinds.get(0)
                    : handler.has("deadLetterUri") ? "deadLetterChannel" : "defaultErrorHandler";
            JsonNode value = handler.get(kind);
            ObjectNode options = value != null && value.isObject()
                    ? ((ObjectNode) value).deepCopy() : JsonNodeFactory.instance.objectNode();
            if (value != null && value.isTextual() && "deadLetterChannel".equals(kind)) {
                // the kind with its uri as the value
                options.set("deadLetterUri", value);
            }
            handler.properties().forEach(e -> {
                if (!kind.equals(e.getKey())) {
                    options.set(e.getKey(), e.getValue());
                }
            });
            List<String> keys = new ArrayList<>();
            options.fieldNames().forEachRemaining(keys::add);
            if (!validator.errorHandlerOptions(kind).containsAll(keys)) {
                return "{" + kind + ": {...}}";
            }
            ObjectNode wrapped = JsonNodeFactory.instance.objectNode();
            wrapped.set(kind, options);
            handler = wrapped;
        }
        String kind = handler.fieldNames().next();
        if (handler.size() == 1 && ROUTE_ERROR_HANDLER_KINDS.contains(kind) && !"refErrorHandler".equals(kind)
                && !handler.get(kind).isObject()) {
            // noErrorHandler: true is not its form: the kind takes a map, {} with no options
            return "{" + kind + ": {}}";
        }
        String text = flow(handler);
        return text.length() <= MAX_HANDLER_TEXT ? text : "{" + kind + ": {...}}";
    }

    /** A YAML scalar that needs no quotes in flow style. */
    private static final Pattern PLAIN_SCALAR = Pattern.compile("[A-Za-z0-9_./-]+");

    /**
     * A YAML node in flow style, on one line: {deadLetterChannel: {deadLetterUri: "direct:parked"}}. A key with no
     * value is written {}, as the error handlers that take no options are.
     */
    static String flow(JsonNode node) {
        if (node == null || node.isNull()) {
            return "{}";
        }
        if (node.isObject()) {
            List<String> entries = new ArrayList<>();
            for (var it = node.fieldNames(); it.hasNext();) {
                String key = it.next();
                entries.add(key + ": " + flow(node.get(key)));
            }
            return "{" + String.join(", ", entries) + "}";
        }
        if (node.isArray()) {
            List<String> items = new ArrayList<>();
            node.forEach(item -> items.add(flow(item)));
            return "[" + String.join(", ", items) + "]";
        }
        String text = node.asText();
        return node.isTextual() && !PLAIN_SCALAR.matcher(text).matches() ? YamlValidator.yamlQuoted(text) : text;
    }

    /**
     * Applies a table to the errors: the first matching row rewrites each error. Two rewrites that say the same at the
     * same location (the branches of an anyOf, once the hint no longer names the branch) are reported once.
     */
    static List<Error> apply(List<Hint> table, List<Error> errors, YamlValidator validator) {
        return apply(table, errors, validator, null);
    }

    /** As {@link #apply(List, List, YamlValidator)}, for rows that read the nodes around the error's node in root. */
    static List<Error> apply(List<Hint> table, List<Error> errors, YamlValidator validator, JsonNode root) {
        List<Error> answer = new ArrayList<>(errors.size());
        Set<String> seen = new LinkedHashSet<>();
        for (Error error : errors) {
            Error hinted = apply(table, error, validator, root);
            if (hinted == error || seen.add(hinted.getInstanceLocation() + " " + hinted.getMessage())) {
                answer.add(hinted);
            }
        }
        return answer;
    }

    static Error apply(List<Hint> table, Error error, YamlValidator validator) {
        return apply(table, error, validator, null);
    }

    static Error apply(List<Hint> table, Error error, YamlValidator validator, JsonNode root) {
        if (error.getMessage() == null) {
            return error;
        }
        Match m = Match.of(error, validator, root);
        for (Hint row : table) {
            if (row.matches(m)) {
                return row.rewrite(m);
            }
        }
        return error;
    }

    // -------------------------------------------------------------------------------------------------------------
    // step hints
    // -------------------------------------------------------------------------------------------------------------

    /**
     * "must have at most 1 properties" at a step: a step holds one EIP, and the second key is either an option that
     * belongs under the EIP (indented one level more) or another step (its own - item).
     */
    static final List<Hint> STEP = List.of(
            // - beans:\n  myBean: ... : the second key was meant to be inside the first; it is not indented enough
            append("maxProperties", "/\\d+", ANY,
                    m -> "a top-level entry is one key: - route:, - beans:, - rest:...; the lines that belong to it"
                         + " must be indented under it, a second key at the same level as the entry is read as a"
                         + " separate property"),
            append("maxProperties", ".*/steps/\\d+", ANY, SchemaHints::stepWithTwoKeys));

    /**
     * The keys of a step after its EIP, each with where it goes: an option of the EIP is indented under it, another EIP
     * is the next - item (CAMEL-25372). Without the keys, the generic sentence.
     */
    static String stepWithTwoKeys(Match m) {
        String generic = "a step is one EIP: an option of that EIP is indented under its key, and the next EIP is its"
                         + " own - item";
        var node = m.error().getInstanceNode();
        if (node == null || !node.isObject() || node.size() < 2) {
            return generic;
        }
        List<String> keys = new ArrayList<>();
        node.fieldNames().forEachRemaining(keys::add);
        Set<String> steps = m.validator().stepNames();
        String eip = keys.stream().filter(steps::contains).findFirst().orElse(null);
        if (eip == null) {
            return generic;
        }
        Set<String> options = m.validator().optionsOf(eip);
        // the key the option lines up with once it is under the EIP: "the column of - split:" read as the column of
        // the dash, and two spaces more than that is where the model had put it already (it wrote the same five times)
        JsonNode value = node.get(eip);
        String sibling = value != null && value.isObject() && value.size() > 0 ? value.fieldNames().next() : null;
        List<String> parts = new ArrayList<>();
        for (String k : keys) {
            if (k.equals(eip)) {
                continue;
            }
            // an option first: doCatch: is an option of doTry and an EIP too
            boolean option = isOptionOf(eip, k, node.get(k), options, m.validator());
            if (option && sibling != null) {
                parts.add(k + ": lines up with " + eip + ":, so it is read as a second key of the - item; it is an"
                          + " option of " + eip + ": indent " + k + ": and the lines under it two spaces more, so " + k
                          + ": lines up with " + sibling + ":");
            } else if (option) {
                parts.add(k + ": lines up with " + eip + ":, so it is read as a second key of the - item; it is an"
                          + " option of " + eip + ": write the options of " + eip + ": on the lines under it, two"
                          + " spaces further right than " + eip + ":, with " + k + ": among them");
            } else if (steps.contains(k)) {
                parts.add(k + ": is another EIP: start it as its own item, - " + k + ":");
            } else {
                // neither: say what a step is, without guessing where the key belongs
                return generic;
            }
        }
        return parts.isEmpty() ? generic : String.join("; ", parts);
    }

    /**
     * Whether a second key of a step is an option of the step's EIP, which goes under the EIP. Three options have the
     * name of an EIP too: doCatch: and doFinally: of doTry, and filter: of claimCheck. Such a key is the option: in r32
     * of the local-model benchmark, told that doCatch: at the column of doTry: is another EIP to start as its own -
     * item, a model sent the same lines 19 times (CAMEL-26874). The exception is an option that takes a plain value, as
     * filter: takes a text: with a mapping or a list, the key is the EIP.
     */
    private static boolean isOptionOf(String eip, String k, JsonNode value, Set<String> options, YamlValidator validator) {
        if (!options.contains(k)) {
            return false;
        }
        if (!validator.stepNames().contains(k) || value == null || !value.isContainerNode()) {
            return true;
        }
        String type = validator.optionType(eip, k);
        return type == null || "array".equals(type) || "object".equals(type);
    }

    /**
     * The options of the step's EIP that are written as second keys of the step (at the column of the EIP), when the
     * step has an EIP; empty otherwise.
     */
    static Set<String> misplacedOptions(JsonNode node, YamlValidator validator) {
        if (node == null || !node.isObject() || node.size() < 2) {
            return Set.of();
        }
        Set<String> steps = validator.stepNames();
        List<String> keys = new ArrayList<>();
        node.fieldNames().forEachRemaining(keys::add);
        String eip = keys.stream().filter(steps::contains).findFirst().orElse(null);
        if (eip == null) {
            return Set.of();
        }
        Set<String> options = validator.optionsOf(eip);
        Set<String> answer = new LinkedHashSet<>();
        for (String k : keys) {
            if (!k.equals(eip) && isOptionOf(eip, k, node.get(k), options, validator)) {
                answer.add(k);
            }
        }
        return answer;
    }

    /**
     * An option of a step's EIP at the column of the EIP fails twice: "at most 1 properties" (the hint above says where
     * it goes) and "property 'steps' is not defined" in the oneOf branch of the step, whose hint says to move the items
     * up a level, the opposite. The second one is dropped. An option with the name of an EIP fails as that EIP instead,
     * at its own line: doCatch: with its list of catches gets "array found, object expected", as a - doCatch: step is a
     * mapping. When the value has the shape of the option, that error is dropped too (CAMEL-26874).
     */
    static List<Error> dropMisplacedOptionRepeats(List<Error> errors, YamlValidator validator) {
        Set<String> repeats = new HashSet<>();
        Set<String> readAsEip = new HashSet<>();
        for (Error e : errors) {
            if ("maxProperties".equals(e.getKeyword())) {
                String at = String.valueOf(e.getInstanceLocation());
                if (at.matches(".*/steps/\\d+")) {
                    JsonNode node = e.getInstanceNode();
                    for (String k : misplacedOptions(node, validator)) {
                        repeats.add(at + " " + k);
                        if (validator.stepNames().contains(k) && hasShapeOfOption(node, k, validator)) {
                            readAsEip.add(at + "/" + k);
                        }
                    }
                }
            }
        }
        if (repeats.isEmpty()) {
            return errors;
        }
        List<Error> answer = new ArrayList<>(errors);
        answer.removeIf(e -> "additionalProperties".equals(e.getKeyword()) && e.getMessage() != null
                && repeats.contains(e.getInstanceLocation() + " " + between(e.getMessage(), "property '", "'")));
        answer.removeIf(e -> "type".equals(e.getKeyword())
                && readAsEip.contains(String.valueOf(e.getInstanceLocation())));
        return answer;
    }

    /**
     * Whether the value of the key k of a step has the shape of the option k of the step's EIP: a list for an array, a
     * mapping for an object, a plain value for the others. A doFinally: with a list is wrong as an option too, so its
     * error stays.
     */
    private static boolean hasShapeOfOption(JsonNode node, String k, YamlValidator validator) {
        Set<String> steps = validator.stepNames();
        List<String> keys = new ArrayList<>();
        node.fieldNames().forEachRemaining(keys::add);
        String eip = keys.stream().filter(steps::contains).findFirst().orElse(null);
        String type = eip != null ? validator.optionType(eip, k) : null;
        JsonNode value = node.get(k);
        if (type == null || value == null) {
            return false;
        }
        return switch (type) {
            case "array" -> value.isArray();
            case "object" -> value.isObject();
            default -> value.isValueNode();
        };
    }

    // -------------------------------------------------------------------------------------------------------------
    // expression hints
    // -------------------------------------------------------------------------------------------------------------

    private static final String EXPRESSION_SUB_ELEMENT = "ExpressionSubElementDefinition";

    /**
     * "string found, object expected" where the schema wants an expression: says to write it as a language map,
     * constant for a fixed value and simple for a dynamic one.
     */
    static final List<Hint> EXPRESSION = List.of(
            replace("type", null,
                    m -> {
                        JsonNode instance = m.error().getInstanceNode();
                        return instance != null && instance.isValueNode()
                                && (m.schemaLocation().contains(EXPRESSION_SUB_ELEMENT)
                                        || String.valueOf(m.error().getEvaluationPath()).contains(EXPRESSION_SUB_ELEMENT));
                    },
                    m -> {
                        String value = m.error().getInstanceNode().asText();
                        return String.format(
                                "a plain value (%s) found, an expression expected: write %s: {constant: {expression: \"%s\"}} for a fixed value, or %s: {simple: {expression: \"...\"}} for a dynamic one",
                                value, m.name(), value, m.name());
                    },
                    "type", "expression"),
            // split: {expression: "${body}"}, setHeader: {expression: constant 404}: the expression: key of an EIP
            // holds a language map, not the text
            replace("type", ".*/expression",
                    m -> {
                        JsonNode instance = m.error().getInstanceNode();
                        return instance != null && instance.isValueNode() && m.message().contains("object expected");
                    },
                    m -> expressionKeyHint(m.error().getInstanceNode().asText()),
                    "type", "expression"));

    /** The languages a model writes in front of the text, as in expression: constant 404. */
    private static final Pattern LANGUAGE_PREFIX = Pattern.compile(
            "(constant|simple|header|exchangeProperty|variable|jq|jsonpath|xpath|groovy|tokenize|method|ref)\\s+(.+)",
            Pattern.DOTALL);

    static String expressionKeyHint(String value) {
        String language = value.contains("${") ? "simple" : "constant";
        String text = value;
        Matcher prefix = LANGUAGE_PREFIX.matcher(value.trim());
        if (prefix.matches()) {
            language = prefix.group(1);
            text = prefix.group(2).trim();
        }
        return String.format(
                "a plain value (%s) found, a language expected: expression: holds the language as its key, write"
                             + " expression: {%s: {expression: \"%s\"}}",
                value, language, text.replace("\"", "\\\""));
    }

    // -------------------------------------------------------------------------------------------------------------
    // list hints
    // -------------------------------------------------------------------------------------------------------------

    /**
     * "object found, array expected" says what the schema wants, not how to write it: a list, each item starting with
     * "- ". At the root of the file it also names the entries (route, from, beans, rest, onException).
     */
    static final List<Hint> LIST = List.of(
            // script: {language: groovy, text: ...}: the language is the key of the expression, not a property
            append("type", ".*/language", m -> m.message().contains("object expected"),
                    m -> "an expression is written with the language as the key and its expression: property, e.g."
                         + " groovy: {expression: \"...\"}, simple: {expression: \"...\"}, constant: {expression: \"...\"};"
                         + " the language: form is language: {language: groovy, expression: \"...\"}"),
            // path: {sku}: braces without quotes are a YAML map ({sku: null}), not the text {sku}
            append("type", null, m -> m.message().contains("object found, string expected") && unquotedBraces(m) != null,
                    m -> {
                        String braces = unquotedBraces(m);
                        return braces + " without quotes is a YAML map (braces open a map in YAML), not text: quote it, "
                               + m.name() + ": \"" + braces + "\"";
                    }),
            // message: {simple: "..."}: a string property that is already an expression, or a plain option
            append("type", null, m -> m.message().contains("object found, string expected"),
                    m -> m.name() + " is a plain string"
                         + (m.locationEndsWith("/log/message")
                                 ? " that is already a simple expression: write message: \"... ${body} ...\""
                                 : ": write " + m.name() + ": \"...\", not a language map")),
            // - onException: [ ... ]: the entry is a map; several handlers are several - onException: items
            append("type",
                    "/\\d+/(onException|onCompletion|intercept|interceptFrom|interceptSendToEndpoint|errorHandler|route|rest|restConfiguration)",
                    m -> m.message().contains("array found, object expected"),
                    m -> m.name() + " is a map, not a list: - " + m.name() + ": followed by its properties indented"
                         + (m.name().equals("onException")
                                 ? " (exception: [java.lang.Exception], handled: {constant: {expression: \"true\"}},"
                                   + " steps: [...])"
                                 : "")
                         + "; several of them are several - " + m.name() + ": items"),
            // otherwise: [- simple: ..., steps: [...], - otherwise: ...]: a second condition and its own otherwise
            // written under otherwise:, whose steps: they are not (r27 connect-http-client step 3: told that the write
            // repairs the shape by itself, a model sent the same lines 16 times)
            append("type", ".*/otherwise", m -> m.message().contains("object expected") && heldCondition(m) != null,
                    SchemaHints::conditionsUnderOtherwise),
            // otherwise: [- log: ...]: the block is a map whose steps: holds the list
            append("type", ".*/(otherwise|doTry|doFinally|doCatch/\\d+)", m -> m.message().contains("object expected"),
                    m -> {
                        String eip = m.nameIsIndex() ? "doCatch" : m.name();
                        return eip + " holds its EIPs under steps: " + eip + ": {steps: [- log: \"...\"]}"
                               + (eip.equals("doCatch") ? ", each - doCatch: item with exception: and steps:" : "");
                    }),
            // setBody: {constant: null} to clear the body before a GET: constant is a text (CAMEL-24888)
            append("type", ".*/constant(/expression)?", m -> m.message().contains("null found"),
                    m -> "constant is a text; to set an empty body (a GET sends none) write setBody: {simple:"
                         + " {expression: \"${null}\"}}"),
            // library: jackson: the enumeration is case sensitive; the name to write is the entry of this data
            // format's enumeration (json, avro, protobuf, yaml each have their own) that matches ignoring case
            append("enum", ".*/library", ANY,
                    m -> {
                        JsonNode instance = m.error().getInstanceNode();
                        String written = instance != null && instance.isValueNode() ? instance.asText() : "";
                        String list = between(m.message(), "[", "]");
                        String match = null;
                        for (String entry : (list == null ? "" : list).split(",")) {
                            String name = entry.trim().replace("\"", "");
                            if (!name.isEmpty() && name.equalsIgnoreCase(written)) {
                                match = name;
                            }
                        }
                        return "the library name is case sensitive"
                               + (match != null ? ": write library: " + match : ", write it as listed");
                    }),
            // a comma-separated list of enum values, such as the jsonpath option (CAMEL-25739): name the values, not the
            // pattern that checks them
            append("pattern", null, m -> m.message().contains("^\\s*("),
                    m -> {
                        String values = between(m.message(), "^\\s*(", ")");
                        return "write one or more of " + (values == null ? "the listed values" : values.replace("|", ", "))
                               + ", separated by comma";
                    }),
            // - stop as a step: a step is a key with its options as a map, {} when it has none to set
            replace("type", ".*/steps/\\d+",
                    m -> m.message().contains("string found, object expected") && m.error().getInstanceNode() != null
                            && m.error().getInstanceNode().isTextual(),
                    m -> {
                        String text = m.error().getInstanceNode().asText().trim();
                        if (!m.validator().stepNames().contains(text)) {
                            return "a step is a key followed by its options, such as - log: {message: \"...\"} or"
                                   + " - stop: {}; " + text + " is not a step";
                        }
                        String property = SchemaHints.STRING_STEP_PROPERTY.get(text);
                        return "a step is a key followed by its options: write - " + text + ": "
                               + (property != null
                                       ? "{" + property + ": \"...\"}"
                                       : "{} (the options of the step, if any, go inside the braces)");
                    },
                    "type", "type"),
            append("type", "/?", m -> m.message().contains("array expected"),
                    m -> "a Camel YAML file is a list of entries, each starting with \"- \": - route:, - from:, - beans:,"
                         + " - rest:, - onException:"),
            append("type", ".*/beans", m -> m.message().contains("array expected"),
                    m -> "beans is a list: - name: myBean followed by type: \"#class:com.example.MyBean\" (indented"
                         + " under the -)"),
            append("type", ".*/(steps|when|get|post|exception|doCatch)", m -> m.message().contains("array expected"),
                    m -> m.name() + " is a list: each item starts with \"- \""),
            append("type", null, m -> m.message().contains("array expected"),
                    m -> "write it as a list: each item starts with \"- \""));

    /**
     * The key of the condition of the first item of an otherwise: list that is a condition with steps, as a when: item
     * is (simple, expression...), or otherwise for an - otherwise: item; null when no item is one: the items are steps.
     */
    static String heldCondition(Match m) {
        JsonNode instance = m.error().getInstanceNode();
        if (instance == null || !instance.isArray()) {
            return null;
        }
        for (JsonNode item : instance) {
            if (!item.isObject()) {
                continue;
            }
            if (item.has("otherwise") || item.has("when")) {
                return "otherwise";
            }
            if (item.has("steps")) {
                for (String key : (Iterable<String>) item::fieldNames) {
                    if ("expression".equals(key) || m.validator().languageKeys().contains(key)) {
                        return key;
                    }
                }
            }
        }
        return null;
    }

    /** What to write for an otherwise: list that holds a second condition, or an - otherwise: item of its own. */
    static String conditionsUnderOtherwise(Match m) {
        String key = heldCondition(m);
        String condition = "otherwise".equals(key) ? "simple" : key;
        String when = "expression".equals(condition)
                ? "{expression: {simple: {expression: \"...\"}}, steps: [...]}"
                : "{" + condition + ": \"...\", steps: [...]}";
        return "otherwise holds only steps:, the steps for a message that no when matches. A second condition is another"
               + " item of when:, with its " + condition + ": and its steps:, and the steps of an - otherwise: item go"
               + " under otherwise: steps:, as in choice: {when: [" + when + ", " + when + "], otherwise: {steps: [...]}}";
    }

    // -------------------------------------------------------------------------------------------------------------
    // property hints
    // -------------------------------------------------------------------------------------------------------------

    /** The options of the log component: written on the log EIP, they are reported as such. */
    private static final Set<String> LOG_COMPONENT_OPTIONS = Set.of(
            "showAll", "showBody", "showBodyType", "showHeaders", "showExchangePattern", "showProperties",
            "showAllProperties", "showVariables", "showExchangeId", "showException", "showCaughtException",
            "showStackTrace", "showStreams", "showFiles", "showFuture", "showRouteId", "showRouteGroup", "multiline",
            "maxChars", "skipBodyLineSeparator", "groupSize", "groupInterval", "groupDelay", "groupActiveOnly",
            "level", "plain", "sourceLocationLoggerName", "style");

    private static final Pattern CLASS_NAME = Pattern.compile("([a-z][\\w]*\\.)+[A-Z]\\w*");

    /**
     * Adds a hint to "property 'x' is not defined in the schema": the closest property name of that node (did you mean
     * 'logName'?), or, when the property is a top-level entry such as onException written inside a route, where it goes
     * instead.
     */
    static final List<Hint> PROPERTY = List.of(
            append("required", ".*/route/from", m -> m.message().contains("required property 'steps' not found"),
                    m -> "steps: is a property of from:, next to uri:; a steps: written at the route level must be"
                         + " indented under from:"),
            // - id: myBean / class: ... : the bean properties are name and type
            unknownProperty(".*/beans/\\d+", m -> Set.of("id", "ref", "class").contains(m.unknown()),
                    m -> "a bean is - name: myBean followed by type: \"#class:com.example.MyBean\" (name instead of "
                         + m.unknown() + (m.unknown().equals("class") ? ", type instead of class" : "") + ")"),
            // - myBean: {type: ...} instead of - name: myBean / type: ...
            unknownProperty(".*/beans/\\d+", ANY,
                    m -> "a bean item is written as - name: " + m.unknown()
                         + " followed by type: \"#class:com.example.MyBean\" (the name is a property, not the key)"),
            // errorHandler: under from: belongs to the route; told only that it is a top-level entry, models moved a
            // noErrorHandler meant for one route to the top level, where it applies to every route (r21 and r25 of the
            // local-model benchmark)
            unknownProperty("/\\d+/(route/)?from", m -> m.unknown().equals("errorHandler"),
                    SchemaHints::errorHandlerUnderFrom),
            unknownProperty(null,
                    m -> m.validator().topLevelEntries().contains(m.unknown())
                            && m.location().chars().filter(c -> c == '/').count() >= 2,
                    m -> "'" + m.unknown() + "' is a top-level entry: write it as a list item at the same level as the"
                         + " route, not inside it"),
            // route: {noErrorHandler: true} or errorHandlerType: none: the route-level error handler is errorHandler: with
            // the kind as its key (CAMEL-24881)
            unknownProperty(".*/(route|from)", m -> ROUTE_ERROR_HANDLER_KINDS.contains(m.unknown())
                    || m.unknown().equals("errorHandlerType") || m.unknown().equals("errorHandlerRef"),
                    m -> "a route-level error handler is written under the route as errorHandler: with the kind as its"
                         + " key: errorHandler: {noErrorHandler: {}}, errorHandler: {deadLetterChannel: {deadLetterUri:"
                         + " \"direct:parked\"}}, errorHandler: {defaultErrorHandler: {redeliveryPolicy: {...}}}"
                         + " (a top-level - errorHandler: item applies to every route)"),
            // - noErrorHandler: {} as a step of the route: the handler goes on the route, not among its steps (CAMEL-25328)
            unknownProperty(".*/steps/\\d+", m -> ROUTE_ERROR_HANDLER_KINDS.contains(m.unknown()),
                    m -> "an error handler is not a step: write it on the route, next to from:, as errorHandler: {"
                         + m.unknown() + ": " + (m.unknown().equals("noErrorHandler") ? "{}" : "{...}")
                         + "} (a top-level - errorHandler: item applies to every route)"),
            unknownProperty(".*/errorHandler", m -> m.unknown().equals("type") || m.unknown().equals("errorHandlerType"),
                    m -> "errorHandler: has the kind of handler as its key, not a " + m.unknown() + " property:"
                         + " errorHandler: {noErrorHandler: {}}, {deadLetterChannel: {deadLetterUri: \"...\"}} or"
                         + " {defaultErrorHandler: {...}}"),
            // errorHandler: {redeliveryPolicy: ...}: an option of a kind of handler written without the kind (r28 of the
            // local-model benchmark, connect-service-jms-request-reply step 4)
            unknownProperty(".*/errorHandler", m -> m.validator().errorHandlerOptions("defaultErrorHandler")
                    .contains(m.unknown()) || m.validator().errorHandlerOptions("deadLetterChannel").contains(m.unknown()),
                    m -> "errorHandler takes the kind of handler as its key, and " + m.unknown() + " is an option of the"
                         + " kind: write errorHandler: " + errorHandlerText(m.error().getInstanceNode(), m.validator())),
            append("type", ".*/errorHandler/noErrorHandler", m -> m.message().contains("object expected"),
                    m -> "noErrorHandler takes no options: write noErrorHandler: {}"),
            // onException: {java.lang.Exception: ...}: the class is a list item under exception:
            unknownProperty(".*/(onException|doCatch/\\d+)", m -> CLASS_NAME.matcher(m.unknown()).matches(),
                    m -> "the exception class is a list item under exception: ("
                         + (m.locationEndsWith("/onException") ? "onException" : "doCatch") + ": {exception: ["
                         + m.unknown() + "], steps: [...]})"),
            unknownProperty(".*/circuitBreaker", m -> m.unknown().equals("name"),
                    m -> "the circuit breaker's name is its id: circuitBreaker: {id: myBreaker, ...}"),
            // circuitBreaker: {failureThreshold: 5}: the thresholds and timeouts are resilience4j configuration
            unknownProperty(".*/circuitBreaker", m -> {
                Set<String> resilience = m.validator().resilienceProperties();
                String lower = m.unknown().toLowerCase(Locale.ROOT);
                return !resilience.isEmpty()
                        && (resilience.contains(m.unknown()) || YamlValidator.closest(m.unknown(), resilience) != null
                                || lower.contains("threshold") || lower.contains("timeout"));
            }, m -> {
                Set<String> resilience = m.validator().resilienceProperties();
                String best = resilience.contains(m.unknown()) ? m.unknown() : YamlValidator.closest(m.unknown(), resilience);
                return "the thresholds, timeouts and the like are written under resilience4jConfiguration:"
                       + " (circuitBreaker: {resilience4jConfiguration: {" + (best != null ? best : "failureRateThreshold")
                       + ": ...}, steps: [...], onFallback: {steps: [...]}})";
            }),
            unknownProperty(".*/log", m -> m.unknown().equals("level") || m.unknown().equals("logLevel"),
                    m -> "did you mean 'loggingLevel'?"),
            // log: {message: ..., showHeaders: true}: those are options of the log component endpoint
            unknownProperty(".*/log", m -> LOG_COMPONENT_OPTIONS.contains(m.unknown()),
                    m -> "'" + m.unknown() + "' is an option of the log component, not of the log EIP: write a to: step"
                         + " with uri: \"log:com.example?" + m.unknown() + "=...\" (the log EIP has message,"
                         + " loggingLevel, logName, marker)"),
            // - route: {from: {uri: ...}, steps: [...]}: steps belongs under from:
            unknownProperty(".*/route", m -> m.unknown().equals("steps"),
                    m -> "steps: goes under from:, indented at the same level as uri: (route: {from: {uri: ...,"
                         + " steps: [...]}})"),
            // otherwise: {log: ...} or when: [- simple: ..., log: ...]: the EIPs go under steps:
            unknownProperty(".*/(otherwise|when/\\d+|doTry|doCatch/\\d+|doFinally|split|filter|loop|aggregate"
                            + "|circuitBreaker|onFallback|multicast|pipeline|saga|resequence|throttle|delay"
                            + "|onException|onCompletion|intercept|interceptFrom|interceptSendToEndpoint|route|from)",
                    m -> m.validator().stepNames().contains(m.unknown()),
                    m -> {
                        String eip = m.nameIsIndex() ? m.parentName() : m.name();
                        return "'" + m.unknown() + "' is a step: the steps of " + eip + " go under steps: (" + eip
                               + ": {steps: [- " + m.unknown() + ": ...]})";
                    }),
            // setBody: {script: ...}: script is an EIP; the language is the key of an expression
            unknownProperty(null,
                    m -> m.unknown().equals("script") && !m.locationEndsWith("/steps")
                            && (YamlValidator.EXPRESSION_REQUIRED.contains(m.name()) || m.locationEndsWith("/expression")),
                    m -> "script is an EIP step, not a language: write the language as the key of the expression"
                         + " (expression: {groovy: {expression: \"...\"}}, expression: {simple: {expression: \"...\"}}),"
                         + " or run a script as its own step with - script: {expression: {groovy: {expression: \"...\"}}}"),
            // setBody: {bean: myBean} : the bean language is method:
            unknownProperty(null, m -> m.unknown().equals("bean") && !m.locationEndsWith("/steps"),
                    m -> "the bean language is written as method: (expression: {method: {ref: myBean, method:"
                         + " process}}), or call the bean as a step with - bean: {ref: myBean, method: process}"),
            // expression: {json: {expression: '{"error": ...}'}}: json is a data format, and "did you mean 'js'?" sent a
            // model to JavaScript 4 times in a row (r25 rest-code-first step 1, for the JSON answer of a 404)
            replace("additionalProperties", null,
                    m -> "json".equals(m.unknown())
                            && (m.locationEndsWith("/expression") || YamlValidator.EXPRESSION_REQUIRED.contains(m.name())),
                    SchemaHints::jsonAsALanguageHint, "additionalProperties", "additionalProperties"),
            // expression: {file: {filePath: stock.json}}: file is not a language of an expression, and "not defined in
            // the schema" said nothing more (r20 to r26 connect-service-jms-request-reply step 3: 5 of 7 runs in r26).
            // Not for the script EIP, where the file is a script to run rather than data
            replace("additionalProperties", null,
                    m -> "file".equals(m.unknown()) && !m.location().matches(".*/script(/expression)?")
                            && (m.locationEndsWith("/expression") || YamlValidator.EXPRESSION_REQUIRED.contains(m.name())),
                    SchemaHints::fileAsALanguageHint, "additionalProperties", "additionalProperties"),
            // dataFormatProperty: [- prettyPrint: "true"]: an item of a key/value property list is a key and a value
            unknownProperty(".*/restConfiguration/(dataFormatProperty|componentProperty|endpointProperty|consumerProperty"
                            + "|apiProperty|corsHeaders)/\\d+",
                    ANY,
                    m -> "an item of " + m.parentName() + " is a key and a value: - key: " + m.unknown()
                         + " followed by value: \"...\" (indented under the -)"),
            // setHeader: {CamelNumberA: {simple: ...}} : the name is a property, not the key
            unknownProperty(".*/(setHeader|setProperty|setVariable|removeHeader|removeProperty|removeVariable)",
                    m -> YamlValidator.closest(m.unknown(), m.validator().knownProperties(m.schemaLocation())) == null,
                    m -> "the name is a property: " + m.name() + ": {name: " + m.unknown()
                         + (m.name().startsWith("set") ? ", expression: {simple: {expression: \"...\"}}}" : "}")
                         + " (" + m.unknown() + " is not the key)"),
            // unmarshal: {jackson: {}}: the data format named as its artifact or catalog entry, not by its key
            unknownProperty(".*/(marshal|unmarshal)", ANY,
                    m -> m.validator().dataFormatHint(m.unknown(), m.name(), m.schemaLocation())),
            unknownProperty(".*/bean", m -> Set.of("parameters", "args", "arguments").contains(m.unknown()),
                    m -> "arguments are written in the method call: bean: {ref: myBean, method: \"process(${body},"
                         + " 'x')\"}"),
            // pollEnrich: {uri: ...}: the endpoint of enrich and pollEnrich is an expression (CAMEL-24850)
            unknownProperty(".*/(enrich|pollEnrich)",
                    m -> m.unknown().equals("uri") || m.unknown().equals("resourceUri"),
                    m -> {
                        JsonNode instance = m.error().getInstanceNode();
                        JsonNode value = instance != null ? instance.get(m.unknown()) : null;
                        String uri = value != null && value.isValueNode() ? value.asText() : "file:...";
                        return "the endpoint of " + m.name() + " is an expression: write " + m.name()
                               + ": {expression: {constant: {expression: \"" + uri + "\"}}}";
                    }),
            // when: [..., - otherwise: {steps: [...]}]: otherwise is a key of choice, not an item of its when: list (r27
            // connect-http-client step 3)
            unknownProperty(".*/when/\\d+", m -> m.unknown().equals("otherwise"),
                    m -> "otherwise is not an item of when: it is a key of choice, next to when: and at its column, and"
                         + " it holds only steps: choice: {when: [...], otherwise: {steps: [...]}}"),
            // choice: {steps: [...]}: the steps of a choice are under each when: and under otherwise: (r26
            // contracts-openapi-server step 4: told that choice owns a steps: list, a model wrote it 6 times)
            unknownProperty(".*/choice", m -> m.unknown().equals("steps"),
                    m -> "choice has no steps: of its own: write the steps under a when: (next to its expression:) or"
                         + " under otherwise:, as in choice: {when: [{expression: {simple: {expression: \"...\"}},"
                         + " steps: [...]}], otherwise: {steps: [...]}}"),
            // - steps: [...] as an item of a steps list, or steps: on an EIP without a pipeline: there is no group item
            // (and no "did you mean 'step'?", which leads to the Step EIP with the EIPs as its keys)
            unknownProperty(null, m -> m.unknown().equals("steps"),
                    m -> "steps: is the list of a route or of an EIP that owns a pipeline (filter, split, loop, step);"
                         + " an EIP is an item of that list, not a group inside it: move the items up one level, or use"
                         + " step: {id: ..., steps: [...]} for a named group"),
            // step: {setHeader: ..., split: ...}: step is the Step EIP; one message for the whole item, not one per EIP
            replace("additionalProperties", ".*/step",
                    m -> m.unknown() != null && m.validator().stepNames().contains(m.unknown()),
                    m -> "step is the Step EIP, a named group: its EIPs go in its steps: list (step: {id: ..., steps: [-"
                         + " setHeader: ...]})",
                    "additionalProperties", "additionalProperties"),
            // CAMEL-24888 (the HTTP rungs of the examples ladder): the shapes a model writes for the EIPs of a REST app
            unknownProperty(null, m -> EXCHANGE_PROPERTY_EIPS.containsKey(m.unknown()),
                    m -> "the EIP is " + EXCHANGE_PROPERTY_EIPS.get(m.unknown()) + ": write - "
                         + EXCHANGE_PROPERTY_EIPS.get(m.unknown()) + ": {name: ..., expression: {simple: {expression:"
                         + " \"...\"}}} (an exchange property is read back as ${exchangeProperty.name})"),
            unknownProperty(".*/toD", m -> m.unknown().equals("options") || m.unknown().equals("params"),
                    m -> "toD takes its options like to: under parameters: (toD: {uri: \"http://...\", parameters:"
                         + " {throwExceptionOnFailure: false}}), or in the uri after ?"),
            unknownProperty(".*/jsonpath", m -> m.unknown().equalsIgnoreCase("jsonPath") || m.unknown().equals("path"),
                    m -> "the JSONPath text goes under expression: (jsonpath: {expression: \"$[?(@.sku == 'X')]\","
                         + " resultType: java.util.List})"),
            // expression: {jsonpath: "$[...]", resultType: java.util.Map}: an option of the language written next to its
            // short form, where the node has no such property; it goes under the language, with the text as expression:
            unknownProperty(null, m -> shortFormLanguage(m) != null,
                    m -> {
                        String language = shortFormLanguage(m);
                        return m.unknown() + " is an option of the " + language + " language: write the language as a"
                               + " map, with the text under expression: and " + m.unknown() + " next to it (" + language
                               + ": {expression: " + YamlValidator.yamlQuoted(valueOf(m, language, "...")) + ", "
                               + m.unknown() + ": " + valueOf(m, m.unknown(), "...") + "})";
                    }),
            // onException: {maximumRedeliveries: 3}, deadLetterChannel: {retryAttemptedLogLevel: WARN}: an option of
            // the redelivery policy written next to it, where the closest name (level) is not what was meant
            unknownProperty(".*/(onException|" + String.join("|", ROUTE_ERROR_HANDLER_KINDS) + ")",
                    m -> m.validator().redeliveryPolicyProperties().contains(m.unknown()),
                    m -> m.unknown() + " is an option of the redelivery policy: write it under redeliveryPolicy:, as"
                         + " redeliveryPolicy: {" + m.unknown() + ": " + valueOf(m, m.unknown(), "...") + "}"),
            // groovy: {script: "..."}, simple: {text: "..."}: the text of every language goes in expression:
            unknownProperty(null,
                    m -> m.validator().languageKeys().contains(m.name()) && !m.name().equals("language")
                            && m.error().getInstanceNode() != null
                            && !m.error().getInstanceNode().has("expression")
                            && m.error().getInstanceNode().path(m.unknown()).isTextual(),
                    m -> languageTextHint(m.name(), m.unknown(), valueOf(m, m.unknown(), "..."))),
            // - simple: "${body}" as a step: a language is not a step (and no "did you mean 'sample'?")
            unknownProperty(".*/steps/\\d+",
                    m -> m.validator().languageKeys().contains(m.unknown())
                            && !m.validator().stepNames().contains(m.unknown()),
                    m -> {
                        String text = YamlValidator.yamlQuoted(valueOf(m, m.unknown(), "..."));
                        return "'" + m.unknown() + "' is a language, not a step: an expression is written in a step that"
                               + " uses it, such as - setBody: {expression: {" + m.unknown() + ": {expression: " + text
                               + "}}} to set the body, or - filter: {expression: {" + m.unknown() + ": {expression: "
                               + text + "}}, steps: [...]} to keep the messages it matches";
                    }),
            unknownProperty(null,
                    m -> YamlValidator.closest(m.unknown(), m.validator().knownProperties(m.schemaLocation())) != null,
                    m -> "did you mean '"
                         + YamlValidator.closest(m.unknown(), m.validator().knownProperties(m.schemaLocation())) + "'?"));

    // -------------------------------------------------------------------------------------------------------------
    // compact notation hints (canonical schema only)
    // -------------------------------------------------------------------------------------------------------------

    /** The property a step written as a string sets: the argument of the definition's String constructor. */
    private static final Map<String, String> STRING_STEP_PROPERTY = Map.ofEntries(
            Map.entry("bean", "ref"), Map.entry("convertBodyTo", "type"), Map.entry("log", "message"),
            Map.entry("poll", "uri"), Map.entry("removeHeader", "name"), Map.entry("removeHeaders", "pattern"),
            Map.entry("removeProperties", "pattern"), Map.entry("removeProperty", "name"),
            Map.entry("removeVariable", "name"), Map.entry("rollback", "message"),
            Map.entry("setExchangePattern", "pattern"), Map.entry("to", "uri"), Map.entry("toD", "uri"));

    private static final String NORMALIZE_HINT = "; camel validate normalize rewrites a file in the canonical format";

    private static final String COMPACT_NOTATION = "compactNotation";

    /**
     * The canonical schema rejects the compact notation as a schema error that says nothing about it: "property
     * 'simple' is not defined" for a language key directly on the EIP, "string found, object expected" for a step or a
     * language written as a string. Each is replaced with a message that names the notation, the canonical form of that
     * line, and the normalize command.
     */
    static final List<Hint> COMPACT = List.of(
            // - from: at the top level: the route is written under route:, as XML writes <route> (CAMEL-24745)
            replace("additionalProperties", null,
                    m -> "from".equals(m.unknown()) && m.nameIsIndex() && m.parentName().isEmpty(),
                    m -> "a top-level from: is the deprecated compact notation: a route is written under route:"
                         + " (- route: {from: {uri: \"...\", steps: [...]}})" + NORMALIZE_HINT,
                    COMPACT_NOTATION, COMPACT_NOTATION),
            // setBody: {simple: ...} or when: [- simple: ...]: the language key sits on the EIP, not under expression:
            replace("additionalProperties", null,
                    m -> m.unknown() != null && m.validator().languageKeys().contains(m.unknown()),
                    m -> {
                        String form = m.validator().languageForm(m.unknown());
                        if (m.nameIsIndex()) {
                            return "a " + m.parentName() + " item with " + m.unknown() + ": ... is the deprecated compact"
                                   + " notation: an expression is written under expression: (- expression: {"
                                   + m.unknown() + ": {" + form + "}})" + NORMALIZE_HINT;
                        }
                        return m.name() + ": {" + m.unknown() + ": ...} is the deprecated compact notation: an"
                               + " expression is written under expression: (" + m.name() + ": {expression: {"
                               + m.unknown() + ": {" + form + "}}})" + NORMALIZE_HINT;
                    },
                    COMPACT_NOTATION, COMPACT_NOTATION),
            // simple: "..." : the language is a map with its expression
            replace("type", null,
                    m -> m.message().contains("string found, object expected")
                            && m.validator().languageKeys().contains(m.name()),
                    m -> m.name() + ": \"...\" is the deprecated compact notation: write " + m.name() + ": {"
                         + m.validator().languageForm(m.name()) + "}" + NORMALIZE_HINT,
                    COMPACT_NOTATION, COMPACT_NOTATION),
            // log: "..." : the step is a map with its properties
            replace("type", null,
                    m -> m.message().contains("string found, object expected")
                            && (m.validator().stepNames().contains(m.name())
                                    || m.validator().topLevelEntries().contains(m.name())),
                    m -> {
                        String property = STRING_STEP_PROPERTY.get(m.name());
                        return m.name() + ": \"...\" is the deprecated compact notation: write " + m.name()
                               + (property != null ? ": {" + property + ": \"...\"}" : " as a map with its properties")
                               + NORMALIZE_HINT;
                    },
                    COMPACT_NOTATION, COMPACT_NOTATION));

    static String between(String text, String start, String end) {
        int i = text.indexOf(start);
        if (i < 0) {
            return null;
        }
        int j = text.indexOf(end, i + start.length());
        return j < 0 ? null : text.substring(i + start.length(), j);
    }

    private SchemaHints() {
    }
}
