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
package org.apache.camel.component.kafka;

import java.net.URISyntaxException;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class KafkaEndpointTest {

    @Test
    public void testCreatingKafkaExchangeSetsHeaders() throws URISyntaxException {
        KafkaEndpoint endpoint = new KafkaEndpoint("kafka:mytopic?brokers=localhost", new KafkaComponent(new DefaultCamelContext()));

        ConsumerRecord<String, String> record = new ConsumerRecord<String, String>("topic", 4, 56, "somekey", "");
        Exchange exchange = endpoint.createKafkaExchange(record);
        assertEquals("somekey", exchange.getIn().getHeader(KafkaConstants.KEY));
        assertEquals("topic", exchange.getIn().getHeader(KafkaConstants.TOPIC));
        assertEquals(4, exchange.getIn().getHeader(KafkaConstants.PARTITION));
        assertEquals(56L, exchange.getIn().getHeader(KafkaConstants.OFFSET));
    }

    @Test
    public void assertSingleton() throws URISyntaxException {
        KafkaEndpoint endpoint = new KafkaEndpoint("kafka:mytopic?brokers=localhost", new KafkaComponent(new DefaultCamelContext()));
        endpoint.getConfiguration().setBrokers("localhost");
        assertTrue(endpoint.isSingleton());
    }

}

