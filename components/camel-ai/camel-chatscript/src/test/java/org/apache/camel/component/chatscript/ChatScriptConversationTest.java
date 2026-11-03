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
package org.apache.camel.component.chatscript;

import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Talks to a fake ChatScript server and checks the messages the component sends on the wire.
 */
class ChatScriptConversationTest {

    private FakeChatScriptServer server;
    private CamelContext context;

    @BeforeEach
    void setUp() throws Exception {
        server = new FakeChatScriptServer();
        context = new DefaultCamelContext();
        context.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        context.stop();
        server.close();
    }

    @Test
    void firstMessageStartsTheConversationAndIsThenSent() throws Exception {
        Producer producer = producer("");

        Exchange exchange = process(producer, "{\"username\": \"bob\", \"message\": \"Hello\"}");

        assertEquals(List.of("bob", "Harry", ""), server.nextMessage(),
                "the conversation must start with an empty message");
        assertEquals(List.of("bob", "Harry", "Hello"), server.nextMessage(),
                "the first message must be sent after the conversation started");
        assertTrue(server.hasNoMoreMessages());
        assertEquals("You said Hello", exchange.getMessage().getBody(ChatScriptMessage.class).getReply());
    }

    @Test
    void nextMessagesAreSentOnce() throws Exception {
        Producer producer = producer("");

        process(producer, new ChatScriptMessage("bob", null, "Hello"));
        Exchange exchange = process(producer, new ChatScriptMessage("bob", null, "How are you"));

        assertEquals(List.of("bob", "Harry", ""), server.nextMessage());
        assertEquals(List.of("bob", "Harry", "Hello"), server.nextMessage());
        assertEquals(List.of("bob", "Harry", "How are you"), server.nextMessage());
        assertTrue(server.hasNoMoreMessages());
        assertEquals("You said How are you", exchange.getMessage().getBody(ChatScriptMessage.class).getReply());
    }

    @Test
    void emptyFirstMessageStartsTheConversationAndReturnsTheGreeting() throws Exception {
        Producer producer = producer("");

        Exchange exchange = process(producer, "{\"username\": \"bob\", \"message\": \"\"}");

        assertEquals(List.of("bob", "Harry", ""), server.nextMessage());
        assertTrue(server.hasNoMoreMessages(), "the empty message must be sent once");
        assertEquals("Welcome bob", exchange.getMessage().getBody(ChatScriptMessage.class).getReply());
    }

    @Test
    void resetChatResetsTheConversationOfTheUserOfTheMessage() throws Exception {
        Producer producer = producer("?resetChat=true");

        process(producer, "{\"username\": \"bob\", \"message\": \"Hello\"}");
        Exchange exchange = process(producer, "{\"username\": \"bob\", \"message\": \"Hello again\"}");

        assertEquals(List.of("bob", "Harry", ":reset"), server.nextMessage(),
                "the conversation of the user of the message must be reset");
        assertEquals(List.of("bob", "Harry", ""), server.nextMessage());
        assertEquals(List.of("bob", "Harry", "Hello"), server.nextMessage());
        assertEquals(List.of("bob", "Harry", ":reset"), server.nextMessage());
        assertEquals(List.of("bob", "Harry", ""), server.nextMessage());
        assertEquals(List.of("bob", "Harry", "Hello again"), server.nextMessage());
        assertTrue(server.hasNoMoreMessages());
        assertEquals("You said Hello again", exchange.getMessage().getBody(ChatScriptMessage.class).getReply());
    }

    @Test
    void resetChatResetsTheChatUserNameWhenTheMessageHasNoUser() throws Exception {
        Producer producer = producer("?resetChat=true&chatUserName=alice");

        Exchange exchange = process(producer, "{\"message\": \"Hello\"}");

        assertEquals(List.of("alice", "Harry", ":reset"), server.nextMessage());
        assertEquals(List.of("alice", "Harry", ""), server.nextMessage());
        assertEquals(List.of("alice", "Harry", "Hello"), server.nextMessage());
        assertTrue(server.hasNoMoreMessages());
        assertEquals("You said Hello", exchange.getMessage().getBody(ChatScriptMessage.class).getReply());
    }

    @Test
    void headersOfTheMessageAreKept() throws Exception {
        Producer producer = producer("");
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader("user", "bob");
        exchange.getIn().setHeader("conversation", 42);
        exchange.getIn().setBody("{\"username\": \"bob\", \"message\": \"Hello\"}");

        producer.process(exchange);

        assertEquals("bob", exchange.getMessage().getHeader("user"));
        assertEquals(42, exchange.getMessage().getHeader("conversation"));
        assertEquals("You said Hello", exchange.getMessage().getBody(ChatScriptMessage.class).getReply());
    }

    @Test
    void sendChatWithTextStartsTheConversationOfTheBotUser() throws Exception {
        ChatScriptBot bot = new ChatScriptBot(FakeChatScriptServer.HOST, server.getPort(), "Harry", "bob");

        assertEquals("You said Hello", bot.sendChat("Hello"));
        assertEquals("You said Bye", bot.sendChat("Bye"));

        assertEquals(List.of("bob", "Harry", ""), server.nextMessage());
        assertEquals(List.of("bob", "Harry", "Hello"), server.nextMessage());
        assertEquals(List.of("bob", "Harry", "Bye"), server.nextMessage());
        assertTrue(server.hasNoMoreMessages());
    }

    @Test
    void missingFieldsAreSentAsEmptyText() {
        assertEquals("bob\0Harry\0\0", new ChatScriptMessage("bob", "Harry", null).toCSFormat());
        assertEquals("\0Harry\0Hello\0", new ChatScriptMessage(null, "Harry", "Hello").toCSFormat());
    }

    private Producer producer(String options) throws Exception {
        ChatScriptEndpoint endpoint = context.getEndpoint(
                "chatscript:" + FakeChatScriptServer.HOST + ":" + server.getPort() + "/Harry" + options,
                ChatScriptEndpoint.class);
        return endpoint.createProducer();
    }

    private Exchange process(Producer producer, Object body) throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(body);
        producer.process(exchange);
        return exchange;
    }
}
