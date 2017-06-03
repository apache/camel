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
package org.apache.camel.component.kestrel;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Exchanger;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import net.spy.memcached.MemcachedClient;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ShutdownRunningTask;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.camel.spi.ShutdownAware;

/**
 * A Camel consumer that polls a kestrel queue.
 */
public class KestrelConsumer extends DefaultConsumer implements ShutdownAware {
    private final KestrelEndpoint endpoint;
    private final MemcachedClient memcachedClient;
    private final BlockingQueue<Exchanger<?>> exchangerQueue = new LinkedBlockingQueue<Exchanger<?>>();
    private ExecutorService pollerExecutor;
    private ExecutorService handlerExecutor;
    private volatile boolean shutdownPending;
    private CountDownLatch shutdownLatch;
    private AtomicInteger pendingExchangeCount = new AtomicInteger(0);

    public KestrelConsumer(final KestrelEndpoint endpoint, Processor processor, final MemcachedClient memcachedClient) {
        super(endpoint, processor);
        this.endpoint = endpoint;
        this.memcachedClient = memcachedClient;
    }

    @Override
    protected void doStart() throws Exception {
        log.info("Starting consumer for " + endpoint.getEndpointUri());

        int poolSize = endpoint.getConfiguration().getConcurrentConsumers();

        shutdownPending = false;

        if (poolSize > 1) {
            // We'll set the shutdown latch to poolSize + 1, since we'll also
            // wait for the poller thread when shutting down.
            shutdownLatch = new CountDownLatch(poolSize + 1);

            // Fire up the handler thread pool
            handlerExecutor = endpoint.getCamelContext().getExecutorServiceManager().newFixedThreadPool(this, "Handlers-" + endpoint.getEndpointUri(), poolSize);
            for (int k = 0; k < poolSize; ++k) {
                handlerExecutor.execute(new Handler());
            }
        } else {
            // Since we only have concurrentConsumers=1, we'll do the handling
            // inside the poller thread, so there will only be one thread to
            // wait for on this latch.
            shutdownLatch = new CountDownLatch(1);
        }

        // Fire up the single poller thread
        pollerExecutor = endpoint.getCamelContext().getExecutorServiceManager().newSingleThreadExecutor(this, "Poller-" + endpoint.getEndpointUri());
        pollerExecutor.submit(new Poller(poolSize > 1));

        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        log.info("Stopping consumer for " + endpoint.getEndpointUri());

        if (pollerExecutor != null) {
            endpoint.getCamelContext().getExecutorServiceManager().shutdown(pollerExecutor);
            pollerExecutor = null;
        }
        if (handlerExecutor != null) {
            endpoint.getCamelContext().getExecutorServiceManager().shutdown(handlerExecutor);
            handlerExecutor = null;
        }

        super.doStop();
    }

    public boolean deferShutdown(ShutdownRunningTask shutdownRunningTask) {
        return false;
    }

    public int getPendingExchangesSize() {
        return pendingExchangeCount.get();
    }

    @Override
    public void prepareShutdown(boolean suspendOnly, boolean forced) {
        // Signal to our threads that shutdown is happening
        shutdownPending = true;

        if (log.isDebugEnabled()) {
            log.debug("Preparing to shutdown, waiting for {} threads to complete.", shutdownLatch.getCount());
        }

        // Wait for all threads to end
        try {
            shutdownLatch.await();
        } catch (InterruptedException e) {
            // ignore
        }
    }

    /**
     * This single thread is responsible for reading objects from kestrel and
     * dispatching them to the handler threads.  The catch is that we don't
     * want to poll kestrel until we know we have a handler thread available
     * and waiting to handle whatever comes up.  So the way we deal with that
     * is...each handler thread has an exchanger used to "receive" objects
     * from the kestrel reader thread.  When a handler thread is ready for
     * work, it simply puts its exchanger in the queue.  The kestrel reader
     * thread takes an exchanger from the queue (which will block until one
     * is there), and *then* it can poll kestrel.  Once an object is received
     * from kestrel, it gets exchanged with the handler thread, which can
     * take the object and process it.  Repeat...
     */
    @SuppressWarnings("unchecked")
    private final class Poller implements Runnable {
        private boolean concurrent;

        private Poller(boolean concurrent) {
            this.concurrent = concurrent;
        }

