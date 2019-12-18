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

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.telegram.model.OutgoingTextMessage;
import org.apache.camel.component.telegram.util.TelegramMockRoutes;
import org.apache.camel.component.telegram.util.TelegramTestSupport;
import org.apache.camel.component.telegram.util.TelegramTestUtil;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.assertCollectionSize;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Checks if conversions of generic objects are happening correctly.
 */
public class TelegramConsumerFallbackConversionTest extends TelegramTestSupport {

    @EndpointInject("direct:message")
    protected ProducerTemplate template;

    @Test
    public void testEverythingOk() throws Exception {

        template.sendBody(new BrandNewType("wrapped message"));

        List<OutgoingTextMessage> msgs = Awaitility.await().atMost(5, TimeUnit.SECONDS)
                .until(() -> getMockRoutes().getMock("sendMessage").getRecordedMessages(), rawMessages -> rawMessages.size() == 1)
                .stream()
                .map(message -> (OutgoingTextMessage) message)
                .collect(Collectors.toList());

        assertCollectionSize(msgs, 1);
        String text = msgs.get(0).getText();
        assertEquals("wrapped message", text);
    }

    @Override
    protected RoutesBuilder[] createRouteBuilders() throws Exception {
        return new RoutesBuilder[] {
            getMockRoutes(),
            new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from("direct:message")
                            .to("telegram:bots?authorizationToken=mock-token&chatId=1234");
                }
            }};
    }

    @Override
    protected TelegramMockRoutes createMockRoutes() {
        return new TelegramMockRoutes(port)
                .addEndpoint(
                        "sendMessage",
                        "POST",
                        OutgoingTextMessage.class,
                        TelegramTestUtil.stringResource("messages/send-message.json"),
                        TelegramTestUtil.stringResource("messages/send-message.json"),
                        TelegramTestUtil.stringResource("messages/send-message.json"));
    }

    private static class BrandNewType {

        String message;

        BrandNewType(String message) {
            this.message = message;
        }

        @Override
        public String toString() {
            // to use default conversion from Object to String
            return message;
        }
    }

}
