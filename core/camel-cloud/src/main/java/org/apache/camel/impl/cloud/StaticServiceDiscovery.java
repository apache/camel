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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.camel.cloud.ServiceDefinition;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;

/**
 * A static list of known servers Camel Service Call EIP.
 */
public class StaticServiceDiscovery extends DefaultServiceDiscovery {
    private final List<ServiceDefinition> services;

    public StaticServiceDiscovery() {
        this.services = new ArrayList<>();
    }

    public StaticServiceDiscovery(List<ServiceDefinition> servers) {
        this.services = new ArrayList<>(servers);
    }

    /**
     * Set the servers.
     *
     * @param servers server in the format: [service@]host:port.
     */
    public void setServers(List<String> servers) {
        this.services.clear();
        servers.forEach(this::addServer);
    }

    public void addServers(String serviceName, List<String> servers) {
        for (String server : servers) {
            String host = StringHelper.before(server, ":");
            String port = StringHelper.after(server, ":");

            if (ObjectHelper.isNotEmpty(host) && ObjectHelper.isNotEmpty(port)) {
                addServer(
                    DefaultServiceDefinition.builder()
                        .withName(serviceName)
                        .withHost(host)
                        .withPort(Integer.parseInt(port))
                        .build()
                );
            }
        }
    }

    /**
     * Set the servers.
     *
     * @param servers servers separated by comma in the format: [service@]host:port,[service@]host2:port,[service@]host3:port and so on.
     */
    public void setServers(String servers) {
        this.services.clear();
        addServer(servers);
    }

    /**
     * Add a server to the known list of servers.
     */
    public void addServer(ServiceDefinition server) {
        services.add(server);
    }

    /**
     * Add a server to the known list of servers.
     * @param serverString servers separated by comma in the format: [service@]host:port,[service@]host2:port,[service@]host3:port and so on.
     */
    public void addServer(String serverString) {
        DefaultServiceDefinition.parse(serverString).forEach(this::addServer);
    }

    /**
     * Remove an existing server from the list of known servers.
     */
    public void removeServer(Predicate<ServiceDefinition> condition) {
        services.removeIf(condition);
    }

    @Override
    public List<ServiceDefinition> getServices(String name) {
        return Collections.unmodifiableList(
            services.stream()
                .filter(s -> Objects.isNull(s.getName()) || Objects.equals(name, s.getName()))
                .collect(Collectors.toList())
        );
    }

    // *************************************************************************
    // Helpers
    // *************************************************************************

    public static StaticServiceDiscovery forServices(Collection<ServiceDefinition> definitions) {
        StaticServiceDiscovery discovery = new StaticServiceDiscovery();
        for (ServiceDefinition definition: definitions) {
            discovery.addServer(definition);
        }

        return discovery;
    }

    public static StaticServiceDiscovery forServices(ServiceDefinition... definitions) {
        StaticServiceDiscovery discovery = new StaticServiceDiscovery();
        for (ServiceDefinition definition: definitions) {
            discovery.addServer(definition);
        }

        return discovery;
    }
}
