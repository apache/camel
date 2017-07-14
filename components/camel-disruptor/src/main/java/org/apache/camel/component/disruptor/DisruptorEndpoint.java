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
package org.apache.camel.component.disruptor;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import com.lmax.disruptor.InsufficientCapacityException;
import org.apache.camel.AsyncEndpoint;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.MultipleConsumersSupport;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.WaitForTaskToComplete;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The disruptor component provides asynchronous SEDA behavior using LMAX Disruptor.
 *
 * This component works much as the standard SEDA Component, but utilizes a Disruptor
 * instead of a BlockingQueue utilized by the standard SEDA.
 */
@ManagedResource(description = "Managed Disruptor Endpoint")
@UriEndpoint(firstVersion = "2.12.0", scheme = "disruptor,disruptor-vm", title = "Disruptor,Disruptor VM", syntax = "disruptor:name", consumerClass = DisruptorConsumer.class, label = "endpoint")
public class DisruptorEndpoint extends DefaultEndpoint implements AsyncEndpoint, MultipleConsumersSupport {
    public static final String DISRUPTOR_IGNORE_EXCHANGE = "disruptor.ignoreExchange";
    private static final Logger LOGGER = LoggerFactory.getLogger(DisruptorEndpoint.class);

    private final Set<DisruptorProducer> producers = new CopyOnWriteArraySet<DisruptorProducer>();
    private final Set<DisruptorConsumer> consumers = new CopyOnWriteArraySet<DisruptorConsumer>();
    private final DisruptorReference disruptorReference;

    @UriPath(description = "Name of queue") @Metadata(required = "true")
    private String name;
    @UriParam(label = "consumer", defaultValue = "1")
    private final int concurrentConsumers;
    @UriParam(label = "consumer")
    private final boolean multipleConsumers;
    @UriParam(label = "producer", defaultValue = "IfReplyExpected")
    private WaitForTaskToComplete waitForTaskToComplete = WaitForTaskToComplete.IfReplyExpected;
    @UriParam(label = "producer", defaultValue = "30000")
    private long timeout = 30000;
    @UriParam(defaultValue = "" + DisruptorComponent.DEFAULT_BUFFER_SIZE)
    private int size;
    @UriParam(label = "producer")
    private boolean blockWhenFull;
    @UriParam(label = "consumer", defaultValue = "Blocking")
    private DisruptorWaitStrategy waitStrategy;
    @UriParam(label = "producer", defaultValue = "Multi")
    private DisruptorProducerType producerType;

    public DisruptorEndpoint(final String endpointUri, final Component component,
                             final DisruptorReference disruptorReference, final int concurrentConsumers,
                             final boolean multipleConsumers, boolean blockWhenFull) throws Exception {
        super(endpointUri, component);
        this.disruptorReference = disruptorReference;
        this.name = disruptorReference.getName();
        this.concurrentConsumers = concurrentConsumers;
        this.multipleConsumers = multipleConsumers;
        this.blockWhenFull = blockWhenFull;
    }

    @ManagedAttribute(description = "Queue name")
    public String getName() {
        return name;
    }

    @ManagedAttribute(description = "Buffer max capacity")
    public int getBufferSize() {
        return disruptorReference.getBufferSize();
    }

    @ManagedAttribute(description = "Remaining capacity in ring buffer")
    public long getRemainingCapacity() throws DisruptorNotStartedException {
        return getDisruptor().getRemainingCapacity();
    }

    @ManagedAttribute(description = "Amount of pending exchanges waiting for consumption in ring buffer")
    public long getPendingExchangeCount() throws DisruptorNotStartedException {
        return getDisruptor().getPendingExchangeCount();
    }

    /**
     * Number of concurrent threads processing exchanges.
     */
    @ManagedAttribute(description = "Number of concurrent consumers")
    public int getConcurrentConsumers() {
        return concurrentConsumers;
    }

