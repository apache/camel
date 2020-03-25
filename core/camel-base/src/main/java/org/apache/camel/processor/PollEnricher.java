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
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.ExtendedExchange;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.PollingConsumer;
import org.apache.camel.impl.engine.DefaultConsumerCache;
import org.apache.camel.spi.ConsumerCache;
import org.apache.camel.spi.EndpointUtilizationStatistics;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.spi.IdAware;
import org.apache.camel.spi.NormalizedEndpointUri;
import org.apache.camel.spi.RouteIdAware;
import org.apache.camel.support.AsyncProcessorSupport;
import org.apache.camel.support.BridgeExceptionHandlerToErrorHandler;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.EventDrivenPollingConsumer;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.service.ServiceHelper;
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
 * Uses a {@link org.apache.camel.PollingConsumer} to obtain the additional data as opposed to {@link Enricher}
 * that uses a {@link org.apache.camel.Producer}.
 *
 * @see Enricher
 */
public class PollEnricher extends AsyncProcessorSupport implements IdAware, RouteIdAware, CamelContextAware {

    private static final Logger LOG = LoggerFactory.getLogger(PollEnricher.class);

    private CamelContext camelContext;
    private ConsumerCache consumerCache;
    private String id;
    private String routeId;
    private AggregationStrategy aggregationStrategy;
    private final Expression expression;
    private final String destination;
    private long timeout;
    private boolean aggregateOnException;
    private int cacheSize;
    private boolean ignoreInvalidEndpoint;

    /**
     * Creates a new {@link PollEnricher}.
     *
     * @param expression expression to use to compute the endpoint to poll from.
     * @param timeout timeout in millis
     */
    public PollEnricher(Expression expression, long timeout) {
        this.expression = expression;
        this.destination = null;
        this.timeout = timeout;
    }

    /**
     * Creates a new {@link PollEnricher}.
     *
     * @param destination the endpoint to poll from.
     * @param timeout timeout in millis
     */
    public PollEnricher(String destination, long timeout) {
        this.expression = null;
        this.destination = destination;
        this.timeout = timeout;
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
        return consumerCache.getEndpointUtilizationStatistics();
    }

    public AggregationStrategy getAggregationStrategy() {
        return aggregationStrategy;
    }

    /**
     * Sets the aggregation strategy for this poll enricher.
     *
     * @param aggregationStrategy the aggregationStrategy to set
     */
    public void setAggregationStrategy(AggregationStrategy aggregationStrategy) {
        this.aggregationStrategy = aggregationStrategy;
    }

    public long getTimeout() {
        return timeout;
    }

    /**
     * Sets the timeout to use when polling.
     * <p/>
     * Use 0 to use receiveNoWait,
     * Use -1 to use receive with no timeout (which will block until data is available).
     *
     * @param timeout timeout in millis.
     */
    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public boolean isAggregateOnException() {
        return aggregateOnException;
    }

    public void setAggregateOnException(boolean aggregateOnException) {
        this.aggregateOnException = aggregateOnException;
    }

