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
import org.apache.camel.spi.KeyValueRepository;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.MemoryKeyValueRepository;
import org.apache.camel.support.service.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The State Store component provides a simple, unified key-value store API backed by a {@link KeyValueRepository}.
 *
 * @since 4.23
 */
@Component("state-store")
public class StateStoreComponent extends DefaultComponent {

    private static final Logger LOG = LoggerFactory.getLogger(StateStoreComponent.class);

    private final ConcurrentHashMap<String, KeyValueRepository> backends = new ConcurrentHashMap<>();

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        StateStoreEndpoint endpoint = new StateStoreEndpoint(uri, this);
        endpoint.setStoreName(remaining);
        setProperties(endpoint, parameters);
        return endpoint;
    }

    /**
     * Returns the backend for the given store name, creating one if needed. Uses first-one-wins semantics: if a backend
     * already exists for this store name, subsequent calls return the existing one regardless of the explicit backend
     * passed.
     */
    KeyValueRepository getOrCreateBackend(String storeName, KeyValueRepository explicitBackend) {
        if (explicitBackend != null) {
            KeyValueRepository existing = backends.putIfAbsent(storeName, explicitBackend);
            if (existing != null) {
                if (existing != explicitBackend) {
                    LOG.warn("Store '{}' already has a backend ({}). Ignoring explicit backend=# reference ({}).",
                            storeName, existing.getClass().getSimpleName(), explicitBackend.getClass().getSimpleName());
                }
                return existing;
            }
            ServiceHelper.startService(explicitBackend);
            return explicitBackend;
        }
        return backends.computeIfAbsent(storeName, k -> {
            // Auto-discover a KeyValueRepository from the registry
            Set<KeyValueRepository> found = getCamelContext().getRegistry().findByType(KeyValueRepository.class);
            KeyValueRepository backend;
            if (found.size() == 1) {
                backend = found.iterator().next();
                LOG.debug("Auto-discovered KeyValueRepository '{}' for store '{}'",
                        backend.getClass().getSimpleName(), storeName);
            } else {
                if (found.size() > 1) {
                    LOG.warn(
                            "Found {} KeyValueRepository instances in the registry for store '{}'. "
                             + "Cannot auto-select — falling back to MemoryKeyValueRepository. "
                             + "Use an explicit backend=# reference to choose one.",
                            found.size(), storeName);
                } else {
                    LOG.info("No KeyValueRepository found in the registry for store '{}'. Using MemoryKeyValueRepository.",
                            storeName);
                }
                backend = new MemoryKeyValueRepository();
            }
            ServiceHelper.startService(backend);
            return backend;
        });
    }

    @Override
    protected void doStop() throws Exception {
        Exception firstException = null;
        for (KeyValueRepository backend : backends.values()) {
            try {
                ServiceHelper.stopService(backend);
            } catch (Exception e) {
                LOG.warn("Error stopping backend: {}", e.getMessage(), e);
                if (firstException == null) {
                    firstException = e;
                }
            }
        }
        backends.clear();
        super.doStop();
        if (firstException != null) {
            throw firstException;
        }
    }
}
