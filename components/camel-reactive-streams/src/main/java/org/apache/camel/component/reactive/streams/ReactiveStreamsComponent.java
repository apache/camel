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
package org.apache.camel.component.reactive.streams;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.reactive.streams.api.CamelReactiveStreamsService;
import org.apache.camel.component.reactive.streams.engine.ReactiveStreamsEngineConfiguration;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.service.ServiceHelper;

/**
 * The Camel reactive-streams component.
 */
@Component("reactive-streams")
public class ReactiveStreamsComponent extends DefaultComponent {
    @Metadata(label = "common", defaultValue = "CamelReactiveStreamsWorker")
    private String threadPoolName = "CamelReactiveStreamsWorker";
    @Metadata(label = "common")
    private int threadPoolMinSize;
    @Metadata(label = "common", defaultValue = "10")
    private int threadPoolMaxSize = 10;
    @Metadata(label = "producer", defaultValue = "BUFFER")
    private ReactiveStreamsBackpressureStrategy backpressureStrategy = ReactiveStreamsBackpressureStrategy.BUFFER;
    @Metadata(label = "advanced")
    private String serviceType;
    @Metadata(label = "advanced")
    private ReactiveStreamsEngineConfiguration reactiveStreamsEngineConfiguration;

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

    public ReactiveStreamsEngineConfiguration getReactiveStreamsEngineConfiguration() {
        return reactiveStreamsEngineConfiguration;
    }

    /**
     * To use an existing reactive stream engine configuration.
     */
    public void setReactiveStreamsEngineConfiguration(ReactiveStreamsEngineConfiguration reactiveStreamsEngineConfiguration) {
        this.reactiveStreamsEngineConfiguration = reactiveStreamsEngineConfiguration;
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

    public String getThreadPoolName() {
        return threadPoolName;
    }

    /**
     * The name of the thread pool used by the reactive streams internal engine.
     */
    public void setThreadPoolName(String threadPoolName) {
        this.threadPoolName = threadPoolName;
    }

    public int getThreadPoolMinSize() {
        return threadPoolMinSize;
    }

    /**
     * The minimum number of threads used by the reactive streams internal engine.
     */
    public void setThreadPoolMinSize(int threadPoolMinSize) {
        this.threadPoolMinSize = threadPoolMinSize;
    }

    public int getThreadPoolMaxSize() {
        return threadPoolMaxSize;
    }

    /**
     * The maximum number of threads used by the reactive streams internal engine.
     */
    public void setThreadPoolMaxSize(int threadPoolMaxSize) {
        this.threadPoolMaxSize = threadPoolMaxSize;
    }

    /**
     * Lazy creation of the CamelReactiveStreamsService
     *
     * @return the reactive streams service
     */
    public synchronized CamelReactiveStreamsService getReactiveStreamsService() {
        if (reactiveStreamsEngineConfiguration == null) {
            reactiveStreamsEngineConfiguration = new ReactiveStreamsEngineConfiguration();
            reactiveStreamsEngineConfiguration.setThreadPoolMaxSize(threadPoolMaxSize);
            reactiveStreamsEngineConfiguration.setThreadPoolMinSize(threadPoolMinSize);
            reactiveStreamsEngineConfiguration.setThreadPoolName(threadPoolName);
        }

        if (service == null) {
            this.service = ReactiveStreamsHelper.resolveReactiveStreamsService(
                getCamelContext(),
                this.serviceType,
                this.reactiveStreamsEngineConfiguration
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
