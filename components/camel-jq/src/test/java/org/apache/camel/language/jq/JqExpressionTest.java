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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.thisptr.jackson.jq.Scope;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

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

            var n1 = MAPPER.createObjectNode().with("commit");
            n1.with("commit").put("name", "Stephen Dolan");
            n1.with("commit").put("message", "Merge pull request #163 from stedolan/utf8-fixes\n\nUtf8 fixes. Closes #161");

            var n2 = MAPPER.createObjectNode();
            n2.with("commit").put("name", "Nicolas Williams");
            n2.with("commit").put("message", "Reject all overlong UTF8 sequences.");

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
            node.with("commit").put("name", "Nicolas Williams");
            node.with("commit").put("message", "Reject all overlong UTF8 sequences.");

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
            node.with("commit").put("name", "Nicolas Williams");
            node.with("commit").put("message", "Reject all overlong UTF8 sequences.");

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
            node.with("commit").put("name", "Nicolas Williams");
            node.with("commit").put("message", "Reject all overlong UTF8 sequences.");

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
            node.with("commit").put("name", "Nicolas Williams");
            node.with("commit").put("message", "Reject all overlong UTF8 sequences.");

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
    public void customScope() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            Scope root = Scope.newEmptyScope();

            JqExpression expression = new JqExpression(".foo");
            expression.setScope(root);
            expression.init(context);

            assertThat(expression.getScope()).isSameAs(root);
            assertThat(expression.getScope().getParentScope()).isNull();
            assertThat(expression.getScope().getLocalFunctions()).doesNotContainKeys("header/1", "header/2");
        }
    }

    private static JqExpression jq(CamelContext context, String expression) {
        JqExpression answer = new JqExpression(expression);
        answer.init(context);
        return answer;
    }

}
