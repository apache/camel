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

package org.apache.camel.component.jms.async;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.AbstractJMSTest;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.TransientCamelContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.extension.RegisterExtension;

public class AsyncJmsProducerTest extends AbstractJMSTest {

    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new TransientCamelContextExtension();

    private static String beforeThreadName;
    private static String afterThreadName;
    protected CamelContext context;
    protected ProducerTemplate template;
    protected ConsumerTemplate consumer;

    private MockEndpoint mockBefore;
    private MockEndpoint mockAfter;
    private MockEndpoint mockResult;

    @BeforeEach
    void setupMocks() {
        mockBefore = getMockEndpoint("mock:before");
        mockAfter = getMockEndpoint("mock:after");
        mockResult = getMockEndpoint("mock:result");

        mockBefore.expectedBodiesReceived("Hello Camel");
        mockAfter.expectedBodiesReceived("Bye Camel");
        mockResult.expectedBodiesReceived("Bye Camel");
    }

    @RepeatedTest(5)
    public void testAsyncEndpoint() throws Exception {
        String reply = template.requestBody("direct:start", "Hello Camel", String.class);
        assertEquals("Bye Camel", reply);

        mockBefore.assertIsSatisfied();
        mockAfter.assertIsSatisfied();
        mockResult.assertIsSatisfied();

        assertFalse(beforeThreadName.equalsIgnoreCase(afterThreadName), "Should use different threads");
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
                        .to("mock:before")
                        .to("log:before")
                        .process(exchange ->
                                beforeThreadName = Thread.currentThread().getName())
                        .to("activemq:queue:AsyncJmsProducerTest")
                        .process(exchange ->
                                afterThreadName = Thread.currentThread().getName())
                        .to("log:after")
                        .to("mock:after")
                        .to("mock:result");

                from("activemq:queue:AsyncJmsProducerTest").transform(constant("Bye Camel"));
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
