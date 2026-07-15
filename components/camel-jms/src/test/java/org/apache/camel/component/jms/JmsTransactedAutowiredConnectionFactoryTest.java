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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.artemis.services.ArtemisService;
import org.apache.camel.test.infra.artemis.services.ArtemisServiceFactory;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that component-level transacted=true does not prevent ConnectionFactory autowiring from the registry.
 *
 * Reproducer for CAMEL-24072.
 */
public class JmsTransactedAutowiredConnectionFactoryTest extends CamelTestSupport {

    @RegisterExtension
    public static ArtemisService service = ArtemisServiceFactory.createVMService();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        // register the CF in the registry so it can be autowired
        ConnectionFactory cf = new ActiveMQConnectionFactory(service.serviceAddress());
        camelContext.getRegistry().bind("myConnectionFactory", cf);

        // add a JMS component with transacted=true but NO explicit ConnectionFactory
        JmsComponent jms = new JmsComponent();
        jms.setTransacted(true);
        camelContext.addComponent("jms", jms);

        return camelContext;
    }

    @Test
    public void testTransactedComponentAutowiresConnectionFactory() {
        JmsComponent jms = context.getComponent("jms", JmsComponent.class);
        assertNotNull(jms);

        JmsConfiguration config = jms.getConfiguration();
        assertNotNull(config.getConnectionFactory(), "ConnectionFactory should have been autowired from the registry");
        assertTrue(config.isTransacted(), "transacted should still be true");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("jms:queue:CAMEL-24072").to("mock:result");
            }
        };
    }
}
