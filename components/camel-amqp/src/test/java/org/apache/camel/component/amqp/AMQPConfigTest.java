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
package org.apache.camel.component.amqp;

import org.apache.camel.CamelContext;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.apache.camel.test.infra.core.annotations.ContextFixture;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.apache.camel.component.amqp.AMQPComponent.amqpComponent;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AMQPConfigTest {

    @RegisterExtension
    protected static CamelContextExtension contextExtension = new DefaultCamelContextExtension();

    @Test
    public void testComponent() {
        AMQPComponent component = contextExtension.getContext().getComponent("amqp", AMQPComponent.class);
        assertTrue(component.getConnectionFactory() instanceof JmsConnectionFactory);

        JmsConnectionFactory connectionFactory = (JmsConnectionFactory) component.getConnectionFactory();
        assertNull(connectionFactory.getUsername());
        assertNull(connectionFactory.getPassword());
        assertEquals("amqp://remotehost:5556", connectionFactory.getRemoteURI());
        assertEquals("topic://", connectionFactory.getTopicPrefix());
    }

    @Test
    public void testConfiguredComponent() {
        AMQPComponent customizedComponent = contextExtension.getContext().getComponent("amqp-configured", AMQPComponent.class);
        assertEquals("remotehost", customizedComponent.getHost());
        assertEquals(5556, customizedComponent.getPort());
        assertEquals("camel", customizedComponent.getUsername());
        assertEquals("rider", customizedComponent.getPassword());

        assertTrue(customizedComponent.getConnectionFactory() instanceof JmsConnectionFactory);
        JmsConnectionFactory connectionFactory = (JmsConnectionFactory) customizedComponent.getConnectionFactory();
        assertEquals("camel", connectionFactory.getUsername());
        assertEquals("rider", connectionFactory.getPassword());
        assertEquals("amqp://remotehost:5556", connectionFactory.getRemoteURI());
        assertEquals("topic://", connectionFactory.getTopicPrefix());
    }

    @Test
    public void testConfiguredSslComponent() {
        AMQPComponent customizedComponent = contextExtension.getContext().getComponent("amqps-configured", AMQPComponent.class);
        assertTrue(customizedComponent.getUseSsl());
        assertEquals("server-ca-truststore.p12", customizedComponent.getTrustStoreLocation());
        assertEquals("securepass", customizedComponent.getTrustStorePassword());
        assertEquals("PKCS12", customizedComponent.getTrustStoreType());
        assertEquals("server-keystore.p12", customizedComponent.getKeyStoreLocation());
        assertEquals("securepass", customizedComponent.getKeyStorePassword());
        assertEquals("PKCS12", customizedComponent.getKeyStoreType());

        assertTrue(customizedComponent.getConnectionFactory() instanceof JmsConnectionFactory);
        JmsConnectionFactory connectionFactory = (JmsConnectionFactory) customizedComponent.getConnectionFactory();
        assertNull(connectionFactory.getUsername());
        assertNull(connectionFactory.getPassword());
        assertEquals(
                "amqps://localhost:5672?transport.trustStoreLocation=server-ca-truststore.p12&transport.trustStoreType=PKCS12&transport.trustStorePassword=securepass&transport.keyStoreLocation=server-keystore.p12&transport.keyStoreType=PKCS12&transport.keyStorePassword=securepass",
                connectionFactory.getRemoteURI());
        assertEquals("topic://", connectionFactory.getTopicPrefix());
    }

    @Test
    public void testEnabledSslComponent() {
        AMQPComponent amqpSslEnabledComponent
                = contextExtension.getContext().getComponent("amqps-enabled", AMQPComponent.class);
        assertTrue(amqpSslEnabledComponent.getUseSsl());
        assertNull(amqpSslEnabledComponent.getTrustStoreLocation());
        assertNull(amqpSslEnabledComponent.getTrustStorePassword());
        assertEquals("JKS", amqpSslEnabledComponent.getTrustStoreType());
        assertNull(amqpSslEnabledComponent.getKeyStoreLocation());
        assertNull(amqpSslEnabledComponent.getKeyStorePassword());
        assertEquals("JKS", amqpSslEnabledComponent.getKeyStoreType());

        assertTrue(amqpSslEnabledComponent.getConnectionFactory() instanceof JmsConnectionFactory);
        JmsConnectionFactory connectionFactory = (JmsConnectionFactory) amqpSslEnabledComponent.getConnectionFactory();
        assertNull(connectionFactory.getUsername());
        assertNull(connectionFactory.getPassword());
        assertEquals(
                "amqps://localhost:5672?transport.trustStoreLocation=&transport.trustStoreType=JKS&transport.trustStorePassword=&transport.keyStoreLocation=&transport.keyStoreType=JKS&transport.keyStorePassword=",
                connectionFactory.getRemoteURI());
        assertEquals("topic://", connectionFactory.getTopicPrefix());
    }

    @Test
    public void testComponentPort() {
        AMQPComponent amqpComponent = contextExtension.getContext().getComponent("amqp-portonly", AMQPComponent.class);
        assertEquals(5556, amqpComponent.getPort());

        assertTrue(amqpComponent.getConnectionFactory() instanceof JmsConnectionFactory);
        JmsConnectionFactory connectionFactory = (JmsConnectionFactory) amqpComponent.getConnectionFactory();
        assertNull(connectionFactory.getUsername());
        assertNull(connectionFactory.getPassword());
        assertEquals("amqp://localhost:5556", connectionFactory.getRemoteURI());
        assertEquals("topic://", connectionFactory.getTopicPrefix());
    }

    @Test
    public void testNoTopicPrefix() {
        AMQPComponent component = contextExtension.getContext().getComponent("amqp-notopicprefix", AMQPComponent.class);
        assertFalse(component.getUseTopicPrefix());

        assertTrue(component.getConnectionFactory() instanceof JmsConnectionFactory);
        JmsConnectionFactory connectionFactory = (JmsConnectionFactory) component.getConnectionFactory();
        assertNull(connectionFactory.getUsername());
        assertNull(connectionFactory.getPassword());
        assertEquals("amqp://localhost:5672", connectionFactory.getRemoteURI());
        assertNull(connectionFactory.getTopicPrefix());
    }

    @ContextFixture
    public void configureContext(CamelContext context) {
        context.addComponent("amqp", amqpComponent("amqp://remotehost:5556"));

        AMQPComponent amqpComponent = new AMQPComponent();
        amqpComponent.setHost("remotehost");
        amqpComponent.setPort(5556);
        amqpComponent.setUsername("camel");
        amqpComponent.setPassword("rider");
        context.addComponent("amqp-configured", amqpComponent);

        AMQPComponent amqpSslComponent = new AMQPComponent();
        amqpSslComponent.setUseSsl(true);
        amqpSslComponent.setTrustStoreLocation("server-ca-truststore.p12");
        amqpSslComponent.setTrustStorePassword("securepass");
        amqpSslComponent.setTrustStoreType("PKCS12");
        amqpSslComponent.setKeyStoreLocation("server-keystore.p12");
        amqpSslComponent.setKeyStorePassword("securepass");
        amqpSslComponent.setKeyStoreType("PKCS12");
        context.addComponent("amqps-configured", amqpSslComponent);

        AMQPComponent amqpSslEnabledComponent = new AMQPComponent();
        amqpSslEnabledComponent.setUseSsl(true);
        context.addComponent("amqps-enabled", amqpSslEnabledComponent);

        AMQPComponent amqpPortOnlyComponent = new AMQPComponent();
        amqpPortOnlyComponent.setPort(5556);
        context.addComponent("amqp-portonly", amqpPortOnlyComponent);

        AMQPComponent amqpNoTopicPrefix = new AMQPComponent();
        amqpNoTopicPrefix.setUseTopicPrefix(false);
        context.addComponent("amqp-notopicprefix", amqpNoTopicPrefix);
    }
}
