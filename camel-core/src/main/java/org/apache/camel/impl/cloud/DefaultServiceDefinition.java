/**
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

import org.apache.camel.cloud.ServiceDefinition;
import org.apache.camel.cloud.ServiceHealth;

public class DefaultServiceDefinition implements ServiceDefinition {
    private static final ServiceHealth DEFAULT_SERVICE_HEALTH = new DefaultServiceHealth();

    private final String name;
    private final String host;
    private final int port;
    private final Map<String, String> meta;
    private final ServiceHealth health;

    public DefaultServiceDefinition(String name, String host, int port) {
        this.name = name;
        this.host = host;
        this.port = port;
        this.meta = Collections.emptyMap();
        this.health = DEFAULT_SERVICE_HEALTH;
    }

    public DefaultServiceDefinition(String name, String host, int port, ServiceHealth health) {
        this.name = name;
        this.host = host;
        this.port = port;
        this.meta = Collections.emptyMap();
        this.health = health;
    }

    public DefaultServiceDefinition(String name, String host, int port, Map<String, String> meta) {
        this.name = name;
        this.host = host;
        this.port = port;
        this.meta = meta != null ? Collections.unmodifiableMap(new HashMap<>(meta)) : Collections.emptyMap();
        this.health = DEFAULT_SERVICE_HEALTH;
    }

    public DefaultServiceDefinition(String name, String host, int port, Map<String, String> meta, ServiceHealth health) {
        this.name = name;
        this.host = host;
        this.port = port;
        this.meta = meta != null ? Collections.unmodifiableMap(new HashMap<>(meta)) : Collections.emptyMap();
        this.health = health;
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
        return "DefaultServiceCallService[" + name + "@" + host + ":" + port + "]";
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

        if (port != that.port) {
            return false;
        }
        if (name != null ? !name.equals(that.name) : that.name != null) {
            return false;
        }
        return host != null ? host.equals(that.host) : that.host == null;
    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (host != null ? host.hashCode() : 0);
        result = 31 * result + port;
        return result;
    }
}