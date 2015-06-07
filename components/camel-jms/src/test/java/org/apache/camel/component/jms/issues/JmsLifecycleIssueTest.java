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
package org.apache.camel.component.jms.issues;

import javax.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.CamelJmsTestHelper;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import static org.apache.camel.ServiceStatus.Started;
import static org.apache.camel.ServiceStatus.Stopped;
import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

public class JmsLifecycleIssueTest extends CamelTestSupport {

    public static final String ROUTE_ID = "simpleRoute";
    public static final String ENDPOINT_URI = "activemq:processOrder";

    @Test
    public void routeThatIsStoppedAndThenResumedAcceptsMessage() throws Exception {
        assertThatRouteIs(Stopped);

        context.resumeRoute(ROUTE_ID);

        assertRouteWorks();
    }

    @Test
    public void routeThatIsStoppedSuspendedAndThenResumedAcceptsMessage() throws Exception {
        assertThatRouteIs(Stopped);

        context.suspendRoute(ROUTE_ID);
        context.resumeRoute(ROUTE_ID);

        assertRouteWorks();
    }

    private void assertThatRouteIs(ServiceStatus expectedStatus) {
        assertEquals(expectedStatus, context.getRouteStatus(ROUTE_ID));
    }

    private void assertRouteWorks() throws Exception {
        assertThatRouteIs(Started);

        getMockEndpoint("mock:result").expectedBodiesReceived("anything");

        template.sendBody(ENDPOINT_URI, "anything");

        assertMockEndpointsSatisfied();
    }

    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        camelContext.addComponent("activemq", jmsComponentAutoAcknowledge(connectionFactory));
        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.addRoutes(new RouteBuilder() {
                    @Override
                    public void configure() throws Exception {
                        from(ENDPOINT_URI).routeId(ROUTE_ID).autoStartup(false)
                            .to("log:input")
                            .to("mock:result");
                    }
                });
            }
        };
    }
}
