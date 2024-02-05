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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.Message;
import org.apache.camel.TypeConverter;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.engine.DefaultHeadersMapFactory;
import org.apache.camel.processor.aggregate.GroupedExchangeAggregationStrategy;
import org.apache.camel.processor.aggregate.GroupedMessageAggregationStrategy;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.DefaultMessage;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.errors.ApiException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;

public class KafkaProducerTest {

    private static final String SOME_INDIVIDUAL_HEADER = "someIndividualHeader";

    private final KafkaProducer producer;
    private final KafkaEndpoint endpoint;
    private final KafkaEndpoint fromEndpoint;

    private final TypeConverter converter = Mockito.mock(TypeConverter.class);
    private final CamelContext context = Mockito.mock(DefaultCamelContext.class);
    private final Exchange exchange = Mockito.mock(Exchange.class);
    private final ExtendedCamelContext ecc = Mockito.mock(ExtendedCamelContext.class);
    private final Message in = new DefaultMessage(context);
    private final AsyncCallback callback = Mockito.mock(AsyncCallback.class);

    @SuppressWarnings({ "unchecked" })
    public KafkaProducerTest() throws Exception {
        KafkaComponent kafka = new KafkaComponent(new DefaultCamelContext());
        kafka.getConfiguration().setBrokers("broker1:1234,broker2:4567");
        kafka.init();

        endpoint = kafka.createEndpoint("kafka:sometopic", "sometopic", new HashMap());
        endpoint.doBuild();
        assertTrue(endpoint.getKafkaClientFactory() instanceof DefaultKafkaClientFactory);

        producer = new KafkaProducer(endpoint);

        fromEndpoint = kafka.createEndpoint("kafka:fromtopic", "fromtopic", new HashMap());
        fromEndpoint.doBuild();
        assertTrue(fromEndpoint.getKafkaClientFactory() instanceof DefaultKafkaClientFactory);

        RecordMetadata rm = new RecordMetadata(null, 0, 0, 0, 0, 0);
        Future future = Mockito.mock(Future.class);
        Mockito.when(future.get()).thenReturn(rm);
        org.apache.kafka.clients.producer.KafkaProducer kp
                = Mockito.mock(org.apache.kafka.clients.producer.KafkaProducer.class);
        Mockito.when(kp.send(any(ProducerRecord.class))).thenReturn(future);

        Mockito.when(exchange.getContext()).thenReturn(context);
        Mockito.when(context.getTypeConverter()).thenReturn(converter);
        Mockito.when(converter.tryConvertTo(String.class, exchange, null)).thenReturn(null);
        Mockito.when(context.getCamelContextExtension()).thenReturn(ecc);
        Mockito.when(ecc.getHeadersMapFactory())
                .thenReturn(new DefaultHeadersMapFactory());
        Mockito.when(context.getTypeConverter()).thenReturn(converter);

        producer.setKafkaProducer(kp);
        producer.setWorkerPool(Executors.newFixedThreadPool(1));
    }

