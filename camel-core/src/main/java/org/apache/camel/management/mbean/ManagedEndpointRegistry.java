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

import java.util.Collection;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularDataSupport;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.CamelOpenMBeanTypes;
import org.apache.camel.api.management.mbean.ManagedEndpointRegistryMBean;
import org.apache.camel.impl.EndpointRegistry;
import org.apache.camel.util.ObjectHelper;

/**
 * @version 
 */
@ManagedResource(description = "Managed EndpointRegistry")
public class ManagedEndpointRegistry extends ManagedService implements ManagedEndpointRegistryMBean {
    private final EndpointRegistry endpointRegistry;

    public ManagedEndpointRegistry(CamelContext context, EndpointRegistry endpointRegistry) {
        super(context, endpointRegistry);
        this.endpointRegistry = endpointRegistry;
    }

    public EndpointRegistry getEndpointRegistry() {
        return endpointRegistry;
    }

    public String getSource() {
        return endpointRegistry.toString();
    }

    public Integer getSize() {
        return endpointRegistry.size();
    }

    public Integer getMaximumCacheSize() {
        return endpointRegistry.getMaxCacheSize();
    }

    public void purge() {
        endpointRegistry.purge();
    }

    @Override
    public TabularData listEndpoints() {
        try {
            TabularData answer = new TabularDataSupport(CamelOpenMBeanTypes.listEndpointsTabularType());
            Collection<Endpoint> endpoints = endpointRegistry.values();
            for (Endpoint endpoint : endpoints) {
                CompositeType ct = CamelOpenMBeanTypes.listEndpointsCompositeType();
                String url = endpoint.getEndpointUri();

                CompositeData data = new CompositeDataSupport(ct, new String[]{"url"}, new Object[]{url});
                answer.put(data);
            }
            return answer;
        } catch (Exception e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
    }


}
