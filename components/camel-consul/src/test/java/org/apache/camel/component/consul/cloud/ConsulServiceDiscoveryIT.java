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
package org.apache.camel.component.consul.cloud;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.camel.cloud.ServiceDefinition;
import org.apache.camel.cloud.ServiceDiscovery;
import org.apache.camel.component.consul.ConsulConfiguration;
import org.apache.camel.component.consul.ConsulTestSupport;
import org.apache.camel.test.AvailablePortFinder;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.kiwiproject.consul.AgentClient;
import org.kiwiproject.consul.model.agent.ImmutableRegCheck;
import org.kiwiproject.consul.model.agent.ImmutableRegistration;
import org.kiwiproject.consul.model.agent.Registration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ConsulServiceDiscoveryIT extends ConsulTestSupport {
    private AgentClient client;
    private List<Registration> registrations;

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Override
    protected void doPreSetup() throws Exception {
        super.doPreSetup();

        client = getConsul().agentClient();
        registrations = new ArrayList<>(3);

        for (int i = 0; i < 6; i++) {
            final boolean healty = ThreadLocalRandom.current().nextBoolean();
            final int port = AvailablePortFinder.getNextAvailable();

            Registration.RegCheck c = ImmutableRegCheck.builder().ttl("1m").status(healty ? "passing" : "critical").build();

            Registration r = ImmutableRegistration.builder().id("service-" + i).name("my-service").address("127.0.0.1")
                    .addTags("a-tag").addTags("key1=value1")
                    .addTags("key2=value2").addTags("healthy=" + healty).putMeta("meta-key", "meta-val").port(port).check(c)
                    .build();

            client.register(r);
            registrations.add(r);
        }
    }

    @Override
    public void doPostTearDown() throws Exception {
        super.doPostTearDown();

        registrations.forEach(r -> client.deregister(r.getId()));
    }

    // *************************************************************************
    // Test
    // *************************************************************************

    @Test
    public void testServiceDiscovery() {
        ConsulConfiguration configuration = new ConsulConfiguration();
        configuration.setUrl(service.getConsulUrl());

        ServiceDiscovery discovery = new ConsulServiceDiscovery(configuration);

        List<ServiceDefinition> services = discovery.getServices("my-service");
        assertNotNull(services);
        assertEquals(6, services.size());

        for (ServiceDefinition service : services) {
            Assertions.assertThat(service.getMetadata()).isNotEmpty();
            Assertions.assertThat(service.getMetadata()).containsEntry(ServiceDefinition.SERVICE_META_NAME, "my-service");
            Assertions.assertThat(service.getMetadata()).containsKey(ServiceDefinition.SERVICE_META_ID);
            Assertions.assertThat(service.getMetadata()).containsKey("a-tag");
            Assertions.assertThat(service.getMetadata()).containsEntry("key1", "value1");
            Assertions.assertThat(service.getMetadata()).containsEntry("key2", "value2");
            Assertions.assertThat(service.getMetadata()).containsEntry("meta-key", "meta-val");
            Assertions.assertThat(Boolean.toString(service.getHealth().isHealthy()))
                    .isEqualTo(service.getMetadata().get("healthy"));
        }
    }
}
