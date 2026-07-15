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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.camel.Exchange;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MessageTimestampRouterTest {

    private final MessageTimestampRouter router = new MessageTimestampRouter();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void shouldFallbackToSecondKeyWhenFirstKeyMissing() throws Exception {
        DefaultCamelContext camelContext = new DefaultCamelContext();
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setHeader(KafkaConstants.TOPIC, "my-topic");

        ObjectNode body = mapper.createObjectNode();
        body.put("ts2", "1719100800000");
        exchange.getMessage().setBody(body);

        router.process("$[topic]_$[timestamp]", "yyyy-MM-dd", "ts1,ts2", "timestamp", exchange);

        assertEquals("my-topic_2024-06-23", exchange.getMessage().getHeader(KafkaConstants.OVERRIDE_TOPIC));
    }

    @Test
    void shouldHandleNumericTimestampField() throws Exception {
        DefaultCamelContext camelContext = new DefaultCamelContext();
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setHeader(KafkaConstants.TOPIC, "my-topic");

        ObjectNode body = mapper.createObjectNode();
        body.put("ts", 1719100800000L);
        exchange.getMessage().setBody(body);

        router.process("$[topic]_$[timestamp]", "yyyy-MM-dd", "ts", "timestamp", exchange);

        assertEquals("my-topic_2024-06-23", exchange.getMessage().getHeader(KafkaConstants.OVERRIDE_TOPIC));
    }

    @Test
    void shouldNotSetTopicWhenNoTimestampKeyMatches() throws Exception {
        DefaultCamelContext camelContext = new DefaultCamelContext();
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setHeader(KafkaConstants.TOPIC, "my-topic");

        ObjectNode body = mapper.createObjectNode();
        body.put("other", "value");
        exchange.getMessage().setBody(body);

        router.process("$[topic]_$[timestamp]", "yyyy-MM-dd", "ts1,ts2", "timestamp", exchange);

        assertNull(exchange.getMessage().getHeader(KafkaConstants.OVERRIDE_TOPIC));
    }
}
