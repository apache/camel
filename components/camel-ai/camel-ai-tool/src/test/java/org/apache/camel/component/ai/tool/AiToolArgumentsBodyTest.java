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
package org.apache.camel.component.ai.tool;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiToolArgumentsBodyTest extends CamelTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("ai-tool:order?tags=test&argumentsAsBody=true&argSchema=classpath:arg-schemas/create-order.json")
                        .setProperty("invoked", constant(true));
                from("ai-tool:defaultBody?tags=test&parameter.name=string")
                        .setProperty("invoked", constant(true));
                from("ai-tool:disabledBody?tags=test&argumentsAsBody=false&parameter.name=string")
                        .setProperty("invoked", constant(true));
                from("ai-tool:empty?tags=test&argumentsAsBody=true")
                        .setProperty("invoked", constant(true));
                from("ai-tool:reserved?tags=test&argumentsAsBody=true&parameter.safe=string"
                     + "&parameter.CamelAiToolCallId=string&parameter.camelAiToolConversationId=string")
                        .setProperty("invoked", constant(true));
            }
        };
    }

    @Test
    void serializesNestedArgumentsAndKeepsHeaders() throws Exception {
        Map<String, Object> customer = new HashMap<>(Map.of("id", "Alice \"A\"", "active", true, "balance", 12.5));
        customer.put("note", null);
        List<Map<String, Object>> items = List.of(Map.of("sku", "TSHIRT", "qty", 2), Map.of("sku", "MUG", "qty", 1));
        Map<String, Object> arguments = Map.of("customer", customer, "items", items);
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("previous body");

        AiToolResult result = AiToolExecutor.execute(spec("order"), arguments, exchange, "call-1", "conversation-1");

        assertThat(result).isInstanceOf(AiToolResult.Success.class);
        assertThat(exchange.getMessage().getBody()).isInstanceOf(String.class);
        assertThat(Jsoner.deserialize(exchange.getMessage().getBody(String.class))).isEqualTo(Jsoner.deserialize("""
                {"customer":{"id":"Alice \\"A\\"","active":true,"balance":12.5,"note":null},
                 "items":[{"sku":"TSHIRT","qty":2},{"sku":"MUG","qty":1}]}
                """));
        assertThat(exchange.getMessage().getHeader("customer")).isSameAs(customer);
        assertThat(exchange.getMessage().getHeader("items")).isSameAs(items);
        assertThat(exchange.getMessage().getHeader(AiToolConstants.TOOL_CALL_ID)).isEqualTo("call-1");
        assertThat(exchange.getMessage().getHeader(AiToolConstants.CONVERSATION_ID)).isEqualTo("conversation-1");
    }

    @Test
    void leavesBodyUnchangedByDefaultAndStillPropagatesIdentifiers() {
        assertBodyUnchanged("defaultBody");
    }

    @Test
    void leavesBodyUnchangedWhenExplicitlyDisabled() {
        assertBodyUnchanged("disabledBody");
    }

    private void assertBodyUnchanged(String toolName) {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("original");

        AiToolResult result = AiToolExecutor.execute(spec(toolName), Map.of("name", "Alice"), exchange, "call-2", "chat-2");

        assertThat(result).isInstanceOf(AiToolResult.Success.class);
        assertThat(exchange.getMessage().getBody()).isEqualTo("original");
        assertThat(exchange.getMessage().getHeader("name")).isEqualTo("Alice");
        assertThat(exchange.getMessage().getHeader(AiToolConstants.TOOL_CALL_ID)).isEqualTo("call-2");
        assertThat(exchange.getMessage().getHeader(AiToolConstants.CONVERSATION_ID)).isEqualTo("chat-2");
    }

    @Test
    void serializesAnEmptyObjectForNullArguments() {
        Exchange exchange = new DefaultExchange(context);

        AiToolResult result = AiToolExecutor.execute(spec("empty"), null, exchange);

        assertThat(result).isInstanceOf(AiToolResult.Success.class);
        assertThat(exchange.getMessage().getBody()).isEqualTo("{}");
        assertThat(exchange.getMessage().getHeader(AiToolConstants.TOOL_CALL_ID)).isNull();
        assertThat(exchange.getMessage().getHeader(AiToolConstants.CONVERSATION_ID)).isNull();
    }

    @Test
    void preservesUndeclaredArgumentsOnlyInTheBody() throws Exception {
        Map<String, Object> arguments = Map.of("extra", List.of("one", "two"), "CamelFileName", "forged");
        Exchange exchange = new DefaultExchange(context);

        AiToolResult result = AiToolExecutor.execute(spec("empty"), arguments, exchange);

        assertThat(result).isInstanceOf(AiToolResult.Success.class);
        assertThat(Jsoner.deserialize(exchange.getMessage().getBody(String.class)))
                .isEqualTo(Jsoner.deserialize("{\"extra\":[\"one\",\"two\"]}"));
        assertThat(exchange.getMessage().getHeaders()).doesNotContainKeys("extra", "CamelFileName");
        assertThat(arguments).hasSize(2);
    }

    @Test
    void doesNotAllowArgumentsToOverrideInvocationIdentifiers() throws Exception {
        Map<String, Object> arguments = Map.of("safe", "ok", "CamelAiToolCallId", "forged-call",
                "camelAiToolConversationId", "forged-chat");
        Exchange exchange = new DefaultExchange(context);

        AiToolResult result = AiToolExecutor.execute(spec("reserved"), arguments, exchange, "real-call", "real-chat");

        assertThat(result).isInstanceOf(AiToolResult.Success.class);
        assertThat(Jsoner.deserialize(exchange.getMessage().getBody(String.class)))
                .isEqualTo(Jsoner.deserialize("{\"safe\":\"ok\"}"));
        assertThat(exchange.getMessage().getHeader(AiToolConstants.TOOL_CALL_ID)).isEqualTo("real-call");
        assertThat(exchange.getMessage().getHeader(AiToolConstants.CONVERSATION_ID)).isEqualTo("real-chat");
        assertThat(arguments).hasSize(3);
    }

    @Test
    void removesStaleIdentifiersWhenNoIdentifiersAreSupplied() {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader(AiToolConstants.TOOL_CALL_ID, "stale-call");
        exchange.getMessage().setHeader(AiToolConstants.CONVERSATION_ID, "stale-chat");

        AiToolExecutor.execute(spec("empty"), Map.of(), exchange);

        assertThat(exchange.getMessage().getHeaders()).doesNotContainKeys(
                AiToolConstants.TOOL_CALL_ID, AiToolConstants.CONVERSATION_ID);
    }

    @Test
    void rejectsMissingRequiredArgumentsBeforeInvokingTheRoute() {
        Exchange exchange = new DefaultExchange(context);

        AiToolResult result = AiToolExecutor.execute(spec("order"), Map.of("items", List.of()), exchange);

        assertThat(result).isInstanceOf(AiToolResult.ArgumentError.class);
        assertThat(exchange.getProperty("invoked")).isNull();
    }

    private AiToolSpec spec(String name) {
        return AiToolRegistry.getOrCreate(context).getToolsByTag("test").stream()
                .filter(tool -> tool.getName().equals(name)).findFirst().orElseThrow();
    }
}
