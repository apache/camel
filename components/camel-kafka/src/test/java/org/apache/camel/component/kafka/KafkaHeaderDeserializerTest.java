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
package org.apache.camel.component.kafka;

import java.nio.charset.StandardCharsets;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class KafkaHeaderDeserializerTest {

    @Test
    public void testSpecialHeadersInAnyCaseAreNotDeserialized() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.start();
            Exchange exchange = new DefaultExchange(context);
            byte[] data = "hello".getBytes(StandardCharsets.UTF_8);
            exchange.getMessage().setHeader("foo", data);
            exchange.getMessage().setHeader("camelkafkamanualcommit", data);
            exchange.getMessage().setHeader("CAMELKAFKAHEADERS", data);

            KafkaHeaderDeserializer deserializer = new KafkaHeaderDeserializer();
            deserializer.setEnabled("true");
            deserializer.process(exchange);

            assertEquals("hello", exchange.getMessage().getHeader("foo"));
            assertInstanceOf(byte[].class, exchange.getMessage().getHeader(KafkaConstants.MANUAL_COMMIT));
            assertInstanceOf(byte[].class, exchange.getMessage().getHeader(KafkaConstants.HEADERS));
        }
    }
}
