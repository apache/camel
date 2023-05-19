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
package org.apache.camel.component.consul.cloud;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.orbitz.consul.Consul;
import com.orbitz.consul.model.catalog.CatalogService;
import com.orbitz.consul.model.health.ServiceHealth;
import com.orbitz.consul.option.ImmutableQueryOptions;
import com.orbitz.consul.option.QueryOptions;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.cloud.ServiceDefinition;
import org.apache.camel.component.consul.ConsulConfiguration;
import org.apache.camel.impl.cloud.DefaultServiceDefinition;
import org.apache.camel.impl.cloud.DefaultServiceDiscovery;
import org.apache.camel.impl.cloud.DefaultServiceHealth;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.function.Suppliers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ConsulServiceDiscovery extends DefaultServiceDiscovery {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConsulServiceDiscovery.class);

    private final Supplier<Consul> client;
    private final QueryOptions queryOptions;

    public ConsulServiceDiscovery(ConsulConfiguration configuration) {
        this.client = Suppliers.memorize(() -> configuration.createConsulClient(getCamelContext()),
                e -> {
                    throw RuntimeCamelException.wrapRuntimeCamelException(e);
                });

        ImmutableQueryOptions.Builder builder = ImmutableQueryOptions.builder();
        ObjectHelper.ifNotEmpty(configuration.getDatacenter(), builder::datacenter);
        ObjectHelper.ifNotEmpty(configuration.getTags(), builder::tag);

        queryOptions = builder.build();
    }

    @Override
    public List<ServiceDefinition> getServices(String name) {
        try {
            List<CatalogService> services = client.get().catalogClient().getService(name, queryOptions).getResponse();
            List<ServiceHealth> healths = client.get().healthClient().getAllServiceInstances(name, queryOptions).getResponse();

            return services.stream().map(service -> newService(name, service, healths)).toList();
        } catch (Exception e) {
            LOGGER.warn("Error getting '{}' services from consul.", name, e);
            return Collections.emptyList();
        }
    }

    // *************************
    // Helpers
    // *************************

    private boolean isHealthy(ServiceHealth serviceHealth) {
        return serviceHealth.getChecks().stream().allMatch(check -> ObjectHelper.equal(check.getStatus(), "passing", true));
    }

    private ServiceDefinition newService(String serviceName, CatalogService service, List<ServiceHealth> serviceHealthList) {
        Map<String, String> meta = new HashMap<>();
        ObjectHelper.ifNotEmpty(service.getServiceId(), val -> meta.put(ServiceDefinition.SERVICE_META_ID, val));
        ObjectHelper.ifNotEmpty(service.getServiceName(), val -> meta.put(ServiceDefinition.SERVICE_META_NAME, val));
        ObjectHelper.ifNotEmpty(service.getNode(), val -> meta.put("service.node", val));
        ObjectHelper.ifNotEmpty(service.getServiceMeta(), meta::putAll);

        List<String> tags = service.getServiceTags();
        if (tags != null) {
            for (String tag : service.getServiceTags()) {
                String[] items = tag.split("=");
                if (items.length == 1) {
                    meta.put(items[0], items[0]);
                } else if (items.length == 2) {
                    meta.put(items[0], items[1]);
                }
            }
        }

        return new DefaultServiceDefinition(
                serviceName, service.getServiceAddress(), service.getServicePort(), meta,
                new DefaultServiceHealth(
                        serviceHealthList.stream()
                                .filter(h -> ObjectHelper.equal(h.getService().getId(), service.getServiceId()))
                                .allMatch(this::isHealthy)));
    }
}
