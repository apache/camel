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
package org.apache.camel.component.amqp;


import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.qpid.client.transport.TransportConnection;

import static org.apache.camel.component.amqp.AMQPComponent.amqpComponent;

/**
 * @version $Revision$
 */
public class AMQPRouteTest extends ContextTestSupport {
    protected MockEndpoint resultEndpoint;

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
        template.sendBodyAndHeader("amqp:queue:test.a", expectedBody, "cheese", 123);
    }


    @Override
    protected void setUp() throws Exception {
        // lets create an in JVM broker
        TransportConnection.createVMBroker(1);

        super.setUp();

        resultEndpoint = (MockEndpoint) context.getEndpoint("mock:result");
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        TransportConnection.killVMBroker(1);
    }

    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        camelContext.addComponent("amqp", amqpComponent("amqp://guest:guest@/test?brokerlist='vm://:1'"));

        return camelContext;
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("amqp:test.a").to("amqp:test.b");
                from("amqp:test.b").to("mock:result");
            }
        };
    }
}