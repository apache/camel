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

import org.apache.camel.Exchange;
import org.apache.camel.management.mbean.ManagedPerformanceCounter;
import org.apache.camel.processor.DelegateProcessor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * JMX enabled processor that uses the {@link org.apache.camel.management.mbean.ManagedCounter} for instrumenting
 * processing of exchanges.
 *
 * @version $Revision$
 */
public class InstrumentationProcessor extends DelegateProcessor {

    private static final transient Log LOG = LogFactory.getLog(InstrumentationProcessor.class);
    private ManagedPerformanceCounter counter;
    private String type;

    public InstrumentationProcessor() {
    }

    public InstrumentationProcessor(ManagedPerformanceCounter counter) {
        this.counter = counter;
    }

    @Override
    public String toString() {
        return "Instrumention" + (type != null ? ":" + type : "") + "[" + processor + "]";
    }

    public void setCounter(ManagedPerformanceCounter counter) {
        this.counter = counter;
    }

    public ManagedPerformanceCounter getCounter() {
        return counter;
    }

    public void process(Exchange exchange) throws Exception {
        if (processor != null) {

            long startTime = -1;
            if (counter != null && counter.isStatisticsEnabled()) {
                startTime = System.currentTimeMillis();
            }

            try {
                processor.process(exchange);
            } catch (Exception e) {
                exchange.setException(e);
            }

            if (startTime != -1) {
                recordTime(exchange, System.currentTimeMillis() - startTime);
            }
        }
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
