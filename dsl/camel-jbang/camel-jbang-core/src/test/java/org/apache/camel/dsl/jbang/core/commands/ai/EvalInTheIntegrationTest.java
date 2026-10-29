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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.console.DevConsole;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.console.EvalLanguageDevConsole;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The expression tools evaluate in the selected integration: it gets the expression and the body as they are, as the
 * eval action of the CLI connector reads them (it does not unescape them), here answered by the eval-language dev
 * console of a Camel context of the test.
 */
class EvalInTheIntegrationTest {

    private static final long PID = 4711L;

    @TempDir
    Path home;

    private Path previousHome;
    private DefaultCamelContext context;
    private EvalLanguageDevConsole console;
    private FakeIntegration integration;

    @BeforeEach
    void start() throws Exception {
        previousHome = CommandLineHelper.getHomeDir();
        CommandLineHelper.useHomeDir(home.toString());
        Files.createDirectories(CommandLineHelper.getCamelDir());
        context = new DefaultCamelContext();
        context.start();
        console = new EvalLanguageDevConsole();
        console.setCamelContext(context);
        console.start();
        integration = new FakeIntegration(PID, this::eval);
    }

    @AfterEach
    void stop() throws Exception {
        integration.close();
        context.close();
        CommandLineHelper.useHomeDir(previousHome.toString());
    }

    @Test
    void anExpressionWithQuotesAndSlashes() {
        JsonObject result = ExpressionEvaluator.evaluate(ctx(), "simple", "${body} contains \"/\"", "orders/1");

        assertThat(result.getString("evaluatedIn")).startsWith("the running integration");
        assertThat(result.getString("status")).isEqualTo("ok");
        assertThat(result.get("result")).isEqualTo("true");
    }

    @Test
    void aBodyWithQuotes() {
        JsonObject result = ExpressionEvaluator.evaluate(ctx(), "simple", "${body}", "{\"id\":1}");

        assertThat(result.getString("status")).isEqualTo("ok");
        assertThat(result.getString("result")).isEqualTo("{\"id\":1}");
    }

    @Test
    void aNullValueOfTheIntegrationIsSaidToBeNull() {
        JsonObject result = ExpressionEvaluator.evaluate(ctx(), "simple", "${header.missing}", null);

        assertThat(result.getString("status")).isEqualTo("ok");
        assertThat(result.get("result")).isNull();
        assertThat(result.getBoolean("nullResult")).isTrue();
        assertThat(result.getString("note")).isEqualTo(ExpressionEvaluator.NULL_NOTE);
    }

    @Test
    void anElvisWithoutItsSpacesInTheIntegration() {
        // CAMEL-26401: answered ok and without a result, which a model took for a form that works
        JsonObject result = ExpressionEvaluator.evaluate(ctx(), "simple", "${header.missing ?:10}", null);

        assertThat(result.getString("status")).isEqualTo("ok");
        assertThat(result.getBoolean("nullResult")).isTrue();
        assertThat(result.getString("note")).startsWith("`${header.missing ?:10}` reads a header named 'missing ?:10'")
                .contains("goes after the function: `${header.missing} ?: 10`");
    }

    @Test
    void theElvisInsideThePlaceholderInAnIntegrationOfAnOlderCamel() throws Exception {
        // the status of the integration says its version; the one of the test reads the form, Camel 4.22 refuses it
        Files.writeString(CommandLineHelper.getCamelDir().resolve(PID + "-status.json"),
                "{\"context\":{\"version\":\"4.22.1\"}}");
        JsonObject result = ExpressionEvaluator.evaluate(ctx(), "simple", "${header.missing ?: 10}", null);

        assertThat(result.getString("note")).contains("needs Camel 4.24 or later")
                .contains("(here Camel 4.22.1)")
                .endsWith("write it after the function: `${header.missing} ?: 10`");

        Files.writeString(CommandLineHelper.getCamelDir().resolve(PID + "-status.json"),
                "{\"context\":{\"version\":\"4.24.0\"}}");
        JsonObject current = ExpressionEvaluator.evaluate(ctx(), "simple", "${header.missing ?: 10}", null);
        assertThat(current.getString("result")).isEqualTo("10");
        assertThat(current.get("note")).isNull();
    }

