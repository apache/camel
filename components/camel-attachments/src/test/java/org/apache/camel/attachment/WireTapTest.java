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

import java.io.File;
import java.util.Iterator;

import jakarta.activation.DataHandler;
import jakarta.activation.FileDataSource;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class WireTapTest extends CamelTestSupport {

    @Test
    public void testWireTap() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock:tap").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        MockEndpoint.assertIsSatisfied(context);

        Exchange e1 = getMockEndpoint("mock:result").getReceivedExchanges().get(0);
        Exchange e2 = getMockEndpoint("mock:tap").getReceivedExchanges().get(0);

        AttachmentMessage am1 = e1.getMessage(AttachmentMessage.class);
        AttachmentMessage am2 = e2.getMessage(AttachmentMessage.class);

        // original has 1 attachment
        Assertions.assertTrue(am1.hasAttachments());
        Assertions.assertEquals(1, am1.getAttachmentNames().size());
        Assertions.assertEquals("message1.xml", am1.getAttachmentNames().iterator().next());

        // tap has 2 because of 1 original and 1 added afterwards
        Assertions.assertTrue(am2.hasAttachments());
        Assertions.assertEquals(2, am2.getAttachmentNames().size());
        Iterator<String> it = am2.getAttachmentNames().iterator();
        Assertions.assertEquals("message1.xml", it.next());
        Assertions.assertEquals("message2.xml", it.next());
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) {
                                AttachmentMessage am = exchange.getMessage(AttachmentMessage.class);
                                am.addAttachment("message1.xml",
                                        new DataHandler(new FileDataSource(new File("src/test/data/message1.xml"))));
                            }
                        })
                        .wireTap("direct:tap")
                        .to("mock:result");

                from("direct:tap")
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) {
                                AttachmentMessage am = exchange.getMessage(AttachmentMessage.class);
                                am.addAttachmentObject("message2.xml",
                                        new DefaultAttachment(new FileDataSource(new File("src/test/data/message2.xml"))));
                            }
                        }).to("mock:tap");
            }
        };
    }
}
