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

import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.impl.engine.DefaultProducerCache;
import org.apache.camel.impl.engine.EmptyProducerCache;
import org.apache.camel.processor.aggregate.UseLatestAggregationStrategy;
import org.apache.camel.spi.EndpointUtilizationStatistics;
import org.apache.camel.spi.IdAware;
import org.apache.camel.spi.ProducerCache;
import org.apache.camel.spi.RouteIdAware;
import org.apache.camel.support.AsyncProcessorSupport;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.util.ObjectHelper.notNull;

/**
 * Implements a dynamic <a
 * href="http://camel.apache.org/recipient-list.html">Recipient List</a>
 * pattern where the list of actual endpoints to send a message exchange to are
 * dependent on some dynamic expression.
 */
public class RecipientList extends AsyncProcessorSupport implements IdAware, RouteIdAware {

    private static final Logger LOG = LoggerFactory.getLogger(RecipientList.class);

    private static final String IGNORE_DELIMITER_MARKER = "false";
    private final CamelContext camelContext;
    private String id;
    private String routeId;
    private ProducerCache producerCache;
    private Expression expression;
    private final String delimiter;
    private boolean parallelProcessing;
    private boolean parallelAggregate;
    private boolean stopOnAggregateException;
    private boolean stopOnException;
    private boolean ignoreInvalidEndpoints;
    private boolean streaming;
    private long timeout;
    private int cacheSize;
    private Processor onPrepare;
    private boolean shareUnitOfWork;
    private ExecutorService executorService;
    private boolean shutdownExecutorService;
    private volatile ExecutorService aggregateExecutorService;
    private AggregationStrategy aggregationStrategy = new UseLatestAggregationStrategy();

    public RecipientList(CamelContext camelContext) {
        // use comma by default as delimiter
        this(camelContext, ",");
    }

    public RecipientList(CamelContext camelContext, String delimiter) {
        notNull(camelContext, "camelContext");
        StringHelper.notEmpty(delimiter, "delimiter");
        this.camelContext = camelContext;
        this.delimiter = delimiter;
    }

    public RecipientList(CamelContext camelContext, Expression expression) {
        // use comma by default as delimiter
        this(camelContext, expression, ",");
    }

    public RecipientList(CamelContext camelContext, Expression expression, String delimiter) {
        notNull(camelContext, "camelContext");
        org.apache.camel.util.ObjectHelper.notNull(expression, "expression");
        StringHelper.notEmpty(delimiter, "delimiter");
        this.camelContext = camelContext;
        this.expression = expression;
        this.delimiter = delimiter;
    }

    /**
     * Wrap {@link RecipientList} in {@link Pipeline}.
     */
    private final class RecipientListPipeline extends Pipeline {

        private final RecipientList recipientList;

        public RecipientListPipeline(RecipientList recipientList, CamelContext camelContext, Collection<Processor> processors) {
            super(camelContext, processors);
            this.recipientList = recipientList;
        }

        @Override
        public void setId(String id) {
            // we want to set the id on the recipient list and not this wrapping pipeline
            recipientList.setId(id);
        }

        @Override
        public String getId() {
            return null;
        }

        @Override
        public String toString() {
            return null;
        }
    }

    public Processor newPipeline(CamelContext camelContext, Collection<Processor> processors) {
        return new RecipientListPipeline(this, camelContext, processors);
    }

