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
package org.apache.camel.component.microprofile.faulttolerance;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import io.smallrye.faulttolerance.core.FaultToleranceStrategy;
import io.smallrye.faulttolerance.core.InvocationContext;
import io.smallrye.faulttolerance.core.bulkhead.ThreadPoolBulkhead;
import io.smallrye.faulttolerance.core.circuit.breaker.CircuitBreaker;
import io.smallrye.faulttolerance.core.fallback.Fallback;
import io.smallrye.faulttolerance.core.stopwatch.SystemStopwatch;
import io.smallrye.faulttolerance.core.timeout.ScheduledExecutorTimeoutWatcher;
import io.smallrye.faulttolerance.core.timeout.Timeout;
import io.smallrye.faulttolerance.core.timeout.TimeoutWatcher;
import io.smallrye.faulttolerance.core.util.SetOfThrowables;
import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.ExtendedExchange;
import org.apache.camel.Navigate;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeExchangeException;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.spi.CircuitBreakerConstants;
import org.apache.camel.spi.IdAware;
import org.apache.camel.support.AsyncProcessorSupport;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.util.ObjectHelper;
import org.eclipse.microprofile.faulttolerance.exceptions.CircuitBreakerOpenException;
import org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.smallrye.faulttolerance.core.Invocation.invocation;

/**
 * Implementation of Circuit Breaker EIP using microprofile fault tolerance.
 */
