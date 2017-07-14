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

import java.util.EventObject;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Route;
import org.apache.camel.management.event.ExchangeCompletedEvent;
import org.apache.camel.support.EventNotifierSupport;
import org.apache.camel.support.RoutePolicySupport;
import org.apache.camel.util.CamelLogger;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;
import org.slf4j.LoggerFactory;

/**
 * A throttle based {@link org.apache.camel.spi.RoutePolicy} which is capable of dynamic
 * throttling a route based on number of current inflight exchanges.
 * <p/>
 * This implementation supports two scopes {@link ThrottlingScope#Context} and {@link ThrottlingScope#Route} (is default).
 * If context scope is selected then this implementation will use a {@link org.apache.camel.spi.EventNotifier} to listen
 * for events when {@link Exchange}s is done, and trigger the {@link #throttle(org.apache.camel.Route, org.apache.camel.Exchange)}
 * method. If the route scope is selected then <b>no</b> {@link org.apache.camel.spi.EventNotifier} is in use, as there is already
 * a {@link org.apache.camel.spi.Synchronization} callback on the current {@link Exchange} which triggers the
 * {@link #throttle(org.apache.camel.Route, org.apache.camel.Exchange)} when the current {@link Exchange} is done.
 *
 * @version 
 */
public class ThrottlingInflightRoutePolicy extends RoutePolicySupport implements CamelContextAware {

    public enum ThrottlingScope {
        Context, Route
    }

    private final Set<Route> routes = new LinkedHashSet<Route>();
    private ContextScopedEventNotifier eventNotifier;
    private CamelContext camelContext;
    private final Lock lock = new ReentrantLock();
    private ThrottlingScope scope = ThrottlingScope.Route;
    private int maxInflightExchanges = 1000;
    private int resumePercentOfMax = 70;
    private int resumeInflightExchanges = 700;
    private LoggingLevel loggingLevel = LoggingLevel.INFO;
    private CamelLogger logger;

    public ThrottlingInflightRoutePolicy() {
    }

