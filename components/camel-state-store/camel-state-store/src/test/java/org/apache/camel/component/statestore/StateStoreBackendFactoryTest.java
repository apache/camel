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
package org.apache.camel.component.statestore;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.KeyValueRepository;
import org.apache.camel.support.MemoryKeyValueRepository;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that an autowired {@link StateStoreBackendFactory} creates one backend per store name, takes precedence over a
 * {@link KeyValueRepository} in the registry, and does not override an explicit endpoint backend.
 */
class StateStoreBackendFactoryTest extends CamelTestSupport {

    private final Map<String, MemoryKeyValueRepository> created = new ConcurrentHashMap<>();
    private final MemoryKeyValueRepository registryBackend = new MemoryKeyValueRepository();
    private final MemoryKeyValueRepository explicitBackend = new MemoryKeyValueRepository();

    @BindToRegistry("factory")
    public StateStoreBackendFactory backendFactory() {
        return storeName -> "fallback".equals(storeName)
                ? null : created.computeIfAbsent(storeName, k -> new MemoryKeyValueRepository());
    }

    @BindToRegistry("registryBackend")
    public KeyValueRepository registryBackend() {
        return registryBackend;
    }

    @Test
    void testBackendPerStoreName() {
        assertThat(context.getComponent("state-store", StateStoreComponent.class).getBackendFactory()).isNotNull();

        template.requestBodyAndHeader("state-store:orders?operation=put", "order-1", StateStoreConstants.KEY, "1");
        template.requestBodyAndHeader("state-store:users?operation=put", "user-1", StateStoreConstants.KEY, "1");

        assertThat(template.requestBodyAndHeader("state-store:orders?operation=get", null, StateStoreConstants.KEY, "1"))
                .isEqualTo("order-1");
        assertThat(template.requestBodyAndHeader("state-store:users?operation=get", null, StateStoreConstants.KEY, "1"))
                .isEqualTo("user-1");

        assertThat(created).containsOnlyKeys("orders", "users");
        assertThat(created.get("orders").isStarted()).isTrue();
        assertThat(registryBackend.keys()).isEmpty();
    }

    @Test
    void testFactoryReturningNullFallsBackToRegistry() {
        template.requestBodyAndHeader("state-store:fallback?operation=put", "v", StateStoreConstants.KEY, "k");

        assertThat(created).doesNotContainKey("fallback");
        assertThat(registryBackend.get("k")).isEqualTo("v");
    }

    @Test
    void testExplicitBackendTakesPrecedence() {
        template.requestBodyAndHeader("direct:explicit", "v", StateStoreConstants.KEY, "k");

        assertThat(created).doesNotContainKey("explicit");
        assertThat(explicitBackend.get("k")).isEqualTo("v");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                StateStoreEndpoint explicit
                        = getContext().getEndpoint("state-store:explicit?operation=put", StateStoreEndpoint.class);
                explicit.setBackend(explicitBackend);
                from("direct:explicit").to(explicit);
            }
        };
    }
}
