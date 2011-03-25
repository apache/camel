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
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.ServiceSupport;
import org.apache.camel.impl.converter.AsyncProcessorTypeConverter;
import org.apache.camel.processor.aggregate.AggregationStrategy;
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
 * input data and additional data is delegated to an {@link AggregationStrategy}
 * object.
 * <p/>
 * Uses a {@link org.apache.camel.Producer} to obtain the additional data as opposed to {@link PollEnricher}
 * that uses a {@link org.apache.camel.PollingConsumer}.
 *
 * @see PollEnricher
 */
public class Enricher extends ServiceSupport implements AsyncProcessor {

    private static final transient Logger LOG = LoggerFactory.getLogger(Enricher.class);
    private AggregationStrategy aggregationStrategy;
    private Producer producer;

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

    /**
     * Sets the aggregation strategy for this enricher.
     *
     * @param aggregationStrategy the aggregationStrategy to set
     */
    public void setAggregationStrategy(AggregationStrategy aggregationStrategy) {
        this.aggregationStrategy = aggregationStrategy;
    }

    /**
     * Sets the default aggregation strategy for this enricher.
     */
    public void setDefaultAggregationStrategy() {
        this.aggregationStrategy = defaultAggregationStrategy();
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

        AsyncProcessor ap = AsyncProcessorTypeConverter.convert(producer);
        boolean sync = AsyncProcessorHelper.process(ap, resourceExchange, new AsyncCallback() {
            public void done(boolean doneSync) {
                // we only have to handle async completion of the routing slip
                if (doneSync) {
                    return;
                }

                if (resourceExchange.isFailed()) {
                    // copy resource exchange onto original exchange (preserving pattern)
                    copyResultsPreservePattern(exchange, resourceExchange);
                } else {
                    prepareResult(exchange);

                    // prepare the exchanges for aggregation
                    ExchangeHelper.prepareAggregation(exchange, resourceExchange);
                    Exchange aggregatedExchange = aggregationStrategy.aggregate(exchange, resourceExchange);
                    if (aggregatedExchange != null) {
                        // copy aggregation result onto original exchange (preserving pattern)
                        copyResultsPreservePattern(exchange, aggregatedExchange);
                    }
                }

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

        if (resourceExchange.isFailed()) {
            // copy resource exchange onto original exchange (preserving pattern)
            copyResultsPreservePattern(exchange, resourceExchange);
        } else {
            prepareResult(exchange);

            // prepare the exchanges for aggregation
            ExchangeHelper.prepareAggregation(exchange, resourceExchange);
            Exchange aggregatedExchange = aggregationStrategy.aggregate(exchange, resourceExchange);
            if (aggregatedExchange != null) {
                // copy aggregation result onto original exchange (preserving pattern)
                copyResultsPreservePattern(exchange, aggregatedExchange);
            }
        }

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
        Exchange target = source.copy();
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
        return "Enrich[" + producer.getEndpoint().getEndpointUri() + "]";
    }

    protected void doStart() throws Exception {
        ServiceHelper.startService(producer);
    }

    protected void doStop() throws Exception {
        ServiceHelper.stopService(producer);
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
