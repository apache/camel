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

public class CircuitBreakerLoadBalancer extends LoadBalancerSupport implements Traceable, CamelContextAware {
    private final List<Class<?>> exceptions;
    private CamelContext camelContext;
    private int threshold;
    private long halfOpenAfter;
    private long lastFailure;
    private AtomicInteger failures = new AtomicInteger();

    public CircuitBreakerLoadBalancer(List<Class<?>> exceptions) {
        this.exceptions = exceptions;
    }
    public CircuitBreakerLoadBalancer() {
        this.exceptions = null;
    }

    public void setHalfOpenAfter(long halfOpenAfter) {
        this.halfOpenAfter = halfOpenAfter;
    }

    public void setThreshold(int threshold) {
        this.threshold = threshold;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public List<Class<?>> getExceptions() {
        return exceptions;
    }

    protected boolean hasFailed(Exchange exchange) {
        boolean answer = false;

        if (exchange.getException() != null) {
            if (exceptions == null || exceptions.isEmpty()) {
                answer = true;
            } else {
                for (Class<?> exception : exceptions) {
                    if (exchange.getException(exception) != null) {
                        answer = true;
                        break;
                    }
                }
            }
        }
        return answer;
    }

    @Override
    public boolean isRunAllowed() {
        boolean forceShutdown = camelContext.getShutdownStrategy().forceShutdown(this);
        if (forceShutdown) {
            log.trace("Run not allowed as ShutdownStrategy is forcing shutting down");
        }
        return !forceShutdown && super.isRunAllowed();
    }

    public boolean process(final Exchange exchange, final AsyncCallback callback) {

        // can we still run
        if (!isRunAllowed()) {
            log.trace("Run not allowed, will reject executing exchange: {}", exchange);
            if (exchange.getException() == null) {
                exchange.setException(new RejectedExecutionException("Run is not allowed"));
            }
            callback.done(true);
            return true;
        }

        if (failures.get() >= threshold && System.currentTimeMillis() - lastFailure < halfOpenAfter) {
            exchange.setException(new RejectedExecutionException("CircuitBreaker Open: failures: " + failures + ", lastFailure: " + lastFailure));
        }
        Processor processor = getProcessors().get(0);
        if (processor == null) {
            throw new IllegalStateException("No processors could be chosen to process CircuitBreaker");
        }

        AsyncProcessor albp = AsyncProcessorConverterHelper.convert(processor);
        boolean sync = albp.process(exchange, callback);

        boolean failed = hasFailed(exchange);

        if (!failed) {
            failures.set(0);
        } else {
            failures.incrementAndGet();
            lastFailure = System.currentTimeMillis();
        }

        if (!sync) {
            log.trace("Processing exchangeId: {} is continued being processed asynchronously", exchange.getExchangeId());
            return false;
        }

        log.trace("Processing exchangeId: {} is continued being processed synchronously", exchange.getExchangeId());
        callback.done(true);
        return true;
    }

    public String toString() {
        return "CircuitBreakerLoadBalancer[" + getProcessors() + "]";
    }

    public String getTraceLabel() {
        return "circuitbreaker";
    }
}
