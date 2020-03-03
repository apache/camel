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

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class KafkaEndpointTest {

    private KafkaEndpoint endpoint;

    @Mock
    private ConsumerRecord<String, String> mockRecord;

    @Mock
    private KafkaComponent mockKafkaComponent;

    @Before
    public void setup() {
        KafkaComponent kafka = new KafkaComponent(new DefaultCamelContext());
        kafka.init();
        endpoint = new KafkaEndpoint("kafka:mytopic?brokers=localhost", kafka);
    }

    @Test
    public void createKafkaExchangeShouldSetHeaders() {

        when(mockRecord.key()).thenReturn("somekey");
        when(mockRecord.topic()).thenReturn("topic");
        when(mockRecord.partition()).thenReturn(4);
        when(mockRecord.offset()).thenReturn(56L);
        when(mockRecord.timestamp()).thenReturn(1518026587392L);

        Exchange exchange = endpoint.createKafkaExchange(mockRecord);
        Message inMessage = exchange.getIn();
        assertNotNull(inMessage);
        assertEquals("somekey", inMessage.getHeader(KafkaConstants.KEY));
        assertEquals("topic", inMessage.getHeader(KafkaConstants.TOPIC));
        assertEquals(4, inMessage.getHeader(KafkaConstants.PARTITION));
        assertEquals(56L, inMessage.getHeader(KafkaConstants.OFFSET));
        assertEquals(1518026587392L, inMessage.getHeader(KafkaConstants.TIMESTAMP));
    }

    @Test
    public void isSingletonShoudlReturnTrue() {
        assertTrue(endpoint.isSingleton());
    }

}
