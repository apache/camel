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
package org.apache.camel.impl.cloud;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.camel.cloud.ServiceRegistry;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ServiceRegistrySelectors {
    public static final ServiceRegistry.Selector DEFAULT_SELECTOR = new SelectSingle();
    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceRegistrySelectors.class);

    private ServiceRegistrySelectors() {
    }

    public static final class SelectSingle implements ServiceRegistry.Selector {
        @Override
        public Optional<ServiceRegistry> select(Collection<ServiceRegistry> services) {
            if (services != null && services.size() == 1) {
                return Optional.of(services.iterator().next());
            } else {
                LOGGER.warn("Multiple ServiceRegistry instances available (items={})", services);
            }

            return Optional.empty();
        }
    }

    public static final class SelectFirst implements ServiceRegistry.Selector {
        @Override
        public Optional<ServiceRegistry> select(Collection<ServiceRegistry> services) {
            return ObjectHelper.isNotEmpty(services)
                ? Optional.of(services.iterator().next())
                : Optional.empty();
        }
    }

    public static final class SelectByOrder implements ServiceRegistry.Selector {
        @Override
        public Optional<ServiceRegistry> select(Collection<ServiceRegistry> services) {
            Optional<Map.Entry<Integer, List<ServiceRegistry>>> highPriorityServices = services.stream()
                .collect(Collectors.groupingBy(ServiceRegistry::getOrder))
                .entrySet().stream()
                    .min(Comparator.comparingInt(Map.Entry::getKey));


            if (highPriorityServices.isPresent()) {
                if (highPriorityServices.get().getValue().size() == 1) {
                    return Optional.of(highPriorityServices.get().getValue().iterator().next());
                } else {
                    LOGGER.warn("Multiple ServiceRegistry instances available for highest priority (order={}, items={})",
                        highPriorityServices.get().getKey(),
                        highPriorityServices.get().getValue()
                    );
                }
            }

            return Optional.empty();
        }
    }

    public static final class SelectByType implements ServiceRegistry.Selector {
        private final Class<? extends ServiceRegistry> type;

        public SelectByType(Class<? extends ServiceRegistry> type) {
            this.type = type;
        }

        @Override
        public Optional<ServiceRegistry> select(Collection<ServiceRegistry> services) {
            for (ServiceRegistry service : services) {
                if (type.isInstance(service)) {
                    return Optional.of(service);
                }
            }

            return Optional.empty();
        }
    }

    public static final class SelectByAttribute implements ServiceRegistry.Selector {
        private final String key;
        private final Object value;

        public SelectByAttribute(String key, Object value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public Optional<ServiceRegistry> select(Collection<ServiceRegistry> services) {
            for (ServiceRegistry service : services) {
                Map<String, Object> attributes = service.getAttributes();

                if (ObjectHelper.equal(attributes.get(key), value)) {
                    return Optional.of(service);
                }
            }

            return Optional.empty();
        }
    }

    // **********************************
    // Helpers
    // **********************************

    public static ServiceRegistry.Selector defaultSelector() {
        return DEFAULT_SELECTOR;
    }

    public static ServiceRegistry.Selector single() {
        return new SelectSingle();
    }

    public static ServiceRegistry.Selector first() {
        return new SelectFirst();
    }

    public static ServiceRegistry.Selector order() {
        return new SelectByOrder();
    }

    public static ServiceRegistry.Selector type(Class<? extends ServiceRegistry> type) {
        return new SelectByType(type);
    }

    public static ServiceRegistry.Selector attribute(String key, Object value) {
        return new SelectByAttribute(key, value);
    }
}
