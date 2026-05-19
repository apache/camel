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
package org.apache.camel.component.a2a.simple;

import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.component.a2a.A2AComponent;
import org.apache.camel.component.a2a.A2AConfiguration;
import org.apache.camel.component.a2a.A2AEndpoint;
import org.apache.camel.component.a2a.model.DataPart;
import org.apache.camel.component.a2a.model.FilePart;
import org.apache.camel.component.a2a.model.Message;
import org.apache.camel.component.a2a.model.Task;
import org.apache.camel.component.a2a.model.TaskState;
import org.apache.camel.component.a2a.model.TaskStatus;
import org.apache.camel.component.a2a.model.TextPart;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class A2ASimpleExpressionBuilderTest {

    private CamelContext context;

    @BeforeEach
    void setUp() throws Exception {
        context = new DefaultCamelContext();
        context.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (context != null) {
            context.stop();
        }
    }

    // ---- emitProgress ----

    @Test
    void emitProgressDefaultStateReturnsMessage() {
        Expression expr = A2ASimpleExpressionBuilder.emitProgress(null, "working on it");
        expr.init(context);

        Exchange exchange = new DefaultExchange(context);
        Object result = expr.evaluate(exchange, Object.class);

        assertThat(result).isEqualTo("working on it");
    }

    @Test
    void emitProgressExplicitStateReturnsMessage() {
        Expression expr = A2ASimpleExpressionBuilder.emitProgress("INPUT_REQUIRED", "need info");
        expr.init(context);

        Exchange exchange = new DefaultExchange(context);
        Object result = expr.evaluate(exchange, Object.class);

        assertThat(result).isEqualTo("need info");
    }

    @Test
    void emitProgressToStringIncludesFunction() {
        Expression expr = A2ASimpleExpressionBuilder.emitProgress(null, "msg");
        assertThat(expr.toString()).isEqualTo("a2a:emit(msg)");

        Expression expr2 = A2ASimpleExpressionBuilder.emitProgress("WORKING", "msg");
        assertThat(expr2.toString()).isEqualTo("a2a:emit(WORKING, msg)");
    }

    // ---- extractText ----

    @Test
    void extractTextFromTaskBody() {
        Expression expr = A2ASimpleExpressionBuilder.extractText(null);
        expr.init(context);

        Message msg = Message.builder()
                .role(Message.Role.ROLE_AGENT)
                .parts(List.of(new TextPart("hello world")))
                .build();
        Task task = Task.builder()
                .id("t1")
                .status(new TaskStatus(TaskState.COMPLETED))
                .history(List.of(msg))
                .build();

        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(task);

        Object result = expr.evaluate(exchange, Object.class);
        assertThat(result).isEqualTo("hello world");
    }

    @Test
    void extractTextFromMessageBody() {
        Expression expr = A2ASimpleExpressionBuilder.extractText(null);
        expr.init(context);

        Message msg = Message.builder()
                .role(Message.Role.ROLE_AGENT)
                .parts(List.of(new TextPart("message text")))
                .build();

        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(msg);

        Object result = expr.evaluate(exchange, Object.class);
        assertThat(result).isEqualTo("message text");
    }

    @Test
    void extractTextFromNonA2ABodyReturnsToString() {
        Expression expr = A2ASimpleExpressionBuilder.extractText(null);
        expr.init(context);

        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("plain string");

        Object result = expr.evaluate(exchange, Object.class);
        assertThat(result).isEqualTo("plain string");
    }

    @Test
    void extractTextFromNullBodyReturnsEmpty() {
        Expression expr = A2ASimpleExpressionBuilder.extractText(null);
        expr.init(context);

        Exchange exchange = new DefaultExchange(context);

        Object result = expr.evaluate(exchange, Object.class);
        assertThat(result).isEqualTo("");
    }

    // ---- extractData ----

    @Test
    void extractDataFromMessageBody() {
        Expression expr = A2ASimpleExpressionBuilder.extractData(null);
        expr.init(context);

        Message msg = Message.builder()
                .role(Message.Role.ROLE_AGENT)
                .parts(List.of(new DataPart(Map.of("key", "value"))))
                .build();

        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(msg);

        Object result = expr.evaluate(exchange, Object.class);
        assertThat((String) result).contains("key").contains("value");
    }

    @Test
    void extractDataFromNonA2ABodyReturnsEmpty() {
        Expression expr = A2ASimpleExpressionBuilder.extractData(null);
        expr.init(context);

        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("plain string");

        Object result = expr.evaluate(exchange, Object.class);
        assertThat(result).isEqualTo("");
    }

    // ---- extractFile ----

    @Test
    void extractFileFromMessageBody() {
        Expression expr = A2ASimpleExpressionBuilder.extractFile(null);
        expr.init(context);

        Message msg = Message.builder()
                .role(Message.Role.ROLE_AGENT)
                .parts(List.of(FilePart.ofUrl("https://example.com/readme.txt", "text/plain", "readme.txt")))
                .build();

        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(msg);

        Object result = expr.evaluate(exchange, Object.class);
        assertThat(result).isEqualTo("https://example.com/readme.txt");
    }

    @Test
    void extractFileFromNonA2ABodyReturnsEmpty() {
        Expression expr = A2ASimpleExpressionBuilder.extractFile(null);
        expr.init(context);

        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("plain string");

        Object result = expr.evaluate(exchange, Object.class);
        assertThat(result).isEqualTo("");
    }

    // ---- toString ----

    @Test
    void extractExpressionToString() {
        assertThat(A2ASimpleExpressionBuilder.extractText(null).toString()).isEqualTo("a2a:text");
        assertThat(A2ASimpleExpressionBuilder.extractText("${body}").toString()).isEqualTo("a2a:text(${body})");
        assertThat(A2ASimpleExpressionBuilder.extractData(null).toString()).isEqualTo("a2a:data");
        assertThat(A2ASimpleExpressionBuilder.extractFile(null).toString()).isEqualTo("a2a:file");
    }

    // ---- extractCardField ----

    @Test
    void extractCardFieldNameFromEndpoint() throws Exception {
        A2AComponent component = new A2AComponent();
        component.setCamelContext(context);
        A2AConfiguration config = new A2AConfiguration();
        config.setName("Card Test Agent");
        config.setVersion("3.0.0");
        config.setDescription("Agent for card field tests");
        A2AEndpoint a2aEndpoint = new A2AEndpoint("a2a:card-test", component, config);
        a2aEndpoint.setAgentCardSource("card-test");
        a2aEndpoint.setCamelContext(context);
        a2aEndpoint.start();

        Expression expr = A2ASimpleExpressionBuilder.extractCardField("name");
        expr.init(context);

        Exchange exchange = new DefaultExchange(context);
        exchange.getExchangeExtension().setFromEndpoint(a2aEndpoint);

        Object result = expr.evaluate(exchange, Object.class);
        assertThat(result).isEqualTo("Card Test Agent");
    }

    @Test
    void extractCardFieldVersionFromEndpoint() throws Exception {
        A2AComponent component = new A2AComponent();
        component.setCamelContext(context);
        A2AConfiguration config = new A2AConfiguration();
        config.setName("Card Test Agent");
        config.setVersion("3.0.0");
        A2AEndpoint a2aEndpoint = new A2AEndpoint("a2a:card-version-test", component, config);
        a2aEndpoint.setAgentCardSource("card-version-test");
        a2aEndpoint.setCamelContext(context);
        a2aEndpoint.start();

        Expression expr = A2ASimpleExpressionBuilder.extractCardField("version");
        expr.init(context);

        Exchange exchange = new DefaultExchange(context);
        exchange.getExchangeExtension().setFromEndpoint(a2aEndpoint);

        Object result = expr.evaluate(exchange, Object.class);
        assertThat(result).isEqualTo("3.0.0");
    }

    @Test
    void extractCardFieldDescriptionFromEndpoint() throws Exception {
        A2AComponent component = new A2AComponent();
        component.setCamelContext(context);
        A2AConfiguration config = new A2AConfiguration();
        config.setName("Desc Agent");
        config.setDescription("This is a description");
        A2AEndpoint a2aEndpoint = new A2AEndpoint("a2a:card-desc-test", component, config);
        a2aEndpoint.setAgentCardSource("card-desc-test");
        a2aEndpoint.setCamelContext(context);
        a2aEndpoint.start();

        Expression expr = A2ASimpleExpressionBuilder.extractCardField("description");
        expr.init(context);

        Exchange exchange = new DefaultExchange(context);
        exchange.getExchangeExtension().setFromEndpoint(a2aEndpoint);

        Object result = expr.evaluate(exchange, Object.class);
        assertThat(result).isEqualTo("This is a description");
    }

    @Test
    void extractCardFieldFullCardReturnsJson() throws Exception {
        A2AComponent component = new A2AComponent();
        component.setCamelContext(context);
        A2AConfiguration config = new A2AConfiguration();
        config.setName("JSON Agent");
        config.setVersion("1.0.0");
        A2AEndpoint a2aEndpoint = new A2AEndpoint("a2a:card-json-test", component, config);
        a2aEndpoint.setAgentCardSource("card-json-test");
        a2aEndpoint.setCamelContext(context);
        a2aEndpoint.start();

        Expression expr = A2ASimpleExpressionBuilder.extractCardField(null);
        expr.init(context);

        Exchange exchange = new DefaultExchange(context);
        exchange.getExchangeExtension().setFromEndpoint(a2aEndpoint);

        Object result = expr.evaluate(exchange, Object.class);
        assertThat(result).isInstanceOf(String.class);
        String json = (String) result;
        assertThat(json).contains("JSON Agent");
        assertThat(json).contains("1.0.0");
    }

    @Test
    void extractCardFieldUnknownReturnsEmpty() throws Exception {
        A2AComponent component = new A2AComponent();
        component.setCamelContext(context);
        A2AConfiguration config = new A2AConfiguration();
        config.setName("Unknown Field Agent");
        A2AEndpoint a2aEndpoint = new A2AEndpoint("a2a:card-unknown-test", component, config);
        a2aEndpoint.setAgentCardSource("card-unknown-test");
        a2aEndpoint.setCamelContext(context);
        a2aEndpoint.start();

        Expression expr = A2ASimpleExpressionBuilder.extractCardField("nonexistent");
        expr.init(context);

        Exchange exchange = new DefaultExchange(context);
        exchange.getExchangeExtension().setFromEndpoint(a2aEndpoint);

        Object result = expr.evaluate(exchange, Object.class);
        assertThat(result).isEqualTo("");
    }

    @Test
    void extractCardFieldNoEndpointReturnsEmpty() {
        Expression expr = A2ASimpleExpressionBuilder.extractCardField("name");
        expr.init(context);

        Exchange exchange = new DefaultExchange(context);

        Object result = expr.evaluate(exchange, Object.class);
        assertThat(result).isEqualTo("");
    }

    @Test
    void extractCardFieldFromContextScan() throws Exception {
        A2AComponent component = new A2AComponent();
        component.setCamelContext(context);
        A2AConfiguration config = new A2AConfiguration();
        config.setName("Context Scan Agent");
        A2AEndpoint a2aEndpoint = new A2AEndpoint("a2a:card-scan-test", component, config);
        a2aEndpoint.setAgentCardSource("card-scan-test");
        a2aEndpoint.setCamelContext(context);
        a2aEndpoint.start();
        // Register endpoint in context so resolveCard() can find it via context.getEndpoints()
        context.addEndpoint("a2a:card-scan-test", a2aEndpoint);

        Expression expr = A2ASimpleExpressionBuilder.extractCardField("name");
        expr.init(context);

        // Exchange WITHOUT fromEndpoint set — should find via context scan
        Exchange exchange = new DefaultExchange(context);

        Object result = expr.evaluate(exchange, Object.class);
        assertThat(result).isEqualTo("Context Scan Agent");
    }

    @Test
    void extractCardFieldSkillsReturnsFormattedText() throws Exception {
        A2AComponent component = new A2AComponent();
        component.setCamelContext(context);
        A2AConfiguration config = new A2AConfiguration();
        A2AEndpoint a2aEndpoint = new A2AEndpoint("a2a:classpath:agent-card-with-skills.json", component, config);
        a2aEndpoint.setAgentCardSource("classpath:agent-card-with-skills.json");
        a2aEndpoint.setCamelContext(context);
        a2aEndpoint.start();

        Expression expr = A2ASimpleExpressionBuilder.extractCardField("skills");
        expr.init(context);

        Exchange exchange = new DefaultExchange(context);
        exchange.getExchangeExtension().setFromEndpoint(a2aEndpoint);

        Object result = expr.evaluate(exchange, Object.class);
        assertThat(result).isInstanceOf(String.class);
        String skillsText = (String) result;
        assertThat(skillsText).contains("*");
    }

    @Test
    void extractCardFieldToString() {
        assertThat(A2ASimpleExpressionBuilder.extractCardField(null).toString()).isEqualTo("a2a:card");
        assertThat(A2ASimpleExpressionBuilder.extractCardField("name").toString()).isEqualTo("a2a:card.name");
        assertThat(A2ASimpleExpressionBuilder.extractCardField("skills").toString()).isEqualTo("a2a:card.skills");
        assertThat(A2ASimpleExpressionBuilder.extractCardField("skills.json").toString())
                .isEqualTo("a2a:card.skills.json");
    }
}
