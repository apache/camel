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
import java.util.Properties;

import kafka.javaapi.producer.Producer;
import kafka.producer.KeyedMessage;

import org.apache.camel.CamelException;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultMessage;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Matchers;
import org.mockito.Mockito;


import static org.junit.Assert.assertEquals;

public class KafkaProducerTest {

    private KafkaProducer producer;
    private KafkaEndpoint endpoint;

    private Exchange exchange = Mockito.mock(Exchange.class);
    private Message in = new DefaultMessage();

    @SuppressWarnings({"unchecked"})
    public KafkaProducerTest() throws IllegalAccessException, InstantiationException, ClassNotFoundException,
            URISyntaxException {
        endpoint = new KafkaEndpoint("kafka:broker1:1234,broker2:4567?topic=sometopic",
                "broker1:1234," + "broker2:4567?topic=sometopic", null);
        producer = new KafkaProducer(endpoint);
        producer.producer = Mockito.mock(Producer.class);
    }

    @Test
    public void testPropertyBuilder() throws Exception {
        endpoint.setPartitioner("com.sksamuel.someclass");
        Properties props = producer.getProps();
        assertEquals("com.sksamuel.someclass", props.getProperty("partitioner.class"));
        assertEquals("broker1:1234,broker2:4567", props.getProperty("metadata.broker.list"));
    }

    @Test
    @SuppressWarnings({"unchecked"})
    public void processSendsMesssage() throws Exception {

        endpoint.setTopic("sometopic");
        Mockito.when(exchange.getIn()).thenReturn(in);
        in.setHeader(KafkaConstants.PARTITION_KEY, "4");

        producer.process(exchange);

        Mockito.verify(producer.producer).send(Matchers.any(KeyedMessage.class));
    }

    @Test
    public void processSendsMesssageWithTopicHeaderAndNoTopicInEndPoint() throws Exception {

        endpoint.setTopic(null);
        Mockito.when(exchange.getIn()).thenReturn(in);
        in.setHeader(KafkaConstants.PARTITION_KEY, "4");
        in.setHeader(KafkaConstants.TOPIC, "anotherTopic");

        producer.process(exchange);

        verifySendMessage("4", "anotherTopic");
    }

    @Test
    public void processSendsMesssageWithTopicHeaderAndEndPoint() throws Exception {

        endpoint.setTopic("sometopic");
        Mockito.when(exchange.getIn()).thenReturn(in);
        in.setHeader(KafkaConstants.PARTITION_KEY, "4");
        in.setHeader(KafkaConstants.TOPIC, "anotherTopic");

        producer.process(exchange);

        verifySendMessage("4", "anotherTopic");
      
    }

    @Test(expected = CamelException.class)
    public void processRequiresTopicInEndpointOrInHeader() throws Exception {
        endpoint.setTopic(null);
        Mockito.when(exchange.getIn()).thenReturn(in);
        in.setHeader(KafkaConstants.PARTITION_KEY, "4");
        producer.process(exchange);
    }

    @Test(expected = CamelException.class)
    public void processRequiresPartitionHeader() throws Exception {
        endpoint.setTopic("sometopic");
        Mockito.when(exchange.getIn()).thenReturn(in);
        producer.process(exchange);
    }
    
    @Test
    
    public void processSendsMesssageWithPartitionKeyHeader() throws Exception {

        endpoint.setTopic("someTopic");
        Mockito.when(exchange.getIn()).thenReturn(in);
        in.setHeader(KafkaConstants.PARTITION_KEY, "4");

        producer.process(exchange);

        verifySendMessage("4", "someTopic");
        
    }
    
    @SuppressWarnings({"unchecked", "rawtypes"})
    protected void verifySendMessage(String key, String topic) {
        ArgumentCaptor<KeyedMessage> captor = ArgumentCaptor.forClass(KeyedMessage.class);
        Mockito.verify(producer.producer).send(captor.capture());
        assertEquals(key, captor.getValue().key());
        assertEquals(topic, captor.getValue().topic());
    }
}
