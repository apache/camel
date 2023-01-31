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

import jakarta.jms.ConnectionFactory;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.FailedToCreateProducerException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;
import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class JmsTestConnectionOnStartupTest extends CamelTestSupport {

    @Test
    public void testConnectionOnStartupConsumerTest() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("activemq:queue:JmsTestConnectionOnStartupTest?testConnectionOnStartup=true").to("mock:foo");
            }
        });

        try {
            context.start();
            fail("Should have thrown an exception");
        } catch (Exception e) {
            assertEquals(
                    "Failed to create Consumer for endpoint: activemq://queue:JmsTestConnectionOnStartupTest?testConnectionOnStartup=true. "
                         + "Reason: Cannot get JMS Connection on startup for destination JmsTestConnectionOnStartupTest",
                    e.getMessage());
        }
    }

    @Test
    public void testConnectionOnStartupProducerTest() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to("activemq:queue:JmsTestConnectionOnStartupTest?testConnectionOnStartup=true");
            }
        });

        try {
            context.start();
            fail("Should have thrown an exception");
        } catch (Exception ex) {
            FailedToCreateProducerException e = assertIsInstanceOf(FailedToCreateProducerException.class, ex.getCause());
            assertTrue(e.getMessage()
                    .startsWith(
                            "Failed to create Producer for endpoint: activemq://queue:JmsTestConnectionOnStartupTest?testConnectionOnStartup=true."));
            assertTrue(e.getCause().toString().contains("jakarta.jms.JMSException: Failed to create session factory"));
        }
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        // we do not start a broker on tcp 61111
        ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("tcp://localhost:61111");
        camelContext.addComponent("activemq", jmsComponentAutoAcknowledge(connectionFactory));

        return camelContext;
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }
}
