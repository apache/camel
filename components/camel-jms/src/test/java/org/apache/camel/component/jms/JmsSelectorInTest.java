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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Timeout(30)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class JmsSelectorInTest extends AbstractJMSTest {

    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();
    protected CamelContext context;
    protected ProducerTemplate template;
    protected ConsumerTemplate consumer;

    @Test
    @Order(1)
    public void testJmsSelectorIn() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result2");
        mock.expectedBodiesReceived("Carlsberg", "Santa Rita");

        template.sendBodyAndHeader("activemq:queue:JmsSelectorInTest", "Carlsberg", "drink", "beer");
        template.sendBodyAndHeader("activemq:queue:JmsSelectorInTest", "Coca Cola", "drink", "soft");
        template.sendBodyAndHeader("activemq:queue:JmsSelectorInTest", "Santa Rita", "drink", "wine");

        mock.assertIsSatisfied();
    }

    @Test
    @Order(2)
    @Disabled("Browsing after consumption is not working on Artermis")
    public void testThatBrowsingWorks() {
        // and there should also only be 2 if browsing as the selector was configured in the route builder
        JmsQueueEndpoint endpoint = context.getEndpoint("activemq:queue:JmsSelectorInTest", JmsQueueEndpoint.class);
        assertEquals(2, endpoint.getExchanges().size());
    }

    @Override
    protected String getComponentName() {
        return "activemq";
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                JmsEndpoint endpoint = getContext().getEndpoint("activemq:queue:JmsSelectorInTest", JmsEndpoint.class);
                endpoint.setSelector("drink IN ('beer', 'wine')");

                from(endpoint).to("log:drink").to("mock:result2");
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
