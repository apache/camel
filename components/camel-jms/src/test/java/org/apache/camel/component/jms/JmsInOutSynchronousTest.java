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

import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(60)
@TestInstance(TestInstance.Lifecycle.PER_METHOD)
public class JmsInOutSynchronousTest extends AbstractJMSTest {

    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();
    private static String beforeThreadName;
    private static String afterThreadName;
    protected CamelContext context;
    protected ProducerTemplate template;
    protected ConsumerTemplate consumer;

    private String reply;
    private final String url = "activemq:queue:JmsInOutSynchronousTest?synchronous=true";

    @BeforeEach
    public void sendMessage() {
        reply = template.requestBody("direct:start", "Hello World", String.class);
    }

    @Test
    public void testSynchronous() {
        assertEquals("Bye World", reply);
        assertTrue(beforeThreadName.equalsIgnoreCase(afterThreadName), "Should use same threads");
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
                        .to("log:before")
                        .process(exchange -> beforeThreadName = Thread.currentThread().getName())
                        .to(url)
                        .process(exchange -> afterThreadName = Thread.currentThread().getName())
                        .to("log:after")
                        .to("mock:result");

                from("activemq:queue:JmsInOutSynchronousTest").process(exchange -> exchange.getMessage().setBody("Bye World"));
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
