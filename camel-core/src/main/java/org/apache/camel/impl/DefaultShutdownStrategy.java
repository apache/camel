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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Consumer;
import org.apache.camel.Route;
import org.apache.camel.Service;
import org.apache.camel.ShutdownRoute;
import org.apache.camel.ShutdownRunningTask;
import org.apache.camel.SuspendableService;
import org.apache.camel.spi.RouteStartupOrder;
import org.apache.camel.spi.ShutdownAware;
import org.apache.camel.spi.ShutdownPrepared;
import org.apache.camel.spi.ShutdownStrategy;
import org.apache.camel.support.ServiceSupport;
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
 * This strategy will perform graceful shutdown in two steps:
 * <ul>
 *     <li>Graceful - By suspending/stopping consumers, and let any in-flight exchanges complete</li>
 *     <li>Forced - After a given period of time, a timeout occurred and if there are still pending
 *     exchanges to complete, then a more aggressive forced strategy is performed.</li>
 * </ul>
 * The idea by the <tt>graceful</tt> shutdown strategy, is to stop taking in more new messages,
 * and allow any existing inflight messages to complete. Then when there is no more inflight messages
 * then the routes can be fully shutdown. This mean that if there is inflight messages then we will have
 * to wait for these messages to complete. If they do not complete after a period of time, then a
 * timeout triggers. And then a more aggressive strategy takes over.
 * <p/>
 * The idea by the <tt>forced</tt> shutdown strategy, is to stop continue processing messages.
 * And force routes and its services to shutdown now. There is a risk when shutting down now,
 * that some resources is not properly shutdown, which can cause side effects. The timeout value
 * is by default 300 seconds, but can be customized. 
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
 * <p/>
 * After route consumers have been shutdown, then any {@link ShutdownPrepared} services on the routes
 * is being prepared for shutdown, by invoking {@link ShutdownPrepared#prepareShutdown(boolean)} which
 * <tt>force=false</tt>.
 * <p/>
 * Then if a timeout occurred and the strategy has been configured with shutdown-now on timeout, then
 * the strategy performs a more aggressive forced shutdown, by forcing all consumers to shutdown
 * and then invokes {@link ShutdownPrepared#prepareShutdown(boolean)} with <tt>force=true</tt>
 * on the services. This allows the services to know they should force shutdown now.
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
    private volatile boolean forceShutdown;

    public DefaultShutdownStrategy() {
    }

    public DefaultShutdownStrategy(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public void shutdown(CamelContext context, List<RouteStartupOrder> routes) throws Exception {
        shutdown(context, routes, getTimeout(), getTimeUnit());
    }

    @Override
    public void shutdownForced(CamelContext context, List<RouteStartupOrder> routes) throws Exception {
        doShutdown(context, routes, getTimeout(), getTimeUnit(), false, false, true);
    }

    public void suspend(CamelContext context, List<RouteStartupOrder> routes) throws Exception {
        doShutdown(context, routes, getTimeout(), getTimeUnit(), true, false, false);
    }

    public void shutdown(CamelContext context, List<RouteStartupOrder> routes, long timeout, TimeUnit timeUnit) throws Exception {
        doShutdown(context, routes, timeout, timeUnit, false, false, false);
    }

    public boolean shutdown(CamelContext context, RouteStartupOrder route, long timeout, TimeUnit timeUnit, boolean abortAfterTimeout) throws Exception {
        List<RouteStartupOrder> routes = new ArrayList<RouteStartupOrder>(1);
        routes.add(route);
        return doShutdown(context, routes, timeout, timeUnit, false, abortAfterTimeout, false);
    }

    public void suspend(CamelContext context, List<RouteStartupOrder> routes, long timeout, TimeUnit timeUnit) throws Exception {
        doShutdown(context, routes, timeout, timeUnit, true, false, false);
    }

    protected boolean doShutdown(CamelContext context, List<RouteStartupOrder> routes, long timeout, TimeUnit timeUnit,
                                 boolean suspendOnly, boolean abortAfterTimeout, boolean forceShutdown) throws Exception {

        // just return if no routes to shutdown
        if (routes.isEmpty()) {
            return true;
        }

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

        LOG.info("Starting to graceful shutdown " + routesOrdered.size() + " routes (timeout " + timeout + " " + timeUnit.toString().toLowerCase(Locale.ENGLISH) + ")");

        // use another thread to perform the shutdowns so we can support timeout
        final AtomicBoolean timeoutOccurred = new AtomicBoolean();
        Future<?> future = getExecutorService().submit(new ShutdownTask(context, routesOrdered, timeout, timeUnit, suspendOnly, abortAfterTimeout, timeoutOccurred));
        try {
            future.get(timeout, timeUnit);
        } catch (TimeoutException e) {
            // we hit a timeout, so set the flag
            timeoutOccurred.set(true);

            // timeout then cancel the task
            future.cancel(true);

            // signal we are forcing shutdown now, since timeout occurred
            this.forceShutdown = forceShutdown;

            // if set, stop processing and return false to indicate that the shutdown is aborting
            if (!forceShutdown && abortAfterTimeout) {
                LOG.warn("Timeout occurred. Aborting the shutdown now.");
                return false;
            } else {
                if (forceShutdown || shutdownNowOnTimeout) {
                    LOG.warn("Timeout occurred. Now forcing the routes to be shutdown now.");
                    // force the routes to shutdown now
                    shutdownRoutesNow(routesOrdered);

                    // now the route consumers has been shutdown, then prepare route services for shutdown now (forced)
                    for (RouteStartupOrder order : routes) {
                        for (Service service : order.getServices()) {
                            prepareShutdown(service, true, true);
                        }
                    }
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

    @Override
    public boolean forceShutdown(Service service) {
        return forceShutdown;
    }

    public void setTimeout(long timeout) {
        if (timeout <= 0) {
            throw new IllegalArgumentException("Timeout must be a positive value");
        }
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
    protected static void shutdownNow(Consumer consumer) {
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
    protected static void suspendNow(Consumer consumer) {
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
            executor = camelContext.getExecutorServiceManager().newSingleThreadExecutor(this, "ShutdownTask");
        }
        return executor;
    }

    @Override
    protected void doStart() throws Exception {
        ObjectHelper.notNull(camelContext, "CamelContext");
        // reset option
        forceShutdown = false;
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }

    @Override
    protected void doShutdown() throws Exception {
        if (executor != null) {
            camelContext.getExecutorServiceManager().shutdownNow(executor);
            // should clear executor so we can restart by creating a new thread pool
            executor = null;
        }
    }

    /**
     * Prepares the services for shutdown, by invoking the {@link ShutdownPrepared#prepareShutdown(boolean)} method
     * on the service if it implement this interface.
     * 
     * @param service the service
     * @param forced  whether to force shutdown
     * @param includeChildren whether to prepare the child of the service as well
     */
    private static void prepareShutdown(Service service, boolean forced, boolean includeChildren) {
        Set<Service> list;
        if (includeChildren) {
            list = ServiceHelper.getChildServices(service);
        } else {
            list = new LinkedHashSet<Service>(1);
            list.add(service);
        }

        for (Service child : list) {
            if (child instanceof ShutdownPrepared) {
                try {
                    LOG.trace("Preparing {} shutdown on {}", forced ? "forced" : "", child);
                    ((ShutdownPrepared) child).prepareShutdown(forced);
                } catch (Exception e) {
                    LOG.warn("Error during prepare shutdown on " + child + ". This exception will be ignored.", e);
                }
            }
        }
    }

    /**
     * Holder for deferred consumers
     */
    static class ShutdownDeferredConsumer {
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
    static class ShutdownTask implements Runnable {

        private final CamelContext context;
        private final List<RouteStartupOrder> routes;
        private final boolean suspendOnly;
        private final boolean abortAfterTimeout;
        private final long timeout;
        private final TimeUnit timeUnit;
        private final AtomicBoolean timeoutOccurred;

        public ShutdownTask(CamelContext context, List<RouteStartupOrder> routes, long timeout, TimeUnit timeUnit,
                            boolean suspendOnly, boolean abortAfterTimeout, AtomicBoolean timeoutOccurred) {
            this.context = context;
            this.routes = routes;
            this.suspendOnly = suspendOnly;
            this.abortAfterTimeout = abortAfterTimeout;
            this.timeout = timeout;
            this.timeUnit = timeUnit;
            this.timeoutOccurred = timeoutOccurred;
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
            while (!done && !timeoutOccurred.get()) {
                int size = 0;
                for (RouteStartupOrder order : routes) {
                    int inflight = context.getInflightRepository().size(order.getRoute().getId());
                    for (Consumer consumer : order.getInputs()) {
                        // include any additional pending exchanges on some consumers which may have internal
                        // memory queues such as seda
                        if (consumer instanceof ShutdownAware) {
                            inflight += ((ShutdownAware) consumer).getPendingExchangesSize();
                        }
                    }
                    if (inflight > 0) {
                        size += inflight;
                        LOG.trace("{} inflight and pending exchanges for route: {}", inflight, order.getRoute().getId());
                    }
                }
                if (size > 0) {
                    try {
                        LOG.info("Waiting as there are still " + size + " inflight and pending exchanges to complete, timeout in "
                                 + (TimeUnit.SECONDS.convert(timeout, timeUnit) - (loopCount++ * loopDelaySeconds)) + " seconds.");
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
                    boolean forced = context.getShutdownStrategy().forceShutdown(consumer);
                    prepareShutdown(consumer, forced, false);
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

            // now the route consumers has been shutdown, then prepare route services for shutdown
            for (RouteStartupOrder order : routes) {
                for (Service service : order.getServices()) {
                    boolean forced = context.getShutdownStrategy().forceShutdown(service);
                    prepareShutdown(service, forced, true);
                }
            }
        }

    }

}
