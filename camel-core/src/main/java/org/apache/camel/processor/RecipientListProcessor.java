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

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.ProducerCache;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.EndpointHelper;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.MessageHelper;
import org.apache.camel.util.ServiceHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements a dynamic <a
 * href="http://camel.apache.org/recipient-list.html">Recipient List</a>
 * pattern where the list of actual endpoints to send a message exchange to are
 * dependent on some dynamic expression.
 * <p/>
 * This implementation is a specialized {@link org.apache.camel.processor.MulticastProcessor} which is based
 * on recipient lists. This implementation have to handle the fact the processors is not known at design time
 * but evaluated at runtime from the dynamic recipient list. Therefore this implementation have to at runtime
 * lookup endpoints and create producers which should act as the processors for the multicast processors which
 * runs under the hood. Also this implementation supports the asynchronous routing engine which makes the code
 * more trickier.
 *
 * @version 
 */
public class RecipientListProcessor extends MulticastProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(RecipientListProcessor.class);
    private final Iterator<Object> iter;
    private boolean ignoreInvalidEndpoints;
    private ProducerCache producerCache;

    /**
     * Class that represent each step in the recipient list to do
     * <p/>
     * This implementation ensures the provided producer is being released back in the producer cache when
     * its done using it.
     */
    static final class RecipientProcessorExchangePair implements ProcessorExchangePair {
        private final int index;
        private final Endpoint endpoint;
        private final Producer producer;
        private Processor prepared;
        private final Exchange exchange;
        private final ProducerCache producerCache;
        private final ExchangePattern pattern;
        private volatile ExchangePattern originalPattern;

        private RecipientProcessorExchangePair(int index, ProducerCache producerCache, Endpoint endpoint, Producer producer,
                                               Processor prepared, Exchange exchange, ExchangePattern pattern) {
            this.index = index;
            this.producerCache = producerCache;
            this.endpoint = endpoint;
            this.producer = producer;
            this.prepared = prepared;
            this.exchange = exchange;
            this.pattern = pattern;
        }

        public int getIndex() {
            return index;
        }

        public Exchange getExchange() {
            return exchange;
        }

        public Producer getProducer() {
            return producer;
        }

        public Processor getProcessor() {
            return prepared;
        }

        public void begin() {
            // we have already acquired and prepare the producer
            LOG.trace("RecipientProcessorExchangePair #{} begin: {}", index, exchange);
            exchange.setProperty(Exchange.RECIPIENT_LIST_ENDPOINT, endpoint.getEndpointUri());
            // ensure stream caching is reset
            MessageHelper.resetStreamCache(exchange.getIn());
            // if the MEP on the endpoint is different then
            if (pattern != null) {
                originalPattern = exchange.getPattern();
                LOG.trace("Using exchangePattern: {} on exchange: {}", pattern, exchange);
                exchange.setPattern(pattern);
            }
        }

        public void done() {
            LOG.trace("RecipientProcessorExchangePair #{} done: {}", index, exchange);
            try {
                // preserve original MEP
                if (originalPattern != null) {
                    exchange.setPattern(originalPattern);
                }
                // when we are done we should release back in pool
                producerCache.releaseProducer(endpoint, producer);
            } catch (Exception e) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Error releasing producer: " + producer + ". This exception will be ignored.", e);
                }
            }
        }

    }

    public RecipientListProcessor(CamelContext camelContext, ProducerCache producerCache, Iterator<Object> iter) {
        super(camelContext, null);
        this.producerCache = producerCache;
        this.iter = iter;
    }

    public RecipientListProcessor(CamelContext camelContext, ProducerCache producerCache, Iterator<Object> iter, AggregationStrategy aggregationStrategy) {
        super(camelContext, null, aggregationStrategy);
        this.producerCache = producerCache;
        this.iter = iter;
    }

    @Deprecated
    public RecipientListProcessor(CamelContext camelContext, ProducerCache producerCache, Iterator<Object> iter, AggregationStrategy aggregationStrategy,
                                  boolean parallelProcessing, ExecutorService executorService, boolean shutdownExecutorService,
                                  boolean streaming, boolean stopOnException, long timeout, Processor onPrepare, boolean shareUnitOfWork) {
        super(camelContext, null, aggregationStrategy, parallelProcessing, executorService, shutdownExecutorService,
                streaming, stopOnException, timeout, onPrepare, shareUnitOfWork, false);
        this.producerCache = producerCache;
        this.iter = iter;
    }

    public RecipientListProcessor(CamelContext camelContext, ProducerCache producerCache, Iterator<Object> iter, AggregationStrategy aggregationStrategy,
                                  boolean parallelProcessing, ExecutorService executorService, boolean shutdownExecutorService,
                                  boolean streaming, boolean stopOnException, long timeout, Processor onPrepare, boolean shareUnitOfWork, boolean parallelAggregate) {
        this(camelContext, producerCache, iter, aggregationStrategy, parallelProcessing, executorService, shutdownExecutorService, streaming, stopOnException, timeout, onPrepare,
             shareUnitOfWork, parallelAggregate, false);
    }

    public RecipientListProcessor(CamelContext camelContext, ProducerCache producerCache, Iterator<Object> iter, AggregationStrategy aggregationStrategy,
                                  boolean parallelProcessing, ExecutorService executorService, boolean shutdownExecutorService, boolean streaming, boolean stopOnException,
                                  long timeout, Processor onPrepare, boolean shareUnitOfWork, boolean parallelAggregate, boolean stopOnAggregateException) {
        super(camelContext, null, aggregationStrategy, parallelProcessing, executorService, shutdownExecutorService, streaming, stopOnException, timeout, onPrepare,
              shareUnitOfWork, parallelAggregate, stopOnAggregateException);
        this.producerCache = producerCache;
        this.iter = iter;
    }

    public boolean isIgnoreInvalidEndpoints() {
        return ignoreInvalidEndpoints;
    }

    public void setIgnoreInvalidEndpoints(boolean ignoreInvalidEndpoints) {
        this.ignoreInvalidEndpoints = ignoreInvalidEndpoints;
    }

    @Override
    protected Iterable<ProcessorExchangePair> createProcessorExchangePairs(Exchange exchange) throws Exception {
        // here we iterate the recipient lists and create the exchange pair for each of those
        List<ProcessorExchangePair> result = new ArrayList<ProcessorExchangePair>();

        // at first we must lookup the endpoint and acquire the producer which can send to the endpoint
        int index = 0;
        while (iter.hasNext()) {
            Object recipient = iter.next();
            Endpoint endpoint;
            Producer producer;
            ExchangePattern pattern;
            try {
                endpoint = resolveEndpoint(exchange, recipient);
                pattern = resolveExchangePattern(recipient);
                producer = producerCache.acquireProducer(endpoint);
            } catch (Exception e) {
                if (isIgnoreInvalidEndpoints()) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Endpoint uri is invalid: " + recipient + ". This exception will be ignored.", e);
                    }
                    continue;
                } else {
                    // failure so break out
                    throw e;
                }
            }

            // then create the exchange pair
            result.add(createProcessorExchangePair(index++, endpoint, producer, exchange, pattern));
        }

        return result;
    }

    /**
     * This logic is similar to MulticastProcessor but we have to return a RecipientProcessorExchangePair instead
     */
    protected ProcessorExchangePair createProcessorExchangePair(int index, Endpoint endpoint, Producer producer, Exchange exchange, ExchangePattern pattern) {
        Processor prepared = producer;

        // copy exchange, and do not share the unit of work
        Exchange copy = ExchangeHelper.createCorrelatedCopy(exchange, false);

        // if we share unit of work, we need to prepare the child exchange
        if (isShareUnitOfWork()) {
            prepareSharedUnitOfWork(copy, exchange);
        }

        // set property which endpoint we send to
        setToEndpoint(copy, prepared);

        // rework error handling to support fine grained error handling
        RouteContext routeContext = exchange.getUnitOfWork() != null ? exchange.getUnitOfWork().getRouteContext() : null;
        prepared = createErrorHandler(routeContext, copy, prepared);

        // invoke on prepare on the exchange if specified
        if (onPrepare != null) {
            try {
                onPrepare.process(copy);
            } catch (Exception e) {
                copy.setException(e);
            }
        }

        // and create the pair
        return new RecipientProcessorExchangePair(index, producerCache, endpoint, producer, prepared, copy, pattern);
    }

    protected static Endpoint resolveEndpoint(Exchange exchange, Object recipient) {
        // trim strings as end users might have added spaces between separators
        if (recipient instanceof String) {
            recipient = ((String) recipient).trim();
        }
        return ExchangeHelper.resolveEndpoint(exchange, recipient);
    }

    protected ExchangePattern resolveExchangePattern(Object recipient) throws UnsupportedEncodingException, URISyntaxException, MalformedURLException {
        // trim strings as end users might have added spaces between separators
        if (recipient instanceof String) {
            String s = ((String) recipient).trim();
            // see if exchangePattern is a parameter in the url
            s = URISupport.normalizeUri(s);
            return EndpointHelper.resolveExchangePatternFromUrl(s);
        }
        return null;
    }

    protected void doStart() throws Exception {
        super.doStart();
        if (producerCache == null) {
            producerCache = new ProducerCache(this, getCamelContext());
        }
        ServiceHelper.startService(producerCache);
    }

    protected void doStop() throws Exception {
        ServiceHelper.stopService(producerCache);
        super.doStop();
    }

    protected void doShutdown() throws Exception {
        ServiceHelper.stopAndShutdownService(producerCache);
        super.doShutdown();
    }

    @Override
    public String toString() {
        return "RecipientList";
    }

    @Override
    public String getTraceLabel() {
        return "recipientList";
    }
}
