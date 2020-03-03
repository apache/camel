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
package org.apache.camel.component.pulsar;

import org.apache.camel.component.pulsar.utils.AutoConfiguration;
import org.apache.camel.component.pulsar.utils.consumers.SubscriptionType;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PulsarComponentTest extends CamelTestSupport {

    private AutoConfiguration autoConfiguration;

    @Override
    @Before
    public void setUp() throws Exception {
        autoConfiguration = mock(AutoConfiguration.class);
        super.setUp();
    }

    @Test
    public void testPulsarEndpointConfiguration() throws Exception {
        PulsarComponent component = context.getComponent("pulsar", PulsarComponent.class);
        component.setAutoConfiguration(autoConfiguration);

        PulsarEndpoint endpoint = (PulsarEndpoint)component
            .createEndpoint("pulsar://persistent/test/foobar/BatchCreated?numberOfConsumers=10&subscriptionName=batch-created-subscription&subscriptionType=Shared");

        assertNotNull(endpoint);
    }

    @Test
    public void testPulsarEndpointDefaultConfiguration() throws Exception {
        PulsarComponent component = context.getComponent("pulsar", PulsarComponent.class);

        PulsarEndpoint endpoint = (PulsarEndpoint)component.createEndpoint("pulsar://persistent/test/foobar/BatchCreated");

        assertNotNull(endpoint);
        assertEquals("sole-consumer", endpoint.getPulsarConfiguration().getConsumerName());
        assertEquals("cons", endpoint.getPulsarConfiguration().getConsumerNamePrefix());
        assertEquals(10, endpoint.getPulsarConfiguration().getConsumerQueueSize());
        assertEquals(1, endpoint.getPulsarConfiguration().getNumberOfConsumers());
        assertNull(endpoint.getPulsarConfiguration().getProducerName());
        assertEquals("subs", endpoint.getPulsarConfiguration().getSubscriptionName());
        assertEquals(SubscriptionType.EXCLUSIVE, endpoint.getPulsarConfiguration().getSubscriptionType());
        assertFalse(endpoint.getPulsarConfiguration().isAllowManualAcknowledgement());
    }

    @Test
    public void testProducerAutoConfigures() throws Exception {
        when(autoConfiguration.isAutoConfigurable()).thenReturn(true);
        PulsarComponent component = context.getComponent("pulsar", PulsarComponent.class);
        component.setAutoConfiguration(autoConfiguration);

        component.createEndpoint("pulsar://persistent/test/foobar/BatchCreated?numberOfConsumers=10&subscriptionName=batch-created-subscription&subscriptionType=Shared");

        verify(autoConfiguration).ensureNameSpaceAndTenant(ArgumentMatchers.anyString());
    }

    @Test
    public void testPulsarEndpointAllowManualAcknowledgementDefaultTrue() throws Exception {
        PulsarComponent component = context.getComponent("pulsar", PulsarComponent.class);
        component.getConfiguration().setAllowManualAcknowledgement(true);

        // allowManualAcknowledgement is absent as a query parameter.
        PulsarEndpoint endpoint = (PulsarEndpoint)component.createEndpoint("pulsar://persistent/test/foobar/BatchCreated");

        assertNotNull(endpoint);
        assertTrue(endpoint.getPulsarConfiguration().isAllowManualAcknowledgement());
    }

}
