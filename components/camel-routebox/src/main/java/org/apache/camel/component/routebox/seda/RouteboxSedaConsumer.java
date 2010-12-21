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
import org.apache.camel.impl.LoggingExceptionHandler;
import org.apache.camel.impl.converter.AsyncProcessorTypeConverter;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.spi.ShutdownAware;
import org.apache.camel.util.AsyncProcessorHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class RouteboxSedaConsumer extends RouteboxServiceSupport implements RouteboxConsumer, Runnable, ShutdownAware {
    private static final transient Log LOG = LogFactory.getLog(RouteboxSedaConsumer.class);
    protected AsyncProcessor processor;
    protected ProducerTemplate producer;
    private int pendingExchanges;
    private ExceptionHandler exceptionHandler;
    
    public RouteboxSedaConsumer(RouteboxSedaEndpoint endpoint, Processor processor) {
        super(endpoint);
        this.setProcessor(AsyncProcessorTypeConverter.convert(processor));
        producer = endpoint.getConfig().getInnerProducerTemplate();
        producer.setMaximumCacheSize(endpoint.getConfig().getThreads());
        if (exceptionHandler == null) {
            exceptionHandler = new LoggingExceptionHandler(getClass());
        }
    }


    /* (non-Javadoc)
     * @see org.apache.camel.impl.ServiceSupport#doStart()
     */
    @Override
    protected void doStart() throws Exception {
        ((RouteboxSedaEndpoint)getRouteboxEndpoint()).onStarted(this);
        doStartInnerContext(); 
        
        // Create a URI link from the primary context to routes in the new inner context
        int poolSize = getRouteboxEndpoint().getConfig().getThreads();
        setExecutor(((RouteboxSedaEndpoint)getRouteboxEndpoint()).getCamelContext().getExecutorServiceStrategy()
                        .newFixedThreadPool(this, ((RouteboxSedaEndpoint)getRouteboxEndpoint()).getEndpointUri(), poolSize));
        for (int i = 0; i < poolSize; i++) {
            getExecutor().execute((Runnable) this);
        }
    }

    /* (non-Javadoc)
     * @see org.apache.camel.impl.ServiceSupport#doStop()
     */
    @Override
    protected void doStop() throws Exception {
        ((RouteboxSedaEndpoint)getRouteboxEndpoint()).onStopped(this);
        // Shutdown the executor
        ((RouteboxSedaEndpoint)getRouteboxEndpoint()).getCamelContext().getExecutorServiceStrategy().shutdown(getExecutor());
        setExecutor(null);
        
        doStopInnerContext(); 
    }
    
    /* (non-Javadoc)
     * @see java.lang.Runnable#run()
     */
    public void run() {       
        BlockingQueue<Exchange> queue = ((RouteboxSedaEndpoint)getRouteboxEndpoint()).getQueue();
        while (queue != null && isRunAllowed()) {
            try {
                final Exchange exchange = queue.poll(getRouteboxEndpoint().getConfig().getPollInterval(), TimeUnit.MILLISECONDS);
                dispatchToInnerRoute(queue, exchange);
            } catch (InterruptedException e) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Sleep interrupted, are we stopping? " + (isStopping() || isStopped()));
                }
                continue;
            }
        }
    }
    
    private void dispatchToInnerRoute(BlockingQueue<Exchange> queue, final Exchange exchange) throws InterruptedException {
        Exchange result = null;

        if (exchange != null) {
            if (isRunAllowed()) {
                try {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("**** Dispatching to Inner Route ****");
                    }
                    RouteboxDispatcher dispatcher = new RouteboxDispatcher(producer);
                    result = dispatcher.dispatchAsync(getRouteboxEndpoint(), exchange); 
                    AsyncProcessorHelper.process(processor, result, new AsyncCallback() {
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
    
    
    /* (non-Javadoc)
     * @see org.apache.camel.Consumer#getEndpoint()
     */
    public Endpoint getEndpoint() {
        return (Endpoint) getRouteboxEndpoint();
    }

    /* (non-Javadoc)
     * @see org.apache.camel.spi.ShutdownAware#deferShutdown(org.apache.camel.ShutdownRunningTask)
     */
    public boolean deferShutdown(ShutdownRunningTask shutdownRunningTask) {
        return false;
    }

    /* (non-Javadoc)
     * @see org.apache.camel.spi.ShutdownAware#getPendingExchangesSize()
     */
    public int getPendingExchangesSize() {
        return getPendingExchanges();
    }
    
    /* (non-Javadoc)
     * @see org.apache.camel.spi.ShutdownAware#prepareShutdown()
     */
    public void prepareShutdown() {
        
    }
    
    public void setProcessor(AsyncProcessor processor) {
        this.processor = processor;
    }

    public AsyncProcessor getProcessor() {
        return processor;
    }

    public void setPendingExchanges(int pendingExchanges) {
        this.pendingExchanges = pendingExchanges;
    }

    public int getPendingExchanges() {
        return pendingExchanges;
    }

    public void setExceptionHandler(ExceptionHandler exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
    }

    public ExceptionHandler getExceptionHandler() {
        return exceptionHandler;
    }
    
}
