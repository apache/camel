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
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.processor.DelegateProcessor;
import org.apache.camel.util.AsyncProcessorHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * JMX enabled processor that uses the {@link Counter} for instrumenting
 * processing of exchanges.
 *
 * @version $Revision$
 */
public class InstrumentationProcessor extends DelegateProcessor implements AsyncProcessor {

    private static final transient Log LOG = LogFactory.getLog(InstrumentationProcessor.class);
    private PerformanceCounter counter;

    public InstrumentationProcessor(PerformanceCounter counter) {
        this.counter = counter;
    }

    public InstrumentationProcessor() {
    }

    public void setCounter(PerformanceCounter counter) {
        this.counter = counter;
    }

    public void process(Exchange exchange) throws Exception {
        AsyncProcessorHelper.process(this, exchange);
    }

    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        final long startTime = System.nanoTime();

        if (processor instanceof AsyncProcessor) {
            return ((AsyncProcessor)processor).process(exchange, new AsyncCallback() {
                public void done(boolean doneSynchronously) {
                    if (counter != null) {
                        // convert nanoseconds to milliseconds
                        recordTime(exchange, (System.nanoTime() - startTime) / 1000000.0);
                    }
                    callback.done(doneSynchronously);
                }
            });
        }

        try {
            processor.process(exchange);
        } catch (Exception e) {
            exchange.setException(e);
        }

        if (counter != null) {
            // convert nanoseconds to milliseconds
            recordTime(exchange, (System.nanoTime() - startTime) / 1000000.0);
        }
        callback.done(true);
        return true;
    }

    protected void recordTime(Exchange exchange, double duration) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Recording duration: " + duration + " millis for exchange: " + exchange);
        }

        if (!exchange.isFailed() && exchange.getException() == null) {
            counter.completedExchange(duration);
        } else {
            counter.failedExchange();
        }
    }

}
