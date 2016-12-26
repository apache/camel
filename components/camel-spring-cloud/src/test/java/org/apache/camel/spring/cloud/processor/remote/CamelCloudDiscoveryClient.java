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
package org.apache.camel.spring.cloud.processor.remote;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;

public class CamelCloudDiscoveryClient implements DiscoveryClient {
    private final String description;
    private final Map<String, List<ServiceInstance>> services;

    public CamelCloudDiscoveryClient(String description) {
        this(description, Collections.emptyMap());
    }

    public CamelCloudDiscoveryClient(String description, Map<String, List<ServiceInstance>> services) {
        this.description = description;
        this.services = new LinkedHashMap<>(services);
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public ServiceInstance getLocalServiceInstance() {
        return null;
    }

    @Override
    public List<ServiceInstance> getInstances(String serviceId) {
        return services.get(serviceId);
    }

    @Override
    public List<String> getServices() {
        return new ArrayList<>(services.keySet());
    }

    public CamelCloudDiscoveryClient addServiceInstance(String serviceId, ServiceInstance instance) {
        services.computeIfAbsent(serviceId, key -> new LinkedList<>()).add(instance);
        return this;
    }

    public CamelCloudDiscoveryClient addServiceInstance(String serviceId, String host, int port) {
        return addServiceInstance(serviceId, new DefaultServiceInstance(serviceId, host, port, false));
    }
}
