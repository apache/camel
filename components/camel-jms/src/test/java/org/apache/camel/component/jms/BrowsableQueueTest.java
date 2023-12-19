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

import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.parallel.Isolated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Isolated("Needs greater isolation due to CAMEL-20269")
public class BrowsableQueueTest extends AbstractJMSTest {
    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();
    private static final Logger LOG = LoggerFactory.getLogger(BrowsableQueueTest.class);

    protected final String componentName = "activemq";
    protected final Object[] expectedBodies = { "body1", "body2", "body3", "body4", "body5", "body6", "body7", "body8" };
    protected CamelContext context;
    protected ProducerTemplate template;
    protected ConsumerTemplate consumer;

    @Test
    public void testSendMessagesThenBrowseQueue() {
        final String queueName = queueNameForClass("activemq:BrowsableQueueTest.b", this.getClass());
        // send some messages
        for (int i = 0; i < expectedBodies.length; i++) {
            Object expectedBody = expectedBodies[i];
            template.sendBodyAndHeader(queueName, expectedBody, "counter", i);
        }

        // now lets browse the queue
        JmsQueueEndpoint endpoint = getMandatoryEndpoint(queueName + "?maximumBrowseSize=6", JmsQueueEndpoint.class);
        assertEquals(6, endpoint.getMaximumBrowseSize());
        List<Exchange> list = endpoint.getExchanges();
        LOG.debug("Received: {}", list);
        assertEquals(6, endpoint.getExchanges().size(), "Size of list");

        int index = -1;
        for (Exchange exchange : list) {
            String actual = exchange.getIn().getBody(String.class);
            LOG.debug("Received body: {}", actual);

            Object expected = expectedBodies[++index];
            assertEquals(expected, actual, "Body: " + index);
        }
    }

    @Test
    public void testSendMessagesThenBrowseQueueLimitNotHit() {
        final String queueName = queueNameForClass("activemq:BrowsableQueueTest.c", this.getClass());

        // send some messages
        for (int i = 0; i < expectedBodies.length; i++) {
            Object expectedBody = expectedBodies[i];
            template.sendBodyAndHeader(queueName, expectedBody, "counter", i);
        }

        // now lets browse the queue
        JmsQueueEndpoint endpoint = getMandatoryEndpoint(queueName + "?maximumBrowseSize=10", JmsQueueEndpoint.class);
        assertEquals(10, endpoint.getMaximumBrowseSize());
        List<Exchange> list = endpoint.getExchanges();
        LOG.debug("Received: {}", list);
        assertEquals(8, endpoint.getExchanges().size(), "Size of list");

        int index = -1;
        for (Exchange exchange : list) {
            String actual = exchange.getIn().getBody(String.class);
            LOG.debug("Received body: {}", actual);

            Object expected = expectedBodies[++index];
            assertEquals(expected, actual, "Body: " + index);
        }
    }

    @Test
    public void testSendMessagesThenBrowseQueueNoMax() {
        final String queueName = queueNameForClass("activemq:BrowsableQueueTest.b", this.getClass());

        // send some messages
        for (int i = 0; i < expectedBodies.length; i++) {
            Object expectedBody = expectedBodies[i];
            template.sendBodyAndHeader(queueName, expectedBody, "counter", i);
        }

        // now lets browse the queue
        JmsQueueEndpoint endpoint = getMandatoryEndpoint(queueName, JmsQueueEndpoint.class);
        assertEquals(-1, endpoint.getMaximumBrowseSize());
        List<Exchange> list = endpoint.getExchanges();
        LOG.debug("Received: {}", list);
        assertEquals(8, endpoint.getExchanges().size(), "Size of list");

        int index = -1;
        for (Exchange exchange : list) {
            String actual = exchange.getIn().getBody(String.class);
            LOG.debug("Received body: {}", actual);

            Object expected = expectedBodies[++index];
            assertEquals(expected, actual, "Body: " + index);
        }
    }

    @Override
    protected String getComponentName() {
        return componentName;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            final String queueNameA = queueNameForClass("activemq:BrowsableQueueTest.a", this.getClass());
            final String queueNameB = queueNameForClass("activemq:BrowsableQueueTest.b", this.getClass());

            public void configure() {
                from(queueNameA).to(queueNameB);
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
