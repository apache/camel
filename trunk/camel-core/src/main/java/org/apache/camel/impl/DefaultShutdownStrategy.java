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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Consumer;
import org.apache.camel.Route;
import org.apache.camel.ShutdownRoute;
import org.apache.camel.ShutdownRunningTask;
import org.apache.camel.SuspendableService;
import org.apache.camel.spi.RouteStartupOrder;
import org.apache.camel.spi.ShutdownAware;
import org.apache.camel.spi.ShutdownStrategy;
import org.apache.camel.util.EventHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;
import org.apache.camel.util.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * Will by default use a timeout of 300 seconds (5 minutes) by which it will shutdown now the remaining consumers.
 * This ensures that when shutting down Camel it at some point eventually will shutdown.
 * This behavior can of course be configured using the {@link #setTimeout(long)} and
 * {@link #setShutdownNowOnTimeout(boolean)} methods.
 * <p/>
 * Routes will by default be shutdown in the reverse order of which they where started.
 * You can customize this using the {@link #setShutdownRoutesInReverseOrder(boolean)} method.
 *
 * @version 
 */
public class DefaultShutdownStrategy extends ServiceSupport implements ShutdownStrategy, CamelContextAware {
    private static final transient Logger LOG = LoggerFactory.getLogger(DefaultShutdownStrategy.class);

    private CamelContext camelContext;
    private ExecutorService executor;
    private long timeout = 5 * 60;
    private TimeUnit timeUnit = TimeUnit.SECONDS;
    private boolean shutdownNowOnTimeout = true;
    private boolean shutdownRoutesInReverseOrder = true;

    public DefaultShutdownStrategy() {
    }

    public DefaultShutdownStrategy(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public void shutdown(CamelContext context, List<RouteStartupOrder> routes) throws Exception {
        shutdown(context, routes, getTimeout(), getTimeUnit());
    }

    public void suspend(CamelContext context, List<RouteStartupOrder> routes) throws Exception {
        doShutdown(context, routes, getTimeout(), getTimeUnit(), true, false);
    }

    public void shutdown(CamelContext context, List<RouteStartupOrder> routes, long timeout, TimeUnit timeUnit) throws Exception {
        doShutdown(context, routes, timeout, timeUnit, false, false);
    }

    public boolean shutdown(CamelContext context, RouteStartupOrder route, long timeout, TimeUnit timeUnit, boolean abortAfterTimeout) throws Exception {
        List<RouteStartupOrder> routes = new ArrayList<RouteStartupOrder>(1);
        routes.add(route);
        return doShutdown(context, routes, timeout, timeUnit, false, abortAfterTimeout);
    }

    public void suspend(CamelContext context, List<RouteStartupOrder> routes, long timeout, TimeUnit timeUnit) throws Exception {
        doShutdown(context, routes, timeout, timeUnit, true, false);
    }

    protected boolean doShutdown(CamelContext context, List<RouteStartupOrder> routes, long timeout, TimeUnit timeUnit, boolean suspendOnly, boolean abortAfterTimeout) throws Exception {
        StopWatch watch = new StopWatch();

        // at first sort according to route startup order
        List<RouteStartupOrder> routesOrdered = new ArrayList<RouteStartupOrder>(routes);
        Collections.sort(routesOrdered, new Comparator<RouteStartupOrder>() {
            public int compare(RouteStartupOrder o1, RouteStartupOrder o2) {
                return o1.getStartupOrder() - o2.getStartupOrder();
            }
        });
        if (shutdownRoutesInReverseOrder) {
            Collections.reverse(routesOrdered);
        }

        if (timeout > 0) {
            LOG.info("Starting to graceful shutdown " + routesOrdered.size() + " routes (timeout " + timeout + " " + timeUnit.toString().toLowerCase() + ")");
        } else {
            LOG.info("Starting to graceful shutdown " + routesOrdered.size() + " routes (no timeout)");
        }

        // use another thread to perform the shutdowns so we can support timeout
        Future future = getExecutorService().submit(new ShutdownTask(context, routesOrdered, suspendOnly, abortAfterTimeout));
        try {
            if (timeout > 0) {
                future.get(timeout, timeUnit);
            } else {
                future.get();
            }
        } catch (TimeoutException e) {
            // timeout then cancel the task
            future.cancel(true);

            // if set, stop processing and return false to indicate that the shutdown is aborting
            if (abortAfterTimeout) {
                LOG.warn("Timeout occurred. Aborting the shutdown now.");
                return false;
            } else {
                if (shutdownNowOnTimeout) {
                    LOG.warn("Timeout occurred. Now forcing the routes to be shutdown now.");
                    // force the routes to shutdown now
                    shutdownRoutesNow(routesOrdered);
                } else {
                    LOG.warn("Timeout occurred. Will ignore shutting down the remainder routes.");
                }
            }
        } catch (ExecutionException e) {
            // unwrap execution exception
            throw ObjectHelper.wrapRuntimeCamelException(e.getCause());
        }

        // convert to seconds as its easier to read than a big milli seconds number
        long seconds = TimeUnit.SECONDS.convert(watch.stop(), TimeUnit.MILLISECONDS);

        LOG.info("Graceful shutdown of " + routesOrdered.size() + " routes completed in " + seconds + " seconds");
        return true;
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

    public boolean isShutdownRoutesInReverseOrder() {
        return shutdownRoutesInReverseOrder;
    }

    public void setShutdownRoutesInReverseOrder(boolean shutdownRoutesInReverseOrder) {
        this.shutdownRoutesInReverseOrder = shutdownRoutesInReverseOrder;
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    /**
     * Shutdown all the consumers immediately.
     *
     * @param routes the routes to shutdown
     */
    protected void shutdownRoutesNow(List<RouteStartupOrder> routes) {
        for (RouteStartupOrder order : routes) {

            // set the route to shutdown as fast as possible by stopping after
            // it has completed its current task
            ShutdownRunningTask current = order.getRoute().getRouteContext().getShutdownRunningTask();
            if (current != ShutdownRunningTask.CompleteCurrentTaskOnly) {
                LOG.debug("Changing shutdownRunningTask from {} to " +  ShutdownRunningTask.CompleteCurrentTaskOnly
                    + " on route {} to shutdown faster", current, order.getRoute().getId());
                order.getRoute().getRouteContext().setShutdownRunningTask(ShutdownRunningTask.CompleteCurrentTaskOnly);
            }

            for (Consumer consumer : order.getInputs()) {
                shutdownNow(consumer);
            }
        }
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
        LOG.trace("Shutting down: {}", consumer);

        // allow us to do custom work before delegating to service helper
        try {
            ServiceHelper.stopService(consumer);
        } catch (Throwable e) {
            LOG.warn("Error occurred while shutting down route: " + consumer + ". This exception will be ignored.", e);
            // fire event
            EventHelper.notifyServiceStopFailure(consumer.getEndpoint().getCamelContext(), consumer, e);
        }

        LOG.trace("Shutdown complete for: {}", consumer);
    }

    /**
     * Suspends/stops the consumer immediately.
     *
     * @param consumer the consumer to suspend
     */
    protected void suspendNow(Consumer consumer) {
        LOG.trace("Suspending: {}", consumer);

        // allow us to do custom work before delegating to service helper
        try {
            ServiceHelper.suspendService(consumer);
        } catch (Throwable e) {
            LOG.warn("Error occurred while suspending route: " + consumer + ". This exception will be ignored.", e);
            // fire event
            EventHelper.notifyServiceStopFailure(consumer.getEndpoint().getCamelContext(), consumer, e);
        }

        LOG.trace("Suspend complete for: {}", consumer);
    }

    private ExecutorService getExecutorService() {
        if (executor == null) {
            executor = camelContext.getExecutorServiceStrategy().newSingleThreadExecutor(this, "ShutdownTask");
        }
        return executor;
    }

    @Override
    protected void doStart() throws Exception {
        ObjectHelper.notNull(camelContext, "CamelContext");
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }

    @Override
    protected void doShutdown() throws Exception {
        if (executor != null) {
            camelContext.getExecutorServiceStrategy().shutdownNow(executor);
            // should clear executor so we can restart by creating a new thread pool
            executor = null;
        }
    }

    class ShutdownDeferredConsumer {
        private final Route route;
        private final Consumer consumer;

        ShutdownDeferredConsumer(Route route, Consumer consumer) {
            this.route = route;
            this.consumer = consumer;
        }

        Route getRoute() {
            return route;
        }

        Consumer getConsumer() {
            return consumer;
        }
    }

    /**
     * Shutdown task which shutdown all the routes in a graceful manner.
     */
    class ShutdownTask implements Runnable {

        private final CamelContext context;
        private final List<RouteStartupOrder> routes;
        private final boolean suspendOnly;
        private final boolean abortAfterTimeout;

        public ShutdownTask(CamelContext context, List<RouteStartupOrder> routes, boolean suspendOnly, boolean abortAfterTimeout) {
            this.context = context;
            this.routes = routes;
            this.suspendOnly = suspendOnly;
            this.abortAfterTimeout = abortAfterTimeout;
        }

        public void run() {
            // the strategy in this run method is to
            // 1) go over the routes and shutdown those routes which can be shutdown asap
            //    some routes will be deferred to shutdown at the end, as they are needed
            //    by other routes so they can complete their tasks
            // 2) wait until all inflight and pending exchanges has been completed
            // 3) shutdown the deferred routes

            LOG.debug("There are {} routes to {}", routes.size(), suspendOnly ? "suspend" : "shutdown");

            // list of deferred consumers to shutdown when all exchanges has been completed routed
            // and thus there are no more inflight exchanges so they can be safely shutdown at that time
            List<ShutdownDeferredConsumer> deferredConsumers = new ArrayList<ShutdownDeferredConsumer>();

            for (RouteStartupOrder order : routes) {

                ShutdownRoute shutdownRoute = order.getRoute().getRouteContext().getShutdownRoute();
                ShutdownRunningTask shutdownRunningTask = order.getRoute().getRouteContext().getShutdownRunningTask();

                if (LOG.isTraceEnabled()) {
                    LOG.trace("{}{} with options [{},{}]",
                            new Object[]{suspendOnly ? "Suspending route: " : "Shutting down route: ",
                                order.getRoute().getId(), shutdownRoute, shutdownRunningTask});
                }

                for (Consumer consumer : order.getInputs()) {

                    boolean suspend = false;

                    // assume we should shutdown if we are not deferred
                    boolean shutdown = shutdownRoute != ShutdownRoute.Defer;

                    if (shutdown) {
                        // if we are to shutdown then check whether we can suspend instead as its a more
                        // gentle way to graceful shutdown

                        // some consumers do not support shutting down so let them decide
                        // if a consumer is suspendable then prefer to use that and then shutdown later
                        if (consumer instanceof ShutdownAware) {
                            shutdown = !((ShutdownAware) consumer).deferShutdown(shutdownRunningTask);
                        }
                        if (shutdown && consumer instanceof SuspendableService) {
                            // we prefer to suspend over shutdown
                            suspend = true;
                        }
                    }

                    // log at info level when a route has been shutdown (otherwise log at debug level to not be too noisy)
                    if (suspend) {
                        // only suspend it and then later shutdown it
                        suspendNow(consumer);
                        // add it to the deferred list so the route will be shutdown later
                        deferredConsumers.add(new ShutdownDeferredConsumer(order.getRoute(), consumer));
                        LOG.debug("Route: {} suspended and shutdown deferred, was consuming from: {}", order.getRoute().getId(), order.getRoute().getEndpoint());
                    } else if (shutdown) {
                        shutdownNow(consumer);
                        LOG.info("Route: {} shutdown complete, was consuming from: {}", order.getRoute().getId(), order.getRoute().getEndpoint());
                    } else {
                        // we will stop it later, but for now it must run to be able to help all inflight messages
                        // be safely completed
                        deferredConsumers.add(new ShutdownDeferredConsumer(order.getRoute(), consumer));
                        LOG.debug("Route: " + order.getRoute().getId() + (suspendOnly ? " shutdown deferred." : " suspension deferred."));
                    }
                }
            }

            // wait till there are no more pending and inflight messages
            boolean done = false;
            long loopDelaySeconds = 1;
            long loopCount = 0;
            while (!done) {
                int size = 0;
                for (RouteStartupOrder order : routes) {
                    for (Consumer consumer : order.getInputs()) {
                        int inflight = context.getInflightRepository().size(consumer.getEndpoint());
                        // include any additional pending exchanges on some consumers which may have internal
                        // memory queues such as seda
                        if (consumer instanceof ShutdownAware) {
                            inflight += ((ShutdownAware) consumer).getPendingExchangesSize();
                        }
                        if (inflight > 0) {
                            size += inflight;
                            LOG.trace("{} inflight and pending exchanges for consumer: {}", inflight, consumer);
                        }
                    }
                }
                if (size > 0) {
                    try {
                        LOG.info("Waiting as there are still " + size + " inflight and pending exchanges to complete, timeout in "
                                 + (TimeUnit.SECONDS.convert(getTimeout(), getTimeUnit()) - (loopCount++ * loopDelaySeconds)) + " seconds.");
                        Thread.sleep(loopDelaySeconds * 1000);
                    } catch (InterruptedException e) {
                        if (abortAfterTimeout) {
                            LOG.warn("Interrupted while waiting during graceful shutdown, will abort.");
                            return;
                        } else {
                            LOG.warn("Interrupted while waiting during graceful shutdown, will force shutdown now.");
                            break;
                        }
                    }
                } else {
                    done = true;
                }
            }

            // prepare for shutdown
            for (ShutdownDeferredConsumer deferred : deferredConsumers) {
                Consumer consumer = deferred.getConsumer();
                if (consumer instanceof ShutdownAware) {
                    LOG.trace("Route: {} preparing to shutdown.", deferred.getRoute().getId());
                    ((ShutdownAware) consumer).prepareShutdown();
                    LOG.debug("Route: {} preparing to shutdown complete.", deferred.getRoute().getId());
                }
            }

            // now all messages has been completed then stop the deferred consumers
            for (ShutdownDeferredConsumer deferred : deferredConsumers) {
                Consumer consumer = deferred.getConsumer();
                if (suspendOnly) {
                    suspendNow(consumer);
                    LOG.info("Route: {} suspend complete, was consuming from: {}", deferred.getRoute().getId(), deferred.getConsumer().getEndpoint());
                } else {
                    shutdownNow(consumer);
                    LOG.info("Route: {} shutdown complete, was consuming from: {}", deferred.getRoute().getId(), deferred.getConsumer().getEndpoint());
                }
            }
        }

    }

}
