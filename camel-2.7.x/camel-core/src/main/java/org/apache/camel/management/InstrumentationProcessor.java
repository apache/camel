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
package org.apache.camel.management;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.management.mbean.ManagedPerformanceCounter;
import org.apache.camel.processor.DelegateAsyncProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JMX enabled processor that uses the {@link org.apache.camel.management.mbean.ManagedCounter} for instrumenting
 * processing of exchanges.
 *
 * @version 
 */
public class InstrumentationProcessor extends DelegateAsyncProcessor {

    private static final transient Logger LOG = LoggerFactory.getLogger(InstrumentationProcessor.class);
    private PerformanceCounter counter;
    private String type;

    public InstrumentationProcessor() {
    }

    public InstrumentationProcessor(PerformanceCounter counter) {
        this.counter = counter;
    }

    @Override
    public String toString() {
        return "Instrumentation" + (type != null ? ":" + type : "") + "[" + processor + "]";
    }

    public void setCounter(ManagedPerformanceCounter counter) {
        if (this.counter instanceof DelegatePerformanceCounter) {
            ((DelegatePerformanceCounter) this.counter).setCounter(counter);
        } else {
            this.counter = counter;
        }
    }

    @Override
    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        // use nano time as its more accurate
        // and only record time if stats is enabled
        long start = -1;
        if (counter != null && counter.isStatisticsEnabled()) {
            start = System.nanoTime();
        }
        final long startTime = start;

        return super.process(exchange, new AsyncCallback() {
            public void done(boolean doneSync) {
                try {
                    // record end time
                    if (startTime > -1) {
                        long diff = (System.nanoTime() - startTime) / 1000000;
                        recordTime(exchange, diff);
                    }
                } finally {
                    // and let the original callback know we are done as well
                    callback.done(doneSync);
                }
            }
        });
    }

    protected void recordTime(Exchange exchange, long duration) {
        if (LOG.isTraceEnabled()) {
            LOG.trace((type != null ? type + ": " : "") + "Recording duration: " + duration + " millis for exchange: " + exchange);
        }

        if (!exchange.isFailed() && exchange.getException() == null) {
            counter.completedExchange(duration);
        } else {
            counter.failedExchange();
        }
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
