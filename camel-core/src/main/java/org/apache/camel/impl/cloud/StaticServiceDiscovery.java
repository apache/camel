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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.camel.cloud.ServiceDefinition;
import org.apache.camel.util.StringHelper;

/**
 * A static list of known servers Camel Service Call EIP.
 */
public class StaticServiceDiscovery extends DefaultServiceDiscovery {

    private final List<ServiceDefinition> servers;

    public StaticServiceDiscovery() {
        this.servers = new ArrayList<>();
    }

    public StaticServiceDiscovery(List<ServiceDefinition> servers) {
        this.servers = new ArrayList<>(servers);
    }

    /**
     * Set the servers.
     *
     * @param servers server in the format: [service@]host:port.
     */
    public void setServers(List<String> servers) {
        this.servers.clear();
        servers.forEach(this::addServer);
    }

    /**
     * Set the servers.
     *
     * @param servers servers separated by comma in the format: [service@]host:port,[service@]host2:port,[service@]host3:port and so on.
     */
    public void setServers(String servers) {
        this.servers.clear();
        addServer(servers);
    }

    /**
     * Add a server to the known list of servers.
     */
    public void addServer(ServiceDefinition server) {
        servers.add(server);
    }

    /**
     * Add a server to the known list of servers.
     * @param serverString servers separated by comma in the format: [service@]host:port,[service@]host2:port,[service@]host3:port and so on.
     */
    public void addServer(String serverString) {
        String[] parts = serverString.split(",");
        for (String part : parts) {
            String service = StringHelper.before(part, "@");
            if (service != null) {
                part = StringHelper.after(service, "@");
            }
            String host = StringHelper.before(part, ":");
            String port = StringHelper.after(part, ":");

            addServer(service, host, Integer.valueOf(port));
        }
    }

    /**
     * Add a server to the known list of servers.
     */
    public void addServer(String host, int port) {
        addServer(null, host, port, null);
    }

    /**
     * Add a server to the known list of servers.
     */
    public void addServer(String name, String host, int port) {
        addServer(name, host, port, null);
    }

    /**
     * Add a server to the known list of servers.
     */
    public void addServer(String name, String host, int port, Map<String, String> meta) {
        servers.add(new DefaultServiceDefinition(name, host, port, meta));
    }

    /**
     * Remove an existing server from the list of known servers.
     */
    public void removeServer(String host, int port) {
        servers.removeIf(
            s -> Objects.equals(host, s.getHost()) && port == s.getPort()
        );
    }

    /**
     * Remove an existing server from the list of known servers.
     */
    public void removeServer(String name, String host, int port) {
        servers.removeIf(
            s -> Objects.equals(name, s.getName()) && Objects.equals(host, s.getHost()) && port == s.getPort()
        );
    }

    @Override
    public List<ServiceDefinition> getUpdatedListOfServices(String name) {
        return Collections.unmodifiableList(servers);
    }
}
