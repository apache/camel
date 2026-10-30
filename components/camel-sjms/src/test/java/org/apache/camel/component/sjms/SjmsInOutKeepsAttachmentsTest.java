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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.sjms.support.JmsTestSupport;
import org.apache.camel.trait.message.MessageTrait;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The attachments of an exchange are kept when an InOut sjms request is answered, as they are when another producer
 * creates a fresh OUT message with exchange.getOut(). The attachments are the {@link MessageTrait#ATTACHMENTS} payload
 * of the message, which is where camel-attachments (AttachmentMessage) keeps them.
 */
class SjmsInOutKeepsAttachmentsTest extends JmsTestSupport {

    @Test
    void replyKeepsTheAttachmentsOfTheRequest() {
        Exchange exchange = template.request("direct:start", e -> {
            e.getIn().setBody("World");
            Map<String, Object> attachments = new LinkedHashMap<>();
            attachments.put("hello.txt", "Hello attachment");
            e.getIn().setPayloadForTrait(MessageTrait.ATTACHMENTS, attachments);
        });

        assertNull(exchange.getException());
        assertEquals("Bye World", exchange.getMessage().getBody(String.class));
        Map<String, Object> attachments = attachmentsOf(exchange);
        assertNotNull(attachments, "the reply must keep the attachments of the exchange");
        assertEquals("Hello attachment", attachments.get("hello.txt"));
    }

    @Test
    void replyToARequestWithoutAttachmentsHasNone() {
        Exchange exchange = template.request("direct:start", e -> e.getIn().setBody("World"));

        assertNull(exchange.getException());
        assertEquals("Bye World", exchange.getMessage().getBody(String.class));
        assertNull(attachmentsOf(exchange));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> attachmentsOf(Exchange exchange) {
        return (Map<String, Object>) exchange.getMessage().getPayloadForTrait(MessageTrait.ATTACHMENTS);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .to("sjms:queue:SjmsInOutKeepsAttachmentsTest?exchangePattern=InOut");

                from("sjms:queue:SjmsInOutKeepsAttachmentsTest")
                        .transform(body().prepend("Bye "));
            }
        };
    }
}
