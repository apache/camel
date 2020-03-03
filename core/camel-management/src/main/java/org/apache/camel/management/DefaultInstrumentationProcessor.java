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
package org.apache.camel.management;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Ordered;
import org.apache.camel.Processor;
import org.apache.camel.management.mbean.ManagedPerformanceCounter;
import org.apache.camel.spi.ManagementInterceptStrategy.InstrumentationProcessor;
import org.apache.camel.support.processor.DelegateAsyncProcessor;
import org.apache.camel.util.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JMX enabled processor or advice that uses the {@link org.apache.camel.management.mbean.ManagedCounter} for instrumenting
 * processing of exchanges.
 * <p/>
 * This implementation has been optimised to work in dual mode, either as an advice or as a processor.
 * The former is faster and the latter is required when the error handler has been configured with redelivery enabled.
 */
public class DefaultInstrumentationProcessor extends DelegateAsyncProcessor
        implements InstrumentationProcessor<StopWatch>, Ordered {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultInstrumentationProcessor.class);


    private PerformanceCounter counter;
    private String type;

    public DefaultInstrumentationProcessor(String type, Processor processor) {
        super(processor);
        this.type = type;
    }

    public DefaultInstrumentationProcessor(String type) {
        super(null);
        this.type = type;
    }

    @Override
    public void setCounter(Object counter) {
        ManagedPerformanceCounter mpc = null;
        if (counter instanceof ManagedPerformanceCounter) {
            mpc = (ManagedPerformanceCounter) counter;
        }

        if (this.counter instanceof DelegatePerformanceCounter) {
            ((DelegatePerformanceCounter) this.counter).setCounter(mpc);
        } else if (mpc != null) {
            this.counter = mpc;
        } else if (counter instanceof PerformanceCounter) {
            this.counter = (PerformanceCounter) counter;
        }
    }

    @Override
    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        final StopWatch watch = before(exchange);

        // optimize to only create a new callback if needed
        AsyncCallback ac = callback;
        boolean newCallback = watch != null;
        if (newCallback) {
            ac = doneSync -> {
                try {
                    // record end time
                    after(exchange, watch);
                } finally {
                    // and let the original callback know we are done as well
                    callback.done(doneSync);
                }
            };
        }

        return processor.process(exchange, ac);
    }

    protected void beginTime(Exchange exchange) {
        counter.processExchange(exchange);
    }

    protected void recordTime(Exchange exchange, long duration) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("{}Recording duration: {} millis for exchange: {}", type != null ? type + ": " : "", duration, exchange);
        }

        if (!exchange.isFailed() && exchange.getException() == null) {
            counter.completedExchange(exchange, duration);
        } else {
            counter.failedExchange(exchange);
        }
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @Override
    public StopWatch before(Exchange exchange) {
        // only record time if stats is enabled
        StopWatch answer = counter != null && counter.isStatisticsEnabled() ? new StopWatch() : null;
        if (answer != null) {
            beginTime(exchange);
        }
        return answer;
    }

    @Override
    public void after(Exchange exchange, StopWatch watch) {
        // record end time
        if (watch != null) {
            recordTime(exchange, watch.taken());
        }
    }

    @Override
    public String toString() {
        return "InstrumentProcessorAdvice";
    }

    @Override
    public int getOrder() {
        // we want instrumentation before calling the processor (but before tracer/debugger)
        return Ordered.LOWEST - 2;
    }
}
