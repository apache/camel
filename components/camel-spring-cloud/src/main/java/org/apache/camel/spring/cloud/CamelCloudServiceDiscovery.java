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

package org.apache.camel.spring.cloud;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.camel.cloud.ServiceDefinition;
import org.apache.camel.impl.cloud.DefaultServiceDefinition;
import org.apache.camel.impl.cloud.DefaultServiceDiscovery;
import org.springframework.cloud.client.discovery.DiscoveryClient;

public class CamelCloudServiceDiscovery extends DefaultServiceDiscovery {
    private final List<DiscoveryClient> clients;

    public CamelCloudServiceDiscovery(List<DiscoveryClient> clients) {
        this.clients = new ArrayList<>(clients);
    }

    @Override
    public List<ServiceDefinition> getInitialListOfServices(String name) {
        return getServers(name);
    }

    @Override
    public List<ServiceDefinition> getUpdatedListOfServices(String name) {
        return getServers(name);
    }

    private List<ServiceDefinition> getServers(String name) {
        return clients.stream()
            .flatMap(c -> c.getInstances(name).stream())
            .map(s -> new DefaultServiceDefinition(s.getServiceId(), s.getHost(), s.getPort(), s.getMetadata()))
            .collect(Collectors.toList());
    }
}
