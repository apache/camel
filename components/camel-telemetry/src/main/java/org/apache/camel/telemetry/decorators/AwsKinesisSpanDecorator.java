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
package org.apache.camel.telemetry.decorators;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.telemetry.Span;

public class AwsKinesisSpanDecorator extends AbstractMessagingSpanDecorator {

    static final String KINESIS_PARTITION_KEY = "partitionKey";
    static final String KINESIS_SHARD_ID = "shardId";
    static final String KINESIS_APPROX_ARRIVAL_TIME = "approximateArrivalTimestamp";

    /**
     * Constants copied from {@link org.apache.camel.component.aws2.kinesis.Kinesis2Constants}
     */
    static final String SEQUENCE_NUMBER = "CamelAwsKinesisSequenceNumber";
    static final String APPROX_ARRIVAL_TIME = "CamelAwsKinesisApproximateArrivalTimestamp";
    static final String PARTITION_KEY = "CamelAwsKinesisPartitionKey";
    static final String SHARD_ID = "CamelAwsKinesisShardId";

    @Override
    public String getComponent() {
        return "aws2-kinesis";
    }

    @Override
    public String getComponentClassName() {
        return "org.apache.camel.component.aws2.kinesis.Kinesis2Component";
    }

    @Override
    public void beforeTracingEvent(Span span, Exchange exchange, Endpoint endpoint) {
        super.beforeTracingEvent(span, exchange, endpoint);

        String partitionKey = exchange.getIn().getHeader(PARTITION_KEY, String.class);
        if (partitionKey != null) {
            span.setTag(KINESIS_PARTITION_KEY, partitionKey);
        }

        String shardId = exchange.getIn().getHeader(SHARD_ID, String.class);
        if (shardId != null) {
            span.setTag(KINESIS_SHARD_ID, shardId);
        }

        String approxArrivalTimestamp = exchange.getIn().getHeader(APPROX_ARRIVAL_TIME, String.class);
        if (approxArrivalTimestamp != null) {
            span.setTag(KINESIS_APPROX_ARRIVAL_TIME, approxArrivalTimestamp);
        }
    }

    @Override
    protected String getMessageId(Exchange exchange) {
        return exchange.getIn().getHeader(SEQUENCE_NUMBER, String.class);
    }

}
