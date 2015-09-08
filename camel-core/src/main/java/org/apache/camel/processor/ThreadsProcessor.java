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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Rejectable;
import org.apache.camel.ThreadPoolRejectedPolicy;
import org.apache.camel.spi.IdAware;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.AsyncProcessorHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Threads processor that leverage a thread pool for continue processing the {@link Exchange}s
 * using the asynchronous routing engine.
 * <p/>
 * <b>Notice:</b> For transacted routes then this {@link ThreadsProcessor} is not in use, as we want to
 * process messages using the same thread to support all work done in the same transaction. The reason
 * is that the transaction manager that orchestrate the transaction, requires all the work to be done
 * on the same thread.
 * <p/>
 * Pay attention to how this processor handles rejected tasks.
 * <ul>
 * <li>Abort - The current exchange will be set with a {@link RejectedExecutionException} exception,
 * and marked to stop continue routing.
 * The {@link org.apache.camel.spi.UnitOfWork} will be regarded as <b>failed</b>, due the exception.</li>
 * <li>Discard - The current exchange will be marked to stop continue routing (notice no exception is set).
 * The {@link org.apache.camel.spi.UnitOfWork} will be regarded as <b>successful</b>, due no exception being set.</li>
 * <li>DiscardOldest - The oldest exchange will be marked to stop continue routing (notice no exception is set).
 * The {@link org.apache.camel.spi.UnitOfWork} will be regarded as <b>successful</b>, due no exception being set.
 * And the current exchange will be added to the task queue.</li>
 * <li>CallerRuns - The current exchange will be processed by the current thread. Which mean the current thread
 * will not be free to process a new exchange, as its processing the current exchange.</li>
 * </ul>
 */
public class ThreadsProcessor extends ServiceSupport implements AsyncProcessor, IdAware {

    private static final Logger LOG = LoggerFactory.getLogger(ThreadsProcessor.class);
    private String id;
    private final CamelContext camelContext;
    private final ExecutorService executorService;
    private volatile boolean shutdownExecutorService;
    private final AtomicBoolean shutdown = new AtomicBoolean(true);
    private boolean callerRunsWhenRejected = true;
    private ThreadPoolRejectedPolicy rejectedPolicy;

    private final class ProcessCall implements Runnable, Rejectable {
        private final Exchange exchange;
        private final AsyncCallback callback;

        public ProcessCall(Exchange exchange, AsyncCallback callback) {
            this.exchange = exchange;
            this.callback = callback;
        }

        @Override
        public void run() {
            LOG.trace("Continue routing exchange {} ", exchange);
            if (shutdown.get()) {
                exchange.setException(new RejectedExecutionException("ThreadsProcessor is not running."));
            }
            callback.done(false);
        }

        @Override
        public void reject() {
            // abort should mark the exchange with an rejected exception
            boolean abort = ThreadPoolRejectedPolicy.Abort == rejectedPolicy;
            if (abort) {
                exchange.setException(new RejectedExecutionException());
            }

            LOG.trace("{} routing exchange {} ", abort ? "Aborted" : "Rejected", exchange);
            // we should not continue routing, and no redelivery should be performed
            exchange.setProperty(Exchange.ROUTE_STOP, true);
            exchange.setProperty(Exchange.REDELIVERY_EXHAUSTED, true);

            if (shutdown.get()) {
                exchange.setException(new RejectedExecutionException("ThreadsProcessor is not running."));
            }
            callback.done(false);
        }

        @Override
        public String toString() {
            return "ProcessCall[" + exchange + "]";
        }
    }

    public ThreadsProcessor(CamelContext camelContext, ExecutorService executorService, boolean shutdownExecutorService) {
        ObjectHelper.notNull(camelContext, "camelContext");
        ObjectHelper.notNull(executorService, "executorService");
        this.camelContext = camelContext;
        this.executorService = executorService;
        this.shutdownExecutorService = shutdownExecutorService;
    }

    public void process(final Exchange exchange) throws Exception {
        AsyncProcessorHelper.process(this, exchange);
    }

    public boolean process(Exchange exchange, AsyncCallback callback) {
        if (shutdown.get()) {
            throw new IllegalStateException("ThreadsProcessor is not running.");
        }

        // we cannot execute this asynchronously for transacted exchanges, as the transaction manager doesn't support
        // using different threads in the same transaction
        if (exchange.isTransacted()) {
            LOG.trace("Transacted Exchange must be routed synchronously for exchangeId: {} -> {}", exchange.getExchangeId(), exchange);
            callback.done(true);
            return true;
        }

        ProcessCall call = new ProcessCall(exchange, callback);
        try {
            LOG.trace("Submitting task {}", call);
            executorService.submit(call);
            // tell Camel routing engine we continue routing asynchronous
            return false;
        } catch (RejectedExecutionException e) {
            boolean callerRuns = isCallerRunsWhenRejected();
            if (!callerRuns) {
                exchange.setException(e);
            }

            LOG.trace("{} executing task {}", callerRuns ? "CallerRuns" : "Aborted", call);
            if (shutdown.get()) {
                exchange.setException(new RejectedExecutionException());
            }
            callback.done(true);
            return true;
        }
    }

    public boolean isCallerRunsWhenRejected() {
        return callerRunsWhenRejected;
    }

    public void setCallerRunsWhenRejected(boolean callerRunsWhenRejected) {
        this.callerRunsWhenRejected = callerRunsWhenRejected;
    }

    public ThreadPoolRejectedPolicy getRejectedPolicy() {
        return rejectedPolicy;
    }

    public void setRejectedPolicy(ThreadPoolRejectedPolicy rejectedPolicy) {
        this.rejectedPolicy = rejectedPolicy;
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }

    public String toString() {
        return "Threads";
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    protected void doStart() throws Exception {
        shutdown.set(false);
    }

    protected void doStop() throws Exception {
        shutdown.set(true);
    }

    protected void doShutdown() throws Exception {
        if (shutdownExecutorService) {
            camelContext.getExecutorServiceManager().shutdownNow(executorService);
        }
        super.doShutdown();
    }

}
