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

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.ExchangePattern;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class Sjms2EndpointTest extends CamelTestSupport {

    @Override
    protected boolean useJmx() {
        return true;
    }

    @Test
    public void testDefaults() throws Exception {
        Endpoint endpoint = context.getEndpoint("sjms2:test");
        assertNotNull(endpoint);
        assertTrue(endpoint instanceof Sjms2Endpoint);
        Sjms2Endpoint sjms = (Sjms2Endpoint)endpoint;
        assertEquals(sjms.getEndpointUri(), "sjms2://test");
        assertEquals(sjms.createExchange().getPattern(), ExchangePattern.InOnly);
    }

    @Test
    public void testQueueEndpoint() throws Exception {
        Endpoint sjms = context.getEndpoint("sjms2:queue:test");
        assertNotNull(sjms);
        assertEquals(sjms.getEndpointUri(), "sjms2://queue:test");
        assertTrue(sjms instanceof Sjms2Endpoint);
    }

    @Test
    public void testJndiStyleEndpointName() throws Exception {
        Sjms2Endpoint sjms = context.getEndpoint("sjms2:/jms/test/hov.t1.dev:topic", Sjms2Endpoint.class);
        assertNotNull(sjms);
        assertFalse(sjms.isTopic());
        assertEquals("/jms/test/hov.t1.dev:topic", sjms.getDestinationName());
    }

    @Test
    public void testSetTransacted() throws Exception {
        Endpoint endpoint = context.getEndpoint("sjms2:queue:test?transacted=true");
        assertNotNull(endpoint);
        assertTrue(endpoint instanceof Sjms2Endpoint);
        Sjms2Endpoint qe = (Sjms2Endpoint)endpoint;
        assertTrue(qe.isTransacted());
    }

    @Test
    public void testAsyncProducer() throws Exception {
        Endpoint endpoint = context.getEndpoint("sjms2:queue:test?synchronous=true");
        assertNotNull(endpoint);
        assertTrue(endpoint instanceof Sjms2Endpoint);
        Sjms2Endpoint qe = (Sjms2Endpoint)endpoint;
        assertTrue(qe.isSynchronous());
    }

    @Test
    public void testNamedReplyTo() throws Exception {
        String namedReplyTo = "reply.to.queue";
        Endpoint endpoint = context.getEndpoint("sjms2:queue:test?namedReplyTo=" + namedReplyTo);
        assertNotNull(endpoint);
        assertTrue(endpoint instanceof Sjms2Endpoint);
        Sjms2Endpoint qe = (Sjms2Endpoint)endpoint;
        assertEquals(qe.getNamedReplyTo(), namedReplyTo);
        assertEquals(qe.createExchange().getPattern(), ExchangePattern.InOut);
    }

    @Test
    public void testDefaultExchangePattern() throws Exception {
        try {
            Sjms2Endpoint sjms = (Sjms2Endpoint)context.getEndpoint("sjms2:queue:test");
            assertNotNull(sjms);
            assertEquals(ExchangePattern.InOnly, sjms.getExchangePattern());
            // assertTrue(sjms.createExchange().getPattern().equals(ExchangePattern.InOnly));
        } catch (Exception e) {
            fail("Exception thrown: " + e.getLocalizedMessage());
        }
    }

    @Test
    public void testInOnlyExchangePattern() throws Exception {
        try {
            Endpoint sjms = context.getEndpoint("sjms2:queue:test?exchangePattern=" + ExchangePattern.InOnly);
            assertNotNull(sjms);
            assertTrue(sjms.createExchange().getPattern().equals(ExchangePattern.InOnly));
        } catch (Exception e) {
            fail("Exception thrown: " + e.getLocalizedMessage());
        }
    }

    @Test
    public void testInOutExchangePattern() throws Exception {
        try {
            Endpoint sjms = context.getEndpoint("sjms2:queue:test?exchangePattern=" + ExchangePattern.InOut);
            assertNotNull(sjms);
            assertTrue(sjms.createExchange().getPattern().equals(ExchangePattern.InOut));
        } catch (Exception e) {
            fail("Exception thrown: " + e.getLocalizedMessage());
        }
    }

    @Test
    public void testNamedReplyToAndMEPMatch() throws Exception {
        String namedReplyTo = "reply.to.queue";
        Endpoint endpoint = context.getEndpoint("sjms2:queue:test?namedReplyTo=" + namedReplyTo + "&exchangePattern=" + ExchangePattern.InOut);
        assertNotNull(endpoint);
        assertTrue(endpoint instanceof Sjms2Endpoint);
        Sjms2Endpoint qe = (Sjms2Endpoint)endpoint;
        assertEquals(qe.getNamedReplyTo(), namedReplyTo);
        assertEquals(qe.createExchange().getPattern(), ExchangePattern.InOut);
    }

    @Test(expected = Exception.class)
    public void testNamedReplyToAndMEPMismatch() throws Exception {
        context.getEndpoint("sjms2:queue:test?namedReplyTo=reply.to.queue&exchangePattern=" + ExchangePattern.InOnly);
    }

    @Test
    public void testDestinationName() throws Exception {
        Endpoint endpoint = context.getEndpoint("sjms2:queue:test?synchronous=true");
        assertNotNull(endpoint);
        assertTrue(endpoint instanceof Sjms2Endpoint);
        Sjms2Endpoint qe = (Sjms2Endpoint)endpoint;
        assertTrue(qe.isSynchronous());
    }

    @Test
    public void testTransactedBatchCountDefault() throws Exception {
        Endpoint endpoint = context.getEndpoint("sjms2:queue:test?transacted=true");
        assertNotNull(endpoint);
        assertTrue(endpoint instanceof Sjms2Endpoint);
        Sjms2Endpoint qe = (Sjms2Endpoint)endpoint;
        assertTrue(qe.getTransactionBatchCount() == -1);
    }

    @Test
    public void testTransactedBatchCountModified() throws Exception {
        Endpoint endpoint = context.getEndpoint("sjms2:queue:test?transacted=true&transactionBatchCount=10");
        assertNotNull(endpoint);
        assertTrue(endpoint instanceof Sjms2Endpoint);
        Sjms2Endpoint qe = (Sjms2Endpoint)endpoint;
        assertTrue(qe.getTransactionBatchCount() == 10);
    }

    @Test
    public void testTransactedBatchTimeoutDefault() throws Exception {
        Endpoint endpoint = context.getEndpoint("sjms2:queue:test?transacted=true");
        assertNotNull(endpoint);
        assertTrue(endpoint instanceof Sjms2Endpoint);
        Sjms2Endpoint qe = (Sjms2Endpoint)endpoint;
        assertTrue(qe.getTransactionBatchTimeout() == 5000);
    }

    @Test
    public void testTransactedBatchTimeoutModified() throws Exception {
        Endpoint endpoint = context.getEndpoint("sjms2:queue:test?transacted=true&transactionBatchTimeout=3000");
        assertNotNull(endpoint);
        assertTrue(endpoint instanceof Sjms2Endpoint);
        Sjms2Endpoint qe = (Sjms2Endpoint)endpoint;
        assertTrue(qe.getTransactionBatchTimeout() == 3000);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://broker?broker.persistent=false&broker.useJmx=false");
        Sjms2Component component = new Sjms2Component();
        component.setConnectionCount(3);
        component.setConnectionFactory(connectionFactory);
        camelContext.addComponent("sjms2", component);

        return camelContext;
    }
}
