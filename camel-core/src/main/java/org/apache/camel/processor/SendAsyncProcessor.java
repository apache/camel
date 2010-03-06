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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Navigate;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.ProducerCallback;
import org.apache.camel.impl.LoggingExceptionHandler;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.util.ExchangeHelper;

/**
 * @version $Revision$
 */
public class SendAsyncProcessor extends SendProcessor implements Runnable, Navigate<Processor> {

    private static final int DEFAULT_THREADPOOL_SIZE = 10;
    protected final Processor target;
    protected final BlockingQueue<Exchange> completedTasks = new LinkedBlockingQueue<Exchange>();
    protected ExecutorService executorService;
    protected ExecutorService producerExecutorService;
    protected int poolSize = DEFAULT_THREADPOOL_SIZE;
    protected ExceptionHandler exceptionHandler;

    public SendAsyncProcessor(Endpoint destination, Processor target) {
        super(destination);
        this.target = target;
    }

    public SendAsyncProcessor(Endpoint destination, ExchangePattern pattern, Processor target) {
        super(destination, pattern);
        this.target = target;
    }

    @Override
    protected Exchange configureExchange(Exchange exchange, ExchangePattern pattern) {
        // use a new copy of the exchange to route async and handover the on completion to the new copy
        // so its the new copy that performs the on completion callback when its done
        final Exchange copy = ExchangeHelper.createCorrelatedCopy(exchange, true);
        if (pattern != null) {
            copy.setPattern(pattern);
        } else {
            // default to use in out as we do request reply over async
            copy.setPattern(ExchangePattern.InOut);
        }
        // configure the endpoint we are sending to
        copy.setProperty(Exchange.TO_ENDPOINT, destination.getEndpointUri());
        // send the copy
        return copy;
    }

    @Override
    public Exchange doProcess(Exchange exchange) throws Exception {
        // now we are done, we should have a API callback for this
        // send the exchange to the destination using a producer
        Exchange answer = getProducerCache(exchange).doInProducer(destination, exchange, pattern, new ProducerCallback<Exchange>() {
            public Exchange doInProducer(Producer producer, Exchange exchange, ExchangePattern pattern) throws Exception {
                exchange = configureExchange(exchange, pattern);

                // pass in the callback that adds the exchange to the completed list of tasks
                final AsyncCallback callback = new AsyncCallback() {
                    public void onTaskCompleted(Exchange exchange) {
                        completedTasks.add(exchange);
                    }
                };

                if (producer instanceof AsyncProcessor) {
                    // producer is async capable so let it process it directly
                    doAsyncProcess((AsyncProcessor) producer, exchange, callback);
                } else {
                    // producer is a regular processor so simulate async behaviour
                    doSimulateAsyncProcess(producer, exchange, callback);
                }

                // and return the exchange
                return exchange;
            }
        });

        return answer;
    }

    /**
     * The producer is already capable of async processing so let it process it directly.
     *
     * @param producer the async producer
     * @param exchange the exchange
     * @param callback the callback
     *
     * @throws Exception can be thrown in case of processing errors
     */
    protected void doAsyncProcess(AsyncProcessor producer, Exchange exchange, AsyncCallback callback) throws Exception {
        producer.process(exchange, callback);
    }

