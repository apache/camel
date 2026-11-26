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
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.AtomicMarkableReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;

import com.lmax.disruptor.InsufficientCapacityException;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holder for Disruptor references.
 * <p/>
 * This is used to keep track of the usages of the Disruptors, so we know when a Disruptor is no longer in use, and can
 * safely be discarded.
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

    // the exchanges taken from the ring buffer of a Disruptor without consumer when it is shut down, for the next
    // Disruptor: not bounded, as the producers that waited on its full ring buffer publish on it before it is shut down
    private final Queue<Exchange> temporaryExchangeBuffer = new LinkedBlockingQueue<>();
    private final Lock lock = new ReentrantLock();

    // the producers that have read the current Disruptor and not yet published on its ring buffer (or failed to): a
    // reconfiguration clears the reference, then waits for them before it drains and halts the Disruptor, so that no
    // exchange is published on a ring buffer whose event handlers have been halted
    private static final long MAX_PUBLISHES_IN_FLIGHT_PAUSE = TimeUnit.MILLISECONDS.toNanos(1);

    private final PublishesInFlight publishesInFlight = new PublishesInFlight();

    // the current Disruptor while it has consumers (the buffered exchanges are only published on such a Disruptor), and
    // whether a thread publishes the buffered exchanges that did not fit its ring buffer when it was started
    private volatile Disruptor<ExchangeEvent> consumingDisruptor;
    private final AtomicBoolean publishingBufferedExchanges = new AtomicBoolean();

    //access guarded by this
    private ExecutorService executor;
    // the CamelContext whose ExecutorServiceManager created the executor, which shuts it down when it stops
    private CamelContext executorContext;

    private LifecycleAwareExchangeEventHandler[] handlers = new LifecycleAwareExchangeEventHandler[0];

    private int uniqueConsumerCount;

    // the number of endpoints in the endpoints set, and of endpoints that are being added to it: read by the component
    // without the lock of this reference, which a reconfiguration holds while it waits for the exchanges in flight
    private final AtomicInteger registeredEndpoints = new AtomicInteger();
    private final AtomicInteger initializingEndpoints = new AtomicInteger();

    DisruptorReference(final DisruptorComponent component, final String uri, final String name, final int size,
                       final DisruptorProducerType producerType, final DisruptorWaitStrategy waitStrategy) throws Exception {
        this.component = component;
        this.uri = uri;
        this.name = name;
        this.size = size;
        this.producerType = producerType;
        this.waitStrategy = waitStrategy;
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
        final RingBuffer<ExchangeEvent> ringBuffer = beginPublish().getRingBuffer();
        try {
            tryPublishExchangeOnRingBuffer(exchange, ringBuffer);
        } finally {
            publishesInFlight.decrement();
        }
    }

    public void publish(final Exchange exchange) throws DisruptorNotStartedException {
        final RingBuffer<ExchangeEvent> ringBuffer = beginPublish().getRingBuffer();
        try {
            publishExchangeOnRingBuffer(exchange, ringBuffer);
        } finally {
            publishesInFlight.decrement();
        }
    }

    /**
     * The current Disruptor, waiting while the Disruptor is being reconfigured. The caller is counted in
     * {@link #publishesInFlight}, and must decrement it once it has published on its ring buffer, or failed to.
     */
    private Disruptor<ExchangeEvent> beginPublish() throws DisruptorNotStartedException {
        while (true) {
            // count, then read the reference: a reconfiguration clears the reference, then waits until the count is 0, so
            // either it waits for this producer, or this producer sees the cleared reference
            publishesInFlight.increment();
            final Disruptor<ExchangeEvent> currentDisruptor = disruptor.getReference();
            if (currentDisruptor != null) {
                return currentDisruptor;
            }
            publishesInFlight.decrement();
            // wait for the reconfiguration to end without being counted (or fail if the Disruptor is not started)
            getCurrentDisruptor();
        }
    }

    private void awaitPublishesInFlight() {
        // a producer in flight usually ends within microseconds, but one that waits for a free slot waits for a consumer:
        // the pause doubles up to 1 ms, so a long wait does not keep a core busy. parkNanos returns at once while the
        // interrupt flag is set, so on an interrupted thread (such as a CamelContext stop that timed out) the loop would
        // spin: the flag is cleared for the wait, and set again after it
        boolean interrupted = Thread.interrupted();
        try {
            long pause = 1000L;
            while (!publishesInFlight.isZero()) {
                LockSupport.parkNanos(pause);
                pause = Math.min(pause * 2, MAX_PUBLISHES_IN_FLIGHT_PAUSE);
                if (Thread.interrupted()) {
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void publishExchangeOnRingBuffer(
            final Exchange exchange,
            final RingBuffer<ExchangeEvent> ringBuffer) {
        final long sequence = ringBuffer.next();
        ringBuffer.get(sequence).setExchange(exchange, uniqueConsumerCount);
        ringBuffer.publish(sequence);
    }

    private void tryPublishExchangeOnRingBuffer(final Exchange exchange, final RingBuffer<ExchangeEvent> ringBuffer)
            throws InsufficientCapacityException {
        final long sequence = ringBuffer.tryNext();
        ringBuffer.get(sequence).setExchange(exchange, uniqueConsumerCount);
        ringBuffer.publish(sequence);
    }

    public void reconfigure() throws Exception {
        lock.lock();
        try {
            LOGGER.debug("Reconfiguring disruptor {}", this);
            shutdownDisruptor(true);

            start();
            startPublishingBufferedExchanges();
        } finally {
            lock.unlock();
        }
    }

    private void start() throws Exception {
        LOGGER.debug("Starting disruptor {}", this);
        Disruptor<ExchangeEvent> newDisruptor = null;
        try {
            newDisruptor = createDisruptor();
            startDisruptor(newDisruptor);
        } catch (Exception | Error e) {
            // the reference was cleared and marked as being reconfigured, so producers wait for the new Disruptor: mark
            // it as not started instead, so they fail (as when no Disruptor was started) instead of waiting forever
            disruptor.set(null, false);
            // and do not run the event processors of this Disruptor later on the executor (the next start would
            // hand them over with its own), nor keep running the ones already handed over
            delayedExecutor.clear();
            if (newDisruptor != null) {
                newDisruptor.halt();
            }
            handlers = new LifecycleAwareExchangeEventHandler[0];
            LOGGER.warn("Failed to start disruptor {}, producers fail until it is started again"
                        + " ({} exchanges buffered before this failure are kept until then): {}",
                    uri, temporaryExchangeBuffer.size(), e.toString());
            throw e;
        }
    }

    private void startDisruptor(Disruptor<ExchangeEvent> newDisruptor) throws Exception {
        newDisruptor.start();

        if (executor != null) {
            //and use our delayed executor to really really execute the event handlers now
            delayedExecutor.executeDelayedCommands(executor);
        }

        //make sure all event handlers are correctly started before we continue
        //The disruptor start command executed above should have triggered a start signal to all
        //event processors which, in their death, should notify our event handlers. They respond by
        //switching a latch and we want to await that latch here to make sure they are started.
        awaitEventHandlers(handlers, true);

        // a Disruptor without consumer would only take the buffered exchanges back into the buffer when it is shut down,
        // so they stay buffered, in order, for the first consumer
        final boolean hasConsumers = getBlockingEventHandler() == null;
        if (hasConsumers) {
            // the buffered exchanges go first: as many as the ring buffer holds, before the producers can use it (this
            // does not wait, as nothing else publishes on it yet). The rest is published by another thread once the
            // reference is set (startPublishingBufferedExchanges), as publishing more than the ring buffer holds waits
            // for its consumers, which may need a lock that is held here (this one, that of the endpoint, or that of
            // the CamelContext that starts the route)
            publishBufferedExchanges(newDisruptor.getRingBuffer());
        }

        consumingDisruptor = hasConsumers ? newDisruptor : null;
        disruptor.set(newDisruptor, false);
    }

    /**
     * Starts a thread that publishes the buffered exchanges that did not fit the ring buffer of the Disruptor that was
     * just started, if there are any and it has consumers (the buffer holds more exchanges than the ring buffer when
     * producers waited on the full ring buffer of a Disruptor without consumer). It publishes them as a producer does,
     * without holding a lock, so that a consumer that needs one of the locks held while the Disruptor is started does
     * not wait for the thread that started it. Called after {@link #start()}, outside of it, so that a failure here
     * does not affect a Disruptor that producers already use: when the thread cannot be started, the exchanges stay
     * buffered for the next start (a {@link VirtualMachineError} is thrown again).
     */
    private void startPublishingBufferedExchanges() {
        if (!hasBufferedExchangesToPublish() || !publishingBufferedExchanges.compareAndSet(false, true)) {
            // nothing to do, or the thread that already does it publishes these too
            return;
        }
        try {
            CamelContext context = executorContext != null ? executorContext : component.getCamelContext();
            Thread thread = context.getExecutorServiceManager()
                    .newThread("DisruptorBacklog[" + uri + "]", this::publishRestOfBufferedExchanges);
            thread.start();
        } catch (Throwable e) {
            // such as the OutOfMemoryError of a thread that cannot be created: no thread publishes them, so the next
            // start must be able to start one
            publishingBufferedExchanges.set(false);
            LOGGER.warn("Cannot start the thread that publishes the {} buffered exchanges of disruptor {}, they are"
                        + " published when it is started again: {}",
                    temporaryExchangeBuffer.size(), uri, e.toString());
            if (e instanceof VirtualMachineError vme) {
                throw vme;
            }
        }
    }

    private boolean hasBufferedExchangesToPublish() {
        final Disruptor<ExchangeEvent> currentDisruptor = disruptor.getReference();
        return currentDisruptor != null && currentDisruptor == consumingDisruptor && !temporaryExchangeBuffer.isEmpty();
    }

    private void publishRestOfBufferedExchanges() {
        do {
            try {
                while (publishNextBufferedExchange()) {
                    // publish the next one
                }
            } finally {
                publishingBufferedExchanges.set(false);
            }
            // a Disruptor started while this thread was ending did not start another one: go on with its exchanges
        } while (hasBufferedExchangesToPublish() && publishingBufferedExchanges.compareAndSet(false, true));
    }

    /**
     * Publishes the first buffered exchange on the current Disruptor, as a producer does. Returns false, leaving the
     * exchanges buffered, when there is none, when the current Disruptor has no consumer (the first consumer gets them,
     * in order) or when it is not started (the next start publishes them).
     */
    private boolean publishNextBufferedExchange() {
        final Disruptor<ExchangeEvent> currentDisruptor;
        try {
            currentDisruptor = beginPublish();
        } catch (DisruptorNotStartedException e) {
            return false;
        }
        try {
            if (currentDisruptor != consumingDisruptor || temporaryExchangeBuffer.isEmpty()) {
                return false;
            }
            // only this thread and start() take exchanges from the buffer, and start() runs only after a reconfiguration
            // has waited for this thread (counted in publishesInFlight): the first exchange is still there once a slot
            // is free, and is published before the ones behind it
            final RingBuffer<ExchangeEvent> ringBuffer = currentDisruptor.getRingBuffer();
            final long sequence = ringBuffer.next();
            ringBuffer.get(sequence).setExchange(temporaryExchangeBuffer.poll(), uniqueConsumerCount);
            ringBuffer.publish(sequence);
            return true;
        } finally {
            publishesInFlight.decrement();
        }
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
            final Map<DisruptorConsumer, Collection<LifecycleAwareExchangeEventHandler>> consumerEventHandlers
                    = endpoint.createConsumerEventHandlers();

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
                eventHandlers.toArray(new LifecycleAwareExchangeEventHandler[0]));

        return newDisruptor;
    }

    private void handleEventsWith(
            Disruptor<ExchangeEvent> newDisruptor,
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

    private void publishBufferedExchanges(RingBuffer<ExchangeEvent> ringBuffer) {
        Exchange exchange;
        while (ringBuffer.remainingCapacity() > 0 && (exchange = temporaryExchangeBuffer.poll()) != null) {
            publishExchangeOnRingBuffer(exchange, ringBuffer);
        }
    }

    /**
     * The event handler that keeps a Disruptor without consumer busy, or null if the Disruptor has consumers.
     */
    private BlockingExchangeEventHandler getBlockingEventHandler() {
        if (handlers.length == 1 && handlers[0] instanceof BlockingExchangeEventHandler blockingExchangeEventHandler) {
            return blockingExchangeEventHandler;
        }
        return null;
    }

    /**
     * The CamelContext to create the threads of the event handlers with: the CamelContext of an endpoint that uses this
     * Disruptor (with disruptor-vm, the endpoints can belong to several CamelContexts), else that of the component that
     * created it.
     */
    private CamelContext resolveExecutorContext() {
        for (DisruptorEndpoint endpoint : endpoints) {
            CamelContext context = endpoint.getCamelContext();
            if (context != null) {
                return context;
            }
        }
        return component.getCamelContext();
    }

    private boolean isExecutorContextInUse() {
        for (DisruptorEndpoint endpoint : endpoints) {
            if (endpoint.getCamelContext() == executorContext) {
                return true;
            }
        }
        return false;
    }

    private void resizeThreadPoolExecutor(final int newSize) {
        if (executor == null && newSize > 0) {
            LOGGER.debug("Creating new executor with {} threads", newSize);
            //no thread pool executor yet, create a new one
            executorContext = resolveExecutorContext();
            executor = executorContext.getExecutorServiceManager().newFixedThreadPool(this, uri, newSize);
        } else if (executor != null && newSize <= 0) {
            LOGGER.debug("Shutting down executor");
            //we need to shut down our executor
            executorContext.getExecutorServiceManager().shutdown(executor);
            executor = null;
            executorContext = null;
        } else if (executor instanceof ThreadPoolExecutor threadPoolExecutor) {
            LOGGER.debug("Resizing existing executor to {} threads", newSize);
            //our thread pool executor is of type ThreadPoolExecutor, we know how to resize it
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
            executorContext.getExecutorServiceManager().shutdown(executor);

            executorContext = resolveExecutorContext();
            executor = executorContext.getExecutorServiceManager().newFixedThreadPool(this, uri, newSize);
        }
    }

    private void shutdownDisruptor(boolean isReconfiguring) {
        lock.lock();
        try {
            LOGGER.debug("Shutting down disruptor {}, reconfiguring: {}", this, isReconfiguring);
            Disruptor<ExchangeEvent> currentDisruptor = disruptor.getReference();
            disruptor.set(null, isReconfiguring);

            if (currentDisruptor != null) {
                //check if we had a blocking event handler to keep an empty disruptor 'busy'
                BlockingExchangeEventHandler blockingExchangeEventHandler = getBlockingEventHandler();
                if (blockingExchangeEventHandler != null) {
                    // yes we did, unblock it so we can get rid of our backlog,
                    // The eventhandler will empty its pending exchanges in our temporary buffer
                    blockingExchangeEventHandler.unblock();
                }

                // the producers that read the reference before it was cleared publish on this ring buffer, also those
                // that wait for a free slot on a full ring buffer (the event handlers keep running until the shutdown
                // below, and a blocking event handler was unblocked above): wait for them, so the shutdown drains their
                // exchanges, instead of halting the event handlers before they are published
                awaitPublishesInFlight();

                currentDisruptor.shutdown();

                //they have already been given a trigger to halt when they are done by shutting down the disruptor
                //we do however want to await their completion before they are scheduled to process events from the new
                //(the event processors, in their death, notify our event handlers, which switch a latch we await here)
                awaitEventHandlers(handlers, false);

                handlers = new LifecycleAwareExchangeEventHandler[0];
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Waits until each of the event handlers has started (or stopped), for at most 10 seconds each. The interrupt flag
     * is kept for the caller, and an interrupt never makes this wait again in a loop (the wait clears the flag when it
     * throws).
     * <p/>
     * An interrupt does not end the wait for the start: producers use the new Disruptor once the start returns, and the
     * next shutdown of a Disruptor (LMAX) only waits for the event processors that run, so a Disruptor whose event
     * processors have not run yet would be halted with the exchanges published on it. The wait goes on for the rest of
     * the time limit, and the flag is set again at the end. An interrupt ends the wait for the stop:
     * {@link Disruptor#shutdown()} returned only once the event processors had taken every exchange published on the
     * ring buffer and had handled it, so an event handler that has not stopped yet is only shutting down, as after the
     * time limit.
     */
    static void awaitEventHandlers(final LifecycleAwareExchangeEventHandler[] eventHandlers, final boolean start) {
        boolean interrupted = false;
        try {
            for (final LifecycleAwareExchangeEventHandler eventHandler : eventHandlers) {
                final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                boolean waiting = true;
                while (waiting) {
                    try {
                        final long remaining = deadline - System.nanoTime();
                        final boolean done = start
                                ? eventHandler.awaitStarted(remaining, TimeUnit.NANOSECONDS)
                                : eventHandler.awaitStopped(remaining, TimeUnit.NANOSECONDS);
                        if (!done) {
                            //we wait for a relatively long, but limited amount of time to prevent an application using
                            //this component from hanging indefinitely
                            //Please report a bug if you can reproduce this
                            LOGGER.error("Disruptor/event handler failed to {} properly, PLEASE REPORT",
                                    start ? "start" : "shut down");
                        }
                        waiting = false;
                    } catch (InterruptedException e) {
                        if (!start) {
                            LOGGER.warn("Interrupted while waiting for the shutdown of the event handlers of a disruptor"
                                        + " to complete, not waiting for them any longer");
                            interrupted = true;
                            return;
                        }
                        if (!interrupted) {
                            LOGGER.warn("Interrupted while waiting for the startup of the event handlers of a disruptor"
                                        + " to complete, still waiting for them before the disruptor is used");
                        }
                        interrupted = true;
                    }
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void shutdownExecutor() {
        lock.lock();
        try {
            resizeThreadPoolExecutor(0);
        } finally {
            lock.unlock();
        }
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
                return (int) (getBufferSize() - getRemainingCapacity() + temporaryExchangeBuffer.size());
            }
        } catch (DisruptorNotStartedException e) {
            //fall through...
        }
        return temporaryExchangeBuffer.size();
    }

    public void addEndpoint(final DisruptorEndpoint disruptorEndpoint) {
        lock.lock();
        try {
            LOGGER.debug("Adding Endpoint: {}", disruptorEndpoint);
            // the disruptor was shut down when its last endpoint was removed, such as while this endpoint was being
            // created on it: start it again, so that it buffers the messages sent before a consumer starts
            boolean restart = endpoints.isEmpty() && hasNullReference();
            endpoints.add(disruptorEndpoint);
            registeredEndpoints.set(endpoints.size());
            if (restart) {
                LOGGER.debug("Starting disruptor {} again for endpoint {}", uri, disruptorEndpoint);
                try {
                    start();
                } catch (Exception e) {
                    endpoints.remove(disruptorEndpoint);
                    registeredEndpoints.set(endpoints.size());
                    throw RuntimeCamelException.wrapRuntimeCamelException(e);
                }
                startPublishingBufferedExchanges();
            }
            LOGGER.debug("Endpoint added: {}, new total endpoints {}", disruptorEndpoint, endpoints.size());
        } finally {
            lock.unlock();
        }
    }

    public void removeEndpoint(final DisruptorEndpoint disruptorEndpoint) {
        lock.lock();
        try {
            LOGGER.debug("Removing Endpoint: {}", disruptorEndpoint);
            if (getEndpointCount() == 1) {
                LOGGER.debug("Last Endpoint removed, shutdown disruptor");
                //Shutdown our disruptor
                shutdownDisruptor(false);

                //As there are no endpoints dependent on this Disruptor, we may also shutdown our executor
                shutdownExecutor();
            }
            endpoints.remove(disruptorEndpoint);
            registeredEndpoints.set(endpoints.size());
            LOGGER.debug("Endpoint removed: {}, new total endpoints {}", disruptorEndpoint, getEndpointCount());
            if (executor != null && !endpoints.isEmpty() && !isExecutorContextInUse()) {
                // a disruptor-vm queue shared with other CamelContexts: the CamelContext of the threads of the event
                // handlers no longer uses the queue, and shuts the threads down when it stops, so move them to the
                // CamelContext of an endpoint that still uses it, or its consumers stop consuming for good
                LOGGER.info("Moving the event handlers of disruptor {} from CamelContext {}, which no longer uses it, to"
                            + " CamelContext {}",
                        uri, executorContext.getName(), resolveExecutorContext().getName());
                shutdownDisruptor(true);
                shutdownExecutor();
                try {
                    start();
                } catch (Exception e) {
                    throw RuntimeCamelException.wrapRuntimeCamelException(e);
                }
                startPublishingBufferedExchanges();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * An endpoint is going to be added ({@link #addEndpoint(DisruptorEndpoint)}).
     */
    void beginInitEndpoint() {
        initializingEndpoints.incrementAndGet();
    }

    /**
     * An endpoint that was going to be added has been added (or failed to).
     */
    void endInitEndpoint() {
        initializingEndpoints.decrementAndGet();
    }

    /**
     * Whether endpoints use this disruptor (or are being added to it). Unlike {@link #getEndpointCount()}, this does
     * not wait for a reconfiguration of the disruptor.
     */
    public boolean isInUse() {
        // in this order: an endpoint that is being added is counted in initializingEndpoints (under the lock of the
        // disruptors, which the callers hold) until after it is counted in registeredEndpoints
        return initializingEndpoints.get() > 0 || registeredEndpoints.get() > 0;
    }

    public int getEndpointCount() {
        lock.lock();
        try {
            return endpoints.size();
        } finally {
            lock.unlock();
        }
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

            if (DisruptorEndpoint.isIgnoreExchange(exchange)) {
                // the producer no longer waits for this exchange (timeout), so don't process it
                LOGGER.trace("Ignoring exchange {}", exchange);
            } else {
                // not bounded, so this never drops the exchange
                temporaryExchangeBuffer.offer(exchange);
            }
        }

        public void unblock() {
            blockingLatch.countDown();
        }

    }

    /**
     * When a consumer is added or removed, we need to create a new Disruptor due to its static configuration. However,
     * we would like to reuse our thread pool executor and only add or remove the threads we need. On a reconfiguraion
     * of the Disruptor, we need to atomically swap the current RingBuffer with a new and fully configured one in order
     * to keep the producers operational without the risk of losing messages. Configuration of a RingBuffer by the
     * Disruptor's start method has a side effect that immediately starts execution of the event processors (consumers)
     * on the Executor passed as a constructor argument which is stored in a final field. In order to be able to delay
     * actual execution of the event processors until the event processors of the previous RingBuffer are done
     * processing and the thread pool executor has been resized to match the new consumer count, we delay their
     * execution using this class.
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

        public void clear() {
            delayedCommands.clear();
        }
    }

    /**
     * The count of {@link #beginPublish()}, without a write shared by all the producers on each publish: a thread
     * counts in a cell chosen by its id (the same cell for its increment and its decrement), alone on its cache line.
     * <p>
     * A reconfiguration cannot stop waiting while a producer that read the old reference publishes: the producer
     * increments (a volatile getAndAdd) before its volatile read of the reference, and the reconfiguration clears the
     * reference (a volatile write) before its volatile reads of the cells, so the cell read sees the increment of a
     * producer that read the old reference. As a thread decrements the cell it incremented, after it, a cell is never
     * below 0, and reads 0 only when each increment it holds has its decrement: the cells being read one after the
     * other can only make the wait longer. A LongAdder does not keep that: it can move a thread to another cell between
     * its increment and its decrement, so a sum can see the decrement of a producer without its increment, which hides
     * another producer in flight.
     */
    private static final class PublishesInFlight {
        // 16 longs: 128 bytes, the larger cache line (such as on Apple silicon), so that no two cells share one
        private static final int PAD = 16;

        private final AtomicLongArray cells;
        private final int mask;

        PublishesInFlight() {
            // a power of 2, at least the number of processors up to 64 cells; with more processors some threads
            // share a cell, which is still correct (each thread adds and removes its own count in the cell it uses)
            int count = Math.min(64, Integer.highestOneBit(Math.max(1, Runtime.getRuntime().availableProcessors() - 1)) << 1);
            mask = count - 1;
            // a free cache line before the first cell (the array header) and after the last one
            cells = new AtomicLongArray((count + 2) * PAD);
        }

        private int index() {
            return (((int) Thread.currentThread().threadId() & mask) + 1) * PAD;
        }

        void increment() {
            cells.getAndIncrement(index());
        }

        void decrement() {
            cells.getAndDecrement(index());
        }

        boolean isZero() {
            for (int i = PAD; i < cells.length() - PAD; i += PAD) {
                if (cells.get(i) != 0) {
                    return false;
                }
            }
            return true;
        }
    }
}