    @Test
    void theEvalToolOfCamelAsk() throws Exception {
        // an expression (not a predicate): the text with its quotes and slash
        Object answer = ToolRegistry.execute("eval_expression", ctx(),
                Map.of("language", "simple", "expression", "\"a/b\""));

        JsonObject json = (JsonObject) Jsoner.deserialize(answer.toString());
        assertThat(json.getString("status")).isEqualTo("success");
        assertThat(json.getString("result")).isEqualTo("\"a/b\"");
    }

    /** CAMEL-26400: the headers, variables, predicate and resultType of the call go to the eval action. */
    @Test
    void theHeadersVariablesPredicateAndResultTypeGoToTheAction() {
        JsonObject result = ExpressionEvaluator.evaluate(ctx(), new ExpressionEvaluator.Request(
                "simple",
                "${header.foo}-${variable.bar}", null, Map.of("foo", "x"), Map.of("bar", "y"), null, null));

        assertThat(result.getString("status")).isEqualTo("ok");
        assertThat(result.getString("result")).isEqualTo("x-y");
        JsonObject action = integration.lastAction();
        Collection<JsonObject> headers = action.getCollection("headers");
        assertThat(headers).containsExactly(keyValue("foo", "x"));
        Collection<JsonObject> variables = action.getCollection("variables");
        assertThat(variables).containsExactly(keyValue("bar", "y"));
        assertThat(action.getString("predicate")).isEqualTo("false");
        assertThat(action.get("resultType")).isNull();

        JsonObject typed = ExpressionEvaluator.evaluate(ctx(), new ExpressionEvaluator.Request(
                "simple", "${body}",
                "0042", null, null, null, "java.lang.Integer"));
        assertThat(integration.lastAction().getString("resultType")).isEqualTo("java.lang.Integer");
        assertThat(typed.getString("result")).isEqualTo("42");
        assertThat(typed.get("note")).isNull();

        // any language can be a predicate: the action says so, and the console answers true or false
        JsonObject predicate = ExpressionEvaluator.evaluate(ctx(), new ExpressionEvaluator.Request(
                "constant",
                "true", null, null, null, true, null));
        assertThat(integration.lastAction().getString("predicate")).isEqualTo("true");
        assertThat(predicate.getString("result")).isEqualTo("true");
    }

    /** A JSON list or object keeps its type, so a groovy headers.rows[0].total works in the integration too. */
    @Test
    void aJsonValueKeepsItsType() {
        Map<String, Object> headers = ExpressionEvaluator.parseValues("headers", "{\"rows\": [{\"total\": 3}]}");
        ExpressionEvaluator.evaluate(ctx(), new ExpressionEvaluator.Request(
                "simple", "${header.rows}", null, headers,
                null, null, null));

        JsonObject header = (JsonObject) List.copyOf(integration.lastAction().getCollection("headers")).get(0);
        assertThat(header.get("value")).isInstanceOf(List.class);
    }

    /**
     * An integration before Camel 4.24 reads every value of a header as text, and has no resultType: the list goes as
     * JSON text, and the answer says that resultType is not used there.
     */
    @Test
    void anIntegrationBefore424GetsTextAndANoteOnTheResultType() throws Exception {
        Files.writeString(CommandLineHelper.getCamelDir().resolve(PID + "-status.json"),
                "{\"context\": {\"name\": \"orders\", \"version\": \"4.23.0\"}}");
        Map<String, Object> headers = ExpressionEvaluator.parseValues("headers", "{\"rows\": [{\"total\": 3}]}");

        JsonObject result = ExpressionEvaluator.evaluate(ctx(), new ExpressionEvaluator.Request(
                "simple", "${body}",
                "1", headers, null, null, "java.lang.Integer"));

        assertThat(result.getString("note")).contains("the integration runs Camel 4.23.0, which ignores resultType");
        JsonObject header = (JsonObject) List.copyOf(integration.lastAction().getCollection("headers")).get(0);
        assertThat(header.get("value")).isEqualTo("[{\"total\":3}]");
    }

