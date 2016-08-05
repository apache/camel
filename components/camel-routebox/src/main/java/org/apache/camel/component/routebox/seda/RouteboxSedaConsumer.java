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
package org.apache.camel.component.routebox.seda;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.ShutdownRunningTask;
import org.apache.camel.component.routebox.RouteboxConsumer;
import org.apache.camel.component.routebox.RouteboxServiceSupport;
import org.apache.camel.component.routebox.strategy.RouteboxDispatcher;
import org.apache.camel.spi.ShutdownAware;
import org.apache.camel.util.AsyncProcessorConverterHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RouteboxSedaConsumer extends RouteboxServiceSupport implements RouteboxConsumer, Runnable, ShutdownAware {
    private static final Logger LOG = LoggerFactory.getLogger(RouteboxSedaConsumer.class);
    protected AsyncProcessor processor;
    protected ProducerTemplate producer;

    public RouteboxSedaConsumer(RouteboxSedaEndpoint endpoint, Processor processor) {
        super(endpoint);
        this.setProcessor(AsyncProcessorConverterHelper.convert(processor));
        this.producer = endpoint.getConfig().getInnerProducerTemplate();
    }

    @Override
    protected void doStart() throws Exception {
        ((RouteboxSedaEndpoint)getRouteboxEndpoint()).onStarted(this);
        doStartInnerContext(); 
        
        // Create a URI link from the primary context to routes in the new inner context
        int poolSize = getRouteboxEndpoint().getConfig().getThreads();
        setExecutor(getRouteboxEndpoint().getCamelContext().getExecutorServiceManager().newFixedThreadPool(this, getRouteboxEndpoint().getEndpointUri(), poolSize));
        for (int i = 0; i < poolSize; i++) {
            getExecutor().execute(this);
        }
    }

    @Override
    protected void doStop() throws Exception {
        ((RouteboxSedaEndpoint)getRouteboxEndpoint()).onStopped(this);
        // Shutdown the executor
        getRouteboxEndpoint().getCamelContext().getExecutorServiceManager().shutdown(getExecutor());
        setExecutor(null);
        
        doStopInnerContext(); 
    }
    
    public void run() {
        BlockingQueue<Exchange> queue = ((RouteboxSedaEndpoint)getRouteboxEndpoint()).getQueue();
        while (queue != null && isRunAllowed()) {
            try {
                final Exchange exchange = queue.poll(getRouteboxEndpoint().getConfig().getPollInterval(), TimeUnit.MILLISECONDS);
                dispatchToInnerRoute(queue, exchange);
            } catch (InterruptedException e) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Sleep interrupted, are we stopping? {}", isStopping() || isStopped());
                }
                continue;
            }
        }
    }
    
    private void dispatchToInnerRoute(BlockingQueue<Exchange> queue, final Exchange exchange) throws InterruptedException {
        Exchange result;

        if (exchange != null) {
            if (isRunAllowed()) {
                try {
                    LOG.debug("Dispatching to inner route: {}", exchange);
                    RouteboxDispatcher dispatcher = new RouteboxDispatcher(producer);
                    result = dispatcher.dispatchAsync(getRouteboxEndpoint(), exchange); 
                    processor.process(result, new AsyncCallback() {
                        public void done(boolean doneSync) {
                            // noop
                        }
                    });
                } catch (Exception e) {
                    getExceptionHandler().handleException("Error processing exchange", exchange, e);
                }
            } else {
                if (LOG.isWarnEnabled()) {
                    LOG.warn("This consumer is stopped during polling an exchange, so putting it back on the seda queue: " + exchange);
                }                
                queue.put(exchange);
            }
        }
    }
    
    public Endpoint getEndpoint() {
        return getRouteboxEndpoint();
    }

    public boolean deferShutdown(ShutdownRunningTask shutdownRunningTask) {
        return false;
    }

    public int getPendingExchangesSize() {
        BlockingQueue<Exchange> queue = ((RouteboxSedaEndpoint)getRouteboxEndpoint()).getQueue();
        if (queue != null) {
            return queue.size();
        }
        return 0;
    }

    @Override
    public void prepareShutdown(boolean suspendOnly, boolean forced) {
    }
    
    public void setProcessor(AsyncProcessor processor) {
        this.processor = processor;
    }

    public AsyncProcessor getProcessor() {
        return processor;
    }

}
