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

package org.apache.camel.management.mbean;

import java.util.List;
import java.util.StringJoiner;

import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularDataSupport;

import org.apache.camel.CamelContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.CamelOpenMBeanTypes;
import org.apache.camel.api.management.mbean.ManagedEndpointServiceRegistryMBean;
import org.apache.camel.spi.EndpointServiceRegistry;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.util.URISupport;

/**
 *
 */
@ManagedResource(description = "Managed EndpointServiceRegistry")
public class ManagedEndpointServiceRegistry extends ManagedService implements ManagedEndpointServiceRegistryMBean {

    private final EndpointServiceRegistry registry;
    private boolean sanitize;

    public ManagedEndpointServiceRegistry(CamelContext context, EndpointServiceRegistry registry) {
        super(context, registry);
        this.registry = registry;
    }

    @Override
    public void init(ManagementStrategy strategy) {
        super.init(strategy);
        sanitize = strategy.getManagementAgent().getMask() != null
                ? strategy.getManagementAgent().getMask()
                : true;
    }

    public EndpointServiceRegistry getRegistry() {
        return registry;
    }

    @Override
    public int getNumberOfEndpointServices() {
        return registry.size();
    }

    @Override
    public TabularData listEndpointServices() {
        try {
            TabularData answer = new TabularDataSupport(CamelOpenMBeanTypes.listEndpointServicesTabularType());
            List<EndpointServiceRegistry.EndpointService> services = registry.listAllEndpointServices();
            for (EndpointServiceRegistry.EndpointService entry : services) {
                CompositeType ct = CamelOpenMBeanTypes.listEndpointServicesCompositeType();
                String component = entry.getComponent();
                String dir = entry.getDirection();
                String protocol = entry.getServiceProtocol();
                String serviceUrl = entry.getServiceUrl();
                String metadata = null;
                String endpointUri = entry.getEndpointUri();
                if (sanitize) {
                    endpointUri = URISupport.sanitizeUri(endpointUri);
                }

                long hits = entry.getHits();
                String routeId = entry.getRouteId();
                var m = entry.getServiceMetadata();
                if (m != null) {
                    StringJoiner sj = new StringJoiner(" ");
                    m.forEach((k, v) -> sj.add(k + "=" + v));
                    metadata = sj.toString();
                }

                CompositeData data = new CompositeDataSupport(
                        ct,
                        new String[] {
                            "component", "dir", "protocol", "serviceUrl", "metadata", "endpointUri", "routeId", "hits"
                        },
                        new Object[] {component, dir, protocol, serviceUrl, metadata, endpointUri, routeId, hits});
                answer.put(data);
            }
            return answer;
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
    }
}
