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
package org.apache.camel.component.seda;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ShutdownRunningTask;
import org.apache.camel.SuspendableService;
import org.apache.camel.impl.LoggingExceptionHandler;
import org.apache.camel.processor.MulticastProcessor;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.spi.ShutdownAware;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.AsyncProcessorConverterHelper;
import org.apache.camel.util.AsyncProcessorHelper;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Consumer for the SEDA component.
 * <p/>
 * In this implementation there is a little <i>slack period</i> when you suspend/stop the consumer, by which
 * the consumer may pickup a newly arrived messages and process it. That period is up till 1 second.
 *
 * @version 
 */
public class SedaConsumer extends ServiceSupport implements Consumer, Runnable, ShutdownAware, SuspendableService {
    private static final transient Logger LOG = LoggerFactory.getLogger(SedaConsumer.class);

    private final AtomicInteger taskCount = new AtomicInteger();
    private CountDownLatch latch;
    private volatile boolean shutdownPending;
    private SedaEndpoint endpoint;
    private AsyncProcessor processor;
    private ExecutorService executor;
    private ExceptionHandler exceptionHandler;

    public SedaConsumer(SedaEndpoint endpoint, Processor processor) {
        this.endpoint = endpoint;
        this.processor = AsyncProcessorConverterHelper.convert(processor);
    }

    @Override
    public String toString() {
        return "SedaConsumer[" + endpoint.getEndpointUri() + "]";
    }

    public Endpoint getEndpoint() {
        return endpoint;
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

    public Processor getProcessor() {
        return processor;
    }

    public boolean deferShutdown(ShutdownRunningTask shutdownRunningTask) {
        // deny stopping on shutdown as we want seda consumers to run in case some other queues
        // depend on this consumer to run, so it can complete its exchanges
        return true;
    }

    public int getPendingExchangesSize() {
        // number of pending messages on the queue
        return endpoint.getQueue().size();
    }

    public void prepareShutdown() {
        // signal we want to shutdown
        shutdownPending = true;

        LOG.debug("Preparing to shutdown, waiting for {} consumer threads to complete.", latch.getCount());

        // wait for all threads to end
        try {
            latch.await();
        } catch (InterruptedException e) {
            // ignore
        }
    }

    @Override
    public boolean isRunAllowed() {
        if (isSuspending() || isSuspended()) {
            // allow to run even if we are suspended as we want to
            // keep the thread task running
            return true;
        }
        return super.isRunAllowed();
    }

    public void run() {
        taskCount.incrementAndGet();
        try {
            doRun();
        } finally {
            taskCount.decrementAndGet();
        }
    }

    protected void doRun() {
        BlockingQueue<Exchange> queue = endpoint.getQueue();
        // loop while we are allowed, or if we are stopping loop until the queue is empty
        while (queue != null && (isRunAllowed())) {
            // do not poll if we are suspended
            if (isSuspending() || isSuspended()) {
                LOG.trace("Consumer is suspended so skip polling");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    LOG.debug("Sleep interrupted, are we stopping? {}", isStopping() || isStopped());
                }
                continue;
            }

            Exchange exchange = null;
            try {
                exchange = queue.poll(1000, TimeUnit.MILLISECONDS);
                if (exchange != null) {
                    try {
                        // send a new copied exchange with new camel context
                        Exchange newExchange = prepareExchange(exchange);
                        // process the exchange
                        sendToConsumers(newExchange);
                        // copy the message back
                        if (newExchange.hasOut()) {
                            exchange.setOut(newExchange.getOut().copy());
                        } else {
                            exchange.setIn(newExchange.getIn());
                        }
                        // log exception if an exception occurred and was not handled
                        if (newExchange.getException() != null) {
                            exchange.setException(newExchange.getException());
                            getExceptionHandler().handleException("Error processing exchange", exchange, exchange.getException());
                        }
                    } catch (Exception e) {
                        getExceptionHandler().handleException("Error processing exchange", exchange, e);
                    }
                } else if (shutdownPending && queue.isEmpty()) {
                    LOG.trace("Shutdown is pending, so this consumer thread is breaking out because the task queue is empty.");
                    // we want to shutdown so break out if there queue is empty
                    break;
                }
            } catch (InterruptedException e) {
                LOG.debug("Sleep interrupted, are we stopping? {}", isStopping() || isStopped());
                continue;
            } catch (Throwable e) {
                if (exchange != null) {
                    getExceptionHandler().handleException("Error processing exchange", exchange, e);
                } else {
                    getExceptionHandler().handleException(e);
                }
            }
        }

        latch.countDown();
        LOG.debug("Ending this polling consumer thread, there are still {} consumer threads left.", latch.getCount());
    }