@ManagedResource(description = "Managed FaultTolerance Processor")
public class FaultToleranceProcessor extends AsyncProcessorSupport
        implements CamelContextAware, Navigate<Processor>, org.apache.camel.Traceable, IdAware {

    private static final Logger LOG = LoggerFactory.getLogger(FaultToleranceProcessor.class);

    private volatile CircuitBreaker circuitBreaker;
    private CamelContext camelContext;
    private String id;
    private final FaultToleranceConfiguration config;
    private final Processor processor;
    private final Processor fallbackProcessor;
    private ScheduledExecutorService scheduledExecutorService;
    private boolean shutdownScheduledExecutorService;
    private ExecutorService executorService;
    private boolean shutdownExecutorService;

    public FaultToleranceProcessor(FaultToleranceConfiguration config, Processor processor,
                                   Processor fallbackProcessor) {
        this.config = config;
        this.processor = processor;
        this.fallbackProcessor = fallbackProcessor;
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
        return "faultTolerance";
    }

    @ManagedAttribute(description = "Returns the current delay in milliseconds.")
    public long getDelay() {
        return config.getDelay();
    }

    @ManagedAttribute(description = "Returns the current failure rate in percentage.")
    public float getFailureRate() {
        return config.getFailureRatio();
    }

    @ManagedAttribute(description = "Returns the current request volume threshold.")
    public int getRequestVolumeThreshold() {
        return config.getRequestVolumeThreshold();
    }

    @ManagedAttribute(description = "Returns the current success threshold.")
    public int getSuccessThreshold() {
        return config.getSuccessThreshold();
    }

    @ManagedAttribute(description = "Is timeout enabled")
    public boolean isTimeoutEnabled() {
        return config.isTimeoutEnabled();
    }

    @ManagedAttribute(description = "The timeout wait duration")
    public long getTimeoutDuration() {
        return config.getTimeoutDuration();
    }

    @ManagedAttribute(description = "The timeout pool size for the thread pool")
    public int getTimeoutPoolSize() {
        return config.getTimeoutPoolSize();
    }

    @ManagedAttribute(description = "Is bulkhead enabled")
    public boolean isBulkheadEnabled() {
        return config.isBulkheadEnabled();
    }

    @ManagedAttribute(description = "The max amount of concurrent calls the bulkhead will support.")
    public int getBulkheadMaxConcurrentCalls() {
        return config.getBulkheadMaxConcurrentCalls();
    }

    @ManagedAttribute(description = "The task queue size for holding waiting tasks to be processed by the bulkhead")
    public int getBulkheadWaitingTaskQueue() {
        return config.getBulkheadWaitingTaskQueue();
    }

    @Override
    public List<Processor> next() {
        if (!hasNext()) {
            return null;
        }
        List<Processor> answer = new ArrayList<>();
        answer.add(processor);
        if (fallbackProcessor != null) {
            answer.add(fallbackProcessor);
        }
        return answer;
    }

    @Override
    public boolean hasNext() {
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean process(Exchange exchange, AsyncCallback callback) {
        // run this as if we run inside try .. catch so there is no regular
        // Camel error handler
        exchange.setProperty(Exchange.TRY_ROUTE_BLOCK, true);

        Callable<Exchange> task = new CircuitBreakerTask(processor, exchange);

        // circuit breaker
        FaultToleranceStrategy target = circuitBreaker;

        // 1. bulkhead
        if (config.isBulkheadEnabled()) {
            target = new ThreadPoolBulkhead(
                    target, "bulkhead", executorService, config.getBulkheadMaxConcurrentCalls(),
                    config.getBulkheadWaitingTaskQueue(), null);
        }
        // 2. timeout
        if (config.isTimeoutEnabled()) {
            TimeoutWatcher watcher = new ScheduledExecutorTimeoutWatcher(scheduledExecutorService);
            target = new Timeout(target, "timeout", config.getTimeoutDuration(), watcher, null);
        }
        // 3. fallback
        if (fallbackProcessor != null) {
            Callable fallbackTask = new CircuitBreakerFallbackTask(fallbackProcessor, exchange);
            target = new Fallback(target, "fallback", fallbackContext -> {
                exchange.setException(fallbackContext.failure);
                return fallbackTask.call();
            }, SetOfThrowables.ALL, SetOfThrowables.EMPTY, null);
        }

        try {
            target.apply(new InvocationContext(task));
        } catch (CircuitBreakerOpenException e) {
            // the circuit breaker triggered a call rejected
            exchange.setProperty(CircuitBreakerConstants.RESPONSE_SUCCESSFUL_EXECUTION, false);
            exchange.setProperty(CircuitBreakerConstants.RESPONSE_FROM_FALLBACK, false);
            exchange.setProperty(CircuitBreakerConstants.RESPONSE_SHORT_CIRCUITED, true);
            exchange.setProperty(CircuitBreakerConstants.RESPONSE_REJECTED, true);
        } catch (Throwable e) {
            // some other kind of exception
            exchange.setException(e);
        }

        exchange.removeProperty(Exchange.TRY_ROUTE_BLOCK);
        callback.done(true);
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void doInit() throws Exception {
        ObjectHelper.notNull(camelContext, "CamelContext", this);
        if (circuitBreaker == null) {
            circuitBreaker = new CircuitBreaker(
                    invocation(), id, SetOfThrowables.ALL,
                    SetOfThrowables.EMPTY, config.getDelay(), config.getRequestVolumeThreshold(), config.getFailureRatio(),
                    config.getSuccessThreshold(), new SystemStopwatch(), null);
        }
    }

    @Override
    protected void doStart() throws Exception {
        if (config.isTimeoutEnabled() && scheduledExecutorService == null) {
            scheduledExecutorService = getCamelContext().getExecutorServiceManager().newScheduledThreadPool(this,
                    "CircuitBreakerTimeout", config.getTimeoutPoolSize());
            shutdownScheduledExecutorService = true;
        }
        if (config.isBulkheadEnabled() && executorService == null) {
            executorService = getCamelContext().getExecutorServiceManager().newThreadPool(this, "CircuitBreakerBulkhead",
                    config.getBulkheadMaxConcurrentCalls(), config.getBulkheadMaxConcurrentCalls());
            shutdownExecutorService = true;
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (shutdownScheduledExecutorService && scheduledExecutorService != null) {
            getCamelContext().getExecutorServiceManager().shutdownNow(scheduledExecutorService);
            scheduledExecutorService = null;
        }
        if (shutdownExecutorService && executorService != null) {
            getCamelContext().getExecutorServiceManager().shutdownNow(executorService);
            executorService = null;
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
            // turn of interruption to allow fault tolerance to process the exchange under its handling
            exchange.adapt(ExtendedExchange.class).setInterruptable(false);

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
                // force exception so the circuit breaker can react
                throw exchange.getException();
            }
            return exchange;
        }
    }

    private static final class CircuitBreakerFallbackTask implements Callable<Exchange> {

        private final Processor processor;
        private final Exchange exchange;

        private CircuitBreakerFallbackTask(Processor processor, Exchange exchange) {
            this.processor = processor;
            this.exchange = exchange;
        }

        @Override
        public Exchange call() throws Exception {
            Throwable throwable = exchange.getException();
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
                } else if (throwable instanceof CircuitBreakerOpenException) {
                    // the circuit breaker triggered a call rejected
                    exchange.setProperty(CircuitBreakerConstants.RESPONSE_SUCCESSFUL_EXECUTION, false);
                    exchange.setProperty(CircuitBreakerConstants.RESPONSE_FROM_FALLBACK, false);
                    exchange.setProperty(CircuitBreakerConstants.RESPONSE_SHORT_CIRCUITED, true);
                    exchange.setProperty(CircuitBreakerConstants.RESPONSE_REJECTED, true);
                    return exchange;
                } else {
                    // throw exception so fault tolerance know it was a failure
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
            exchange.adapt(ExtendedExchange.class).setRedeliveryExhausted(false);
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

}
