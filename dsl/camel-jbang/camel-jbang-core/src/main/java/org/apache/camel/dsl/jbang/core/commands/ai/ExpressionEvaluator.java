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
package org.apache.camel.dsl.jbang.core.commands.ai;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.catalog.LanguageValidationResult;
import org.apache.camel.dsl.jbang.core.common.ActionClient;
import org.apache.camel.dsl.jbang.core.common.RuntimeHelper;
import org.apache.camel.dsl.jbang.core.common.ScratchContextHelper;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.main.download.DependencyDownloaderClassLoader;
import org.apache.camel.main.download.MavenDependencyDownloader;
import org.apache.camel.spi.Language;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.tooling.model.LanguageModel;
import org.apache.camel.util.json.DeserializationException;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/**
 * Evaluates an expression for an AI agent: inside the running integration when one is selected (the way
 * {@code camel cmd eval} does, so the expression sees the real context), otherwise locally in a scratch CamelContext
 * with the languages on the CLI's classpath, so a simple expression can be checked before it is written into a route
 * that is not running yet. The result is the value, or the parser or evaluation error, so the tool doubles as a
 * validator.
 */
public final class ExpressionEvaluator {

    private static final Pattern PREDICATE_OPERATOR = Pattern.compile(
            "\\s(==|=~|!=|!=~|>|>=|<|<=|~~|!~~|contains|!contains|regex|!regex|in|!in|is|!is|range|!range"
                                                                      + "|startsWith|!startsWith|endsWith|!endsWith|equals|!equals|&&|\\|\\|)\\s");

    // a path that ends with a filter: $[?(@.sku == 'CAMEL-MUG')]
    private static final Pattern FILTER_ENDS_PATH = Pattern.compile("\\)]\\s*$");

    // the JDK's XPath asked for nodes, the default result of the xpath language, by a function that gives a value
    private static final Pattern XPATH_NOT_NODES = Pattern.compile("Can not convert #(STRING|BOOLEAN|NUMBER) to a NodeList");

    private ExpressionEvaluator() {
    }

    /**
     * What to evaluate, and the message to evaluate it against.
     *
     * @param language   the language, simple by default
     * @param expression the expression
     * @param body       the message body, or null for an empty one
     * @param headers    the message headers, or null for none
     * @param variables  the exchange variables, or null for none
     * @param predicate  whether to evaluate it as a predicate; null guesses it for simple (an operator) and is false
     *                   for the other languages
     * @param resultType the class of the result, such as java.lang.String, as the resultType of the language in a
     *                   route, or null for the language's own (nodes for xpath)
     */
    public record Request(
            String language, String expression, String body, Map<String, Object> headers,
            Map<String, Object> variables, Boolean predicate, String resultType) {

        public Request(String language, String expression, String body) {
            this(language, expression, body, null, null, null, null);
        }

        boolean isPredicate() {
            return Boolean.TRUE.equals(predicate);
        }
    }

    /**
     * Headers or variables as the tool takes them: a JSON object, whose values keep their JSON type (so a list of rows
     * works for a groovy headers.rows[0].total), or key=value lines as camel_runtime_send takes headers, whose values
     * are text.
     *
     * @param  name                   the argument, headers or variables, for the error
     * @param  text                   the argument, or null
     * @return                        the values by name, or null when there are none
     * @throws ToolExecutionException when the text is neither
     */
    public static Map<String, Object> parseValues(String name, String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String trimmed = text.strip();
        Map<String, Object> answer = new LinkedHashMap<>();
        if (trimmed.startsWith("{")) {
            Object json;
            try {
                json = Jsoner.deserialize(trimmed);
            } catch (DeserializationException e) {
                throw new ToolExecutionException(name + " is not a valid JSON object: " + e.getMessage());
            }
            if (json instanceof Map<?, ?> map) {
                map.forEach((k, v) -> answer.put(String.valueOf(k), v));
                return answer.isEmpty() ? null : answer;
            }
        }
        try {
            for (Object entry : RuntimeHelper.parseKeyValues(trimmed, "A line of " + name)) {
                JsonObject jo = (JsonObject) entry;
                answer.put(jo.getString("key"), jo.getString("value"));
            }
        } catch (IllegalArgumentException e) {
            throw new ToolExecutionException(e.getMessage() + " (or pass a JSON object)");
        }
        return answer.isEmpty() ? null : answer;
    }