    /**
     * Sets the default aggregation strategy for this poll enricher.
     */
    public void setDefaultAggregationStrategy() {
        this.aggregationStrategy = defaultAggregationStrategy();
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

    @Override
    protected void doInit() throws Exception {
        if (destination != null) {
            Endpoint endpoint = getExistingEndpoint(camelContext, destination);
            if (endpoint == null) {
                endpoint = resolveEndpoint(camelContext, destination, cacheSize < 0);
            }
        } else if (expression != null) {
            expression.init(camelContext);
        }
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
    public boolean process(Exchange exchange, AsyncCallback callback) {
        try {
            preCheckPoll(exchange);
        } catch (Exception e) {
            exchange.setException(new CamelExchangeException("Error during pre poll check", exchange, e));
            callback.done(true);
            return true;
        }

        // which consumer to use
        PollingConsumer consumer;
        Endpoint endpoint;

        // use dynamic endpoint so calculate the endpoint to use
        Object recipient = null;
        boolean prototype = cacheSize < 0;
        try {
            recipient = destination != null ? destination : expression.evaluate(exchange, Object.class);
            recipient = prepareRecipient(exchange, recipient);
            Endpoint existing = getExistingEndpoint(camelContext, recipient);
            if (existing == null) {
                endpoint = resolveEndpoint(camelContext, recipient, prototype);
            } else {
                endpoint = existing;
                // we have an existing endpoint then its not a prototype scope
                prototype = false;
            }
            // acquire the consumer from the cache
            consumer = consumerCache.acquirePollingConsumer(endpoint);
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

        // grab the real delegate consumer that performs the actual polling
        Consumer delegate = consumer;
        if (consumer instanceof EventDrivenPollingConsumer) {
            delegate = ((EventDrivenPollingConsumer) consumer).getDelegateConsumer();
        }

        // is the consumer bridging the error handler?
        boolean bridgeErrorHandler = false;
        if (delegate instanceof DefaultConsumer) {
            ExceptionHandler handler = ((DefaultConsumer) delegate).getExceptionHandler();
            if (handler instanceof BridgeExceptionHandlerToErrorHandler) {
                bridgeErrorHandler = true;
            }
        }

        Exchange resourceExchange;
        try {
            if (timeout < 0) {
                LOG.debug("Consumer receive: {}", consumer);
                resourceExchange = consumer.receive();
            } else if (timeout == 0) {
                LOG.debug("Consumer receiveNoWait: {}", consumer);
                resourceExchange = consumer.receiveNoWait();
            } else {
                LOG.debug("Consumer receive with timeout: {} ms. {}", timeout, consumer);
                resourceExchange = consumer.receive(timeout);
            }

            if (resourceExchange == null) {
                LOG.debug("Consumer received no exchange");
            } else {
                LOG.debug("Consumer received: {}", resourceExchange);
            }
        } catch (Exception e) {
            exchange.setException(new CamelExchangeException("Error during poll", exchange, e));
            callback.done(true);
            return true;
        } finally {
            // return the consumer back to the cache
            consumerCache.releasePollingConsumer(endpoint, consumer);
            // and stop prototype endpoints
            if (prototype) {
                ServiceHelper.stopAndShutdownService(endpoint);
            }
        }

        // remember current redelivery stats
        Object redeliveried = exchange.getIn().getHeader(Exchange.REDELIVERED);
        Object redeliveryCounter = exchange.getIn().getHeader(Exchange.REDELIVERY_COUNTER);
        Object redeliveryMaxCounter = exchange.getIn().getHeader(Exchange.REDELIVERY_MAX_COUNTER);

        // if we are bridging error handler and failed then remember the caused exception
        Throwable cause = null;
        if (resourceExchange != null && bridgeErrorHandler) {
            cause = resourceExchange.getException();
        }

        try {
            if (!isAggregateOnException() && (resourceExchange != null && resourceExchange.isFailed())) {
                // copy resource exchange onto original exchange (preserving pattern)
                // and preserve redelivery headers
                copyResultsPreservePattern(exchange, resourceExchange);
            } else {
                prepareResult(exchange);

                // prepare the exchanges for aggregation
                ExchangeHelper.prepareAggregation(exchange, resourceExchange);
                // must catch any exception from aggregation
                Exchange aggregatedExchange = aggregationStrategy.aggregate(exchange, resourceExchange);
                if (aggregatedExchange != null) {
                    // copy aggregation result onto original exchange (preserving pattern)
                    copyResultsPreservePattern(exchange, aggregatedExchange);
                    // handover any synchronization
                    if (resourceExchange != null) {
                        resourceExchange.adapt(ExtendedExchange.class).handoverCompletions(exchange);
                    }
                }
            }

            // if we failed then restore caused exception
            if (cause != null) {
                // restore caused exception
                exchange.setException(cause);
                // remove the exhausted marker as we want to be able to perform redeliveries with the error handler
                exchange.adapt(ExtendedExchange.class).setRedeliveryExhausted(false);

                // preserve the redelivery stats
                if (redeliveried != null) {
                    exchange.getMessage().setHeader(Exchange.REDELIVERED, redeliveried);
                }
                if (redeliveryCounter != null) {
                    exchange.getMessage().setHeader(Exchange.REDELIVERY_COUNTER, redeliveryCounter);
                }
                if (redeliveryMaxCounter != null) {
                    exchange.getMessage().setHeader(Exchange.REDELIVERY_MAX_COUNTER, redeliveryMaxCounter);
                }
            }

            // set header with the uri of the endpoint enriched so we can use that for tracing etc
            exchange.getMessage().setHeader(Exchange.TO_ENDPOINT, consumer.getEndpoint().getEndpointUri());
        } catch (Throwable e) {
            exchange.setException(new CamelExchangeException("Error occurred during aggregation", exchange, e));
            callback.done(true);
            return true;
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

    protected static Endpoint getExistingEndpoint(CamelContext context, Object recipient) {
        if (recipient instanceof Endpoint) {
            return (Endpoint) recipient;
        }
        if (recipient != null) {
            if (recipient instanceof NormalizedEndpointUri) {
                NormalizedEndpointUri nu = (NormalizedEndpointUri) recipient;
                ExtendedCamelContext ecc = context.adapt(ExtendedCamelContext.class);
                return ecc.hasEndpoint(nu);
            } else {
                String uri = recipient.toString();
                return context.hasEndpoint(uri);
            }
        }
        return null;
    }

    protected static Endpoint resolveEndpoint(CamelContext camelContext, Object recipient, boolean prototype) {
        return prototype ? ExchangeHelper.resolvePrototypeEndpoint(camelContext, recipient)
                : ExchangeHelper.resolveEndpoint(camelContext, recipient);
    }

    /**
     * Strategy to pre check polling.
     * <p/>
     * Is currently used to prevent doing poll enrich from a file based endpoint when the current route also
     * started from a file based endpoint as that is not currently supported.
     *
     * @param exchange the current exchange
     */
    protected void preCheckPoll(Exchange exchange) throws Exception {
        // noop
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
        if (consumerCache == null) {
            // create consumer cache if we use dynamic expressions for computing the endpoints to poll
            consumerCache = new DefaultConsumerCache(this, camelContext, cacheSize);
            LOG.debug("PollEnrich {} using ConsumerCache with cacheSize={}", this, cacheSize);
        }
        if (aggregationStrategy instanceof CamelContextAware) {
            ((CamelContextAware) aggregationStrategy).setCamelContext(camelContext);
        }
        ServiceHelper.startService(consumerCache, aggregationStrategy);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(aggregationStrategy, consumerCache);
    }

    @Override
    protected void doShutdown() throws Exception {
        ServiceHelper.stopAndShutdownServices(aggregationStrategy, consumerCache);
    }

    private static class CopyAggregationStrategy implements AggregationStrategy {

        @Override
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            if (newExchange != null) {
                copyResultsPreservePattern(oldExchange, newExchange);
            } else {
                // if no newExchange then there was no message from the external resource
                // and therefore we should set an empty body to indicate this fact
                // but keep headers/attachments as we want to propagate those
                oldExchange.getIn().setBody(null);
                oldExchange.setOut(null);
            }
            return oldExchange;
        }

    }

}
