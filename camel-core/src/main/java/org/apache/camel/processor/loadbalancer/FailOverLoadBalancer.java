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
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.converter.AsyncProcessorTypeConverter;
import org.apache.camel.processor.Traceable;
import org.apache.camel.util.AsyncProcessorHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * This FailOverLoadBalancer will failover to use next processor when an exception occurred
 * <p/>
 * This implementation mirrors the logic from the {@link org.apache.camel.processor.Pipeline} in the async variation
 * as the failover load balancer is a specialized pipeline. So the trick is to keep doing the same as the
 * pipeline to ensure it works the same and the async routing engine is flawless.
 */
public class FailOverLoadBalancer extends LoadBalancerSupport implements Traceable {

    private final List<Class<?>> exceptions;
    private boolean roundRobin;
    private int maximumFailoverAttempts = -1;

    // stateful counter
    private final AtomicInteger counter = new AtomicInteger(-1);

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
        }

        if (log.isTraceEnabled()) {
            log.trace("Should failover: " + answer + " for exchangeId: " + exchange.getExchangeId());
        }

        return answer;
    }

    public boolean process(Exchange exchange, AsyncCallback callback) {
        final List<Processor> processors = getProcessors();

        final AtomicInteger index = new AtomicInteger();
        final AtomicInteger attempts = new AtomicInteger();
        boolean first = true;

        // get the next processor
        if (isRoundRobin()) {
            if (counter.incrementAndGet() >= processors.size()) {
                counter.set(0);
            }
            index.set(counter.get());
        }
        if (log.isTraceEnabled()) {
            log.trace("Failover starting with endpoint index " + index);
        }

        while (first || shouldFailOver(exchange)) {
            if (!first) {
                attempts.incrementAndGet();
                // are we exhausted by attempts?
                if (maximumFailoverAttempts > -1 && attempts.get() > maximumFailoverAttempts) {
                    if (log.isDebugEnabled()) {
                        log.debug("Breaking out of failover after " + attempts + " failover attempts");
                    }
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

            // try again but prepare exchange before we failover
            prepareExchangeForFailover(exchange);
            Processor processor = processors.get(index.get());

            // process the exchange
            boolean sync = processExchange(processor, exchange, attempts, index, callback, processors);

            // continue as long its being processed synchronously
            if (!sync) {
                if (log.isTraceEnabled()) {
                    log.trace("Processing exchangeId: " + exchange.getExchangeId() + " is continued being processed asynchronously");
                }
                // the remainder of the failover will be completed async
                // so we break out now, then the callback will be invoked which then continue routing from where we left here
                return false;
            }

            if (log.isTraceEnabled()) {
                log.trace("Processing exchangeId: " + exchange.getExchangeId() + " is continued being processed synchronously");
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("Failover complete for exchangeId: " + exchange.getExchangeId() + " >>> " + exchange);
        }

        callback.done(true);
        return true;
    }

    /**
     * Prepares the exchange for failover
     *
     * @param exchange the exchange
     */
    protected void prepareExchangeForFailover(Exchange exchange) {
        if (exchange.getException() != null) {
            if (log.isDebugEnabled()) {
                log.debug("Failover due " + exchange.getException().getMessage() + " for exchangeId: " + exchange.getExchangeId());
            }

            // clear exception so we can try failover
            exchange.setException(null);
        }

        exchange.setProperty(Exchange.ERRORHANDLER_HANDLED, null);
        exchange.setProperty(Exchange.FAILURE_HANDLED, null);
        exchange.setProperty(Exchange.EXCEPTION_CAUGHT, null);
        exchange.getIn().removeHeader(Exchange.REDELIVERED);
        exchange.getIn().removeHeader(Exchange.REDELIVERY_COUNTER);
        exchange.getIn().removeHeader(Exchange.REDELIVERY_MAX_COUNTER);
    }

    private boolean processExchange(Processor processor, Exchange exchange,
                                    AtomicInteger attempts, AtomicInteger index,
                                    AsyncCallback callback, List<Processor> processors) {
        if (processor == null) {
            throw new IllegalStateException("No processors could be chosen to process " + exchange);
        }
        if (log.isDebugEnabled()) {
            log.debug("Processing failover at attempt " + attempts + " for " + exchange);
        }

        AsyncProcessor albp = AsyncProcessorTypeConverter.convert(processor);
        return AsyncProcessorHelper.process(albp, exchange, new FailOverAsyncCallback(exchange, attempts, index, callback, processors));
    }

    /**
     * Failover logic to be executed asynchronously if one of the failover endpoints
     * is a real {@link AsyncProcessor}.
     */
    private final class FailOverAsyncCallback implements AsyncCallback {

        private final Exchange exchange;
        private final AtomicInteger attempts;
        private final AtomicInteger index;
        private final AsyncCallback callback;
        private final List<Processor> processors;

        private FailOverAsyncCallback(Exchange exchange, AtomicInteger attempts, AtomicInteger index, AsyncCallback callback, List<Processor> processors) {
            this.exchange = exchange;
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

            while (shouldFailOver(exchange)) {
                attempts.incrementAndGet();
                // are we exhausted by attempts?
                if (maximumFailoverAttempts > -1 && attempts.get() > maximumFailoverAttempts) {
                    if (log.isTraceEnabled()) {
                        log.trace("Breaking out of failover after " + attempts + " failover attempts");
                    }
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
                prepareExchangeForFailover(exchange);
                Processor processor = processors.get(index.get());

                // try to failover using the next processor
                doneSync = processExchange(processor, exchange, attempts, index, callback, processors);
                if (!doneSync) {
                    if (log.isTraceEnabled()) {
                        log.trace("Processing exchangeId: " + exchange.getExchangeId() + " is continued being processed asynchronously");
                    }
                    // the remainder of the failover will be completed async
                    // so we break out now, then the callback will be invoked which then continue routing from where we left here
                    return;
                }
            }

            if (log.isDebugEnabled()) {
                log.debug("Failover complete for exchangeId: " + exchange.getExchangeId() + " >>> " + exchange);
            }

            // signal callback we are done
            callback.done(false);
        };
    }

    public String toString() {
        return "FailoverLoadBalancer[" + getProcessors() + "]";
    }

    public String getTraceLabel() {
        return "failover";
    }
}
