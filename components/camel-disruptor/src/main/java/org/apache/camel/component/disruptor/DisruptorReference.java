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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicMarkableReference;
import java.util.concurrent.locks.LockSupport;

import com.lmax.disruptor.InsufficientCapacityException;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holder for Disruptor references.
 * <p/>
 * This is used to keep track of the usages of the Disruptors, so we know when a Disruptor is no longer in use, and
 * can safely be discarded.
 */
public class DisruptorReference {
    private static final Logger LOGGER = LoggerFactory.getLogger(DisruptorReference.class);

    private final Set<DisruptorEndpoint> endpoints = Collections
            .newSetFromMap(new WeakHashMap<DisruptorEndpoint, Boolean>(4));
    private final DisruptorComponent component;
    private final String uri;
    private final String name;

    //The mark on the reference indicates if we are in the process of reconfiguring the Disruptor:
    //(ref,   mark) : Description
    //(null, false) : not started or completely shut down
    //(null,  true) : in process of reconfiguring
    //( x  , false) : normally functioning Disruptor
    //( x  ,  true) : never set
    private final AtomicMarkableReference<Disruptor<ExchangeEvent>> disruptor = new AtomicMarkableReference<>(null, false);

    private final DelayedExecutor delayedExecutor = new DelayedExecutor();

    private final DisruptorProducerType producerType;

    private final int size;

    private final DisruptorWaitStrategy waitStrategy;

    private final Queue<Exchange> temporaryExchangeBuffer;

    //access guarded by this
    private ExecutorService executor;

    private LifecycleAwareExchangeEventHandler[] handlers = new LifecycleAwareExchangeEventHandler[0];

    private int uniqueConsumerCount;

    DisruptorReference(final DisruptorComponent component, final String uri, final String name, final int size,
                       final DisruptorProducerType producerType, final DisruptorWaitStrategy waitStrategy) throws Exception {
        this.component = component;
        this.uri = uri;
        this.name = name;
        this.size = size;
        this.producerType = producerType;
        this.waitStrategy = waitStrategy;
        temporaryExchangeBuffer = new ArrayBlockingQueue<>(size);
        reconfigure();
    }

    public boolean hasNullReference() {
        return disruptor.getReference() == null;
    }

    private Disruptor<ExchangeEvent> getCurrentDisruptor() throws DisruptorNotStartedException {
        Disruptor<ExchangeEvent> currentDisruptor = disruptor.getReference();

        if (currentDisruptor == null) {
            // no current Disruptor reference, we may be reconfiguring or it was not started
            // check which by looking at the reference mark...
            boolean[] changeIsPending = new boolean[1];

            while (currentDisruptor == null) {
                currentDisruptor = disruptor.get(changeIsPending);
                //Check if we are reconfiguring
                if (currentDisruptor == null && !changeIsPending[0]) {
                    throw new DisruptorNotStartedException(
                            "Disruptor is not yet started or already shut down.");
                } else if (currentDisruptor == null && changeIsPending[0]) {
                    //We should be back shortly...keep trying but spare CPU resources
                    LockSupport.parkNanos(1L);
                }
            }
        }

        return currentDisruptor;
    }

    public void tryPublish(final Exchange exchange) throws DisruptorNotStartedException, InsufficientCapacityException {
        tryPublishExchangeOnRingBuffer(exchange, getCurrentDisruptor().getRingBuffer());
    }

    public void publish(final Exchange exchange) throws DisruptorNotStartedException {
        publishExchangeOnRingBuffer(exchange, getCurrentDisruptor().getRingBuffer());
    }

    private void publishExchangeOnRingBuffer(final Exchange exchange,
                                                             final RingBuffer<ExchangeEvent> ringBuffer) {
        final long sequence = ringBuffer.next();
        ringBuffer.get(sequence).setExchange(exchange, uniqueConsumerCount);
        ringBuffer.publish(sequence);
    }

    private void tryPublishExchangeOnRingBuffer(final Exchange exchange, final RingBuffer<ExchangeEvent> ringBuffer) throws InsufficientCapacityException {
        final long sequence = ringBuffer.tryNext();
        ringBuffer.get(sequence).setExchange(exchange, uniqueConsumerCount);
        ringBuffer.publish(sequence);
    }

