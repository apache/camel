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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import org.apache.camel.cloud.ServiceDefinition;
import org.apache.camel.cloud.ServiceHealth;
import org.apache.camel.util.CollectionHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;

public class DefaultServiceDefinition implements ServiceDefinition {
    private static final ServiceHealth DEFAULT_SERVICE_HEALTH = new DefaultServiceHealth();

    private final String id;
    private final String name;
    private final String host;
    private final int port;
    private final Map<String, String> meta;
    private final ServiceHealth health;

    public DefaultServiceDefinition(String name, String host, int port) {
        this(null, name, host, port, Collections.emptyMap(), DEFAULT_SERVICE_HEALTH);
    }

    public DefaultServiceDefinition(String id, String name, String host, int port) {
        this(id, name, host, port, Collections.emptyMap(), DEFAULT_SERVICE_HEALTH);
    }

    public DefaultServiceDefinition(String name, String host, int port, ServiceHealth health) {
        this(null, name, host, port, Collections.emptyMap(), health);
    }

    public DefaultServiceDefinition(String id, String name, String host, int port, ServiceHealth health) {
        this(id, name, host, port, Collections.emptyMap(), health);
    }

    public DefaultServiceDefinition(String name, String host, int port, Map<String, String> meta) {
        this(null, name, host, port, meta, DEFAULT_SERVICE_HEALTH);
    }

    public DefaultServiceDefinition(String id, String name, String host, int port, Map<String, String> meta) {
        this(id, name, host, port, meta, DEFAULT_SERVICE_HEALTH);
    }

    public DefaultServiceDefinition(String name, String host, int port, Map<String, String> meta, ServiceHealth health) {
        this(null, name, host, port, meta, health);
    }

    public DefaultServiceDefinition(String id, String name, String host, int port, Map<String, String> meta, ServiceHealth health) {
        this.id = id;
        this.name = name;
        this.host = host;
        this.port = port;
        this.meta = CollectionHelper.unmodifiableMap(meta);
        this.health = ObjectHelper.supplyIfEmpty(health, () -> DEFAULT_SERVICE_HEALTH);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getHost() {
        return host;
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public ServiceHealth getHealth() {
        return health;
    }

    @Override
    public Map<String, String> getMetadata() {
        return this.meta;
    }

    @Override
    public String toString() {
        return "DefaultServiceDefinition[" + id + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DefaultServiceDefinition that = (DefaultServiceDefinition) o;
        return getPort() == that.getPort()
            && Objects.equals(getId(), that.getId())
            && Objects.equals(getName(), that.getName())
            && Objects.equals(getHost(), that.getHost());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getName(), getHost(), getPort());
    }

    // ***************************
    // Builder
    // ***************************

    public static Stream<? extends ServiceDefinition> parse(String serverString) {
        return Stream.of(serverString.split(","))
            .map(part -> {
                String serviceId = null;
                String serviceName = StringHelper.before(part, "@");

                if (serviceName != null) {
                    serviceId = StringHelper.before(serviceName, "/");
                    serviceName = StringHelper.after(serviceName, "/");

                    if (serviceName == null) {
                        serviceName = StringHelper.before(part, "@");
                    }

                    part = StringHelper.after(part, "@");
                }

                String serviceHost = StringHelper.before(part, ":");
                String servicePort = StringHelper.after(part, ":");

                if (ObjectHelper.isNotEmpty(serviceHost) && ObjectHelper.isNotEmpty(servicePort)) {
                    return new DefaultServiceDefinition(serviceId, serviceName, serviceHost, Integer.valueOf(servicePort));
                }

                return null;
            }
        ).filter(Objects::nonNull);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder to construct ServiceDefinition.
     */
    public static class Builder {
        private String id;
        private String name;
        private String host;
        private Integer port;
        private Map<String, String> meta;
        private ServiceHealth health;

        public Builder from(ServiceDefinition source) {
            withId(source.getId());
            withName(source.getName());
            withHost(source.getHost());
            withPort(source.getPort());
            withMeta(source.getMetadata());
            withHealth(source.getHealth());

            return this;
        }

        public Builder from(Map<String, String> properties) {
            ObjectHelper.ifNotEmpty(properties.get(ServiceDefinition.SERVICE_META_ID), this::withId);
            ObjectHelper.ifNotEmpty(properties.get(ServiceDefinition.SERVICE_META_NAME), this::withName);
            ObjectHelper.ifNotEmpty(properties.get(ServiceDefinition.SERVICE_META_HOST), this::withHost);
            ObjectHelper.ifNotEmpty(properties.get(ServiceDefinition.SERVICE_META_PORT), this::withPort);

            for (Map.Entry<String, String> entry : properties.entrySet()) {
                if (!entry.getKey().startsWith(ServiceDefinition.SERVICE_META_PREFIX)) {
                    continue;
                }

                addMeta(entry.getKey(), entry.getValue());
            }

            return this;
        }

        public Builder withId(String id) {
            this.id = id;
            return this;
        }

        public String id() {
            return id;
        }

        public Builder withName(String name) {
            this.name = name;
            return this;
        }

        public String name() {
            return name;
        }

        public Builder withHost(String host) {
            this.host = host;
            return this;
        }

        public String host() {
            return host;
        }

        public Builder withPort(Integer port) {
            this.port = port;
            return this;
        }

        public Builder withPort(String port) {
            if (port != null) {
                withPort(Integer.parseInt(port));
            }

            return this;
        }

        public Integer port() {
            return port;
        }

        public Builder withMeta(Map<String, String> meta) {
            this.meta = new HashMap<>(meta);
            return this;
        }

        public Builder addMeta(String key, String val) {
            if (this.meta == null) {
                this.meta = new HashMap<>();
            }

            this.meta.put(key, val);

            return this;
        }

        public Builder addAllMeta(Map<String, String> meta) {
            if (this.meta == null) {
                this.meta = new HashMap<>();
            }

            this.meta.putAll(meta);

            return this;
        }

        public Map<String, String> meta() {
            return meta;
        }

        public Builder withHealth(ServiceHealth health) {
            this.health = health;

            return this;
        }

        public ServiceHealth health() {
            return health;
        }

        public ServiceDefinition build() {
            return new DefaultServiceDefinition(id, name, host, port != null ? port : -1, meta, health);
        }
    }
}
