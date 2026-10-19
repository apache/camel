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

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.BindToRegistry;
import org.apache.camel.support.MemoryKeyValueRepository;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that two {@link StateStoreBackendFactory} beans in the registry are not autowired, so the component falls back
 * to the default backend resolution.
 */
class StateStoreBackendFactoryAmbiguousTest extends CamelTestSupport {

    private final Set<String> calls = ConcurrentHashMap.newKeySet();

    @BindToRegistry("factory1")
    public StateStoreBackendFactory factory1() {
        return storeName -> {
            calls.add("factory1:" + storeName);
            return new MemoryKeyValueRepository();
        };
    }

    @BindToRegistry("factory2")
    public StateStoreBackendFactory factory2() {
        return storeName -> {
            calls.add("factory2:" + storeName);
            return new MemoryKeyValueRepository();
        };
    }

    @Test
    void testTwoFactoriesAreNotAutowired() {
        template.requestBodyAndHeader("state-store:orders?operation=put", "order-1", StateStoreConstants.KEY, "1");

        assertThat(template.requestBodyAndHeader("state-store:orders?operation=get", null, StateStoreConstants.KEY, "1"))
                .isEqualTo("order-1");
        assertThat(context.getComponent("state-store", StateStoreComponent.class).getBackendFactory()).isNull();
        assertThat(calls).isEmpty();
    }
}
