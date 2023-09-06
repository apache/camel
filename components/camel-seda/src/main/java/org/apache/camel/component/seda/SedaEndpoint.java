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
package org.apache.camel.component.seda;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;

import org.apache.camel.AsyncEndpoint;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.MultipleConsumersSupport;
import org.apache.camel.PollingConsumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.WaitForTaskToComplete;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.spi.BrowsableEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Asynchronously call another endpoint from any Camel Context in the same JVM.
 */
@ManagedResource(description = "Managed SedaEndpoint")
@UriEndpoint(firstVersion = "1.1.0", scheme = "seda", title = "SEDA", syntax = "seda:name",
             category = { Category.CORE, Category.MESSAGING })
public class SedaEndpoint extends DefaultEndpoint implements AsyncEndpoint, BrowsableEndpoint, MultipleConsumersSupport {

    private static final Logger LOG = LoggerFactory.getLogger(SedaEndpoint.class);

    private final Set<SedaProducer> producers = new CopyOnWriteArraySet<>();
    private final Set<SedaConsumer> consumers = new CopyOnWriteArraySet<>();
    private volatile AsyncProcessor consumerMulticastProcessor;
    private volatile boolean multicastStarted;
    private volatile ExecutorService multicastExecutor;

    @UriPath(description = "Name of queue")
    @Metadata(required = true)
    private String name;
    @UriParam(label = "advanced", description = "Define the queue instance which will be used by the endpoint")
    private BlockingQueue<Exchange> queue;
    @UriParam(defaultValue = "" + SedaConstants.QUEUE_SIZE)
    private int size = SedaConstants.QUEUE_SIZE;

    @UriParam(label = "consumer", defaultValue = "1")
    private int concurrentConsumers = 1;
    @UriParam(label = "consumer,advanced", defaultValue = "true")
    private boolean limitConcurrentConsumers = true;
    @UriParam(label = "consumer,advanced")
    private boolean multipleConsumers;
    @UriParam(label = "consumer,advanced")
    private boolean purgeWhenStopping;
    @UriParam(label = "consumer,advanced", defaultValue = "1000")
    private int pollTimeout = 1000;

    @UriParam(label = "producer", defaultValue = "IfReplyExpected")
    private WaitForTaskToComplete waitForTaskToComplete = WaitForTaskToComplete.IfReplyExpected;
    @UriParam(label = "producer", defaultValue = "30000", javaType = "java.time.Duration")
    private long timeout = 30000;
    @UriParam(label = "producer", javaType = "java.time.Duration")
    private long offerTimeout;
    @UriParam(label = "producer")
    private boolean blockWhenFull;
    @UriParam(label = "producer")
    private boolean discardWhenFull;
    @UriParam(label = "producer")
    private boolean failIfNoConsumers;
    @UriParam(label = "producer")
    private boolean discardIfNoConsumers;

    private BlockingQueueFactory<Exchange> queueFactory;

    public SedaEndpoint() {
        queueFactory = new LinkedBlockingQueueFactory<>();
    }

    public SedaEndpoint(String endpointUri, Component component, BlockingQueue<Exchange> queue) {
        this(endpointUri, component, queue, 1);
    }

    public SedaEndpoint(String endpointUri, Component component, BlockingQueue<Exchange> queue, int concurrentConsumers) {
        this(endpointUri, component, concurrentConsumers);
        this.queue = queue;
        if (queue != null) {
            this.size = queue.remainingCapacity();
        }
        queueFactory = new LinkedBlockingQueueFactory<>();
        getComponent().registerQueue(this, queue);
    }

    public SedaEndpoint(String endpointUri, Component component, BlockingQueueFactory<Exchange> queueFactory,
                        int concurrentConsumers) {
        this(endpointUri, component, concurrentConsumers);
        this.queueFactory = queueFactory;
    }

    private SedaEndpoint(String endpointUri, Component component, int concurrentConsumers) {
        super(endpointUri, component);
        this.concurrentConsumers = concurrentConsumers;
    }

    @Override
    public SedaComponent getComponent() {
        return (SedaComponent) super.getComponent();
    }

