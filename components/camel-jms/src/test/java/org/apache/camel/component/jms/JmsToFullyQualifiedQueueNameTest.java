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
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Queue can be specified using `Fully Qualified Queue Name`.
 */
public class JmsToFullyQualifiedQueueNameTest extends AbstractJMSTest {

    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();
    protected CamelContext context;
    protected ProducerTemplate template;
    protected ConsumerTemplate consumer;

    @Test
    void testFullyQualifiedQueueName() throws Exception {
        expect("mock:dynamic.foo", "Hello foo");
        expect("mock:dynamic.bar", "Hello bar");
        expect("mock:dynamic.from.header", "Hello from header");
        expect("mock:static.name", "Hello name");

        template.sendBodyAndHeader("direct:start-tod", "Hello foo", "where", "address::dynamic.foo");
        template.sendBodyAndHeader("direct:start-tod", "Hello bar", "where", "address::dynamic.bar");
        template.sendBody("direct:start-tod-header", "Hello from header");
        template.sendBody("direct:start-to", "Hello name");

        MockEndpoint.assertIsSatisfied(context);
    }

    private void expect(String uri, String body) {
        MockEndpoint mockEndpoint = getMockEndpoint(uri);
        mockEndpoint.expectedMessageCount(1);
        mockEndpoint.expectedBodiesReceived(body);
        mockEndpoint.whenAnyExchangeReceived(x -> assertNull(x.getMessage().getHeader(JmsConstants.JMS_DESTINATION_NAME)));
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
                // toD
                from("direct:start-tod").toD("activemq:queue:${header.where}");
                from("activemq:queue:address::dynamic.foo").to("mock:dynamic.foo");
                from("activemq:queue:address::dynamic.bar").to("mock:dynamic.bar");

                // toD with JMS_DESTINATION_NAME
                from("direct:start-tod-header")
                        .setHeader(JmsConstants.JMS_DESTINATION_NAME, constant("address::from.header"))
                        .toD("activemq:dummy");
                from("activemq:queue:address::from.header").to("mock:dynamic.from.header");

                // to
                from("direct:start-to").to("activemq:queue:address::static.name");
                from("activemq:queue:address::static.name").to("mock:static.name");
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
