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

import org.apache.camel.CamelContext;
import org.apache.camel.impl.EndpointRegistry;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedResource;

/**
 * @version 
 */
@ManagedResource(description = "Managed EndpointRegistry")
public class ManagedEndpointRegistry extends ManagedService {
    private final EndpointRegistry endpointRegistry;

    public ManagedEndpointRegistry(CamelContext context, EndpointRegistry endpointRegistry) {
        super(context, endpointRegistry);
        this.endpointRegistry = endpointRegistry;
    }

    public EndpointRegistry getEndpointRegistry() {
        return endpointRegistry;
    }

    @ManagedAttribute(description = "Source")
    public String getSource() {
        return endpointRegistry.toString();
    }

    @ManagedAttribute(description = "Number of endpoints cached")
    public Integer getSize() {
        return endpointRegistry.size();
    }

    @ManagedAttribute(description = "Maximum cache size (capacity)")
    public Integer getMaximumCacheSize() {
        return endpointRegistry.getMaxCacheSize();
    }

    @ManagedOperation(description = "Purges the cache")
    public void purge() {
        endpointRegistry.purge();
    }

}
