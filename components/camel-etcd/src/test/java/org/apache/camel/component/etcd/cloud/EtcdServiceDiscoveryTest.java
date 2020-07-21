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
package org.apache.camel.component.etcd.cloud;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.ObjectMapper;
import mousio.etcd4j.EtcdClient;
import mousio.etcd4j.responses.EtcdException;
import org.apache.camel.cloud.ServiceDefinition;
import org.apache.camel.component.etcd.EtcdConfiguration;
import org.apache.camel.component.etcd.EtcdHelper;
import org.apache.camel.component.etcd.support.EtcdTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EtcdServiceDiscoveryTest extends EtcdTestSupport {
    private static final ObjectMapper MAPPER = EtcdHelper.createObjectMapper();
    private static final AtomicInteger PORT = new AtomicInteger(0);

    private EtcdClient client;
    private EtcdConfiguration configuration;

    @Override
    public void doPreSetup() throws Exception {
        configuration = new EtcdConfiguration();
        configuration.setCamelContext(context);
        configuration.setUris(getClientUri());

        client = getClient();
        try {
            client.deleteDir(configuration.getServicePath()).recursive().send().get();
        } catch (EtcdException e) {
            // Ignore
        }
    }

    @Override
    protected void cleanupResources() throws Exception {
        try {
            client.deleteDir(configuration.getServicePath()).recursive().send().get();
            client.close();
            client = null;
        } catch (EtcdException e) {
            // Ignore
        }

        super.cleanupResources();
    }

    @Test
    public void testOnDemandDiscovery() throws Exception {
        for (int i = 0; i < 3; i++) {
            addServer(client, "serviceType-1");
        }
        for (int i = 0; i < 2; i++) {
            addServer(client, "serviceType-2");
        }

        EtcdOnDemandServiceDiscovery strategy = new EtcdOnDemandServiceDiscovery(configuration);
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

        strategy.stop();
    }

    @Test
    public void testWatchDiscovery() throws Exception {
        addServer(client, "serviceType-3");

        EtcdWatchServiceDiscovery strategy = new EtcdWatchServiceDiscovery(configuration);
        strategy.start();

        assertEquals(1, strategy.getServices("serviceType-3").size());

        addServer(client, "serviceType-3");
        addServer(client, "serviceType-3");
        addServer(client, "serviceType-4");

        Thread.sleep(250);

        assertEquals(3, strategy.getServices("serviceType-3").size());

        strategy.stop();
    }

    private void addServer(EtcdClient client, String name) throws Exception {
        int port = PORT.incrementAndGet();

        Map<String, String> tags = new HashMap<>();
        tags.put("service_name", name);
        tags.put("port_delta", Integer.toString(port));

        Map<String, Object> server = new HashMap<>();
        server.put("name", name);
        server.put("address", "127.0.0.1");
        server.put("port", 8000 + port);
        server.put("tags", tags);

        client.put(configuration.getServicePath() + "service-" + port, MAPPER.writeValueAsString(server)).send().get();
    }
}
