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
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Consumer;
import org.apache.camel.Route;
import org.apache.camel.Service;
import org.apache.camel.ShutdownRoute;
import org.apache.camel.ShutdownRunningTask;
import org.apache.camel.Suspendable;
import org.apache.camel.spi.InflightRepository;
import org.apache.camel.spi.RouteStartupOrder;
import org.apache.camel.spi.ShutdownAware;
import org.apache.camel.spi.ShutdownPrepared;
import org.apache.camel.spi.ShutdownStrategy;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.CollectionStringBuffer;
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
 * is being prepared for shutdown, by invoking {@link ShutdownPrepared#prepareShutdown(boolean,boolean)} which
 * <tt>force=false</tt>.
 * <p/>
 * Then if a timeout occurred and the strategy has been configured with shutdown-now on timeout, then
 * the strategy performs a more aggressive forced shutdown, by forcing all consumers to shutdown
 * and then invokes {@link ShutdownPrepared#prepareShutdown(boolean,boolean)} with <tt>force=true</tt>
 * on the services. This allows the services to know they should force shutdown now.
 * <p/>
 * When timeout occurred and a forced shutdown is happening, then there may be threads/tasks which are
 * still inflight which may be rejected continued being routed. By default this can cause WARN and ERRORs
 * to be logged. The option {@link #setSuppressLoggingOnTimeout(boolean)} can be used to suppress these
 * logs, so they are logged at TRACE level instead.
 * <p/>
 * Also when a timeout occurred then information about the inflight exchanges is logged, if {@link #isLogInflightExchangesOnTimeout()}
 * is enabled (is by default). This allows end users to known where these inflight exchanges currently are in the route(s),
 * and how long time they have been inflight.
 * <p/>
 * This information can also be obtained from the {@link org.apache.camel.spi.InflightRepository}
 * at all time during runtime.
 *
 * @version
 */
public class DefaultShutdownStrategy extends ServiceSupport implements ShutdownStrategy, CamelContextAware {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultShutdownStrategy.class);

    private CamelContext camelContext;
    private ExecutorService executor;
    private long timeout = 5 * 60;
    private TimeUnit timeUnit = TimeUnit.SECONDS;
    private boolean shutdownNowOnTimeout = true;
    private boolean shutdownRoutesInReverseOrder = true;
    private boolean suppressLoggingOnTimeout;
    private boolean logInflightExchangesOnTimeout = true;

    private volatile boolean forceShutdown;
    private final AtomicBoolean timeoutOccurred = new AtomicBoolean();
    private volatile Future<?> currentShutdownTaskFuture;

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

        // timeout must be a positive value
        if (timeout <= 0) {
            throw new IllegalArgumentException("Timeout must be a positive value");
        }

        // just return if no routes to shutdown
        if (routes.isEmpty()) {
            return true;
        }

        StopWatch watch = new StopWatch();

        // at first sort according to route startup order
        List<RouteStartupOrder> routesOrdered = new ArrayList<RouteStartupOrder>(routes);
        routesOrdered.sort(new Comparator<RouteStartupOrder>() {
            public int compare(RouteStartupOrder o1, RouteStartupOrder o2) {
                return o1.getStartupOrder() - o2.getStartupOrder();
            }
        });
        if (shutdownRoutesInReverseOrder) {
            Collections.reverse(routesOrdered);
        }

        if (suspendOnly) {
            LOG.info("Starting to graceful suspend {} routes (timeout {} {})", routesOrdered.size(), timeout, timeUnit.toString().toLowerCase(Locale.ENGLISH));
        } else {
            LOG.info("Starting to graceful shutdown {} routes (timeout {} {})", routesOrdered.size(), timeout, timeUnit.toString().toLowerCase(Locale.ENGLISH));
        }

        // use another thread to perform the shutdowns so we can support timeout
        timeoutOccurred.set(false);
        currentShutdownTaskFuture = getExecutorService().submit(new ShutdownTask(context, routesOrdered, timeout, timeUnit, suspendOnly,
            abortAfterTimeout, timeoutOccurred, isLogInflightExchangesOnTimeout()));
        try {
            currentShutdownTaskFuture.get(timeout, timeUnit);
        } catch (ExecutionException e) {
            // unwrap execution exception
            throw ObjectHelper.wrapRuntimeCamelException(e.getCause());
        } catch (Exception e) {
            // either timeout or interrupted exception was thrown so this is okay
            // as interrupted would mean cancel was called on the currentShutdownTaskFuture to signal a forced timeout

            // we hit a timeout, so set the flag
            timeoutOccurred.set(true);

            // timeout then cancel the task
            currentShutdownTaskFuture.cancel(true);

            // signal we are forcing shutdown now, since timeout occurred
            this.forceShutdown = forceShutdown;

            // if set, stop processing and return false to indicate that the shutdown is aborting
            if (!forceShutdown && abortAfterTimeout) {
                LOG.warn("Timeout occurred during graceful shutdown. Aborting the shutdown now."
                        + " Notice: some resources may still be running as graceful shutdown did not complete successfully.");

                // we attempt to force shutdown so lets log the current inflight exchanges which are affected
                logInflightExchanges(context, routes, isLogInflightExchangesOnTimeout());

                return false;
            } else {
                if (forceShutdown || shutdownNowOnTimeout) {
                    LOG.warn("Timeout occurred during graceful shutdown. Forcing the routes to be shutdown now."
                            + " Notice: some resources may still be running as graceful shutdown did not complete successfully.");

                    // we attempt to force shutdown so lets log the current inflight exchanges which are affected
                    logInflightExchanges(context, routes, isLogInflightExchangesOnTimeout());

                    // force the routes to shutdown now
                    shutdownRoutesNow(routesOrdered);

                    // now the route consumers has been shutdown, then prepare route services for shutdown now (forced)
                    for (RouteStartupOrder order : routes) {
                        for (Service service : order.getServices()) {
                            prepareShutdown(service, false, true, true, isSuppressLoggingOnTimeout());
                        }
                    }
                } else {
                    LOG.warn("Timeout occurred during graceful shutdown. Will ignore shutting down the remainder routes."
                            + " Notice: some resources may still be running as graceful shutdown did not complete successfully.");

                    logInflightExchanges(context, routes, isLogInflightExchangesOnTimeout());
                }
            }
        } finally {
            currentShutdownTaskFuture = null;
        }

        // convert to seconds as its easier to read than a big milli seconds number
        long seconds = TimeUnit.SECONDS.convert(watch.taken(), TimeUnit.MILLISECONDS);

        LOG.info("Graceful shutdown of {} routes completed in {} seconds", routesOrdered.size(), seconds);
        return true;
    }

    @Override
    public boolean forceShutdown(Service service) {
        return forceShutdown;
    }

    @Override
    public boolean hasTimeoutOccurred() {
        return timeoutOccurred.get();
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

    public boolean isSuppressLoggingOnTimeout() {
        return suppressLoggingOnTimeout;
    }

    public void setSuppressLoggingOnTimeout(boolean suppressLoggingOnTimeout) {
        this.suppressLoggingOnTimeout = suppressLoggingOnTimeout;
    }

    public boolean isLogInflightExchangesOnTimeout() {
        return logInflightExchangesOnTimeout;
    }

    public void setLogInflightExchangesOnTimeout(boolean logInflightExchangesOnTimeout) {
        this.logInflightExchangesOnTimeout = logInflightExchangesOnTimeout;
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public Future<?> getCurrentShutdownTaskFuture() {
        return currentShutdownTaskFuture;
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
            // use a thread pool that allow to terminate idle threads so they do not hang around forever
            executor = camelContext.getExecutorServiceManager().newThreadPool(this, "ShutdownTask", 0, 1);
        }
        return executor;
    }

    @Override
    protected void doStart() throws Exception {
        ObjectHelper.notNull(camelContext, "CamelContext");
        // reset option
        forceShutdown = false;
        timeoutOccurred.set(false);
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }

    @Override
    protected void doShutdown() throws Exception {
        if (executor != null) {
            // force shutting down as we are shutting down Camel
            camelContext.getExecutorServiceManager().shutdownNow(executor);
            // should clear executor so we can restart by creating a new thread pool
            executor = null;
        }
    }

    /**
     * Prepares the services for shutdown, by invoking the {@link ShutdownPrepared#prepareShutdown(boolean, boolean)} method
     * on the service if it implement this interface.
     *
     * @param service the service
     * @param forced  whether to force shutdown
     * @param includeChildren whether to prepare the child of the service as well
     */
    private static void prepareShutdown(Service service, boolean suspendOnly, boolean forced, boolean includeChildren, boolean suppressLogging) {
        Set<Service> list;
        if (includeChildren) {
            // include error handlers as we want to prepare them for shutdown as well
            list = ServiceHelper.getChildServices(service, true);
        } else {
            list = new LinkedHashSet<Service>(1);
            list.add(service);
        }

        for (Service child : list) {
            if (child instanceof ShutdownPrepared) {
                try {
                    LOG.trace("Preparing {} shutdown on {}", forced ? "forced" : "", child);
                    ((ShutdownPrepared) child).prepareShutdown(suspendOnly, forced);
                } catch (Exception e) {
                    if (suppressLogging) {
                        LOG.trace("Error during prepare shutdown on " + child + ". This exception will be ignored.", e);
                    } else {
                        LOG.warn("Error during prepare shutdown on " + child + ". This exception will be ignored.", e);
                    }
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
        private final boolean logInflightExchangesOnTimeout;

        ShutdownTask(CamelContext context, List<RouteStartupOrder> routes, long timeout, TimeUnit timeUnit,
                            boolean suspendOnly, boolean abortAfterTimeout, AtomicBoolean timeoutOccurred, boolean logInflightExchangesOnTimeout) {
            this.context = context;
            this.routes = routes;
            this.suspendOnly = suspendOnly;
            this.abortAfterTimeout = abortAfterTimeout;
            this.timeout = timeout;
            this.timeUnit = timeUnit;
            this.timeoutOccurred = timeoutOccurred;
            this.logInflightExchangesOnTimeout = logInflightExchangesOnTimeout;
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
                        if (shutdown && consumer instanceof Suspendable) {
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

            // notify the services we intend to shutdown
            for (RouteStartupOrder order : routes) {
                for (Service service : order.getServices()) {
                    // skip the consumer as we handle that specially
                    if (service instanceof Consumer) {
                        continue;
                    }
                    prepareShutdown(service, suspendOnly, false, true, false);
                }
            }

            // wait till there are no more pending and inflight messages
            boolean done = false;
            long loopDelaySeconds = 1;
            long loopCount = 0;
            while (!done && !timeoutOccurred.get()) {
                int size = 0;
                // number of inflights per route
                final Map<String, Integer> routeInflight = new LinkedHashMap<String, Integer>();

                for (RouteStartupOrder order : routes) {
                    int inflight = context.getInflightRepository().size(order.getRoute().getId());
                    inflight += getPendingInflightExchanges(order);
                    if (inflight > 0) {
                        String routeId = order.getRoute().getId();
                        routeInflight.put(routeId, inflight);
                        size += inflight;
                        LOG.trace("{} inflight and pending exchanges for route: {}", inflight, routeId);
                    }
                }
                if (size > 0) {
                    try {
                        // build a message with inflight per route
                        CollectionStringBuffer csb = new CollectionStringBuffer();
                        for (Map.Entry<String, Integer> entry : routeInflight.entrySet()) {
                            String row = String.format("%s = %s", entry.getKey(), entry.getValue());
                            csb.append(row);
                        }

                        String msg = "Waiting as there are still " + size + " inflight and pending exchanges to complete, timeout in "
                                + (TimeUnit.SECONDS.convert(timeout, timeUnit) - (loopCount++ * loopDelaySeconds)) + " seconds.";
                        msg += " Inflights per route: [" + csb.toString() + "]";

                        LOG.info(msg);

                        // log verbose if DEBUG logging is enabled
                        logInflightExchanges(context, routes, logInflightExchangesOnTimeout);

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
                    boolean suppress = context.getShutdownStrategy().isSuppressLoggingOnTimeout();
                    prepareShutdown(consumer, suspendOnly, forced, false, suppress);
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
                    boolean suppress = context.getShutdownStrategy().isSuppressLoggingOnTimeout();
                    prepareShutdown(service, suspendOnly, forced, true, suppress);
                }
            }
        }

    }

    /**
     * Calculates the total number of inflight exchanges for the given route
     *
     * @param order the route
     * @return number of inflight exchanges
     */
    protected static int getPendingInflightExchanges(RouteStartupOrder order) {
        int inflight = 0;

        // the consumer is the 1st service so we always get the consumer
        // the child services are EIPs in the routes which may also have pending
        // inflight exchanges (such as the aggregator)
        for (Service service : order.getServices()) {
            Set<Service> children = ServiceHelper.getChildServices(service);
            for (Service child : children) {
                if (child instanceof ShutdownAware) {
                    inflight += ((ShutdownAware) child).getPendingExchangesSize();
                }
            }
        }

        return inflight;
    }

    /**
     * Logs information about the inflight exchanges
     *
     * @param infoLevel <tt>true</tt> to log at INFO level, <tt>false</tt> to log at DEBUG level
     */
    protected static void logInflightExchanges(CamelContext camelContext, List<RouteStartupOrder> routes, boolean infoLevel) {
        // check if we need to log
        if (!infoLevel && !LOG.isDebugEnabled()) {
            return;
        }

        Collection<InflightRepository.InflightExchange> inflights = camelContext.getInflightRepository().browse();
        int size = inflights.size();
        if (size == 0) {
            return;
        }

        // filter so inflight must start from any of the routes
        Set<String> routeIds = new HashSet<String>();
        for (RouteStartupOrder route : routes) {
            routeIds.add(route.getRoute().getId());
        }
        Collection<InflightRepository.InflightExchange> filtered = new ArrayList<InflightRepository.InflightExchange>();
        for (InflightRepository.InflightExchange inflight : inflights) {
            String routeId = inflight.getExchange().getFromRouteId();
            if (routeIds.contains(routeId)) {
                filtered.add(inflight);
            }
        }

        size = filtered.size();
        if (size == 0) {
            return;
        }

        StringBuilder sb = new StringBuilder("There are " + size + " inflight exchanges:");
        for (InflightRepository.InflightExchange inflight : filtered) {
            sb.append("\n\tInflightExchange: [exchangeId=").append(inflight.getExchange().getExchangeId())
                    .append(", fromRouteId=").append(inflight.getExchange().getFromRouteId())
                    .append(", routeId=").append(inflight.getRouteId())
                    .append(", nodeId=").append(inflight.getNodeId())
                    .append(", elapsed=").append(inflight.getElapsed())
                    .append(", duration=").append(inflight.getDuration())
                    .append("]");
        }

        if (infoLevel) {
            LOG.info(sb.toString());
        } else {
            LOG.debug(sb.toString());
        }
    }

}
