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
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;

public class KafkaProducerTest {

    private KafkaProducer producer;
    private KafkaEndpoint endpoint;
    private KafkaEndpoint fromEndpoint;

    private TypeConverter converter = Mockito.mock(TypeConverter.class);
    private CamelContext context = Mockito.mock(CamelContext.class);
    private Exchange exchange = Mockito.mock(Exchange.class);
    private ExtendedCamelContext camelContext = Mockito.mock(ExtendedCamelContext.class);
    private Message in = new DefaultMessage(camelContext);
    private Message out = new DefaultMessage(camelContext);
    private AsyncCallback callback = Mockito.mock(AsyncCallback.class);

    @SuppressWarnings({"unchecked"})
    public KafkaProducerTest() throws Exception {
        KafkaComponent kafka = new KafkaComponent(new DefaultCamelContext());
        kafka.getConfiguration().setBrokers("broker1:1234,broker2:4567");
        kafka.init();

        endpoint = kafka.createEndpoint("kafka:sometopic", "sometopic", new HashMap());
        producer = new KafkaProducer(endpoint);

        fromEndpoint = kafka.createEndpoint("kafka:fromtopic", "fromtopic", new HashMap());

        RecordMetadata rm = new RecordMetadata(null, 0, 0, 0, 0L, 0, 0);
        Future future = Mockito.mock(Future.class);
        Mockito.when(future.get()).thenReturn(rm);
        org.apache.kafka.clients.producer.KafkaProducer kp = Mockito.mock(org.apache.kafka.clients.producer.KafkaProducer.class);
        Mockito.when(kp.send(any(ProducerRecord.class))).thenReturn(future);

        Mockito.when(exchange.getContext()).thenReturn(context);
        Mockito.when(context.getTypeConverter()).thenReturn(converter);
        Mockito.when(converter.tryConvertTo(String.class, exchange, null)).thenReturn(null);
        Mockito.when(camelContext.adapt(ExtendedCamelContext.class)).thenReturn(camelContext);
        Mockito.when(camelContext.adapt(ExtendedCamelContext.class).getHeadersMapFactory()).thenReturn(new DefaultHeadersMapFactory());
        Mockito.when(camelContext.getTypeConverter()).thenReturn(converter);

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
        Mockito.when(exchange.getMessage()).thenReturn(out);

        in.setHeader(KafkaConstants.PARTITION_KEY, 4);

        producer.process(exchange);
        Mockito.verify(producer.getKafkaProducer()).send(any(ProducerRecord.class));
        assertRecordMetadataExists();
    }

    @Test(expected = Exception.class)
    @SuppressWarnings({"unchecked"})
    public void processSendsMessageWithException() throws Exception {
        endpoint.getConfiguration().setTopic("sometopic");
        // setup the exception here
        org.apache.kafka.clients.producer.KafkaProducer kp = producer.getKafkaProducer();
        Mockito.when(kp.send(any(ProducerRecord.class))).thenThrow(new ApiException());
        Mockito.when(exchange.getIn()).thenReturn(in);
        in.setHeader(KafkaConstants.PARTITION_KEY, 4);

        producer.process(exchange);

        assertRecordMetadataExists();
    }

    @Test
    public void processAsyncSendsMessage() throws Exception {
        endpoint.getConfiguration().setTopic("sometopic");
        Mockito.when(exchange.getIn()).thenReturn(in);
        Mockito.when(exchange.getMessage()).thenReturn(out);

        in.setHeader(KafkaConstants.PARTITION_KEY, 4);

        producer.process(exchange, callback);

        ArgumentCaptor<Callback> callBackCaptor = ArgumentCaptor.forClass(Callback.class);
        Mockito.verify(producer.getKafkaProducer()).send(any(ProducerRecord.class), callBackCaptor.capture());
        Callback kafkaCallback = callBackCaptor.getValue();
        kafkaCallback.onCompletion(new RecordMetadata(null, 0, 0, 0, 0L, 0, 0), null);
        assertRecordMetadataExists();
    }

    @Test
    public void processAsyncSendsMessageWithException() throws Exception {
        endpoint.getConfiguration().setTopic("sometopic");
        Mockito.when(exchange.getIn()).thenReturn(in);
        Mockito.when(exchange.getMessage()).thenReturn(out);

        // setup the exception here
        org.apache.kafka.clients.producer.KafkaProducer kp = producer.getKafkaProducer();
        Mockito.when(kp.send(any(ProducerRecord.class), any(Callback.class))).thenThrow(new ApiException());

        in.setHeader(KafkaConstants.PARTITION_KEY, 4);

        producer.process(exchange, callback);

        ArgumentCaptor<Callback> callBackCaptor = ArgumentCaptor.forClass(Callback.class);
        Mockito.verify(producer.getKafkaProducer()).send(any(ProducerRecord.class), callBackCaptor.capture());
        Mockito.verify(exchange).setException(isA(ApiException.class));
        Mockito.verify(callback).done(eq(true));
        Callback kafkaCallback = callBackCaptor.getValue();
        kafkaCallback.onCompletion(new RecordMetadata(null, 0, 0, 0, 0L, 0, 0), null);
        assertRecordMetadataExists();
    }

