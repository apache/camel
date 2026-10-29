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

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CAMEL-26400: camel_eval_expression evaluates an expression the way the route will, with the headers and variables an
 * earlier step sets, the resultType of the language and as a predicate in any language. Here in the local scratch
 * context, with xpath, groovy and jq on the classpath of the tests.
 */
class ExpressionEvaluatorRequestTest {

    private static final String XML = "<x><y>1</y><y>2</y></x>";

    @TempDir
    Path home;

    private Path previousHome;

    // a home of its own: the tool picks the only running integration of the machine, when there is one
    @BeforeEach
    void useOwnHome() {
        previousHome = CommandLineHelper.getHomeDir();
        CommandLineHelper.useHomeDir(home.toString());
    }

    @AfterEach
    void restoreHome() {
        CommandLineHelper.useHomeDir(previousHome.toString());
    }

    @Test
    void anXPathBooleanIsEvaluatedAsAPredicate() {
        String expression = "boolean(/*[local-name()='x'])";
        JsonObject asNodes = eval("xpath", expression, XML, null, null, null, null);
        assertThat(asNodes.getString("status")).isEqualTo("error");
        assertThat(asNodes.getString("error")).contains("#BOOLEAN to a NodeList");
        assertThat(asNodes.getString("hint")).startsWith("this XPath returns a boolean, not nodes: pass predicate=true")
                .contains("xpath: {expression: \"boolean(/*[local-name()='x'])\", resultType: java.lang.Boolean}");

        JsonObject predicate = eval("xpath", expression, XML, null, null, true, null);
        assertThat(predicate.getString("status")).isEqualTo("ok");
        assertThat(predicate.getString("result")).isEqualTo("true");
        assertThat(predicate.getBoolean("predicate")).isTrue();
        assertThat(predicate.get("hint")).isNull();
    }

    @Test
    void anXPathStringFunctionTakesTheResultType() {
        String expression = "local-name(/*[local-name()='x']/*[1])";
        JsonObject asNodes = eval("xpath", expression, XML, null, null, null, null);
        assertThat(asNodes.getString("status")).isEqualTo("error");
        assertThat(asNodes.getString("hint")).startsWith("this XPath returns a string, not nodes: pass resultType"
                                                         + " java.lang.String")
                .contains("(in a route: xpath: {expression: \"" + expression + "\", resultType: java.lang.String})");

        JsonObject typed = eval("xpath", expression, XML, null, null, null, "java.lang.String");
        assertThat(typed.getString("status")).isEqualTo("ok");
        assertThat(typed.getString("result")).isEqualTo("y");
        assertThat(typed.getString("resultType")).isEqualTo("java.lang.String");
        // the short name works as in a route, where the class resolver knows it
        assertThat(eval("xpath", expression, XML, null, null, null, "String").getString("result")).isEqualTo("y");
    }

    @Test
    void anXPathNumberTakesANumberType() {
        JsonObject asNodes = eval("xpath", "count(/x/y)", XML, null, null, null, null);
        assertThat(asNodes.getString("hint")).startsWith("this XPath returns a number, not nodes: pass resultType"
                                                         + " java.lang.Integer");

        assertThat(eval("xpath", "count(/x/y)", XML, null, null, null, "java.lang.Integer").getString("result"))
                .isEqualTo("2");
    }

    @Test
    void nodesShowTheirXmlAsTheIntegrationAnswers() {
        JsonObject nodes = eval("xpath", "/x/y[1]", XML, null, null, null, null);

        assertThat(nodes.getString("status")).isEqualTo("ok");
        // not com.sun.org.apache.xml.internal.dtm.ref.DTMNodeList@60b333fd
        assertThat(nodes.getString("result")).isEqualTo("<y>1</y>");
    }

    @Test
    void anUnknownResultTypeSaysWhatToPass() {
        JsonObject result = eval("xpath", "string(/x/y)", XML, null, null, null, "Map");

        assertThat(result.getString("status")).isEqualTo("error");
        assertThat(result.getString("error")).contains("resultType Map is not a class on the classpath")
                .contains("java.util.Map");
    }

    @Test
    void aPredicateSaysThatItHasNoResultType() {
        JsonObject result = eval("simple", "${body} == 'a'", "a", null, null, null, "java.lang.Integer");

        assertThat(result.getString("result")).isEqualTo("true");
        assertThat(result.get("resultType")).isNull();
        assertThat(result.getString("note")).contains("resultType is not used for a predicate");
    }

