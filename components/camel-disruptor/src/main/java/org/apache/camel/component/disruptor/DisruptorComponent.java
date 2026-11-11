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
package org.apache.camel.component.disruptor;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An implementation of the <a href="https://github.com/sirchia/camel-disruptor">Disruptor component</a> for
 * asynchronous SEDA exchanges on an <a href="https://github.com/LMAX-Exchange/disruptor">LMAX Disruptor</a> within a
 * CamelContext
 */
@Component("disruptor")
public class DisruptorComponent extends DefaultComponent {

    public static final int DEFAULT_BUFFER_SIZE = 1024;
    public static final int MAX_CONCURRENT_CONSUMERS = 500;

    private static final Logger LOGGER = LoggerFactory.getLogger(DisruptorComponent.class);

    @Metadata(defaultValue = "" + DEFAULT_BUFFER_SIZE)
    private int bufferSize = -1;
    @Metadata(label = "consumer", defaultValue = "1")
    private int defaultConcurrentConsumers = 1;
    @Metadata(label = "consumer")
    private boolean defaultMultipleConsumers;
    @Metadata(label = "producer", defaultValue = "Multi")
    private DisruptorProducerType defaultProducerType = DisruptorProducerType.Multi;
    @Metadata(label = "consumer", defaultValue = "Blocking")
    private DisruptorWaitStrategy defaultWaitStrategy = DisruptorWaitStrategy.Blocking;
    @Metadata(label = "producer", defaultValue = "true")
    private boolean defaultBlockWhenFull = true;

    // access guarded by getDisruptorsLock()
    private final Map<String, DisruptorReference> disruptors = new HashMap<>();

    public DisruptorComponent() {
    }

    @Override
    protected Endpoint createEndpoint(
            final String uri, final String remaining,
            final Map<String, Object> parameters)
            throws Exception {

        final int concurrentConsumers
                = getAndRemoveParameter(parameters, "concurrentConsumers", Integer.class, defaultConcurrentConsumers);
        final boolean limitConcurrentConsumers
                = getAndRemoveParameter(parameters, "limitConcurrentConsumers", Boolean.class, true);

        if (limitConcurrentConsumers && concurrentConsumers > MAX_CONCURRENT_CONSUMERS) {
            throw new IllegalArgumentException(
                    "The limitConcurrentConsumers flag in set to true. ConcurrentConsumers cannot be set at a value greater than "
                                               + MAX_CONCURRENT_CONSUMERS + " was " + concurrentConsumers);
        }

        if (concurrentConsumers < 0) {
            throw new IllegalArgumentException(
                    "concurrentConsumers found to be " + concurrentConsumers
                                               + ", must be greater than 0");
        }

        int size = 0;
        if (parameters.containsKey("size")) {
            size = getAndRemoveParameter(parameters, "size", int.class);
            if (size <= 0) {
                throw new IllegalArgumentException("size found to be " + size + ", must be greater than 0");
            }
        }

        // Check if the pollTimeout argument is set (maybe the case if the Disruptor component is used as drop-in
        // replacement for the SEDA component).
        if (parameters.containsKey("pollTimeout")) {
            throw new IllegalArgumentException("The 'pollTimeout' argument is not supported by the Disruptor component");
        }

        DisruptorWaitStrategy waitStrategy
                = getAndRemoveParameter(parameters, "waitStrategy", DisruptorWaitStrategy.class, defaultWaitStrategy);
        DisruptorProducerType producerType
                = getAndRemoveParameter(parameters, "producerType", DisruptorProducerType.class, defaultProducerType);
        boolean multipleConsumers
                = getAndRemoveParameter(parameters, "multipleConsumers", boolean.class, defaultMultipleConsumers);
        boolean blockWhenFull = getAndRemoveParameter(parameters, "blockWhenFull", boolean.class, defaultBlockWhenFull);

        DisruptorReference disruptorReference = getOrCreateDisruptor(uri, remaining, size, producerType, waitStrategy);
        DisruptorEndpoint answer = new DisruptorEndpoint(uri, this, disruptorReference);
        answer.setConcurrentConsumers(concurrentConsumers);
        answer.setMultipleConsumers(multipleConsumers);
        answer.setBlockWhenFull(blockWhenFull);
        answer.setWaitStrategy(waitStrategy);
        answer.setProducerType(producerType);
        setProperties(answer, parameters);

        return answer;
    }

    private DisruptorReference getOrCreateDisruptor(
            final String uri, final String name, final int size,
            final DisruptorProducerType producerType,
            final DisruptorWaitStrategy waitStrategy)
            throws Exception {
        final String key = getDisruptorKey(uri);

        int sizeToUse;
        if (size > 0) {
            sizeToUse = size;
        } else if (bufferSize > 0) {
            sizeToUse = bufferSize;
        } else {
            sizeToUse = DEFAULT_BUFFER_SIZE;
        }
        sizeToUse = powerOfTwo(sizeToUse);

        Lock disruptorsLock = getDisruptorsLock();
        disruptorsLock.lock();
        try {
            DisruptorReference ref = getDisruptors().get(key);
            if (ref == null) {
                LOGGER.debug("Creating new disruptor for key {}", key);
                ref = new DisruptorReference(this, uri, name, sizeToUse, producerType, waitStrategy);
                getDisruptors().put(key, ref);
            } else {
                //if size was explicitly requested, the size to use should match the retrieved DisruptorReference
                if (size != 0 && ref.getBufferSize() != sizeToUse) {
                    // there is already a queue, so make sure the size matches
                    throw new IllegalArgumentException(
                            "Cannot use existing queue " + key + " as the existing queue size "
                                                       + ref.getBufferSize() + " does not match given queue size " + sizeToUse);
                }
                LOGGER.debug("Reusing disruptor {} for key {}", ref, key);
            }

            return ref;
        } finally {
            disruptorsLock.unlock();
        }
    }

