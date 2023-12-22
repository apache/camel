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

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.AsyncProducer;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Expression;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.Route;
import org.apache.camel.spi.NormalizedEndpointUri;
import org.apache.camel.spi.ProducerCache;
import org.apache.camel.support.AsyncProcessorConverterHelper;
import org.apache.camel.support.EndpointHelper;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.support.service.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements a dynamic <a href="http://camel.apache.org/recipient-list.html">Recipient List</a> pattern where the list
 * of actual endpoints to send a message exchange to are dependent on some dynamic expression.
 * <p/>
 * This implementation is a specialized {@link org.apache.camel.processor.MulticastProcessor} which is based on
 * recipient lists. This implementation have to handle the fact the processors is not known at design time but evaluated
 * at runtime from the dynamic recipient list. Therefore this implementation have to at runtime lookup endpoints and
 * create producers which should act as the processors for the multicast processors which runs under the hood. Also this
 * implementation supports the asynchronous routing engine which makes the code more trickier.
 */
public class RecipientListProcessor extends MulticastProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(RecipientListProcessor.class);

    private static final String IGNORE_DELIMITER_MARKER = "false";

    private boolean ignoreInvalidEndpoints;
    private final Expression expression;
    private final String delimiter;
    private final ProducerCache producerCache;
    private int cacheSize;
    private Map<String, Object> txData;

    /**
     * Class that represent each step in the recipient list to do
     * <p/>
     * This implementation ensures the provided producer is being released back in the producer cache when its done
     * using it.
     */
    static final class RecipientProcessorExchangePair implements ProcessorExchangePair {
        private final int index;
        private final Endpoint endpoint;
        private final AsyncProducer producer;
        private final Processor prepared;
        private final Exchange exchange;
        private final ProducerCache producerCache;
        private final ExchangePattern pattern;
        private volatile ExchangePattern originalPattern;
        private final boolean prototypeEndpoint;

        private RecipientProcessorExchangePair(int index, ProducerCache producerCache, Endpoint endpoint, Producer producer,
                                               Processor prepared, Exchange exchange, ExchangePattern pattern,
                                               boolean prototypeEndpoint) {
            this.index = index;
            this.producerCache = producerCache;
            this.endpoint = endpoint;
            this.producer = AsyncProcessorConverterHelper.convert(producer);
            this.prepared = prepared;
            this.exchange = exchange;
            this.pattern = pattern;
            this.prototypeEndpoint = prototypeEndpoint;
        }

        @Override
        public int getIndex() {
            return index;
        }

        @Override
        public Exchange getExchange() {
            return exchange;
        }

        @Override
        public Producer getProducer() {
            return producer;
        }

        @Override
        public Processor getProcessor() {
            return prepared;
        }

        @Override
        public void begin() {
            // we have already acquired and prepare the producer
            LOG.trace("RecipientProcessorExchangePair #{} begin: {}", index, exchange);
            exchange.setProperty(ExchangePropertyKey.RECIPIENT_LIST_ENDPOINT, endpoint.getEndpointUri());
            // ensure stream caching is reset
            MessageHelper.resetStreamCache(exchange.getIn());
            // if the MEP on the endpoint is different then
            if (pattern != null) {
                originalPattern = exchange.getPattern();
                LOG.trace("Using exchangePattern: {} on exchange: {}", pattern, exchange);
                exchange.setPattern(pattern);
            }
        }

        @Override
        public void done() {
            LOG.trace("RecipientProcessorExchangePair #{} done: {}", index, exchange);
            try {
                // preserve original MEP
                if (originalPattern != null) {
                    exchange.setPattern(originalPattern);
                }
                // when we are done we should release back in pool
                producerCache.releaseProducer(endpoint, producer);
                // and stop prototype endpoints
                if (prototypeEndpoint) {
                    ServiceHelper.stopAndShutdownService(endpoint);
                }
            } catch (Exception e) {
                LOG.debug("Error releasing producer: {}. This exception will be ignored.", producer, e);
            }
        }

    }

    public RecipientListProcessor(CamelContext camelContext, Route route, Expression expression, String delimiter,
                                  ProducerCache producerCache,
                                  AggregationStrategy aggregationStrategy,
                                  boolean parallelProcessing, ExecutorService executorService, boolean shutdownExecutorService,
                                  boolean streaming, boolean stopOnException,
                                  long timeout, Processor onPrepare, boolean shareUnitOfWork, boolean parallelAggregate) {
        super(camelContext, route, null, aggregationStrategy, parallelProcessing, executorService, shutdownExecutorService,
              streaming, stopOnException, timeout, onPrepare,
              shareUnitOfWork, parallelAggregate);
        this.expression = expression;
        this.delimiter = delimiter;
        this.producerCache = producerCache;
    }

    public int getCacheSize() {
        return cacheSize;
    }

    public void setCacheSize(int cacheSize) {
        this.cacheSize = cacheSize;
    }

    public boolean isIgnoreInvalidEndpoints() {
        return ignoreInvalidEndpoints;
    }

    public void setIgnoreInvalidEndpoints(boolean ignoreInvalidEndpoints) {
        this.ignoreInvalidEndpoints = ignoreInvalidEndpoints;
    }

    @Override
    protected Iterable<ProcessorExchangePair> createProcessorExchangePairs(Exchange exchange)
            throws Exception {

        // use the evaluate expression result if exists
        Object recipientList = exchange.removeProperty(ExchangePropertyKey.EVALUATE_EXPRESSION_RESULT);
        if (recipientList == null && expression != null) {
            // fallback and evaluate the expression
            recipientList = expression.evaluate(exchange, Object.class);
        }

        // optimize for recipient without need for using delimiter
        // (if its list/collection/array type)
        if (recipientList instanceof List) {
            List<?> col = (List<?>) recipientList;
            int size = col.size();
            List<ProcessorExchangePair> result = new ArrayList<>(size);
            int index = 0;
            for (int i = 0; i < size; i++) {
                Object recipient = col.get(i);
                index = doCreateProcessorExchangePairs(exchange, recipient, result, index);
            }
            return result;
        } else if (recipientList instanceof Collection) {
            Collection<?> col = (Collection<?>) recipientList;
            int size = col.size();
            List<ProcessorExchangePair> result = new ArrayList<>(size);
            int index = 0;
            for (Object recipient : col) {
                index = doCreateProcessorExchangePairs(exchange, recipient, result, index);
            }
            return result;
        } else if (recipientList != null && recipientList.getClass().isArray()) {
            Object[] arr = (Object[]) recipientList;
            int size = Array.getLength(recipientList);
            List<ProcessorExchangePair> result = new ArrayList<>(size);
            int index = 0;
            for (int i = 0; i < size; i++) {
                Object recipient = arr[i];
                index = doCreateProcessorExchangePairs(exchange, recipient, result, index);
            }
            return result;
        }

        // okay we have to use iterator based separated by delimiter
        Iterator<?> iter;
        if (delimiter != null && delimiter.equalsIgnoreCase(IGNORE_DELIMITER_MARKER)) {
            iter = ObjectHelper.createIterator(recipientList, null);
        } else {
            iter = ObjectHelper.createIterator(recipientList, delimiter);
        }
        List<ProcessorExchangePair> result = new ArrayList<>();
        int index = 0;
        while (iter.hasNext()) {
            index = doCreateProcessorExchangePairs(exchange, iter.next(), result, index);
        }
        return result;
    }

    private int doCreateProcessorExchangePairs(
            Exchange exchange, Object recipient, List<ProcessorExchangePair> result, int index)
            throws NoTypeConversionAvailableException {
        boolean prototype = cacheSize < 0;

        Endpoint endpoint;
        Producer producer;
        ExchangePattern pattern;
        try {
            recipient = prepareRecipient(exchange, recipient);
            Endpoint existing = getExistingEndpoint(exchange, recipient);
            if (existing == null) {
                endpoint = resolveEndpoint(exchange, recipient, prototype);
            } else {
                endpoint = existing;
                // we have an existing endpoint then its not a prototype scope
                prototype = false;
            }
            pattern = resolveExchangePattern(recipient);
            producer = producerCache.acquireProducer(endpoint);
        } catch (Exception e) {
            if (isIgnoreInvalidEndpoints()) {
                LOG.debug("Endpoint uri is invalid: {}. This exception will be ignored.", recipient, e);
                return index;
            } else {
                // failure so break out
                throw e;
            }
        }

        // then create the exchange pair
        result.add(createProcessorExchangePair(index++, endpoint, producer, exchange, pattern, prototype));
        return index;
    }

    /**
     * This logic is similar to MulticastProcessor but we have to return a RecipientProcessorExchangePair instead
     */
    protected ProcessorExchangePair createProcessorExchangePair(
            int index, Endpoint endpoint, Producer producer,
            Exchange exchange, ExchangePattern pattern, boolean prototypeEndpoint) {
        // copy exchange, and do not share the unit of work
        Exchange copy = processorExchangeFactory.createCorrelatedCopy(exchange, false);
        copy.getExchangeExtension().setTransacted(exchange.isTransacted());

        // If we are in a transaction, set TRANSACTION_CONTEXT_DATA property for new exchanges to share txData
        // during the transaction.
        if (exchange.isTransacted() && copy.getProperty(Exchange.TRANSACTION_CONTEXT_DATA) == null) {
            if (txData == null) {
                txData = new ConcurrentHashMap<>();
            }
            copy.setProperty(Exchange.TRANSACTION_CONTEXT_DATA, txData);
        }

        // if we share unit of work, we need to prepare the child exchange
        if (isShareUnitOfWork()) {
            prepareSharedUnitOfWork(copy, exchange);
        }

        // set property which endpoint we send to
        setToEndpoint(copy, endpoint);

        // rework error handling to support fine grained error handling
        Route route = ExchangeHelper.getRoute(exchange);
        Processor prepared = wrapInErrorHandler(route, copy, producer);

        // invoke on prepare on the exchange if specified
        if (onPrepare != null) {
            try {
                onPrepare.process(copy);
            } catch (Exception e) {
                copy.setException(e);
            }
        }

        // and create the pair
        return new RecipientProcessorExchangePair(
                index, producerCache, endpoint, producer, prepared, copy, pattern, prototypeEndpoint);
    }

    protected static Object prepareRecipient(Exchange exchange, Object recipient) throws NoTypeConversionAvailableException {
        if (recipient instanceof Endpoint || recipient instanceof NormalizedEndpointUri) {
            return recipient;
        } else if (recipient instanceof String) {
            // trim strings as end users might have added spaces between separators
            recipient = ((String) recipient).trim();
        }
        if (recipient != null) {
            CamelContext ecc = exchange.getContext();
            String uri;
            if (recipient instanceof String) {
                uri = (String) recipient;
            } else {
                // convert to a string type we can work with
                uri = ecc.getTypeConverter().mandatoryConvertTo(String.class, exchange, recipient);
            }
            // optimize and normalize endpoint
            return ecc.getCamelContextExtension().normalizeUri(uri);
        }
        return null;
    }

    protected static Endpoint getExistingEndpoint(Exchange exchange, Object recipient) {
        if (recipient instanceof Endpoint) {
            return (Endpoint) recipient;
        }
        if (recipient != null) {
            if (recipient instanceof NormalizedEndpointUri nu) {
                CamelContext ecc = exchange.getContext();
                return ecc.getCamelContextExtension().hasEndpoint(nu);
            } else {
                String uri = recipient.toString().trim();
                return exchange.getContext().hasEndpoint(uri);
            }
        }
        return null;
    }

    protected static Endpoint resolveEndpoint(Exchange exchange, Object recipient, boolean prototype) {
        return prototype
                ? ExchangeHelper.resolvePrototypeEndpoint(exchange, recipient)
                : ExchangeHelper.resolveEndpoint(exchange, recipient);
    }

    protected ExchangePattern resolveExchangePattern(Object recipient) {
        String s = null;

        if (recipient instanceof NormalizedEndpointUri) {
            s = ((NormalizedEndpointUri) recipient).getUri();
        } else if (recipient instanceof String) {
            // trim strings as end users might have added spaces between separators
            s = ((String) recipient).trim();
        }
        if (s != null) {
            return EndpointHelper.resolveExchangePatternFromUrl(s);
        }

        return null;
    }

    protected static void setToEndpoint(Exchange exchange, Endpoint endpoint) {
        exchange.setProperty(ExchangePropertyKey.TO_ENDPOINT, endpoint.getEndpointUri());
    }

    @Override
    protected void doBuild() throws Exception {
        super.doBuild();
        ServiceHelper.buildService(producerCache);
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();
        ServiceHelper.initService(producerCache);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        ServiceHelper.startService(producerCache);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(producerCache);
        super.doStop();
    }

    @Override
    protected void doShutdown() throws Exception {
        ServiceHelper.stopAndShutdownService(producerCache);
        super.doShutdown();
    }

    @Override
    public String getTraceLabel() {
        return "recipientList";
    }
}