    /**
     * Strategy to prepare exchange for being processed by this consumer
     *
     * @param exchange the exchange
     * @return the exchange to process by this consumer.
     */
    protected Exchange prepareExchange(Exchange exchange) {
        // send a new copied exchange with new camel context
        Exchange newExchange = ExchangeHelper.copyExchangeAndSetCamelContext(exchange, endpoint.getCamelContext());
        // set the from endpoint
        newExchange.setFromEndpoint(endpoint);
        return newExchange;
    }

    /**
     * Send the given {@link Exchange} to the consumer(s).
     * <p/>
     * If multiple consumers then they will each receive a copy of the Exchange.
     * A multicast processor will send the exchange in parallel to the multiple consumers.
     * <p/>
     * If there is only a single consumer then its dispatched directly to it using same thread.
     * 
     * @param exchange the exchange
     * @throws Exception can be thrown if processing of the exchange failed
     */
    protected void sendToConsumers(Exchange exchange) throws Exception {
        int size = endpoint.getConsumers().size();

        // if there are multiple consumers then multicast to them
        if (size > 1) {

            // validate multiple consumers has been enabled
            if (!endpoint.isMultipleConsumersSupported()) {
                throw new IllegalStateException("Multiple consumers for the same endpoint is not allowed: " + endpoint);
            }

            if (LOG.isDebugEnabled()) {
                LOG.debug("Multicasting to {} consumers for Exchange: {}", endpoint.getConsumers().size(), exchange);
            }
           
            // use a multicast processor to process it
            MulticastProcessor mp = endpoint.getConsumerMulticastProcessor();
            ObjectHelper.notNull(mp, "ConsumerMulticastProcessor", this);

            // and use the asynchronous routing engine to support it
            AsyncProcessorHelper.process(mp, exchange, new AsyncCallback() {
                public void done(boolean doneSync) {
                    // noop
                }
            });
        } else {
            // use the regular processor and use the asynchronous routing engine to support it
            AsyncProcessorHelper.process(processor, exchange, new AsyncCallback() {
                public void done(boolean doneSync) {
                    // noop
                }
            });
        }
    }

    protected void doStart() throws Exception {
        latch = new CountDownLatch(endpoint.getConcurrentConsumers());
        shutdownPending = false;

        setupTasks();
        endpoint.onStarted(this);
    }

    @Override
    protected void doSuspend() throws Exception {
        endpoint.onStopped(this);
    }

    @Override
    protected void doResume() throws Exception {
        doStart();
    }

    protected void doStop() throws Exception {
        endpoint.onStopped(this);
    }

    @Override
    protected void doShutdown() throws Exception {
        // only shutdown thread pool when we shutdown
        if (executor != null) {
            endpoint.getCamelContext().getExecutorServiceManager().shutdownNow(executor);
            executor = null;
        }
    }

    /**
     * Setup the thread pool and ensures tasks gets executed (if needed)
     */
    private void setupTasks() {
        int poolSize = endpoint.getConcurrentConsumers();

        // create thread pool if needed
        if (executor == null) {
            executor = endpoint.getCamelContext().getExecutorServiceManager().newFixedThreadPool(this, endpoint.getEndpointUri(), poolSize);
        }

        // submit needed number of tasks
        int tasks = poolSize - taskCount.get();
        LOG.debug("Creating {} consumer tasks", tasks);
        for (int i = 0; i < tasks; i++) {
            executor.execute(this);
        }
    }

}
