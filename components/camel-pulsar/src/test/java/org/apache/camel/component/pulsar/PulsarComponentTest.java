/**
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
package org.apache.camel.component.pulsar;

import org.apache.camel.component.pulsar.utils.AutoConfiguration;
import org.apache.camel.component.pulsar.utils.consumers.SubscriptionType;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;

import static org.mockito.Mockito.*;

public class PulsarComponentTest extends CamelTestSupport {

    private AutoConfiguration autoConfiguration;

    @Before
    public void setUp() throws Exception {
        autoConfiguration = mock(AutoConfiguration.class);
        super.setUp();
    }

    @Test
    public void testPulsarEndpointConfiguration() throws Exception {
        PulsarComponent component = new PulsarComponent(context, autoConfiguration, null);

        PulsarEndpoint endpoint = (PulsarEndpoint) component.createEndpoint("pulsar://persistent/omega-pl/fulfilment/BatchCreated?numberOfConsumers=10&subscriptionName=batch-created-subscription&subscriptionType=Shared");

        assertNotNull(endpoint);
    }

    @Test
    public void testPulsarEndpointDefaultConfiguration() throws Exception {
        PulsarComponent component = new PulsarComponent(context, null, null);

        PulsarEndpoint endpoint = (PulsarEndpoint) component.createEndpoint("pulsar://persistent/omega-pl/fulfilment/BatchCreated");

        assertNotNull(endpoint);
        assertEquals("sole-consumer", endpoint.getConfiguration().getConsumerName());
        assertEquals("cons", endpoint.getConfiguration().getConsumerNamePrefix());
        assertEquals(10, endpoint.getConfiguration().getConsumerQueueSize());
        assertEquals(1, endpoint.getConfiguration().getNumberOfConsumers());
        assertEquals("default-producer", endpoint.getConfiguration().getProducerName());
        assertEquals("subs", endpoint.getConfiguration().getSubscriptionName());
        assertEquals(SubscriptionType.EXCLUSIVE, endpoint.getConfiguration().getSubscriptionType());
    }

    @Test
    public void testProducerAutoConfigures() throws Exception {
        when(autoConfiguration.isAutoConfigurable()).thenReturn(true);
        PulsarComponent component = new PulsarComponent(context, autoConfiguration, null);

        component.createEndpoint("pulsar://persistent/omega-pl/fulfilment/BatchCreated?numberOfConsumers=10&subscriptionName=batch-created-subscription&subscriptionType=Shared");

        verify(autoConfiguration).ensureNameSpaceAndTenant(Matchers.anyString());
    }
}
