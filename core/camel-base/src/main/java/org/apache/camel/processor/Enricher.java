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

import org.apache.camel.AggregationStrategy;
import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.AsyncProducer;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Expression;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.ExtendedExchange;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.impl.engine.DefaultProducerCache;
import org.apache.camel.impl.engine.EmptyProducerCache;
import org.apache.camel.spi.EndpointUtilizationStatistics;
import org.apache.camel.spi.IdAware;
import org.apache.camel.spi.NormalizedEndpointUri;
import org.apache.camel.spi.ProducerCache;
import org.apache.camel.spi.RouteIdAware;
import org.apache.camel.support.AsyncProcessorConverterHelper;
import org.apache.camel.support.AsyncProcessorSupport;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.EventHelper;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.support.ExchangeHelper.copyResultsPreservePattern;

/**
 * A content enricher that enriches input data by first obtaining additional
 * data from a <i>resource</i> represented by an endpoint <code>producer</code>
 * and second by aggregating input data and additional data. Aggregation of
 * input data and additional data is delegated to an {@link AggregationStrategy}
 * object.
 * <p/>
 * Uses a {@link org.apache.camel.Producer} to obtain the additional data as opposed to {@link PollEnricher}
 * that uses a {@link org.apache.camel.PollingConsumer}.
 *
 * @see PollEnricher
 */
public class Enricher extends AsyncProcessorSupport implements IdAware, RouteIdAware, CamelContextAware {

    private static final Logger LOG = LoggerFactory.getLogger(Enricher.class);

    private CamelContext camelContext;
    private String id;
    private String routeId;
    private ProducerCache producerCache;
    private final Expression expression;
    private AggregationStrategy aggregationStrategy;
    private boolean aggregateOnException;
    private boolean shareUnitOfWork;
    private int cacheSize;
    private boolean ignoreInvalidEndpoint;

