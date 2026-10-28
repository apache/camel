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
    void theEvalToolOfCamelAsk() throws Exception {
        // an expression (not a predicate): the text with its quotes and slash
        Object answer = ToolRegistry.execute("eval_expression", ctx(),
                Map.of("language", "simple", "expression", "\"a/b\""));

        JsonObject json = (JsonObject) Jsoner.deserialize(answer.toString());
        assertThat(json.getString("status")).isEqualTo("success");
        assertThat(json.getString("result")).isEqualTo("\"a/b\"");
    }

    private static ToolContext ctx() {
        ToolContext ctx = new ToolContext();
        ctx.selectProcess(PID);
        return ctx;
    }

    // the eval action of the CLI connector: the values of the action as they are, to the eval-language console
    private Object eval(JsonObject root) {
        Object predicate = root.get("predicate");
        return console.call(DevConsole.MediaType.JSON, Map.of(
                "language", root.getStringOrDefault("language", "simple"),
                "predicate", predicate != null && Boolean.parseBoolean(predicate.toString()),
                "template", root.getStringOrDefault("template", ""),
                "body", root.getStringOrDefault("body", ""),
                "headers", Map.of(), "variables", Map.of()));
    }
}
