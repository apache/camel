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
import org.apache.camel.Navigate;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeExchangeException;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.spi.CircuitBreakerConstants;
import org.apache.camel.spi.IdAware;
import org.apache.camel.support.AsyncProcessorSupport;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of Circuit Breaker EIP using resilience4j.
 */
@ManagedResource(description = "Managed Resilience Processor")
public class ResilienceProcessor extends AsyncProcessorSupport implements CamelContextAware, Navigate<Processor>, org.apache.camel.Traceable, IdAware {

    private static final Logger LOG = LoggerFactory.getLogger(ResilienceProcessor.class);

    private volatile CircuitBreaker circuitBreaker;
    private CamelContext camelContext;
    private String id;
    private final CircuitBreakerConfig circuitBreakerConfig;
    private final BulkheadConfig bulkheadConfig;
    private final TimeLimiterConfig timeLimiterConfig;
    private final Processor processor;
    private final Processor fallback;
    private boolean shutdownExecutorService;
    private ExecutorService executorService;

    public ResilienceProcessor(CircuitBreakerConfig circuitBreakerConfig, BulkheadConfig bulkheadConfig, TimeLimiterConfig timeLimiterConfig, Processor processor,
                               Processor fallback) {
        this.circuitBreakerConfig = circuitBreakerConfig;
        this.bulkheadConfig = bulkheadConfig;
        this.timeLimiterConfig = timeLimiterConfig;
        this.processor = processor;
        this.fallback = fallback;
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
        return circuitBreakerConfig.getWaitDurationInOpenState().getSeconds();
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
        exchange.setProperty(Exchange.TRY_ROUTE_BLOCK, true);

        Callable<Exchange> task = CircuitBreaker.decorateCallable(circuitBreaker, new CircuitBreakerTask(processor, exchange));
        Function<Throwable, Exchange> fallbackTask = new CircuitBreakerFallbackTask(fallback, exchange);
        if (bulkheadConfig != null) {
            Bulkhead bh = Bulkhead.of(id, bulkheadConfig);
            task = Bulkhead.decorateCallable(bh, task);
        }

        if (timeLimiterConfig != null) {
            // timeout handling is more complex with thread-pools
            final CircuitBreakerTimeoutTask timeoutTask = new CircuitBreakerTimeoutTask(task, exchange);
            Supplier<CompletableFuture<Exchange>> futureSupplier;
            if (executorService == null) {
                futureSupplier = () -> CompletableFuture.supplyAsync(timeoutTask::get);
            } else {
                futureSupplier = () -> CompletableFuture.supplyAsync(timeoutTask::get, executorService);
            }

            TimeLimiter tl = TimeLimiter.of(id, timeLimiterConfig);
            task = TimeLimiter.decorateFutureSupplier(tl, futureSupplier);
        }

        Try.ofCallable(task).recover(fallbackTask).andFinally(() -> callback.done(false)).get();

        return false;
    }