    @Override
    public String toString() {
        return id;
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
    public boolean process(Exchange exchange, AsyncCallback callback) {
        if (!isStarted()) {
            throw new IllegalStateException("RecipientList has not been started: " + this);
        }

        // use the evaluate expression result if exists
        Object recipientList = exchange.removeProperty(Exchange.EVALUATE_EXPRESSION_RESULT);
        if (recipientList == null && expression != null) {
            // fallback and evaluate the expression
            recipientList = expression.evaluate(exchange, Object.class);
        }

        return sendToRecipientList(exchange, recipientList, callback);
    }

    /**
     * Sends the given exchange to the recipient list
     */
    public boolean sendToRecipientList(Exchange exchange, Object recipientList, AsyncCallback callback) {
        Iterator<?> iter;

        if (delimiter != null && delimiter.equalsIgnoreCase(IGNORE_DELIMITER_MARKER)) {
            iter = ObjectHelper.createIterator(recipientList, null);
        } else {
            iter = ObjectHelper.createIterator(recipientList, delimiter);
        }

        RecipientListProcessor rlp = new RecipientListProcessor(exchange.getContext(), null, producerCache, iter, getAggregationStrategy(),
                isParallelProcessing(), getExecutorService(), isShutdownExecutorService(),
                isStreaming(), isStopOnException(), getTimeout(), getOnPrepare(), isShareUnitOfWork(), isParallelAggregate(),
                isStopOnAggregateException());
        rlp.setAggregateExecutorService(aggregateExecutorService);
        rlp.setIgnoreInvalidEndpoints(isIgnoreInvalidEndpoints());
        rlp.setCacheSize(getCacheSize());
        rlp.setId(getId());
        rlp.setRouteId(getRouteId());

        // start ourselves
        try {
            ServiceHelper.startService(rlp);
        } catch (Exception e) {
            exchange.setException(e);
            callback.done(true);
            return true;
        }

        // now let the multicast process the exchange
        return rlp.process(exchange, callback);
    }

    public EndpointUtilizationStatistics getEndpointUtilizationStatistics() {
        return producerCache.getEndpointUtilizationStatistics();
    }

    @Override
    protected void doStart() throws Exception {
        if (producerCache == null) {
            if (cacheSize < 0) {
                producerCache = new EmptyProducerCache(this, camelContext);
                LOG.debug("RecipientList {} is not using ProducerCache", this);
            } else {
                producerCache = new DefaultProducerCache(this, camelContext, cacheSize);
                LOG.debug("RecipientList {} using ProducerCache with cacheSize={}", this, cacheSize);
            }
        }
        if (timeout > 0) {
            // use a cached thread pool so we each on-the-fly task has a dedicated thread to process completions as they come in
            aggregateExecutorService = camelContext.getExecutorServiceManager().newScheduledThreadPool(this, "RecipientList-AggregateTask", 0);
        }
        ServiceHelper.startService(aggregationStrategy, producerCache);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(producerCache, aggregationStrategy);
    }

    @Override
    protected void doShutdown() throws Exception {
        ServiceHelper.stopAndShutdownServices(producerCache, aggregationStrategy);

        if (aggregateExecutorService != null) {
            camelContext.getExecutorServiceManager().shutdownNow(aggregateExecutorService);
        }
        if (shutdownExecutorService && executorService != null) {
            camelContext.getExecutorServiceManager().shutdownNow(executorService);
        }
    }

    public Expression getExpression() {
        return expression;
    }

    public String getDelimiter() {
        return delimiter;
    }

    public boolean isStreaming() {
        return streaming;
    }
    
    public void setStreaming(boolean streaming) {
        this.streaming = streaming;
    }
 
    public boolean isIgnoreInvalidEndpoints() {
        return ignoreInvalidEndpoints;
    }
    
    public void setIgnoreInvalidEndpoints(boolean ignoreInvalidEndpoints) {
        this.ignoreInvalidEndpoints = ignoreInvalidEndpoints;
    }

    public boolean isParallelProcessing() {
        return parallelProcessing;
    }

    public void setParallelProcessing(boolean parallelProcessing) {
        this.parallelProcessing = parallelProcessing;
    }

    public boolean isParallelAggregate() {
        return parallelAggregate;
    }

    public void setParallelAggregate(boolean parallelAggregate) {
        this.parallelAggregate = parallelAggregate;
    }

    public boolean isStopOnAggregateException() {
        return stopOnAggregateException;
    }

    public void setStopOnAggregateException(boolean stopOnAggregateException) {
        this.stopOnAggregateException = stopOnAggregateException;
    }

    public boolean isStopOnException() {
        return stopOnException;
    }

    public void setStopOnException(boolean stopOnException) {
        this.stopOnException = stopOnException;
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }

    public void setExecutorService(ExecutorService executorService) {
        this.executorService = executorService;
    }

    public boolean isShutdownExecutorService() {
        return shutdownExecutorService;
    }

    public void setShutdownExecutorService(boolean shutdownExecutorService) {
        this.shutdownExecutorService = shutdownExecutorService;
    }

    public AggregationStrategy getAggregationStrategy() {
        return aggregationStrategy;
    }

    public void setAggregationStrategy(AggregationStrategy aggregationStrategy) {
        this.aggregationStrategy = aggregationStrategy;
    }

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public Processor getOnPrepare() {
        return onPrepare;
    }

    public void setOnPrepare(Processor onPrepare) {
        this.onPrepare = onPrepare;
    }

    public boolean isShareUnitOfWork() {
        return shareUnitOfWork;
    }

    public void setShareUnitOfWork(boolean shareUnitOfWork) {
        this.shareUnitOfWork = shareUnitOfWork;
    }

    public int getCacheSize() {
        return cacheSize;
    }

    public void setCacheSize(int cacheSize) {
        this.cacheSize = cacheSize;
    }
}