    /** Whether a simple expression is a predicate: it has a comparison or logical operator and no ternary or elvis. */
    public static boolean looksLikePredicate(String expression) {
        if (expression == null || expression.contains(" ? ") || expression.contains(" ?: ")) {
            return false;
        }
        return PREDICATE_OPERATOR.matcher(expression).find();
    }

    /**
     * @param  ctx        the context; its selected process evaluates the expression when it has one
     * @param  language   the language, simple by default
     * @param  expression the expression
     * @param  body       the message body, or null for an empty one
     * @return            language, expression, predicate, status (ok or error) and the result or error
     */
    public static JsonObject evaluate(ToolContext ctx, String language, String expression, String body) {
        return evaluate(ctx, new Request(language, expression, body));
    }

    /**
     * @param  ctx     the context; its selected process evaluates the expression when it has one
     * @param  request the expression, and the message to evaluate it against
     * @return         language, expression, predicate, status (ok or error) and the result or error
     */
    public static JsonObject evaluate(ToolContext ctx, Request request) {
        String lang = request.language() == null || request.language().isBlank() ? "simple" : request.language();
        String expression = request.expression();
        String body = request.body();
        boolean predicate = request.predicate() != null
                ? request.predicate() : "simple".equals(lang) && looksLikePredicate(expression);
        String resultType = request.resultType() == null || request.resultType().isBlank()
                ? null : request.resultType().strip();
        JsonObject result = new JsonObject();
        result.put("language", lang);
        result.put("expression", expression);
        if (predicate) {
            result.put("predicate", true);
        } else if (resultType != null) {
            result.put("resultType", resultType);
        }
        if (body == null && expression.contains("${body")) {
            result.put("note", "evaluated with an empty body; pass body to evaluate against a value");
        }
        if (predicate && resultType != null) {
            addNote(result, "resultType is not used for a predicate, which gives true or false");
            resultType = null;
        }
        Request r = new Request(lang, expression, body, request.headers(), request.variables(), predicate, resultType);
        if (ctx.hasProcess()) {
            result.put("evaluatedIn", "the running integration (pid " + ctx.pid() + ")");
            evaluateInProcess(ctx, r, result);
        } else {
            result.put("evaluatedIn", "a local scratch context (no integration selected)");
            evaluateLocally(ctx, r, result);
        }
        addNullAndElvisNotes(ctx, result, lang, expression);
        addFilterNote(result, lang, expression);
        addXPathHint(result, lang, expression);
        return result;
    }

    static final String NULL_NOTE = "evaluated to null: a header, property or variable that is not set, or a function"
                                    + " name that took in the rest of the text";

    /**
     * Says that a value is null, and why it may be. An answer that leaves out null fields (the MCP server does) has no
     * result then, which a model read as a form that works: it wrote ${header.offset ?:0} into its SQL, which reads a
     * header named 'offset ?:0' (CAMEL-26401). An elvis operator inside ${...} that the Camel of the evaluation does
     * not read as one is the note, also when the value is not null or the evaluation failed.
     */
    static void addNullAndElvisNotes(ToolContext ctx, JsonObject result, String lang, String expression) {
        boolean isNull = "ok".equals(result.getString("status")) && result.get("result") == null;
        if (isNull) {
            result.put("result", null);
            result.put("nullResult", true);
        }
        String elvis = elvisNote(ctx, lang, expression);
        String note = elvis != null ? elvis : isNull ? NULL_NOTE : null;
        if (note != null) {
            result.put("note", result.containsKey("note") ? result.getString("note") + "; " + note : note);
        }
    }

    /**
     * What to say about an elvis operator inside ${...} that the Camel of the evaluation does not read as one, or null.
     */
    private static String elvisNote(ToolContext ctx, String lang, String expression) {
        return "simple".equals(lang) && expression != null && expression.contains("?:")
                ? SimpleChecks.elvisInsideFunction(expression, camelVersion(ctx)) : null;
    }

    /**
     * The Camel version that evaluates: the selected integration's, else the one the tools are for (null for the CLI's
     * own, which evaluates locally).
     */
    private static String camelVersion(ToolContext ctx) {
        if (!ctx.hasProcess()) {
            return ctx.camelVersion();
        }
        try {
            JsonObject status = ctx.readFullStatus();
            JsonObject context = status != null ? status.getMap("context") : null;
            return context != null ? context.getString("version") : null;
        } catch (Exception e) {
            // the status cannot be read: the version is not known
            return null;
        }
    }

