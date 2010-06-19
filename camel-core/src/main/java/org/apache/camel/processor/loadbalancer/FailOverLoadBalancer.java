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
import org.apache.camel.util.ObjectHelper;

/**
 * This FailOverLoadBalancer will failover to use next processor when an exception occurred
 */
public class FailOverLoadBalancer extends LoadBalancerSupport {

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
        if (exchange.getException() != null) {

            if (exceptions == null || exceptions.isEmpty()) {
                // always failover if no exceptions defined
                return true;
            }

            for (Class<?> exception : exceptions) {
                // will look in exception hierarchy 
                if (exchange.getException(exception) != null) {
                    return true;
                }
            }
        }

        return false;
    }

    public boolean process(Exchange exchange, AsyncCallback callback) {
        boolean sync;

        List<Processor> processors = getProcessors();
        if (processors.isEmpty()) {
            throw new IllegalStateException("No processors available to process " + exchange);
        }

        final AtomicInteger index = new AtomicInteger();
        final AtomicInteger attempts = new AtomicInteger();

        // pick the first endpoint to use
        if (isRoundRobin()) {
            if (counter.incrementAndGet() >= processors.size()) {
                counter.set(0);
            }
            index.set(counter.get());
        }
        if (log.isDebugEnabled()) {
            log.debug("Failover starting with endpoint index " + index);
        }

        Processor processor = processors.get(index.get());

        // process the failover
        sync = processExchange(processor, exchange, attempts, index, callback, processors);

        // continue as long its being processed synchronously
        if (!sync) {
            if (log.isTraceEnabled()) {
                log.trace("Processing exchangeId: " + exchange.getExchangeId() + " is continued being processed asynchronously");
            }
            // the remainder of the failover will be completed async
            // so we break out now, then the callback will be invoked which then continue routing from where we left here
            return false;
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
        exchange.setException(null);

        exchange.setProperty(Exchange.ERRORHANDLER_HANDLED, null);
        exchange.setProperty(Exchange.FAILURE_HANDLED, null);
        exchange.setProperty(Exchange.EXCEPTION_CAUGHT, null);
        exchange.getIn().removeHeader(Exchange.REDELIVERED);
        exchange.getIn().removeHeader(Exchange.REDELIVERY_COUNTER);
    }

    private boolean processExchange(Processor processor, Exchange exchange,
                                    AtomicInteger attempts, AtomicInteger index,
                                    AsyncCallback callback, List<Processor> processors) {
        if (processor == null) {
            throw new IllegalStateException("No processors could be chosen to process " + exchange);
        }
        if (log.isDebugEnabled()) {
            log.debug("Processing failover at attempt " + attempts + " for exchange: " + exchange);
        }

        AsyncProcessor albp = AsyncProcessorTypeConverter.convert(processor);
        boolean sync = albp.process(exchange, new FailOverAsyncCallback(exchange, attempts, index, callback, processors));
        return sync;
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
            // should we failover?
            if (shouldFailOver(exchange)) {
                attempts.incrementAndGet();
                // are we exhausted by attempts?
                if (maximumFailoverAttempts > -1 && attempts.get() > maximumFailoverAttempts) {
                    if (log.isDebugEnabled()) {
                        log.debug("Braking out of failover after " + attempts + " failover attempts");
                    }
                    callback.done(doneSync);
                    return;
                }

                index.incrementAndGet();
                counter.incrementAndGet();

                if (index.get() >= processors.size()) {
                    // out of bounds
                    if (isRoundRobin()) {
                        log.debug("Failover is round robin enabled and therefore starting from the first endpoint");
                        index.set(0);
                        counter.set(0);
                    } else {
                        // no more processors to try
                        log.debug("Braking out of failover as we reach the end of endpoints to use for failover");
                        callback.done(doneSync);
                        return;
                    }
                }

                // try again but prepare exchange before we failover
                prepareExchangeForFailover(exchange);
                Processor processor = processors.get(index.get());

                // try to failover using the next processor
                AsyncProcessor albp = AsyncProcessorTypeConverter.convert(processor);
                albp.process(exchange, this);
            } else {
                // we are done doing failover
                callback.done(doneSync);
            }
        }
    }

    public String toString() {
        return "FailoverLoadBalancer";
    }

}