    @Override
    public Producer createProducer() throws Exception {
        return new SedaProducer(
                this, getWaitForTaskToComplete(), getTimeout(),
                isBlockWhenFull(), isDiscardWhenFull(), getOfferTimeout());
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        if (getComponent() != null) {
            // all consumers must match having the same multipleConsumers options
            String key = getComponent().getQueueKey(getEndpointUri());
            QueueReference ref = getComponent().getQueueReference(key);
            if (ref != null && ref.getMultipleConsumers() != isMultipleConsumers()) {
                // there is already a multiple consumers, so make sure they matches
                throw new IllegalArgumentException(
                        "Cannot use existing queue " + key + " as the existing queue multiple consumers "
                                                   + ref.getMultipleConsumers() + " does not match given multiple consumers "
                                                   + multipleConsumers);
            }
        }

        Consumer answer = createNewConsumer(processor);
        configureConsumer(answer);
        return answer;
    }

    protected SedaConsumer createNewConsumer(Processor processor) {
        return new SedaConsumer(this, processor);
    }

    @Override
    public PollingConsumer createPollingConsumer() throws Exception {
        SedaPollingConsumer answer = new SedaPollingConsumer(this);
        configureConsumer(answer);
        return answer;
    }

    public synchronized BlockingQueue<Exchange> getQueue() {
        if (queue == null) {
            // prefer to lookup queue from component, so if this endpoint is re-created or re-started
            // then the existing queue from the component can be used, so new producers and consumers
            // can use the already existing queue referenced from the component
            if (getComponent() != null) {
                // use null to indicate default size (= use what the existing queue has been configured with)
                Integer size = (getSize() == Integer.MAX_VALUE || getSize() == SedaConstants.QUEUE_SIZE) ? null : getSize();
                QueueReference ref = getComponent().getOrCreateQueue(this, size, isMultipleConsumers(), queueFactory);
                queue = ref.getQueue();
                String key = getComponent().getQueueKey(getEndpointUri());
                LOG.debug("Endpoint {} is using shared queue: {} with size: {}", this, key,
                        ref.getSize() != null ? ref.getSize() : Integer.MAX_VALUE);
                // and set the size we are using
                if (ref.getSize() != null) {
                    setSize(ref.getSize());
                }
            } else {
                // fallback and create queue (as this endpoint has no component)
                queue = createQueue();
                LOG.debug("Endpoint {} is using queue: {} with size: {}", this, getEndpointUri(), getSize());
            }
        }
        return queue;
    }

    protected BlockingQueue<Exchange> createQueue() {
        if (size > 0) {
            return queueFactory.create(size);
        } else {
            return queueFactory.create();
        }
    }

    /**
     * Gets the {@link QueueReference} for this endpoint.
     *
     * @return the reference, or <tt>null</tt> if no queue reference exists.
     */
    public QueueReference getQueueReference() {
        String key = getComponent().getQueueKey(getEndpointUri());

        synchronized (this) {
            return getComponent().getQueueReference(key);
        }
    }

    protected synchronized AsyncProcessor getConsumerMulticastProcessor() {
        if (!multicastStarted && consumerMulticastProcessor != null) {
            // only start it on-demand to avoid starting it during stopping
            ServiceHelper.startService(consumerMulticastProcessor);
            multicastStarted = true;
        }
        return consumerMulticastProcessor;
    }

    protected synchronized void updateMulticastProcessor() throws Exception {
        // only needed if we support multiple consumers
        if (!isMultipleConsumersSupported()) {
            return;
        }

        // stop old before we create a new
        if (consumerMulticastProcessor != null) {
            ServiceHelper.stopService(consumerMulticastProcessor);
            consumerMulticastProcessor = null;
        }

        int size = getConsumers().size();
        if (size >= 1) {
            if (multicastExecutor == null) {
                // create multicast executor as we need it when we have more than 1 processor
                multicastExecutor = getCamelContext().getExecutorServiceManager().newDefaultThreadPool(this,
                        URISupport.sanitizeUri(getEndpointUri()) + "(multicast)");
            }
            // create list of consumers to multicast to
            List<Processor> processors = new ArrayList<>(size);
            for (SedaConsumer consumer : getConsumers()) {
                processors.add(consumer.getProcessor());
            }
            // create multicast processor
            multicastStarted = false;

            consumerMulticastProcessor = (AsyncProcessor) PluginHelper.getProcessorFactory(getCamelContext())
                    .createProcessor(getCamelContext(), "MulticastProcessor",
                            new Object[] { processors, multicastExecutor, false });
        }
    }

    void setName(String name) {
        this.name = name;
    }

    @ManagedAttribute(description = "Queue name")
    public String getName() {
        return name;
    }

