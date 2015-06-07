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
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Endpoint;
import org.apache.camel.EndpointAware;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.spi.IdAware;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.AsyncProcessorConverterHelper;
import org.apache.camel.util.AsyncProcessorHelper;
import org.apache.camel.util.EventHelper;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.ServiceHelper;
import org.apache.camel.util.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static org.apache.camel.util.ExchangeHelper.copyResultsPreservePattern;

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
public class Enricher extends ServiceSupport implements AsyncProcessor, EndpointAware, IdAware {

    private static final Logger LOG = LoggerFactory.getLogger(Enricher.class);
    private String id;
    private AggregationStrategy aggregationStrategy;
    private Producer producer;
    private boolean aggregateOnException;

    /**
     * Creates a new {@link Enricher}. The default aggregation strategy is to
     * copy the additional data obtained from the enricher's resource over the
     * input data. When using the copy aggregation strategy the enricher
     * degenerates to a normal transformer.
     * 
     * @param producer producer to resource endpoint.
     */
    public Enricher(Producer producer) {
        this(defaultAggregationStrategy(), producer);
    }

    /**
     * Creates a new {@link Enricher}.
     * 
     * @param aggregationStrategy  aggregation strategy to aggregate input data and additional data.
     * @param producer producer to resource endpoint.
     */
    public Enricher(AggregationStrategy aggregationStrategy, Producer producer) {
        this.aggregationStrategy = aggregationStrategy;
        this.producer = producer;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    /**
     * Sets the aggregation strategy for this enricher.
     *
     * @param aggregationStrategy the aggregationStrategy to set
     */
    public void setAggregationStrategy(AggregationStrategy aggregationStrategy) {
        this.aggregationStrategy = aggregationStrategy;
    }

    public AggregationStrategy getAggregationStrategy() {
        return aggregationStrategy;
    }

    public boolean isAggregateOnException() {
        return aggregateOnException;
    }

    /**
     * Whether to call {@link org.apache.camel.processor.aggregate.AggregationStrategy#aggregate(org.apache.camel.Exchange, org.apache.camel.Exchange)} if
     * an exception was thrown.
     */
    public void setAggregateOnException(boolean aggregateOnException) {
        this.aggregateOnException = aggregateOnException;
    }

    /**
     * Sets the default aggregation strategy for this enricher.
     */
    public void setDefaultAggregationStrategy() {
        this.aggregationStrategy = defaultAggregationStrategy();
    }

    public Endpoint getEndpoint() {
        return producer.getEndpoint();
    }

    public void process(Exchange exchange) throws Exception {
        AsyncProcessorHelper.process(this, exchange);
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
    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        final Exchange resourceExchange = createResourceExchange(exchange, ExchangePattern.InOut);
        final Endpoint destination = producer.getEndpoint();
        
        EventHelper.notifyExchangeSending(exchange.getContext(), resourceExchange, destination);
        // record timing for sending the exchange using the producer
        final StopWatch watch = new StopWatch();
        AsyncProcessor ap = AsyncProcessorConverterHelper.convert(producer);
        boolean sync = ap.process(resourceExchange, new AsyncCallback() {
            public void done(boolean doneSync) {
                // we only have to handle async completion of the routing slip
                if (doneSync) {
                    return;
                }

                // emit event that the exchange was sent to the endpoint
                long timeTaken = watch.stop();
                EventHelper.notifyExchangeSent(resourceExchange.getContext(), resourceExchange, destination, timeTaken);
                
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

                callback.done(false);
            }
        });

        if (!sync) {
            LOG.trace("Processing exchangeId: {} is continued being processed asynchronously", exchange.getExchangeId());
            // the remainder of the routing slip will be completed async
            // so we break out now, then the callback will be invoked which then continue routing from where we left here
            return false;
        }

        LOG.trace("Processing exchangeId: {} is continued being processed synchronously", exchange.getExchangeId());

        // emit event that the exchange was sent to the endpoint
        long timeTaken = watch.stop();
        EventHelper.notifyExchangeSent(resourceExchange.getContext(), resourceExchange, destination, timeTaken);
        
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

        callback.done(true);
        return true;
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
        return "Enrich[" + producer.getEndpoint() + "]";
    }

    protected void doStart() throws Exception {
        ServiceHelper.startServices(aggregationStrategy, producer);
    }

    protected void doStop() throws Exception {
        ServiceHelper.stopServices(producer, aggregationStrategy);
    }

    private static class CopyAggregationStrategy implements AggregationStrategy {

        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            if (newExchange != null) {
                copyResultsPreservePattern(oldExchange, newExchange);
            }
            return oldExchange;
        }

    }

}