    @Override
    public String toString() {
        return "ThrottlingInflightRoutePolicy[" + maxInflightExchanges + " / " + resumePercentOfMax + "% using scope " + scope + "]";
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public void onInit(Route route) {
        // we need to remember the routes we apply for
        routes.add(route);
    }

    @Override
    public void onExchangeDone(Route route, Exchange exchange) {
        // if route scoped then throttle directly
        // as context scoped is handled using an EventNotifier instead
        if (scope == ThrottlingScope.Route) {
            throttle(route, exchange);
        }
    }

    /**
     * Throttles the route when {@link Exchange}s is done.
     *
     * @param route  the route
     * @param exchange the exchange
     */
    protected void throttle(Route route, Exchange exchange) {
        // this works the best when this logic is executed when the exchange is done
        Consumer consumer = route.getConsumer();

        int size = getSize(route, exchange);
        boolean stop = maxInflightExchanges > 0 && size > maxInflightExchanges;
        if (log.isTraceEnabled()) {
            log.trace("{} > 0 && {} > {} evaluated as {}", new Object[]{maxInflightExchanges, size, maxInflightExchanges, stop});
        }
        if (stop) {
            try {
                lock.lock();
                stopConsumer(size, consumer);
            } catch (Exception e) {
                handleException(e);
            } finally {
                lock.unlock();
            }
        }

        // reload size in case a race condition with too many at once being invoked
        // so we need to ensure that we read the most current size and start the consumer if we are already to low
        size = getSize(route, exchange);
        boolean start = size <= resumeInflightExchanges;
        if (log.isTraceEnabled()) {
            log.trace("{} <= {} evaluated as {}", new Object[]{size, resumeInflightExchanges, start});
        }
        if (start) {
            try {
                lock.lock();
                startConsumer(size, consumer);
            } catch (Exception e) {
                handleException(e);
            } finally {
                lock.unlock();
            }
        }
    }

    public int getMaxInflightExchanges() {
        return maxInflightExchanges;
    }

    /**
     * Sets the upper limit of number of concurrent inflight exchanges at which point reached
     * the throttler should suspend the route.
     * <p/>
     * Is default 1000.
     *
     * @param maxInflightExchanges the upper limit of concurrent inflight exchanges
     */
    public void setMaxInflightExchanges(int maxInflightExchanges) {
        this.maxInflightExchanges = maxInflightExchanges;
        // recalculate, must be at least at 1
        this.resumeInflightExchanges = Math.max(resumePercentOfMax * maxInflightExchanges / 100, 1);
    }

    public int getResumePercentOfMax() {
        return resumePercentOfMax;
    }

    /**
     * Sets at which percentage of the max the throttler should start resuming the route.
     * <p/>
     * Will by default use 70%.
     *
     * @param resumePercentOfMax the percentage must be between 0 and 100
     */
    public void setResumePercentOfMax(int resumePercentOfMax) {
        if (resumePercentOfMax < 0 || resumePercentOfMax > 100) {
            throw new IllegalArgumentException("Must be a percentage between 0 and 100, was: " + resumePercentOfMax);
        }

        this.resumePercentOfMax = resumePercentOfMax;
        // recalculate, must be at least at 1
        this.resumeInflightExchanges = Math.max(resumePercentOfMax * maxInflightExchanges / 100, 1);
    }

    public ThrottlingScope getScope() {
        return scope;
    }

    /**
     * Sets which scope the throttling should be based upon, either route or total scoped.
     *
     * @param scope the scope
     */
    public void setScope(ThrottlingScope scope) {
        this.scope = scope;
    }

    public LoggingLevel getLoggingLevel() {
        return loggingLevel;
    }

    public CamelLogger getLogger() {
        if (logger == null) {
            logger = createLogger();
        }
        return logger;
    }

    /**
     * Sets the logger to use for logging throttling activity.
     *
     * @param logger the logger
     */
    public void setLogger(CamelLogger logger) {
        this.logger = logger;
    }

    /**
     * Sets the logging level to report the throttling activity.
     * <p/>
     * Is default <tt>INFO</tt> level.
     *
     * @param loggingLevel the logging level
     */
    public void setLoggingLevel(LoggingLevel loggingLevel) {
        this.loggingLevel = loggingLevel;
    }

    protected CamelLogger createLogger() {
        return new CamelLogger(LoggerFactory.getLogger(ThrottlingInflightRoutePolicy.class), getLoggingLevel());
    }

    private int getSize(Route route, Exchange exchange) {
        if (scope == ThrottlingScope.Context) {
            return exchange.getContext().getInflightRepository().size();
        } else {
            return exchange.getContext().getInflightRepository().size(route.getId());
        }
    }

    private void startConsumer(int size, Consumer consumer) throws Exception {
        boolean started = resumeOrStartConsumer(consumer);
        if (started) {
            getLogger().log("Throttling consumer: " + size + " <= " + resumeInflightExchanges + " inflight exchange by resuming consumer: " + consumer);
        }
    }

    private void stopConsumer(int size, Consumer consumer) throws Exception {
        boolean stopped = suspendOrStopConsumer(consumer);
        if (stopped) {
            getLogger().log("Throttling consumer: " + size + " > " + maxInflightExchanges + " inflight exchange by suspending consumer: " + consumer);
        }
    }

    @Override
    protected void doStart() throws Exception {
        ObjectHelper.notNull(camelContext, "CamelContext", this);
        if (scope == ThrottlingScope.Context) {
            eventNotifier = new ContextScopedEventNotifier();
            // must start the notifier before it can be used
            ServiceHelper.startService(eventNotifier);
            // we are in context scope, so we need to use an event notifier to keep track
            // when any exchanges is done on the camel context.
            // This ensures we can trigger accordingly to context scope
            camelContext.getManagementStrategy().addEventNotifier(eventNotifier);
        }
    }

    @Override
    protected void doStop() throws Exception {
        ObjectHelper.notNull(camelContext, "CamelContext", this);
        if (scope == ThrottlingScope.Context) {
            camelContext.getManagementStrategy().removeEventNotifier(eventNotifier);
        }
    }

    /**
     * {@link org.apache.camel.spi.EventNotifier} to keep track on when {@link Exchange}
     * is done, so we can throttle accordingly.
     */
    private class ContextScopedEventNotifier extends EventNotifierSupport {

        @Override
        public void notify(EventObject event) throws Exception {
            ExchangeCompletedEvent completedEvent = (ExchangeCompletedEvent) event;
            for (Route route : routes) {
                throttle(route, completedEvent.getExchange());
            }
        }

        @Override
        public boolean isEnabled(EventObject event) {
            return event instanceof ExchangeCompletedEvent;
        }

        @Override
        protected void doStart() throws Exception {
            // noop
        }

        @Override
        protected void doStop() throws Exception {
            // noop
        }

        @Override
        public String toString() {
            return "ContextScopedEventNotifier";
        }
    }

}
