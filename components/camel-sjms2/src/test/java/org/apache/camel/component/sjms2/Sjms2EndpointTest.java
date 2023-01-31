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
package org.apache.camel.component.sjms2;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.ExchangePattern;
import org.apache.camel.test.infra.artemis.services.ArtemisService;
import org.apache.camel.test.infra.artemis.services.ArtemisServiceFactory;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class Sjms2EndpointTest extends CamelTestSupport {

    @RegisterExtension
    public ArtemisService service = ArtemisServiceFactory.createVMService();

    @Override
    protected boolean useJmx() {
        return true;
    }

    @Test
    public void testDefaults() {
        Endpoint endpoint = context.getEndpoint("sjms2:test");
        assertNotNull(endpoint);
        assertTrue(endpoint instanceof Sjms2Endpoint);
        Sjms2Endpoint sjms = (Sjms2Endpoint) endpoint;
        assertEquals("sjms2://test", sjms.getEndpointUri());
        assertEquals(ExchangePattern.InOnly, sjms.createExchange().getPattern());
    }

    @Test
    public void testQueueEndpoint() {
        Endpoint sjms = context.getEndpoint("sjms2:queue:test");
        assertNotNull(sjms);
        assertEquals("sjms2://queue:test", sjms.getEndpointUri());
        assertTrue(sjms instanceof Sjms2Endpoint);
    }

    @Test
    public void testJndiStyleEndpointName() {
        Sjms2Endpoint sjms = context.getEndpoint("sjms2:/jms/test/hov.t1.dev:topic", Sjms2Endpoint.class);
        assertNotNull(sjms);
        assertFalse(sjms.isTopic());
        assertEquals("/jms/test/hov.t1.dev:topic", sjms.getDestinationName());
    }

    @Test
    public void testSetTransacted() {
        Endpoint endpoint = context.getEndpoint("sjms2:queue:test?transacted=true");
        assertNotNull(endpoint);
        assertTrue(endpoint instanceof Sjms2Endpoint);
        Sjms2Endpoint qe = (Sjms2Endpoint) endpoint;
        assertTrue(qe.isTransacted());
    }

    @Test
    public void testAsyncProducer() {
        Endpoint endpoint = context.getEndpoint("sjms2:queue:test?synchronous=true");
        assertNotNull(endpoint);
        assertTrue(endpoint instanceof Sjms2Endpoint);
        Sjms2Endpoint qe = (Sjms2Endpoint) endpoint;
        assertTrue(qe.isSynchronous());
    }

    @Test
    public void testReplyTo() {
        String replyTo = "reply.to.queue";
        Endpoint endpoint = context.getEndpoint("sjms2:queue:test?replyTo=" + replyTo);
        assertNotNull(endpoint);
        assertTrue(endpoint instanceof Sjms2Endpoint);
        Sjms2Endpoint qe = (Sjms2Endpoint) endpoint;
        assertEquals(qe.getReplyTo(), replyTo);
        assertEquals(ExchangePattern.InOut, qe.createExchange().getPattern());
    }

    @Test
    public void testDefaultExchangePattern() {
        try {
            Sjms2Endpoint sjms = (Sjms2Endpoint) context.getEndpoint("sjms2:queue:test");
            assertNotNull(sjms);
            assertEquals(ExchangePattern.InOnly, sjms.getExchangePattern());
        } catch (Exception e) {
            fail("Exception thrown: " + e.getLocalizedMessage());
        }
    }

    @Test
    public void testInOnlyExchangePattern() {
        try {
            Endpoint sjms = context.getEndpoint("sjms2:queue:test?exchangePattern=" + ExchangePattern.InOnly);
            assertNotNull(sjms);
            assertEquals(ExchangePattern.InOnly, sjms.createExchange().getPattern());
        } catch (Exception e) {
            fail("Exception thrown: " + e.getLocalizedMessage());
        }
    }

    @Test
    public void testInOutExchangePattern() {
        try {
            Endpoint sjms = context.getEndpoint("sjms2:queue:test?exchangePattern=" + ExchangePattern.InOut);
            assertNotNull(sjms);
            assertEquals(ExchangePattern.InOut, sjms.createExchange().getPattern());
        } catch (Exception e) {
            fail("Exception thrown: " + e.getLocalizedMessage());
        }
    }

    @Test
    public void testReplyToAndMEPMatch() {
        String replyTo = "reply.to.queue";
        Endpoint endpoint = context
                .getEndpoint("sjms2:queue:test?replyTo=" + replyTo + "&exchangePattern=" + ExchangePattern.InOut);
        assertNotNull(endpoint);
        assertTrue(endpoint instanceof Sjms2Endpoint);
        Sjms2Endpoint qe = (Sjms2Endpoint) endpoint;
        assertEquals(qe.getReplyTo(), replyTo);
        assertEquals(ExchangePattern.InOut, qe.createExchange().getPattern());
    }

    @Test
    public void testDestinationName() {
        Endpoint endpoint = context.getEndpoint("sjms2:queue:test?synchronous=true");
        assertNotNull(endpoint);
        assertTrue(endpoint instanceof Sjms2Endpoint);
        Sjms2Endpoint qe = (Sjms2Endpoint) endpoint;
        assertTrue(qe.isSynchronous());
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ActiveMQConnectionFactory connectionFactory
                = new ActiveMQConnectionFactory(service.serviceAddress());
        Sjms2Component component = new Sjms2Component();
        component.setConnectionFactory(connectionFactory);
        camelContext.addComponent("sjms2", component);

        return camelContext;
    }
}
