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

import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.AbstractJMSTest;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.apache.camel.test.infra.core.annotations.ContextFixture;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;

@Timeout(30)
public class AsyncConsumerInOutTest extends AbstractJMSTest {

    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();
    protected CamelContext context;
    protected ProducerTemplate template;
    protected ConsumerTemplate consumer;

    void waitForConnections() {
        Awaitility.await().until(() -> context.getRoute("route-1").getUptimeMillis() > 200);
        Awaitility.await().until(() -> context.getRoute("route-2").getUptimeMillis() > 200);
    }

    @Test
    public void testAsyncJmsConsumer() throws Exception {
        // Hello World is received first despite its send last
        // the reason is that the first message is processed asynchronously
        // and it takes 2 sec to complete, so in between we have time to
        // process the 2nd message on the queue
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World", "Bye Camel");

        template.sendBody("activemq:queue:AsyncConsumerInOutTest.start", "Hello Camel");
        template.sendBody("activemq:queue:AsyncConsumerInOutTest.start", "Hello World");

        MockEndpoint.assertIsSatisfied(context, 20, TimeUnit.SECONDS);
    }

    @Override
    protected String getComponentName() {
        return "activemq";
    }

    @ContextFixture
    public void configureComponent(CamelContext context) {
        context.addComponent("async", new MyAsyncComponent());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // enable async in only mode on the consumer
                from("activemq:queue:AsyncConsumerInOutTest.start?asyncConsumer=true")
                        .routeId("route-1")
                        .choice()
                        .when(body().contains("Camel"))
                        .to("async:camel?delay=2000")
                        .to(ExchangePattern.InOut, "activemq:queue:AsyncConsumerInOutTest.camel")
                        .to("mock:result")
                        .otherwise()
                        .to("log:other")
                        .to("mock:result");

                from("activemq:queue:AsyncConsumerInOutTest.camel")
                        .routeId("route-2")
                        .to("log:camel")
                        .transform(constant("Bye Camel"));
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

        waitForConnections();
    }
}
