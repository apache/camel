/*
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

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.LongAdder;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.ShutdownRunningTask;
import org.apache.camel.StreamCache;
import org.apache.camel.Traceable;
import org.apache.camel.spi.EndpointUtilizationStatistics;
import org.apache.camel.spi.IdAware;
import org.apache.camel.spi.ProcessorExchangeFactory;
import org.apache.camel.spi.RouteIdAware;
import org.apache.camel.spi.ShutdownAware;
import org.apache.camel.support.AsyncProcessorConverterHelper;
import org.apache.camel.support.AsyncProcessorSupport;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Processor for wire tapping exchanges to an endpoint destination.
 */
public class WireTapProcessor extends AsyncProcessorSupport
        implements Traceable, ShutdownAware, IdAware, RouteIdAware, CamelContextAware {

    private static final Logger LOG = LoggerFactory.getLogger(WireTapProcessor.class);

    private String id;
    private String routeId;
    private CamelContext camelContext;
    private final SendDynamicProcessor dynamicSendProcessor; // is only used for reporting statistics
    private final String uri;
    private final boolean dynamicUri;
    private final Processor processor;
    private final AsyncProcessor asyncProcessor;
    private final ExchangePattern exchangePattern;
    private final boolean copy;
    private final ExecutorService executorService;
    private final boolean shutdownExecutorService;
    private final LongAdder taskCount = new LongAdder();
    private ProcessorExchangeFactory processorExchangeFactory;
    private PooledExchangeTaskFactory taskFactory;
    private Processor onPrepare;

    public WireTapProcessor(SendDynamicProcessor dynamicSendProcessor, Processor processor, String uri,
                            ExchangePattern exchangePattern, boolean copy,
                            ExecutorService executorService, boolean shutdownExecutorService, boolean dynamicUri) {
        this.dynamicSendProcessor = dynamicSendProcessor;
        this.uri = uri;
        this.processor = processor;
        this.asyncProcessor = AsyncProcessorConverterHelper.convert(processor);
        this.exchangePattern = exchangePattern;
        this.copy = copy;
        ObjectHelper.notNull(executorService, "executorService");
        this.executorService = executorService;
        this.shutdownExecutorService = shutdownExecutorService;
        this.dynamicUri = dynamicUri;
    }

    private final class WireTapTask implements PooledExchangeTask, Runnable {

        private Exchange exchange;
        private final AsyncCallback callback = new AsyncCallback() {
            @Override
            public void done(boolean doneSync) {
                taskCount.decrement();
                if (processorExchangeFactory != null) {
                    processorExchangeFactory.release(exchange);
                }
                taskFactory.release(WireTapTask.this);
            }
        };

        @Override
        public void prepare(Exchange exchange, AsyncCallback callback) {
            this.exchange = exchange;
            // we use our own callback
        }

        @Override
        public void reset() {
            this.exchange = null;
        }

        @Override
        public void run() {
            taskCount.increment();
            LOG.debug(">>>> (wiretap) {} {}", uri, exchange);
            asyncProcessor.process(exchange, callback);
        }
    }

    @Override
    public String toString() {
        return id;
    }

    @Override
    public String getTraceLabel() {
        return "wireTap(" + uri + ")";
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String getRouteId() {
        return routeId;
    }

    @Override
    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public boolean deferShutdown(ShutdownRunningTask shutdownRunningTask) {
        // not in use
        return true;
    }

    @Override
    public int getPendingExchangesSize() {
        return taskCount.intValue();
    }

    @Override
    public void prepareShutdown(boolean suspendOnly, boolean forced) {
        // noop
    }

    public EndpointUtilizationStatistics getEndpointUtilizationStatistics() {
        if (dynamicSendProcessor != null) {
            return dynamicSendProcessor.getEndpointUtilizationStatistics();
        } else {
            return null;
        }
    }

    @Override
    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        if (!isStarted()) {
            throw new IllegalStateException("WireTapProcessor has not been started: " + this);
        }

        // must configure the wire tap beforehand
        Exchange target;
        try {
            target = configureExchange(exchange, exchangePattern);
        } catch (Exception e) {
            exchange.setException(e);
            callback.done(true);
            return true;
        }

        // send the exchange to the destination using an executor service
        try {
            // create task which has state used during routing
            PooledExchangeTask task = taskFactory.acquire(target, null);
            executorService.submit(task);
        } catch (Exception e) {
            // in case the thread pool rejects or cannot submit the task then we need to catch
            // so camel error handler can react
            exchange.setException(e);
        }

        // continue routing this synchronously
        callback.done(true);
        return true;
    }

    protected Exchange configureExchange(Exchange exchange, ExchangePattern pattern) throws IOException {
        Exchange answer;
        if (copy) {
            // use a copy of the original exchange
            answer = configureCopyExchange(exchange);
        } else {
            // use a new exchange
            answer = configureNewExchange(exchange);
        }

        // if the body is a stream cache we must use a copy of the stream in the wire tapped exchange
        Message msg = answer.getMessage();
        if (msg.getBody() instanceof StreamCache) {
            // in parallel processing case, the stream must be copied, therefore get the stream
            StreamCache cache = (StreamCache) msg.getBody();
            StreamCache copied = cache.copy(answer);
            if (copied != null) {
                msg.setBody(copied);
            }
        }

        // invoke on prepare on the exchange if specified
        if (onPrepare != null) {
            try {
                onPrepare.process(answer);
            } catch (Exception e) {
                throw RuntimeCamelException.wrapRuntimeCamelException(e);
            }
        }

        return answer;
    }

    private Exchange configureCopyExchange(Exchange exchange) {
        // must use a copy as we dont want it to cause side effects of the original exchange
        Exchange copy = processorExchangeFactory.createCorrelatedCopy(exchange, false);
        // should not be correlated, but we needed to copy without handover
        copy.removeProperty(ExchangePropertyKey.CORRELATION_ID);
        // set MEP to InOnly as this wire tap is a fire and forget
        copy.setPattern(ExchangePattern.InOnly);
        // move OUT to IN if needed
        if (copy.hasOut()) {
            copy.setIn(copy.getOut());
            copy.setOut(null);
        }
        // remove STREAM_CACHE_UNIT_OF_WORK property because this wire tap will
        // close its own created stream cache(s)
        copy.removeProperty(ExchangePropertyKey.STREAM_CACHE_UNIT_OF_WORK);
        return copy;
    }

    private Exchange configureNewExchange(Exchange exchange) {
        return processorExchangeFactory.create(exchange.getFromEndpoint(), ExchangePattern.InOnly);
    }

    public boolean isCopy() {
        return copy;
    }

    public Processor getOnPrepare() {
        return onPrepare;
    }

    public void setOnPrepare(Processor onPrepare) {
        this.onPrepare = onPrepare;
    }

    public String getUri() {
        return uri;
    }

    public int getCacheSize() {
        if (dynamicSendProcessor != null) {
            return dynamicSendProcessor.getCacheSize();
        } else {
            return 0;
        }
    }

    public boolean isIgnoreInvalidEndpoint() {
        if (dynamicSendProcessor != null) {
            return dynamicSendProcessor.isIgnoreInvalidEndpoint();
        } else {
            return false;
        }
    }

    public boolean isDynamicUri() {
        return dynamicUri;
    }

    @Override
    protected void doBuild() throws Exception {
        // create a per processor exchange factory
        this.processorExchangeFactory = getCamelContext().getCamelContextExtension()
                .getProcessorExchangeFactory().newProcessorExchangeFactory(this);
        this.processorExchangeFactory.setRouteId(getRouteId());
        this.processorExchangeFactory.setId(getId());

        boolean pooled = camelContext.getCamelContextExtension().getExchangeFactory().isPooled();
        if (pooled) {
            taskFactory = new PooledTaskFactory(getId()) {
                @Override
                public PooledExchangeTask create(Exchange exchange, AsyncCallback callback) {
                    return new WireTapTask();
                }
            };
            int capacity = camelContext.getCamelContextExtension().getExchangeFactory().getCapacity();
            taskFactory.setCapacity(capacity);
        } else {
            taskFactory = new PrototypeTaskFactory() {
                @Override
                public PooledExchangeTask create(Exchange exchange, AsyncCallback callback) {
                    return new WireTapTask();
                }
            };
        }
        LOG.trace("Using TaskFactory: {}", taskFactory);

        ServiceHelper.buildService(processorExchangeFactory, taskFactory, processor);
    }

    @Override
    protected void doInit() throws Exception {
        ServiceHelper.initService(processorExchangeFactory, taskFactory, processor);
    }

    @Override
    protected void doStart() throws Exception {
        ServiceHelper.startService(processorExchangeFactory, taskFactory, processor);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(processorExchangeFactory, taskFactory, processor);
    }

    @Override
    protected void doShutdown() throws Exception {
        ServiceHelper.stopAndShutdownServices(processorExchangeFactory, taskFactory, processor);
        if (shutdownExecutorService) {
            getCamelContext().getExecutorServiceManager().shutdownNow(executorService);
        }
    }
}
