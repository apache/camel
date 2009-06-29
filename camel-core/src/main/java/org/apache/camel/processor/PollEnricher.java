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
import org.apache.camel.PollingConsumer;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.ServiceSupport;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.util.ExchangeHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import static org.apache.camel.util.ExchangeHelper.copyResultsPreservePattern;

/**
 * A content enricher that enriches input data by first obtaining additional
 * data from a <i>resource</i> represented by an endpoint <code>producer</code>
 * and second by aggregating input data and additional data. Aggregation of
 * input data and additional data is delegated to an {@link org.apache.camel.processor.aggregate.AggregationStrategy}
 * object.
 * <p/>
 * Uses a {@link org.apache.camel.PollingConsumer} to obatin the additional data as opposed to {@link Enricher}
 * that uses a {@link org.apache.camel.Producer}.
 *
 * @see Enricher
 */
public class PollEnricher extends ServiceSupport implements Processor {

    private static final transient Log LOG = LogFactory.getLog(PollEnricher.class);
    private AggregationStrategy aggregationStrategy;
    private PollingConsumer consumer;
    private long timeout;

    /**
     * Creates a new {@link PollEnricher}. The default aggregation strategy is to
     * copy the additional data obtained from the enricher's resource over the
     * input data. When using the copy aggregation strategy the enricher
     * degenerates to a normal transformer.
     *
     * @param consumer consumer to resource endpoint.
     */
    public PollEnricher(PollingConsumer consumer) {
        this(defaultAggregationStrategy(), consumer, 0);
    }

    /**
     * Creates a new {@link PollEnricher}.
     *
     * @param aggregationStrategy  aggregation strategy to aggregate input data and additional data.
     * @param consumer consumer to resource endpoint.
     */
    public PollEnricher(AggregationStrategy aggregationStrategy, PollingConsumer consumer, long timeout) {
        this.aggregationStrategy = aggregationStrategy;
        this.consumer = consumer;
        this.timeout = timeout;
    }

    /**
     * Sets the aggregation strategy for this poll enricher.
     *
     * @param aggregationStrategy the aggregationStrategy to set
     */
    public void setAggregationStrategy(AggregationStrategy aggregationStrategy) {
        this.aggregationStrategy = aggregationStrategy;
    }

    /**
     * Sets the default aggregation strategy for this poll enricher.
     */
    public void setDefaultAggregationStrategy() {
        this.aggregationStrategy = defaultAggregationStrategy();
    }

    /**
     * Sets the timeout to use when polling.
     * <p/>
     * Use 0 or negative to not use timeout and block until data is available.
     *
     * @param timeout timeout in millis.
     */
    public void setTimeout(long timeout) {
        this.timeout = timeout;
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
    public void process(Exchange exchange) throws Exception {
        Exchange resourceExchange;
        if (timeout < 0) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Consumer receive: " + consumer);
            }
            resourceExchange = consumer.receive();
        } else if (timeout == 0) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Consumer receiveNoWait: " + consumer);
            }
            resourceExchange = consumer.receiveNoWait();
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Consumer receive with timeout: " + timeout + " ms. " + consumer);
            }
            resourceExchange = consumer.receive(timeout);
        }

        if (resourceExchange != null && resourceExchange.isFailed()) {
            // copy resource exchange onto original exchange (preserving pattern)
            copyResultsPreservePattern(exchange, resourceExchange);
        } else {
            prepareResult(exchange);

            // aggregate original exchange and resource exchange
            // but do not aggregate if the resource exchange was filtered
            Boolean filtered = null;
            if (resourceExchange != null) {
                filtered = resourceExchange.getProperty(Exchange.FILTERED, Boolean.class);
            }
            if (filtered == null || !filtered) {
                // prepare the exchanges for aggregation
                ExchangeHelper.prepareAggregation(exchange, resourceExchange);
                Exchange aggregatedExchange = aggregationStrategy.aggregate(exchange, resourceExchange);
                // copy aggregation result onto original exchange (preserving pattern)
                copyResultsPreservePattern(exchange, aggregatedExchange);
            } else {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Cannot aggregate exchange as its filtered: " + resourceExchange);
                }
            }
        }
    }

    /**
     * Creates a new {@link org.apache.camel.impl.DefaultExchange} instance from the given
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

    @Override
    public String toString() {
        return "PollEnrich[" + consumer + "]";
    }

    protected void doStart() throws Exception {
        consumer.start();
    }

    protected void doStop() throws Exception {
        consumer.stop();
    }

    private static class CopyAggregationStrategy implements AggregationStrategy {

        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            copyResultsPreservePattern(oldExchange, newExchange);
            return oldExchange;
        }

    }

}