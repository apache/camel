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
package org.apache.camel.component.statestore.infinispan;

import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.statestore.StateStoreConstants;
import org.apache.camel.component.statestore.StateStoreOperations;
import org.apache.camel.test.infra.infinispan.services.InfinispanService;
import org.apache.camel.test.infra.infinispan.services.InfinispanServiceFactory;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class InfinispanStateStoreBackendIT extends CamelTestSupport {

    private static final boolean IS_MAC_OS = System.getProperty("os.name", "").startsWith("Mac");

    @RegisterExtension
    static InfinispanService service = InfinispanServiceFactory.createSingletonInfinispanService();

    private InfinispanStateStoreBackend backend;

    @BeforeEach
    void clearCache() {
        if (backend != null) {
            template.requestBodyAndHeaders("direct:op", (Object) null,
                    Map.of(StateStoreConstants.OPERATION, StateStoreOperations.clear));
        }
    }

    @BindToRegistry("infinispanBackend")
    public InfinispanStateStoreBackend createBackend() {
        backend = new InfinispanStateStoreBackend();

        ConfigurationBuilder builder = new ConfigurationBuilder();
        builder.forceReturnValues(true);
        builder.addServers(service.host() + ":" + service.port());
        if (service.username() != null) {
            builder.security().authentication()
                    .username(service.username())
                    .password(service.password())
                    .saslMechanism("SCRAM-SHA-512")
                    .realm("default")
                    .serverName("infinispan");
        }
        if (IS_MAC_OS) {
            Properties properties = new Properties();
            properties.put("infinispan.client.hotrod.client_intelligence", "BASIC");
            builder.withProperties(properties);
        }
        RemoteCacheManager cacheManager = new RemoteCacheManager(builder.build(), true);
        backend.setCacheManager(cacheManager);
        backend.setCacheName("state-store-test");
        return backend;
    }

    @Test
    void testPutAndGet() {
        template.requestBodyAndHeaders(
                "direct:put", "hello",
                Map.of(StateStoreConstants.KEY, "key1"));

        Object result = template.requestBodyAndHeaders(
                "direct:get", null,
                Map.of(StateStoreConstants.KEY, "key1"));
        assertEquals("hello", result);
    }

    @Test
    void testPutReturnsOldValue() {
        template.requestBodyAndHeaders(
                "direct:put", "first",
                Map.of(StateStoreConstants.KEY, "key1"));

        Object previous = template.requestBodyAndHeaders(
                "direct:put", "second",
                Map.of(StateStoreConstants.KEY, "key1"));
        assertEquals("first", previous);
    }

    @Test
    void testDelete() {
        template.requestBodyAndHeaders(
                "direct:put", "value",
                Map.of(StateStoreConstants.KEY, "key1"));

        Object removed = template.requestBodyAndHeaders(
                "direct:delete", null,
                Map.of(StateStoreConstants.KEY, "key1"));
        assertEquals("value", removed);
    }

    @Test
    void testContains() {
        template.requestBodyAndHeaders(
                "direct:put", "value",
                Map.of(StateStoreConstants.KEY, "key1"));

        Object exists = template.requestBodyAndHeaders(
                "direct:op", null,
                Map.of(StateStoreConstants.OPERATION, StateStoreOperations.contains,
                        StateStoreConstants.KEY, "key1"));
        assertEquals(true, exists);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testKeysAndSize() {
        template.requestBodyAndHeaders(
                "direct:put", "v1",
                Map.of(StateStoreConstants.KEY, "a"));
        template.requestBodyAndHeaders(
                "direct:put", "v2",
                Map.of(StateStoreConstants.KEY, "b"));

        Set<String> keys = (Set<String>) template.requestBodyAndHeaders(
                "direct:op", null,
                Map.of(StateStoreConstants.OPERATION, StateStoreOperations.keys));
        assertEquals(Set.of("a", "b"), keys);

        Object size = template.requestBodyAndHeaders(
                "direct:op", null,
                Map.of(StateStoreConstants.OPERATION, StateStoreOperations.size));
        assertEquals(2, size);
    }

    @Test
    void testPutIfAbsent() {
        Object result = template.requestBodyAndHeaders(
                "direct:op", "first",
                Map.of(StateStoreConstants.OPERATION, StateStoreOperations.putIfAbsent,
                        StateStoreConstants.KEY, "key1"));
        assertNull(result);

        result = template.requestBodyAndHeaders(
                "direct:op", "second",
                Map.of(StateStoreConstants.OPERATION, StateStoreOperations.putIfAbsent,
                        StateStoreConstants.KEY, "key1"));
        assertEquals("first", result);
    }

    @Test
    void testTtlExpiry() throws Exception {
        template.requestBodyAndHeaders(
                "direct:put", "expiring",
                Map.of(StateStoreConstants.KEY, "ttlKey",
                        StateStoreConstants.TTL, 500L));

        Object result = template.requestBodyAndHeaders(
                "direct:get", null,
                Map.of(StateStoreConstants.KEY, "ttlKey"));
        assertEquals("expiring", result);

        Thread.sleep(700);

        result = template.requestBodyAndHeaders(
                "direct:get", null,
                Map.of(StateStoreConstants.KEY, "ttlKey"));
        assertNull(result);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:put").to("state-store:ispnStore?operation=put&backend=#infinispanBackend");
                from("direct:get").to("state-store:ispnStore?operation=get&backend=#infinispanBackend");
                from("direct:delete").to("state-store:ispnStore?operation=delete&backend=#infinispanBackend");
                from("direct:op").to("state-store:ispnStore?backend=#infinispanBackend");
            }
        };
    }
}
