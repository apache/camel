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

import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class JmsInOnlyWithReplyToHeaderTopicTest extends AbstractJMSTest {

    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();
    protected CamelContext context;
    protected ProducerTemplate template;
    protected ConsumerTemplate consumer;

    void waitForConnections() {
        Awaitility.await().until(() -> context.getRoute("route-1").getUptimeMillis() > 100);
        Awaitility.await().until(() -> context.getRoute("route-2").getUptimeMillis() > 100);
    }

    @Test
    public void testJmsInOnlyWithReplyToHeader() throws Exception {
        getMockEndpoint("mock:JmsInOnlyWithReplyToHeaderTopicTest.bar").expectedMessageCount(1);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");
        mock.expectedHeaderReceived("JMSReplyTo", "ActiveMQTopic[JmsInOnlyWithReplyToHeaderTopicTest.bar]");

        template.send("activemq:queue:JmsInOnlyWithReplyToHeaderTopicTest.foo?preserveMessageQos=true", exchange -> {
            exchange.getIn().setBody("World");
            exchange.getIn().setHeader("JMSReplyTo", "topic:JmsInOnlyWithReplyToHeaderTopicTest.bar");
        });

        MockEndpoint.assertIsSatisfied(context, 30, TimeUnit.SECONDS);
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
                from("activemq:queue:JmsInOnlyWithReplyToHeaderTopicTest.foo")
                        .transform(body().prepend("Hello "))
                        .routeId("route-1")
                        .to("log:result")
                        .to("mock:result");

                // we should disable reply to to avoid sending the message back to our self
                // after we have consumed it
                from("activemq:topic:JmsInOnlyWithReplyToHeaderTopicTest.bar?disableReplyTo=true")
                        .routeId("route-2")
                        .to("log:JmsInOnlyWithReplyToHeaderTopicTest.bar")
                        .to("mock:JmsInOnlyWithReplyToHeaderTopicTest.bar");
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
