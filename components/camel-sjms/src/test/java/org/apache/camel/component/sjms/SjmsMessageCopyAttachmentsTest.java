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
package org.apache.camel.component.sjms;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.sjms.support.JmsTestSupport;
import org.apache.camel.trait.message.MessageTrait;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Attachments of a message received from sjms (an {@link SjmsMessage}) are kept when the exchange is copied, such as by
 * the Multicast EIP. The attachments are the {@link MessageTrait#ATTACHMENTS} payload of the message, which is where
 * camel-attachments (AttachmentMessage) keeps them.
 */
class SjmsMessageCopyAttachmentsTest extends JmsTestSupport {

    @Test
    void multicastCopyKeepsTheAttachmentsOfAnSjmsMessage() throws Exception {
        MockEndpoint copy = getMockEndpoint("mock:copy");
        copy.expectedMessageCount(1);

        template.sendBody("sjms:SjmsMessageCopyAttachmentsTest", "Hello World");

        MockEndpoint.assertIsSatisfied(context);
        Message received = copy.getReceivedExchanges().get(0).getMessage();
        assertInstanceOf(SjmsMessage.class, received);
        Map<String, Object> attachments = attachmentsOf(received);
        assertNotNull(attachments, "the copy made by the multicast must keep the attachments");
        assertEquals("Hello attachment", attachments.get("hello.txt"));
    }

    @Test
    void copyKeepsTheAttachments() {
        Exchange exchange = context.getEndpoint("sjms:SjmsMessageCopyAttachmentsTest").createExchange();
        SjmsMessage message = new SjmsMessage(exchange, null, null, null);
        message.setBody("Hello World");
        Map<String, Object> attachments = new LinkedHashMap<>();
        attachments.put("hello.txt", "Hello attachment");
        message.setPayloadForTrait(MessageTrait.ATTACHMENTS, attachments);

        Message copy = message.copy();

        Map<String, Object> copied = attachmentsOf(copy);
        assertNotNull(copied, "copy() must keep the attachments");
        assertEquals(attachments, copied);
        assertNotSame(attachments, copied, "the copy has its own attachment map");
    }

    @Test
    void copyOfAMessageWithoutAttachmentsHasNone() {
        Exchange exchange = context.getEndpoint("sjms:SjmsMessageCopyAttachmentsTest").createExchange();
        SjmsMessage message = new SjmsMessage(exchange, null, null, null);
        message.setBody("Hello World");

        assertNull(attachmentsOf(message.copy()));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> attachmentsOf(Message message) {
        return (Map<String, Object>) message.getPayloadForTrait(MessageTrait.ATTACHMENTS);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("sjms:SjmsMessageCopyAttachmentsTest")
                        .process(e -> {
                            Map<String, Object> attachments = new LinkedHashMap<>();
                            attachments.put("hello.txt", "Hello attachment");
                            e.getMessage().setPayloadForTrait(MessageTrait.ATTACHMENTS, attachments);
                        })
                        .multicast().to("mock:copy");
            }
        };
    }
}
