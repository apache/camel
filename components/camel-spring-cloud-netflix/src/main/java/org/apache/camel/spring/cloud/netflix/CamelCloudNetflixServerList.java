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
package org.apache.camel.spring.cloud.netflix;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.AbstractServerList;
import org.apache.camel.cloud.ServiceDefinition;
import org.apache.camel.cloud.ServiceDiscovery;
import org.apache.camel.cloud.ServiceFilter;
import org.apache.camel.component.ribbon.cloud.RibbonServiceDefinition;

public class CamelCloudNetflixServerList extends AbstractServerList<RibbonServiceDefinition> {
    private ServiceDiscovery serviceDiscovery;
    private ServiceFilter serviceFilter;
    private String serviceId;

    public CamelCloudNetflixServerList() {
        this(null, list -> list);
    }

    public CamelCloudNetflixServerList(ServiceDiscovery serviceDiscovery, ServiceFilter serviceFilter) {
        this.serviceDiscovery = serviceDiscovery;
        this.serviceFilter = serviceFilter;
    }

    public ServiceDiscovery getServiceDiscovery() {
        return serviceDiscovery;
    }

    public void setServiceDiscovery(ServiceDiscovery serviceDiscovery) {
        this.serviceDiscovery = serviceDiscovery;
    }

    public ServiceFilter getServiceFilter() {
        return serviceFilter;
    }

    public void setServiceFilter(ServiceFilter serviceFilter) {
        this.serviceFilter = serviceFilter;
    }

    @Override
    public void initWithNiwsConfig(IClientConfig clientConfig) {
        this.serviceId = clientConfig.getClientName();
    }

    @Override
    public List<RibbonServiceDefinition> getInitialListOfServers() {
        if (serviceId == null) {
            return Collections.emptyList();
        }

        List<ServiceDefinition> services = serviceDiscovery.getServices(serviceId);
        if (serviceFilter != null) {
            services = serviceFilter.apply(services);
        }

        return convert(services);
    }

    @Override
    public List<RibbonServiceDefinition> getUpdatedListOfServers() {
        if (serviceId == null) {
            return Collections.emptyList();
        }

        List<ServiceDefinition> services = serviceDiscovery.getServices(serviceId);
        if (serviceFilter != null) {
            services = serviceFilter.apply(services);
        }

        return convert(services);
    }

    // *************************************************************************
    // Helpers
    // *************************************************************************

    private List<RibbonServiceDefinition> convert(List<? extends ServiceDefinition> definitions) {
        if (definitions.isEmpty()) {
            return Collections.emptyList();
        }

        return definitions.stream().map(RibbonServiceDefinition::new).collect(Collectors.toList());
    }
}
