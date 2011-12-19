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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A useful base class for any processor which provides some kind of throttling
 * or delayed processing.
 * <p/>
 * This implementation will block while waiting.
 * 
 * @version 
 */
public abstract class DelayProcessorSupport extends DelegateAsyncProcessor {
    protected final transient Logger log = LoggerFactory.getLogger(getClass());
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
            log.trace("Delayed task woke up and continues routing for exchangeId: {}", exchange.getExchangeId());
            if (!isRunAllowed()) {
                exchange.setException(new RejectedExecutionException("Run is not allowed"));
            }

            // process the exchange now that we woke up
            DelayProcessorSupport.super.process(exchange, new AsyncCallback() {
                @Override
                public void done(boolean doneSync) {
                    log.trace("Delayed task done for exchangeId: {}", exchange.getExchangeId());
                    // we must done the callback from this async callback as well, to ensure callback is done correctly
                    // must invoke done on callback with false, as that is what the original caller would
                    // expect as we returned false in the process method
                    callback.done(false);
                }
            });
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
            log.trace("No delay for exchangeId: {}", exchange.getExchangeId());
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
                log.trace("Scheduling delayed task to run in {} millis for exchangeId: {}",
                        delay, exchange.getExchangeId());
                executorService.schedule(call, delay, TimeUnit.MILLISECONDS);
                // tell Camel routing engine we continue routing asynchronous
                return false;
            } catch (RejectedExecutionException e) {
                if (isCallerRunsWhenRejected()) {
                    if (!isRunAllowed()) {
                        exchange.setException(new RejectedExecutionException());
                    } else {
                        log.debug("Scheduling rejected task, so letting caller run, delaying at first for {} millis for exchangeId: {}", delay, exchange.getExchangeId());
                        // let caller run by processing
                        try {
                            delay(delay, exchange);
                        } catch (InterruptedException ie) {
                            exchange.setException(ie);
                        }
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
    protected void delay(long delay, Exchange exchange) throws InterruptedException {
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
                handleSleepInterruptedException(e, exchange);
            }
        }
    }

    /**
     * Called when a sleep is interrupted; allows derived classes to handle this case differently
     */
    protected void handleSleepInterruptedException(InterruptedException e, Exchange exchange) throws InterruptedException {
        if (log.isDebugEnabled()) {
            log.debug("Sleep interrupted, are we stopping? {}", isStopping() || isStopped());
        }
        Thread.currentThread().interrupt();
        throw e;
    }

    protected long currentSystemTime() {
        return System.currentTimeMillis();
    }

    private void sleep(long delay) throws InterruptedException {
        if (delay <= 0) {
            return;
        }
        log.trace("Sleeping for: {} millis", delay);
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
