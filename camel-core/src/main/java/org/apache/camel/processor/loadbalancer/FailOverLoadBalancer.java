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
package org.apache.camel.processor.loadbalancer;

import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Traceable;
import org.apache.camel.util.AsyncProcessorConverterHelper;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * This FailOverLoadBalancer will failover to use next processor when an exception occurred
 * <p/>
 * This implementation mirrors the logic from the {@link org.apache.camel.processor.Pipeline} in the async variation
 * as the failover load balancer is a specialized pipeline. So the trick is to keep doing the same as the
 * pipeline to ensure it works the same and the async routing engine is flawless.
 */
public class FailOverLoadBalancer extends LoadBalancerSupport implements Traceable, CamelContextAware {

    private final List<Class<?>> exceptions;
    private CamelContext camelContext;
    private boolean roundRobin;
    private boolean sticky;
    private int maximumFailoverAttempts = -1;

    // stateful statistics
    private final AtomicInteger counter = new AtomicInteger(-1);
    private final AtomicInteger lastGoodIndex = new AtomicInteger(-1);
    private final ExceptionFailureStatistics statistics = new ExceptionFailureStatistics();

    public FailOverLoadBalancer() {
        this.exceptions = null;
    }

    public FailOverLoadBalancer(List<Class<?>> exceptions) {
        this.exceptions = exceptions;

        // validate its all exception types
        for (Class<?> type : exceptions) {
            if (!ObjectHelper.isAssignableFrom(Throwable.class, type)) {
                throw new IllegalArgumentException("Class is not an instance of Throwable: " + type);
            }
        }

        statistics.init(exceptions);
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public int getLastGoodIndex() {
        return lastGoodIndex.get();
    }

    public List<Class<?>> getExceptions() {
        return exceptions;
    }

    public boolean isRoundRobin() {
        return roundRobin;
    }

    public void setRoundRobin(boolean roundRobin) {
        this.roundRobin = roundRobin;
    }

    public boolean isSticky() {
        return sticky;
    }

    public void setSticky(boolean sticky) {
        this.sticky = sticky;
    }

    public int getMaximumFailoverAttempts() {
        return maximumFailoverAttempts;
    }

    public void setMaximumFailoverAttempts(int maximumFailoverAttempts) {
        this.maximumFailoverAttempts = maximumFailoverAttempts;
    }

    /**
     * Should the given failed Exchange failover?
     *
     * @param exchange the exchange that failed
     * @return <tt>true</tt> to failover
     */
    protected boolean shouldFailOver(Exchange exchange) {
        if (exchange == null) {
            return false;
        }

        boolean answer = false;

        if (exchange.getException() != null) {
            if (exceptions == null || exceptions.isEmpty()) {
                // always failover if no exceptions defined
                answer = true;
            } else {
                for (Class<?> exception : exceptions) {
                    // will look in exception hierarchy
                    if (exchange.getException(exception) != null) {
                        answer = true;
                        break;
                    }
                }
            }

            if (answer) {
                // record the failure in the statistics
                statistics.onHandledFailure(exchange.getException());
            }
        }

        log.trace("Should failover: {} for exchangeId: {}", answer, exchange.getExchangeId());

        return answer;
    }

    @Override
    public boolean isRunAllowed() {
        // determine if we can still run, or the camel context is forcing a shutdown
        boolean forceShutdown = camelContext.getShutdownStrategy().forceShutdown(this);
        if (forceShutdown) {
            log.trace("Run not allowed as ShutdownStrategy is forcing shutting down");
        }
        return !forceShutdown && super.isRunAllowed();
    }

    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        final List<Processor> processors = getProcessors();

        final AtomicInteger index = new AtomicInteger();
        final AtomicInteger attempts = new AtomicInteger();
        boolean first = true;
        // use a copy of the original exchange before failover to avoid populating side effects
        // directly into the original exchange
        Exchange copy = null;

        // get the next processor
        if (isSticky()) {
            int idx = lastGoodIndex.get();
            if (idx == -1) {
                idx = 0;
            }
            index.set(idx);
        } else if (isRoundRobin()) {
            if (counter.incrementAndGet() >= processors.size()) {
                counter.set(0);
            }
            index.set(counter.get());
        }
        log.trace("Failover starting with endpoint index {}", index);

        while (first || shouldFailOver(copy)) {

            // can we still run
            if (!isRunAllowed()) {
                log.trace("Run not allowed, will reject executing exchange: {}", exchange);
                if (exchange.getException() == null) {
                    exchange.setException(new RejectedExecutionException());
                }
                // we cannot process so invoke callback
                callback.done(true);
                return true;
            }

            if (!first) {
                attempts.incrementAndGet();
                // are we exhausted by attempts?
                if (maximumFailoverAttempts > -1 && attempts.get() > maximumFailoverAttempts) {
                    log.debug("Breaking out of failover after {} failover attempts", attempts);
                    break;
                }

                index.incrementAndGet();
                counter.incrementAndGet();
            } else {
                // flip first switch
                first = false;
            }

            if (index.get() >= processors.size()) {
                // out of bounds
                if (isRoundRobin()) {
                    log.trace("Failover is round robin enabled and therefore starting from the first endpoint");
                    index.set(0);
                    counter.set(0);
                } else {
                    // no more processors to try
                    log.trace("Breaking out of failover as we reached the end of endpoints to use for failover");
                    break;
                }
            }

            // try again but copy original exchange before we failover
            copy = prepareExchangeForFailover(exchange);
            Processor processor = processors.get(index.get());

            // process the exchange
            boolean sync = processExchange(processor, exchange, copy, attempts, index, callback, processors);

            // continue as long its being processed synchronously
            if (!sync) {
                log.trace("Processing exchangeId: {} is continued being processed asynchronously", exchange.getExchangeId());
                // the remainder of the failover will be completed async
                // so we break out now, then the callback will be invoked which then continue routing from where we left here
                return false;
            }

            log.trace("Processing exchangeId: {} is continued being processed synchronously", exchange.getExchangeId());
        }

        // remember last good index
        lastGoodIndex.set(index.get());

        // and copy the current result to original so it will contain this result of this eip
        if (copy != null) {
            ExchangeHelper.copyResults(exchange, copy);
        }
        log.debug("Failover complete for exchangeId: {} >>> {}", exchange.getExchangeId(), exchange);
        callback.done(true);
        return true;
    }