    /**
     * Define the queue instance which will be used by the endpoint.
     * <p/>
     * This option is only for rare use-cases where you want to use a custom queue instance.
     */
    public void setQueue(BlockingQueue<Exchange> queue) {
        this.queue = queue;
        this.size = queue.remainingCapacity();
    }

    @ManagedAttribute(description = "Queue max capacity")
    public int getSize() {
        return size;
    }

    /**
     * The maximum capacity of the SEDA queue (i.e., the number of messages it can hold). Will by default use the
     * defaultSize set on the SEDA component.
     */
    public void setSize(int size) {
        this.size = size;
    }

    @ManagedAttribute(description = "Current queue size")
    public int getCurrentQueueSize() {
        return queue.size();
    }

    /**
     * Whether a thread that sends messages to a full SEDA queue will block until the queue's capacity is no longer
     * exhausted. By default, an exception will be thrown stating that the queue is full. By enabling this option, the
     * calling thread will instead block and wait until the message can be accepted.
     */
    public void setBlockWhenFull(boolean blockWhenFull) {
        this.blockWhenFull = blockWhenFull;
    }

    @ManagedAttribute(description = "Whether the caller will block sending to a full queue")
    public boolean isBlockWhenFull() {
        return blockWhenFull;
    }

    /**
     * Whether a thread that sends messages to a full SEDA queue will be discarded. By default, an exception will be
     * thrown stating that the queue is full. By enabling this option, the calling thread will give up sending and
     * continue, meaning that the message was not sent to the SEDA queue.
     */
    public void setDiscardWhenFull(boolean discardWhenFull) {
        this.discardWhenFull = discardWhenFull;
    }

    @ManagedAttribute(description = "Whether the caller will discard sending to a full queue")
    public boolean isDiscardWhenFull() {
        return discardWhenFull;
    }

    /**
     * Number of concurrent threads processing exchanges.
     */
    public void setConcurrentConsumers(int concurrentConsumers) {
        this.concurrentConsumers = concurrentConsumers;
    }

    @ManagedAttribute(description = "Number of concurrent consumers")
    public int getConcurrentConsumers() {
        return concurrentConsumers;
    }

    @ManagedAttribute
    public boolean isLimitConcurrentConsumers() {
        return limitConcurrentConsumers;
    }

    /**
     * Whether to limit the number of concurrentConsumers to the maximum of 500. By default, an exception will be thrown
     * if an endpoint is configured with a greater number. You can disable that check by turning this option off.
     */
    public void setLimitConcurrentConsumers(boolean limitConcurrentConsumers) {
        this.limitConcurrentConsumers = limitConcurrentConsumers;
    }

    public WaitForTaskToComplete getWaitForTaskToComplete() {
        return waitForTaskToComplete;
    }

    /**
     * Option to specify whether the caller should wait for the async task to complete or not before continuing. The
     * following three options are supported: Always, Never or IfReplyExpected. The first two values are
     * self-explanatory. The last value, IfReplyExpected, will only wait if the message is Request Reply based. The
     * default option is IfReplyExpected.
     */
    public void setWaitForTaskToComplete(WaitForTaskToComplete waitForTaskToComplete) {
        this.waitForTaskToComplete = waitForTaskToComplete;
    }

    @ManagedAttribute
    public long getTimeout() {
        return timeout;
    }

    /**
     * Timeout (in milliseconds) before a SEDA producer will stop waiting for an asynchronous task to complete. You can
     * disable timeout by using 0 or a negative value.
     */
    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    @ManagedAttribute
    public long getOfferTimeout() {
        return offerTimeout;
    }

    /**
     * Offer timeout (in milliseconds) can be added to the block case when queue is full. You can disable timeout by
     * using 0 or a negative value.
     */
    public void setOfferTimeout(long offerTimeout) {
        this.offerTimeout = offerTimeout;
    }

    @ManagedAttribute
    public boolean isFailIfNoConsumers() {
        return failIfNoConsumers;
    }

    /**
     * Whether the producer should fail by throwing an exception, when sending to a queue with no active consumers.
     * <p/>
     * Only one of the options <tt>discardIfNoConsumers</tt> and <tt>failIfNoConsumers</tt> can be enabled at the same
     * time.
     */
    public void setFailIfNoConsumers(boolean failIfNoConsumers) {
        this.failIfNoConsumers = failIfNoConsumers;
    }

    @ManagedAttribute
    public boolean isDiscardIfNoConsumers() {
        return discardIfNoConsumers;
    }

