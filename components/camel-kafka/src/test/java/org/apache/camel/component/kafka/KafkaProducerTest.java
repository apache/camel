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

import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelException;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultMessage;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.errors.ApiException;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Matchers;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class KafkaProducerTest {

    private KafkaProducer producer;
    private KafkaEndpoint endpoint;

    private Exchange exchange = Mockito.mock(Exchange.class);
    private Message in = new DefaultMessage();
    private Message out = new DefaultMessage();
    private AsyncCallback callback = Mockito.mock(AsyncCallback.class);

    @SuppressWarnings({"unchecked"})
    public KafkaProducerTest() throws Exception {
        endpoint = new KafkaEndpoint(
                "kafka:broker1:1234,broker2:4567?topic=sometopic", null);
        endpoint.getConfiguration().setBrokers("broker1:1234,broker2:4567");
        producer = new KafkaProducer(endpoint);

        RecordMetadata rm = new RecordMetadata(null, 1, 1);
        Future future = Mockito.mock(Future.class);
        Mockito.when(future.get()).thenReturn(rm);
        org.apache.kafka.clients.producer.KafkaProducer kp = Mockito.mock(org.apache.kafka.clients.producer.KafkaProducer.class);
        Mockito.when(kp.send(Matchers.any(ProducerRecord.class))).thenReturn(future);

        producer.setKafkaProducer(kp);
        producer.setWorkerPool(Executors.newFixedThreadPool(1));
    }

    @Test
    public void testPropertyBuilder() throws Exception {
        Properties props = producer.getProps();
        assertEquals("broker1:1234,broker2:4567", props.getProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG));
    }

    @Test
    @SuppressWarnings({"unchecked"})
    public void processSendsMessage() throws Exception {
        endpoint.getConfiguration().setTopic("sometopic");
        Mockito.when(exchange.getIn()).thenReturn(in);
        Mockito.when(exchange.getOut()).thenReturn(out);

        in.setHeader(KafkaConstants.PARTITION_KEY, "4");

        producer.process(exchange);
        Mockito.verify(producer.getKafkaProducer()).send(Matchers.any(ProducerRecord.class));
        assertRecordMetadataExists();
    }

    @Test(expected = Exception.class)
    @SuppressWarnings({"unchecked"})
    public void processSendsMessageWithException() throws Exception {
        endpoint.getConfiguration().setTopic("sometopic");
        // setup the exception here
        org.apache.kafka.clients.producer.KafkaProducer kp = producer.getKafkaProducer();
        Mockito.when(kp.send(Matchers.any(ProducerRecord.class))).thenThrow(new ApiException());
        Mockito.when(exchange.getIn()).thenReturn(in);
        in.setHeader(KafkaConstants.PARTITION_KEY, "4");

        producer.process(exchange);

        assertRecordMetadataExists();
    }

    @Test
    public void processAsyncSendsMessage() throws Exception {
        endpoint.getConfiguration().setTopic("sometopic");
        Mockito.when(exchange.getIn()).thenReturn(in);
        Mockito.when(exchange.getOut()).thenReturn(out);

        in.setHeader(KafkaConstants.PARTITION_KEY, "4");

        producer.process(exchange, callback);

        ArgumentCaptor<Callback> callBackCaptor = ArgumentCaptor.forClass(Callback.class);
        Mockito.verify(producer.getKafkaProducer()).send(Matchers.any(ProducerRecord.class), callBackCaptor.capture());
        Callback kafkaCallback = callBackCaptor.getValue();
        kafkaCallback.onCompletion(new RecordMetadata(null, 1, 1), null);
        assertRecordMetadataExists();
    }

    @Test
    public void processAsyncSendsMessageWithException() throws Exception {
        endpoint.getConfiguration().setTopic("sometopic");
        Mockito.when(exchange.getIn()).thenReturn(in);
        Mockito.when(exchange.getOut()).thenReturn(out);

        // setup the exception here
        org.apache.kafka.clients.producer.KafkaProducer kp = producer.getKafkaProducer();
        Mockito.when(kp.send(Matchers.any(ProducerRecord.class), Matchers.any(Callback.class))).thenThrow(new ApiException());

        in.setHeader(KafkaConstants.PARTITION_KEY, "4");

        producer.process(exchange, callback);

        ArgumentCaptor<Callback> callBackCaptor = ArgumentCaptor.forClass(Callback.class);
        Mockito.verify(producer.getKafkaProducer()).send(Matchers.any(ProducerRecord.class), callBackCaptor.capture());
        Mockito.verify(exchange).setException(Matchers.isA(ApiException.class));
        Mockito.verify(callback).done(Matchers.eq(true));
        Callback kafkaCallback = callBackCaptor.getValue();
        kafkaCallback.onCompletion(new RecordMetadata(null, 1, 1), null);
        assertRecordMetadataExists();
    }

    @Test
    public void processSendsMessageWithTopicHeaderAndNoTopicInEndPoint() throws Exception {
        endpoint.getConfiguration().setTopic(null);
        Mockito.when(exchange.getIn()).thenReturn(in);
        in.setHeader(KafkaConstants.TOPIC, "anotherTopic");
        Mockito.when(exchange.getOut()).thenReturn(out);

        producer.process(exchange);

        verifySendMessage("anotherTopic");
        assertRecordMetadataExists();
    }

    @Test
    public void processSendsMessageWithTopicHeaderAndEndPoint() throws Exception {
        endpoint.getConfiguration().setTopic("sometopic");
        Mockito.when(exchange.getIn()).thenReturn(in);
        Mockito.when(exchange.getOut()).thenReturn(out);

        in.setHeader(KafkaConstants.PARTITION_KEY, "4");
        in.setHeader(KafkaConstants.TOPIC, "anotherTopic");
        in.setHeader(KafkaConstants.KEY, "someKey");

        producer.process(exchange);

        verifySendMessage("4", "anotherTopic", "someKey");
        assertRecordMetadataExists();
    }

    @Test(expected = CamelException.class)
    public void processRequiresTopicInEndpointOrInHeader() throws Exception {
        endpoint.getConfiguration().setTopic(null);
        Mockito.when(exchange.getIn()).thenReturn(in);
        in.setHeader(KafkaConstants.PARTITION_KEY, "4");

        producer.process(exchange);

        assertRecordMetadataExists();
    }

    @Test
    public void processDoesNotRequirePartitionHeader() throws Exception {
        endpoint.getConfiguration().setTopic("sometopic");
        Mockito.when(exchange.getIn()).thenReturn(in);
        Mockito.when(exchange.getOut()).thenReturn(out);

        producer.process(exchange);

        assertRecordMetadataExists();
    }

    @Test
    public void processSendsMesssageWithPartitionKeyHeader() throws Exception {
        endpoint.getConfiguration().setTopic("someTopic");
        Mockito.when(exchange.getIn()).thenReturn(in);
        Mockito.when(exchange.getOut()).thenReturn(out);
        in.setHeader(KafkaConstants.PARTITION_KEY, "4");
        in.setHeader(KafkaConstants.KEY, "someKey");

        producer.process(exchange);

        verifySendMessage("4", "someTopic", "someKey");
        assertRecordMetadataExists();
    }

    @Test
    public void processSendsMesssageWithMessageKeyHeader() throws Exception {
        endpoint.getConfiguration().setTopic("someTopic");
        Mockito.when(exchange.getIn()).thenReturn(in);
        Mockito.when(exchange.getOut()).thenReturn(out);
        in.setHeader(KafkaConstants.KEY, "someKey");

        producer.process(exchange);

        verifySendMessage("someTopic", "someKey");
        assertRecordMetadataExists();
    }

    @Test
    public void processSendMessageWithBridgeEndpoint() throws Exception {
        endpoint.getConfiguration().setTopic("someTopic");
        endpoint.setBridgeEndpoint(true);
        Mockito.when(exchange.getIn()).thenReturn(in);
        Mockito.when(exchange.getOut()).thenReturn(out);
        in.setHeader(KafkaConstants.TOPIC, "anotherTopic");
        in.setHeader(KafkaConstants.KEY, "someKey");
        in.setHeader(KafkaConstants.PARTITION_KEY, "4");

        producer.process(exchange);

        verifySendMessage("4", "someTopic", "someKey");
        assertRecordMetadataExists();
    }

    @Test // Message and Topic Name alone
    public void processSendsMesssageWithMessageTopicName() throws Exception {
        endpoint.getConfiguration().setTopic("someTopic");
        Mockito.when(exchange.getIn()).thenReturn(in);
        Mockito.when(exchange.getOut()).thenReturn(out);

        producer.process(exchange);

        verifySendMessage("someTopic");
        assertRecordMetadataExists();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    protected void verifySendMessage(String partitionKey, String topic, String messageKey) {
        ArgumentCaptor<ProducerRecord> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        Mockito.verify(producer.getKafkaProducer()).send(captor.capture());
        assertEquals(new Integer(partitionKey), captor.getValue().partition());
        assertEquals(messageKey, captor.getValue().key());
        assertEquals(topic, captor.getValue().topic());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    protected void verifySendMessage(String topic, String messageKey) {
        ArgumentCaptor<ProducerRecord> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        Mockito.verify(producer.getKafkaProducer()).send(captor.capture());
        assertEquals(messageKey, captor.getValue().key());
        assertEquals(topic, captor.getValue().topic());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    protected void verifySendMessage(String topic) {
        ArgumentCaptor<ProducerRecord> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        Mockito.verify(producer.getKafkaProducer()).send(captor.capture());
        assertEquals(topic, captor.getValue().topic());
    }

    private void assertRecordMetadataExists() {
        List<RecordMetadata> recordMetaData1 = (List<RecordMetadata>) in.getHeader(KafkaConstants.KAFKA_RECORDMETA);
        assertTrue(recordMetaData1 != null);
        assertEquals("Expected one recordMetaData", recordMetaData1.size(), 1);
        assertTrue(recordMetaData1.get(0) != null);
    }
}
