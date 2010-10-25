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
import org.apache.camel.PollingConsumer;
import org.apache.camel.Processor;
import org.apache.camel.impl.ServiceSupport;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.ServiceHelper;
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
 * Uses a {@link org.apache.camel.PollingConsumer} to obtain the additional data as opposed to {@link Enricher}
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
     * @param timeout timeout in millis
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
        preCheckPoll(exchange);

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

        if (LOG.isDebugEnabled()) {
            if (resourceExchange == null) {
                LOG.debug("Consumer received no exchange");
            } else {
                LOG.debug("Consumer received: " + resourceExchange);
            }
        }

        if (resourceExchange != null && resourceExchange.isFailed()) {
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
                // handover any synchronization
                if (resourceExchange != null) {
                    resourceExchange.handoverCompletions(exchange);
                }
            }
        }
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
        return "PollEnrich[" + consumer + "]";
    }

    protected void doStart() throws Exception {
        ServiceHelper.startService(consumer);
    }

    protected void doStop() throws Exception {
        ServiceHelper.stopService(consumer);
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