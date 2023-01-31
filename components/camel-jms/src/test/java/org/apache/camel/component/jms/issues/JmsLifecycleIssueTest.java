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

import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.AbstractJMSTest;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.TransientCamelContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.apache.camel.ServiceStatus.Started;
import static org.apache.camel.ServiceStatus.Stopped;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
public class JmsLifecycleIssueTest extends AbstractJMSTest {

    public static final String ROUTE_ID = "JmsLifecycleIssueTestRoute";
    public static final String ENDPOINT_URI = "activemq:JmsLifecycleIssueTest.processOrder";
    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new TransientCamelContextExtension();
    protected CamelContext context;
    protected ProducerTemplate template;
    protected ConsumerTemplate consumer;

    @Test
    public void routeThatIsStoppedAndThenResumedAcceptsMessage() throws Exception {
        assertThatRouteIs(Stopped);

        context.getRouteController().resumeRoute(ROUTE_ID);

        assertRouteWorks();
    }

    @Test
    public void routeThatIsStoppedSuspendedAndThenResumedAcceptsMessage() throws Exception {
        assertThatRouteIs(Stopped);

        context.getRouteController().resumeRoute(ROUTE_ID);
        context.getRouteController().resumeRoute(ROUTE_ID);

        assertRouteWorks();
    }

    private void assertThatRouteIs(ServiceStatus expectedStatus) {
        assertEquals(expectedStatus, context.getRouteController().getRouteStatus(ROUTE_ID));
    }

    private void assertRouteWorks() throws Exception {
        assertThatRouteIs(Started);

        getMockEndpoint("mock:result").expectedBodiesReceived("anything");

        template.sendBody(ENDPOINT_URI, "anything");

        MockEndpoint.assertIsSatisfied(context);
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
                from(ENDPOINT_URI).routeId(ROUTE_ID).autoStartup(false)
                        .to("log:input")
                        .to("mock:result");
            }
        };
    }

    @Override
    public CamelContextExtension getCamelContextExtension() {
        return camelContextExtension;
    }

    @BeforeEach
    void setUpRequirements() {
        context = camelContextExtension.getContext();
        template = camelContextExtension.getProducerTemplate();
        consumer = camelContextExtension.getConsumerTemplate();
    }
}
