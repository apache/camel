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
package org.apache.camel.component.resilience4j;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Supplier;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.vavr.control.Try;
import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Navigate;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.RuntimeExchangeException;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.processor.PooledExchangeTask;
import org.apache.camel.processor.PooledExchangeTaskFactory;
import org.apache.camel.processor.PooledTaskFactory;
import org.apache.camel.processor.PrototypeTaskFactory;
import org.apache.camel.spi.IdAware;
import org.apache.camel.spi.ProcessorExchangeFactory;
import org.apache.camel.spi.RouteIdAware;
import org.apache.camel.spi.UnitOfWork;
import org.apache.camel.support.AsyncProcessorSupport;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.UnitOfWorkHelper;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of Circuit Breaker EIP using resilience4j.
 */
@ManagedResource(description = "Managed Resilience Processor")
public class ResilienceProcessor extends AsyncProcessorSupport
        implements CamelContextAware, Navigate<Processor>, org.apache.camel.Traceable, IdAware, RouteIdAware {

    private static final Logger LOG = LoggerFactory.getLogger(ResilienceProcessor.class);

    private volatile CircuitBreaker circuitBreaker;
    private CamelContext camelContext;
    private String id;
    private String routeId;
    private final CircuitBreakerConfig circuitBreakerConfig;
    private final BulkheadConfig bulkheadConfig;
    private Bulkhead bulkhead;
    private final TimeLimiterConfig timeLimiterConfig;
    private TimeLimiter timeLimiter;
    private final Processor processor;
    private final Processor fallback;
    private final boolean throwExceptionWhenHalfOpenOrOpenState;
    private boolean shutdownExecutorService;
    private ExecutorService executorService;
    private ProcessorExchangeFactory processorExchangeFactory;
    private PooledExchangeTaskFactory taskFactory;
    private PooledExchangeTaskFactory fallbackTaskFactory;

    public ResilienceProcessor(CircuitBreakerConfig circuitBreakerConfig, BulkheadConfig bulkheadConfig,
                               TimeLimiterConfig timeLimiterConfig, Processor processor,
                               Processor fallback, boolean throwExceptionWhenHalfOpenOrOpenState) {
        this.circuitBreakerConfig = circuitBreakerConfig;
        this.bulkheadConfig = bulkheadConfig;
        this.timeLimiterConfig = timeLimiterConfig;
        this.processor = processor;
        this.fallback = fallback;
        this.throwExceptionWhenHalfOpenOrOpenState = throwExceptionWhenHalfOpenOrOpenState;
    }

    @Override
    protected void doBuild() throws Exception {
        ObjectHelper.notNull(camelContext, "CamelContext", this);

        if (timeLimiterConfig != null) {
            timeLimiter = TimeLimiter.of(id, timeLimiterConfig);
        }
        if (bulkheadConfig != null) {
            bulkhead = Bulkhead.of(id, bulkheadConfig);
        }

        boolean pooled = camelContext.getCamelContextExtension().getExchangeFactory().isPooled();
        if (pooled) {
            int capacity = camelContext.getCamelContextExtension().getExchangeFactory().getCapacity();
            taskFactory = new PooledTaskFactory(getId()) {
                @Override
                public PooledExchangeTask create(Exchange exchange, AsyncCallback callback) {
                    return new CircuitBreakerTask();
                }
            };
            taskFactory.setCapacity(capacity);
            fallbackTaskFactory = new PooledTaskFactory(getId()) {
                @Override
                public PooledExchangeTask create(Exchange exchange, AsyncCallback callback) {
                    return new CircuitBreakerFallbackTask();
                }
            };
            fallbackTaskFactory.setCapacity(capacity);
        } else {
            taskFactory = new PrototypeTaskFactory() {
                @Override
                public PooledExchangeTask create(Exchange exchange, AsyncCallback callback) {
                    return new CircuitBreakerTask();
                }
            };
            fallbackTaskFactory = new PrototypeTaskFactory() {
                @Override
                public PooledExchangeTask create(Exchange exchange, AsyncCallback callback) {
                    return new CircuitBreakerFallbackTask();
                }
            };
        }

        // create a per processor exchange factory
        this.processorExchangeFactory = getCamelContext().getCamelContextExtension()
                .getProcessorExchangeFactory().newProcessorExchangeFactory(this);
        this.processorExchangeFactory.setRouteId(getRouteId());
        this.processorExchangeFactory.setId(getId());

        ServiceHelper.buildService(processorExchangeFactory, taskFactory, fallbackTaskFactory, processor);
    }

    @Override
    protected void doStart() throws Exception {
        if (circuitBreaker == null) {
            circuitBreaker = CircuitBreaker.of(id, circuitBreakerConfig);
        }

        ServiceHelper.startService(processorExchangeFactory, taskFactory, fallbackTaskFactory, processor);
    }

    @Override
    protected void doStop() throws Exception {
        if (shutdownExecutorService && executorService != null) {
            getCamelContext().getExecutorServiceManager().shutdownNow(executorService);
        }

        ServiceHelper.stopService(processorExchangeFactory, taskFactory, fallbackTaskFactory, processor);
    }

    @Override
    protected void doShutdown() throws Exception {
        ServiceHelper.stopAndShutdownServices(processorExchangeFactory, taskFactory, fallbackTaskFactory, processor);
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
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

    public CircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }

    public void setCircuitBreaker(CircuitBreaker circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
    }

    public boolean isShutdownExecutorService() {
        return shutdownExecutorService;
    }

    public void setShutdownExecutorService(boolean shutdownExecutorService) {
        this.shutdownExecutorService = shutdownExecutorService;
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }

    public void setExecutorService(ExecutorService executorService) {
        this.executorService = executorService;
    }

    @Override
    public String getTraceLabel() {
        return "resilience4j";
    }

    @ManagedAttribute(description = "Returns the current failure rate in percentage.")
    public float getFailureRate() {
        if (circuitBreaker != null) {
            return circuitBreaker.getMetrics().getFailureRate();
        } else {
            return 0;
        }
    }

    @ManagedAttribute(description = "Returns the current percentage of calls which were slower than a certain threshold.")
    public float getSlowCallRate() {
        if (circuitBreaker != null) {
            return circuitBreaker.getMetrics().getSlowCallRate();
        } else {
            return 0;
        }
    }

    @ManagedAttribute(description = "Returns the current total number of calls which were slower than a certain threshold.")
    public int getNumberOfSlowCalls() {
        if (circuitBreaker != null) {
            return circuitBreaker.getMetrics().getNumberOfSlowCalls();
        } else {
            return 0;
        }
    }

    @ManagedAttribute(description = "Returns the current number of successful calls which were slower than a certain threshold.")
    public int getNumberOfSlowSuccessfulCalls() {
        if (circuitBreaker != null) {
            return circuitBreaker.getMetrics().getNumberOfSlowCalls();
        } else {
            return 0;
        }
    }

    @ManagedAttribute(description = "Returns the current number of failed calls which were slower than a certain threshold.")
    public int getNumberOfSlowFailedCalls() {
        if (circuitBreaker != null) {
            return circuitBreaker.getMetrics().getNumberOfSlowFailedCalls();
        } else {
            return 0;
        }
    }

    @ManagedAttribute(description = "Returns the current total number of buffered calls in the ring buffer.")
    public int getNumberOfBufferedCalls() {
        if (circuitBreaker != null) {
            return circuitBreaker.getMetrics().getNumberOfBufferedCalls();
        } else {
            return 0;
        }
    }

    @ManagedAttribute(description = "Returns the current number of failed buffered calls in the ring buffer.")
    public int getNumberOfFailedCalls() {
        if (circuitBreaker != null) {
            return circuitBreaker.getMetrics().getNumberOfFailedCalls();
        } else {
            return 0;
        }
    }

    @ManagedAttribute(description = "Returns the current number of successful buffered calls in the ring buffer")
    public int getNumberOfSuccessfulCalls() {
        if (circuitBreaker != null) {
            return circuitBreaker.getMetrics().getNumberOfSuccessfulCalls();
        } else {
            return 0;
        }
    }

    @ManagedAttribute(description = "Returns the current number of not permitted calls, when the state is OPEN.")
    public long getNumberOfNotPermittedCalls() {
        if (circuitBreaker != null) {
            return circuitBreaker.getMetrics().getNumberOfNotPermittedCalls();
        } else {
            return 0;
        }
    }

    @ManagedAttribute(description = "Returns the current state of the circuit breaker")
    public String getCircuitBreakerState() {
        if (circuitBreaker != null) {
            return circuitBreaker.getState().name();
        } else {
            return null;
        }
    }

    @ManagedOperation(description = "Transitions the circuit breaker to CLOSED state.")
    public void transitionToCloseState() {
        if (circuitBreaker != null) {
            circuitBreaker.transitionToClosedState();
        }
    }

    @ManagedOperation(description = "Transitions the circuit breaker to OPEN state.")
    public void transitionToOpenState() {
        if (circuitBreaker != null) {
            circuitBreaker.transitionToOpenState();
        }
    }

    @ManagedOperation(description = "Transitions the circuit breaker to HALF_OPEN state.")
    public void transitionToHalfOpenState() {
        if (circuitBreaker != null) {
            circuitBreaker.transitionToHalfOpenState();
        }
    }

    @ManagedOperation(description = "Transitions the state machine to a FORCED_OPEN state, stopping state transition, metrics and event publishing.")
    public void transitionToForcedOpenState() {
        if (circuitBreaker != null) {
            circuitBreaker.transitionToForcedOpenState();
        }
    }

    @ManagedAttribute
    public float getCircuitBreakerFailureRateThreshold() {
        return circuitBreakerConfig.getFailureRateThreshold();
    }

    @ManagedAttribute
    public float getCircuitBreakerSlowCallRateThreshold() {
        return circuitBreakerConfig.getSlowCallRateThreshold();
    }

    @ManagedAttribute
    public int getCircuitBreakerMinimumNumberOfCalls() {
        return circuitBreakerConfig.getMinimumNumberOfCalls();
    }

    @ManagedAttribute
    public int getCircuitBreakerPermittedNumberOfCallsInHalfOpenState() {
        return circuitBreakerConfig.getPermittedNumberOfCallsInHalfOpenState();
    }

    @ManagedAttribute
    public int getCircuitBreakerSlidingWindowSize() {
        return circuitBreakerConfig.getSlidingWindowSize();
    }

    @ManagedAttribute
    public String getCircuitBreakerSlidingWindowType() {
        return circuitBreakerConfig.getSlidingWindowType().name();
    }

    @ManagedAttribute
    public long getCircuitBreakerWaitDurationInOpenState() {
        return Duration.ofMillis(circuitBreakerConfig.getWaitIntervalFunctionInOpenState().apply(1)).getSeconds();
    }

    @ManagedAttribute
    public boolean isCircuitBreakerTransitionFromOpenToHalfOpenEnabled() {
        return circuitBreakerConfig.isAutomaticTransitionFromOpenToHalfOpenEnabled();
    }

    @ManagedAttribute
    public boolean isCircuitBreakerWritableStackTraceEnabled() {
        return circuitBreakerConfig.isWritableStackTraceEnabled();
    }

    @ManagedAttribute
    public boolean isBulkheadEnabled() {
        return bulkheadConfig != null;
    }

    @ManagedAttribute
    public int getBulkheadMaxConcurrentCalls() {
        if (bulkheadConfig != null) {
            return bulkheadConfig.getMaxConcurrentCalls();
        } else {
            return 0;
        }
    }

    @ManagedAttribute()
    public long getBulkheadMaxWaitDuration() {
        if (bulkheadConfig != null) {
            return bulkheadConfig.getMaxWaitDuration().toMillis();
        } else {
            return 0;
        }
    }

    @ManagedAttribute
    public boolean isTimeoutEnabled() {
        return timeLimiterConfig != null;
    }

    @ManagedAttribute
    public long getTimeoutDuration() {
        if (timeLimiterConfig != null) {
            return timeLimiterConfig.getTimeoutDuration().toMillis();
        } else {
            return 0;
        }
    }

    @Override
    public List<Processor> next() {
        if (!hasNext()) {
            return null;
        }
        List<Processor> answer = new ArrayList<>();
        answer.add(processor);
        if (fallback != null) {
            answer.add(fallback);
        }
        return answer;
    }

    @Override
    public boolean hasNext() {
        return true;
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        // run this as if we run inside try .. catch so there is no regular
        // Camel error handler
        exchange.setProperty(ExchangePropertyKey.TRY_ROUTE_BLOCK, true);

        CircuitBreakerFallbackTask fallbackTask = null;
        CircuitBreakerTask task = null;
        try {
            fallbackTask = (CircuitBreakerFallbackTask) fallbackTaskFactory.acquire(exchange, callback);
            task = (CircuitBreakerTask) taskFactory.acquire(exchange, callback);
            final CircuitBreakerTask ftask = task; // annoying final java thingy!
            Callable<Exchange> callable;

            if (timeLimiter != null) {
                Supplier<CompletableFuture<Exchange>> futureSupplier;
                if (executorService == null) {
                    futureSupplier = () -> CompletableFuture.supplyAsync(ftask);
                } else {
                    futureSupplier = () -> CompletableFuture.supplyAsync(ftask, executorService);
                }
                callable = TimeLimiter.decorateFutureSupplier(timeLimiter, futureSupplier);
            } else {
                callable = task;
            }
            if (bulkhead != null) {
                callable = Bulkhead.decorateCallable(bulkhead, callable);
            }

            callable = CircuitBreaker.decorateCallable(circuitBreaker, callable);
            if (LOG.isTraceEnabled()) {
                LOG.trace("Processing exchange: {} using circuit breaker: {}", exchange.getExchangeId(), id);
            }
            Try.ofCallable(callable).recover(fallbackTask).get();
        } catch (Exception e) {
            exchange.setException(e);
        } finally {
            if (task != null) {
                taskFactory.release(task);
            }
            if (fallbackTask != null) {
                fallbackTaskFactory.release(fallbackTask);
            }
        }

        if (LOG.isTraceEnabled()) {
            boolean failed = exchange.isFailed();
            LOG.trace("Processing exchange: {} using circuit breaker: {} complete (failed: {})", exchange.getExchangeId(), id,
                    failed);
        }

        exchange.removeProperty(ExchangePropertyKey.TRY_ROUTE_BLOCK);
        callback.done(true);
        return true;
    }

    private Exchange processTask(Exchange exchange) {
        Exchange copy = null;
        UnitOfWork uow = null;
        Throwable cause;
        try {
            LOG.debug("Running processor: {} with exchange: {}", processor, exchange);
            // prepare a copy of exchange so downstream processors don't
            // cause side-effects if they mutate the exchange
            // in case timeout processing and continue with the fallback etc
            copy = processorExchangeFactory.createCorrelatedCopy(exchange, false);
            if (copy.getUnitOfWork() != null) {
                uow = copy.getUnitOfWork();
            } else {
                // prepare uow on copy
                uow = PluginHelper.getUnitOfWorkFactory(copy.getContext()).createUnitOfWork(copy);
                copy.getExchangeExtension().setUnitOfWork(uow);
                // the copy must be starting from the route where its copied from
                Route route = ExchangeHelper.getRoute(exchange);
                if (route != null) {
                    uow.pushRoute(route);
                }
            }

            // process the processor until its fully done
            processor.process(copy);

            // handle the processing result
            if (copy.getException() != null) {
                exchange.setException(copy.getException());
            } else {
                // copy the result as its regarded as success
                ExchangeHelper.copyResults(exchange, copy);
                exchange.setProperty(ExchangePropertyKey.CIRCUIT_BREAKER_RESPONSE_SUCCESSFUL_EXECUTION, true);
                exchange.setProperty(ExchangePropertyKey.CIRCUIT_BREAKER_RESPONSE_FROM_FALLBACK, false);
            }
        } catch (Exception e) {
            exchange.setException(e);
        } finally {
            // must done uow
            UnitOfWorkHelper.doneUow(uow, copy);
            // remember any thrown exception
            cause = exchange.getException();
        }

        // and release exchange back in pool
        processorExchangeFactory.release(exchange);

        if (cause != null) {
            // throw exception so resilient4j know it was a failure
            throw RuntimeExchangeException.wrapRuntimeException(cause);
        }
        return exchange;
    }

    private final class CircuitBreakerTask implements PooledExchangeTask, Callable<Exchange>, Supplier<Exchange> {

        private Exchange exchange;

        @Override
        public void prepare(Exchange exchange, AsyncCallback callback) {
            this.exchange = exchange;
            // callback not in use
        }

        @Override
        public void reset() {
            this.exchange = null;
        }

        @Override
        public void run() {
            // not in use
        }

        @Override
        public Exchange call() throws Exception {
            // this task is either use as callable or supplier
            // therefore we must call process task before returning the response
            return processTask(exchange);
        }

        @Override
        public Exchange get() {
            // this task is either use as callable or supplier
            // therefore we must call process task before returning the response
            return processTask(exchange);
        }
    }

    private final class CircuitBreakerFallbackTask implements PooledExchangeTask, Function<Throwable, Exchange> {

        private Exchange exchange;

        @Override
        public void prepare(Exchange exchange, AsyncCallback callback) {
            this.exchange = exchange;
            // callback not in use
        }

        @Override
        public void reset() {
            this.exchange = null;
        }

        @Override
        public void run() {
            // not in use
        }

        @Override
        public Exchange apply(Throwable throwable) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Processing exchange: {} recover task using circuit breaker: {} from: {}", exchange.getExchangeId(),
                        id, throwable);
            }

            if (fallback == null) {
                if (throwable instanceof TimeoutException) {
                    // the circuit breaker triggered a timeout (and there is no
                    // fallback) so lets mark the exchange as failed
                    exchange.setProperty(ExchangePropertyKey.CIRCUIT_BREAKER_RESPONSE_SUCCESSFUL_EXECUTION, false);
                    exchange.setProperty(ExchangePropertyKey.CIRCUIT_BREAKER_RESPONSE_FROM_FALLBACK, false);
                    exchange.setProperty(ExchangePropertyKey.CIRCUIT_BREAKER_RESPONSE_SHORT_CIRCUITED, false);
                    exchange.setProperty(ExchangePropertyKey.CIRCUIT_BREAKER_RESPONSE_TIMED_OUT, true);
                    exchange.setException(throwable);
                    return exchange;
                } else if (throwable instanceof CallNotPermittedException) {
                    // the circuit breaker triggered a call rejected
                    exchange.setProperty(ExchangePropertyKey.CIRCUIT_BREAKER_RESPONSE_SUCCESSFUL_EXECUTION, false);
                    exchange.setProperty(ExchangePropertyKey.CIRCUIT_BREAKER_RESPONSE_FROM_FALLBACK, false);
                    exchange.setProperty(ExchangePropertyKey.CIRCUIT_BREAKER_RESPONSE_SHORT_CIRCUITED, true);
                    exchange.setProperty(ExchangePropertyKey.CIRCUIT_BREAKER_RESPONSE_REJECTED, true);
                    if (throwExceptionWhenHalfOpenOrOpenState) {
                        exchange.setException(throwable);
                    }
                    return exchange;
                } else if (throwable instanceof BulkheadFullException) {
                    // the circuit breaker bulkhead is full
                    exchange.setProperty(ExchangePropertyKey.CIRCUIT_BREAKER_RESPONSE_SUCCESSFUL_EXECUTION, false);
                    exchange.setProperty(ExchangePropertyKey.CIRCUIT_BREAKER_RESPONSE_FROM_FALLBACK, false);
                    exchange.setProperty(ExchangePropertyKey.CIRCUIT_BREAKER_RESPONSE_SHORT_CIRCUITED, true);
                    exchange.setProperty(ExchangePropertyKey.CIRCUIT_BREAKER_RESPONSE_REJECTED, true);
                    exchange.setException(throwable);
                    return exchange;
                } else {
                    // other kind of exception
                    exchange.setProperty(ExchangePropertyKey.CIRCUIT_BREAKER_RESPONSE_SUCCESSFUL_EXECUTION, false);
                    exchange.setProperty(ExchangePropertyKey.CIRCUIT_BREAKER_RESPONSE_FROM_FALLBACK, false);
                    exchange.setProperty(ExchangePropertyKey.CIRCUIT_BREAKER_RESPONSE_SHORT_CIRCUITED, true);
                    exchange.setProperty(ExchangePropertyKey.CIRCUIT_BREAKER_RESPONSE_REJECTED, true);
                    exchange.setException(throwable);
                    return exchange;
                }
            }

            // fallback route is handling the exception so its short-circuited
            exchange.setProperty(ExchangePropertyKey.CIRCUIT_BREAKER_RESPONSE_SUCCESSFUL_EXECUTION, false);
            exchange.setProperty(ExchangePropertyKey.CIRCUIT_BREAKER_RESPONSE_FROM_FALLBACK, true);
            exchange.setProperty(ExchangePropertyKey.CIRCUIT_BREAKER_RESPONSE_SHORT_CIRCUITED, true);

            // store the last to endpoint as the failure endpoint
            if (exchange.getProperty(ExchangePropertyKey.FAILURE_ENDPOINT) == null) {
                exchange.setProperty(ExchangePropertyKey.FAILURE_ENDPOINT,
                        exchange.getProperty(ExchangePropertyKey.TO_ENDPOINT));
            }
            // give the rest of the pipeline another chance
            exchange.setProperty(ExchangePropertyKey.EXCEPTION_HANDLED, true);
            exchange.setProperty(ExchangePropertyKey.EXCEPTION_CAUGHT, exchange.getException());
            exchange.setRouteStop(false);
            exchange.setException(null);
            // and we should not be regarded as exhausted as we are in a try ..
            // catch block
            exchange.getExchangeExtension().setRedeliveryExhausted(false);
            // run the fallback processor
            try {
                LOG.debug("Running fallback: {} with exchange: {}", fallback, exchange);
                // process the fallback until its fully done
                fallback.process(exchange);
                LOG.debug("Running fallback: {} with exchange: {} done", fallback, exchange);
            } catch (Throwable e) {
                exchange.setException(e);
            }

            return exchange;
        }
    }
}
