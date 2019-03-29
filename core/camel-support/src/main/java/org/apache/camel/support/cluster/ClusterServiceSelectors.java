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
package org.apache.camel.support.cluster;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.camel.cluster.CamelClusterService;
import org.apache.camel.cluster.CamelClusterService.Selector;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ClusterServiceSelectors {
    public static final Selector DEFAULT_SELECTOR = new SelectSingle();
    private static final Logger LOGGER = LoggerFactory.getLogger(ClusterServiceSelectors.class);

    private ClusterServiceSelectors() {
    }

    public static final class SelectSingle implements Selector {
        @Override
        public Optional<CamelClusterService> select(Collection<CamelClusterService> services) {
            if (services != null && services.size() == 1) {
                return Optional.of(services.iterator().next());
            } else {
                LOGGER.warn("Multiple CamelClusterService instances available (items={})", services);
            }

            return Optional.empty();
        }
    }

    public static final class SelectFirst implements CamelClusterService.Selector {
        @Override
        public Optional<CamelClusterService> select(Collection<CamelClusterService> services) {
            return ObjectHelper.isNotEmpty(services)
                ? Optional.of(services.iterator().next())
                : Optional.empty();
        }
    }

    public static final class SelectByOrder implements CamelClusterService.Selector {
        @Override
        public Optional<CamelClusterService> select(Collection<CamelClusterService> services) {
            Optional<Map.Entry<Integer, List<CamelClusterService>>> highPriorityServices = services.stream()
                .collect(Collectors.groupingBy(CamelClusterService::getOrder))
                .entrySet().stream()
                    .min(Comparator.comparingInt(Map.Entry::getKey));


            if (highPriorityServices.isPresent()) {
                if (highPriorityServices.get().getValue().size() == 1) {
                    return Optional.of(highPriorityServices.get().getValue().iterator().next());
                } else {
                    LOGGER.warn("Multiple CamelClusterService instances available for highest priority (order={}, items={})",
                        highPriorityServices.get().getKey(),
                        highPriorityServices.get().getValue()
                    );
                }
            }

            return Optional.empty();
        }
    }

    public static final class SelectByType implements CamelClusterService.Selector {
        private final Class<? extends CamelClusterService> type;

        public SelectByType(Class<? extends CamelClusterService> type) {
            this.type = type;
        }

        @Override
        public Optional<CamelClusterService> select(Collection<CamelClusterService> services) {
            for (CamelClusterService service : services) {
                if (type.isInstance(service)) {
                    return Optional.of(service);
                }
            }

            return Optional.empty();
        }
    }

    public static final class SelectByAttribute implements CamelClusterService.Selector {
        private final String key;
        private final Object value;

        public SelectByAttribute(String key, Object value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public Optional<CamelClusterService> select(Collection<CamelClusterService> services) {
            for (CamelClusterService service : services) {
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

    public static CamelClusterService.Selector defaultSelector() {
        return DEFAULT_SELECTOR;
    }

    public static CamelClusterService.Selector single() {
        return new SelectSingle();
    }

    public static CamelClusterService.Selector first() {
        return new SelectFirst();
    }

    public static CamelClusterService.Selector order() {
        return new SelectByOrder();
    }

    public static CamelClusterService.Selector type(Class<? extends CamelClusterService> type) {
        return new SelectByType(type);
    }

    public static CamelClusterService.Selector attribute(String key, Object value) {
        return new SelectByAttribute(key, value);
    }
}
