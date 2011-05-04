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
package org.apache.camel.component.timer;

import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The timer consumer.
 *
 * @version 
 */
public class TimerConsumer extends DefaultConsumer {
    private static final transient Logger LOG = LoggerFactory.getLogger(TimerConsumer.class);
    private final TimerEndpoint endpoint;
    private volatile TimerTask task;

    public TimerConsumer(TimerEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        task = new TimerTask() {
            // counter
            private final AtomicLong counter = new AtomicLong();

            @Override
            public void run() {
                try {
                    long count = counter.incrementAndGet();

                    boolean fire = endpoint.getRepeatCount() <= 0 || count <= endpoint.getRepeatCount();
                    if (fire) {
                        sendTimerExchange(count);
                    } else {
                        // no need to fire anymore as we exceeded repeat count
                        LOG.debug("Cancelling {} timer as repeat count limit reached after {} counts.", endpoint.getTimerName(), endpoint.getRepeatCount());
                        cancel();
                    }
                } catch (Throwable e) {
                    // catch all to avoid the JVM closing the thread and not firing again
                    LOG.warn("Error processing exchange. This exception will be ignored, to let the timer be able to trigger again.", e);
                }
            }
        };

        Timer timer = endpoint.getTimer();
        configureTask(task, timer);
    }

    @Override
    protected void doStop() throws Exception {
        if (task != null) {
            task.cancel();
        }
        task = null;
    }

    protected void configureTask(TimerTask task, Timer timer) {
        if (endpoint.isFixedRate()) {
            if (endpoint.getTime() != null) {
                timer.scheduleAtFixedRate(task, endpoint.getTime(), endpoint.getPeriod());
            } else {
                timer.scheduleAtFixedRate(task, endpoint.getDelay(), endpoint.getPeriod());
            }
        } else {
            if (endpoint.getTime() != null) {
                if (endpoint.getPeriod() > 0) {
                    timer.schedule(task, endpoint.getTime(), endpoint.getPeriod());
                } else {
                    timer.schedule(task, endpoint.getTime());
                }
            } else {
                if (endpoint.getPeriod() > 0) {
                    timer.schedule(task, endpoint.getDelay(), endpoint.getPeriod());
                } else {
                    timer.schedule(task, endpoint.getDelay());
                }
            }
        }
    }

    protected void sendTimerExchange(long counter) {
        Exchange exchange = endpoint.createExchange();
        exchange.setProperty(Exchange.TIMER_COUNTER, counter);
        exchange.setProperty(Exchange.TIMER_NAME, endpoint.getTimerName());
        exchange.setProperty(Exchange.TIMER_TIME, endpoint.getTime());
        exchange.setProperty(Exchange.TIMER_PERIOD, endpoint.getPeriod());

        Date now = new Date();
        exchange.setProperty(Exchange.TIMER_FIRED_TIME, now);
        // also set now on in header with same key as quartz to be consistent
        exchange.getIn().setHeader("firedTime", now);

        LOG.trace("Timer {} is firing #{} count", endpoint.getTimerName(), counter);
        try {
            getProcessor().process(exchange);

            // log exception if an exception occurred and was not handled
            if (exchange.getException() != null) {
                getExceptionHandler().handleException("Error processing exchange", exchange, exchange.getException());
            }
        } catch (Exception e) {
            getExceptionHandler().handleException("Error processing exchange", exchange, exchange.getException());
        }
    }
}