    /**
     * Prepares the exchange for failover
     *
     * @param exchange the exchange
     * @return a copy of the exchange to use for failover
     */
    protected Exchange prepareExchangeForFailover(Exchange exchange) {
        // use a copy of the exchange to avoid side effects on the original exchange
        return ExchangeHelper.createCopy(exchange, true);
    }

    private boolean processExchange(Processor processor, Exchange exchange, Exchange copy,
                                    AtomicInteger attempts, AtomicInteger index,
                                    AsyncCallback callback, List<Processor> processors) {
        if (processor == null) {
            throw new IllegalStateException("No processors could be chosen to process " + copy);
        }
        log.debug("Processing failover at attempt {} for {}", attempts, copy);

        AsyncProcessor albp = AsyncProcessorConverterHelper.convert(processor);
        return albp.process(copy, new FailOverAsyncCallback(exchange, copy, attempts, index, callback, processors));
    }

    /**
     * Failover logic to be executed asynchronously if one of the failover endpoints
     * is a real {@link AsyncProcessor}.
     */
    private final class FailOverAsyncCallback implements AsyncCallback {

        private final Exchange exchange;
        private Exchange copy;
        private final AtomicInteger attempts;
        private final AtomicInteger index;
        private final AsyncCallback callback;
        private final List<Processor> processors;

        private FailOverAsyncCallback(Exchange exchange, Exchange copy, AtomicInteger attempts, AtomicInteger index, AsyncCallback callback, List<Processor> processors) {
            this.exchange = exchange;
            this.copy = copy;
            this.attempts = attempts;
            this.index = index;
            this.callback = callback;
            this.processors = processors;
        }

        public void done(boolean doneSync) {
            // we only have to handle async completion of the pipeline
            if (doneSync) {
                return;
            }

            while (shouldFailOver(copy)) {

                // can we still run
                if (!isRunAllowed()) {
                    log.trace("Run not allowed, will reject executing exchange: {}", exchange);
                    if (exchange.getException() == null) {
                        exchange.setException(new RejectedExecutionException());
                    }
                    // we cannot process so invoke callback
                    callback.done(false);
                }

                attempts.incrementAndGet();
                // are we exhausted by attempts?
                if (maximumFailoverAttempts > -1 && attempts.get() > maximumFailoverAttempts) {
                    log.trace("Breaking out of failover after {} failover attempts", attempts);
                    break;
                }

                index.incrementAndGet();
                counter.incrementAndGet();

                if (index.get() >= processors.size()) {
                    // out of bounds
                    if (isRoundRobin()) {
                        log.trace("Failover is round robin enabled and therefore starting from the first endpoint");
                        index.set(0);
                        counter.set(0);
                    } else {
                        // no more processors to try
                        log.trace("Breaking out of failover as we reached the end of endpoints to use for failover");
                        break;
                    }
                }

                // try again but prepare exchange before we failover
                copy = prepareExchangeForFailover(exchange);
                Processor processor = processors.get(index.get());

                // try to failover using the next processor
                doneSync = processExchange(processor, exchange, copy, attempts, index, callback, processors);
                if (!doneSync) {
                    log.trace("Processing exchangeId: {} is continued being processed asynchronously", exchange.getExchangeId());
                    // the remainder of the failover will be completed async
                    // so we break out now, then the callback will be invoked which then continue routing from where we left here
                    return;
                }
            }

            // remember last good index
            lastGoodIndex.set(index.get());

            // and copy the current result to original so it will contain this result of this eip
            if (copy != null) {
                ExchangeHelper.copyResults(exchange, copy);
            }
            log.debug("Failover complete for exchangeId: {} >>> {}", exchange.getExchangeId(), exchange);
            // signal callback we are done
            callback.done(false);
        }
    }

    public String toString() {
        return "FailoverLoadBalancer[" + getProcessors() + "]";
    }

    public String getTraceLabel() {
        return "failover";
    }

    public ExceptionFailureStatistics getExceptionFailureStatistics() {
        return statistics;
    }

    public void reset() {
        // reset state
        lastGoodIndex.set(-1);
        counter.set(-1);
        statistics.reset();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // reset state
        reset();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        // noop
    }

}
