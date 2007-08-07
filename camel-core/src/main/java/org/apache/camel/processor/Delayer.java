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
import org.apache.camel.util.ExpressionHelper;

/**
 * A <a href="http://activemq.apache.org/camel/delayer.html">Delayer</a> which
 * delays processing the exchange until the correct amount of time has elapsed
 * using an expression to determine the delivery time. <p/> For example if you
 * wish to delay JMS messages by 25 seconds from their publish time you could
 * create an instance of this class with the expression
 * <code>header("JMSTimestamp")</code> and a delay value of 25000L.
 * 
 * @version $Revision: 1.1 $
 */
public class Delayer extends DelayProcessorSupport {
    private Expression<Exchange> timeExpression;
    private long delay;

    public Delayer(Processor processor, Expression<Exchange> timeExpression, long delay) {
        super(processor);
        this.timeExpression = timeExpression;
        this.delay = delay;
    }

    @Override
    public String toString() {
        return "Delayer[on: " + timeExpression + " delay: " + delay + " to: " + getProcessor() + "]";
    }

    // Properties
    // -------------------------------------------------------------------------
    public long getDelay() {
        return delay;
    }

    /**
     * Sets the delay from the publish time; which is typically the time from
     * the expression or the current system time if none is available
     */
    public void setDelay(long delay) {
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
        if (timeExpression != null) {
            Long longValue = ExpressionHelper.evaluateAsType(timeExpression, exchange, Long.class);
            if (longValue != null) {
                time = longValue.longValue();
            }
        }
        if (time <= 0) {
            time = defaultProcessTime(exchange);
        }

        time += delay;

        waitUntil(time, exchange);
    }

    /**
     * A Strategy Method to allow derived implementations to decide the current
     * system time or some other default exchange property
     * 
     * @param exchange
     */
    protected long defaultProcessTime(Exchange exchange) {
        return currentSystemTime();
    }

}
