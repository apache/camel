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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.catalog.LanguageValidationResult;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.main.download.DependencyDownloaderClassLoader;
import org.apache.camel.main.download.MavenDependencyDownloader;
import org.apache.camel.spi.Language;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.tooling.model.LanguageModel;
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

    private ExpressionEvaluator() {
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
        String lang = language == null || language.isBlank() ? "simple" : language;
        boolean predicate = "simple".equals(lang) && looksLikePredicate(expression);
        JsonObject result = new JsonObject();
        result.put("language", lang);
        result.put("expression", expression);
        if (predicate) {
            result.put("predicate", true);
        }
        if (body == null && expression.contains("${body")) {
            result.put("note", "evaluated with an empty body; pass body to evaluate against a value");
        }
        if (ctx.hasProcess()) {
            result.put("evaluatedIn", "the running integration (pid " + ctx.pid() + ")");
            evaluateInProcess(ctx, lang, expression, body, predicate, result);
        } else {
            result.put("evaluatedIn", "a local scratch context (no integration selected)");
            evaluateLocally(ctx, lang, expression, body, predicate, result);
        }
        return result;
    }

    private static void evaluateInProcess(
            ToolContext ctx, String lang, String expression, String body, boolean predicate, JsonObject result) {
        String raw = ctx.executeAction("eval", root -> {
            root.put("language", lang);
            root.put("predicate", String.valueOf(predicate));
            root.put("template", Jsoner.escape(expression));
            if (body != null) {
                root.put("body", Jsoner.escape(body));
            }
        });
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

    private static void evaluateLocally(
            ToolContext ctx, String lang, String expression, String body, boolean predicate, JsonObject result) {
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        try {
            String gav = languageArtifact(ctx, lang);
            // the languages of this process (simple, constant, header, ...) need no class loader of their own
            ClassLoader known = gav != null ? DOWNLOADED.get(gav) : null;
            if (evaluateWith(known, lang, expression, body, predicate, result)) {
                if (known != null) {
                    // downloaded by an earlier call: say so again, so the answers of two calls read the same
                    result.put("downloaded", gav);
                }
                syntaxCheck(ctx, known, lang, expression, predicate, result);
                return;
            }
            // the language is not on this process's classpath: download its component as camel run does, so a
            // jsonpath, jq or xpath expression can be tried before it is written (CAMEL-24907)
            StringBuilder failure = new StringBuilder();
            ClassLoader downloaded = download(gav, failure);
            if (downloaded == null || !evaluateWith(downloaded, lang, expression, body, predicate, result)) {
                result.put("status", "error");
                result.put("error", "The language '" + lang + "' is not on the classpath of this process"
                                    + (gav != null ? " and " + gav + " could not be downloaded" : "")
                                    + (failure.isEmpty() ? "" : " (" + failure + ")")
                                    + "; select a running integration that has it (camel_run, then its name) and the"
                                    + " expression is evaluated there");
                return;
            }
            result.put("downloaded", gav);
            syntaxCheck(ctx, downloaded, lang, expression, predicate, result);
        } finally {
            Thread.currentThread().setContextClassLoader(tccl);
        }
    }

    /**
     * Evaluates with the given class loader (null for this process's own), and answers whether the language was found;
     * the result of the evaluation, or its error, is put in {@code result}.
     */
    private static boolean evaluateWith(
            ClassLoader loader, String lang, String expression, String body, boolean predicate, JsonObject result) {
        try (DefaultCamelContext context = new DefaultCamelContext(false)) {
            if (loader != null) {
                // before the start: the language resolver reads the class loader as the context comes up
                context.setApplicationContextClassLoader(loader);
                Thread.currentThread().setContextClassLoader(loader);
            }
            context.start();
            Language language;
            try {
                language = context.resolveLanguage(lang);
            } catch (Exception e) {
                return false;
            }
            Exchange exchange = new DefaultExchange(context);
            exchange.getMessage().setBody(body != null ? body : "");
            Object value;
            if (predicate) {
                Predicate p = language.createPredicate(expression);
                p.init(context);
                value = p.matches(exchange);
            } else {
                Expression e = language.createExpression(expression);
                e.init(context);
                value = e.evaluate(exchange, Object.class);
            }
            result.put("status", "ok");
            result.put("result", value != null ? value.toString() : null);
        } catch (Exception e) {
            result.put("status", "error");
            result.put("error", rootCause(e));
        }
        return true;
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
