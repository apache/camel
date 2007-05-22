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
package org.apache.camel.component.activemq;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import static org.apache.camel.component.activemq.ActiveMQComponent.activeMQComponent;
import org.apache.camel.component.jms.JmsEndpoint;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version $Revision: 538973 $
 */
public class ActiveMQRouteTest extends ContextTestSupport {
    protected MockEndpoint resultEndpoint;
    protected String startEndpointUri = "activemq:queue:test.a";

    public void testJmsRouteWithTextMessage() throws Exception {
        String expectedBody = "Hello there!";

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
        super.setUp();

        resultEndpoint = (MockEndpoint) context.getEndpoint("mock:result");
    }

    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        // START SNIPPET: example
        camelContext.addComponent("activemq", activeMQComponent("vm://localhost?broker.persistent=false"));
        // END SNIPPET: example

        return camelContext;
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(startEndpointUri).to("activemq:queue:test.b");
                from("activemq:queue:test.b").to("mock:result");

                JmsEndpoint endpoint1 = (JmsEndpoint) endpoint("activemq:topic:quote.IONA");
                endpoint1.getConfiguration().setTransacted(true);
                from(endpoint1).to("mock:transactedClient");

                JmsEndpoint endpoint2 = (JmsEndpoint) endpoint("activemq:topic:quote.IONA");
                endpoint1.getConfiguration().setTransacted(true);
                from(endpoint2).to("mock:nonTrasnactedClient");
            }
        };
    }
}