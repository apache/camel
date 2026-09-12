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

import java.util.regex.Pattern;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.Language;
import org.apache.camel.support.DefaultExchange;
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
            evaluateLocally(lang, expression, body, predicate, result);
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
            String lang, String expression, String body, boolean predicate, JsonObject result) {
        try (DefaultCamelContext context = new DefaultCamelContext(false)) {
            context.start();
            Language language;
            try {
                language = context.resolveLanguage(lang);
            } catch (Exception e) {
                result.put("status", "error");
                result.put("error", "Unknown language '" + lang + "': " + e.getMessage());
                return;
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
            Throwable cause = e;
            while (cause.getCause() != null && cause.getCause() != cause) {
                cause = cause.getCause();
            }
            String message = cause.getMessage();
            result.put("error", message != null ? message : cause.toString());
        }
    }
}
