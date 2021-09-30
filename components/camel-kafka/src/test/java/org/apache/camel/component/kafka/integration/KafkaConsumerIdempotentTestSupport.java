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

package org.apache.camel.component.kafka.integration;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.camel.Exchange;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class KafkaConsumerIdempotentTestSupport extends BaseEmbeddedKafkaTestSupport {

    protected void doSend(int size, String topic) {
        Properties props = getDefaultProperties();
        org.apache.kafka.clients.producer.KafkaProducer<String, String> producer
                = new org.apache.kafka.clients.producer.KafkaProducer<>(props);

        try {
            for (int k = 0; k < size; k++) {
                String msg = "message-" + k;
                ProducerRecord<String, String> data = new ProducerRecord<>(topic, String.valueOf(k), msg);

                data.headers().add(new RecordHeader("id", BigInteger.valueOf(k).toByteArray()));
                producer.send(data);
            }
        } finally {
            if (producer != null) {
                producer.close();
            }
        }
    }

    protected void doRun(MockEndpoint mockEndpoint, int size) throws InterruptedException {
        mockEndpoint.expectedMessageCount(size);

        List<Exchange> exchangeList = mockEndpoint.getReceivedExchanges();

        mockEndpoint.assertIsSatisfied(10000);

        assertEquals(size, exchangeList.size());

        Map<String, Object> headers = mockEndpoint.getExchanges().get(0).getIn().getHeaders();
        assertTrue(headers.containsKey("id"), "0");
    }
}
