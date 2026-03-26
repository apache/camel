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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.Endpoint;
import org.apache.camel.support.DefaultComponent;

/**
 * The State Store component provides a simple, unified key-value store API with pluggable backends.
 */
@org.apache.camel.spi.annotations.Component("state-store")
public class StateStoreComponent extends DefaultComponent {

    private final ConcurrentHashMap<String, StateStoreBackend> backends = new ConcurrentHashMap<>();

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        StateStoreEndpoint endpoint = new StateStoreEndpoint(uri, this);
        endpoint.setStoreName(remaining);
        setProperties(endpoint, parameters);
        return endpoint;
    }

    StateStoreBackend getOrCreateBackend(String storeName, StateStoreBackend explicitBackend) {
        if (explicitBackend != null) {
            return backends.computeIfAbsent(storeName, k -> explicitBackend);
        }
        return backends.computeIfAbsent(storeName, k -> {
            // Auto-discover a StateStoreBackend from the registry
            Set<StateStoreBackend> found = getCamelContext().getRegistry().findByType(StateStoreBackend.class);
            if (found.size() == 1) {
                return found.iterator().next();
            }
            return new InMemoryStateStoreBackend();
        });
    }

    @Override
    protected void doStop() throws Exception {
        for (StateStoreBackend backend : backends.values()) {
            backend.stop();
        }
        backends.clear();
        super.doStop();
    }
}