    public synchronized void reconfigure() throws Exception {
        LOGGER.debug("Reconfiguring disruptor {}", this);
        shutdownDisruptor(true);

        start();
    }

    private void start() throws Exception {
        LOGGER.debug("Starting disruptor {}", this);
        Disruptor<ExchangeEvent> newDisruptor = createDisruptor();

        newDisruptor.start();

        if (executor != null) {
            //and use our delayed executor to really really execute the event handlers now
            delayedExecutor.executeDelayedCommands(executor);
        }

        //make sure all event handlers are correctly started before we continue
        for (final LifecycleAwareExchangeEventHandler handler : handlers) {
            boolean eventHandlerStarted = false;
            while (!eventHandlerStarted) {
                try {
                    //The disruptor start command executed above should have triggered a start signal to all
                    //event processors which, in their death, should notify our event handlers. They respond by
                    //switching a latch and we want to await that latch here to make sure they are started.
                    if (!handler.awaitStarted(10, TimeUnit.SECONDS)) {
                        //we wait for a relatively long, but limited amount of time to prevent an application using
                        //this component from hanging indefinitely
                        //Please report a bug if you can reproduce this
                        LOGGER.error("Disruptor/event handler failed to start properly, PLEASE REPORT");
                    }
                    eventHandlerStarted = true;
                } catch (InterruptedException e) {
                    //just retry
                }
            }
        }

        publishBufferedExchanges(newDisruptor);

        disruptor.set(newDisruptor, false);
    }

    private Disruptor<ExchangeEvent> createDisruptor() throws Exception {
        //create a new Disruptor
        final Disruptor<ExchangeEvent> newDisruptor = new Disruptor<>(
                ExchangeEventFactory.INSTANCE, size, delayedExecutor, producerType.getProducerType(),
                waitStrategy.createWaitStrategyInstance());

        //determine the list of eventhandlers to be associated to the Disruptor
        final ArrayList<LifecycleAwareExchangeEventHandler> eventHandlers = new ArrayList<>();

        uniqueConsumerCount = 0;

        for (final DisruptorEndpoint endpoint : endpoints) {
            final Map<DisruptorConsumer, Collection<LifecycleAwareExchangeEventHandler>> consumerEventHandlers = endpoint.createConsumerEventHandlers();

            if (consumerEventHandlers != null) {
                uniqueConsumerCount += consumerEventHandlers.keySet().size();

                for (Collection<LifecycleAwareExchangeEventHandler> lifecycleAwareExchangeEventHandlers : consumerEventHandlers
                        .values()) {
                    eventHandlers.addAll(lifecycleAwareExchangeEventHandlers);
                }

            }
        }

        LOGGER.debug("Disruptor created with {} event handlers", eventHandlers.size());
        handleEventsWith(newDisruptor,
                eventHandlers.toArray(new LifecycleAwareExchangeEventHandler[eventHandlers.size()]));

        return newDisruptor;
    }

    private void handleEventsWith(Disruptor<ExchangeEvent> newDisruptor,
                                  final LifecycleAwareExchangeEventHandler[] newHandlers) {
        if (newHandlers == null || newHandlers.length == 0) {
            handlers = new LifecycleAwareExchangeEventHandler[1];
            handlers[0] = new BlockingExchangeEventHandler();
        } else {
            handlers = newHandlers;
        }
        resizeThreadPoolExecutor(handlers.length);
        newDisruptor.handleEventsWith(handlers);
    }

    private void publishBufferedExchanges(Disruptor<ExchangeEvent> newDisruptor) {
        //now empty out all buffered Exchange if we had any
        final List<Exchange> exchanges = new ArrayList<>(temporaryExchangeBuffer.size());
        while (!temporaryExchangeBuffer.isEmpty()) {
            exchanges.add(temporaryExchangeBuffer.remove());
        }
        RingBuffer<ExchangeEvent> ringBuffer = newDisruptor.getRingBuffer();
        //and offer them again to our new ringbuffer
        for (final Exchange exchange : exchanges) {
            publishExchangeOnRingBuffer(exchange, ringBuffer);
        }
    }

