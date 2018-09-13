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

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.PollingConsumer;
import org.apache.camel.impl.BridgeExceptionHandlerToErrorHandler;
import org.apache.camel.impl.ConsumerCache;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.camel.impl.EmptyConsumerCache;
import org.apache.camel.impl.EventDrivenPollingConsumer;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.spi.EndpointUtilizationStatistics;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.spi.IdAware;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.AsyncProcessorHelper;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.util.ExchangeHelper.copyResultsPreservePattern;

/**
 * A content enricher that enriches input data by first obtaining additional
 * data from a <i>resource</i> represented by an endpoint <code>producer</code>
 * and second by aggregating input data and additional data. Aggregation of
 * input data and additional data is delegated to an {@link org.apache.camel.processor.aggregate.AggregationStrategy}
 * object.
 * <p/>
 * Uses a {@link org.apache.camel.PollingConsumer} to obtain the additional data as opposed to {@link Enricher}
 * that uses a {@link org.apache.camel.Producer}.
 *
 * @see Enricher
 */
public class PollEnricher extends ServiceSupport implements AsyncProcessor, IdAware, CamelContextAware {

    private static final Logger LOG = LoggerFactory.getLogger(PollEnricher.class);
    private CamelContext camelContext;
    private ConsumerCache consumerCache;
    private String id;
    private AggregationStrategy aggregationStrategy;
    private final Expression expression;
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
        this.timeout = timeout;
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public void process(Exchange exchange) throws Exception {
        AsyncProcessorHelper.process(this, exchange);
    }

    /**
     * Enriches the input data (<code>exchange</code>) by first obtaining
     * additional data from an endpoint represented by an endpoint
     * <code>producer</code> and second by aggregating input data and additional
     * data. Aggregation of input data and additional data is delegated to an
     * {@link org.apache.camel.processor.aggregate.AggregationStrategy} object set at construction time. If the
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
        try {
            recipient = expression.evaluate(exchange, Object.class);
            endpoint = resolveEndpoint(exchange, recipient);
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
                        resourceExchange.handoverCompletions(exchange);
                    }
                }
            }

            // if we failed then restore caused exception
            if (cause != null) {
                // restore caused exception
                exchange.setException(cause);
                // remove the exhausted marker as we want to be able to perform redeliveries with the error handler
                exchange.removeProperties(Exchange.REDELIVERY_EXHAUSTED);

                // preserve the redelivery stats
                if (redeliveried != null) {
                    if (exchange.hasOut()) {
                        exchange.getOut().setHeader(Exchange.REDELIVERED, redeliveried);
                    } else {
                        exchange.getIn().setHeader(Exchange.REDELIVERED, redeliveried);
                    }
                }
                if (redeliveryCounter != null) {
                    if (exchange.hasOut()) {
                        exchange.getOut().setHeader(Exchange.REDELIVERY_COUNTER, redeliveryCounter);
                    } else {
                        exchange.getIn().setHeader(Exchange.REDELIVERY_COUNTER, redeliveryCounter);
                    }
                }
                if (redeliveryMaxCounter != null) {
                    if (exchange.hasOut()) {
                        exchange.getOut().setHeader(Exchange.REDELIVERY_MAX_COUNTER, redeliveryMaxCounter);
                    } else {
                        exchange.getIn().setHeader(Exchange.REDELIVERY_MAX_COUNTER, redeliveryMaxCounter);
                    }
                }
            }

            // set header with the uri of the endpoint enriched so we can use that for tracing etc
            if (exchange.hasOut()) {
                exchange.getOut().setHeader(Exchange.TO_ENDPOINT, consumer.getEndpoint().getEndpointUri());
            } else {
                exchange.getIn().setHeader(Exchange.TO_ENDPOINT, consumer.getEndpoint().getEndpointUri());
            }
        } catch (Throwable e) {
            exchange.setException(new CamelExchangeException("Error occurred during aggregation", exchange, e));
            callback.done(true);
            return true;
        }

        callback.done(true);
        return true;
    }

    protected Endpoint resolveEndpoint(Exchange exchange, Object recipient) {
        // trim strings as end users might have added spaces between separators
        if (recipient instanceof String) {
            recipient = ((String)recipient).trim();
        }
        return ExchangeHelper.resolveEndpoint(exchange, recipient);
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
        return "PollEnrich[" + expression + "]";
    }

    protected void doStart() throws Exception {
        if (consumerCache == null) {
            // create consumer cache if we use dynamic expressions for computing the endpoints to poll
            if (cacheSize < 0) {
                consumerCache = new EmptyConsumerCache(this, camelContext);
                LOG.debug("PollEnrich {} is not using ConsumerCache", this);
            } else if (cacheSize == 0) {
                consumerCache = new ConsumerCache(this, camelContext);
                LOG.debug("PollEnrich {} using ConsumerCache with default cache size", this);
            } else {
                consumerCache = new ConsumerCache(this, camelContext, cacheSize);
                LOG.debug("PollEnrich {} using ConsumerCache with cacheSize={}", this, cacheSize);
            }
        }
        if (aggregationStrategy instanceof CamelContextAware) {
            ((CamelContextAware) aggregationStrategy).setCamelContext(camelContext);
        }
        ServiceHelper.startServices(consumerCache, aggregationStrategy);
    }

    protected void doStop() throws Exception {
        ServiceHelper.stopServices(aggregationStrategy, consumerCache);
    }

    protected void doShutdown() throws Exception {
        ServiceHelper.stopAndShutdownServices(aggregationStrategy, consumerCache);
    }

    private static class CopyAggregationStrategy implements AggregationStrategy {

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
