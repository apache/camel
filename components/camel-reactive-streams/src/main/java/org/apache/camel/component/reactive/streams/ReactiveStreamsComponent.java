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
package org.apache.camel.component.reactive.streams;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.reactive.streams.api.CamelReactiveStreamsService;
import org.apache.camel.component.reactive.streams.engine.ReactiveStreamsEngineConfiguration;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.spi.Metadata;
import org.apache.camel.util.ServiceHelper;

/**
 * The Camel reactive-streams component.
 */
public class ReactiveStreamsComponent extends DefaultComponent {
    @Metadata(label = "advanced")
    private ReactiveStreamsEngineConfiguration internalEngineConfiguration = new ReactiveStreamsEngineConfiguration();
    @Metadata(label = "producer", defaultValue = "BUFFER")
    private ReactiveStreamsBackpressureStrategy backpressureStrategy = ReactiveStreamsBackpressureStrategy.BUFFER;
    @Metadata(label = "advanced")
    private String serviceType;

    private CamelReactiveStreamsService service;

    public ReactiveStreamsComponent() {
    }

    // ****************************************
    // Lifecycle/Implementation
    // ****************************************

    @Override
    protected void doStart() throws Exception {
        // force creation of ReactiveStreamsService
        getReactiveStreamsService();

        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(service);

        super.doStop();
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        ReactiveStreamsEndpoint endpoint = new ReactiveStreamsEndpoint(uri, this);
        endpoint.setStream(remaining);

        setProperties(endpoint, parameters);

        if (endpoint.getBackpressureStrategy() == null) {
            endpoint.setBackpressureStrategy(this.backpressureStrategy);
        }

        return endpoint;
    }

    // ****************************************
    // Properties
    // ****************************************

    public ReactiveStreamsEngineConfiguration getInternalEngineConfiguration() {
        return internalEngineConfiguration;
    }

    /**
     * Configures the internal engine for Reactive Streams.
     */
    public void setInternalEngineConfiguration(ReactiveStreamsEngineConfiguration internalEngineConfiguration) {
        this.internalEngineConfiguration = internalEngineConfiguration;
    }

    public ReactiveStreamsBackpressureStrategy getBackpressureStrategy() {
        return backpressureStrategy;
    }

    /**
     * The backpressure strategy to use when pushing events to a slow subscriber.
     */
    public void setBackpressureStrategy(ReactiveStreamsBackpressureStrategy backpressureStrategy) {
        this.backpressureStrategy = backpressureStrategy;
    }

    public String getServiceType() {
        return serviceType;
    }

    /**
     * Set the type of the underlying reactive streams implementation to use. The
     * implementation is looked up from the registry or using a ServiceLoader, the
     * default implementation is DefaultCamelReactiveStreamsService
     *
     * @param serviceType the reactive service implementation name type
     */
    public void setServiceType(String serviceType) {
        this.serviceType = serviceType;
    }

    /**
     * Lazy creation of the CamelReactiveStreamsService
     *
     * @return the reactive streams service
     */
    public synchronized CamelReactiveStreamsService getReactiveStreamsService() {
        if (service == null) {
            this.service = ReactiveStreamsHelper.resolveReactiveStreamsService(
                getCamelContext(),
                this.serviceType,
                this.internalEngineConfiguration
            );

            try {
                // Start the service and add it to the Camel context to expose managed attributes
                getCamelContext().addService(service, true, true);
            } catch (Exception e) {
                throw new RuntimeCamelException(e);
            }
        }

        return service;
    }

    // ****************************************
    // Helpers
    // ****************************************

    public static final ReactiveStreamsComponent withServiceType(String serviceType) {
        ReactiveStreamsComponent component = new ReactiveStreamsComponent();
        component.setServiceType(serviceType);

        return component;
    }
}
