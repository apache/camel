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
package org.apache.camel.language.jq;

import java.util.List;

import net.thisptr.jackson.jq.v2.core.Environment;
import net.thisptr.jackson.jq.v2.core.EnvironmentBuilder;
import net.thisptr.jackson.jq.v2.core.version.Versions;
import net.thisptr.jackson.jq.v2.json.impl.jackson3.Jackson3JsonProvider;
import net.thisptr.jackson.jq.v2.spi.exception.JsonQueryException;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.SimpleTypeConverter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class JqExpressionTest {
    public static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    public void extractHeader() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            Exchange exchange = new DefaultExchange(context);
            exchange.getMessage().setBody(MAPPER.createObjectNode());
            exchange.getMessage().setHeader("CommitterName", "Andrea");

            JqExpression expression = new JqExpression("header(\"CommitterName\")");
            expression.init(context);

            JsonNode result = expression.evaluate(exchange, JsonNode.class);

            assertThatJson(result)
                    .isString()
                    .isEqualTo("Andrea");
        }
    }

    @Test
    public void jackson2NodeBodyIsReadAsJson() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            Exchange exchange = new DefaultExchange(context);
            exchange.getMessage().setBody(new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode()
                    .put("name", "Andrea"));

            JqExpression expression = new JqExpression(".name");
            expression.init(context);

            JsonNode result = expression.evaluate(exchange, JsonNode.class);

            assertThatJson(result)
                    .isString()
                    .isEqualTo("Andrea");
        }
    }

    @Test
    public void jackson2NodeResultTypeIsConvertedFromJson() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            // stands in for the converter of camel-jackson, which reads JSON text into a Jackson 2 node
            com.fasterxml.jackson.databind.ObjectMapper jackson2 = new com.fasterxml.jackson.databind.ObjectMapper();
            context.getTypeConverterRegistry().addTypeConverter(com.fasterxml.jackson.databind.JsonNode.class, String.class,
                    new SimpleTypeConverter(false, (type, exchange, value) -> jackson2.readTree((String) value)));

            Exchange exchange = new DefaultExchange(context);
            exchange.getMessage().setBody(MAPPER.createObjectNode().set("g", MAPPER.createObjectNode().put("x", 1)));

            JqExpression expression = new JqExpression(".g");
            expression.setResultType(com.fasterxml.jackson.databind.JsonNode.class);
            expression.init(context);

            Object result = expression.evaluate(exchange, Object.class);

            assertThat(result).isInstanceOf(com.fasterxml.jackson.databind.JsonNode.class);
            assertThat(result).hasToString("{\"x\":1}");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "   ", "\n" })
    public void emptyBodyIsReadAsNull(String body) throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            Exchange exchange = new DefaultExchange(context);
            exchange.getMessage().setBody(body);

            assertThat(evaluate(context, exchange, "type")).isEqualTo("null");
            assertThat(evaluate(context, exchange, "try .a catch \"c\"")).isNull();
            assertThat(evaluate(context, exchange, ". // \"def\"")).isEqualTo("def");
        }
    }

    private static Object evaluate(CamelContext context, Exchange exchange, String query) {
        JqExpression expression = new JqExpression(query);
        expression.setResultType(String.class);
        expression.init(context);
        return expression.evaluate(exchange, Object.class);
    }

    @Test
    public void extractProperty() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            Exchange exchange = new DefaultExchange(context);
            exchange.getMessage().setBody(MAPPER.createObjectNode());
            exchange.setProperty("CommitterName", "Andrea");

            JqExpression expression = new JqExpression("property(\"CommitterName\")");
            expression.init(context);

            JsonNode result = expression.evaluate(exchange, JsonNode.class);

            assertThatJson(result)
                    .isString()
                    .isEqualTo("Andrea");
        }
    }

    @Test
    public void extractHeaderWithDefault() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            Exchange exchange = new DefaultExchange(context);
            exchange.getMessage().setBody(MAPPER.createObjectNode());
            exchange.getMessage().setHeader("MyHeader", "Andrea");

            JqExpression expression = new JqExpression("header(\"CommitterName\"; \"DefVal\")");
            expression.init(context);

            JsonNode result = expression.evaluate(exchange, JsonNode.class);

            assertThatJson(result)
                    .isString()
                    .isEqualTo("DefVal");
        }
    }

    @Test
    public void extractField() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("foo", "bar");
            node.put("baz", "bak");

            Exchange exchange = new DefaultExchange(context);
            exchange.getMessage().setBody(node);

            JqExpression expression = new JqExpression(".baz");
            expression.init(context);

            JsonNode result = expression.evaluate(exchange, JsonNode.class);

            assertThatJson(result)
                    .isString()
                    .isEqualTo("bak");
        }
    }

    @Test
    public void matches() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("foo", "bar");
            node.put("baz", "bak");

            Exchange exchange = new DefaultExchange(context);
            exchange.getMessage().setBody(node);
            exchange.getMessage().setHeader("CommitterName", "Andrea");

            assertThat(jq(context, "has(\"baz\")").matches(exchange)).isTrue();
            assertThat(jq(context, "has(\"bar\")").matches(exchange)).isFalse();
            assertThat(jq(context, "header(\"CommitterName\") == \"Andrea\"").matches(exchange)).isTrue();
            assertThat(jq(context, "header(\"CommitterName\") != \"Andrea\"").matches(exchange)).isFalse();
        }
    }

    @Test
    public void selectArray() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            ArrayNode node = MAPPER.createArrayNode();

            var n1 = MAPPER.createObjectNode().withObjectProperty("commit");
            n1.withObjectProperty("commit").put("name", "Stephen Dolan");
            n1.withObjectProperty("commit").put("message",
                    "Merge pull request #163 from stedolan/utf8-fixes\n\nUtf8 fixes. Closes #161");

            var n2 = MAPPER.createObjectNode();
            n2.withObjectProperty("commit").put("name", "Nicolas Williams");
            n2.withObjectProperty("commit").put("message", "Reject all overlong UTF8 sequences.");

            node.add(n1);
            node.add(n2);

            Exchange exchange = new DefaultExchange(context);
            exchange.getMessage().setBody(node);

            JqExpression expression = new JqExpression(".[] | { message: .commit.message, name: .commit.name} ");
            expression.init(context);

            List<JsonNode> result = (List<JsonNode>) expression.evaluate(exchange);

            assertThatJson(result.get(0)).isObject().containsEntry("name", "Stephen Dolan");
            assertThatJson(result.get(1)).isObject().containsEntry("name", "Nicolas Williams");
        }
    }

    @Test
    public void setField() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            ObjectNode node = MAPPER.createObjectNode();
            node.withObjectProperty("commit").put("name", "Nicolas Williams");
            node.withObjectProperty("commit").put("message", "Reject all overlong UTF8 sequences.");

            Exchange exchange = new DefaultExchange(context);
            exchange.getMessage().setBody(node);

            JqExpression expression = new JqExpression(".commit.name = \"Andrea\"");
            expression.init(context);

            JsonNode result = expression.evaluate(exchange, JsonNode.class);

            assertThatJson(result)
                    .inPath("$.commit.name")
                    .isString()
                    .isEqualTo("Andrea");
        }
    }

    @Test
    public void setFieldFromHeader() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            ObjectNode node = MAPPER.createObjectNode();
            node.withObjectProperty("commit").put("name", "Nicolas Williams");
            node.withObjectProperty("commit").put("message", "Reject all overlong UTF8 sequences.");

            Exchange exchange = new DefaultExchange(context);
            exchange.getMessage().setHeader("CommitterName", "Andrea");
            exchange.getMessage().setBody(node);

            JqExpression expression = new JqExpression(".commit.name = header(\"CommitterName\")");
            expression.init(context);

            JsonNode result = expression.evaluate(exchange, JsonNode.class);

            assertThatJson(result)
                    .inPath("$.commit.name")
                    .isString()
                    .isEqualTo("Andrea");
        }
    }

    @Test
    public void setFieldFromProperty() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            ObjectNode node = MAPPER.createObjectNode();
            node.withObjectProperty("commit").put("name", "Nicolas Williams");
            node.withObjectProperty("commit").put("message", "Reject all overlong UTF8 sequences.");

            Exchange exchange = new DefaultExchange(context);
            exchange.setProperty("CommitterName", "Andrea");
            exchange.getMessage().setBody(node);

            JqExpression expression = new JqExpression(".commit.name = property(\"CommitterName\")");
            expression.init(context);

            JsonNode result = expression.evaluate(exchange, JsonNode.class);

            assertThatJson(result)
                    .inPath("$.commit.name")
                    .isString()
                    .isEqualTo("Andrea");
        }
    }

    @Test
    public void removeField() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            ObjectNode node = MAPPER.createObjectNode();
            node.withObjectProperty("commit").put("name", "Nicolas Williams");
            node.withObjectProperty("commit").put("message", "Reject all overlong UTF8 sequences.");

            Exchange exchange = new DefaultExchange(context);
            exchange.getMessage().setBody(node);

            JqExpression expression = new JqExpression("del(.commit.name)");
            expression.init(context);

            JsonNode result = expression.evaluate(exchange, JsonNode.class);

            assertThatJson(result)
                    .inPath("$.commit")
                    .isObject()
                    .containsOnlyKeys("message");
        }
    }

    @Test
    public void customEnvironment() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            // built without JqFunctions, and without the classpath function loader, so no Camel functions
            Environment<JsonNode> environment = EnvironmentBuilder
                    .withDefaultLoaders(Jackson3JsonProvider.getInstance(), Versions.JQ_1_7)
                    .clearFunctionLoaders()
                    .build();

            JqExpression expression = new JqExpression(".foo");
            expression.setEnvironment(environment);
            expression.init(context);

            assertThat(expression.getEnvironment()).isSameAs(environment);
            assertThatThrownBy(() -> environment.compile("header(\"foo\")"))
                    .isInstanceOf(JsonQueryException.class)
                    .hasMessageContaining("header/1");
        }
    }

    private static JqExpression jq(CamelContext context, String expression) {
        JqExpression answer = new JqExpression(expression);
        answer.init(context);
        return answer;
    }

}