    /**
     * The lock that guards the disruptors of {@link #getDisruptors()}. It must be shared by all the components that
     * share these disruptors. It is held while a disruptor is created. It is taken after the lock of an endpoint or of
     * a component, and the lock of an existing disruptor reference is never taken while it is held (a reconfiguration
     * holds that one while it waits for the exchanges in flight).
     */
    protected Lock getDisruptorsLock() {
        return lock;
    }

    /**
     * An endpoint is initialized. The disruptor it was created with may have been removed since (the last other
     * endpoint of the disruptor was shut down meanwhile), and the key may even have a new disruptor by now: the
     * endpoint uses the disruptor of the key, or puts its own back for the key, so that all the endpoints of the key
     * use the same one.
     */
    void onInitEndpoint(DisruptorEndpoint disruptorEndpoint) {
        DisruptorReference disruptorReference;
        Lock disruptorsLock = getDisruptorsLock();
        disruptorsLock.lock();
        try {
            String key = getDisruptorKey(disruptorEndpoint.getEndpointUri());
            DisruptorReference own = disruptorEndpoint.getDisruptor();
            disruptorReference = getDisruptors().putIfAbsent(key, own);
            if (disruptorReference != null && disruptorReference != own) {
                if (disruptorReference.getBufferSize() != own.getBufferSize()) {
                    LOGGER.warn(
                            "Endpoint {} uses the disruptor of key {} that was created meanwhile, with size {} instead of {}",
                            disruptorEndpoint, key, disruptorReference.getBufferSize(), own.getBufferSize());
                }
                disruptorEndpoint.setDisruptor(disruptorReference);
            } else {
                disruptorReference = own;
            }
            // keeps the disruptor in the map until the endpoint is added to it, outside this lock
            disruptorReference.beginInitEndpoint();
        } finally {
            disruptorsLock.unlock();
        }
        try {
            disruptorReference.addEndpoint(disruptorEndpoint);
        } finally {
            disruptorReference.endInitEndpoint();
        }
    }

    private static int powerOfTwo(int size) {
        size--;
        size |= size >> 1;
        size |= size >> 2;
        size |= size >> 4;
        size |= size >> 8;
        size |= size >> 16;
        size++;
        return size;
    }

    public static String getDisruptorKey(String uri) {
        return StringHelper.before(uri, "?", uri);
    }

    @Override
    protected void doStop() throws Exception {
        Lock disruptorsLock = getDisruptorsLock();
        disruptorsLock.lock();
        try {
            getDisruptors().clear();
        } finally {
            disruptorsLock.unlock();
        }
        super.doStop();
    }

    public Map<String, DisruptorReference> getDisruptors() {
        return disruptors;
    }

    public int getDefaultConcurrentConsumers() {
        return defaultConcurrentConsumers;
    }

    /**
     * To configure the default number of concurrent consumers
     */
    public void setDefaultConcurrentConsumers(final int defaultConcurrentConsumers) {
        this.defaultConcurrentConsumers = defaultConcurrentConsumers;
    }

    public boolean isDefaultMultipleConsumers() {
        return defaultMultipleConsumers;
    }

    /**
     * To configure the default value for multiple consumers
     */
    public void setDefaultMultipleConsumers(final boolean defaultMultipleConsumers) {
        this.defaultMultipleConsumers = defaultMultipleConsumers;
    }

    public DisruptorProducerType getDefaultProducerType() {
        return defaultProducerType;
    }

    /**
     * To configure the default value for DisruptorProducerType
     * <p/>
     * The default value is Multi.
     */
    public void setDefaultProducerType(final DisruptorProducerType defaultProducerType) {
        this.defaultProducerType = defaultProducerType;
    }

    public DisruptorWaitStrategy getDefaultWaitStrategy() {
        return defaultWaitStrategy;
    }

    /**
     * To configure the default value for DisruptorWaitStrategy
     * <p/>
     * The default value is Blocking.
     */
    public void setDefaultWaitStrategy(final DisruptorWaitStrategy defaultWaitStrategy) {
        this.defaultWaitStrategy = defaultWaitStrategy;
    }

    public boolean isDefaultBlockWhenFull() {
        return defaultBlockWhenFull;
    }

    /**
     * To configure the default value for block when full
     * <p/>
     * The default value is true.
     */
    public void setDefaultBlockWhenFull(boolean defaultBlockWhenFull) {
        this.defaultBlockWhenFull = defaultBlockWhenFull;
    }

    /**
     * To configure the ring buffer size
     */
    public void setBufferSize(final int size) {
        bufferSize = size;
    }

    public int getBufferSize() {
        return bufferSize;
    }

    public void onShutdownEndpoint(DisruptorEndpoint disruptorEndpoint) {
        Lock disruptorsLock = getDisruptorsLock();
        disruptorsLock.lock();
        try {
            // the disruptor of this endpoint, as the one of the key may be another one by now
            DisruptorReference disruptorReference = disruptorEndpoint.getDisruptor();
            if (!disruptorReference.isInUse()) {
                //the last disruptor has been removed, we can delete the disruptor
                getDisruptors().remove(getDisruptorKey(disruptorEndpoint.getEndpointUri()), disruptorReference);
            }
        } finally {
            disruptorsLock.unlock();
        }
    }
}
