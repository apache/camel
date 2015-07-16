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
package org.apache.camel.component.sjms.batch;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;

/**
 * @author jkorab
 */
@UriEndpoint(scheme = "sjmsBatch", title = "Simple JMS Batch Component", syntax = "sjms-batch:destinationName?aggregationStrategy=#aggStrategy", consumerClass = SjmsBatchComponent.class, label = "messaging")
public class SjmsBatchEndpoint extends DefaultEndpoint {

    public static final int DEFAULT_COMPLETION_SIZE = 200; // the default dispatch queue size in ActiveMQ
    public static final int DEFAULT_COMPLETION_TIMEOUT = 500;
    public static final String PROPERTY_BATCH_SIZE = "CamelSjmsBatchSize";

    @UriPath
    @Metadata(required = "true")
    private String destinationName;

    @UriParam(label = "consumer", defaultValue = "1", description = "The number of JMS sessions to consume from")
    private Integer consumerCount = 1;

    @UriParam(label = "consumer", defaultValue = "200",
            description = "The number of messages consumed at which the batch will be completed")
    private Integer completionSize = DEFAULT_COMPLETION_SIZE;

    @UriParam(label = "consumer", defaultValue = "500",
            description = "The timeout from receipt of the first first message when the batch will be completed")
    private Integer completionTimeout = DEFAULT_COMPLETION_TIMEOUT;

    @UriParam(label = "consumer", defaultValue = "1000",
            description = "The duration in milliseconds of each poll for messages. " +
                    "completionTimeOut will be used if it is shorter and a batch has started.")
    private Integer pollDuration = 1000;

    @Metadata(required = "true")
    @UriParam(label = "consumer", description = "A #-reference to an AggregationStrategy visible to Camel")
    private AggregationStrategy aggregationStrategy;

    public SjmsBatchEndpoint() {}

    public SjmsBatchEndpoint(String endpointUri, Component component, String remaining) {
        super(endpointUri, component);
        this.destinationName = remaining;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    @Override
    public Producer createProducer() throws Exception {
        throw new UnsupportedOperationException("Cannot produce though a " + SjmsBatchEndpoint.class.getName());
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        return new SjmsBatchConsumer(this, processor);
    }

    public AggregationStrategy getAggregationStrategy() {
        return aggregationStrategy;
    }

    public void setAggregationStrategy(AggregationStrategy aggregationStrategy) {
        this.aggregationStrategy = aggregationStrategy;
    }

    public Integer getCompletionSize() {
        return completionSize;
    }

    public void setCompletionSize(Integer completionSize) {
        this.completionSize = completionSize;
    }

    public Integer getCompletionTimeout() {
        return completionTimeout;
    }

    public void setCompletionTimeout(Integer completionTimeout) {
        this.completionTimeout = completionTimeout;
    }

    public String getDestinationName() {
        return destinationName;
    }

    public void setDestinationName(String destinationName) {
        this.destinationName = destinationName;
    }

    public Integer getConsumerCount() {
        return consumerCount;
    }

    public void setConsumerCount(Integer consumerCount) {
        this.consumerCount = consumerCount;
    }

    public Integer getPollDuration() {
        return pollDuration;
    }

    public void setPollDuration(Integer pollDuration) {
        this.pollDuration = pollDuration;
    }
}
