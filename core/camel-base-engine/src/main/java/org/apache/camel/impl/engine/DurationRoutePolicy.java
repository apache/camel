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
package org.apache.camel.impl.engine;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link org.apache.camel.spi.RoutePolicy} which executes for a duration and then triggers an action.
 * <p/>
 * This can be used to stop the route after it has processed a number of messages, or has been running for N seconds.
 */
public class DurationRoutePolicy extends org.apache.camel.support.RoutePolicySupport implements CamelContextAware {

    private static final Logger LOG = LoggerFactory.getLogger(DurationRoutePolicy.class);

    enum Action {
        STOP_CAMEL_CONTEXT,
        STOP_ROUTE,
        SUSPEND_ROUTE,
        SUSPEND_ALL_ROUTES
    }

    private CamelContext camelContext;
    private String routeId;
    private ScheduledExecutorService executorService;
    private volatile ScheduledFuture<?> task;
    private final AtomicInteger doneMessages = new AtomicInteger();
    private final AtomicBoolean actionDone = new AtomicBoolean();

    private Action action = Action.STOP_ROUTE;
    private int maxMessages;
    private int maxSeconds;

    public DurationRoutePolicy() {
    }

    public DurationRoutePolicy(CamelContext camelContext, String routeId) {
        this.camelContext = camelContext;
        this.routeId = routeId;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public int getMaxMessages() {
        return maxMessages;
    }

    /**
     * Maximum number of messages to process before the action is triggered
     */
    public void setMaxMessages(int maxMessages) {
        this.maxMessages = maxMessages;
    }

    public int getMaxSeconds() {
        return maxSeconds;
    }

    /**
     * Maximum seconds Camel is running before the action is triggered
     */
    public void setMaxSeconds(int maxSeconds) {
        this.maxSeconds = maxSeconds;
    }

    public Action getAction() {
        return action;
    }

    /**
     * What action to perform when maximum is triggered.
     */
    public void setAction(Action action) {
        this.action = action;
    }

    @Override
    public void onInit(Route route) {
        super.onInit(route);

        ObjectHelper.notNull(camelContext, "camelContext", this);

        if (maxMessages == 0 && maxSeconds == 0) {
            throw new IllegalArgumentException("The options maxMessages or maxSeconds must be configured");
        }

        if (routeId == null) {
            this.routeId = route.getId();
        }

        if (executorService == null) {
            executorService = camelContext.getExecutorServiceManager().newSingleThreadScheduledExecutor(this,
                    "DurationRoutePolicy[" + routeId + "]");
        }

        if (maxSeconds > 0) {
            task = performMaxDurationAction();
        }
    }

    @Override
    public void onExchangeDone(Route route, Exchange exchange) {
        int newDoneMessages = doneMessages.incrementAndGet();

        if (maxMessages > 0 && newDoneMessages >= maxMessages) {
            if (actionDone.compareAndSet(false, true)) {
                performMaxMessagesAction();
                if (task != null && !task.isDone()) {
                    task.cancel(false);
                }
            }
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (task != null && !task.isDone()) {
            task.cancel(false);
        }

        if (executorService != null) {
            getCamelContext().getExecutorServiceManager().shutdownNow(executorService);
            executorService = null;
        }
    }

    protected void performMaxMessagesAction() {
        executorService.submit(createTask(true));
    }

    protected ScheduledFuture<?> performMaxDurationAction() {
        return executorService.schedule(createTask(false), maxSeconds, TimeUnit.SECONDS);
    }

    private Runnable createTask(boolean maxMessagesHit) {
        return () -> {
            try {
                String tail;
                if (maxMessagesHit) {
                    tail = " due max messages " + getMaxMessages() + " processed";
                } else {
                    tail = " due max seconds " + getMaxSeconds();
                }

                if (action == Action.STOP_CAMEL_CONTEXT) {
                    LOG.info("Stopping CamelContext {}", tail);
                    camelContext.stop();
                } else if (action == Action.STOP_ROUTE) {
                    LOG.info("Stopping route: {}{}", routeId, tail);
                    camelContext.getRouteController().stopRoute(routeId);
                } else if (action == Action.SUSPEND_ROUTE) {
                    LOG.info("Suspending route: {}{}", routeId, tail);
                    camelContext.getRouteController().suspendRoute(routeId);
                } else if (action == Action.SUSPEND_ALL_ROUTES) {
                    LOG.info("Suspending all routes {}", tail);
                    camelContext.suspend();
                }
            } catch (Exception e) {
                LOG.warn("Error performing action: {}", action, e);
            }
        };
    }
}
