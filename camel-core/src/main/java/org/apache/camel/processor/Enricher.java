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

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.ServiceSupport;
import org.apache.camel.processor.aggregate.AggregationStrategy;

import static org.apache.camel.util.ExchangeHelper.copyResultsPreservePattern;

/**
 * A content enricher that enriches input data by first obtaining additional
 * data from a <i>resource</i> represented by an endpoint <code>producer</code>
 * and second by aggregating input data and additional data. Aggregation of
 * input data and additional data is delegated to an {@link AggregationStrategy}
 * object.
 */
public class Enricher extends ServiceSupport implements Processor {

    private AggregationStrategy aggregationStrategy;

    private Producer producer;

    /**
     * Creates a new {@link Enricher}. The default aggregation strategy is to
     * copy the additional data obtained from the enricher's resource over the
     * input data. When using the copy aggregation strategy the enricher
     * degenerates to a normal transformer.
     * 
     * @param producer
     *            producer to resource endpoint.
     */
    public Enricher(Producer producer) {
        this(defaultAggregationStrategy(), producer);
    }

    /**
     * Creates a new {@link Enricher}.
     * 
     * @param aggregationStrategy
     *            aggregation strategy to aggregate input data and additional
     *            data.
     * @param producer
     *            producer to resource endpoint.
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
     * @param exchange
     *            input data.
     */
    public void process(Exchange exchange) throws Exception {
        Exchange resourceExchange = createResourceExchange(exchange, ExchangePattern.InOut);
        producer.process(resourceExchange);

        if (resourceExchange.isFailed()) {
            // copy resource exchange onto original exchange (preserving pattern)
            copyResultsPreservePattern(exchange, resourceExchange);
        } else {
            prepareResult(exchange);
            // aggregate original exchange and resource exchange
            Exchange aggregatedExchange = aggregationStrategy.aggregate(exchange, resourceExchange);
            // copy aggregation result onto original exchange (preserving pattern)
            copyResultsPreservePattern(exchange, aggregatedExchange);
        }
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
        DefaultExchange target = new DefaultExchange(source.getContext());
        target.copyFrom(source);
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

    protected void doStart() throws Exception {
        producer.start();
    }

    protected void doStop() throws Exception {
        producer.stop();
    }

    private static class CopyAggregationStrategy implements AggregationStrategy {

        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            copyResultsPreservePattern(oldExchange, newExchange);
            return oldExchange;
        }

    }

}
