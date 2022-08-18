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
import org.junit.jupiter.api.Test;

public class JmsInOnlyWithReplyToDisabledButJMSReplyToHeaderPreservedTest extends AbstractJMSTest {

    @Test
    public void testJMSReplyToHeaderPreserved() throws Exception {
        getMockEndpoint("mock:foo").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:foo").expectedHeaderReceived("JMSReplyTo", "queue://bar");
        getMockEndpoint("mock:done").expectedBodiesReceived("Hello World");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
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
                from("direct:start")
                        // must use preserveMessageQos to include JMSReplyTo
                        .to("activemq:queue:JmsInOnlyWithReplyToDisabledButJMSReplyToHeaderPreservedTest?replyTo=queue:bar&preserveMessageQos=true")
                        .to("mock:done");

                // and disable reply to as we do not want to send back a reply message in this route
                from("activemq:queue:JmsInOnlyWithReplyToDisabledButJMSReplyToHeaderPreservedTest?disableReplyTo=true")
                        .to("log:foo?showAll=true", "mock:foo");
            }
        };
    }
}
