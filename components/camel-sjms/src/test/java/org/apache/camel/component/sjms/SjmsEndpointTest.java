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
package org.apache.camel.component.sjms;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class SjmsEndpointTest extends CamelTestSupport {

    @Override
    protected boolean useJmx() {
        return true;
    }

    @Test
    public void testDefaults() {
        Endpoint endpoint = context.getEndpoint("sjms:test.SjmsEndpointTest");
        assertNotNull(endpoint);
        assertTrue(endpoint instanceof SjmsEndpoint);
        SjmsEndpoint sjms = (SjmsEndpoint) endpoint;
        assertEquals("sjms://test.SjmsEndpointTest", sjms.getEndpointUri());
        assertEquals(ExchangePattern.InOnly, sjms.createExchange().getPattern());
    }

    @Test
    public void testQueueEndpoint() {
        Endpoint sjms = context.getEndpoint("sjms:queue:test.SjmsEndpointTest");
        assertNotNull(sjms);
        assertEquals("sjms://queue:test.SjmsEndpointTest", sjms.getEndpointUri());
        assertTrue(sjms instanceof SjmsEndpoint);
    }

    @Test
    public void testJndiStyleEndpointName() {
        SjmsEndpoint sjms = context.getEndpoint("sjms:/jms/test/hov.t1.dev:topic.SjmsEndpointTest", SjmsEndpoint.class);
        assertNotNull(sjms);
        assertFalse(sjms.isTopic());
        assertEquals("/jms/test/hov.t1.dev:topic.SjmsEndpointTest", sjms.getDestinationName());
    }

    @Test
    public void testSetTransacted() {
        Endpoint endpoint = context.getEndpoint("sjms:queue:test.SjmsEndpointTest?transacted=true");
        assertNotNull(endpoint);
        assertTrue(endpoint instanceof SjmsEndpoint);
        SjmsEndpoint qe = (SjmsEndpoint) endpoint;
        assertTrue(qe.isTransacted());
    }

    @Test
    public void testAsyncProducer() {
        Endpoint endpoint = context.getEndpoint("sjms:queue:test.SjmsEndpointTest?synchronous=true");
        assertNotNull(endpoint);
        assertTrue(endpoint instanceof SjmsEndpoint);
        SjmsEndpoint qe = (SjmsEndpoint) endpoint;
        assertTrue(qe.isSynchronous());
    }

    @Test
    public void testReplyTo() {
        String replyTo = "reply.to.queue";
        Endpoint endpoint = context.getEndpoint("sjms:queue:test.SjmsEndpointTest?replyTo=" + replyTo);
        assertNotNull(endpoint);
        assertTrue(endpoint instanceof SjmsEndpoint);
        SjmsEndpoint qe = (SjmsEndpoint) endpoint;
        assertEquals(qe.getReplyTo(), replyTo);
        assertEquals(ExchangePattern.InOut, qe.createExchange().getPattern());
    }

    @Test
    public void testDefaultExchangePattern() {
        try {
            SjmsEndpoint sjms = (SjmsEndpoint) context.getEndpoint("sjms:queue:test.SjmsEndpointTest");
            assertNotNull(sjms);
            assertEquals(ExchangePattern.InOnly, sjms.getExchangePattern());
            // assertTrue(sjms.createExchange().getPattern().equals(ExchangePattern.InOnly));
        } catch (Exception e) {
            fail("Exception thrown: " + e.getLocalizedMessage());
        }
    }

    @Test
    public void testInOnlyExchangePattern() {
        try {
            Endpoint sjms = context.getEndpoint("sjms:queue:test.SjmsEndpointTest?exchangePattern=" + ExchangePattern.InOnly);
            assertNotNull(sjms);
            assertEquals(ExchangePattern.InOnly, sjms.createExchange().getPattern());
        } catch (Exception e) {
            fail("Exception thrown: " + e.getLocalizedMessage());
        }
    }

    @Test
    public void testInOutExchangePattern() {
        try {
            Endpoint sjms = context.getEndpoint("sjms:queue:test.SjmsEndpointTest?exchangePattern=" + ExchangePattern.InOut);
            assertNotNull(sjms);
            assertEquals(ExchangePattern.InOut, sjms.createExchange().getPattern());
        } catch (Exception e) {
            fail("Exception thrown: " + e.getLocalizedMessage());
        }
    }

    @Test
    public void testUnsupportedMessageExchangePattern() {
        assertThrows(ResolveEndpointFailedException.class,
                () -> context.getEndpoint("sjms:queue:test2.SjmsEndpointTest?messageExchangePattern=OutOnly"));
    }

    @Test
    public void testReplyToAndMEPMatch() {
        String replyTo = "reply.to.queue";
        Endpoint endpoint = context
                .getEndpoint(
                        "sjms:queue:test.SjmsEndpointTest?replyTo=" + replyTo + "&exchangePattern=" + ExchangePattern.InOut);
        assertNotNull(endpoint);
        assertTrue(endpoint instanceof SjmsEndpoint);
        SjmsEndpoint qe = (SjmsEndpoint) endpoint;
        assertEquals(qe.getReplyTo(), replyTo);
        assertEquals(ExchangePattern.InOut, qe.createExchange().getPattern());
    }

    @Test
    public void testDestinationName() {
        Endpoint endpoint = context.getEndpoint("sjms:queue:test.SjmsEndpointTest?synchronous=true");
        assertNotNull(endpoint);
        assertTrue(endpoint instanceof SjmsEndpoint);
        SjmsEndpoint qe = (SjmsEndpoint) endpoint;
        assertTrue(qe.isSynchronous());
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ActiveMQConnectionFactory connectionFactory
                = new ActiveMQConnectionFactory("vm://broker?broker.persistent=false&broker.useJmx=false");
        SjmsComponent component = new SjmsComponent();
        component.setConnectionFactory(connectionFactory);
        camelContext.addComponent("sjms", component);

        return camelContext;
    }
}
