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
package org.apache.kafka.clients.producer;

import java.util.List;

import com.fasterxml.jackson.databind.node.TextNode;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.kafka.KafkaComponent;
import org.apache.camel.component.kafka.KafkaEndpoint;
import org.apache.camel.component.kafka.KafkaProducer;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.kafka.common.serialization.StringSerializer;
import org.hamcrest.core.Is;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class KafkaProducerTest {

    private final MockProducer kafkaProducer = new MockProducer<>(true, new StringSerializer(), new StringSerializer());
    private KafkaProducer camelProducer;
    @Spy
    private Exchange exchange;
    @Spy
    private Message message;

    @BeforeEach
    public void init() throws Exception {
        CamelContext context = new DefaultCamelContext();
        KafkaComponent component = new KafkaComponent(context);
        camelProducer = new KafkaProducer((KafkaEndpoint) component.createEndpoint("kafka:test"));
        camelProducer.setKafkaProducer(kafkaProducer);
        when(exchange.getIn()).thenReturn(message);
        when(exchange.getMessage()).thenReturn(message);
        when(exchange.getContext()).thenReturn(context);
        when(message.getHeader("kafka.PARTITION_KEY", Integer.class)).thenReturn(0);
        when(message.getHeader("kafka.KEY")).thenReturn("key");
    }

    @AfterEach
    public void after() {
        kafkaProducer.clear();
    }

    @Test
    public void testSendOverrideTopic() throws Exception {
        when(message.removeHeader("kafka.OVERRIDE_TOPIC")).thenReturn("overridden-topic");
        camelProducer.process(exchange);
        when(message.removeHeader("kafka.OVERRIDE_TOPIC")).thenReturn(new TextNode("overridden-topic-jackson"));
        camelProducer.process(exchange);
        List<ProducerRecord<Object, Object>> records = kafkaProducer.history();
        assertThat(records.get(0).topic(), Is.is("overridden-topic"));
        assertThat(records.get(1).topic(), Is.is("overridden-topic-jackson"));
    }

}