    /** Adds a note to the result of an evaluation, after the note it has already. */
    private static void addNote(JsonObject result, String note) {
        result.put("note", result.containsKey("note") ? result.getString("note") + "; " + note : note);
    }

    /** Adds the note about a jsonpath filter to the result of an evaluation, after the note it has already. */
    static void addFilterNote(JsonObject result, String lang, String expression) {
        String filter = filterNote(lang, expression, result.getString("status"));
        if (filter != null) {
            addNote(result, filter);
        }
    }

    /** Adds what to pass to an XPath function that gives a value instead of nodes, to an error of an evaluation. */
    static void addXPathHint(JsonObject result, String lang, String expression) {
        String hint = xpathHint(lang, expression, result.getString("error"));
        if (hint != null) {
            result.put("hint", hint);
        }
    }

    /**
     * The xpath language evaluates to nodes unless told otherwise, so a function that gives a value (string(),
     * local-name(), boolean(), count()) fails with "Can not convert #STRING to a NodeList": a model of the eval tried
     * the tool three times with such an XPath, then gave up on it. The hint says what to pass, here and in the route.
     */
    static String xpathHint(String lang, String expression, String error) {
        if (!"xpath".equals(lang) || error == null) {
            return null;
        }
        Matcher m = XPATH_NOT_NODES.matcher(error);
        if (!m.find()) {
            return null;
        }
        String route = "xpath: {expression: " + quoted(expression) + ", resultType: ";
        return switch (m.group(1)) {
            case "STRING" -> "this XPath returns a string, not nodes: pass resultType java.lang.String (in a route: "
                             + route + "java.lang.String})";
            case "BOOLEAN" -> "this XPath returns a boolean, not nodes: pass predicate=true (in a route: use it as the"
                              + " predicate of a filter or a when, or as " + route + "java.lang.Boolean})";
            default -> "this XPath returns a number, not nodes: pass resultType java.lang.Integer, or"
                       + " java.lang.Double for a fraction (in a route: " + route + "java.lang.Integer})";
        };
    }

    /** The text as a quoted YAML value: in single quotes when it holds a double quote, as the YAML validator quotes. */
    private static String quoted(String text) {
        return text.contains("\"") || text.contains("\\") ? "'" + text.replace("'", "''") + "'" : "\"" + text + "\"";
    }

    /**
     * A jsonpath filter gives a list, also for one match: models picked the element with an index after the filter,
     * which gives an empty list, or a made-up syntax. In a route, resultType java.util.Map gives the one element when
     * the path ends with the filter, and the type of the value gives the one value of a path that goes on after it.
     */
    static String filterNote(String lang, String expression, String status) {
        if (!"jsonpath".equals(lang) || expression == null || !expression.contains("[?(") || !"ok".equals(status)) {
            return null;
        }
        String problem = JsonPathChecks.filterProblem(expression, false);
        if (problem != null) {
            return problem;
        }
        if (FILTER_ENDS_PATH.matcher(expression).find()) {
            return "a filter gives a list, also for one match; in a route, resultType: java.util.Map gives the one"
                   + " element it matches (null when none or several do)";
        }
        return "a filter gives a list, also for one match; in a route, a resultType that is not a list, such as"
               + " java.lang.String or java.lang.Integer, gives the one value when one element matches";
    }