    @Test
    public void processSendsMessageWithTopicHeaderAndNoTopicInEndPoint() throws Exception {
        endpoint.getConfiguration().setTopic(null);
        Mockito.when(exchange.getIn()).thenReturn(in);
        in.setHeader(KafkaConstants.TOPIC, "anotherTopic");
        Mockito.when(exchange.getMessage()).thenReturn(out);

        producer.process(exchange);

        verifySendMessage("sometopic");
        assertRecordMetadataExists();
    }

    @Test
    public void processSendsMessageWithTopicHeaderAndEndPoint() throws Exception {
        endpoint.getConfiguration().setTopic("sometopic");
        Mockito.when(exchange.getIn()).thenReturn(in);
        Mockito.when(exchange.getMessage()).thenReturn(out);

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
        Mockito.when(exchange.getMessage()).thenReturn(out);

        in.setHeader(KafkaConstants.PARTITION_KEY, 4);
        in.setHeader(KafkaConstants.OVERRIDE_TOPIC, "anotherTopic");
        in.setHeader(KafkaConstants.KEY, "someKey");

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
        Mockito.when(exchange.getMessage()).thenReturn(out);

        producer.process(exchange);

        assertRecordMetadataExists();
    }

    @Test
    public void processSendsMessageWithPartitionKeyHeader() throws Exception {
        endpoint.getConfiguration().setTopic("someTopic");
        Mockito.when(exchange.getIn()).thenReturn(in);
        Mockito.when(exchange.getMessage()).thenReturn(out);
        in.setHeader(KafkaConstants.PARTITION_KEY, 4);
        in.setHeader(KafkaConstants.KEY, "someKey");

        producer.process(exchange);

        verifySendMessage(4, "someTopic", "someKey");
        assertRecordMetadataExists();
    }

    @Test
    public void processSendsMessageWithMessageKeyHeader() throws Exception {
        endpoint.getConfiguration().setTopic("someTopic");
        Mockito.when(exchange.getIn()).thenReturn(in);
        Mockito.when(exchange.getMessage()).thenReturn(out);
        in.setHeader(KafkaConstants.KEY, "someKey");

        producer.process(exchange);

        verifySendMessage("someTopic", "someKey");
        assertRecordMetadataExists();
    }

    @Test
    public void processSendMessageWithTopicHeader() throws Exception {
        endpoint.getConfiguration().setTopic("someTopic");
        Mockito.when(exchange.getIn()).thenReturn(in);
        Mockito.when(exchange.getMessage()).thenReturn(out);
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
        Mockito.when(exchange.getMessage()).thenReturn(out);

        producer.process(exchange);

        verifySendMessage("someTopic");
        assertRecordMetadataExists();
    }

    @Test
    public void processSendsMessageWithListOfExchangesWithOverrideTopicHeaderOnEveryExchange() throws Exception {
        endpoint.getConfiguration().setTopic("someTopic");
        Mockito.when(exchange.getIn()).thenReturn(in);

        // we set the initial topic
        in.setHeader(KafkaConstants.OVERRIDE_TOPIC, "anotherTopic");
        in.setHeader(KafkaConstants.KEY, "someKey");

        // we add our exchanges in order to aggregate
        final List<Exchange> nestedExchanges = createListOfExchangesWithTopics(Arrays.asList("overridenTopic1", "overridenTopic2", "overridenTopic3"));

        // aggregate
        final Exchange finalAggregatedExchange = aggregateExchanges(nestedExchanges, new GroupedExchangeAggregationStrategy());

        in.setBody(finalAggregatedExchange.getIn().getBody());
        in.setHeaders(finalAggregatedExchange.getIn().getHeaders());

        producer.process(exchange);

        // assert results
        verifySendMessages(Arrays.asList("overridenTopic1", "overridenTopic2", "overridenTopic3"));
        assertRecordMetadataExists(3);
        assertRecordMetadataExistsForEachAggregatedExchange();
    }

