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
        assertTrue(customizedComponent.getConnectionFactory() instanceof JmsConnectionFactory);

        JmsConnectionFactory connectionFactory = (JmsConnectionFactory) customizedComponent.getConnectionFactory();
        assertNull(connectionFactory.getUsername());
        assertNull(connectionFactory.getPassword());
        assertEquals(
                "amqps://localhost:5672?transport.trustStoreLocation=server-ca-truststore.p12&transport.trustStoreType=PKCS12&transport.trustStorePassword=securepass&transport.keyStoreLocation=server-keystore.p12&transport.keyStoreType=PKCS12&transport.keyStorePassword=securepass",
                connectionFactory.getRemoteURI());
        assertEquals("topic://", connectionFactory.getTopicPrefix());
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
    }
}
