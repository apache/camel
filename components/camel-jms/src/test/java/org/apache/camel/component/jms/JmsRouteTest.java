/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.jms;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import static org.apache.camel.component.jms.JmsComponent.jmsComponentClientAcknowledge;
import org.apache.camel.component.mock.MockEndpoint;

import javax.jms.ConnectionFactory;

/**
 * @version $Revision$
 */
public class JmsRouteTest extends ContextTestSupport {
    protected MockEndpoint resultEndpoint;
    protected String componentName = "activemq";
    protected String startEndpointUri;

    public void testJmsRouteWithTextMessage() throws Exception {
        String expectedBody = "Hello there!";

        resultEndpoint.expectedBodiesReceived(expectedBody);
        resultEndpoint.message(0).header("cheese").isEqualTo(123);

        sendExchange(expectedBody);

        resultEndpoint.assertIsSatisfied();
    }

    public void testJmsRouteWithObjectMessage() throws Exception {
        PurchaseOrder expectedBody = new PurchaseOrder("Beer", 10);

        resultEndpoint.expectedBodiesReceived(expectedBody);
        resultEndpoint.message(0).header("cheese").isEqualTo(123);

        sendExchange(expectedBody);

        resultEndpoint.assertIsSatisfied();
    }

    protected void sendExchange(final Object expectedBody) {
        template.sendBody(startEndpointUri, expectedBody, "cheese", 123);
    }


    @Override
    protected void setUp() throws Exception {
        startEndpointUri = componentName + ":queue:test.a";

        super.setUp();

        resultEndpoint = (MockEndpoint) context.getEndpoint("mock:result");
    }

    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://localhost?broker.persistent=false");
        camelContext.addComponent(componentName, jmsComponentClientAcknowledge(connectionFactory));

        return camelContext;
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(startEndpointUri).to(componentName + ":queue:test.b");
                from(componentName + ":queue:test.b").to("mock:result");

                JmsEndpoint endpoint1 = (JmsEndpoint) endpoint(componentName + ":topic:quote.IONA");
                endpoint1.getConfiguration().setTransacted(true);
                from(endpoint1).to("mock:transactedClient");

                JmsEndpoint endpoint2 = (JmsEndpoint) endpoint(componentName + ":topic:quote.IONA");
                endpoint1.getConfiguration().setTransacted(true);
                from(endpoint2).to("mock:nonTrasnactedClient");
            }
        };
    }
}
