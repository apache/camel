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
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.MultipleConsumersSupport;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.ServiceStatus;
import org.apache.camel.WaitForTaskToComplete;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An implementation of the <a href="https://github.com/sirchia/camel-disruptor">Disruptor component</a>
 * for asynchronous SEDA exchanges on an
 * <a href="https://github.com/LMAX-Exchange/disruptor">LMAX Disruptor</a> within a CamelContext
 */
@ManagedResource(description = "Managed Disruptor Endpoint")
@UriEndpoint(scheme = "disruptor,disruptor-vm", title = "Disruptor,Disruptor VM", syntax = "disruptor:name", consumerClass = DisruptorConsumer.class, label = "endpoint")
public class DisruptorEndpoint extends DefaultEndpoint implements MultipleConsumersSupport {
    public static final String DISRUPTOR_IGNORE_EXCHANGE = "disruptor.ignoreExchange";
    private static final Logger LOGGER = LoggerFactory.getLogger(DisruptorEndpoint.class);

    @UriPath(description = "Name of queue")
    private String name;
    @UriParam(defaultValue = "1")
    private final int concurrentConsumers;
    @UriParam
    private final boolean multipleConsumers;
    @UriParam(defaultValue = "IfReplyExpected")
    private WaitForTaskToComplete waitForTaskToComplete = WaitForTaskToComplete.IfReplyExpected;
    @UriParam(defaultValue = "30000")
    private long timeout = 30000;
    @UriParam
    private boolean blockWhenFull;
    @UriParam(defaultValue = "Blocking")
    private DisruptorWaitStrategy waitStrategy;
    @UriParam(defaultValue = "Multi")
    private DisruptorProducerType producerType;

    private final Set<DisruptorProducer> producers = new CopyOnWriteArraySet<DisruptorProducer>();
    private final Set<DisruptorConsumer> consumers = new CopyOnWriteArraySet<DisruptorConsumer>();

    private final DisruptorReference disruptorReference;

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

    @ManagedAttribute(description = "Camel ID")
    public String getCamelId() {
        return getCamelContext().getName();
    }

    @ManagedAttribute(description = "Camel ManagementName")
    public String getCamelManagementName() {
        return getCamelContext().getManagementName();
    }

    @ManagedAttribute(description = "Endpoint Uri", mask = true)
    public String getEndpointUri() {
        return super.getEndpointUri();
    }

    @ManagedAttribute(description = "Service State")
    public String getState() {
        ServiceStatus status = this.getStatus();
        // if no status exists then its stopped
        if (status == null) {
            status = ServiceStatus.Stopped;
        }
        return status.name();
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

    @ManagedAttribute(description = "Number of concurrent consumers")
    public int getConcurrentConsumers() {
        return concurrentConsumers;
    }

    public WaitForTaskToComplete getWaitForTaskToComplete() {
        return waitForTaskToComplete;
    }

    public void setWaitForTaskToComplete(final WaitForTaskToComplete waitForTaskToComplete) {
        this.waitForTaskToComplete = waitForTaskToComplete;
    }

    @ManagedAttribute
    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(final long timeout) {
        this.timeout = timeout;
    }

    @ManagedAttribute
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

    @Override
    @ManagedAttribute
    public boolean isMultipleConsumersSupported() {
        return isMultipleConsumers();
    }

    @ManagedAttribute
    public boolean isBlockWhenFull() {
        return blockWhenFull;
    }

    public void setBlockWhenFull(boolean blockWhenFull) {
        this.blockWhenFull = blockWhenFull;
    }

    public DisruptorWaitStrategy getWaitStrategy() {
        return waitStrategy;
    }

    public void setWaitStrategy(DisruptorWaitStrategy waitStrategy) {
        this.waitStrategy = waitStrategy;
    }

    public DisruptorProducerType getProducerType() {
        return producerType;
    }

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
     *
     * @param exchange
     */
    void publish(final Exchange exchange) throws DisruptorNotStartedException {
        disruptorReference.publish(exchange);
    }

    /**
     * Called by DisruptorProducers to publish new exchanges on the RingBuffer, throwing InsufficientCapacityException
     * when full
     *
     * @param exchange
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
