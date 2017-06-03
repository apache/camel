/**
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

import javax.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Before;
import org.junit.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

/**
 * @version 
 */
public class JmsRouteTest extends CamelTestSupport {
    protected MockEndpoint resultEndpoint;
    protected String componentName = "activemq";
    protected String startEndpointUri;

    @Test
    public void testSendAndReceiveMessage() throws Exception {
        assertSendAndReceiveBody("Hello there!");
    }

    @Test
    public void testSendEmptyMessage() throws Exception {
        resultEndpoint.expectedMessageCount(2);

        sendExchange("");
        sendExchange(null);

        resultEndpoint.assertIsSatisfied();
    }

    protected void assertSendAndReceiveBody(Object expectedBody) throws InterruptedException {
        resultEndpoint.expectedBodiesReceived(expectedBody);
        resultEndpoint.message(0).header("cheese").isEqualTo(123);

        sendExchange(expectedBody);

        resultEndpoint.assertIsSatisfied();
    }

    protected void sendExchange(final Object expectedBody) {
        template.sendBodyAndHeader(startEndpointUri, expectedBody, "cheese", 123);
    }


    @Override
    @Before
    public void setUp() throws Exception {
        startEndpointUri = componentName + ":queue:test.a";

        super.setUp();

        resultEndpoint = context.getEndpoint("mock:result", MockEndpoint.class);
    }

    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        camelContext.addComponent(componentName, jmsComponentAutoAcknowledge(connectionFactory));

        return camelContext;
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(startEndpointUri).to(componentName + ":queue:test.b");
                from(componentName + ":queue:test.b").to("mock:result");
            }
        };
    }
}
