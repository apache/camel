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
package org.apache.camel.component.service;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.DelegateEndpoint;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.cloud.DiscoverableService;
import org.apache.camel.cloud.ServiceDefinition;
import org.apache.camel.cloud.ServiceRegistry;
import org.apache.camel.impl.cloud.DefaultServiceDefinition;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

/**
 * Represents an endpoint which is registered to a Service Registry such as Consul, Etcd.
 */
@ManagedResource(description = "Managed Service Endpoint")
@UriEndpoint(
    firstVersion = "2.22.0",
    scheme = "service",
    syntax = "service:delegateUri",
    consumerOnly = true,
    title = "Service",
    lenientProperties = true,
    label = "cloud")
public class ServiceEndpoint extends DefaultEndpoint implements DelegateEndpoint {
    private final Endpoint delegateEndpoint;
    private final ServiceRegistry serviceRegistry;
    private final Map<String, String> serviceParameters;
    private final ServiceDefinition serviceDefinition;

    @UriPath(description = "The endpoint uri to expose as service")
    @Metadata(required = true)
    private final String delegateUri;

    public ServiceEndpoint(String uri, ServiceComponent component, ServiceRegistry serviceRegistry, Map<String, String> serviceParameters, String delegateUri) {
        super(uri, component);

        this.serviceRegistry = serviceRegistry;
        this.serviceParameters = serviceParameters;
        this.delegateUri = delegateUri;
        this.delegateEndpoint = getCamelContext().getEndpoint(delegateUri);

        // The service properties set on uri override parameter provided by a
        // an endpoint of type DiscoverableService.
        this.serviceDefinition = computeServiceDefinition(component.getCamelContext(), delegateEndpoint);
    }

    @Override
    public Endpoint getEndpoint() {
        return this.delegateEndpoint;
    }

    @ManagedAttribute(description = "The consumer endpoint to expose as a service", mask = true)
    public String getDelegateEndpointUri() {
        return this.delegateEndpoint.getEndpointUri();
    }

    public ServiceDefinition getServiceDefinition() {
        return this.serviceDefinition;
    }

    @Override
    public Producer createProducer() throws Exception {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        return new ServiceConsumer(this, processor, serviceRegistry);
    }

    @Override
    public boolean isLenientProperties() {
        return true;
    } 

    private ServiceDefinition computeServiceDefinition(CamelContext context, Endpoint delegateEndpoint) {
        Map<String, String> parameters = new HashMap<>();

        if (delegateEndpoint instanceof DiscoverableService) {
            parameters.putAll(((DiscoverableService)delegateEndpoint).getServiceProperties());
        }

        parameters.putAll(serviceParameters);
        parameters.computeIfAbsent(ServiceDefinition.SERVICE_META_ID, k -> context.getUuidGenerator().generateUuid());

        return DefaultServiceDefinition.builder().from(parameters).build();
    }
}