    @Test
    public void testPropertyBuilder() {
        Properties props = producer.getProps();
        assertEquals("broker1:1234,broker2:4567", props.getProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG));
    }

    @Test
    @SuppressWarnings({ "unchecked" })
    public void processSendsMessage() throws Exception {
        endpoint.getConfiguration().setTopic("sometopic");
        Mockito.when(exchange.getIn()).thenReturn(in);
        Mockito.when(exchange.getMessage()).thenReturn(in);

        in.setHeader(KafkaConstants.PARTITION_KEY, 4);

        producer.process(exchange);
        Mockito.verify(producer.getKafkaProducer()).send(any(ProducerRecord.class));
        assertRecordMetadataExists();
    }

    @Test
    @SuppressWarnings({ "unchecked" })
    public void processSendsMessageWithException() {
        endpoint.getConfiguration().setTopic("sometopic");
        // set up the exception here
        org.apache.kafka.clients.producer.Producer kp = producer.getKafkaProducer();
        Mockito.when(kp.send(any(ProducerRecord.class))).thenThrow(new ApiException());
        Mockito.when(exchange.getIn()).thenReturn(in);
        Mockito.when(exchange.getMessage()).thenReturn(in);
        in.setHeader(KafkaConstants.PARTITION_KEY, 4);

        assertThrows(ApiException.class,
                () -> producer.process(exchange));
    }

    @Test
    public void processAsyncSendsMessage() {
        endpoint.getConfiguration().setTopic("sometopic");
        Mockito.when(exchange.getIn()).thenReturn(in);
        Mockito.when(exchange.getMessage()).thenReturn(in);

        in.setHeader(KafkaConstants.PARTITION_KEY, 4);

        producer.process(exchange, callback);

        ArgumentCaptor<Callback> callBackCaptor = ArgumentCaptor.forClass(Callback.class);
        Mockito.verify(producer.getKafkaProducer()).send(any(ProducerRecord.class), callBackCaptor.capture());
        Callback kafkaCallback = callBackCaptor.getValue();
        kafkaCallback.onCompletion(new RecordMetadata(null, 0, 0, 0, 0, 0), null);
        assertRecordMetadataExists();
    }

    @Test
    public void processAsyncSendsMessageWithException() {
        endpoint.getConfiguration().setTopic("sometopic");
        Mockito.when(exchange.getIn()).thenReturn(in);
        Mockito.when(exchange.getMessage()).thenReturn(in);

        // set up the exception here
        org.apache.kafka.clients.producer.Producer kp = producer.getKafkaProducer();
        Mockito.when(kp.send(any(ProducerRecord.class), any(Callback.class))).thenThrow(new ApiException());

        in.setHeader(KafkaConstants.PARTITION_KEY, 4);

        producer.process(exchange, callback);

        ArgumentCaptor<Callback> callBackCaptor = ArgumentCaptor.forClass(Callback.class);
        Mockito.verify(producer.getKafkaProducer()).send(any(ProducerRecord.class), callBackCaptor.capture());
        Mockito.verify(exchange).setException(isA(ApiException.class));
        Mockito.verify(callback).done(eq(true));
        Callback kafkaCallback = callBackCaptor.getValue();
        kafkaCallback.onCompletion(new RecordMetadata(null, 0, 0, 0, 0, 0), null);
        assertRecordMetadataExists();
    }

    @Test
    public void processSendsMessageWithTopicHeaderAndNoTopicInEndPoint() throws Exception {
        endpoint.getConfiguration().setTopic(null);
        Mockito.when(exchange.getIn()).thenReturn(in);
        in.setHeader(KafkaConstants.TOPIC, "anotherTopic");
        Mockito.when(exchange.getMessage()).thenReturn(in);

        producer.process(exchange);

        verifySendMessage("sometopic");
        assertRecordMetadataExists();
    }

    @Test
    public void processSendsMessageWithTopicHeaderAndEndPoint() throws Exception {
        endpoint.getConfiguration().setTopic("sometopic");
        Mockito.when(exchange.getIn()).thenReturn(in);
        Mockito.when(exchange.getMessage()).thenReturn(in);

        in.setHeader(KafkaConstants.PARTITION_KEY, 4);
        in.setHeader(KafkaConstants.TOPIC, "anotherTopic");
        in.setHeader(KafkaConstants.KEY, "someKey");

        producer.process(exchange);

        // the header is preserved
        assertNotNull(in.getHeader(KafkaConstants.TOPIC));

        verifySendMessage(4, "sometopic", "someKey");
        assertRecordMetadataExists();
    }

    @Test
    public void processSendsMessageWithOverrideTopicHeaderAndEndPoint() throws Exception {
        endpoint.getConfiguration().setTopic("sometopic");
        Mockito.when(exchange.getIn()).thenReturn(in);
        Mockito.when(exchange.getMessage()).thenReturn(in);

        in.setHeader(KafkaConstants.PARTITION_KEY, 4);
        in.setHeader(KafkaConstants.OVERRIDE_TOPIC, "anotherTopic");
        in.setHeader(KafkaConstants.KEY, "someKey");

        // test using a string value instead of long
        String time = String.valueOf(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        in.setHeader(KafkaConstants.OVERRIDE_TIMESTAMP, time);

        producer.process(exchange);

        // the header is now removed
        assertNull(in.getHeader(KafkaConstants.OVERRIDE_TOPIC));

        verifySendMessage(4, "anotherTopic", "someKey");
        assertRecordMetadataExists();
    }

    @Test
    public void processRequiresTopicInEndpointOrInHeader() throws Exception {
        endpoint.getConfiguration().setTopic(null);
        Mockito.when(exchange.getIn()).thenReturn(in);
        Mockito.when(exchange.getMessage()).thenReturn(in);
        in.setHeader(KafkaConstants.PARTITION_KEY, 4);
        in.setHeader(KafkaConstants.KEY, "someKey");

        producer.process(exchange);

        verifySendMessage("sometopic", "someKey");
        assertRecordMetadataExists();
    }

    @Test
    public void processRequiresTopicInConfiguration() throws Exception {
        endpoint.getConfiguration().setTopic("configTopic");
        Mockito.when(exchange.getIn()).thenReturn(in);
        Mockito.when(exchange.getMessage()).thenReturn(in);
        in.setHeader(KafkaConstants.PARTITION_KEY, 4);
        in.setHeader(KafkaConstants.KEY, "someKey");

        producer.process(exchange);

        verifySendMessage("configTopic", "someKey");
        assertRecordMetadataExists();
    }

    @Test
    public void processDoesNotRequirePartitionHeader() throws Exception {
        endpoint.getConfiguration().setTopic("sometopic");
        Mockito.when(exchange.getIn()).thenReturn(in);
        Mockito.when(exchange.getMessage()).thenReturn(in);

        producer.process(exchange);

        assertRecordMetadataExists();
    }

    @Test
    public void processSendsMessageWithPartitionKeyHeader() throws Exception {
        endpoint.getConfiguration().setTopic("someTopic");
        Mockito.when(exchange.getIn()).thenReturn(in);
        Mockito.when(exchange.getMessage()).thenReturn(in);
        in.setHeader(KafkaConstants.PARTITION_KEY, 4);
        in.setHeader(KafkaConstants.KEY, "someKey");

        producer.process(exchange);

        verifySendMessage(4, "someTopic", "someKey");
        assertRecordMetadataExists();
    }

    @Test
    public void processSendsMessageWithPartitionKeyHeaderOnly() throws Exception {
        endpoint.getConfiguration().setTopic("someTopic");
        Mockito.when(exchange.getIn()).thenReturn(in);
        Mockito.when(exchange.getMessage()).thenReturn(in);
        in.setHeader(KafkaConstants.PARTITION_KEY, 4);

        producer.process(exchange);

        verifySendMessage(4, "someTopic");
        assertRecordMetadataExists();
    }

    @Test
    public void processSendsMessageWithMessageKeyHeader() throws Exception {
        endpoint.getConfiguration().setTopic("someTopic");
        Mockito.when(exchange.getIn()).thenReturn(in);
        Mockito.when(exchange.getMessage()).thenReturn(in);
        in.setHeader(KafkaConstants.KEY, "someKey");

        producer.process(exchange);

        verifySendMessage("someTopic", "someKey");
        assertRecordMetadataExists();
    }

    @Test
    public void processSendsMessageWithMessageTimestampHeader() throws Exception {
        endpoint.getConfiguration().setTopic("someTopic");
        Mockito.when(exchange.getIn()).thenReturn(in);
        Mockito.when(exchange.getMessage()).thenReturn(in);
        in.setHeader(KafkaConstants.KEY, "someKey");
        in.setHeader(KafkaConstants.OVERRIDE_TIMESTAMP,
                LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());

        producer.process(exchange);

        verifySendMessage("someTopic", "someKey");
        assertRecordMetadataTimestampExists();
    }

    @Test
    public void processSendMessageWithTopicHeader() throws Exception {
        endpoint.getConfiguration().setTopic("someTopic");
        Mockito.when(exchange.getIn()).thenReturn(in);
        Mockito.when(exchange.getMessage()).thenReturn(in);
        in.setHeader(KafkaConstants.TOPIC, "anotherTopic");
        in.setHeader(KafkaConstants.KEY, "someKey");
        in.setHeader(KafkaConstants.PARTITION_KEY, 4);

        producer.process(exchange);

        verifySendMessage(4, "someTopic", "someKey");
        assertRecordMetadataExists();
    }

    @Test // Message and Topic Name alone
    public void processSendsMessageWithMessageTopicName() throws Exception {
        endpoint.getConfiguration().setTopic("someTopic");
        Mockito.when(exchange.getIn()).thenReturn(in);
        Mockito.when(exchange.getMessage()).thenReturn(in);

        producer.process(exchange);

        verifySendMessage("someTopic");
        assertRecordMetadataExists();
    }

    @Test
    public void processSendsMessageWithListOfExchangesWithOverrideTopicHeaderOnEveryExchange() throws Exception {
        endpoint.getConfiguration().setTopic("someTopic");
        Mockito.when(exchange.getIn()).thenReturn(in);
        Mockito.when(exchange.getMessage()).thenReturn(in);

        // we set the initial topic
        in.setHeader(KafkaConstants.OVERRIDE_TOPIC, "anotherTopic");
        in.setHeader(KafkaConstants.KEY, "someKey");

        // we add our exchanges in order to aggregate
        final List<Exchange> nestedExchanges
                = createListOfExchangesWithTopics(Arrays.asList("overridenTopic1", "overridenTopic2", "overridenTopic3"));

        // aggregate
        final Exchange finalAggregatedExchange = aggregateExchanges(nestedExchanges, new GroupedExchangeAggregationStrategy());

        in.setBody(finalAggregatedExchange.getIn().getBody());
        in.setHeaders(finalAggregatedExchange.getIn().getHeaders());

        producer.process(exchange);

        // assert results
        verifySendMessages(Arrays.asList("overridenTopic1", "overridenTopic2", "overridenTopic3"), null);
        assertRecordMetadataExists(3);
        assertRecordMetadataExistsForEachAggregatedExchange();
    }

    @Test
    public void processSendsMessageWithListOfMessagesWithOverrideTopicHeaderOnEveryExchange() throws Exception {
        endpoint.getConfiguration().setTopic("someTopic");
        Mockito.when(exchange.getIn()).thenReturn(in);
        Mockito.when(exchange.getMessage()).thenReturn(in);

        // we set the initial topic
        in.setHeader(KafkaConstants.OVERRIDE_TOPIC, "anotherTopic");
        in.setHeader(KafkaConstants.KEY, "someKey");

        // we add our exchanges in order to aggregate
        final List<Exchange> nestedExchanges
                = createListOfExchangesWithTopics(Arrays.asList("overridenTopic1", "overridenTopic2", "overridenTopic3"));

        // aggregate
        final Exchange finalAggregatedExchange = aggregateExchanges(nestedExchanges, new GroupedMessageAggregationStrategy());

        in.setBody(finalAggregatedExchange.getIn().getBody());
        in.setHeaders(finalAggregatedExchange.getIn().getHeaders());

        producer.process(exchange);

        // assert results
        verifySendMessages(Arrays.asList("overridenTopic1", "overridenTopic2", "overridenTopic3"),
                Arrays.asList("", "", ""));
        assertRecordMetadataExists(3);
        assertRecordMetadataExistsForEachAggregatedMessage();
    }

    @Test
    public void processSendsMessageWithListOfExchangesWithIndividualHeaders() throws Exception {
        endpoint.getConfiguration().setBatchWithIndividualHeaders(true);
        endpoint.getConfiguration().setTopic("someTopic");
        Mockito.when(exchange.getIn()).thenReturn(in);
        Mockito.when(exchange.getMessage()).thenReturn(in);

        // we set the initial topic
        in.setHeader(KafkaConstants.OVERRIDE_TOPIC, "anotherTopic");
        in.setHeader(KafkaConstants.KEY, "someKey");
        in.setHeader(SOME_INDIVIDUAL_HEADER, "default");

        // we add our exchanges in order to aggregate
        final List<Exchange> nestedExchanges
                = createListOfExchangesWithTopics(Arrays.asList("overridenTopic1", "overridenTopic2", "overridenTopic3"));

        // aggregate
        final Exchange finalAggregatedExchange = aggregateExchanges(nestedExchanges, new GroupedExchangeAggregationStrategy());

        in.setBody(finalAggregatedExchange.getIn().getBody());
        in.setHeaders(finalAggregatedExchange.getIn().getHeaders());

        producer.process(exchange);

        // assert results
        verifySendMessages(Arrays.asList("overridenTopic1", "overridenTopic2", "overridenTopic3"),
                Arrays.asList("value-1", "value-2", "value-3"));
        assertRecordMetadataExists(3);
        assertRecordMetadataExistsForEachAggregatedExchange();
    }

    @Test
    public void processSendsMessageWithListOfMessagesWithIndividualHeaders() throws Exception {
        endpoint.getConfiguration().setBatchWithIndividualHeaders(true);
        endpoint.getConfiguration().setTopic("someTopic");
        Mockito.when(exchange.getIn()).thenReturn(in);
        Mockito.when(exchange.getMessage()).thenReturn(in);

        // we set the initial topic
        in.setHeader(KafkaConstants.OVERRIDE_TOPIC, "anotherTopic");
        in.setHeader(KafkaConstants.KEY, "someKey");

        // we add our exchanges in order to aggregate
        final List<Exchange> nestedExchanges
                = createListOfExchangesWithTopics(Arrays.asList("overridenTopic1", "overridenTopic2", "overridenTopic3"));

        // aggregate messages
        final Exchange finalAggregatedExchange = aggregateExchanges(nestedExchanges, new GroupedMessageAggregationStrategy());

        in.setBody(finalAggregatedExchange.getIn().getBody());
        in.setHeaders(finalAggregatedExchange.getIn().getHeaders());

        producer.process(exchange);

        // assert results
        verifySendMessages(Arrays.asList("overridenTopic1", "overridenTopic2", "overridenTopic3"),
                Arrays.asList("value-1", "value-2", "value-3"));
        assertRecordMetadataExists(3);
        assertRecordMetadataExistsForEachAggregatedMessage();
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    protected void verifySendMessage(Integer partitionKey, String topic, String messageKey) {
        ArgumentCaptor<ProducerRecord> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        Mockito.verify(producer.getKafkaProducer()).send(captor.capture());
        assertEquals(partitionKey, captor.getValue().partition());
        assertEquals(messageKey, captor.getValue().key());
        assertEquals(topic, captor.getValue().topic());
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    protected void verifySendMessage(Integer partitionKey, String topic) {
        ArgumentCaptor<ProducerRecord> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        Mockito.verify(producer.getKafkaProducer()).send(captor.capture());
        assertEquals(partitionKey, captor.getValue().partition());
        assertEquals(topic, captor.getValue().topic());
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    protected void verifySendMessage(String topic, String messageKey) {
        ArgumentCaptor<ProducerRecord> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        Mockito.verify(producer.getKafkaProducer()).send(captor.capture());
        assertEquals(messageKey, captor.getValue().key());
        assertEquals(topic, captor.getValue().topic());
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    protected void verifySendMessage(String topic) {
        ArgumentCaptor<ProducerRecord> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        Mockito.verify(producer.getKafkaProducer()).send(captor.capture());
        assertEquals(topic, captor.getValue().topic());
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    protected void verifySendMessages(final List<String> expectedTopics, final List<String> expectedIndividualHeaderValues) {
        final ArgumentCaptor<ProducerRecord> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        Mockito.verify(producer.getKafkaProducer(), Mockito.atLeast(expectedTopics.size())).send(captor.capture());
        final List<ProducerRecord> records = captor.getAllValues();
        final List<String> actualTopics
                = records.stream().map(ProducerRecord::topic).toList();

        assertEquals(expectedTopics, actualTopics);

        if (expectedIndividualHeaderValues == null) {
            return;
        }

        final List<String> actualIndividualHeaderValues = records.stream()
                .map(ProducerRecord::headers)
                .map(headers -> headers.lastHeader(SOME_INDIVIDUAL_HEADER))
                .map(header -> header == null ? "" : new String(header.value(), StandardCharsets.UTF_8))
                .collect(Collectors.toList());

        assertEquals(expectedIndividualHeaderValues, actualIndividualHeaderValues);
    }

    private void assertRecordMetadataTimestampExists() {
        List<RecordMetadata> recordMetaData1 = (List<RecordMetadata>) in.getHeader(KafkaConstants.KAFKA_RECORD_META);
        assertNotNull(recordMetaData1);
        assertEquals(1, recordMetaData1.size(), "Expected one recordMetaData");
        assertNotNull(recordMetaData1.get(0));
    }

    private void assertRecordMetadataExists() {
        List<RecordMetadata> recordMetaData1 = (List<RecordMetadata>) in.getHeader(KafkaConstants.KAFKA_RECORD_META);
        assertNotNull(recordMetaData1);
        assertEquals(1, recordMetaData1.size(), "Expected one recordMetaData");
        assertNotNull(recordMetaData1.get(0));
    }

    private void assertRecordMetadataExists(final int numMetadata) {
        List<RecordMetadata> recordMetaData1 = (List<RecordMetadata>) in.getHeader(KafkaConstants.KAFKA_RECORD_META);
        assertNotNull(recordMetaData1);
        assertEquals(recordMetaData1.size(), numMetadata, "Expected one recordMetaData");
        assertNotNull(recordMetaData1.get(0));
    }

    private void assertRecordMetadataExistsForEachAggregatedExchange() {
        List<Exchange> exchanges = (List<Exchange>) in.getBody();
        for (Exchange ex : exchanges) {
            List<RecordMetadata> recordMetaData
                    = (List<RecordMetadata>) ex.getMessage().getHeader(KafkaConstants.KAFKA_RECORD_META);
            assertNotNull(recordMetaData);
            assertEquals(1, recordMetaData.size(), "Expected one recordMetaData");
            assertNotNull(recordMetaData.get(0));
        }
    }

    private void assertRecordMetadataExistsForEachAggregatedMessage() {
        List<Message> messages = (List<Message>) in.getBody();
        for (Message msg : messages) {
            List<RecordMetadata> recordMetaData = (List<RecordMetadata>) msg.getHeader(KafkaConstants.KAFKA_RECORD_META);
            assertNotNull(recordMetaData);
            assertEquals(1, recordMetaData.size(), "Expected one recordMetaData");
            assertNotNull(recordMetaData.get(0));
        }
    }

    private Exchange aggregateExchanges(final List<Exchange> exchangesToAggregate, final AggregationStrategy strategy) {
        Exchange exchangeHolder = new DefaultExchange(context);

        for (final Exchange innerExchange : exchangesToAggregate) {
            exchangeHolder = strategy.aggregate(exchangeHolder, innerExchange);
        }

        strategy.onCompletion(exchangeHolder);

        return exchangeHolder;
    }

    private List<Exchange> createListOfExchangesWithTopics(final List<String> topics) {
        final List<Exchange> resultLists = new LinkedList<>();

        int index = 1;
        for (String topic : topics) {
            final Exchange innerExchange = new DefaultExchange(context);
            innerExchange.setExchangeId("exchange-" + index);
            final Message msg = innerExchange.getIn();
            msg.setMessageId("message-" + index);
            msg.setHeader(KafkaConstants.OVERRIDE_TOPIC, topic);
            msg.setHeader(SOME_INDIVIDUAL_HEADER, "value-" + index++);
            resultLists.add(innerExchange);
        }

        return resultLists;
    }
}
