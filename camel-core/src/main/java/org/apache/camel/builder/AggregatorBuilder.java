/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.builder;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.Service;
import org.apache.camel.processor.Aggregator;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.processor.aggregate.UseLatestAggregationStrategy;

import java.util.List;

/**
 * A builder for the <a href="http://activemq.apache.org/camel/aggregator.html">Aggregator</a> pattern
 * where a batch of messages are processed (up to a maximum amount or until some timeout is reached)
 * and messages for the same correlation key are combined together using some kind of
 * {@link AggregationStrategy ) (by default the latest message is used) to compress many message exchanges
 * into a smaller number of exchanges.
 * <p/>
 * A good example of this is stock market data; you may be receiving 30,000 messages/second and you may want to
 * throttle it right down so that multiple messages for the same stock are combined (or just the latest
 * message is used and older prices are discarded). Another idea is to combine line item messages together
 * into a single invoice message.
 *
 * @version $Revision: 1.1 $
 */
public class AggregatorBuilder extends FromBuilder {
    private final Expression correlationExpression;
    private long batchTimeout = 1000L;
    private int batchSize = 50000;
    private AggregationStrategy aggregationStrategy = new UseLatestAggregationStrategy();

    public AggregatorBuilder(FromBuilder builder, Expression correlationExpression) {
        super(builder);
        this.correlationExpression = correlationExpression;
    }

    @Override
    public Route createRoute() throws Exception {
        final Processor processor = super.createProcessor();
        final Aggregator service = new Aggregator(getFrom(), processor, correlationExpression, aggregationStrategy);

        return new Route<Exchange>(getFrom()) {
            protected void addServices(List<Service> list) throws Exception {
                list.add(service);
            }

            @Override
            public String toString() {
                return "AggregatorRoute[" + getEndpoint() + " -> " + processor + "]";
            }
        };
    }

    // Builder methods
    //-------------------------------------------------------------------------
    public AggregatorBuilder aggregationStrategy(AggregationStrategy aggregationStrategy) {
        setAggregationStrategy(aggregationStrategy);
        return this;
    }

    public AggregatorBuilder batchSize(int batchSize) {
        setBatchSize(batchSize);
        return this;
    }

    public AggregatorBuilder batchTimeout(int batchTimeout) {
        setBatchTimeout(batchTimeout);
        return this;
    }

    // Properties
    //-------------------------------------------------------------------------
    public AggregationStrategy getAggregationStrategy() {
        return aggregationStrategy;
    }

    public void setAggregationStrategy(AggregationStrategy aggregationStrategy) {
        this.aggregationStrategy = aggregationStrategy;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public long getBatchTimeout() {
        return batchTimeout;
    }

    public void setBatchTimeout(long batchTimeout) {
        this.batchTimeout = batchTimeout;
    }

}