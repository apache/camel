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

import jakarta.jms.ConnectionFactory;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentTransacted;

public class JmsTransactedRouteTest extends AbstractJMSTest {

    @Test
    public void testJmsRouteWithTextMessage() throws Exception {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        String expectedBody = "Hello there!";
        String expectedBody2 = "Goodbye!";

        resultEndpoint.expectedBodiesReceived(expectedBody, expectedBody2);
        resultEndpoint.message(0).header("cheese").isEqualTo(123);

        template.sendBodyAndHeader("activemq:test.a.JmsTransactedRouteTest", expectedBody, "cheese", 123);
        template.sendBodyAndHeader("activemq:test.a.JmsTransactedRouteTest", expectedBody2, "cheese", 124);

        resultEndpoint.assertIsSatisfied();
    }

    @Override
    protected String getComponentName() {
        return "activemq";
    }

    @Override
    protected JmsComponent buildComponent(ConnectionFactory connectionFactory) {
        return jmsComponentTransacted(connectionFactory);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("activemq:test.a.JmsTransactedRouteTest").to("activemq:test.b.JmsTransactedRouteTest");
                from("activemq:test.b.JmsTransactedRouteTest").to("log:result", "mock:result");
            }
        };
    }
}
