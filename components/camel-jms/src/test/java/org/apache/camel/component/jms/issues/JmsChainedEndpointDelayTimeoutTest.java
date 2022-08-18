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
package org.apache.camel.component.jms.issues;

import org.apache.camel.ExchangePattern;
import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.AbstractJMSTest;
import org.junit.jupiter.api.Test;

/**
 * Unit test to assert that timeouts don't trigger twice when JMS InOut endpoints are chained, and the second endpoint
 * takes longer to respond than the timeout set for the first endpoint.
 */
public class JmsChainedEndpointDelayTimeoutTest extends AbstractJMSTest {

    @Test
    public void testTimeoutNotTriggeredTempQueue() throws Exception {
        getMockEndpoint("mock:exception").expectedMessageCount(0);
        getMockEndpoint("mock:ping").expectedMessageCount(1);
        template.requestBody("activemq:JmsChainedEndpointDelayTimeoutTest.test", "<hello />");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testTimeoutNotTriggeredFixedQueue() throws Exception {
        getMockEndpoint("mock:exception").expectedMessageCount(0);
        getMockEndpoint("mock:ping").expectedMessageCount(1);
        template.requestBody("activemq:JmsChainedEndpointDelayTimeoutTest.fixed", "<hello />");
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

                onException(ExchangeTimedOutException.class)
                        .handled(true)
                        .to("mock:exception");

                from("activemq:JmsChainedEndpointDelayTimeoutTest.test")
                        .to(ExchangePattern.InOut, "activemq:JmsChainedEndpointDelayTimeoutTest.ping?requestTimeout=500")
                        .delay(constant(1000));

                from("activemq:JmsChainedEndpointDelayTimeoutTest.fixed")
                        .to(ExchangePattern.InOut,
                                "activemq:JmsChainedEndpointDelayTimeoutTest.ping?requestTimeout=500&replyToType=Exclusive&replyTo=JmsChainedEndpointDelayTimeoutTest.reply")
                        .delay(constant(1000));

                from("activemq:JmsChainedEndpointDelayTimeoutTest.ping")
                        .to("mock:ping")
                        .log("pong");

            }
        };
    }
}
