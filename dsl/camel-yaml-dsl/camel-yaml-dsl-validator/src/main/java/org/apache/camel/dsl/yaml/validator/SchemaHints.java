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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.Error;

/**
 * The hints the validator adds to the schema library's errors so they say what to write: one table per stage of the
 * pipeline in {@link YamlValidator}, each row a keyword, a location pattern, a condition and the text. The first row
 * that matches an error rewrites it; an error no row matches is kept as is.
 */
final class SchemaHints {

    /** What a row's condition and text see: the error and the pieces every row would otherwise recompute. */
    record Match(Error error, String location, String name, String message, String unknown, YamlValidator validator) {

        static Match of(Error error, YamlValidator validator) {
            String location = String.valueOf(error.getInstanceLocation());
            String message = error.getMessage();
            return new Match(
                    error, location, location.substring(location.lastIndexOf('/') + 1), message,
                    message != null && "additionalProperties".equals(error.getKeyword())
                            ? between(message, "property '", "'") : null,
                    validator);
        }

        /** The key before the last segment: the EIP a when/0 or doCatch/0 item belongs to. */
        String parentName() {
            String parent = location.substring(0, location.lastIndexOf('/'));
            return parent.substring(parent.lastIndexOf('/') + 1);
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

    /** The EIP names a model writes for the exchange properties, and the ones Camel has. */
    private static final Map<String, String> EXCHANGE_PROPERTY_EIPS = Map.of(
            "setExchangeProperty", "setProperty", "setExchangeProperties", "setProperties",
            "removeExchangeProperty", "removeProperty", "removeExchangeProperties", "removeProperties",
            "setExchangeVariable", "setVariable", "setExchangeVariables", "setVariables");

    private static final Set<String> ROUTE_ERROR_HANDLER_KINDS
            = Set.of("noErrorHandler", "deadLetterChannel", "defaultErrorHandler", "springTransactionErrorHandler",
                    "jtaTransactionErrorHandler", "refErrorHandler");

    /**
     * Applies a table to the errors: the first matching row rewrites each error. Two rewrites that say the same at the
     * same location (the branches of an anyOf, once the hint no longer names the branch) are reported once.
     */
    static List<Error> apply(List<Hint> table, List<Error> errors, YamlValidator validator) {
        List<Error> answer = new ArrayList<>(errors.size());
        Set<String> seen = new LinkedHashSet<>();
        for (Error error : errors) {
            Error hinted = apply(table, error, validator);
            if (hinted == error || seen.add(hinted.getInstanceLocation() + " " + hinted.getMessage())) {
                answer.add(hinted);
            }
        }
        return answer;
    }

    static Error apply(List<Hint> table, Error error, YamlValidator validator) {
        if (error.getMessage() == null) {
            return error;
        }
        Match m = Match.of(error, validator);
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
            append("maxProperties", ".*/steps/\\d+", ANY,
                    m -> "a step is one EIP: an option of that EIP is indented under its key, and the next EIP is its"
                         + " own - item"));

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
                    "type", "expression"));

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
            unknownProperty("/\\d+/beans/\\d+", m -> Set.of("id", "ref", "class").contains(m.unknown()),
                    m -> "a bean is - name: myBean followed by type: \"#class:com.example.MyBean\" (name instead of "
                         + m.unknown() + (m.unknown().equals("class") ? ", type instead of class" : "") + ")"),
            // - myBean: {type: ...} instead of - name: myBean / type: ...
            unknownProperty("/\\d+/beans/\\d+", ANY,
                    m -> "a bean item is written as - name: " + m.unknown()
                         + " followed by type: \"#class:com.example.MyBean\" (the name is a property, not the key)"),
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
            unknownProperty(".*/errorHandler", m -> m.unknown().equals("type") || m.unknown().equals("errorHandlerType"),
                    m -> "errorHandler: has the kind of handler as its key, not a " + m.unknown() + " property:"
                         + " errorHandler: {noErrorHandler: {}}, {deadLetterChannel: {deadLetterUri: \"...\"}} or"
                         + " {defaultErrorHandler: {...}}"),
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
            // - steps: [...] as an item of a steps list, or steps: on an EIP without a pipeline: there is no group item
            // (and no "did you mean 'step'?", which leads to the Step EIP with the EIPs as its keys)
            unknownProperty(null, m -> m.unknown().equals("steps"),
                    m -> "steps: is the list of a route or of an EIP that owns a pipeline (filter, split, choice, step);"
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
