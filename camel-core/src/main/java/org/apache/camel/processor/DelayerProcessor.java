/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import org.apache.camel.impl.ServiceSupport;
import org.apache.camel.util.ExpressionHelper;
import org.apache.camel.util.ServiceHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * A <a href="http://activemq.apache.org/camel/delayer.html">Delayer</a> which delays
 * processing the exchange until the correct amount of time has elapsed
 * using an expression to determine the delivery time.
 *
 * For example if you wish to delay JMS messages by 25 seconds from their publish time you could create
 * an instance of this class with the expression <code>header("JMSTimestamp")</code> and a delay value of 25000L.
 *
 * @version $Revision: 1.1 $
 */
public class DelayerProcessor extends ServiceSupport implements Processor {
    private static final transient Log log = LogFactory.getLog(DelayerProcessor.class);
    private Expression<Exchange> timeExpression;
    private Processor processor;
    private long delay = 0L;

    public DelayerProcessor(Processor processor, Expression<Exchange> timeExpression, long delay) {
        this.processor = processor;
        this.timeExpression = timeExpression;
        this.delay = delay;
    }

    public void process(Exchange exchange) throws Exception {
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
        processor.process(exchange);
    }

    // Properties
    //-------------------------------------------------------------------------
    public long getDelay() {
        return delay;
    }

    /**
     * Sets the delay from the publish time; which is typically the time from the expression
     * or the current system time if none is available
     */
    public void setDelay(long delay) {
        this.delay = delay;
    }

    // Implementation methods
    //-------------------------------------------------------------------------

    protected void doStart() throws Exception {
        ServiceHelper.startService(processor);
    }

    protected void doStop() throws Exception {
        ServiceHelper.stopService(processor);
    }

    /**
     * Wait until the given system time before continuing
     *
     * @param time     the system time to wait for
     * @param exchange the exchange being processed
     */
    protected void waitUntil(long time, Exchange exchange) {
        while (true) {
            long delay = time - currentSystemTime();
            if (delay < 0) {
                return;
            }
            else {
                try {
                    if (log.isDebugEnabled()) {
                        log.debug("Sleeping for: " + delay + " millis");
                    }
                    Thread.sleep(delay);
                }
                catch (InterruptedException e) {
                    handleSleepInteruptedException(e);
                }
            }
        }
    }

    /**
     * Called when a sleep is interupted; allows derived classes to handle this case differently
     */
    protected void handleSleepInteruptedException(InterruptedException e) {
        log.debug("Sleep interupted: " + e, e);
    }

    /**
     * A Strategy Method to allow derived implementations to decide the current system time or some other
     * default exchange property
     *
     * @param exchange
     */
    protected long defaultProcessTime(Exchange exchange) {
        return currentSystemTime();
    }

    protected long currentSystemTime() {
        return System.currentTimeMillis();
    }
}