    private static void evaluateInProcess(ToolContext ctx, Request r, JsonObject result) {
        // an integration before Camel 4.24 reads the value of a header or variable as text, and has no resultType
        String version = ActionClient.versionOf(ctx.pid());
        boolean before424 = version != null && !ActionClient.isAtLeast(version, 4, 24);
        String ignored = before424 && r.resultType() != null
                ? "the integration runs Camel " + version + ", which ignores resultType (taken from Camel 4.24 on)"
                : null;
        if (ignored != null) {
            addNote(result, ignored);
        }
        String raw;
        try {
            raw = ctx.executeAction("eval", root -> {
                root.put("language", r.language());
                root.put("predicate", String.valueOf(r.isPredicate()));
                // as they are: the action file is JSON, and the integration does not unescape them
                root.put("template", r.expression());
                if (r.body() != null) {
                    root.put("body", r.body());
                }
                if (r.headers() != null) {
                    root.put("headers", keyValues(r.headers(), !before424));
                }
                if (r.variables() != null) {
                    root.put("variables", keyValues(r.variables(), !before424));
                }
                if (r.resultType() != null) {
                    root.put("resultType", r.resultType());
                }
            });
        } catch (ToolExecutionException e) {
            // the integration answers an expression that fails as a failed action, a tool error (CAMEL-25398): what
            // to pass for an XPath function, that resultType was not used, and the rewrite of an elvis operator inside
            // ${...} that an integration before Camel 4.24 refuses, go with its message
            String hint = xpathHint(r.language(), r.expression(), e.getMessage());
            String elvis = elvisNote(ctx, r.language(), r.expression());
            if (hint == null && ignored == null && elvis == null) {
                throw e;
            }
            throw new ToolExecutionException(
                    e.getMessage() + (hint != null ? "; " + hint : "") + (ignored != null ? "; " + ignored : "")
                                             + (elvis != null ? "; " + elvis : ""));
        }
        JsonObject out = null;
        try {
            out = (JsonObject) Jsoner.deserialize(raw);
        } catch (Exception e) {
            // not JSON: a timeout message
        }
        if (out == null) {
            result.put("status", "error");
            result.put("error", raw);
            return;
        }
        if ("success".equals(out.getString("status"))) {
            result.put("status", "ok");
            result.put("result", out.get("result"));
            return;
        }
        result.put("status", "error");
        JsonObject cause = out.getMap("exception");
        String message = cause != null ? cause.getString("message") : null;
        result.put("error", message != null ? Jsoner.unescape(message) : "evaluation failed");
    }

    /**
     * Headers or variables as the eval action of the CLI connector reads them: a key and a value for each. A JSON list
     * or object goes as JSON text to an integration before Camel 4.24, which reads every value as text.
     */
    private static JsonArray keyValues(Map<String, Object> values, boolean typed) {
        JsonArray list = new JsonArray();
        values.forEach((k, v) -> {
            JsonObject jo = new JsonObject();
            jo.put("key", k);
            jo.put("value", typed || !(v instanceof Map || v instanceof Collection) ? v : Jsoner.serialize(v));
            list.add(jo);
        });
        return list;
    }

    private static void evaluateLocally(ToolContext ctx, Request r, JsonObject result) {
        String lang = r.language();
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        try {
            String gav = languageArtifact(ctx, lang);
            // the languages of this process (simple, constant, header, ...) need no class loader of their own
            ClassLoader known = gav != null ? DOWNLOADED.get(gav) : null;
            if (evaluateWith(known, r, result)) {
                if (known != null) {
                    // downloaded by an earlier call: say so again, so the answers of two calls read the same
                    result.put("downloaded", gav);
                }
                syntaxCheck(ctx, known, lang, r.expression(), r.isPredicate(), result);
                return;
            }
            // the language is not on this process's classpath: download its component as camel run does, so a
            // jsonpath, jq or xpath expression can be tried before it is written (CAMEL-24907)
            StringBuilder failure = new StringBuilder();
            ClassLoader downloaded = download(gav, failure);
            if (downloaded == null || !evaluateWith(downloaded, r, result)) {
                result.put("status", "error");
                result.put("error", "The language '" + lang + "' is not on the classpath of this process"
                                    + (gav != null ? " and " + gav + " could not be downloaded" : "")
                                    + (failure.isEmpty() ? "" : " (" + failure + ")")
                                    + "; select a running integration that has it (camel_run, then its name) and the"
                                    + " expression is evaluated there");
                return;
            }
            result.put("downloaded", gav);
            syntaxCheck(ctx, downloaded, lang, r.expression(), r.isPredicate(), result);
        } finally {
            Thread.currentThread().setContextClassLoader(tccl);
        }
    }

    /**
     * Evaluates with the given class loader (null for this process's own), and answers whether the language was found;
     * the result of the evaluation, or its error, is put in {@code result}.
     */
    private static boolean evaluateWith(ClassLoader loader, Request r, JsonObject result) {
        // without the CLI connector, which would make this process, such as the MCP server, a running integration
        try (DefaultCamelContext context = ScratchContextHelper.withoutCliConnector(new DefaultCamelContext(false))) {
            if (loader != null) {
                // before the start: the language resolver reads the class loader as the context comes up
                context.setApplicationContextClassLoader(loader);
                Thread.currentThread().setContextClassLoader(loader);
            }
            context.start();
            Language language;
            try {
                language = context.resolveLanguage(r.language());
            } catch (Exception e) {
                return false;
            }
            Exchange exchange = new DefaultExchange(context);
            exchange.getMessage().setBody(r.body() != null ? r.body() : "");
            if (r.headers() != null) {
                r.headers().forEach(exchange.getMessage()::setHeader);
            }
            if (r.variables() != null) {
                r.variables().forEach(exchange::setVariable);
            }
            Object value;
            if (r.isPredicate()) {
                Predicate p = language.createPredicate(r.expression());
                p.init(context);
                value = p.matches(exchange);
            } else {
                // the resultType of a route goes to the language as its first property, as the reifiers pass it
                Expression e = r.resultType() != null
                        ? language.createExpression(r.expression(), new Object[] { resultType(context, r.resultType()) })
                        : language.createExpression(r.expression());
                e.init(context);
                value = e.evaluate(exchange, Object.class);
            }
            result.put("status", "ok");
            result.put("result", text(exchange, value));
        } catch (Exception e) {
            result.put("status", "error");
            result.put("error", rootCause(e));
        }
        return true;
    }

