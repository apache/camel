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
package org.apache.camel.opentracing.decorators;

import java.util.Map;

import io.opentracing.Span;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;

public class KafkaSpanDecorator extends AbstractMessagingSpanDecorator {

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
    public String getComponentClassName() {
        return "org.apache.camel.component.kafka.KafkaComponent";
    }

    @Override
    public String getDestination(Exchange exchange, Endpoint endpoint) {
        String topic = (String)exchange.getIn().getHeader(TOPIC);
        if (topic == null) {
            Map<String, String> queryParameters = toQueryParameters(endpoint.getEndpointUri());
            topic = queryParameters.get("topic");
        }
        return topic != null ? topic : super.getDestination(exchange, endpoint);
    }

    @Override
    public void pre(Span span, Exchange exchange, Endpoint endpoint) {
        super.pre(span, exchange, endpoint);

        String partition = getValue(exchange, PARTITION, Integer.class);
        if (partition != null) {
            span.setTag(KAFKA_PARTITION_TAG, partition);
        }

        String partitionKey = (String)exchange.getIn().getHeader(PARTITION_KEY);
        if (partitionKey != null) {
            span.setTag(KAFKA_PARTITION_KEY_TAG, partitionKey);
        }

        String key = (String)exchange.getIn().getHeader(KEY);
        if (key != null) {
            span.setTag(KAFKA_KEY_TAG, key);
        }

        String offset = getValue(exchange, OFFSET, Long.class);
        if (offset != null) {
            span.setTag(KAFKA_OFFSET_TAG, offset);
        }
    }

    /**
     * Extracts header value from the exchange for given header
     * @param exchange the {@link Exchange}
     * @param header the header name
     * @param type the class type of the exchange header
     * @return
     */
    private <T> String getValue(final Exchange exchange, final String header, Class<T> type) {
        T partition = exchange.getIn().getHeader(header, type);
        return partition != null ? String.valueOf(partition) : exchange.getIn().getHeader(header, String.class);
    }

}
