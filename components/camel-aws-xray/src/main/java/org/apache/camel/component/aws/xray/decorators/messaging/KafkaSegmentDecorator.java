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
package org.apache.camel.component.aws.xray.decorators.messaging;

import java.util.Map;

import com.amazonaws.xray.entities.Entity;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;

public class KafkaSegmentDecorator extends AbstractMessagingSegmentDecorator {

    public static final String KAFKA_PARTITION_TAG = "kafka.partition";
    public static final String KAFKA_PARTITION_KEY_TAG = "kafka.partition.key";
    public static final String KAFKA_KEY_TAG = "kafka.key";
    public static final String KAFKA_OFFSET_TAG = "kafka.offset";

    /**
     * Constants copied from {@link org.apache.camel.component.kafka.KafkaConstants}
     */
    protected static final String PARTITION_KEY = "kafka.PARTITION_KEY";
    protected static final String PARTITION = "kafka.PARTITION";
    protected static final String KEY = "kafka.KEY";
    protected static final String TOPIC = "kafka.TOPIC";
    protected static final String OFFSET = "kafka.OFFSET";

    @Override
    public String getComponent() {
        return "kafka";
    }

    @Override
    public String getDestination(Exchange exchange, Endpoint endpoint) {
        String topic = (String) exchange.getIn().getHeader(TOPIC);
        if (topic == null) {
            Map<String, String> queryParameters = toQueryParameters(endpoint.getEndpointUri());
            topic = queryParameters.get("topic");
        }
        return topic != null ? topic : super.getDestination(exchange, endpoint);
    }

    @Override
    public void pre(Entity segment, Exchange exchange, Endpoint endpoint) {
        super.pre(segment, exchange, endpoint);

        String partition = (String) exchange.getIn().getHeader(PARTITION);
        if (partition != null) {
            segment.putMetadata(KAFKA_PARTITION_TAG, partition);
        }

        String partitionKey = (String) exchange.getIn().getHeader(PARTITION_KEY);
        if (partitionKey != null) {
            segment.putMetadata(KAFKA_PARTITION_KEY_TAG, partitionKey);
        }

        String key = (String) exchange.getIn().getHeader(KEY);
        if (key != null) {
            segment.putMetadata(KAFKA_KEY_TAG, key);
        }

        String offset = (String) exchange.getIn().getHeader(OFFSET);
        if (offset != null) {
            segment.putMetadata(KAFKA_OFFSET_TAG, offset);
        }
    }
}