    /** The class of a resultType as a route resolves it, so java.lang.String works, and String as well. */
    private static Class<?> resultType(CamelContext context, String name) {
        Class<?> type = context.getClassResolver().resolveClass(name);
        if (type == null) {
            throw new IllegalArgumentException(
                    "resultType " + name + " is not a class on the classpath; use a class name such as"
                                               + " java.lang.String, java.lang.Integer or java.util.Map");
        }
        return type;
    }

    /**
     * The value as text, the way the integration answers it: converted by the context, so an XPath node list shows its
     * XML instead of the name of its class.
     */
    private static String text(Exchange exchange, Object value) {
        if (value == null) {
            return null;
        }
        String text = exchange.getContext().getTypeConverter().tryConvertTo(String.class, exchange, value);
        return text != null ? text : value.toString();
    }

    /**
     * The catalog's own check of the text, which names where the syntax breaks (its index), so an error says more than
     * the evaluation's exception; only added when it finds something the evaluation did not.
     */
    private static void syntaxCheck(
            ToolContext ctx, ClassLoader loader, String lang, String expression, boolean predicate, JsonObject result) {
        if ("ok".equals(result.getString("status"))) {
            return;
        }
        try {
            LanguageValidationResult check = predicate
                    ? ctx.catalog().validateLanguagePredicate(loader, lang, expression)
                    : ctx.catalog().validateLanguageExpression(loader, lang, expression);
            if (!check.isSuccess()) {
                String error = check.getShortError() != null ? check.getShortError() : check.getError();
                if (error != null) {
                    result.put("syntaxError", error);
                    if (check.getIndex() >= 0) {
                        result.put("syntaxErrorAt", check.getIndex());
                    }
                }
            }
        } catch (Exception e) {
            // the catalog cannot check this language here; the evaluation's own error stands
        }
    }

    private static String rootCause(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getMessage() != null ? cause.getMessage() : cause.toString();
    }

    /** The groupId:artifactId:version of a language, from the catalog, or null when the catalog does not know it. */
    private static String languageArtifact(ToolContext ctx, String lang) {
        try {
            LanguageModel model = ctx.catalog().languageModel(lang);
            if (model != null && model.getArtifactId() != null) {
                return model.getGroupId() + ":" + model.getArtifactId() + ":" + model.getVersion();
            }
        } catch (Exception e) {
            // the catalog does not know it
        }
        return null;
    }

    /**
     * Downloads the component of a language and keeps its class loader, so the next call does not download again.
     * Returns null when there is nothing to download (an unknown name) or the download fails, appending the reason to
     * {@code failure}.
     */
    private static ClassLoader download(String gav, StringBuilder failure) {
        if (gav == null) {
            return null;
        }
        String[] parts = gav.split(":");
        // computeIfAbsent so two evaluations of the same language do not download it twice and leak a class loader;
        // a failed download stores nothing, so the next call tries again
        return DOWNLOADED.computeIfAbsent(gav, k -> {
            try {
                DependencyDownloaderClassLoader cl
                        = new DependencyDownloaderClassLoader(ExpressionEvaluator.class.getClassLoader());
                try (MavenDependencyDownloader downloader = new MavenDependencyDownloader()) {
                    downloader.setClassLoader(cl);
                    downloader.start();
                    downloader.downloadDependency(parts[0], parts[1], parts[2]);
                }
                return cl;
            } catch (Exception e) {
                // say why, so the answer names the cause (offline, wrong repository, unknown artifact)
                failure.append(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                return null;
            }
        });
    }

    /** The class loaders of the languages downloaded so far, so the next call does not download again. */
    private static final Map<String, ClassLoader> DOWNLOADED = new ConcurrentHashMap<>();
}
