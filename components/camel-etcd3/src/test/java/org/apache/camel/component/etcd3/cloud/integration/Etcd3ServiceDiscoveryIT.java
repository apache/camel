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
package org.apache.camel.component.etcd3.cloud.integration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.common.exception.EtcdException;
import io.etcd.jetcd.options.DeleteOption;
import org.apache.camel.cloud.ServiceDefinition;
import org.apache.camel.component.etcd3.Etcd3Configuration;
import org.apache.camel.component.etcd3.Etcd3Helper;
import org.apache.camel.component.etcd3.cloud.Etcd3OnDemandServiceDiscovery;
import org.apache.camel.component.etcd3.cloud.Etcd3WatchServiceDiscovery;
import org.apache.camel.component.etcd3.support.Etcd3TestSupport;
import org.junit.jupiter.api.Test;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Etcd3ServiceDiscoveryIT extends Etcd3TestSupport {
    private static final ObjectMapper MAPPER = Etcd3Helper.createObjectMapper();
    private static final AtomicInteger PORT = new AtomicInteger();

    private Client client;
    private Etcd3Configuration configuration;

    @Override
    public void doPreSetup() throws Exception {
        configuration = new Etcd3Configuration();
        configuration.setEndpoints(service.getServiceAddress());

        client = getClient();
        try {
            client.getKVClient().delete(ByteSequence.from(configuration.getServicePath().getBytes()),
                    DeleteOption.newBuilder().isPrefix(true).build()).get();
        } catch (EtcdException e) {
            // Ignore
        }
    }

    @Override
    protected void cleanupResources() throws Exception {
        try {
            client.getKVClient().delete(ByteSequence.from(configuration.getServicePath().getBytes()),
                    DeleteOption.newBuilder().isPrefix(true).build()).get();
            client.close();
            client = null;
        } catch (EtcdException e) {
            // Ignore
        }

        super.cleanupResources();
    }

    @Test
    void testOnDemandDiscovery() throws Exception {
        for (int i = 0; i < 3; i++) {
            addServer("serviceType-1");
        }
        for (int i = 0; i < 2; i++) {
            addServer("serviceType-2");
        }

        try (Etcd3OnDemandServiceDiscovery strategy = new Etcd3OnDemandServiceDiscovery(configuration)) {
            strategy.start();

            List<ServiceDefinition> type1 = strategy.getServices("serviceType-1");
            assertEquals(3, type1.size());
            for (ServiceDefinition service : type1) {
                assertNotNull(service.getMetadata());
                assertTrue(service.getMetadata().containsKey("service_name"));
                assertTrue(service.getMetadata().containsKey("port_delta"));
            }

            List<ServiceDefinition> type2 = strategy.getServices("serviceType-2");
            assertEquals(2, type2.size());
            for (ServiceDefinition service : type2) {
                assertNotNull(service.getMetadata());
                assertTrue(service.getMetadata().containsKey("service_name"));
                assertTrue(service.getMetadata().containsKey("port_delta"));
            }
        }
    }

    @Test
    void testWatchDiscovery() throws Exception {
        addServer("serviceType-3");

        try (Etcd3WatchServiceDiscovery strategy = new Etcd3WatchServiceDiscovery(configuration)) {
            strategy.start();

            assertEquals(1, strategy.getServices("serviceType-3").size());

            addServer("serviceType-3");
            addServer("serviceType-3");
            addServer("serviceType-4");

            await().atMost(1, SECONDS).untilAsserted(
                    () -> assertEquals(3, strategy.getServices("serviceType-3").size()));
        }
    }

    private void addServer(String name) throws Exception {
        int port = PORT.incrementAndGet();

        Map<String, String> tags = new HashMap<>();
        tags.put("service_name", name);
        tags.put("port_delta", Integer.toString(port));

        Map<String, Object> server = new HashMap<>();
        server.put("name", name);
        server.put("address", "127.0.0.1");
        server.put("port", 8000 + port);
        server.put("tags", tags);

        client.getKVClient().put(ByteSequence.from((configuration.getServicePath() + "service-" + port).getBytes()),
                ByteSequence.from(MAPPER.writeValueAsString(server).getBytes())).get();
    }
}
