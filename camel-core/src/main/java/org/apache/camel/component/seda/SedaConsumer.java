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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ShutdownRunningTask;
import org.apache.camel.impl.LoggingExceptionHandler;
import org.apache.camel.impl.ServiceSupport;
import org.apache.camel.impl.converter.AsyncProcessorTypeConverter;
import org.apache.camel.processor.MulticastProcessor;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.spi.ShutdownAware;
import org.apache.camel.util.AsyncProcessorHelper;
import org.apache.camel.util.ServiceHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * A Consumer for the SEDA component.
 *
 * @version $Revision$
 */
public class SedaConsumer extends ServiceSupport implements Consumer, Runnable, ShutdownAware {
    private static final transient Log LOG = LogFactory.getLog(SedaConsumer.class);

    private SedaEndpoint endpoint;
    private AsyncProcessor processor;
    private ExecutorService executor;
    private MulticastProcessor multicast;
    private ExceptionHandler exceptionHandler;

    public SedaConsumer(SedaEndpoint endpoint, Processor processor) {
        this.endpoint = endpoint;
        this.processor = AsyncProcessorTypeConverter.convert(processor);
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

    public void run() {
        BlockingQueue<Exchange> queue = endpoint.getQueue();
        while (queue != null && isRunAllowed()) {
            final Exchange exchange;
            try {
                exchange = queue.poll(1000, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Sleep interrupted, are we stopping? " + (isStopping() || isStopped()));
                }
                continue;
            }
            if (exchange != null) {
                if (isRunAllowed()) {
                    try {
                        sendToConsumers(exchange);

                        // log exception if an exception occurred and was not handled
                        if (exchange.getException() != null) {
                            getExceptionHandler().handleException("Error processing exchange", exchange, exchange.getException());
                        }
                    } catch (Exception e) {
                        getExceptionHandler().handleException("Error processing exchange", exchange, e);
                    }
                } else {
                    if (LOG.isWarnEnabled()) {
                        LOG.warn("This consumer is stopped during polling an exchange, so putting it back on the seda queue: " + exchange);
                    }
                    try {
                        queue.put(exchange);
                    } catch (InterruptedException e) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Sleep interrupted, are we stopping? " + (isStopping() || isStopped()));
                        }
                        continue;
                    }
                }
            }
        }
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

            if (LOG.isDebugEnabled()) {
                LOG.debug("Multicasting to " + endpoint.getConsumers().size() + " consumers for Exchange: " + exchange);
            }

            // use a multicast processor to process it
            MulticastProcessor mp = getMulticastProcessor();

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

    protected synchronized MulticastProcessor getMulticastProcessor() {
        if (multicast == null) {
            int size = endpoint.getConsumers().size();

            List<Processor> processors = new ArrayList<Processor>(size);
            for (SedaConsumer consumer : endpoint.getConsumers()) {
                processors.add(consumer.getProcessor());
            }

            ExecutorService multicastExecutor = endpoint.getCamelContext().getExecutorServiceStrategy()
                                                    .newFixedThreadPool(this, endpoint.getEndpointUri() + "(multicast)", size);
            multicast = new MulticastProcessor(endpoint.getCamelContext(), processors, null, true, multicastExecutor, false, false);
        }
        return multicast;
    }

    protected void doStart() throws Exception {
        int poolSize = endpoint.getConcurrentConsumers();
        executor = endpoint.getCamelContext().getExecutorServiceStrategy()
                        .newFixedThreadPool(this, endpoint.getEndpointUri(), poolSize);
        for (int i = 0; i < poolSize; i++) {
            executor.execute(this);
        }
        endpoint.onStarted(this);
    }

    protected void doStop() throws Exception {
        endpoint.onStopped(this);
        // must shutdown executor on stop to avoid overhead of having them running
        endpoint.getCamelContext().getExecutorServiceStrategy().shutdown(executor);
        executor = null;

        if (multicast != null) {
            ServiceHelper.stopService(multicast);
            multicast = null;
        }
    }

}