    private void resizeThreadPoolExecutor(final int newSize) {
        if (executor == null && newSize > 0) {
            LOGGER.debug("Creating new executor with {} threads", newSize);
            //no thread pool executor yet, create a new one
            executor = component.getCamelContext().getExecutorServiceManager().newFixedThreadPool(this, uri,
                    newSize);
        } else if (executor != null && newSize <= 0) {
            LOGGER.debug("Shutting down executor");
            //we need to shut down our executor
            component.getCamelContext().getExecutorServiceManager().shutdown(executor);
            executor = null;
        } else if (executor instanceof ThreadPoolExecutor) {
            LOGGER.debug("Resizing existing executor to {} threads", newSize);
            //our thread pool executor is of type ThreadPoolExecutor, we know how to resize it
            final ThreadPoolExecutor threadPoolExecutor = (ThreadPoolExecutor)executor;
            //Java 9 support, checkout http://download.java.net/java/jdk9/docs/api/java/util/concurrent/ThreadPoolExecutor.html#setCorePoolSize-int- 
            // and http://download.java.net/java/jdk9/docs/api/java/util/concurrent/ThreadPoolExecutor.html#setMaximumPoolSize-int-
            //for more information
            if (newSize <= threadPoolExecutor.getCorePoolSize()) {
                threadPoolExecutor.setCorePoolSize(newSize);
                threadPoolExecutor.setMaximumPoolSize(newSize);
            } else {
                threadPoolExecutor.setMaximumPoolSize(newSize);
                threadPoolExecutor.setCorePoolSize(newSize);
            }
        } else if (newSize > 0) {
            LOGGER.debug("Shutting down old and creating new executor with {} threads", newSize);
            //hmmm...no idea what kind of executor this is...just kill it and start fresh
            component.getCamelContext().getExecutorServiceManager().shutdown(executor);

            executor = component.getCamelContext().getExecutorServiceManager().newFixedThreadPool(this, uri,
                    newSize);
        }
    }

    private synchronized void shutdownDisruptor(boolean isReconfiguring) {
        LOGGER.debug("Shutting down disruptor {}, reconfiguring: {}", this, isReconfiguring);
        Disruptor<ExchangeEvent> currentDisruptor = disruptor.getReference();
        disruptor.set(null, isReconfiguring);

        if (currentDisruptor != null) {
            //check if we had a blocking event handler to keep an empty disruptor 'busy'
            if (handlers != null && handlers.length == 1
                    && handlers[0] instanceof BlockingExchangeEventHandler) {
                // yes we did, unblock it so we can get rid of our backlog,
                // The eventhandler will empty its pending exchanges in our temporary buffer
                final BlockingExchangeEventHandler blockingExchangeEventHandler = (BlockingExchangeEventHandler)handlers[0];
                blockingExchangeEventHandler.unblock();
            }

            currentDisruptor.shutdown();

            //they have already been given a trigger to halt when they are done by shutting down the disruptor
            //we do however want to await their completion before they are scheduled to process events from the new
            for (final LifecycleAwareExchangeEventHandler eventHandler : handlers) {
                boolean eventHandlerFinished = false;
                //the disruptor is now empty and all consumers are either done or busy processing their last exchange
                while (!eventHandlerFinished) {
                    try {
                        //The disruptor shutdown command executed above should have triggered a halt signal to all
                        //event processors which, in their death, should notify our event handlers. They respond by
                        //switching a latch and we want to await that latch here to make sure they are done.
                        if (!eventHandler.awaitStopped(10, TimeUnit.SECONDS)) {
                            //we wait for a relatively long, but limited amount of time to prevent an application using
                            //this component from hanging indefinitely
                            //Please report a bug if you can repruduce this
                            LOGGER.error("Disruptor/event handler failed to shut down properly, PLEASE REPORT");
                        }
                        eventHandlerFinished = true;
                    } catch (InterruptedException e) {
                        //just retry
                    }
                }
            }

            handlers = new LifecycleAwareExchangeEventHandler[0];
        }
    }

    private synchronized void shutdownExecutor() {
        resizeThreadPoolExecutor(0);
    }

