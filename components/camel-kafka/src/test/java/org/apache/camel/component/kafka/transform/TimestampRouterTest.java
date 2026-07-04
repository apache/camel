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
package org.apache.camel.component.kafka.transform;

import org.apache.camel.Exchange;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TimestampRouterTest {

    private final TimestampRouter router = new TimestampRouter();

    @Test
    public void testDefaultTopicHeader() {
        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.getMessage().setHeader(KafkaConstants.TOPIC, "my-topic");
        exchange.getMessage().setHeader("kafka.TIMESTAMP", "1719100800000");

        router.process("$[topic]_$[timestamp]", "yyyy-MM-dd", "kafka.TIMESTAMP", null, exchange);

        assertEquals("my-topic_2024-06-23", exchange.getMessage().getHeader(KafkaConstants.OVERRIDE_TOPIC));
    }

    @Test
    public void testCustomTopicHeaderName() {
        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.getMessage().setHeader("kafka.TOPIC", "my-topic-3");
        exchange.getMessage().setHeader("kafka.TIMESTAMP", "1719100800000");

        router.process("$[topic]_$[timestamp]", "yyyy-MM-dd", "kafka.TIMESTAMP", "kafka.TOPIC", exchange);

        assertEquals("my-topic-3_2024-06-23", exchange.getMessage().getHeader(KafkaConstants.OVERRIDE_TOPIC));
    }

    @Test
    public void testEmptyTopicHeaderNameFallsBackToDefault() {
        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.getMessage().setHeader(KafkaConstants.TOPIC, "fallback-topic");
        exchange.getMessage().setHeader("kafka.TIMESTAMP", "1719100800000");

        router.process("$[topic]_$[timestamp]", "yyyy-MM-dd", "kafka.TIMESTAMP", "", exchange);

        assertEquals("fallback-topic_2024-06-23", exchange.getMessage().getHeader(KafkaConstants.OVERRIDE_TOPIC));
    }

    @Test
    public void testNullTopicHeaderNameFallsBackToDefault() {
        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.getMessage().setHeader(KafkaConstants.TOPIC, "fallback-topic");
        exchange.getMessage().setHeader("kafka.TIMESTAMP", "1719100800000");

        router.process("$[topic]_$[timestamp]", "yyyy-MM-dd", "kafka.TIMESTAMP", null, exchange);

        assertEquals("fallback-topic_2024-06-23", exchange.getMessage().getHeader(KafkaConstants.OVERRIDE_TOPIC));
    }

    @Test
    public void testNoTopicHeaderSet() {
        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.getMessage().setHeader("kafka.TIMESTAMP", "1719100800000");

        router.process("$[topic]_$[timestamp]", "yyyy-MM-dd", "kafka.TIMESTAMP", null, exchange);

        assertEquals("_2024-06-23", exchange.getMessage().getHeader(KafkaConstants.OVERRIDE_TOPIC));
    }

    @Test
    public void testNoTimestamp() {
        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.getMessage().setHeader(KafkaConstants.TOPIC, "my-topic");

        router.process("$[topic]_$[timestamp]", "yyyy-MM-dd", "kafka.TIMESTAMP", null, exchange);

        assertNull(exchange.getMessage().getHeader(KafkaConstants.OVERRIDE_TOPIC));
    }
}
