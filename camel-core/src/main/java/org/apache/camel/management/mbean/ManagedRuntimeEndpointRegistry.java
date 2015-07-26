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
package org.apache.camel.management.mbean;

import java.util.List;
import java.util.Map;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularDataSupport;

import org.apache.camel.CamelContext;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.CamelOpenMBeanTypes;
import org.apache.camel.api.management.mbean.ManagedRuntimeEndpointRegistryMBean;
import org.apache.camel.spi.EndpointRegistry;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.spi.RuntimeEndpointRegistry;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;

@ManagedResource(description = "Managed RuntimeEndpointRegistry")
public class ManagedRuntimeEndpointRegistry extends ManagedService implements ManagedRuntimeEndpointRegistryMBean {

    private final RuntimeEndpointRegistry registry;
    private boolean sanitize;

    public ManagedRuntimeEndpointRegistry(CamelContext context, RuntimeEndpointRegistry registry) {
        super(context, registry);
        this.registry = registry;
    }

    public void init(ManagementStrategy strategy) {
        super.init(strategy);
        sanitize = strategy.getManagementAgent().getMask() != null ? strategy.getManagementAgent().getMask() : false;
    }

    @Override
    public void reset() {
        registry.reset();
    }

    @Override
    public boolean isEnabled() {
        return registry.isEnabled();
    }

    @Override
    public void setEnabled(boolean enabled) {
        registry.setEnabled(enabled);
    }

    @Override
    public int getLimit() {
        return registry.getLimit();
    }

    @Override
    public int size() {
        return registry.size();
    }

    @Override
    public List<String> getAllEndpoints(boolean includeInputs) {
        return registry.getAllEndpoints(includeInputs);
    }

    @Override
    public List<String> getEndpointsPerRoute(String routeId, boolean includeInputs) {
        return registry.getEndpointsPerRoute(routeId, includeInputs);
    }

    @Override
    public TabularData listEndpoints() {
        try {
            TabularData answer = new TabularDataSupport(CamelOpenMBeanTypes.listRuntimeEndpointsTabularType());

            EndpointRegistry staticRegistry = getContext().getEndpointRegistry();

            Map<String, RuntimeEndpointRegistry.Statistic> stats = registry.getStatistics();
            for (Map.Entry<String, RuntimeEndpointRegistry.Statistic> entry : stats.entrySet()) {
                CompositeType ct = CamelOpenMBeanTypes.listRuntimeEndpointsCompositeType();

                String url = entry.getKey();

                Boolean isStatic = staticRegistry.isStatic(url);
                Boolean isDynamic = staticRegistry.isDynamic(url);
                if (sanitize) {
                    url = URISupport.sanitizeUri(url);
                }
                String routeId = entry.getValue().getRouteId();
                Boolean input = entry.getValue().isInput();
                Boolean output = entry.getValue().isOutput();

                CompositeData data = new CompositeDataSupport(ct, new String[]{"url", "routeId", "input", "output", "static", "dynamic"},
                        new Object[]{url, routeId, input, output, isStatic, isDynamic});
                answer.put(data);
            }
            return answer;
        } catch (Exception e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
    }
}