    public String getName() {
        return name;
    }

    public long getRemainingCapacity() throws DisruptorNotStartedException {
        return getCurrentDisruptor().getRingBuffer().remainingCapacity();
    }

    public DisruptorWaitStrategy getWaitStrategy() {
        return waitStrategy;
    }

    DisruptorProducerType getProducerType() {
        return producerType;
    }

    public int getBufferSize() {
        return size;
    }

    public int getPendingExchangeCount() {
        try {
            if (!hasNullReference()) {
                return (int)(getBufferSize() - getRemainingCapacity() + temporaryExchangeBuffer.size());
            }
        } catch (DisruptorNotStartedException e) {
            //fall through...
        }
        return temporaryExchangeBuffer.size();
    }

    public synchronized void addEndpoint(final DisruptorEndpoint disruptorEndpoint) {
        LOGGER.debug("Adding Endpoint: " + disruptorEndpoint);
        endpoints.add(disruptorEndpoint);
        LOGGER.debug("Endpoint added: {}, new total endpoints {}", disruptorEndpoint, endpoints.size());
    }

    public synchronized void removeEndpoint(final DisruptorEndpoint disruptorEndpoint) {
        LOGGER.debug("Removing Endpoint: " + disruptorEndpoint);
        if (getEndpointCount() == 1) {
            LOGGER.debug("Last Endpoint removed, shutdown disruptor");
            //Shutdown our disruptor
            shutdownDisruptor(false);

            //As there are no endpoints dependent on this Disruptor, we may also shutdown our executor
            shutdownExecutor();
        }
        endpoints.remove(disruptorEndpoint);
        LOGGER.debug("Endpoint removed: {}, new total endpoints {}", disruptorEndpoint, getEndpointCount());
    }

    public synchronized int getEndpointCount() {
        return endpoints.size();
    }

    @Override
    public String toString() {
        return "DisruptorReference{" + "uri='" + uri + '\'' + ", endpoint count=" + endpoints.size()
               + ", handler count=" + handlers.length + '}';
    }

    /**
     * Implementation of the {@link LifecycleAwareExchangeEventHandler} interface that blocks all calls to the #onEvent
     * method until the #unblock method is called.
     */
    private class BlockingExchangeEventHandler extends AbstractLifecycleAwareExchangeEventHandler {

        private final CountDownLatch blockingLatch = new CountDownLatch(1);

        @Override
        public void onEvent(final ExchangeEvent event, final long sequence, final boolean endOfBatch) throws Exception {
            blockingLatch.await();
            final Exchange exchange = event.getSynchronizedExchange().cancelAndGetOriginalExchange();

            if (exchange.getProperty(DisruptorEndpoint.DISRUPTOR_IGNORE_EXCHANGE, false, boolean.class)) {
                // Property was set and it was set to true, so don't process Exchange.
                LOGGER.trace("Ignoring exchange {}", exchange);
            } else {
                temporaryExchangeBuffer.offer(exchange);
            }
        }

        public void unblock() {
            blockingLatch.countDown();
        }

    }

    /**
     * When a consumer is added or removed, we need to create a new Disruptor due to its static configuration. However, we
     * would like to reuse our thread pool executor and only add or remove the threads we need. On a reconfiguraion of the
     * Disruptor, we need to atomically swap the current RingBuffer with a new and fully configured one in order to keep
     * the producers operational without the risk of losing messages. Configuration of a RingBuffer by the Disruptor's
     * start method has a side effect that immediately starts execution of the event processors (consumers) on the
     * Executor passed as a constructor argument which is stored in a final field. In order to be able to delay actual
     * execution of the event processors until the event processors of the previous RingBuffer are done processing and the
     * thread pool executor has been resized to match the new consumer count, we delay their execution using this class.
     */
    private static class DelayedExecutor implements Executor {

        private final Queue<Runnable> delayedCommands = new LinkedList<>();

        @Override
        public void execute(final Runnable command) {
            delayedCommands.offer(command);
        }

        public void executeDelayedCommands(final Executor actualExecutor) {
            Runnable command;

            while ((command = delayedCommands.poll()) != null) {
                actualExecutor.execute(command);
            }
        }
    }
}
