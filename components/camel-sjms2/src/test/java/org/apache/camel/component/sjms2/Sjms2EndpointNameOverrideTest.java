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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Sjms2EndpointNameOverrideTest extends CamelTestSupport {

    private static final String BEAN_NAME = "not-sjms";

    @RegisterExtension
    public ArtemisService service = ArtemisServiceFactory.createVMService();

    @Override
    protected boolean useJmx() {
        return true;
    }

    @Test
    public void testDefaults() {
        Endpoint endpoint = context.getEndpoint(BEAN_NAME + ":test");
        assertNotNull(endpoint);
        assertTrue(endpoint instanceof Sjms2Endpoint);
        Sjms2Endpoint sjms = (Sjms2Endpoint) endpoint;
        assertEquals(BEAN_NAME + "://test", sjms.getEndpointUri());
        assertEquals(ExchangePattern.InOnly, sjms.createExchange().getPattern());
    }

    @Test
    public void testQueueEndpoint() {
        Endpoint sjms = context.getEndpoint(BEAN_NAME + ":queue:test");
        assertNotNull(sjms);
        assertTrue(sjms instanceof Sjms2Endpoint);
        assertEquals(BEAN_NAME + "://queue:test", sjms.getEndpointUri());
    }

    @Test
    public void testTopicEndpoint() {
        Endpoint sjms = context.getEndpoint(BEAN_NAME + ":topic:test");
        assertNotNull(sjms);
        assertTrue(sjms instanceof Sjms2Endpoint);
        assertEquals(BEAN_NAME + "://topic:test", sjms.getEndpointUri());
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ActiveMQConnectionFactory connectionFactory
                = new ActiveMQConnectionFactory(service.serviceAddress());
        Sjms2Component component = new Sjms2Component();
        component.setConnectionFactory(connectionFactory);
        camelContext.addComponent(BEAN_NAME, component);

        return camelContext;
    }
}