    /**
     * The producer is <b>not</b> capable of async processing so lets simulate this by transferring the task
     * to another {@link ExecutorService} for async processing.
     *
     * @param producer the producer
     * @param exchange the exchange
     * @param callback the callback
     *
     * @throws Exception can be thrown in case of processing errors
     */
    protected void doSimulateAsyncProcess(final Processor producer, final Exchange exchange, final AsyncCallback callback) throws Exception {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Producer " + producer + " is not an instanceof AsyncProcessor"
                + ". Will fallback to simulate async behavior by transferring task to a producer thread pool for further processing.");
        }

        // let the producer thread pool handle the task of sending the request which then will simulate the async
        // behavior as the original thread is not blocking while we wait for the reply
        getProducerExecutorService().submit(new Callable<Exchange>() {
            public Exchange call() throws Exception {
                // convert the async producer which just blocks until the task is complete
                try {
                    AsyncProcessor asyncProducer = exchange.getContext().getTypeConverter().convertTo(AsyncProcessor.class, producer);
                    asyncProducer.process(exchange, callback);
                } catch (Exception e) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Caught exception while processing: " + exchange, e);
                    }
                    // set the exception on the exchange so Camel error handling can deal with it
                    exchange.setException(e);
                }
                return exchange;
            }
        });
    }

    @Override
    public String toString() {
        return "sendAsyncTo(" + destination + (pattern != null ? " " + pattern : "") + " -> " + target + ")";
    }

    public ExecutorService getExecutorService() {
        if (executorService == null) {
            executorService = destination.getCamelContext().getExecutorServiceStrategy()
                                .newThreadPool("SendAsyncProcessor-Consumer", poolSize, poolSize);
        }
        return executorService;
    }

    /**
     * Sets the {@link java.util.concurrent.ExecutorService} to use for consuming replies.
     *
     * @param executorService the custom executor service
     */
    public void setExecutorService(ExecutorService executorService) {
        this.executorService = executorService;
    }

    public ExecutorService getProducerExecutorService() {
        if (producerExecutorService == null) {
            // use a cached pool for the producers which can grow/schrink itself
            producerExecutorService = destination.getCamelContext().getExecutorServiceStrategy()
                                        .newCachedThreadPool("SendAsyncProcessor-Producer");
        }
        return producerExecutorService;
    }

    /**
     * Sets the {@link java.util.concurrent.ExecutorService} to use for simulating async producers
     * by transferring the {@link Exchange} to this {@link java.util.concurrent.ExecutorService} for
     * sending the request and block while waiting for the reply. However the original thread
     * will not block and as such it all appears as real async request/reply mechanism.
     *
     * @param producerExecutorService the custom executor service for producers
     */
    public void setProducerExecutorService(ExecutorService producerExecutorService) {
        this.producerExecutorService = producerExecutorService;
    }

    public int getPoolSize() {
        return poolSize;
    }

    public void setPoolSize(int poolSize) {
        this.poolSize = poolSize;
    }

    public ExceptionHandler getExceptionHandler() {
        if (exceptionHandler == null) {
            exceptionHandler = new LoggingExceptionHandler(getClass());
        }
        return exceptionHandler;
    }

    public void setExceptionHandler(ExceptionHandler exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
    }

    public boolean hasNext() {
        return target != null;
    }

    public List<Processor> next() {
        if (!hasNext()) {
            return null;
        }
        List<Processor> answer = new ArrayList<Processor>(1);
        answer.add(target);
        return answer;
    }

    public void run() {
        while (isRunAllowed()) {
            Exchange exchange;
            try {
                exchange = completedTasks.poll(1000, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Sleep interrupted, are we stopping? " + (isStopping() || isStopped()));
                }
                continue;
            }

            if (exchange != null) {
                try {
                    // copy OUT to IN
                    if (exchange.hasOut()) {
                        // replace OUT with IN as async processing changed something
                        exchange.setIn(exchange.getOut());
                        exchange.setOut(null);
                    }

                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Async reply received now routing the Exchange: " + exchange);
                    }
                    target.process(exchange);
                } catch (Exception e) {
                    getExceptionHandler().handleException(e);
                }
            }
        }
    }

    protected void doStart() throws Exception {
        super.doStart();

        for (int i = 0; i < poolSize; i++) {
            getExecutorService().execute(this);
        }
    }

    protected void doStop() throws Exception {
        super.doStop();

        if (producerExecutorService != null) {
            producerExecutorService.shutdownNow();
            producerExecutorService = null;
        }
        if (executorService != null) {
            executorService.shutdownNow();
            executorService = null;
        }
        completedTasks.clear();

    }

}