    /**
     * Whether the producer should discard the message (do not add the message to the queue), when sending to a queue
     * with no active consumers.
     * <p/>
     * Only one of the options <tt>discardIfNoConsumers</tt> and <tt>failIfNoConsumers</tt> can be enabled at the same
     * time.
     */
    public void setDiscardIfNoConsumers(boolean discardIfNoConsumers) {
        this.discardIfNoConsumers = discardIfNoConsumers;
    }

    @ManagedAttribute
    public boolean isMultipleConsumers() {
        return multipleConsumers;
    }

    /**
     * Specifies whether multiple consumers are allowed. If enabled, you can use SEDA for Publish-Subscribe messaging.
     * That is, you can send a message to the SEDA queue and have each consumer receive a copy of the message. When
     * enabled, this option should be specified on every consumer endpoint.
     */
    public void setMultipleConsumers(boolean multipleConsumers) {
        this.multipleConsumers = multipleConsumers;
    }

    @ManagedAttribute
    public int getPollTimeout() {
        return pollTimeout;
    }

    /**
     * The timeout (in milliseconds) used when polling. When a timeout occurs, the consumer can check whether it is
     * allowed to continue running. Setting a lower value allows the consumer to react more quickly upon shutdown.
     */
    public void setPollTimeout(int pollTimeout) {
        this.pollTimeout = pollTimeout;
    }

    @ManagedAttribute
    public boolean isPurgeWhenStopping() {
        return purgeWhenStopping;
    }

    /**
     * Whether to purge the task queue when stopping the consumer/route. This allows to stop faster, as any pending
     * messages on the queue is discarded.
     */
    public void setPurgeWhenStopping(boolean purgeWhenStopping) {
        this.purgeWhenStopping = purgeWhenStopping;
    }

    /**
     * Returns the current pending exchanges
     */
    @Override
    public List<Exchange> getExchanges() {
        return new ArrayList<>(getQueue());
    }

    @Override
    @ManagedAttribute
    public boolean isMultipleConsumersSupported() {
        return isMultipleConsumers();
    }

    /**
     * Purges the queue
     */
    @ManagedOperation(description = "Purges the seda queue")
    public void purgeQueue() {
        LOG.debug("Purging queue with {} exchanges", queue.size());
        queue.clear();
    }

    /**
     * Returns the current active consumers on this endpoint
     */
    public Set<SedaConsumer> getConsumers() {
        return consumers;
    }

    /**
     * Returns the current active producers on this endpoint
     */
    public Set<SedaProducer> getProducers() {
        return new HashSet<>(producers);
    }

    void onStarted(SedaProducer producer) {
        producers.add(producer);
    }

    void onStopped(SedaProducer producer) {
        producers.remove(producer);
    }

    void onStarted(SedaConsumer consumer) throws Exception {
        consumers.add(consumer);
        if (isMultipleConsumers()) {
            updateMulticastProcessor();
        }
    }

    void onStopped(SedaConsumer consumer) throws Exception {
        consumers.remove(consumer);
        if (isMultipleConsumers()) {
            updateMulticastProcessor();
        }
    }

    public boolean hasConsumers() {
        return !this.consumers.isEmpty();
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        if (discardWhenFull && blockWhenFull) {
            throw new IllegalArgumentException(
                    "Cannot enable both discardWhenFull=true and blockWhenFull=true."
                                               + " You can only either discard or block when full.");
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // force creating queue when starting
        if (queue == null) {
            queue = getQueue();
        }
    }

    @Override
    public void stop() {
        if (getConsumers().isEmpty()) {
            super.stop();
        } else {
            LOG.debug("There is still active consumers.");
        }
    }

    @Override
    public void shutdown() {
        if (isShutdown()) {
            LOG.trace("Service already shut down");
            return;
        }

        // notify component we are shutting down this endpoint
        if (getComponent() != null) {
            getComponent().onShutdownEndpoint(this);
        }

        if (getConsumers().isEmpty()) {
            super.shutdown();
        } else {
            LOG.debug("There is still active consumers.");
        }
    }

    @Override
    protected void doShutdown() throws Exception {
        // shutdown thread pool if it was in use
        if (multicastExecutor != null) {
            getCamelContext().getExecutorServiceManager().shutdownNow(multicastExecutor);
            multicastExecutor = null;
        }

        // clear queue, as we are shutdown, so if re-created then the queue must be updated
        queue = null;
    }

}
