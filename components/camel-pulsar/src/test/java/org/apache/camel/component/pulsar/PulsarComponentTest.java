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

import org.apache.camel.component.pulsar.configuration.PulsarEndpointConfiguration;
import org.apache.camel.component.pulsar.utils.AutoConfiguration;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class PulsarComponentTest extends CamelTestSupport {

    private AutoConfiguration autoConfiguration;

    @Before
    public void setUp() {
        autoConfiguration = mock(AutoConfiguration.class);
    }

    @Test
    public void testProducer() throws Exception {
        PulsarEndpointConfiguration configuration = new PulsarEndpointConfiguration();
        configuration.setPulsarBrokerUrl("pulsar://localhost:6650");
        PulsarComponent component = new PulsarComponent(context, configuration, null);

        PulsarEndpoint endpoint = (PulsarEndpoint) component.createEndpoint("pulsar://persistent/omega-pl/fulfilment/BatchCreated?numberOfConsumers=10&subscriptionName=batch-created-subscription&subscriptionType=Shared");

        endpoint.isSingleton();
    }

    @Test
    public void testProducerAutoconfigures() throws Exception {
        PulsarEndpointConfiguration configuration = new PulsarEndpointConfiguration();
        configuration.setPulsarBrokerUrl("pulsar://localhost:6650");
        PulsarComponent component = new PulsarComponent(context, configuration, autoConfiguration);

        PulsarEndpoint endpoint = (PulsarEndpoint) component.createEndpoint("pulsar://persistent/omega-pl/fulfilment/BatchCreated?numberOfConsumers=10&subscriptionName=batch-created-subscription&subscriptionType=Shared");

        endpoint.isSingleton();

        verify(autoConfiguration).ensureNameSpaceAndTenant(Matchers.anyString());
    }
}
