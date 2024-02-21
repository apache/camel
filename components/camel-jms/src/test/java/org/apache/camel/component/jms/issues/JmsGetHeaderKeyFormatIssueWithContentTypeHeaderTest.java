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
import org.apache.camel.component.jms.JmsMessage;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Unit test to verify that we can route a JMS message and do header lookup by name without mutating it and that it can
 * handle the default keyFormatStrategy with _HYPHEN_ in the key name
 */
public class JmsGetHeaderKeyFormatIssueWithContentTypeHeaderTest extends AbstractJMSTest {

    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();
    protected CamelContext context;
    protected ProducerTemplate template;
    protected ConsumerTemplate consumer;
    private final String uri
            = "activemq:queue:JmsGetHeaderKeyFormatIssueWithContentTypeHeaderTest?jmsKeyFormatStrategy=default";

    @Test
    public void testSendWithHeaders() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).body().isEqualTo("Hello World");
        mock.message(0).header("Content-Type").isEqualTo("text/plain");

        MockEndpoint copy = getMockEndpoint("mock:copy");
        copy.expectedMessageCount(1);
        copy.message(0).body().isEqualTo("Hello World");
        copy.message(0).header("Content-Type").isEqualTo("text/plain");

        template.sendBodyAndHeader(uri, "Hello World", "Content-Type", "text/plain");

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
                from(uri)
                        .process(exchange -> {
                            assertEquals("text/plain", exchange.getIn().getHeader("Content-Type"));

                            // do not mutate it
                            JmsMessage msg = assertIsInstanceOf(JmsMessage.class, exchange.getIn());
                            assertNotNull(msg.getJmsMessage(), "jakarta.jms.Message should not be null");
                        })
                        .to("activemq:queue:JmsGetHeaderKeyFormatIssueWithContentTypeHeaderTest.copy", "mock:result");

                from("activemq:queue:JmsGetHeaderKeyFormatIssueWithContentTypeHeaderTest.copy").to("mock:copy");
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
