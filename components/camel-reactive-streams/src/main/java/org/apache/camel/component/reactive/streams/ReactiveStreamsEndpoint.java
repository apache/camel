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

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.component.reactive.streams.api.CamelReactiveStreamsService;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

/**
 * Reactive Camel using reactive streams
 */
@UriEndpoint(firstVersion = "2.19.0", scheme = "reactive-streams", title = "Reactive Streams", syntax = "reactive-streams:stream",
        label = "reactive,streams")
@ManagedResource(description = "Managed ReactiveStreamsEndpoint")
public class ReactiveStreamsEndpoint extends DefaultEndpoint {

    @UriPath
    private String stream;

    @UriParam(label = "consumer", defaultValue = "128")
    private Integer maxInflightExchanges = 128;

    @UriParam(label = "consumer", defaultValue = "0.25")
    private double exchangesRefillLowWatermark = 0.25;

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
    public Producer createProducer() throws Exception {
        return new ReactiveStreamsProducer(this, stream, getReactiveStreamsService());
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        return new ReactiveStreamsConsumer(this, processor, getReactiveStreamsService());
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

    @ManagedAttribute(description = "The percentage of maxInflightExchanges below which new items can be requested to the source subscription")
    public double getExchangesRefillLowWatermark() {
        return exchangesRefillLowWatermark;
    }

    /**
     * Set the low watermark of requested exchanges to the active subscription as percentage of the maxInflightExchanges.
     * When the number of pending items from the upstream source is lower than the watermark, new items can be requested to the subscription.
     * If set to 0, the subscriber will request items in batches of maxInflightExchanges, only after all items of the previous batch have been processed.
     * If set to 1, the subscriber can request a new item each time an exchange is processed (chatty).
     * Any intermediate value can be used.
     */
    public void setExchangesRefillLowWatermark(double exchangesRefillLowWatermark) {
        this.exchangesRefillLowWatermark = exchangesRefillLowWatermark;
    }

    CamelReactiveStreamsService getReactiveStreamsService() {
        return ((ReactiveStreamsComponent)getComponent()).getReactiveStreamsService();
    }

}
