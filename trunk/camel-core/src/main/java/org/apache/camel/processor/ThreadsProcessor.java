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
import org.apache.camel.impl.ServiceSupport;
import org.apache.camel.util.AsyncProcessorHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * Threads processor that leverage a thread pool for continue processing the {@link Exchange}s
 * using the asynchronous routing engine.
 *
 * @version 
 */
public class ThreadsProcessor extends ServiceSupport implements AsyncProcessor {

    private final ExecutorService executorService;
    private final AtomicBoolean shutdown = new AtomicBoolean(true);
    private boolean callerRunsWhenRejected = true;

    private final class ProcessCall implements Runnable {
        private final Exchange exchange;
        private final AsyncCallback callback;

        public ProcessCall(Exchange exchange, AsyncCallback callback) {
            this.exchange = exchange;
            this.callback = callback;
        }

        public void run() {
            if (shutdown.get()) {
                exchange.setException(new RejectedExecutionException("ThreadsProcessor is not running."));
            }
            callback.done(false);
        }
    }

    public ThreadsProcessor(CamelContext camelContext, ExecutorService executorService) {
        ObjectHelper.notNull(camelContext, "camelContext");
        ObjectHelper.notNull(executorService, "executorService");
        this.executorService = executorService;
    }

    public void process(final Exchange exchange) throws Exception {
        AsyncProcessorHelper.process(this, exchange);
    }

    public boolean process(Exchange exchange, AsyncCallback callback) {
        if (shutdown.get()) {
            throw new IllegalStateException("ThreadsProcessor is not running.");
        }

        ProcessCall call = new ProcessCall(exchange, callback);
        try {
            executorService.submit(call);
            // tell Camel routing engine we continue routing asynchronous
            return false;
        } catch (RejectedExecutionException e) {
            if (isCallerRunsWhenRejected()) {
                if (shutdown.get()) {
                    exchange.setException(new RejectedExecutionException());
                } else {
                    callback.done(true);
                }
            } else {
                exchange.setException(e);
            }
            return true;
        }
    }

    public boolean isCallerRunsWhenRejected() {
        return callerRunsWhenRejected;
    }

    public void setCallerRunsWhenRejected(boolean callerRunsWhenRejected) {
        this.callerRunsWhenRejected = callerRunsWhenRejected;
    }

    public String toString() {
        return "Threads";
    }

    protected void doStart() throws Exception {
        shutdown.set(false);
    }

    protected void doStop() throws Exception {
        shutdown.set(true);
    }
}
