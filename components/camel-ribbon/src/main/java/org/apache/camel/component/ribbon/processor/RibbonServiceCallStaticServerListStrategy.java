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
package org.apache.camel.component.ribbon.processor;

import java.util.ArrayList;
import java.util.List;

import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.AbstractServerList;
import com.netflix.loadbalancer.ServerList;
import org.apache.camel.spi.ServiceCallServerListStrategy;
import org.apache.camel.util.ObjectHelper;

/**
 * A static list of known servers to be used by the Ribbon load balancer with the Camel Service Call EIP.
 * <p/>
 * You can implement custom implementations by existing this class and override the {@link #getUpdatedListOfServers()} that is called by Ribbon to refresh the known list
 * of servers. For example to periodically query a remote server registry for a list of active servers.
 */
public class RibbonServiceCallStaticServerListStrategy extends AbstractServerList<RibbonServer> implements ServerList<RibbonServer>, ServiceCallServerListStrategy<RibbonServer> {

    private IClientConfig clientConfig;
    private final List<RibbonServer> servers = new ArrayList<>();

    public RibbonServiceCallStaticServerListStrategy() {
    }

    public RibbonServiceCallStaticServerListStrategy(List<RibbonServer> servers) {
        this.servers.addAll(servers);
    }

    /**
     * Build a {@link RibbonServiceCallStaticServerListStrategy} with the initial list of servers
     *
     * @param servers servers separated by comma in the format: host:port,host2:port,host3:port and so on.
     */
    public static RibbonServiceCallStaticServerListStrategy build(String servers) {
        RibbonServiceCallStaticServerListStrategy answer = new RibbonServiceCallStaticServerListStrategy();
        String[] parts = servers.split(",");
        for (String part : parts) {
            String host = ObjectHelper.before(part, ":");
            String port = ObjectHelper.after(part, ":");
            int num = Integer.valueOf(port);
            answer.addServer(host, num);
        }
        return answer;
    }

    /**
     * Add a server to the known list of servers.
     */
    public void addServer(RibbonServer server) {
        servers.add(server);
    }

    /**
     * Add a server to the known list of servers.
     */
    public void addServer(String host, int port) {
        servers.add(new RibbonServer(host, port));
    }

    /**
     * Remove an existing server from the list of known servers.
     */
    public void removeServer(String host, int port) {
        servers.remove(new RibbonServer(host, port));
    }

    @Override
    public void initWithNiwsConfig(IClientConfig clientConfig) {
        this.clientConfig = clientConfig;
    }

    @Override
    public List<RibbonServer> getInitialListOfServers() {
        return servers;
    }

    @Override
    public List<RibbonServer> getUpdatedListOfServers() {
        return servers;
    }

    @Override
    public List<RibbonServer> getInitialListOfServers(String name) {
        return getInitialListOfServers();
    }

    @Override
    public List<RibbonServer> getUpdatedListOfServers(String name) {
        return getUpdatedListOfServers();
    }
}
