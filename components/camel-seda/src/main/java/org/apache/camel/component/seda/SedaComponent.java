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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

import org.apache.camel.Component;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.spi.Metadata;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The <a href="http://camel.apache.org/seda.html">SEDA Component</a> is for asynchronous SEDA exchanges on a
 * {@link BlockingQueue} within a CamelContext
 */
@org.apache.camel.spi.annotations.Component("seda")
public class SedaComponent extends DefaultComponent {
    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final int maxConcurrentConsumers = SedaConstants.MAX_CONCURRENT_CONSUMERS;

    @Metadata(label = "consumer", defaultValue = "" + SedaConstants.CONCURRENT_CONSUMERS)
    protected int concurrentConsumers = SedaConstants.CONCURRENT_CONSUMERS;
    @Metadata(label = "advanced", defaultValue = "" + SedaConstants.QUEUE_SIZE)
    protected int queueSize = SedaConstants.QUEUE_SIZE;
    @Metadata(label = "advanced")
    protected BlockingQueueFactory<Exchange> defaultQueueFactory = new LinkedBlockingQueueFactory<>();
    @Metadata(label = "producer")
    private boolean defaultBlockWhenFull;
    @Metadata(label = "producer")
    private boolean defaultDiscardWhenFull;
    @Metadata(label = "producer")
    private long defaultOfferTimeout;
    @Metadata(label = "consumer,advanced", defaultValue = "1000")
    private int defaultPollTimeout = 1000;

    private final Map<String, QueueReference> queues = new HashMap<>();
    private final Map<String, Integer> customSize = new HashMap<>();

    public SedaComponent() {
    }

    /**
     * Sets the default maximum capacity of the SEDA queue (i.e., the number of messages it can hold).
     */
    public void setQueueSize(int size) {
        queueSize = size;
    }

    public int getQueueSize() {
        return queueSize;
    }

    /**
     * Sets the default number of concurrent threads processing exchanges.
     */
    public void setConcurrentConsumers(int size) {
        concurrentConsumers = size;
    }

    public int getConcurrentConsumers() {
        return concurrentConsumers;
    }

    public BlockingQueueFactory<Exchange> getDefaultQueueFactory() {
        return defaultQueueFactory;
    }

    /**
     * Sets the default queue factory.
     */
    public void setDefaultQueueFactory(BlockingQueueFactory<Exchange> defaultQueueFactory) {
        this.defaultQueueFactory = defaultQueueFactory;
    }

    public boolean isDefaultBlockWhenFull() {
        return defaultBlockWhenFull;
    }

    /**
     * Whether a thread that sends messages to a full SEDA queue will block until the queue's capacity is no longer
     * exhausted. By default, an exception will be thrown stating that the queue is full. By enabling this option, the
     * calling thread will instead block and wait until the message can be accepted.
     */
    public void setDefaultBlockWhenFull(boolean defaultBlockWhenFull) {
        this.defaultBlockWhenFull = defaultBlockWhenFull;
    }

    /**
     * Whether a thread that sends messages to a full SEDA queue will be discarded. By default, an exception will be
     * thrown stating that the queue is full. By enabling this option, the calling thread will give up sending and
     * continue, meaning that the message was not sent to the SEDA queue.
     */
    public boolean isDefaultDiscardWhenFull() {
        return defaultDiscardWhenFull;
    }

    public void setDefaultDiscardWhenFull(boolean defaultDiscardWhenFull) {
        this.defaultDiscardWhenFull = defaultDiscardWhenFull;
    }

    public long getDefaultOfferTimeout() {
        return defaultOfferTimeout;
    }

    /**
     * Whether a thread that sends messages to a full SEDA queue will block until the queue's capacity is no longer
     * exhausted. By default, an exception will be thrown stating that the queue is full. By enabling this option, where
     * a configured timeout can be added to the block case. Utilizing the .offer(timeout) method of the underlining java
     * queue
     */
    public void setDefaultOfferTimeout(long defaultOfferTimeout) {
        this.defaultOfferTimeout = defaultOfferTimeout;
    }

    public int getDefaultPollTimeout() {
        return defaultPollTimeout;
    }

    /**
     * The timeout (in milliseconds) used when polling. When a timeout occurs, the consumer can check whether it is
     * allowed to continue running. Setting a lower value allows the consumer to react more quickly upon shutdown.
     */
    public void setDefaultPollTimeout(int defaultPollTimeout) {
        this.defaultPollTimeout = defaultPollTimeout;
    }

    public synchronized QueueReference getOrCreateQueue(
            SedaEndpoint endpoint, Integer size, Boolean multipleConsumers, BlockingQueueFactory<Exchange> customQueueFactory) {

        String key = getQueueKey(endpoint.getEndpointUri());

        if (size == null) {
            // there may be a custom size during startup
            size = customSize.get(key);
        }

        QueueReference ref = getQueues().get(key);
        if (ref != null) {
            // if the given size is not provided, we just use the existing queue as is
            if (size != null && !size.equals(ref.getSize())) {
                // there is already a queue, so make sure the size matches
                throw new IllegalArgumentException(
                        "Cannot use existing queue " + key + " as the existing queue size "
                                                   + (ref.getSize() != null ? ref.getSize() : SedaConstants.QUEUE_SIZE)
                                                   + " does not match given queue size " + size);
            }
            // add the reference before returning queue
            ref.addReference(endpoint);

            if (log.isDebugEnabled()) {
                log.debug("Reusing existing queue {} with size {} and reference count {}", key, size, ref.getCount());
            }
            return ref;
        }

        // create queue
        BlockingQueue<Exchange> queue;
        BlockingQueueFactory<Exchange> queueFactory = customQueueFactory == null ? defaultQueueFactory : customQueueFactory;
        if (size != null && size > 0) {
            queue = queueFactory.create(size);
        } else {
            if (getQueueSize() > 0) {
                size = getQueueSize();
                queue = queueFactory.create(getQueueSize());
            } else {
                queue = queueFactory.create();
            }
        }
        log.debug("Created queue {} with size {}", key, size);

        // create and add a new reference queue
        ref = new QueueReference(queue, size, multipleConsumers);
        ref.addReference(endpoint);
        getQueues().put(key, ref);

        return ref;
    }

