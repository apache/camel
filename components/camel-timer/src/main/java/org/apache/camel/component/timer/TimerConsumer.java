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
package org.apache.camel.component.timer;

import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.StartupListener;
import org.apache.camel.Suspendable;
import org.apache.camel.support.DefaultConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The timer consumer.
 */
public class TimerConsumer extends DefaultConsumer implements StartupListener, Suspendable {

    private static final Logger LOG = LoggerFactory.getLogger(TimerConsumer.class);
    private final TimerEndpoint endpoint;
    private volatile TimerTask task;
    private volatile boolean configured;
    private ExecutorService executorService;

    public TimerConsumer(TimerEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    @Override
    public TimerEndpoint getEndpoint() {
        return (TimerEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (endpoint.getDelay() >= 0) {
            task = new TimerTask() {
                // counter
                private final AtomicLong counter = new AtomicLong();

                @Override
                public void run() {
                    if (!isTaskRunAllowed()) {
                        // do not run timer task as it was not allowed
                        LOG.debug("Run not allowed for timer: {}", endpoint);
                        return;
                    }

                    try {
                        long count = counter.incrementAndGet();

                        boolean fire = endpoint.getRepeatCount() <= 0 || count <= endpoint.getRepeatCount();
                        if (fire) {
                            sendTimerExchange(count);
                        } else {
                            // no need to fire anymore as we exceeded repeat
                            // count
                            LOG.debug("Cancelling {} timer as repeat count limit reached after {} counts.", endpoint.getTimerName(), endpoint.getRepeatCount());
                            cancel();
                        }
                    } catch (Throwable e) {
                        // catch all to avoid the JVM closing the thread and not
                        // firing again
                        LOG.warn("Error processing exchange. This exception will be ignored, to let the timer be able to trigger again.", e);
                    }
                }
            };

            // only configure task if CamelContext already started, otherwise
            // the StartupListener
            // is configuring the task later
            if (!configured && endpoint.getCamelContext().getStatus().isStarted()) {
                Timer timer = endpoint.getTimer(this);
                configureTask(task, timer);
            }
        } else {
            // if the delay is negative then we use an ExecutorService and fire messages as soon as possible
            executorService = endpoint.getCamelContext().getExecutorServiceManager().newSingleThreadExecutor(this, endpoint.getEndpointUri());

            executorService.execute(new Runnable() {
                public void run() {
                    final AtomicLong counter = new AtomicLong();
                    long count = counter.incrementAndGet();
                    while ((endpoint.getRepeatCount() <= 0 || count <= endpoint.getRepeatCount()) && isRunAllowed()) {
                        sendTimerExchange(count);
                        count = counter.incrementAndGet();
                    }
                }
            });
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (task != null) {
            task.cancel();
        }
        task = null;
        configured = false;

        // remove timer
        endpoint.removeTimer(this);
        
        // if executorService is instantiated then we shutdown it
        if (executorService != null) {
            endpoint.getCamelContext().getExecutorServiceManager().shutdown(executorService);
            executorService = null;
        }

        super.doStop();
    }

    @Override
    public void onCamelContextStarted(CamelContext context, boolean alreadyStarted) throws Exception {
        if (task != null && !configured) {
            Timer timer = endpoint.getTimer(this);
            configureTask(task, timer);
        } 
    }

    /**
     * Whether the timer task is allow to run or not
     */
    protected boolean isTaskRunAllowed() {
        // only run if we are started
        return isStarted();
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
        configured = true;
    }

    protected void sendTimerExchange(long counter) {
        final Exchange exchange = endpoint.createExchange();

        if (endpoint.isIncludeMetadata()) {
            exchange.setProperty(Exchange.TIMER_COUNTER, counter);
            exchange.setProperty(Exchange.TIMER_NAME, endpoint.getTimerName());
            exchange.setProperty(Exchange.TIMER_TIME, endpoint.getTime());
            exchange.setProperty(Exchange.TIMER_PERIOD, endpoint.getPeriod());

            Date now = new Date();
            exchange.setProperty(Exchange.TIMER_FIRED_TIME, now);
            // also set now on in header with same key as quartz to be consistent
            exchange.getIn().setHeader("firedTime", now);
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace("Timer {} is firing #{} count", endpoint.getTimerName(), counter);
        }

        if (!endpoint.isSynchronous()) {
            getAsyncProcessor().process(exchange, new AsyncCallback() {
                @Override
                public void done(boolean doneSync) {
                    // handle any thrown exception
                    if (exchange.getException() != null) {
                        getExceptionHandler().handleException("Error processing exchange", exchange, exchange.getException());
                    }
                }
            });
        } else {
            try {
                getProcessor().process(exchange);
            } catch (Exception e) {
                exchange.setException(e);
            }

            // handle any thrown exception
            if (exchange.getException() != null) {
                getExceptionHandler().handleException("Error processing exchange", exchange, exchange.getException());
            }
        }
    }
}
