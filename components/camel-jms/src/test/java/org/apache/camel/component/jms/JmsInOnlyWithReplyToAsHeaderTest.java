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

public class JmsInOnlyWithReplyToAsHeaderTest extends AbstractJMSTest {

    @Test
    public void testSendInOnlyWithReplyTo() throws Exception {
        getMockEndpoint("mock:foo").expectedBodiesReceived("World");
        getMockEndpoint("mock:bar").expectedBodiesReceived("Bye World");
        getMockEndpoint("mock:done").expectedBodiesReceived("World");

        template.sendBodyAndHeader("direct:start", "World", "JMSReplyTo", "queue:barJmsInOnlyWithReplyToAsHeaderTest");

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
                        // must enable preserveMessageQos to force Camel to use the JMSReplyTo header
                        .to("activemq:queue:fooJmsInOnlyWithReplyToAsHeaderTest?preserveMessageQos=true")
                        .to("mock:done");

                from("activemq:queue:fooJmsInOnlyWithReplyToAsHeaderTest")
                        .to("log:foo?showAll=true", "mock:foo")
                        .transform(body().prepend("Bye "));

                // we should disable reply to avoid sending the message back to our self
                // after we have consumed it
                from("activemq:queue:barJmsInOnlyWithReplyToAsHeaderTest?disableReplyTo=true")
                        .to("log:bar?showAll=true", "mock:bar");
            }
        };
    }
}
