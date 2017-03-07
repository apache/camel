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
import org.apache.camel.component.reactive.streams.engine.ReactiveStreamsEngineConfiguration;
import org.apache.camel.impl.UriEndpointComponent;

/**
 * The Camel reactive-streams component.
 */
public class ReactiveStreamsComponent extends UriEndpointComponent {

    private ReactiveStreamsEngineConfiguration internalEngineConfiguration = new ReactiveStreamsEngineConfiguration();

    private ReactiveStreamsBackpressureStrategy backpressureStrategy = ReactiveStreamsBackpressureStrategy.BUFFER;

    public ReactiveStreamsComponent() {
        super(ReactiveStreamsEndpoint.class);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        ReactiveStreamsEndpoint endpoint = new ReactiveStreamsEndpoint(uri, this);
        endpoint.setStream(remaining);
        setProperties(endpoint, parameters);

        return endpoint;
    }

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

}
