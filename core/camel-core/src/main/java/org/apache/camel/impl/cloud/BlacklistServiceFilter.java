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
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.camel.cloud.ServiceDefinition;
import org.apache.camel.cloud.ServiceFilter;

public class BlacklistServiceFilter implements ServiceFilter {
    private List<ServiceDefinition> services;

    public BlacklistServiceFilter() {
        this.services = new ArrayList<>();
    }

    public BlacklistServiceFilter(List<ServiceDefinition> blacklist) {
        this.services = new ArrayList<>(blacklist);
    }

    /**
     * Set the servers to blacklist.
     *
     * @param servers server in the format: [service@]host:port.
     */
    public void setServers(List<String> servers) {
        this.services.clear();
        servers.forEach(this::addServer);
    }

    /**
     * Set the servers to blacklist.
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
     * Add a server to the known list of servers to blacklist.
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
    public List<ServiceDefinition> apply(List<ServiceDefinition> services) {
        return services.stream().filter(
            s -> this.services.stream().noneMatch(b -> b.matches(s))
        ).collect(
            Collectors.toList()
        );
    }

    List<ServiceDefinition> getBlacklistedServices() {
        return Collections.unmodifiableList(this.services);
    }

    // *************************************************************************
    // Helpers
    // *************************************************************************

    public static BlacklistServiceFilter forServices(Collection<ServiceDefinition> definitions) {
        BlacklistServiceFilter filter = new BlacklistServiceFilter();
        for (ServiceDefinition definition: definitions) {
            filter.addServer(definition);
        }

        return filter;
    }

    public static BlacklistServiceFilter forServices(ServiceDefinition... definitions) {
        BlacklistServiceFilter filter = new BlacklistServiceFilter();
        for (ServiceDefinition definition: definitions) {
            filter.addServer(definition);
        }

        return filter;
    }
}
