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
package org.apache.camel.component.huggingface.tasks;

import java.util.ArrayList;
import java.util.List;

import ai.djl.modality.Input;
import ai.djl.modality.Output;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.apache.camel.Exchange;
import org.apache.camel.component.huggingface.HuggingFaceConfiguration;
import org.apache.camel.component.huggingface.HuggingFaceEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The chat task keeps a history per memory ID: the number of histories and the messages of each history are bounded.
 * The model is played by the test, which answers each turn with the whole conversation as the text-generation pipeline
 * does.
 */
class ChatHistoryTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private DefaultCamelContext context;

    @BeforeEach
    void setUp() {
        context = new DefaultCamelContext();
        context.start();
    }

    @AfterEach
    void tearDown() {
        context.stop();
    }

    @Test
    void historiesAreBoundedByDefault() throws Exception {
        ChatPredictor predictor = new ChatPredictor(new HuggingFaceEndpoint(null, null, new HuggingFaceConfiguration()));
        for (int i = 0; i <= 1000; i++) {
            turn(predictor, "user-" + i, "Hello", "Hi");
        }

        assertEquals(List.of("user:Hello again"), turn(predictor, "user-0", "Hello again", "Hi"),
                "the history of the least recently used memory ID must be dropped");
        assertEquals(List.of("user:Hello", "assistant:Hi", "user:Hello again"),
                turn(predictor, "user-1000", "Hello again", "Hi"),
                "the history of a recently used memory ID must be kept");
    }

    @Test
    void maxChatHistoriesBoundsTheHistories() throws Exception {
        ChatPredictor predictor = predictor("maxChatHistories=2");
        turn(predictor, "alice", "Hello", "Hi alice");
        turn(predictor, "bob", "Hello", "Hi bob");
        turn(predictor, "carol", "Hello", "Hi carol");

        assertEquals(List.of("user:Hello again"), turn(predictor, "alice", "Hello again", "Hi"));
        assertEquals(List.of("user:Hello", "assistant:Hi carol", "user:Hello again"),
                turn(predictor, "carol", "Hello again", "Hi"));
    }

    @Test
    void zeroMaxChatHistoriesKeepsEveryHistory() throws Exception {
        ChatPredictor predictor = predictor("maxChatHistories=0");
        turn(predictor, "alice", "Hello", "Hi alice");
        turn(predictor, "bob", "Hello", "Hi bob");
        turn(predictor, "carol", "Hello", "Hi carol");

        assertEquals(List.of("user:Hello", "assistant:Hi alice", "user:Hello again"),
                turn(predictor, "alice", "Hello again", "Hi"));
    }

    @Test
    void maxHistoryMessagesKeepsTheSystemPromptAndTheLatestMessagesFromAUserMessage() throws Exception {
        ChatPredictor predictor = predictor("systemPrompt=Brief&maxHistoryMessages=3");
        turn(predictor, "alice", "One", "Reply one");
        turn(predictor, "alice", "Two", "Reply two");

        // the history had 4 messages besides the system prompt: the last 3 start with a reply, so 2 are kept
        assertEquals(List.of("system:Brief", "user:Two", "assistant:Reply two", "user:Three"),
                turn(predictor, "alice", "Three", "Reply three"));
    }

    @Test
    void shortHistoryIsKeptWholeByDefault() throws Exception {
        ChatPredictor predictor = predictor("systemPrompt=Brief");
        turn(predictor, "alice", "One", "Reply one");
        turn(predictor, "alice", "Two", "Reply two");

        assertEquals(List.of("system:Brief", "user:One", "assistant:Reply one", "user:Two", "assistant:Reply two",
                "user:Three"), turn(predictor, "alice", "Three", "Reply three"));
    }

    @Test
    void historyKeepsTheLastFiftyMessagesByDefault() throws Exception {
        ChatPredictor predictor = new ChatPredictor(new HuggingFaceEndpoint(null, null, new HuggingFaceConfiguration()));
        for (int i = 1; i <= 26; i++) {
            turn(predictor, "alice", "Turn " + i, "Reply " + i);
        }

        // 26 turns are 52 messages: the first turn is dropped, the next 25 turns are kept
        List<String> sent = turn(predictor, "alice", "Turn 27", "Reply 27");
        assertEquals(51, sent.size());
        assertEquals("user:Turn 2", sent.get(0));
        assertEquals("user:Turn 27", sent.get(50));
    }

    @Test
    void zeroMaxHistoryMessagesKeepsEveryMessage() throws Exception {
        ChatPredictor predictor = predictor("maxHistoryMessages=0");
        for (int i = 1; i <= 26; i++) {
            turn(predictor, "alice", "Turn " + i, "Reply " + i);
        }

        List<String> sent = turn(predictor, "alice", "Turn 27", "Reply 27");
        assertEquals(53, sent.size());
        assertEquals("user:Turn 1", sent.get(0));
    }

    @Test
    void maxHistoryMessagesOfTwoKeepsTheLastTurn() throws Exception {
        ChatPredictor predictor = predictor("maxHistoryMessages=2");
        turn(predictor, "alice", "One", "Reply one");
        turn(predictor, "alice", "Two", "Reply two");

        assertEquals(List.of("user:Two", "assistant:Reply two", "user:Three"),
                turn(predictor, "alice", "Three", "Reply three"));
    }

    @Test
    void maxHistoryMessagesBelowTwoIsRejected() {
        // a history trimmed to 1 message would keep no reply, as the kept messages start with a user message
        IllegalArgumentException one
                = assertThrows(IllegalArgumentException.class, () -> predictor("maxHistoryMessages=1"));
        assertTrue(one.getMessage().contains("maxHistoryMessages"), one.getMessage());
        IllegalArgumentException negative
                = assertThrows(IllegalArgumentException.class, () -> predictor("maxHistoryMessages=-1"));
        assertTrue(negative.getMessage().contains("maxHistoryMessages"), negative.getMessage());
    }

    private ChatPredictor predictor(String options) {
        return new ChatPredictor(
                context.getEndpoint("huggingface:chat?modelId=test&" + options, HuggingFaceEndpoint.class));
    }

    /**
     * Runs one turn of the conversation of the memory ID, and returns the messages that were sent to the model as
     * role:content.
     */
    private List<String> turn(ChatPredictor predictor, String memoryId, String message, String reply) throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader("CamelChatMemoryId", memoryId);
        exchange.getIn().setBody(message);
        Input input = predictor.prepareInput(exchange);

        JsonNode sent = mapper.readTree(input.getAsString("data"));
        ArrayNode conversation = sent.deepCopy();
        conversation.addObject().put("role", "assistant").put("content", reply);
        ArrayNode result = mapper.createArrayNode();
        result.addObject().set("generated_text", conversation);
        Output output = new Output();
        output.add("data", result.toString());
        predictor.processOutput(exchange, output);
        assertEquals(reply, exchange.getMessage().getBody(String.class));

        List<String> messages = new ArrayList<>();
        for (JsonNode node : sent) {
            messages.add(node.get("role").asText() + ":" + node.get("content").asText());
        }
        return messages;
    }
}
