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

import org.apache.camel.CamelContext;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.ManagedRuntimeEndpointRegistryMBean;
import org.apache.camel.spi.RuntimeEndpointRegistry;

/**
 *
 */
@ManagedResource(description = "Managed RuntimeEndpointRegistry")
public class ManagedRuntimeEndpointRegistry extends ManagedService implements ManagedRuntimeEndpointRegistryMBean {

    private final RuntimeEndpointRegistry registry;

    public ManagedRuntimeEndpointRegistry(CamelContext context, RuntimeEndpointRegistry registry) {
        super(context, registry);
        this.registry = registry;
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
    public List<String> getAllEndpoints(boolean includeInputs) {
        return registry.getAllEndpoints(includeInputs);
    }

    @Override
    public List<String> getEndpointsPerRoute(String routeId, boolean includeInputs) {
        return registry.getEndpointsPerRoute(routeId, includeInputs);
    }
}
