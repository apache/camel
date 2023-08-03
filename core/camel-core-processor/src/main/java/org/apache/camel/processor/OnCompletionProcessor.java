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
package org.apache.camel.processor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Message;
import org.apache.camel.Ordered;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.Traceable;
import org.apache.camel.spi.IdAware;
import org.apache.camel.spi.RouteIdAware;
import org.apache.camel.spi.SynchronizationRouteAware;
import org.apache.camel.support.AsyncProcessorSupport;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.SynchronizationAdapter;
import org.apache.camel.support.service.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.util.ObjectHelper.notNull;

/**
 * Processor implementing <a href="http://camel.apache.org/oncompletion.html">onCompletion</a>.
 */
public class OnCompletionProcessor extends AsyncProcessorSupport implements Traceable, IdAware, RouteIdAware {

    private static final Logger LOG = LoggerFactory.getLogger(OnCompletionProcessor.class);

    private final CamelContext camelContext;
    private String id;
    private String routeId;
    private final Processor processor;
    private final ExecutorService executorService;
    private final boolean shutdownExecutorService;
    private final boolean onCompleteOnly;
    private final boolean onFailureOnly;
    private final Predicate onWhen;
    private final boolean useOriginalBody;
    private final boolean afterConsumer;
    private final boolean routeScoped;

    public OnCompletionProcessor(CamelContext camelContext, Processor processor, ExecutorService executorService,
                                 boolean shutdownExecutorService,
                                 boolean onCompleteOnly, boolean onFailureOnly, Predicate onWhen, boolean useOriginalBody,
                                 boolean afterConsumer, boolean routeScoped) {
        notNull(camelContext, "camelContext");
        notNull(processor, "processor");
        this.camelContext = camelContext;
        this.processor = processor;
        this.executorService = executorService;
        this.shutdownExecutorService = shutdownExecutorService;
        this.onCompleteOnly = onCompleteOnly;
        this.onFailureOnly = onFailureOnly;
        this.onWhen = onWhen;
        this.useOriginalBody = useOriginalBody;
        this.afterConsumer = afterConsumer;
        this.routeScoped = routeScoped;
    }

    @Override
    protected void doBuild() throws Exception {
        ServiceHelper.buildService(processor);
    }

    @Override
    protected void doInit() throws Exception {
        ServiceHelper.initService(processor);
    }

    @Override
    protected void doStart() throws Exception {
        ServiceHelper.startService(processor);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(processor);
    }

    @Override
    protected void doShutdown() throws Exception {
        ServiceHelper.stopAndShutdownService(processor);
        if (shutdownExecutorService) {
            getCamelContext().getExecutorServiceManager().shutdownNow(executorService);
        }
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String getRouteId() {
        return routeId;
    }

    @Override
    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        if (processor != null) {
            // register callback
            if (afterConsumer) {
                exchange.getUnitOfWork()
                        .addSynchronization(new OnCompletionSynchronizationAfterConsumer(routeScoped, getRouteId()));
            } else {
                exchange.getUnitOfWork()
                        .addSynchronization(new OnCompletionSynchronizationBeforeConsumer(routeScoped, getRouteId()));
            }
        }

        callback.done(true);
        return true;
    }

    protected boolean isCreateCopy() {
        // we need to create a correlated copy if we run in parallel mode or is in after consumer mode (as the UoW would be done on the original exchange otherwise)
        return executorService != null || afterConsumer;
    }

    /**
     * Processes the exchange by the processors
     *
     * @param processor the processor
     * @param exchange  the exchange
     */
    protected static void doProcess(Processor processor, Exchange exchange) {
        // must remember some properties which we cannot use during onCompletion processing
        // as otherwise we may cause issues
        // but keep the caused exception stored as a property (Exchange.EXCEPTION_CAUGHT) on the exchange
        boolean stop = exchange.isRouteStop();
        exchange.setRouteStop(false);
        boolean failureHandled = exchange.getExchangeExtension().isFailureHandled();
        Boolean errorhandlerHandled = exchange.getExchangeExtension().getErrorHandlerHandled();
        exchange.getExchangeExtension().setErrorHandlerHandled(null);
        boolean rollbackOnly = exchange.isRollbackOnly();
        exchange.setRollbackOnly(false);
        boolean rollbackOnlyLast = exchange.isRollbackOnlyLast();
        exchange.setRollbackOnlyLast(false);
        // and we should not be regarded as exhausted as we are in a onCompletion block
        boolean exhausted = exchange.getExchangeExtension().isRedeliveryExhausted();
        exchange.getExchangeExtension().setRedeliveryExhausted(false);

        Exception cause = exchange.getException();
        if (cause != null) {
            exchange.setException(null);
        }

        try {
            processor.process(exchange);
        } catch (Exception e) {
            exchange.setException(e);
        } finally {
            // restore the options
            exchange.setRouteStop(stop);
            if (failureHandled) {
                exchange.getExchangeExtension().setFailureHandled(true);
            }
            if (errorhandlerHandled != null) {
                exchange.getExchangeExtension().setErrorHandlerHandled(errorhandlerHandled);
            }
            exchange.setRollbackOnly(rollbackOnly);
            exchange.setRollbackOnlyLast(rollbackOnlyLast);
            exchange.getExchangeExtension().setRedeliveryExhausted(exhausted);
            if (cause != null) {
                // if there is any exception in onCompletionProcessor, the exception should be suppressed
                if (exchange.isFailed()) {
                    cause.addSuppressed(exchange.getException());
                }
                exchange.setException(cause);
            }
        }
    }

