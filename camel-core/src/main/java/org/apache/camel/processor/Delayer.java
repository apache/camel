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
package org.apache.camel.processor;

import java.util.concurrent.ScheduledExecutorService;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.Traceable;
import org.apache.camel.spi.IdAware;

/**
 * A <a href="http://camel.apache.org/delayer.html">Delayer</a> which
 * delays processing the exchange until the correct amount of time has elapsed
 * using an expression to determine the delivery time.
 * <p/>
 * This implementation will block while waiting.
 *
 * @version 
 */
public class Delayer extends DelayProcessorSupport implements Traceable, IdAware {
    private String id;
    private Expression delay;
    private long delayValue;

    public Delayer(CamelContext camelContext, Processor processor, Expression delay,
                   ScheduledExecutorService executorService, boolean shutdownExecutorService) {
        super(camelContext, processor, executorService, shutdownExecutorService);
        this.delay = delay;
    }

    @Override
    public String toString() {
        return "Delayer[" + delay + " to: " + getProcessor() + "]";
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTraceLabel() {
        return "delay[" + delay + "]";
    }

    public Expression getDelay() {
        return delay;
    }

    public long getDelayValue() {
        return delayValue;
    }

    public void setDelay(Expression delay) {
        this.delay = delay;
    }

    // Implementation methods
    // -------------------------------------------------------------------------

    protected long calculateDelay(Exchange exchange) {
        long time = 0;
        if (delay != null) {
            Long longValue = delay.evaluate(exchange, Long.class);
            if (longValue != null) {
                delayValue = longValue;
                time = longValue;
            } else {
                delayValue = 0;
            }
        }
        if (time <= 0) {
            // no delay
            return 0;
        }

        return time;
    }

}
