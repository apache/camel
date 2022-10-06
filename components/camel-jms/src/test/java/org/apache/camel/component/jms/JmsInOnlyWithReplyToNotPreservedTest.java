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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;

public class JmsInOnlyWithReplyToNotPreservedTest extends AbstractJMSTest {

    @Test
    public void testSendInOnlyWithReplyTo() throws Exception {
        getMockEndpoint("mock:JmsInOnlyWithReplyToNotPreservedTest.Request").expectedBodiesReceived("World");
        getMockEndpoint("mock:done").expectedBodiesReceived("World");

        template.sendBody("direct:JmsInOnlyWithReplyToNotPreservedTest", "World");

        MockEndpoint.assertIsSatisfied(context);

        // there should be no messages on the JmsInOnlyWithReplyToNotPreservedTest.Reply queue
        Object msg = consumer.receiveBody("activemq:queue:JmsInOnlyWithReplyToNotPreservedTest.Reply", 1000);
        assertNull(msg, "Should be no message on JmsInOnlyWithReplyToNotPreservedTest.Reply queue");
    }

    @Override
    protected String getComponentName() {
        return "activemq";
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:JmsInOnlyWithReplyToNotPreservedTest")
                        .to("activemq:queue:JmsInOnlyWithReplyToNotPreservedTest.Request?replyTo=queue:JmsInOnlyWithReplyToNotPreservedTest.Reply")
                        .to("mock:done");

                from("activemq:queue:JmsInOnlyWithReplyToNotPreservedTest.Request")
                        .to("log:JmsInOnlyWithReplyToNotPreservedTest.Request?showAll=true",
                                "mock:JmsInOnlyWithReplyToNotPreservedTest.Request")
                        .transform(body().prepend("Bye "));
            }
        };
    }
}