    @Test
    public void processSendsMessageWithListOfMessagesWithOverrideTopicHeaderOnEveryExchange() throws Exception {
        endpoint.getConfiguration().setTopic("someTopic");
        Mockito.when(exchange.getIn()).thenReturn(in);

        // we set the initial topic
        in.setHeader(KafkaConstants.OVERRIDE_TOPIC, "anotherTopic");
        in.setHeader(KafkaConstants.KEY, "someKey");

        // we add our exchanges in order to aggregate
        final List<Exchange> nestedExchanges = createListOfExchangesWithTopics(Arrays.asList("overridenTopic1", "overridenTopic2", "overridenTopic3"));

        // aggregate
        final Exchange finalAggregatedExchange = aggregateExchanges(nestedExchanges, new GroupedMessageAggregationStrategy());

        in.setBody(finalAggregatedExchange.getIn().getBody());
        in.setHeaders(finalAggregatedExchange.getIn().getHeaders());

        producer.process(exchange);

        // assert results
        verifySendMessages(Arrays.asList("overridenTopic1", "overridenTopic2", "overridenTopic3"));
        assertRecordMetadataExists(3);
        assertRecordMetadataExistsForEachAggregatedMessage();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    protected void verifySendMessage(Integer partitionKey, String topic, String messageKey) {
        ArgumentCaptor<ProducerRecord> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        Mockito.verify(producer.getKafkaProducer()).send(captor.capture());
        assertEquals(partitionKey, captor.getValue().partition());
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

    @SuppressWarnings({"unchecked", "rawtypes"})
    protected void verifySendMessages(final List<String> expectedTopics) {
        final ArgumentCaptor<ProducerRecord> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        Mockito.verify(producer.getKafkaProducer(), Mockito.atLeast(expectedTopics.size())).send(captor.capture());
        final List<String> actualTopics = captor.getAllValues().stream().map(ProducerRecord::topic).collect(Collectors.toList());

        assertEquals(expectedTopics, actualTopics);
    }

    private void assertRecordMetadataExists() {
        List<RecordMetadata> recordMetaData1 = (List<RecordMetadata>)in.getHeader(KafkaConstants.KAFKA_RECORDMETA);
        assertTrue(recordMetaData1 != null);
        assertEquals("Expected one recordMetaData", recordMetaData1.size(), 1);
        assertTrue(recordMetaData1.get(0) != null);
    }

    private void assertRecordMetadataExists(final int numMetadata) {
        List<RecordMetadata> recordMetaData1 = (List<RecordMetadata>)in.getHeader(KafkaConstants.KAFKA_RECORDMETA);
        assertTrue(recordMetaData1 != null);
        assertEquals("Expected one recordMetaData", recordMetaData1.size(), numMetadata);
        assertTrue(recordMetaData1.get(0) != null);
    }

    private void assertRecordMetadataExistsForEachAggregatedExchange() {
        List<Exchange> exchanges = (List<Exchange>)in.getBody();
        for (Exchange ex : exchanges) {
            List<RecordMetadata> recordMetaData = (List<RecordMetadata>)ex.getMessage().getHeader(KafkaConstants.KAFKA_RECORDMETA);
            assertTrue(recordMetaData != null);
            assertEquals("Expected one recordMetaData", recordMetaData.size(), 1);
            assertTrue(recordMetaData.get(0) != null);
        }
    }

    private void assertRecordMetadataExistsForEachAggregatedMessage() {
        List<Message> messages = (List<Message>)in.getBody();
        for (Message msg : messages) {
            List<RecordMetadata> recordMetaData = (List<RecordMetadata>)msg.getHeader(KafkaConstants.KAFKA_RECORDMETA);
            assertTrue(recordMetaData != null);
            assertEquals("Expected one recordMetaData", recordMetaData.size(), 1);
            assertTrue(recordMetaData.get(0) != null);
        }
    }

    private Exchange aggregateExchanges(final List<Exchange> exchangesToAggregate, final AggregationStrategy strategy) {
        Exchange exchangeHolder = new DefaultExchange(camelContext);

        for (final Exchange innerExchange : exchangesToAggregate) {
            exchangeHolder = strategy.aggregate(exchangeHolder, innerExchange);
        }

        strategy.onCompletion(exchangeHolder);

        return exchangeHolder;
    }

    private List<Exchange> createListOfExchangesWithTopics(final List<String> topics) {
        final List<Exchange> resultLists = new LinkedList<>();

        topics.forEach(topic -> {
            final Exchange innerExchange = new DefaultExchange(camelContext);
            innerExchange.getIn().setHeader(KafkaConstants.OVERRIDE_TOPIC, topic);
            resultLists.add(innerExchange);
        });

        return resultLists;
    }
}