    /**
     * The XPath of a route that fails in the integration gets the same hint as a local evaluation, with the error the
     * tool answers (a failed eval action is a tool error).
     */
    @Test
    void anXPathErrorOfTheIntegrationGetsTheHint() throws Exception {
        try (FakeIntegration failing = new FakeIntegration(
                PID + 1, root -> Map.of("status", "failed", "exception",
                        Map.of("message", "Invalid xpath: boolean(/x). Reason: javax.xml.xpath.XPathExpressionException:"
                                          + " com.sun.org.apache.xpath.internal.XPathException: Can not convert #BOOLEAN to a"
                                          + " NodeList!")))) {
            ToolContext ctx = new ToolContext();
            ctx.selectProcess(PID + 1);

            assertThatThrownBy(() -> ExpressionEvaluator.evaluate(ctx, "xpath", "boolean(/x)", "<x/>"))
                    .isInstanceOf(ToolExecutionException.class)
                    .hasMessageStartingWith("Invalid xpath: boolean(/x).")
                    .hasMessageContaining("NodeList!; this XPath returns a boolean, not nodes: pass predicate=true");
            assertThat(failing.actions()).hasSize(1);
        }
    }

    /**
     * An integration before Camel 4.24 refuses the elvis operator inside ${...}: the tool error says how to write it
     * there, as the answer of an evaluation that works does (CAMEL-26401).
     */
    @Test
    void anElvisInsideThePlaceholderRefusedByAnOlderIntegrationGetsTheRewrite() throws Exception {
        Files.writeString(CommandLineHelper.getCamelDir().resolve((PID + 1) + "-status.json"),
                "{\"context\":{\"version\":\"4.22.1\"}}");
        try (FakeIntegration failing = new FakeIntegration(
                PID + 1, root -> Map.of("status", "failed", "exception",
                        Map.of("message", "${ cannot accept ?: at location 17")))) {
            ToolContext ctx = new ToolContext();
            ctx.selectProcess(PID + 1);

            assertThatThrownBy(() -> ExpressionEvaluator.evaluate(ctx, "simple", "${header.missing ?: 10}", null))
                    .isInstanceOf(ToolExecutionException.class)
                    .hasMessageStartingWith("${ cannot accept ?: at location 17; ")
                    .hasMessageContaining("needs Camel 4.24 or later")
                    .hasMessageContaining("(here Camel 4.22.1)")
                    .hasMessageEndingWith("write it after the function: `${header.missing} ?: 10`");
        }
    }

    private static JsonObject keyValue(String key, Object value) {
        JsonObject jo = new JsonObject();
        jo.put("key", key);
        jo.put("value", value);
        return jo;
    }

    private static ToolContext ctx() {
        ToolContext ctx = new ToolContext();
        ctx.selectProcess(PID);
        return ctx;
    }

    // the eval action of the CLI connector: the values of the action as they are, to the eval-language console
    private Object eval(JsonObject root) {
        Object predicate = root.get("predicate");
        Map<String, Object> options = new HashMap<>();
        options.put("language", root.getStringOrDefault("language", "simple"));
        options.put("predicate", predicate != null && Boolean.parseBoolean(predicate.toString()));
        options.put("template", root.getStringOrDefault("template", ""));
        options.put("body", root.getStringOrDefault("body", ""));
        options.put("headers", values(root, "headers"));
        options.put("variables", values(root, "variables"));
        if (root.getString("resultType") != null) {
            options.put("resultType", root.getString("resultType"));
        }
        return console.call(DevConsole.MediaType.JSON, options);
    }

    private static Map<String, Object> values(JsonObject root, String name) {
        Map<String, Object> values = new LinkedHashMap<>();
        Collection<JsonObject> list = root.getCollection(name);
        if (list != null) {
            list.forEach(jo -> values.put(jo.getString("key"), jo.get("value")));
        }
        return values;
    }
}