        public void run() {
            log.trace("Kestrel poller is running");

            // Construct the target key that we'll be requesting from kestrel.
            // Include the /t=... wait time as applicable.
            String target;
            if (endpoint.getConfiguration().getWaitTimeMs() > 0) {
                target = endpoint.getQueue() + "/t=" + endpoint.getConfiguration().getWaitTimeMs();
            } else {
                target = endpoint.getQueue();
            }

            @SuppressWarnings("rawtypes")
            Exchanger exchanger = null;
            while (isRunAllowed() && !shutdownPending) {
                if (concurrent) {
                    // Wait until an exchanger is available, indicating that a
                    // handler thread is ready to handle the next request.
                    // Don't read from kestrel until we know a handler is ready.
                    try {
                        exchanger = exchangerQueue.take();
                    } catch (InterruptedException e) {
                        if (log.isDebugEnabled()) {
                            log.debug("Interrupted, are we stopping? {}", isStopping() || isStopped());
                        }
                        continue;
                    }

                    // We have the exchanger, so there's a handler thread ready
                    // to handle whatever we may read...so read the next object
                    // from the queue.
                }

                // Poll kestrel until we get an object back
                Object value = null;
                while (isRunAllowed() && !shutdownPending) {
                    log.trace("Polling {}", target);
                    try {
                        value = memcachedClient.get(target);
                        if (value != null) {
                            break;
                        }
                    } catch (Exception e) {
                        if (isRunAllowed() && !shutdownPending) {
                            getExceptionHandler().handleException("Failed to get object from kestrel", e);
                        }
                    }

                    // We didn't get a value back from kestrel
                    if (isRunAllowed() && !shutdownPending) {
                        if (endpoint.getConfiguration().getWaitTimeMs() > 0) {
                            // Kestrel did the blocking for us
                        } else {
                            // We're doing non-blocking get, so in between we
                            // should at least sleep some short period of time
                            // so this loop doesn't go nuts so tightly.
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException ignored) {
                            }
                        }
                    }
                }

                log.trace("Got object from {}", target);

                if (concurrent) {
                    // Pass the object to the handler thread via the exchanger.
                    // The handler will take it from there.
                    try {
                        exchanger.exchange(value);
                    } catch (InterruptedException e) {
                        if (log.isDebugEnabled()) {
                            log.debug("Interrupted, are we stopping? {}", isStopping() || isStopped());
                        }
                        continue;
                    }
                } else {
                    // We're non-concurrent, so handle it right here
                    pendingExchangeCount.incrementAndGet();
                    try {
                        // Create the exchange and let camel process/route it
                        Exchange exchange = null;
                        try {
                            exchange = endpoint.createExchange();
                            exchange.getIn().setBody(value);
                            getProcessor().process(exchange);
                        } catch (Exception e) {
                            if (exchange != null) {
                                getExceptionHandler().handleException("Error processing exchange", exchange, e);
                            } else {
                                getExceptionHandler().handleException(e);
                            }
                        }
                    } finally {
                        // Decrement our pending exchange counter
                        pendingExchangeCount.decrementAndGet();
                    }
                }
            }
            log.trace("Finished polling {}", target);

            // Decrement the shutdown countdown latch
            shutdownLatch.countDown();
        }
    }

    private final class Handler implements Runnable {
        private Exchanger<Handler> exchanger = new Exchanger<Handler>();

        public void run() {
            if (log.isTraceEnabled()) {
                log.trace("{} is starting", Thread.currentThread().getName());
            }

            while (isRunAllowed() && !shutdownPending) {
                // First things first, add our exchanger to the queue,
                // indicating that we're ready for a hand-off of work
                try {
                    exchangerQueue.put(exchanger);
                } catch (InterruptedException e) {
                    if (log.isDebugEnabled()) {
                        log.debug("Interrupted, are we stopping? {}", isStopping() || isStopped());
                    }
                    continue;
                }

                // Optimistically increment our internal pending exchange
                // counter, anticipating getting a value back from the exchanger
                pendingExchangeCount.incrementAndGet();
                try {
                    // Now wait for an object to come through the exchanger
                    Object value;
                    try {
                        value = exchanger.exchange(this);
                    } catch (InterruptedException e) {
                        if (log.isDebugEnabled()) {
                            log.debug("Interrupted, are we stopping? {}", isStopping() || isStopped());
                        }
                        continue;
                    }

                    log.trace("Got a value from the exchanger");

                    // Create the exchange and let camel process/route it
                    Exchange exchange = null;
                    try {
                        exchange = endpoint.createExchange();
                        exchange.getIn().setBody(value);
                        getProcessor().process(exchange);
                    } catch (Exception e) {
                        if (exchange != null) {
                            getExceptionHandler().handleException("Error processing exchange", exchange, e);
                        } else {
                            getExceptionHandler().handleException(e);
                        }
                    }
                } finally {
                    // Decrement our pending exchange counter
                    pendingExchangeCount.decrementAndGet();
                }
            }

            // Decrement the shutdown countdown latch
            shutdownLatch.countDown();

            if (log.isTraceEnabled()) {
                log.trace("{} is finished", Thread.currentThread().getName());
            }
        }
    }
}
