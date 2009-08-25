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

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Processor;

/**
 * A <a href="http://camel.apache.org/delayer.html">Delayer</a> which
 * delays processing the exchange until the correct amount of time has elapsed
 * using an expression to determine the delivery time.
 * 
 * @version $Revision$
 */
public class Delayer extends DelayProcessorSupport implements Traceable {
    private Expression delay;

    public Delayer(Processor processor, Expression delay) {
        super(processor);
        this.delay = delay;
    }

    @Override
    public String toString() {
        return "Delayer[" + delay + " to: " + getProcessor() + "]";
    }

    public String getTraceLabel() {
        return "Delayer[" + delay + "]";
    }

    public Expression getDelay() {
        return delay;
    }

    public void setDelay(Expression delay) {
        this.delay = delay;
    }

    // Implementation methods
    // -------------------------------------------------------------------------

    /**
     * Waits for an optional time period before continuing to process the
     * exchange
     */
    protected void delay(Exchange exchange) throws Exception {
        long time = 0;
        if (delay != null) {
            Long longValue = delay.evaluate(exchange, Long.class);
            if (longValue != null) {
                time = longValue;
            }
        }
        if (time <= 0) {
            // no delay
            return;
        }

        // now add the current time
        time += defaultProcessTime(exchange);

        waitUntil(time, exchange);
    }

    /**
     * A Strategy Method to allow derived implementations to decide the current
     * system time or some other default exchange property
     */
    protected long defaultProcessTime(Exchange exchange) {
        return currentSystemTime();
    }

}
