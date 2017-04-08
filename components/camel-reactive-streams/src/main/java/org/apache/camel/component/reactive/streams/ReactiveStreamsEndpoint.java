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

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;

/**
 * Reactive Camel using reactive streams
 */
@UriEndpoint(firstVersion = "2.19.0", scheme = "reactive-streams", title = "Reactive Streams", syntax = "reactive-streams:stream",
        consumerClass = ReactiveStreamsConsumer.class, label = "reactive,streams")
@ManagedResource(description = "Managed ReactiveStreamsEndpoint")
public class ReactiveStreamsEndpoint extends DefaultEndpoint {

    @UriPath
    private String stream;

    @UriParam
    private String serviceName;

    @UriParam(label = "consumer", defaultValue = "128")
    private Integer maxInflightExchanges = 128;

    @UriParam(label = "consumer", defaultValue = "1")
    private int concurrentConsumers = 1;

    @UriParam(label = "consumer", defaultValue = "false")
    private boolean forwardOnComplete;

    @UriParam(label = "consumer", defaultValue = "false")
    private boolean forwardOnError;

    @UriParam(label = "producer")
    private ReactiveStreamsBackpressureStrategy backpressureStrategy;

    public ReactiveStreamsEndpoint(String endpointUri, ReactiveStreamsComponent component) {
        super(endpointUri, component);
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new ReactiveStreamsProducer(this, stream);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        return new ReactiveStreamsConsumer(this, processor);
    }

    @ManagedAttribute(description = "Name of the stream channel used by the endpoint to exchange messages")
    public String getStream() {
        return stream;
    }

    /**
     * Name of the stream channel used by the endpoint to exchange messages.
     */
    public void setStream(String stream) {
        this.stream = stream;
    }

    @ManagedAttribute(description = "Maximum number of exchanges concurrently being processed by Camel")
    public Integer getMaxInflightExchanges() {
        return maxInflightExchanges;
    }

    /**
     * Maximum number of exchanges concurrently being processed by Camel.
     * This parameter controls backpressure on the stream.
     * Setting a non-positive value will disable backpressure.
     */
    public void setMaxInflightExchanges(Integer maxInflightExchanges) {
        this.maxInflightExchanges = maxInflightExchanges;
    }

    public int getConcurrentConsumers() {
        return concurrentConsumers;
    }

    /**
     * Number of threads used to process exchanges in the Camel route.
     */
    @ManagedAttribute(description = "Number of threads used to process exchanges in the Camel route")
    public void setConcurrentConsumers(int concurrentConsumers) {
        this.concurrentConsumers = concurrentConsumers;
    }

    public String getServiceName() {
        return serviceName;
    }

    /**
     * Allows using an alternative CamelReactiveStreamService implementation. The implementation is looked up from the registry.
     */
    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
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

    @ManagedAttribute(description = "Determines if onComplete events should be pushed to the Camel route")
    public boolean isForwardOnComplete() {
        return forwardOnComplete;
    }

    /**
     * Determines if onComplete events should be pushed to the Camel route.
     */
    public void setForwardOnComplete(boolean forwardOnComplete) {
        this.forwardOnComplete = forwardOnComplete;
    }

    @ManagedAttribute(description = "Determines if onError events should be pushed to the Camel route")
    public boolean isForwardOnError() {
        return forwardOnError;
    }

    /**
     * Determines if onError events should be pushed to the Camel route.
     * Exceptions will be set as message body.
     */
    public void setForwardOnError(boolean forwardOnError) {
        this.forwardOnError = forwardOnError;
    }

}