    public synchronized QueueReference registerQueue(SedaEndpoint endpoint, BlockingQueue<Exchange> queue) {
        String key = getQueueKey(endpoint.getEndpointUri());

        QueueReference ref = getQueues().get(key);
        if (ref == null) {
            ref = new QueueReference(queue, endpoint.getSize(), endpoint.isMultipleConsumers());
            ref.addReference(endpoint);
            getQueues().put(key, ref);
        }

        return ref;
    }

    public Map<String, QueueReference> getQueues() {
        return queues;
    }

    public QueueReference getQueueReference(String key) {
        return queues.get(key);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        int consumers = getAndRemoveOrResolveReferenceParameter(parameters, "concurrentConsumers", Integer.class,
                concurrentConsumers);
        boolean limitConcurrentConsumers
                = getAndRemoveOrResolveReferenceParameter(parameters, "limitConcurrentConsumers", Boolean.class, true);
        if (limitConcurrentConsumers && consumers > maxConcurrentConsumers) {
            throw new IllegalArgumentException(
                    "The limitConcurrentConsumers flag in set to true. ConcurrentConsumers cannot be set at a value greater than "
                                               + maxConcurrentConsumers + " was " + consumers);
        }

        // Resolve queue reference
        BlockingQueue<Exchange> queue = resolveAndRemoveReferenceParameter(parameters, "queue", BlockingQueue.class);
        SedaEndpoint answer;
        // Resolve queue factory when no queue specified
        if (queue == null) {
            BlockingQueueFactory<Exchange> queueFactory
                    = resolveAndRemoveReferenceParameter(parameters, "queueFactory", BlockingQueueFactory.class);
            // defer creating queue till endpoint is started, so we pass the queue factory
            answer = createEndpoint(uri, this, queueFactory, consumers);
        } else {
            answer = createEndpoint(uri, this, queue, consumers);
        }
        answer.setName(remaining);

        // if blockWhenFull is set on endpoint, defaultBlockWhenFull is ignored.
        boolean blockWhenFull = getAndRemoveParameter(parameters, "blockWhenFull", Boolean.class, defaultBlockWhenFull);
        // if discardWhenFull is set on endpoint, defaultBlockWhenFull is ignored.
        boolean discardWhenFull = getAndRemoveParameter(parameters, "discardWhenFull", Boolean.class, defaultDiscardWhenFull);
        // if offerTimeout is set on endpoint, defaultOfferTimeout is ignored.
        long offerTimeout = getAndRemoveParameter(parameters, "offerTimeout", long.class, defaultOfferTimeout);
        // if offerTimeout is set on endpoint, defaultOfferTimeout is ignored.
        int pollTimeout = getAndRemoveParameter(parameters, "pollTimeout", int.class, defaultPollTimeout);

        // using custom size?
        Integer size = getAndRemoveParameter(parameters, "size", Integer.class);
        if (size != null) {
            answer.setSize(size);
            // this queue has a custom size remember this while setting up routes
            if (!getCamelContext().isStarted()) {
                String key = getQueueKey(uri);
                customSize.put(key, size);
            }
        }

        answer.setOfferTimeout(offerTimeout);
        answer.setBlockWhenFull(blockWhenFull);
        answer.setDiscardWhenFull(discardWhenFull);
        answer.setConcurrentConsumers(consumers);
        answer.setLimitConcurrentConsumers(limitConcurrentConsumers);
        answer.setPollTimeout(pollTimeout);
        setProperties(answer, parameters);
        return answer;
    }

    protected SedaEndpoint createEndpoint(
            String endpointUri, Component component, BlockingQueueFactory<Exchange> queueFactory, int concurrentConsumers) {
        return new SedaEndpoint(endpointUri, component, queueFactory, concurrentConsumers);
    }

    protected SedaEndpoint createEndpoint(
            String endpointUri, Component component, BlockingQueue<Exchange> queue, int concurrentConsumers) {
        return new SedaEndpoint(endpointUri, component, queue, concurrentConsumers);
    }

    public String getQueueKey(String uri) {
        return StringHelper.before(uri, "?", uri);
    }

    @Override
    protected void doStop() throws Exception {
        getQueues().clear();
        customSize.clear();
        super.doStop();
    }

    /**
     * On shutting down the endpoint
     *
     * @param endpoint the endpoint
     */
    void onShutdownEndpoint(SedaEndpoint endpoint) {
        // we need to remove the endpoint from the reference counter
        String key = getQueueKey(endpoint.getEndpointUri());
        QueueReference ref = getQueues().get(key);
        if (ref != null && endpoint.getConsumers().isEmpty()) {
            // only remove the endpoint when the consumers are removed
            ref.removeReference(endpoint);
            if (ref.getCount() <= 0) {
                // reference no longer needed so remove from queues
                getQueues().remove(key);
            }
        }
    }

}
