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
package org.apache.camel.component.jms;

import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A simple request / reply test
 */
public class JmsSimpleRequestReplyFixedReplyQueueTest extends AbstractJMSTest {

    protected final String componentName = "activemq";

    @Test
    public void testWithInOnly() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");

        // send an InOnly
        template.sendBody("direct:start", "World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testWithInOut() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");

        // send an InOut
        String out = template.requestBody("direct:start", "World", String.class);
        assertEquals("Hello World", out);

        assertMockEndpointsSatisfied();
    }

    @Override
    public String getComponentName() {
        return componentName;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .to(ExchangePattern.InOut,
                                "activemq:queue:JmsSimpleRequestReplyFixedReplyQueueTest?replyTo=queue:JmsSimpleRequestReplyFixedReplyQueueTest.reply")
                        .to("mock:result");

                from("activemq:queue:JmsSimpleRequestReplyFixedReplyQueueTest")
                        .transform(body().prepend("Hello "));
            }
        };
    }
}
