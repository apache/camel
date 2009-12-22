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
package org.apache.camel.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.SuspendableService;
import org.apache.camel.spi.ShutdownAware;
import org.apache.camel.spi.ShutdownStrategy;
import org.apache.camel.util.EventHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;
import org.apache.camel.util.concurrent.ExecutorServiceHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Default {@link org.apache.camel.spi.ShutdownStrategy} which uses graceful shutdown.
 * <p/>
 * Graceful shutdown ensures that any inflight and pending messages will be taken into account
 * and it will wait until these exchanges has been completed.
 * <p/>
 * As this strategy will politely wait until all exchanges has been completed it can potential wait
 * for a long time, and hence why a timeout value can be set. When the timeout triggers you can also
 * specify whether the remainder consumers should be shutdown now or ignore.
 * <p/>
 * Will by default use a timeout of 5 minutes by which it will shutdown now the remaining consumers.
 * This ensures that when shutting down Camel it at some point eventually will shutdown.
 * This behavior can of course be configured using the {@link #setTimeout(long)} and
 * {@link #setShutdownNowOnTimeout(boolean)} methods.
 *
 * @version $Revision$
 */
public class DefaultShutdownStrategy extends ServiceSupport implements ShutdownStrategy {
    private static final transient Log LOG = LogFactory.getLog(DefaultShutdownStrategy.class);

    private ExecutorService executor;
    private long timeout = 5 * 60;
    private TimeUnit timeUnit = TimeUnit.SECONDS;
    private boolean shutdownNowOnTimeout = true;

    public void shutdown(CamelContext context, List<Consumer> consumers) throws Exception {

        long start = System.currentTimeMillis();

        if (timeout > 0) {
            LOG.info("Starting to graceful shutdown routes (timeout " + timeout + " " + timeUnit.toString().toLowerCase() + ")");
        } else {
            LOG.info("Starting to graceful shutdown routes (no timeout)");
        }

        // use another thread to perform the shutdowns so we can support timeout
        Future future = getExecutorService().submit(new ShutdownTask(context, consumers));
        try {
            if (timeout > 0) {
                future.get(timeout, timeUnit);
            } else {
                future.get();
            }
        } catch (TimeoutException e) {
            // timeout then cancel the task
            future.cancel(true);

            if (shutdownNowOnTimeout) {
                LOG.warn("Timeout occurred. Now forcing all routes to be shutdown now.");
                // force the consumers to shutdown now
                shutdownNow(consumers);
            } else {
                LOG.warn("Timeout occurred. Will ignore shutting down the remainder route input consumers.");
            }
        } catch (ExecutionException e) {
            // unwrap execution exception
            throw ObjectHelper.wrapRuntimeCamelException(e.getCause());
        }

        long delta = System.currentTimeMillis() - start;
        // convert to seconds as its easier to read than a big milli seconds number 
        long seconds = TimeUnit.SECONDS.convert(delta, TimeUnit.MILLISECONDS);

        LOG.info("Graceful shutdown of routes completed in " + seconds + " seconds");
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public long getTimeout() {
        return timeout;
    }

    public void setTimeUnit(TimeUnit timeUnit) {
        this.timeUnit = timeUnit;
    }

    public TimeUnit getTimeUnit() {
        return timeUnit;
    }

    public void setShutdownNowOnTimeout(boolean shutdownNowOnTimeout) {
        this.shutdownNowOnTimeout = shutdownNowOnTimeout;
    }

    public boolean isShutdownNowOnTimeout() {
        return shutdownNowOnTimeout;
    }

    /**
     * Shutdown all the consumers immediately.
     *
     * @param consumers the consumers to shutdown
     */
    protected void shutdownNow(List<Consumer> consumers) {
        for (Consumer consumer : consumers) {
            shutdownNow(consumer);
        }
    }

    /**
     * Shutdown the consumer immediately.
     *
     * @param consumer the consumer to shutdown
     */
    protected void shutdownNow(Consumer consumer) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Shutting down: " + consumer);
        }

        // allow us to do custom work before delegating to service helper
        try {
            ServiceHelper.stopService(consumer);
        } catch (Exception e) {
            LOG.warn("Error occurred while shutting down route: " + consumer + ". This exception will be ignored.");
            // fire event
            EventHelper.notifyServiceStopFailure(consumer.getEndpoint().getCamelContext(), consumer, e);
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Shutdown complete for: " + consumer);
        }
    }

    /**
     * Suspends the consumer immediately.
     *
     * @param service the suspendable consumer
     * @param consumer the consumer to suspend
     */
    protected void suspendNow(SuspendableService service, Consumer consumer) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Suspending: " + consumer);
        }

        try {
            service.suspend();
        } catch (Exception e) {
            LOG.warn("Error occurred while suspending route: " + consumer + ". This exception will be ignored.");
            // fire event
            EventHelper.notifyServiceStopFailure(consumer.getEndpoint().getCamelContext(), consumer, e);
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Suspend complete for: " + consumer);
        }
    }

    private ExecutorService getExecutorService() {
        if (executor == null) {
            executor = ExecutorServiceHelper.newSingleThreadExecutor("ShutdownTask", true);
        }
        return executor;
    }

    @Override
    protected void doStart() throws Exception {
    }

    @Override
    protected void doStop() throws Exception {
        if (executor != null) {
            executor.shutdownNow();
        }
        executor = null;
    }

    /**
     * Shutdown task which shutdown all the routes in a graceful manner.
     */
    class ShutdownTask implements Runnable {

        private final CamelContext context;
        private final List<Consumer> consumers;

        public ShutdownTask(CamelContext context, List<Consumer> consumers) {
            this.context = context;
            this.consumers = consumers;
        }

        public void run() {
            if (LOG.isDebugEnabled()) {
                LOG.debug("There are " + consumers.size() + " routes to shutdown");
            }

            // list of deferred consumers to shutdown when all exchanges has been completed routed
            // and thus there are no more inflight exchanges so they can be safely shutdown at that time
            List<Consumer> deferredConsumers = new ArrayList<Consumer>();

            for (Consumer consumer : consumers) {

                // some consumers do not support shutting down so let them decide
                // if a consumer is suspendable then prefer to use that and then shutdown later
                boolean shutdown = true;
                boolean suspend = false;
                if (consumer instanceof ShutdownAware) {
                    shutdown = ((ShutdownAware) consumer).deferShutdown();
                } else if (consumer instanceof SuspendableService) {
                    shutdown = false;
                    suspend = true;
                }

                if (suspend) {
                    // only suspend it and then later shutdown it
                    suspendNow((SuspendableService) consumer, consumer);
                    // add it to the deferred list so the route will be shutdown later
                    deferredConsumers.add(consumer);
                } else if (shutdown) {
                    shutdownNow(consumer);
                } else {
                    // we will stop it later, but for now it must run to be able to help all inflight messages
                    // be safely completed
                    deferredConsumers.add(consumer);
                }
            }

            // wait till there are no more pending inflight messages
            boolean done = false;
            while (!done) {
                int size = 0;
                for (Consumer consumer : consumers) {
                    size += context.getInflightRepository().size(consumer.getEndpoint());
                    // include any additional pending exchanges on some consumers which may have internal
                    // memory queues such as seda
                    if (consumer instanceof ShutdownAware) {
                        size += ((ShutdownAware) consumer).getPendingExchangesSize();
                    }
                }
                if (size > 0) {
                    try {
                        LOG.info("Waiting as there are still " + size + " inflight exchanges to complete before we can shutdown");
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        LOG.warn("Interrupted while waiting during graceful shutdown, will force shutdown now.");
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    done = true;
                }
            }

            // now all messages has been completed then stop the deferred consumers
            shutdownNow(deferredConsumers);
        }

    }

}
