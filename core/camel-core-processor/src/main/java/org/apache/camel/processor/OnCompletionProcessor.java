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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.LongAdder;

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
import org.apache.camel.ShutdownRunningTask;
import org.apache.camel.StreamCache;
import org.apache.camel.Traceable;
import org.apache.camel.spi.IdAware;
import org.apache.camel.spi.RouteIdAware;
import org.apache.camel.spi.ShutdownAware;
import org.apache.camel.spi.StepIdAware;
import org.apache.camel.spi.SynchronizationRouteAware;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.SynchronizationAdapter;
import org.apache.camel.support.UnitOfWorkHelper;
import org.apache.camel.support.service.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.processor.ProcessorHelper.prepareMDCParallelTask;
import static org.apache.camel.util.ObjectHelper.notNull;

/**
 * Processor implementing <a href="http://camel.apache.org/oncompletion.html">onCompletion</a>.
 */
public class OnCompletionProcessor extends BaseProcessorSupport
        implements Traceable, ShutdownAware, IdAware, RouteIdAware, StepIdAware {

    private static final Logger LOG = LoggerFactory.getLogger(OnCompletionProcessor.class);

    private final CamelContext camelContext;
    private String id;
    private String routeId;
    private String stepId;
    private final Processor processor;
    private final ExecutorService executorService;
    private final boolean shutdownExecutorService;
    private final boolean onCompleteOnly;
    private final boolean onFailureOnly;
    private final Predicate onWhen;
    private final boolean useOriginalBody;
    private final boolean afterConsumer;
    private final boolean routeScoped;
    // non-null only for named (route-scoped) route configurations; used to dedup
    // across multiple OnCompletionProcessor instances sharing the same configuration
    private final String configurationId;
    private final LongAdder taskCount = new LongAdder();
    // the parallel onCompletion tasks that have been submitted but have not started yet
    private final Set<ParallelTask> pendingTasks = ConcurrentHashMap.newKeySet();

    public OnCompletionProcessor(CamelContext camelContext, Processor processor, ExecutorService executorService,
                                 boolean shutdownExecutorService,
                                 boolean onCompleteOnly, boolean onFailureOnly, Predicate onWhen, boolean useOriginalBody,
                                 boolean afterConsumer, boolean routeScoped, String configurationId) {
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
        this.configurationId = configurationId;
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
            // the tasks that have not started (the tasks still queued in the thread pool, which shutdownNow dropped, and
            // a task a thread has taken from the queue but not started) will never run, so they are no longer pending,
            // and what their copies hold is released
            for (ParallelTask task : pendingTasks) {
                task.discard();
            }
        }
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public boolean deferShutdown(ShutdownRunningTask shutdownRunningTask) {
        // not in use
        return true;
    }

    @Override
    public int getPendingExchangesSize() {
        return taskCount.intValue();
    }

    @Override
    public void prepareShutdown(boolean suspendOnly, boolean forced) {
        // noop
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
    public String getStepId() {
        return stepId;
    }

    @Override
    public void setStepId(String stepId) {
        this.stepId = stepId;
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        if (processor != null) {
            // register callback
            if (afterConsumer) {
                exchange.getUnitOfWork()
                        .addSynchronization(
                                new OnCompletionSynchronizationAfterConsumer(routeScoped, getRouteId(), configurationId));
            } else {
                exchange.getUnitOfWork()
                        .addSynchronization(
                                new OnCompletionSynchronizationBeforeConsumer(routeScoped, getRouteId(), configurationId));
            }
        }

        callback.done(true);
        return true;
    }

    /**
     * Submits the onCompletion task of the given copy to the thread pool (parallel processing). The task is counted as
     * pending from when it is submitted until it is done, so a graceful shutdown waits for it.
     * <p>
     * The copy may hold its own reference to a stream cache (see {@link #prepareExchange(Exchange)}), which is released
     * when the copy is done. When the task never runs (the thread pool rejects it, discards it because it is shut down,
     * or drops it when the processor shuts its thread pool down), it is no longer counted as pending and the reference
     * is released instead, as otherwise a spooled file would be kept until the stream caching strategy is stopped.
     * <p>
     * A thread pool that is shut down rejects every task, and may do so without failing (such as with the CallerRuns or
     * Discard policy), so the task is discarded when the thread pool was already shut down when the task was submitted.
     * A thread pool that is shut down (gracefully) after it has accepted the task still runs it, so the task must then
     * not be discarded. In the rare case that the thread pool is shut down while the task is being submitted, and
     * silently rejects it, the task is left as pending, and is discarded when the processor shuts its thread pool down.
     */
    @SuppressWarnings("deprecation")
    private void submitTask(Exchange copy, Runnable task) {
        ParallelTask parallelTask = new ParallelTask(copy, task);
        taskCount.increment();
        pendingTasks.add(parallelTask);
        // check before submitting, as the thread pool may be shut down by another thread after it accepted the task
        boolean shutdown = executorService.isShutdown();
        try {
            // Deprecated since 4.19.0
            executorService.submit(prepareMDCParallelTask(camelContext, parallelTask));
        } catch (RuntimeException e) {
            // the task will not run
            parallelTask.discard();
            throw e;
        }
        if (shutdown) {
            // the task was rejected, maybe without failing (unless the rejection policy has run it, and then this is a
            // noop)
            parallelTask.discard();
        }
    }

    /**
     * A parallel onCompletion task. Either the thread pool runs it, or it is discarded because it will not run, never
     * both: whichever comes first removes it from the pending tasks. Both stop counting it as pending.
     */
    private final class ParallelTask implements Runnable {

        private final Exchange copy;
        private final Runnable task;

        private ParallelTask(Exchange copy, Runnable task) {
            this.copy = copy;
            this.task = task;
        }

        @Override
        public void run() {
            // a task that was discarded (when the thread pool was shut down) must not be processed
            if (pendingTasks.remove(this)) {
                try {
                    task.run();
                } finally {
                    taskCount.decrement();
                }
            }
        }

        void discard() {
            if (pendingTasks.remove(this)) {
                taskCount.decrement();
                UnitOfWorkHelper.doneSynchronizations(copy, copy.getExchangeExtension().handoverCompletions());
            }
        }
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
    protected void doProcess(Processor processor, Exchange exchange) {
        // must remember some properties which we cannot use during onCompletion processing
        // as otherwise we may cause issues
        // but keep the caused exception stored as a property (Exchange.EXCEPTION_CAUGHT) on the exchange
        boolean stop = exchange.isRouteStop();
        exchange.setRouteStop(false);
        boolean failureHandled = exchange.getExchangeExtension().isFailureHandled();
        // the onCompletion is not failure handled (so its own failures can be handled by the error handler)
        exchange.getExchangeExtension().setFailureHandled(false);
        Boolean errorhandlerHandled = exchange.getExchangeExtension().getErrorHandlerHandled();
        exchange.getExchangeExtension().setErrorHandlerHandled(null);
        Object caught = exchange.getProperty(ExchangePropertyKey.EXCEPTION_CAUGHT);
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
            boolean newFailure = cause == null && exchange.getException() != null;
            if (newFailure) {
                // the onCompletion failed (and was not handled) so keep its error handler state
                if (failureHandled) {
                    exchange.getExchangeExtension().setFailureHandled(true);
                }
                if (errorhandlerHandled != null) {
                    exchange.getExchangeExtension().setErrorHandlerHandled(errorhandlerHandled);
                }
            } else {
                // restore the state as it was before the onCompletion (such as when the onCompletion
                // handled an exception by its error handler)
                exchange.getExchangeExtension().setFailureHandled(failureHandled);
                exchange.getExchangeExtension().setErrorHandlerHandled(errorhandlerHandled);
                if (caught != null) {
                    exchange.setProperty(ExchangePropertyKey.EXCEPTION_CAUGHT, caught);
                } else {
                    exchange.removeProperty(ExchangePropertyKey.EXCEPTION_CAUGHT);
                }
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
    @SuppressWarnings("deprecation")
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
            // the copy is routed when the original exchange is done (or in parallel with its completion), so it must
            // hold its own reference to a stream cache, as a spooled file is deleted when the original exchange is
            // done (same as the Wire Tap EIP does)
            answer.removeProperty(ExchangePropertyKey.STREAM_CACHE_UNIT_OF_WORK);
            if (answer.getIn().getBody() instanceof StreamCache sc) {
                try {
                    StreamCache copied = sc.copy(answer);
                    if (copied != null) {
                        answer.getIn().setBody(copied);
                    }
                } catch (IOException e) {
                    answer.setException(e);
                }
            }
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

    private final class OnCompletionSynchronizationAfterConsumer extends SynchronizationAdapter {

        private final boolean routeScoped;
        private final String routeId;
        private final String configurationId;

        public OnCompletionSynchronizationAfterConsumer(boolean routeScoped, String routeId, String configurationId) {
            this.routeScoped = routeScoped;
            this.routeId = routeId;
            this.configurationId = configurationId;
        }

        @Override
        public int getOrder() {
            // we want to be last, but before the stream cache clean up (Ordered.LOWEST), so we can read a spooled body
            return Ordered.LOWEST - 1;
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
                Runnable task = () -> {
                    LOG.debug("Processing onComplete: {}", copy);
                    doProcess(processor, copy);
                };
                submitTask(copy, task);
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
                Runnable task = () -> {
                    LOG.debug("Processing onFailure: {}", copy);
                    doProcess(processor, copy);
                    // restore exception after processing
                    copy.setException(original);
                };
                submitTask(copy, task);
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
                // named configuration: fire only once per exchange across all opted-in routes.
                // Multiple OnCompletionProcessor instances may share the same configurationId
                // (when several routes use the same routeConfigurationId); only the first to
                // fire records the id and the others skip.
                if (configurationId != null) {
                    Set<String> firedConfigIds
                            = exchange.getProperty(ExchangePropertyKey.ON_COMPLETION_FIRED_CONFIG_IDS, Set.class);
                    if (firedConfigIds != null && firedConfigIds.contains(configurationId)) {
                        return true;
                    }
                    if (firedConfigIds == null) {
                        firedConfigIds = new HashSet<>();
                        exchange.setProperty(ExchangePropertyKey.ON_COMPLETION_FIRED_CONFIG_IDS, firedConfigIds);
                    }
                    firedConfigIds.add(configurationId);
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

    private final class OnCompletionSynchronizationBeforeConsumer extends SynchronizationAdapter {

        private final boolean routeScoped;
        private final String routeId;
        private final String configurationId;

        public OnCompletionSynchronizationBeforeConsumer(boolean routeScoped, String routeId, String configurationId) {
            this.routeScoped = routeScoped;
            this.routeId = routeId;
            this.configurationId = configurationId;
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

                    // named configuration: fire only once per exchange across all opted-in routes
                    if (routeScoped && configurationId != null) {
                        @SuppressWarnings("unchecked")
                        Set<String> firedConfigIds
                                = exchange.getProperty(ExchangePropertyKey.ON_COMPLETION_FIRED_CONFIG_IDS, Set.class);
                        if (firedConfigIds != null && firedConfigIds.contains(configurationId)) {
                            return; // another processor for the same named config already fired
                        }
                        if (firedConfigIds == null) {
                            firedConfigIds = new HashSet<>();
                            exchange.setProperty(ExchangePropertyKey.ON_COMPLETION_FIRED_CONFIG_IDS, firedConfigIds);
                        }
                        firedConfigIds.add(configurationId);
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
                        Runnable task = () -> {
                            LOG.debug("Processing onAfterRoute: {}", copy);
                            doProcess(processor, copy);
                        };
                        submitTask(copy, task);
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
