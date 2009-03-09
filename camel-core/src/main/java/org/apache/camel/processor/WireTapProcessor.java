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

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;

/**
 * Processor for wire tapping exchanges to an endpoint destination.
 *
 * @version $Revision$
 */
public class WireTapProcessor extends SendProcessor {

    private int defaultThreadPoolSize = 5;
    private ExecutorService executorService;

    public WireTapProcessor(Endpoint destination) {
        super(destination);
    }

    public WireTapProcessor(Endpoint destination, ExchangePattern pattern) {
        super(destination, pattern);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        if (executorService != null) {
            executorService.shutdown();
        }
        super.doStop();
    }

    @Override
    public String toString() {
        return "wireTap(" + destination.getEndpointUri() + ")";
    }

    public void process(Exchange exchange) throws Exception {
        if (producer == null) {
            if (isStopped()) {
                LOG.warn("Ignoring exchange sent after processor is stopped: " + exchange);
            } else {
                throw new IllegalStateException("No producer, this processor has not been started!");
            }
        } else {
            final Exchange wireTapExchange = configureExchange(exchange);

            // use submit instead of execute to force it to use a new thread, execute might
            // decide to use current thread, so we must submit a new task
            // as we dont care for the response we dont hold the future object and wait for the result
            getExecutorService().submit(new Callable<Object>() {
                public Object call() throws Exception {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Processing wiretap: " + wireTapExchange);
                    }
                    producer.process(wireTapExchange);
                    return null;
                }
            });
        }
    }

    public boolean process(Exchange exchange, final AsyncCallback callback) {
        if (producer == null) {
            if (isStopped()) {
                LOG.warn("Ignoring exchange sent after processor is stopped: " + exchange);
            } else {
                exchange.setException(new IllegalStateException("No producer, this processor has not been started!"));
            }
            callback.done(true);
            return true;
        } else {
            exchange = configureExchange(exchange);

            final Exchange wireTapExchange = configureExchange(exchange);

            // use submit instead of execute to force it to use a new thread, execute might
            // decide to use current thread, so we must submit a new task
            // as we dont care for the response we dont hold the future object and wait for the result
            getExecutorService().submit(new Callable<Object>() {
                public Object call() throws Exception {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Processing wiretap: " + wireTapExchange);
                    }
                    return processor.process(wireTapExchange, callback);
                }
            });

            // return true to indicate caller its okay, and he should not wait as this wiretap
            // is a fire and forget
            return true;
        }
    }


    @Override
    protected Exchange configureExchange(Exchange exchange) {
        // must use a copy as we dont want it to cause side effects of the original exchange
        Exchange copy = exchange.copy();
        // set MEP to InOnly as this wire tap is a fire and forget
        copy.setPattern(ExchangePattern.InOnly);
        return copy;
    }

    public ExecutorService getExecutorService() {
        if (executorService == null) {
            executorService = createExecutorService();
        }
        return executorService;
    }

    private ExecutorService createExecutorService() {
        return new ScheduledThreadPoolExecutor(defaultThreadPoolSize, new ThreadFactory() {
            int counter;

            public synchronized Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable);
                thread.setName("Thread: " + (++counter) + " " + WireTapProcessor.this.toString());
                return thread;
            }
        });
    }

    public void setExecutorService(ExecutorService executorService) {
        this.executorService = executorService;
    }
    
}
