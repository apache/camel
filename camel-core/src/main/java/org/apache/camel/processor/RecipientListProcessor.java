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
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.builder.ErrorHandlerBuilder;
import org.apache.camel.impl.ProducerCache;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

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
 * @version $Revision$
 */
public class RecipientListProcessor extends MulticastProcessor {

    private static final transient Log LOG = LogFactory.getLog(RecipientListProcessor.class);
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

        private RecipientProcessorExchangePair(int index, ProducerCache producerCache, Endpoint endpoint, Producer producer,
                                               Processor prepared, Exchange exchange) {
            this.index = index;
            this.producerCache = producerCache;
            this.endpoint = endpoint;
            this.producer = producer;
            this.prepared = prepared;
            this.exchange = exchange;
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
            // we have already acquired and prepare the producer so we
            if (LOG.isTraceEnabled()) {
                LOG.trace("RecipientProcessorExchangePair #" + index + " begin: " + exchange);
            }
        }

        public void done() {
            if (LOG.isTraceEnabled()) {
                LOG.trace("RecipientProcessorExchangePair #" + index + " done: " + exchange);
            }
            // when we are done we should release back in pool
            try {
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

    public RecipientListProcessor(CamelContext camelContext, ProducerCache producerCache, Iterator<Object> iter, AggregationStrategy aggregationStrategy,
                                  boolean parallelProcessing, ExecutorService executorService, boolean streaming, boolean stopOnException, long timeout) {
        super(camelContext, null, aggregationStrategy, parallelProcessing, executorService, streaming, stopOnException, timeout);
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
            try {
                endpoint = resolveEndpoint(exchange, recipient);
                producer = producerCache.acquireProducer(endpoint);
            } catch (Exception e) {
                if (isIgnoreInvalidEndpoints()) {
                    LOG.info("Endpoint uri is invalid: " + recipient + ". This exception will be ignored.", e);
                    continue;
                } else {
                    // failure so break out
                    throw e;
                }
            }

            // then create the exchange pair
            result.add(createProcessorExchangePair(index++, endpoint, producer, exchange));
        }

        return result;
    }

    /**
     * This logic is similar to MulticastProcessor but we have to return a RecipientProcessorExchangePair instead
     */
    protected ProcessorExchangePair createProcessorExchangePair(int index, Endpoint endpoint, Producer producer, Exchange exchange) {
        Processor prepared = producer;

        // copy exchange, and do not share the unit of work
        Exchange copy = ExchangeHelper.createCorrelatedCopy(exchange, false);

        // set property which endpoint we send to
        setToEndpoint(copy, prepared);

        // rework error handling to support fine grained error handling
        if (exchange.getUnitOfWork() != null && exchange.getUnitOfWork().getRouteContext() != null) {
            // wrap the producer in error handler so we have fine grained error handling on
            // the output side instead of the input side
            // this is needed to support redelivery on that output alone and not doing redelivery
            // for the entire multicast block again which will start from scratch again
            RouteContext routeContext = exchange.getUnitOfWork().getRouteContext();
            ErrorHandlerBuilder builder = routeContext.getRoute().getErrorHandlerBuilder();
            // create error handler (create error handler directly to keep it light weight,
            // instead of using ProcessorDefinition.wrapInErrorHandler)
            try {
                prepared = builder.createErrorHandler(routeContext, prepared);
                // and wrap in unit of work processor so the copy exchange also can run under UoW
                prepared = new UnitOfWorkProcessor(prepared);
            } catch (Exception e) {
                throw ObjectHelper.wrapRuntimeCamelException(e);
            }
        }

        return new RecipientProcessorExchangePair(index, producerCache, endpoint, producer, prepared, copy);
    }

    protected static Endpoint resolveEndpoint(Exchange exchange, Object recipient) {
        // trim strings as end users might have added spaces between separators
        if (recipient instanceof String) {
            recipient = ((String) recipient).trim();
        }
        return ExchangeHelper.resolveEndpoint(exchange, recipient);
    }

    protected void doStart() throws Exception {
        super.doStart();
        if (producerCache == null) {
            producerCache = new ProducerCache(this, getCamelContext());
            // add it as a service so we can manage it
            getCamelContext().addService(producerCache);
        }
        ServiceHelper.startService(producerCache);
    }

    protected void doStop() throws Exception {
        ServiceHelper.stopService(producerCache);
        super.doStop();
    }

}
