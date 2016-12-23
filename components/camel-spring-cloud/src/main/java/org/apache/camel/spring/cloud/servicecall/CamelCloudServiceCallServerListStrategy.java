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

package org.apache.camel.spring.cloud.servicecall;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.camel.impl.remote.DefaultServiceCallServer;
import org.apache.camel.impl.remote.DefaultServiceCallServerListStrategy;
import org.apache.camel.spi.ServiceCallServer;
import org.springframework.cloud.client.discovery.DiscoveryClient;

public class CamelCloudServiceCallServerListStrategy extends DefaultServiceCallServerListStrategy<ServiceCallServer> {
    private final List<DiscoveryClient> clients;

    public CamelCloudServiceCallServerListStrategy(List<DiscoveryClient> clients) {
        this.clients = new ArrayList<>(clients);
    }

    @Override
    public List<ServiceCallServer> getInitialListOfServers(String name) {
        return getServers(name);
    }

    @Override
    public List<ServiceCallServer> getUpdatedListOfServers(String name) {
        return getServers(name);
    }

    private List<ServiceCallServer> getServers(String name) {
        return clients.stream()
            .flatMap(c -> c.getInstances(name).stream())
            .map(s -> new DefaultServiceCallServer(s.getHost(), s.getPort(), s.getMetadata()))
            .collect(Collectors.toList());
    }
}
