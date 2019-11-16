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
import java.util.function.Function;
import java.util.function.Supplier;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.vavr.control.Try;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Navigate;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeExchangeException;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.spi.IdAware;
import org.apache.camel.support.AsyncProcessorSupport;
import org.apache.camel.support.ExchangeHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of Circuit Breaker EIP using resilience4j.
 */
@ManagedResource(description = "Managed Resilience Processor")
public class ResilienceProcessor extends AsyncProcessorSupport implements Navigate<Processor>, org.apache.camel.Traceable, IdAware {

    private static final Logger LOG = LoggerFactory.getLogger(ResilienceProcessor.class);

    private String id;
    private final Processor processor;
    private final Processor fallback;
    private final boolean fallbackViaNetwork;

    public ResilienceProcessor(Processor processor, Processor fallback, boolean fallbackViaNetwork) {
        this.processor = processor;
        this.fallback = fallback;
        this.fallbackViaNetwork = fallbackViaNetwork;
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
    public String getTraceLabel() {
        return "resilience4j";
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
        // run this as if we run inside try .. catch so there is no regular Camel error handler
        exchange.setProperty(Exchange.TRY_ROUTE_BLOCK, true);

//        Supplier<CompletableFuture<String>> futureSupplier = () -> CompletableFuture.supplyAsync(() -> "Hello");
//        Callable<String> callable = TimeLimiter.decorateFutureSupplier(TimeLimiter.of(Duration.ofMillis(500)), futureSupplier);
//        String result = CircuitBreaker.decorateCheckedSupplier(cb, callable::call).apply();

//        Bulkhead bh = Bulkhead.ofDefaults("ddd");
//        BulkheadConfig.

//                TimeLimiter time = TimeLimiter.of(Duration.ofSeconds(1));
//        Supplier<Future<Exchange>> task2 = time.decorateFutureSupplier(() -> {
//            task.get();
//            Future
//        });

        CircuitBreaker cb = CircuitBreaker.ofDefaults(id);
        Supplier<Exchange> task = CircuitBreaker.decorateSupplier(cb, new CircuitBreakerTask(processor, exchange));
        Try.ofSupplier(task)
                .recover(new CircuitBreakerFallbackTask(fallback, exchange))
                .andFinally(() -> callback.done(false)).get();

        return false;
    }

    @Override
    protected void doStart() throws Exception {
        // noop
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }

    private static class CircuitBreakerTask implements Supplier<Exchange> {

        private final Processor processor;
        private final Exchange exchange;

        private CircuitBreakerTask(Processor processor, Exchange exchange) {
            this.processor = processor;
            this.exchange = exchange;
        }

        @Override
        public Exchange get() {
            try {
                LOG.debug("Running processor: {} with exchange: {}", processor, exchange);
                // prepare a copy of exchange so downstream processors don't cause side-effects if they mutate the exchange
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

    private static class CircuitBreakerFallbackTask implements Function<Throwable, Exchange> {

        private final Processor processor;
        private final Exchange exchange;

        private CircuitBreakerFallbackTask(Processor processor, Exchange exchange) {
            this.processor = processor;
            this.exchange = exchange;
        }

        @Override
        public Exchange apply(Throwable throwable) {
            if (processor != null) {
                // store the last to endpoint as the failure endpoint
                if (exchange.getProperty(Exchange.FAILURE_ENDPOINT) == null) {
                    exchange.setProperty(Exchange.FAILURE_ENDPOINT, exchange.getProperty(Exchange.TO_ENDPOINT));
                }
                // give the rest of the pipeline another chance
                exchange.setProperty(Exchange.EXCEPTION_HANDLED, true);
                exchange.setProperty(Exchange.EXCEPTION_CAUGHT, exchange.getException());
                exchange.removeProperty(Exchange.ROUTE_STOP);
                exchange.setException(null);
                // and we should not be regarded as exhausted as we are in a try .. catch block
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

                exchange.setProperty(CircuitBreakerConstants.RESPONSE_SUCCESSFUL_EXECUTION, false);
                exchange.setProperty(CircuitBreakerConstants.RESPONSE_FROM_FALLBACK, true);
            }

            return exchange;
        }
    }

}