    /**
     * Prepares the {@link Exchange} to send as onCompletion.
     *
     * @param  exchange the current exchange
     * @return          the exchange to be routed in onComplete
     */
    protected Exchange prepareExchange(Exchange exchange) {
        Exchange answer;

        if (isCreateCopy()) {
            // for asynchronous routing we must use a copy as we don't want it
            // to cause side effects of the original exchange
            // (the original thread will run in parallel)
            answer = ExchangeHelper.createCorrelatedCopy(exchange, false);
            if (answer.hasOut()) {
                // move OUT to IN (pipes and filters)
                answer.setIn(answer.getOut());
                answer.setOut(null);
            }
            // set MEP to InOnly as this onCompletion is a fire and forget
            answer.setPattern(ExchangePattern.InOnly);
        } else {
            // use the exchange as-is
            answer = exchange;
        }

        if (useOriginalBody) {
            LOG.trace("Using the original IN message instead of current");

            Message original = ExchangeHelper.getOriginalInMessage(exchange);
            answer.setIn(original);
        }

        // add a header flag to indicate its a on completion exchange
        answer.setProperty(ExchangePropertyKey.ON_COMPLETION, Boolean.TRUE);

        return answer;
    }

    private final class OnCompletionSynchronizationAfterConsumer extends SynchronizationAdapter implements Ordered {

        private final boolean routeScoped;
        private final String routeId;

        public OnCompletionSynchronizationAfterConsumer(boolean routeScoped, String routeId) {
            this.routeScoped = routeScoped;
            this.routeId = routeId;
        }

        @Override
        public int getOrder() {
            // we want to be last
            return Ordered.LOWEST;
        }

        @Override
        public SynchronizationRouteAware getRouteSynchronization() {
            return new SynchronizationRouteAware() {
                @Override
                public void onBeforeRoute(Route route, Exchange exchange) {
                    // NO-OP
                }

                @Override
                public void onAfterRoute(Route route, Exchange exchange) {
                    // route scope = remember we have been at this route
                    if (routeScoped && route.getRouteId().equals(routeId)) {
                        @SuppressWarnings("unchecked")
                        List<String> routeIds = exchange.getProperty(ExchangePropertyKey.ON_COMPLETION_ROUTE_IDS, List.class);
                        if (routeIds == null) {
                            routeIds = new ArrayList<>();
                            exchange.setProperty(ExchangePropertyKey.ON_COMPLETION_ROUTE_IDS, routeIds);
                        }
                        routeIds.add(route.getRouteId());
                    }
                }
            };
        }

        @Override
        public void onComplete(final Exchange exchange) {
            if (shouldSkip(exchange, onFailureOnly)) {
                return;
            }

            // must use a copy as we don't want it to cause side effects of the original exchange
            final Exchange copy = prepareExchange(exchange);

            if (executorService != null) {
                executorService.submit(new Callable<Exchange>() {
                    public Exchange call() throws Exception {
                        LOG.debug("Processing onComplete: {}", copy);
                        doProcess(processor, copy);
                        return copy;
                    }
                });
            } else {
                // run without thread-pool
                LOG.debug("Processing onComplete: {}", copy);
                doProcess(processor, copy);
            }
        }

        @Override
        public void onFailure(final Exchange exchange) {
            if (shouldSkip(exchange, onCompleteOnly)) {
                return;
            }

            // must use a copy as we don't want it to cause side effects of the original exchange
            final Exchange copy = prepareExchange(exchange);
            final Exception original = copy.getException();
            if (original != null) {
                // must remove exception otherwise onFailure routing will fail as well
                // the caused exception is stored as a property (Exchange.EXCEPTION_CAUGHT) on the exchange
                copy.setException(null);
            }

            if (executorService != null) {
                executorService.submit(new Callable<Exchange>() {
                    public Exchange call() throws Exception {
                        LOG.debug("Processing onFailure: {}", copy);
                        doProcess(processor, copy);
                        // restore exception after processing
                        copy.setException(original);
                        return null;
                    }
                });
            } else {
                // run without thread-pool
                LOG.debug("Processing onFailure: {}", copy);
                doProcess(processor, copy);
                // restore exception after processing
                copy.setException(original);
            }
        }