    @Override
    protected void doStart() throws Exception {
        ObjectHelper.notNull(camelContext, "CamelContext", this);
        if (circuitBreaker == null) {
            circuitBreaker = CircuitBreaker.of(id, circuitBreakerConfig);
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (shutdownExecutorService && executorService != null) {
            getCamelContext().getExecutorServiceManager().shutdownNow(executorService);
        }
    }

    private static final class CircuitBreakerTask implements Callable<Exchange> {

        private final Processor processor;
        private final Exchange exchange;

        private CircuitBreakerTask(Processor processor, Exchange exchange) {
            this.processor = processor;
            this.exchange = exchange;
        }

        @Override
        public Exchange call() throws Exception {
            try {
                LOG.debug("Running processor: {} with exchange: {}", processor, exchange);
                // prepare a copy of exchange so downstream processors don't
                // cause side-effects if they mutate the exchange
                // in case timeout processing and continue with the fallback etc
                Exchange copy = ExchangeHelper.createCorrelatedCopy(exchange, false, false);
                // process the processor until its fully done
                processor.process(copy);
                if (copy.getException() != null) {
                    exchange.setException(copy.getException());
                } else {
                    // copy the result as its regarded as success
                    ExchangeHelper.copyResults(exchange, copy);
                    exchange.setProperty(CircuitBreakerConstants.RESPONSE_SUCCESSFUL_EXECUTION, true);
                    exchange.setProperty(CircuitBreakerConstants.RESPONSE_FROM_FALLBACK, false);
                }
            } catch (Throwable e) {
                exchange.setException(e);
            }
            if (exchange.getException() != null) {
                // throw exception so resilient4j know it was a failure
                throw RuntimeExchangeException.wrapRuntimeException(exchange.getException());
            }
            return exchange;
        }
    }

    private static final class CircuitBreakerFallbackTask implements Function<Throwable, Exchange> {

        private final Processor processor;
        private final Exchange exchange;

        private CircuitBreakerFallbackTask(Processor processor, Exchange exchange) {
            this.processor = processor;
            this.exchange = exchange;
        }

        @Override
        public Exchange apply(Throwable throwable) {
            if (processor == null) {
                if (throwable instanceof TimeoutException) {
                    // the circuit breaker triggered a timeout (and there is no
                    // fallback) so lets mark the exchange as failed
                    exchange.setProperty(CircuitBreakerConstants.RESPONSE_SUCCESSFUL_EXECUTION, false);
                    exchange.setProperty(CircuitBreakerConstants.RESPONSE_FROM_FALLBACK, false);
                    exchange.setProperty(CircuitBreakerConstants.RESPONSE_SHORT_CIRCUITED, false);
                    exchange.setProperty(CircuitBreakerConstants.RESPONSE_TIMED_OUT, true);
                    exchange.setException(throwable);
                    return exchange;
                } else if (throwable instanceof CallNotPermittedException) {
                    // the circuit breaker triggered a call rejected
                    exchange.setProperty(CircuitBreakerConstants.RESPONSE_SUCCESSFUL_EXECUTION, false);
                    exchange.setProperty(CircuitBreakerConstants.RESPONSE_FROM_FALLBACK, false);
                    exchange.setProperty(CircuitBreakerConstants.RESPONSE_SHORT_CIRCUITED, true);
                    exchange.setProperty(CircuitBreakerConstants.RESPONSE_REJECTED, true);
                    return exchange;
                } else {
                    // throw exception so resilient4j know it was a failure
                    throw RuntimeExchangeException.wrapRuntimeException(throwable);
                }
            }

            // fallback route is handling the exception so its short-circuited
            exchange.setProperty(CircuitBreakerConstants.RESPONSE_SUCCESSFUL_EXECUTION, false);
            exchange.setProperty(CircuitBreakerConstants.RESPONSE_FROM_FALLBACK, true);
            exchange.setProperty(CircuitBreakerConstants.RESPONSE_SHORT_CIRCUITED, true);

            // store the last to endpoint as the failure endpoint
            if (exchange.getProperty(Exchange.FAILURE_ENDPOINT) == null) {
                exchange.setProperty(Exchange.FAILURE_ENDPOINT, exchange.getProperty(Exchange.TO_ENDPOINT));
            }
            // give the rest of the pipeline another chance
            exchange.setProperty(Exchange.EXCEPTION_HANDLED, true);
            exchange.setProperty(Exchange.EXCEPTION_CAUGHT, exchange.getException());
            exchange.setRouteStop(false);
            exchange.setException(null);
            // and we should not be regarded as exhausted as we are in a try ..
            // catch block
            exchange.removeProperty(Exchange.REDELIVERY_EXHAUSTED);
            // run the fallback processor
            try {
                LOG.debug("Running fallback: {} with exchange: {}", processor, exchange);
                // process the fallback until its fully done
                processor.process(exchange);
                LOG.debug("Running fallback: {} with exchange: {} done", processor, exchange);
            } catch (Exception e) {
                exchange.setException(e);
            }

            return exchange;
        }
    }

    private static final class CircuitBreakerTimeoutTask implements Supplier<Exchange> {

        private final Callable<Exchange> future;
        private final Exchange exchange;

        private CircuitBreakerTimeoutTask(Callable<Exchange> future, Exchange exchange) {
            this.future = future;
            this.exchange = exchange;
        }

        @Override
        public Exchange get() {
            try {
                return future.call();
            } catch (Exception e) {
                exchange.setException(e);
            }
            return exchange;
        }
    }

}
