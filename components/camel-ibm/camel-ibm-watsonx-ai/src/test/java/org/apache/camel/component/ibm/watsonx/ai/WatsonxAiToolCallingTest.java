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
package org.apache.camel.component.ibm.watsonx.ai;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.ibm.watsonx.ai.chat.ExecutableTool;
import com.ibm.watsonx.ai.chat.ToolRegistry;
import com.ibm.watsonx.ai.chat.model.Tool;
import com.ibm.watsonx.ai.chat.model.ToolArguments;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The declarative tool calling loop (chat, then processToolCalls and chat while the model calls tools) sends the whole
 * conversation, from the system message and the question of the body, to the model on every turn.
 */
class WatsonxAiToolCallingTest extends CamelTestSupport {

    private static final String TOOL_CALL_RESPONSE = """
            {"id":"chat-1","object":"chat.completion","model_id":"model","model":"model","created":1,
             "created_at":"2026-01-01T00:00:00.000Z",
             "choices":[{"index":0,"message":{"role":"assistant","tool_calls":[{"id":"call-1","type":"function",
               "function":{"name":"capital","arguments":"{\\"country\\":\\"France\\"}"}}]},"finish_reason":"tool_calls"}],
             "usage":{"completion_tokens":1,"prompt_tokens":2,"total_tokens":3}}""";

    private static final String ANSWER_RESPONSE = """
            {"id":"chat-2","object":"chat.completion","model_id":"model","model":"model","created":1,
             "created_at":"2026-01-01T00:00:00.000Z",
             "choices":[{"index":0,"message":{"role":"assistant","content":"The capital of France is Paris."},
               "finish_reason":"stop"}],
             "usage":{"completion_tokens":1,"prompt_tokens":2,"total_tokens":3}}""";

    private static final ExecutableTool CAPITAL = new ExecutableTool() {
        @Override
        public String name() {
            return "capital";
        }

        @Override
        public Tool schema() {
            return Tool.of("capital", "The capital of a country",
                    Map.of("type", "object", "properties", Map.of("country", Map.of("type", "string"))));
        }

        @Override
        public String execute(ToolArguments args) {
            return "France".equals(args.get("country")) ? "Paris" : "unknown";
        }
    };

    private static final ToolRegistry REGISTRY = ToolRegistry.builder().register(CAPITAL).build();

    private static FakeWatsonxServer server;

    @BeforeAll
    static void startServer() throws IOException {
        server = FakeWatsonxServer.start();
    }

    @AfterAll
    static void stopServer() {
        server.close();
    }

    @BeforeEach
    void resetServer() {
        server.reset();
        server.respond("/ml/v1/text/chat", TOOL_CALL_RESPONSE).respond("/ml/v1/text/chat", ANSWER_RESPONSE);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                getContext().getRegistry().bind("auth", FakeWatsonxServer.TEST_AUTHENTICATOR);
                String chat = "ibm-watsonx-ai:chat?authenticator=#auth&baseUrl=" + server.baseUrl()
                              + "&projectId=my-project&modelId=model&operation=chat";
                from("direct:agent")
                        .setHeader(WatsonxAiConstants.SYSTEM_MESSAGE, constant("You are a helpful assistant."))
                        .setHeader(WatsonxAiConstants.TOOLS, constant(REGISTRY.tools()))
                        .setHeader(WatsonxAiConstants.TOOL_REGISTRY, constant(REGISTRY))
                        .to(chat)
                        .loopDoWhile(header(WatsonxAiConstants.HAS_TOOL_CALLS).isEqualTo(true))
                            .to("ibm-watsonx-ai:tools?operation=processToolCalls")
                            .to(chat)
                        .end();
            }
        };
    }

    @Test
    void theConversationKeepsTheSystemMessageAndTheQuestion() {
        Exchange result = template.send("direct:agent", e -> e.getIn().setBody("What is the capital of France?"));

        assertNull(result.getException());
        assertEquals("The capital of France is Paris.", result.getMessage().getBody());
        // the tool calls of the first answer are not left on the message
        assertFalse(result.getMessage().getHeader(WatsonxAiConstants.HAS_TOOL_CALLS, Boolean.class));
        assertNull(result.getMessage().getHeader(WatsonxAiConstants.TOOL_CALLS));

        List<FakeWatsonxServer.Request> requests = server.requests("/ml/v1/text/chat");
        assertEquals(2, requests.size());

        JsonNode first = requests.get(0).json().get("messages");
        assertEquals(2, first.size());
        assertEquals("system", first.get(0).get("role").asText());
        assertEquals("user", first.get(1).get("role").asText());

        JsonNode second = requests.get(1).json().get("messages");
        assertEquals(4, second.size(), second::toString);
        assertEquals("system", second.get(0).get("role").asText());
        assertEquals("You are a helpful assistant.", second.get(0).get("content").asText());
        assertEquals("user", second.get(1).get("role").asText());
        assertEquals("What is the capital of France?", second.get(1).get("content").get(0).get("text").asText());
        assertEquals("assistant", second.get(2).get("role").asText());
        assertEquals("call-1", second.get(2).get("tool_calls").get(0).get("id").asText());
        assertEquals("tool", second.get(3).get("role").asText());
        assertEquals("call-1", second.get(3).get("tool_call_id").asText());
        assertEquals("Paris", second.get(3).get("content").asText());
        // the tools are sent on every turn
        assertEquals("capital", requests.get(1).json().get("tools").get(0).get("function").get("name").asText());
    }
}
