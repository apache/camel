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
package org.apache.camel.component.telegram;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.telegram.model.OutgoingTextMessage;
import org.apache.camel.component.telegram.util.TelegramMockRoutes;
import org.apache.camel.component.telegram.util.TelegramMockRoutes.MockProcessor;
import org.apache.camel.component.telegram.util.TelegramTestSupport;
import org.apache.camel.component.telegram.util.TelegramTestUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Sends from several threads at once through one endpoint: each request carries the message of its own send only.
 */
public class TelegramProducerConcurrentTest extends TelegramTestSupport {

    private static final int MESSAGES = 100;

    @Test
    public void testConcurrentSends() throws Exception {
        final MockProcessor<String> mockProcessor = getMockRoutes().getMock("sendMessage");
        mockProcessor.clearRecordedMessages();

        ExecutorService executor = Executors.newFixedThreadPool(10);
        try {
            List<Future<?>> sends = new ArrayList<>();
            for (int i = 0; i < MESSAGES; i++) {
                final int n = i;
                sends.add(executor.submit(() -> template.sendBodyAndHeader("direct:telegram", "msg-" + n,
                        TelegramConstants.TELEGRAM_CHAT_ID, "chat-" + n)));
            }
            for (Future<?> send : sends) {
                send.get(30, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        // each request is the JSON of one message, sent to the chat of that message
        ObjectMapper mapper = new ObjectMapper();
        Set<String> texts = new HashSet<>();
        for (String request : mockProcessor.awaitRecordedMessages(MESSAGES, 10000)) {
            OutgoingTextMessage message = mapper.readValue(request, OutgoingTextMessage.class);
            assertEquals(message.getText().replace("msg-", "chat-"), message.getChatId(), request);
            texts.add(message.getText());
        }
        assertEquals(MESSAGES, texts.size());
    }

    @Override
    protected RoutesBuilder[] createRouteBuilders() {
        return new RoutesBuilder[] {
                getMockRoutes(),
                new RouteBuilder() {
                    @Override
                    public void configure() {
                        from("direct:telegram")
                                .to("telegram:bots?authorizationToken=mock-token");
                    }
                } };
    }

    @Override
    protected TelegramMockRoutes createMockRoutes() {
        return new TelegramMockRoutes(port.getPort())
                .addEndpoint(
                        "sendMessage",
                        "POST",
                        String.class,
                        TelegramTestUtil.stringResource("messages/send-message.json"));
    }
}
