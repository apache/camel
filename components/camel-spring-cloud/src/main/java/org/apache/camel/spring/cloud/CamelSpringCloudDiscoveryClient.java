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

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.camel.cloud.ServiceDiscovery;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;

public class CamelSpringCloudDiscoveryClient implements DiscoveryClient {
    private final String description;
    private final ServiceDiscovery serviceDiscovery;
    private ServiceInstance localInstance;

    public CamelSpringCloudDiscoveryClient(String description, ServiceDiscovery serviceDiscovery) {
        this(description, null, serviceDiscovery);
    }

    public CamelSpringCloudDiscoveryClient(String description, ServiceInstance localServiceDiscovery, ServiceDiscovery serviceDiscovery) {
        this.description = description;
        this.serviceDiscovery = serviceDiscovery;
        this.localInstance = localServiceDiscovery;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public ServiceInstance getLocalServiceInstance() {
        return this.localInstance;
    }

    @Override
    public List<ServiceInstance> getInstances(String serviceId) {
        return serviceDiscovery.getServices(serviceId).stream()
            .map(s -> new DefaultServiceInstance(s.getName(), s.getHost(), s.getPort(), false, s.getMetadata()))
            .collect(Collectors.toList());
    }

    @Override
    public List<String> getServices() {
        return Collections.emptyList();
    }
}