        @SuppressWarnings("unchecked")
        private boolean shouldSkip(Exchange exchange, boolean onCompleteOrOnFailureOnly) {
            String currentRouteId = ExchangeHelper.getRouteId(exchange);
            if (!routeScoped && currentRouteId != null && !routeId.equals(currentRouteId)) {
                return true;
            }

            if (routeScoped) {
                // check if we visited the route
                List<String> routeIds = exchange.getProperty(ExchangePropertyKey.ON_COMPLETION_ROUTE_IDS, List.class);
                if (routeIds == null || !routeIds.contains(routeId)) {
                    return true;
                }
            }

            if (onCompleteOrOnFailureOnly) {
                return true;
            }

            if (onWhen != null && !onWhen.matches(exchange)) {
                // predicate did not match so do not route the onComplete
                return true;
            }

            return false;
        }

        @Override
        public String toString() {
            if (!onCompleteOnly && !onFailureOnly) {
                return "onCompleteOrFailure";
            } else if (onCompleteOnly) {
                return "onCompleteOnly";
            } else {
                return "onFailureOnly";
            }
        }

        @Override
        public void beforeHandover(Exchange target) {
            // The onAfterRoute method will not be called after the handover
            // To ensure that completions are called, remember the route IDs here.
            // Assumption: the fromRouteId on the target Exchange is the route
            // which owns the completion
            LOG.debug("beforeHandover from Route {}", target.getFromRouteId());
            final String exchangeRouteId = target.getFromRouteId();
            if (routeScoped && exchangeRouteId != null && exchangeRouteId.equals(routeId)) {
                List<String> routeIds = target.getProperty(ExchangePropertyKey.ON_COMPLETION_ROUTE_IDS, List.class);
                if (routeIds == null) {
                    routeIds = new ArrayList<>();
                    target.setProperty(ExchangePropertyKey.ON_COMPLETION_ROUTE_IDS, routeIds);
                }
                if (!routeIds.contains(exchangeRouteId)) {
                    routeIds.add(exchangeRouteId);
                }
            }
        }
    }

    private final class OnCompletionSynchronizationBeforeConsumer extends SynchronizationAdapter implements Ordered {

        private final boolean routeScoped;
        private final String routeId;

        public OnCompletionSynchronizationBeforeConsumer(boolean routeScoped, String routeId) {
            this.routeScoped = routeScoped;
            this.routeId = routeId;
        }

        @Override
        public int getOrder() {
            // we want to be last
            return Ordered.LOWEST;
        }

        @Override
        public SynchronizationRouteAware getRouteSynchronization() {
            return new SynchronizationRouteAware() {
                @Override
                public void onBeforeRoute(Route route, Exchange exchange) {
                    // NO-OP
                }

                @Override
                public void onAfterRoute(Route route, Exchange exchange) {
                    LOG.debug("onAfterRoute from Route {}", route.getRouteId());
                    // route scope = should be from this route
                    if (routeScoped && !route.getRouteId().equals(routeId)) {
                        return;
                    }

                    // global scope = should be from the original route
                    if (!routeScoped && (!route.getRouteId().equals(routeId) || !exchange.getFromRouteId().equals(routeId))) {
                        return;
                    }

                    if (exchange.isFailed() && onCompleteOnly) {
                        return;
                    }

                    if (!exchange.isFailed() && onFailureOnly) {
                        return;
                    }

                    if (onWhen != null && !onWhen.matches(exchange)) {
                        // predicate did not match so do not route the onComplete
                        return;
                    }

                    // must use a copy as we don't want it to cause side effects of the original exchange
                    final Exchange copy = prepareExchange(exchange);

                    if (executorService != null) {
                        executorService.submit(new Callable<Exchange>() {
                            public Exchange call() throws Exception {
                                LOG.debug("Processing onAfterRoute: {}", copy);
                                doProcess(processor, copy);
                                return copy;
                            }
                        });
                    } else {
                        // run without thread-pool
                        LOG.debug("Processing onAfterRoute: {}", copy);
                        doProcess(processor, copy);
                    }
                }
            };
        }

        @Override
        public boolean allowHandover() {
            return false;
        }

        @Override
        public String toString() {
            return "onAfterRoute";
        }
    }

    @Override
    public String toString() {
        return id;
    }

    @Override
    public String getTraceLabel() {
        return "onCompletion";
    }
}
