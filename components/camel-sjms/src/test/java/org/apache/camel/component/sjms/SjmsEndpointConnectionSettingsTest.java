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
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.SimpleRegistry;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SjmsEndpointConnectionSettingsTest extends CamelTestSupport {
    private final ActiveMQConnectionFactory connectionFactory
            = new ActiveMQConnectionFactory("vm://broker?broker.persistent=false&broker.useJmx=false");

    @Test
    public void testConnectionFactory() {
        Endpoint endpoint
                = context.getEndpoint("sjms:queue:test.SjmsEndpointConnectionSettingsTest?connectionFactory=#activemq");
        assertNotNull(endpoint);
        assertTrue(endpoint instanceof SjmsEndpoint);
        SjmsEndpoint qe = (SjmsEndpoint) endpoint;
        assertEquals(connectionFactory, qe.getConnectionFactory());
    }

    @Override
    protected CamelContext createCamelContext() {
        SimpleRegistry registry = new SimpleRegistry();
        registry.bind("activemq", connectionFactory);
        return new DefaultCamelContext(registry);
    }
}
