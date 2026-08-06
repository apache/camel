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
package org.apache.camel.attachment;

import jakarta.activation.DataHandler;

import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test that attachments on the IN message survive when a producer creates a fresh OUT message via exchange.getOut().
 * This is a regression test for CAMEL-24365.
 */
class AttachmentOnOutMessageTest extends CamelTestSupport {

    @Test
    void testAttachmentSurvivesOutMessage() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello");

        MockEndpoint.assertIsSatisfied(context);

        Exchange received = getMockEndpoint("mock:result").getReceivedExchanges().get(0);
        AttachmentMessage am = received.getMessage(AttachmentMessage.class);
        assertTrue(am.hasAttachments());
        assertEquals(1, am.getAttachmentNames().size());
        assertTrue(am.getAttachmentNames().contains("test.txt"));
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .process(exchange -> {
                            AttachmentMessage msg = exchange.getMessage(AttachmentMessage.class);
                            msg.addAttachment("test.txt", new DataHandler("content", "text/plain"));
                        })
                        .process(exchange -> {
                            // simulate what HttpProducer does: create a fresh OUT message
                            exchange.getOut().setBody("response");
                        })
                        .process(exchange -> {
                            // after pipeline promotes OUT to IN, attachments must still be present
                            AttachmentMessage msg = exchange.getMessage(AttachmentMessage.class);
                            exchange.getMessage().setHeader("attachmentCount", msg.getAttachmentNames().size());
                        })
                        .to("mock:result");
            }
        };
    }
}