    @Test
    void groovyReadsTheRowsOfAHeader() {
        Map<String, Object> headers = ExpressionEvaluator.parseValues("headers",
                "{\"totalOrdersRows\": [{\"total_orders\": 3}]}");
        JsonObject result = eval("groovy", "def total = headers.totalOrdersRows[0].total_orders as long; total + 1",
                null, headers, null, null, null);

        assertThat(result.getString("status")).as(result.toJson()).isEqualTo("ok");
        assertThat(result.getString("result")).isEqualTo("4");
    }

    @Test
    void jqReadsAVariable() {
        Map<String, Object> variables = ExpressionEvaluator.parseValues("variables",
                Jsoner.serialize(Map.of("requestBodyJson", "{\"products\": [{\"sku\": \"A\"}, {\"sku\": \"B\"}]}")));
        JsonObject result = eval("jq", "[ (variable(\"requestBodyJson\") | fromjson).products[] | .sku ]", "{}",
                null, variables, null, null);

        assertThat(result.getString("status")).as(result.toJson()).isEqualTo("ok");
        // as the integration answers it: the JSON of the node, which the type converter may indent
        assertThat(result.getString("result").replace(" ", "")).isEqualTo("[\"A\",\"B\"]");
    }

    @Test
    void headersAndVariablesAreKeyValueLines() {
        Map<String, Object> headers = ExpressionEvaluator.parseValues("headers", "foo=bar\n qty=3\n");
        Map<String, Object> variables = ExpressionEvaluator.parseValues("variables", "greeting=Hi");
        JsonObject result = eval("simple", "${variable.greeting} ${header.foo} ${header.qty}", null, headers, variables,
                null, null);

        assertThat(result.getString("result")).isEqualTo("Hi bar 3");
        assertThat(ExpressionEvaluator.parseValues("headers", " ")).isNull();
    }

    @Test
    void headersThatAreNeitherSayWhy() {
        assertThatThrownBy(() -> ExpressionEvaluator.parseValues("headers", "foo"))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessage("A line of headers must be in key=value format, was: foo (or pass a JSON object)");
        assertThatThrownBy(() -> ExpressionEvaluator.parseValues("variables", "{\"a\": "))
                .isInstanceOf(ToolExecutionException.class).hasMessageStartingWith("variables is not a valid JSON object");
    }

    /** The tool takes the arguments as text, as the MCP servers, the TUI and camel ask pass them. */
    @Test
    void theToolTakesTheNewArguments() throws Exception {
        Object answer = ToolRegistry.execute("camel_eval_expression", new ToolContext(), Map.of(
                "language", "xpath", "expression", "boolean(/x/y)", "body", XML, "predicate", "true"));
        JsonObject predicate = (JsonObject) Jsoner.deserialize(answer.toString());
        assertThat(predicate.getString("result")).isEqualTo("true");

        answer = ToolRegistry.execute("camel_eval_expression", new ToolContext(), Map.of(
                "expression", "${header.foo}-${variable.bar}", "headers", "{\"foo\": \"x\"}", "variables", "bar=y"));
        assertThat(((JsonObject) Jsoner.deserialize(answer.toString())).getString("result")).isEqualTo("x-y");

        answer = ToolRegistry.execute("camel_eval_expression", new ToolContext(), Map.of(
                "language", "xpath", "expression", "string(/x/y[2])", "body", XML, "resultType", "java.lang.String"));
        assertThat(((JsonObject) Jsoner.deserialize(answer.toString())).getString("result")).isEqualTo("2");

        // an explicit false keeps a simple predicate an expression
        answer = ToolRegistry.execute("camel_eval_expression", new ToolContext(), Map.of(
                "expression", "${body} == 'a'", "body", "a", "predicate", "false"));
        assertThat(((JsonObject) Jsoner.deserialize(answer.toString())).get("predicate")).isNull();

        assertThat(ToolRegistry.findTool("camel_eval_expression").params()).extracting(ToolDescriptor.Param::name)
                .containsAll(List.of("headers", "variables", "predicate", "resultType"));
    }

    private static JsonObject eval(
            String language, String expression, String body, Map<String, Object> headers, Map<String, Object> variables,
            Boolean predicate, String resultType) {
        return ExpressionEvaluator.evaluate(new ToolContext(),
                new ExpressionEvaluator.Request(language, expression, body, headers, variables, predicate, resultType));
    }
}