    @ManagedAttribute(description = "Option to specify whether the caller should wait for the async task to complete or not before continuing")
    public WaitForTaskToComplete getWaitForTaskToComplete() {
        return waitForTaskToComplete;
    }

    /**
     * Option to specify whether the caller should wait for the async task to complete or not before continuing.
     * The following three options are supported: Always, Never or IfReplyExpected. The first two values are self-explanatory.
     * The last value, IfReplyExpected, will only wait if the message is Request Reply based.
     */
    public void setWaitForTaskToComplete(final WaitForTaskToComplete waitForTaskToComplete) {
        this.waitForTaskToComplete = waitForTaskToComplete;
    }

    @ManagedAttribute(description = "Timeout (in milliseconds) before a producer will stop waiting for an asynchronous task to complete")
    public long getTimeout() {
        return timeout;
    }

    /**
     * Timeout (in milliseconds) before a producer will stop waiting for an asynchronous task to complete.
     * You can disable timeout by using 0 or a negative value.
     */
    public void setTimeout(final long timeout) {
        this.timeout = timeout;
    }

    @ManagedAttribute(description = "The maximum capacity of the Disruptors ringbuffer")
    public int getSize() {
        return size;
    }

    /**
     * The maximum capacity of the Disruptors ringbuffer
     * Will be effectively increased to the nearest power of two.
     * Notice: Mind if you use this option, then its the first endpoint being created with the queue name,
     * that determines the size. To make sure all endpoints use same size, then configure the size option
     * on all of them, or the first endpoint being created.
     */
    public void setSize(int size) {
        this.size = size;
    }

    @Override
    @ManagedAttribute(description = "Specifies whether multiple consumers are allowed")
    public boolean isMultipleConsumersSupported() {
        return isMultipleConsumers();
    }

    /**
     * Specifies whether multiple consumers are allowed.
     * If enabled, you can use Disruptor for Publish-Subscribe messaging.
     * That is, you can send a message to the queue and have each consumer receive a copy of the message.
     * When enabled, this option should be specified on every consumer endpoint.
     */
    public boolean isMultipleConsumers() {
        return multipleConsumers;
    }

    /**
     * Returns the current active consumers on this endpoint
     */
    public Set<DisruptorConsumer> getConsumers() {
        return Collections.unmodifiableSet(consumers);
    }

    /**
     * Returns the current active producers on this endpoint
     */
    public Set<DisruptorProducer> getProducers() {
        return Collections.unmodifiableSet(producers);
    }

    @ManagedAttribute
    public boolean isBlockWhenFull() {
        return blockWhenFull;
    }

    /**
     * Whether a thread that sends messages to a full Disruptor will block until the ringbuffer's capacity is no longer exhausted.
     * By default, the calling thread will block and wait until the message can be accepted.
     * By disabling this option, an exception will be thrown stating that the queue is full.
     */
    public void setBlockWhenFull(boolean blockWhenFull) {
        this.blockWhenFull = blockWhenFull;
    }

    @ManagedAttribute(description = "Defines the strategy used by consumer threads to wait on new exchanges to be published")
    public DisruptorWaitStrategy getWaitStrategy() {
        return waitStrategy;
    }

    /**
     * Defines the strategy used by consumer threads to wait on new exchanges to be published.
     * The options allowed are:Blocking, Sleeping, BusySpin and Yielding.
     */
    public void setWaitStrategy(DisruptorWaitStrategy waitStrategy) {
        this.waitStrategy = waitStrategy;
    }

    @ManagedAttribute(description = " Defines the producers allowed on the Disruptor")
    public DisruptorProducerType getProducerType() {
        return producerType;
    }