    public Enricher(Expression expression) {
        this.expression = expression;
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

    public Expression getExpression() {
        return expression;
    }

    public EndpointUtilizationStatistics getEndpointUtilizationStatistics() {
        return producerCache.getEndpointUtilizationStatistics();
    }

    public void setAggregationStrategy(AggregationStrategy aggregationStrategy) {
        this.aggregationStrategy = aggregationStrategy;
    }

    public AggregationStrategy getAggregationStrategy() {
        return aggregationStrategy;
    }

    public boolean isAggregateOnException() {
        return aggregateOnException;
    }

    public void setAggregateOnException(boolean aggregateOnException) {
        this.aggregateOnException = aggregateOnException;
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

    public boolean isIgnoreInvalidEndpoint() {
        return ignoreInvalidEndpoint;
    }

    public void setIgnoreInvalidEndpoint(boolean ignoreInvalidEndpoint) {
        this.ignoreInvalidEndpoint = ignoreInvalidEndpoint;
    }

    /**
     * Enriches the input data (<code>exchange</code>) by first obtaining
     * additional data from an endpoint represented by an endpoint
     * <code>producer</code> and second by aggregating input data and additional
     * data. Aggregation of input data and additional data is delegated to an
     * {@link AggregationStrategy} object set at construction time. If the
     * message exchange with the resource endpoint fails then no aggregation
     * will be done and the failed exchange content is copied over to the
     * original message exchange.
     *
     * @param exchange input data.
     */
    @Override
    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        // which producer to use
        final AsyncProducer producer;
        final Endpoint endpoint;

        // use dynamic endpoint so calculate the endpoint to use
        Object recipient = null;
        boolean prototype = cacheSize < 0;
        try {
            recipient = expression.evaluate(exchange, Object.class);
            recipient = prepareRecipient(exchange, recipient);
            Endpoint existing = getExistingEndpoint(exchange, recipient);
            if (existing == null) {
                endpoint = resolveEndpoint(exchange, recipient, prototype);
            } else {
                endpoint = existing;
                // we have an existing endpoint then its not a prototype scope
                prototype = false;
            }
            // acquire the producer from the cache
            producer = producerCache.acquireProducer(endpoint);
        } catch (Throwable e) {
            if (isIgnoreInvalidEndpoint()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Endpoint uri is invalid: " + recipient + ". This exception will be ignored.", e);
                }
            } else {
                exchange.setException(e);
            }
            callback.done(true);
            return true;
        }

        final Exchange resourceExchange = createResourceExchange(exchange, ExchangePattern.InOut);
        final Endpoint destination = producer.getEndpoint();

        StopWatch sw = null;
        boolean sending = EventHelper.notifyExchangeSending(exchange.getContext(), resourceExchange, destination);
        if (sending) {
            sw = new StopWatch();
        }
        // record timing for sending the exchange using the producer
        final StopWatch watch = sw;
        final boolean prototypeEndpoint = prototype;
        AsyncProcessor ap = AsyncProcessorConverterHelper.convert(producer);
        boolean sync = ap.process(resourceExchange, new AsyncCallback() {
            public void done(boolean doneSync) {
                // we only have to handle async completion
                if (doneSync) {
                    return;
                }

                // emit event that the exchange was sent to the endpoint
                if (watch != null) {
                    long timeTaken = watch.taken();
                    EventHelper.notifyExchangeSent(resourceExchange.getContext(), resourceExchange, destination, timeTaken);
                }
                
                if (!isAggregateOnException() && resourceExchange.isFailed()) {
                    // copy resource exchange onto original exchange (preserving pattern)
                    copyResultsPreservePattern(exchange, resourceExchange);
                } else {
                    prepareResult(exchange);
                    try {
                        // prepare the exchanges for aggregation
                        ExchangeHelper.prepareAggregation(exchange, resourceExchange);

                        Exchange aggregatedExchange = aggregationStrategy.aggregate(exchange, resourceExchange);
                        if (aggregatedExchange != null) {
                            // copy aggregation result onto original exchange (preserving pattern)
                            copyResultsPreservePattern(exchange, aggregatedExchange);
                        }
                    } catch (Throwable e) {
                        // if the aggregationStrategy threw an exception, set it on the original exchange
                        exchange.setException(new CamelExchangeException("Error occurred during aggregation", exchange, e));
                        callback.done(false);
                        // we failed so break out now
                        return;
                    }
                }

                // set property with the uri of the endpoint enriched so we can use that for tracing etc
                exchange.setProperty(Exchange.TO_ENDPOINT, producer.getEndpoint().getEndpointUri());

                // return the producer back to the cache
                try {
                    producerCache.releaseProducer(endpoint, producer);
                } catch (Exception e) {
                    // ignore
                }
                // and stop prototype endpoints
                if (prototypeEndpoint) {
                    ServiceHelper.stopAndShutdownService(endpoint);
                }

                callback.done(false);
            }
        });

        if (!sync) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Processing exchangeId: {} is continued being processed asynchronously", exchange.getExchangeId());
            }
            // the remainder of the routing slip will be completed async
            // so we break out now, then the callback will be invoked which then continue routing from where we left here
            return false;
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace("Processing exchangeId: {} is continued being processed synchronously", exchange.getExchangeId());
        }

        if (watch != null) {
            // emit event that the exchange was sent to the endpoint
            long timeTaken = watch.taken();
            EventHelper.notifyExchangeSent(resourceExchange.getContext(), resourceExchange, destination, timeTaken);
        }
        
        if (!isAggregateOnException() && resourceExchange.isFailed()) {
            // copy resource exchange onto original exchange (preserving pattern)
            copyResultsPreservePattern(exchange, resourceExchange);
        } else {
            prepareResult(exchange);

            try {
                // prepare the exchanges for aggregation
                ExchangeHelper.prepareAggregation(exchange, resourceExchange);

                Exchange aggregatedExchange = aggregationStrategy.aggregate(exchange, resourceExchange);
                if (aggregatedExchange != null) {
                    // copy aggregation result onto original exchange (preserving pattern)
                    copyResultsPreservePattern(exchange, aggregatedExchange);
                }
            } catch (Throwable e) {
                // if the aggregationStrategy threw an exception, set it on the original exchange
                exchange.setException(new CamelExchangeException("Error occurred during aggregation", exchange, e));
                callback.done(true);
                // we failed so break out now
                return true;
            }
        }

        // set property with the uri of the endpoint enriched so we can use that for tracing etc
        exchange.setProperty(Exchange.TO_ENDPOINT, producer.getEndpoint().getEndpointUri());

        // return the producer back to the cache
        try {
            producerCache.releaseProducer(endpoint, producer);
        } catch (Exception e) {
            // ignore
        }
        // and stop prototype endpoints
        if (prototypeEndpoint) {
            ServiceHelper.stopAndShutdownService(endpoint);
        }

        callback.done(true);
        return true;
    }

    protected static Object prepareRecipient(Exchange exchange, Object recipient) throws NoTypeConversionAvailableException {
        if (recipient instanceof Endpoint || recipient instanceof NormalizedEndpointUri) {
            return recipient;
        } else if (recipient instanceof String) {
            // trim strings as end users might have added spaces between separators
            recipient = ((String) recipient).trim();
        }
        if (recipient != null) {
            ExtendedCamelContext ecc = (ExtendedCamelContext) exchange.getContext();
            String uri;
            if (recipient instanceof String) {
                uri = (String) recipient;
            } else {
                // convert to a string type we can work with
                uri = ecc.getTypeConverter().mandatoryConvertTo(String.class, exchange, recipient);
            }
            // optimize and normalize endpoint
            return ecc.normalizeUri(uri);
        }
        return null;
    }

    protected static Endpoint getExistingEndpoint(Exchange exchange, Object recipient) {
        if (recipient instanceof Endpoint) {
            return (Endpoint) recipient;
        }
        if (recipient != null) {
            if (recipient instanceof NormalizedEndpointUri) {
                NormalizedEndpointUri nu = (NormalizedEndpointUri) recipient;
                ExtendedCamelContext ecc = exchange.getContext().adapt(ExtendedCamelContext.class);
                return ecc.hasEndpoint(nu);
            } else {
                String uri = recipient.toString();
                return exchange.getContext().hasEndpoint(uri);
            }
        }
        return null;
    }

    protected static Endpoint resolveEndpoint(Exchange exchange, Object recipient, boolean prototype) {
        return prototype ? ExchangeHelper.resolvePrototypeEndpoint(exchange, recipient) : ExchangeHelper.resolveEndpoint(exchange, recipient);
    }

    /**
     * Creates a new {@link DefaultExchange} instance from the given
     * <code>exchange</code>. The resulting exchange's pattern is defined by
     * <code>pattern</code>.
     *
     * @param source  exchange to copy from.
     * @param pattern exchange pattern to set.
     * @return created exchange.
     */
    protected Exchange createResourceExchange(Exchange source, ExchangePattern pattern) {
        // copy exchange, and do not share the unit of work
        Exchange target = ExchangeHelper.createCorrelatedCopy(source, false);
        target.setPattern(pattern);

        // if we share unit of work, we need to prepare the resource exchange
        if (isShareUnitOfWork()) {
            target.setProperty(Exchange.PARENT_UNIT_OF_WORK, source.getUnitOfWork());
            // and then share the unit of work
            target.adapt(ExtendedExchange.class).setUnitOfWork(source.getUnitOfWork());
        }
        return target;
    }

    private static void prepareResult(Exchange exchange) {
        if (exchange.getPattern().isOutCapable()) {
            exchange.getOut().copyFrom(exchange.getIn());
        }
    }

    private static AggregationStrategy defaultAggregationStrategy() {
        return new CopyAggregationStrategy();
    }

    @Override
    public String toString() {
        return id;
    }

    @Override
    protected void doStart() throws Exception {
        if (aggregationStrategy == null) {
            aggregationStrategy = defaultAggregationStrategy();
        }
        if (aggregationStrategy instanceof CamelContextAware) {
            ((CamelContextAware) aggregationStrategy).setCamelContext(camelContext);
        }

        if (producerCache == null) {
            if (cacheSize < 0) {
                producerCache = new EmptyProducerCache(this, camelContext);
                LOG.debug("Enricher {} is not using ProducerCache", this);
            } else {
                producerCache = new DefaultProducerCache(this, camelContext, cacheSize);
                LOG.debug("Enricher {} using ProducerCache with cacheSize={}", this, cacheSize);
            }
        }

        ServiceHelper.startService(producerCache, aggregationStrategy);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(aggregationStrategy, producerCache);
    }

    private static class CopyAggregationStrategy implements AggregationStrategy {

        @Override
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            if (newExchange != null) {
                copyResultsPreservePattern(oldExchange, newExchange);
            }
            return oldExchange;
        }

    }

}
