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

import javax.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;
import static org.apache.camel.test.infra.activemq.common.ConnectionFactoryHelper.createConnectionFactory;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Tags({ @Tag("not-parallel") })
public class JmsSelectorInTest extends AbstractJMSTest {

    @Test
    public void testJmsSelectorIn() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Carlsberg", "Santa Rita");

        template.sendBodyAndHeader("activemq:queue:JmsSelectorInTest", "Carlsberg", "drink", "beer");
        template.sendBodyAndHeader("activemq:queue:JmsSelectorInTest", "Coca Cola", "drink", "soft");
        template.sendBodyAndHeader("activemq:queue:JmsSelectorInTest", "Santa Rita", "drink", "wine");

        mock.assertIsSatisfied();

        // and there should also only be 2 if browsing as the selector was configured in the route builder
        JmsQueueEndpoint endpoint = context.getEndpoint("activemq:queue:JmsSelectorInTest", JmsQueueEndpoint.class);
        assertEquals(2, endpoint.getExchanges().size());
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ConnectionFactory connectionFactory
                = createConnectionFactory(service);
        JmsComponent component = jmsComponentAutoAcknowledge(connectionFactory);
        camelContext.addComponent("activemq", component);
        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                JmsEndpoint endpoint = context.getEndpoint("activemq:queue:JmsSelectorInTest", JmsEndpoint.class);
                endpoint.setSelector("drink IN ('beer', 'wine')");

                from(endpoint).to("log:drink").to("mock:result");
            }
        };
    }
}
