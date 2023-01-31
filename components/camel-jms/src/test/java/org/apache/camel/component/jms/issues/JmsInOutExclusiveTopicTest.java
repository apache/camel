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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.AbstractJMSTest;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.apache.camel.util.StringHelper;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JmsInOutExclusiveTopicTest extends AbstractJMSTest {

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
    public void testJmsInOutExclusiveTopicTest() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye Camel");

        String out = template.requestBody("direct:start", "Camel", String.class);
        assertEquals("Bye Camel", out);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected String getComponentName() {
        return "activemq";
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .routeId("route-1")
                        .to("activemq:topic:news?replyToType=Exclusive&replyTo=queue:JmsInOutExclusiveTopicTest.reply")
                        .to("mock:result");

                from("activemq:topic:news?disableReplyTo=true")
                        .routeId("route-2")
                        .transform(body().prepend("Bye "))
                        .process(exchange -> {
                            String replyTo = exchange.getIn().getHeader("JMSReplyTo", String.class);
                            replyTo = StringHelper.between(replyTo, "ActiveMQQueue[", "]");
                            String cid = exchange.getIn().getHeader("JMSCorrelationID", String.class);

                            log.info("ReplyTo: {}", replyTo);
                            log.info("CorrelationID: {}", cid);
                            if (replyTo != null && cid != null) {
                                // wait a bit before sending back
                                Thread.sleep(1000);
                                log.info("Sending back reply message on {}", replyTo);
                                template.sendBodyAndHeader("activemq:" + replyTo, exchange.getIn().getBody(),
                                        "JMSCorrelationID", cid);
                            }
                        });
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