    /**
     * Defines the producers allowed on the Disruptor.
     * The options allowed are: Multi to allow multiple producers and Single to enable certain optimizations only
     * allowed when one concurrent producer (on one thread or otherwise synchronized) is active.
     */
    public void setProducerType(DisruptorProducerType producerType) {
        this.producerType = producerType;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    @Override
    public Producer createProducer() throws Exception {
        if (getProducers().size() == 1 && getDisruptor().getProducerType() == DisruptorProducerType.Single) {
            throw new IllegalStateException(
                    "Endpoint can't support multiple producers when ProducerType SINGLE is configured");
        }
        return new DisruptorProducer(this, getWaitForTaskToComplete(), getTimeout(), isBlockWhenFull());
    }

    @Override
    public Consumer createConsumer(final Processor processor) throws Exception {
        return new DisruptorConsumer(this, processor);
    }

    @Override
    protected void doStart() throws Exception {
        // notify reference we are shutting down this endpoint
        disruptorReference.addEndpoint(this);
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        // notify reference we are shutting down this endpoint
        disruptorReference.removeEndpoint(this);
        super.doStop();
    }

    @Override
    protected void doShutdown() throws Exception {
        // notify component we are shutting down this endpoint
        if (getComponent() != null) {
            getComponent().onShutdownEndpoint(this);
        }

        super.doShutdown();
    }

    @Override
    public DisruptorComponent getComponent() {
        return (DisruptorComponent)super.getComponent();
    }

    void onStarted(final DisruptorConsumer consumer) throws Exception {
        synchronized (this) {
            // validate multiple consumers has been enabled is necessary
            if (!consumers.isEmpty() && !isMultipleConsumersSupported()) {
                throw new IllegalStateException(
                        "Multiple consumers for the same endpoint is not allowed: " + this);
            }
            if (consumers.add(consumer)) {
                LOGGER.debug("Starting consumer {} on endpoint {}", consumer, getEndpointUri());
                getDisruptor().reconfigure();
            } else {
                LOGGER.debug("Tried to start Consumer {} on endpoint {} but it was already started", consumer, getEndpointUri());
            }
        }
    }


    void onStopped(final DisruptorConsumer consumer) throws Exception {
        synchronized (this) {
            if (consumers.remove(consumer)) {
                LOGGER.debug("Stopping consumer {} on endpoint {}", consumer, getEndpointUri());
                getDisruptor().reconfigure();
            } else {
                LOGGER.debug("Tried to stop Consumer {} on endpoint {} but it was already stopped", consumer, getEndpointUri());
            }
        }
    }

    void onStarted(final DisruptorProducer producer) {
        producers.add(producer);
    }

    void onStopped(final DisruptorProducer producer) {
        producers.remove(producer);
    }

    Map<DisruptorConsumer, Collection<LifecycleAwareExchangeEventHandler>> createConsumerEventHandlers() {
        Map<DisruptorConsumer, Collection<LifecycleAwareExchangeEventHandler>> result =
                new HashMap<DisruptorConsumer, Collection<LifecycleAwareExchangeEventHandler>>();

        for (final DisruptorConsumer consumer : consumers) {
            result.put(consumer, consumer.createEventHandlers(concurrentConsumers));
        }

        return result;
    }

    /**
     * Called by DisruptorProducers to publish new exchanges on the RingBuffer, blocking when full
     */
    void publish(final Exchange exchange) throws DisruptorNotStartedException {
        disruptorReference.publish(exchange);
    }

    /**
     * Called by DisruptorProducers to publish new exchanges on the RingBuffer, throwing InsufficientCapacityException
     * when full
     *
     * @throws InsufficientCapacityException when the Ringbuffer is full.
     */
    void tryPublish(final Exchange exchange) throws DisruptorNotStartedException, InsufficientCapacityException {
        disruptorReference.tryPublish(exchange);
    }

    DisruptorReference getDisruptor() {
        return disruptorReference;
    }

    @Override
    public boolean equals(Object object) {
        boolean result = super.equals(object);
        return result && getCamelContext().equals(((DisruptorEndpoint)object).getCamelContext());
    }
    
    @Override
    public int hashCode() {
        return getEndpointUri().hashCode() * 37 + getCamelContext().hashCode();
    }
}
