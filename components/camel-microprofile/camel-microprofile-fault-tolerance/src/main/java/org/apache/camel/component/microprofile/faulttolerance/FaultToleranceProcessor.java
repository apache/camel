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

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

import io.smallrye.faulttolerance.api.CircuitBreakerMaintenance;
import io.smallrye.faulttolerance.api.CircuitBreakerState;
import io.smallrye.faulttolerance.api.TypedGuard;
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
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.processor.BaseProcessorSupport;
import org.apache.camel.processor.PooledExchangeTask;
import org.apache.camel.processor.PooledExchangeTaskFactory;
import org.apache.camel.processor.PooledTaskFactory;
import org.apache.camel.processor.PrototypeTaskFactory;
import org.apache.camel.spi.IdAware;
import org.apache.camel.spi.ProcessorExchangeFactory;
import org.apache.camel.spi.RouteIdAware;
import org.apache.camel.spi.UnitOfWork;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.UnitOfWorkHelper;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.ObjectHelper;
import org.eclipse.microprofile.faulttolerance.exceptions.CircuitBreakerOpenException;
import org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of Circuit Breaker EIP using microprofile fault tolerance.
 */
@ManagedResource(description = "Managed FaultTolerance Processor")
public class FaultToleranceProcessor extends BaseProcessorSupport
        implements CamelContextAware, Navigate<Processor>, org.apache.camel.Traceable, IdAware, RouteIdAware {

    private static final Logger LOG = LoggerFactory.getLogger(FaultToleranceProcessor.class);

    private CamelContext camelContext;
    private String id;
    private String routeId;
    private final FaultToleranceConfiguration config;
    private final Processor processor;
    private final Processor fallbackProcessor;
    private ExecutorService executorService;
    private boolean shutdownExecutorService;
    private ProcessorExchangeFactory processorExchangeFactory;
    private PooledExchangeTaskFactory taskFactory;
    private PooledExchangeTaskFactory fallbackTaskFactory;
    private TypedGuard.Builder<Exchange> typedGuardBuilder;
    private TypedGuard<Exchange> typedGuard;

    public FaultToleranceProcessor(
            FaultToleranceConfiguration config, Processor processor, Processor fallbackProcessor) {
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

    @Override
    public String getRouteId() {
        return routeId;
    }

    @Override
    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }

    public TypedGuard<Exchange> getTypedGuard() {
        return typedGuard;
    }

    public void setTypedGuard(TypedGuard<Exchange> typedGuard) {
        this.typedGuard = typedGuard;
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

    @ManagedAttribute(description = "Returns the current state of the circuit breaker")
    public String getCircuitBreakerState() {
        try {
            CircuitBreakerState circuitBreakerState =
                    CircuitBreakerMaintenance.get().currentState(id);
            return circuitBreakerState.name();
        } catch (Exception e) {
            return null;
        }
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
        // run this as if we run inside try / catch so there is no regular Camel error handler
        exchange.setProperty(ExchangePropertyKey.TRY_ROUTE_BLOCK, true);
        CircuitBreakerTask task = (CircuitBreakerTask) taskFactory.acquire(exchange, callback);
        CircuitBreakerFallbackTask fallbackTask = null;

        try {
            // Run the fault-tolerant task within the guard
            try {
                typedGuard.call(task);
            } catch (Exception e) {
                // Do fallback if applicable. Note that a fallback handler is not configured on the TypedGuard builder
                // and is instead invoked manually here since we need access to the message exchange on each
                // FaultToleranceProcessor.process call
                if (fallbackProcessor != null) {
                    fallbackTask = (CircuitBreakerFallbackTask) fallbackTaskFactory.acquire(exchange, null);
                    fallbackTask.call();
                } else {
                    throw e;
                }
            }
        } catch (CircuitBreakerOpenException e) {
            // the circuit breaker triggered a call rejected
            exchange.setProperty(ExchangePropertyKey.CIRCUIT_BREAKER_RESPONSE_SUCCESSFUL_EXECUTION, false);
            exchange.setProperty(ExchangePropertyKey.CIRCUIT_BREAKER_RESPONSE_FROM_FALLBACK, false);
            exchange.setProperty(ExchangePropertyKey.CIRCUIT_BREAKER_RESPONSE_SHORT_CIRCUITED, true);
            exchange.setProperty(ExchangePropertyKey.CIRCUIT_BREAKER_RESPONSE_REJECTED, true);
        } catch (Exception e) {
            // some other kind of exception
            exchange.setException(e);
        } finally {
            if (task != null) {
                taskFactory.release(task);
            }

            if (fallbackTask != null) {
                fallbackTaskFactory.release(fallbackTask);
            }
        }

        exchange.removeProperty(ExchangePropertyKey.TRY_ROUTE_BLOCK);
        callback.done(true);
        return true;
    }

    @Override
    protected void doBuild() throws Exception {
        ObjectHelper.notNull(camelContext, "CamelContext", this);

        boolean pooled =
                camelContext.getCamelContextExtension().getExchangeFactory().isPooled();
        if (pooled) {
            int capacity =
                    camelContext.getCamelContextExtension().getExchangeFactory().getCapacity();
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
        this.processorExchangeFactory = getCamelContext()
                .getCamelContextExtension()
                .getProcessorExchangeFactory()
                .newProcessorExchangeFactory(this);
        this.processorExchangeFactory.setRouteId(getRouteId());
        this.processorExchangeFactory.setId(getId());

        ServiceHelper.buildService(processorExchangeFactory, taskFactory, fallbackTaskFactory, processor);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void doInit() throws Exception {
        ObjectHelper.notNull(camelContext, "CamelContext", this);

        ServiceHelper.initService(processorExchangeFactory, taskFactory, fallbackTaskFactory, processor);

        if (typedGuard == null) {
            typedGuardBuilder = TypedGuard.create(Exchange.class)
                    .withThreadOffload(true)
                    .withCircuitBreaker()
                    .name(id)
                    .delay(config.getDelay(), ChronoUnit.MILLIS)
                    .failureRatio(config.getFailureRatio())
                    .requestVolumeThreshold(config.getRequestVolumeThreshold())
                    .successThreshold(config.getSuccessThreshold())
                    .done();

            if (config.isTimeoutEnabled()) {
                typedGuardBuilder
                        .withTimeout()
                        .duration(config.getTimeoutDuration(), ChronoUnit.MILLIS)
                        .done();
            }

            if (config.isBulkheadEnabled()) {
                typedGuardBuilder
                        .withBulkhead()
                        .queueSize(config.getBulkheadWaitingTaskQueue())
                        .limit(config.getBulkheadMaxConcurrentCalls())
                        .done();
            }
        }
    }

    @Override
    protected void doStart() throws Exception {
        if (typedGuard == null) {
            if (executorService == null) {
                executorService = getCamelContext()
                        .getExecutorServiceManager()
                        .newCachedThreadPool(this, "CamelMicroProfileFaultTolerance");
                shutdownExecutorService = true;
            }
            typedGuardBuilder.withThreadOffloadExecutor(executorService);
            typedGuard = typedGuardBuilder.build();
        }

        ServiceHelper.startService(processorExchangeFactory, taskFactory, fallbackTaskFactory, processor);
    }

    @Override
    protected void doStop() throws Exception {
        if (shutdownExecutorService && executorService != null) {
            getCamelContext().getExecutorServiceManager().shutdownNow(executorService);
            executorService = null;
        }
        ServiceHelper.stopService(processorExchangeFactory, taskFactory, fallbackTaskFactory, processor);

        try {
            CircuitBreakerMaintenance.get().reset(id);
        } catch (Exception e) {
            // Ignored - CircuitBreaker does not exist
        }
    }

    @Override
    protected void doShutdown() throws Exception {
        ServiceHelper.stopAndShutdownServices(processorExchangeFactory, taskFactory, fallbackTaskFactory, processor);
    }

    private final class CircuitBreakerTask implements PooledExchangeTask, Callable<Exchange> {

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
            Exchange copy = null;
            UnitOfWork uow = null;
            Throwable cause;

            // turn of interruption to allow fault tolerance to process the exchange under its handling
            exchange.getExchangeExtension().setInterruptable(false);

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
                    // copy the result as it's regarded as success
                    ExchangeHelper.copyResults(exchange, copy);
                    exchange.setProperty(ExchangePropertyKey.CIRCUIT_BREAKER_RESPONSE_SUCCESSFUL_EXECUTION, true);
                    exchange.setProperty(ExchangePropertyKey.CIRCUIT_BREAKER_RESPONSE_FROM_FALLBACK, false);
                    String state = getCircuitBreakerState();
                    if (state != null) {
                        exchange.setProperty(ExchangePropertyKey.CIRCUIT_BREAKER_RESPONSE_STATE, state);
                    }
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
                // throw exception so fault tolerance knows it was a failure
                throw RuntimeExchangeException.wrapRuntimeException(cause);
            }
            return exchange;
        }
    }

    private final class CircuitBreakerFallbackTask implements PooledExchangeTask, Callable<Exchange> {

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
            String state = getCircuitBreakerState();
            if (state != null) {
                exchange.setProperty(ExchangePropertyKey.CIRCUIT_BREAKER_RESPONSE_STATE, state);
            }

            Throwable throwable = exchange.getException();
            if (fallbackProcessor == null) {
                if (throwable instanceof TimeoutException) {
                    // the circuit breaker triggered a timeout (and there is no
                    // fallback) so lets mark the exchange as failed
                    exchange.setProperty(ExchangePropertyKey.CIRCUIT_BREAKER_RESPONSE_SUCCESSFUL_EXECUTION, false);
                    exchange.setProperty(ExchangePropertyKey.CIRCUIT_BREAKER_RESPONSE_FROM_FALLBACK, false);
                    exchange.setProperty(ExchangePropertyKey.CIRCUIT_BREAKER_RESPONSE_SHORT_CIRCUITED, false);
                    exchange.setProperty(ExchangePropertyKey.CIRCUIT_BREAKER_RESPONSE_TIMED_OUT, true);
                    exchange.setException(throwable);
                    return exchange;
                } else if (throwable instanceof CircuitBreakerOpenException) {
                    // the circuit breaker triggered a call rejected
                    exchange.setProperty(ExchangePropertyKey.CIRCUIT_BREAKER_RESPONSE_SUCCESSFUL_EXECUTION, false);
                    exchange.setProperty(ExchangePropertyKey.CIRCUIT_BREAKER_RESPONSE_FROM_FALLBACK, false);
                    exchange.setProperty(ExchangePropertyKey.CIRCUIT_BREAKER_RESPONSE_SHORT_CIRCUITED, true);
                    exchange.setProperty(ExchangePropertyKey.CIRCUIT_BREAKER_RESPONSE_REJECTED, true);
                    return exchange;
                } else {
                    // throw exception so fault tolerance know it was a failure
                    throw RuntimeExchangeException.wrapRuntimeException(throwable);
                }
            }

            // fallback route is handling the exception so its short-circuited
            exchange.setProperty(ExchangePropertyKey.CIRCUIT_BREAKER_RESPONSE_SUCCESSFUL_EXECUTION, false);
            exchange.setProperty(ExchangePropertyKey.CIRCUIT_BREAKER_RESPONSE_FROM_FALLBACK, true);
            exchange.setProperty(ExchangePropertyKey.CIRCUIT_BREAKER_RESPONSE_SHORT_CIRCUITED, true);

            // store the last to endpoint as the failure endpoint
            if (exchange.getProperty(ExchangePropertyKey.FAILURE_ENDPOINT) == null) {
                exchange.setProperty(
                        ExchangePropertyKey.FAILURE_ENDPOINT, exchange.getProperty(ExchangePropertyKey.TO_ENDPOINT));
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
                LOG.debug("Running fallback: {} with exchange: {}", fallbackProcessor, exchange);
                // process the fallback until its fully done
                fallbackProcessor.process(exchange);
                LOG.debug("Running fallback: {} with exchange: {} done", fallbackProcessor, exchange);
            } catch (Exception e) {
                exchange.setException(e);
            }

            return exchange;
        }
    }
}
