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

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * A useful base class for any processor which provides some kind of throttling
 * or delayed processing.
 * <p/>
 * This implementation will block while waiting.
 * 
 * @version $Revision$
 */
public abstract class DelayProcessorSupport extends DelegateAsyncProcessor {
    protected final transient Log log = LogFactory.getLog(getClass());
    private final ScheduledExecutorService executorService;
    private boolean asyncDelayed;
    private boolean callerRunsWhenRejected = true;

    // TODO: Add option to cancel tasks on shutdown so we can stop fast

    private final class ProcessCall implements Runnable {
        private final Exchange exchange;
        private final AsyncCallback callback;

        public ProcessCall(Exchange exchange, AsyncCallback callback) {
            this.exchange = exchange;
            this.callback = callback;
        }

        public void run() {
            if (log.isTraceEnabled()) {
                log.trace("Delayed task woke up and continues routing for exchangeId: " + exchange.getExchangeId());
            }
            if (!isRunAllowed()) {
                exchange.setException(new RejectedExecutionException("Run is not allowed"));
            }
            DelayProcessorSupport.super.process(exchange, callback);
            // signal callback we are done async
            callback.done(false);
        }
    }

    public DelayProcessorSupport(Processor processor) {
        this(processor, null);
    }

    public DelayProcessorSupport(Processor processor, ScheduledExecutorService executorService) {
        super(processor);
        this.executorService = executorService;
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        if (!isRunAllowed()) {
            exchange.setException(new RejectedExecutionException("Run is not allowed"));
            callback.done(true);
            return true;
        }

        // calculate delay and wait
        long delay = calculateDelay(exchange);
        if (delay <= 0) {
            // no delay then continue routing
            return super.process(exchange, callback);
        }

        if (!isAsyncDelayed() || exchange.isTransacted()) {
            // use synchronous delay (also required if using transactions)
            try {
                delay(delay, exchange);
                // then continue routing
                return super.process(exchange, callback);
            } catch (Exception e) {
                // exception occurred so we are done
                exchange.setException(e);
                callback.done(true);
                return true;
            }
        } else {
            // asynchronous delay so schedule a process call task
            ProcessCall call = new ProcessCall(exchange, callback);
            try {
                if (log.isTraceEnabled()) {
                    log.trace("Scheduling delayed task to run in " + delay + " millis for exchangeId: " + exchange.getExchangeId());
                }
                executorService.schedule(call, delay, TimeUnit.MILLISECONDS);
                // tell Camel routing engine we continue routing asynchronous
                return false;
            } catch (RejectedExecutionException e) {
                if (isCallerRunsWhenRejected()) {
                    if (!isRunAllowed()) {
                        exchange.setException(new RejectedExecutionException());
                    } else {
                        // let caller run by processing
                        delay(delay, exchange);
                        // then continue routing
                        return super.process(exchange, callback);
                    }
                } else {
                    exchange.setException(e);
                }
                // caller don't run the task so we are done
                callback.done(true);
                return true;
            }
        }
    }

    public boolean isAsyncDelayed() {
        return asyncDelayed;
    }

    public void setAsyncDelayed(boolean asyncDelayed) {
        this.asyncDelayed = asyncDelayed;
    }

    public boolean isCallerRunsWhenRejected() {
        return callerRunsWhenRejected;
    }

    public void setCallerRunsWhenRejected(boolean callerRunsWhenRejected) {
        this.callerRunsWhenRejected = callerRunsWhenRejected;
    }

    protected abstract long calculateDelay(Exchange exchange);

    /**
     * Delays the given time before continuing.
     * <p/>
     * This implementation will block while waiting
     * 
     * @param delay the delay time in millis
     * @param exchange the exchange being processed
     */
    protected void delay(long delay, Exchange exchange) {
        // only run is we are started
        if (!isRunAllowed()) {
            return;
        }

        if (delay < 0) {
            return;
        } else {
            try {
                sleep(delay);
            } catch (InterruptedException e) {
                handleSleepInterruptedException(e);
            }
        }
    }

    /**
     * Called when a sleep is interrupted; allows derived classes to handle this
     * case differently
     */
    protected void handleSleepInterruptedException(InterruptedException e) {
        if (log.isDebugEnabled()) {
            log.debug("Sleep interrupted, are we stopping? " + (isStopping() || isStopped()));
        }
    }

    protected long currentSystemTime() {
        return System.currentTimeMillis();
    }

    private void sleep(long delay) throws InterruptedException {
        if (delay <= 0) {
            return;
        }
        if (log.isTraceEnabled()) {
            log.trace("Sleeping for: " + delay + " millis");
        }
        Thread.sleep(delay);
    }

    @Override
    protected void doStart() throws Exception {
        if (isAsyncDelayed()) {
            ObjectHelper.notNull(executorService, "executorService", this);
        }
        super.doStart();
    }
}
