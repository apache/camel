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
package org.apache.camel.component.aws2.kinesis;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.PutRecordRequest;
import software.amazon.awssdk.services.kinesis.model.PutRecordResponse;
import software.amazon.awssdk.services.kinesis.model.PutRecordsRequest;
import software.amazon.awssdk.services.kinesis.model.PutRecordsRequestEntry;
import software.amazon.awssdk.services.kinesis.model.PutRecordsResponse;
import software.amazon.awssdk.services.kinesis.model.PutRecordsResultEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class Kinesis2ProducerTest {

    @Mock
    private KinesisClient kinesisClient;

    private final CamelContext context = new DefaultCamelContext();
    private final Kinesis2Component component = new Kinesis2Component(context);
    private Kinesis2Producer producer;

    @BeforeEach
    public void setup() {
        component.start();

        Kinesis2Configuration configuration = new Kinesis2Configuration();
        configuration.setAmazonKinesisClient(kinesisClient);
        configuration.setStreamName("testStream");

        Kinesis2Endpoint endpoint = new Kinesis2Endpoint("aws2-kinesis:testStream", configuration, component);
        endpoint.start();
        producer = new Kinesis2Producer(endpoint);
        producer.setConnection(component.getConnection());
        producer.start();
    }

    @Test
    public void batchWithSinglePartitionKeyAppliesSameKeyToAll() throws Exception {
        when(kinesisClient.putRecords(any(PutRecordsRequest.class)))
                .thenReturn(PutRecordsResponse.builder()
                        .failedRecordCount(0)
                        .records(PutRecordsResultEntry.builder().build(),
                                PutRecordsResultEntry.builder().build())
                        .build());

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader(Kinesis2Constants.PARTITION_KEY, "shared-key");
        exchange.getIn().setBody(Arrays.asList("record1", "record2"));

        producer.process(exchange);

        ArgumentCaptor<PutRecordsRequest> captor = ArgumentCaptor.forClass(PutRecordsRequest.class);
        verify(kinesisClient).putRecords(captor.capture());

        List<PutRecordsRequestEntry> entries = captor.getValue().records();
        assertEquals(2, entries.size());
        assertEquals("shared-key", entries.get(0).partitionKey());
        assertEquals("shared-key", entries.get(1).partitionKey());
    }

    @Test
    public void batchWithPerRecordPartitionKeys() throws Exception {
        when(kinesisClient.putRecords(any(PutRecordsRequest.class)))
                .thenReturn(PutRecordsResponse.builder()
                        .failedRecordCount(0)
                        .records(PutRecordsResultEntry.builder().build(),
                                PutRecordsResultEntry.builder().build(),
                                PutRecordsResultEntry.builder().build())
                        .build());

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader(Kinesis2Constants.PARTITION_KEYS,
                Arrays.asList("key-A", "key-B", "key-C"));
        exchange.getIn().setBody(Arrays.asList("record1", "record2", "record3"));

        producer.process(exchange);

        ArgumentCaptor<PutRecordsRequest> captor = ArgumentCaptor.forClass(PutRecordsRequest.class);
        verify(kinesisClient).putRecords(captor.capture());

        List<PutRecordsRequestEntry> entries = captor.getValue().records();
        assertEquals(3, entries.size());
        assertEquals("key-A", entries.get(0).partitionKey());
        assertEquals("key-B", entries.get(1).partitionKey());
        assertEquals("key-C", entries.get(2).partitionKey());
    }

    @Test
    public void batchWithFewerPartitionKeysFallsBackToSingleKey() throws Exception {
        when(kinesisClient.putRecords(any(PutRecordsRequest.class)))
                .thenReturn(PutRecordsResponse.builder()
                        .failedRecordCount(0)
                        .records(PutRecordsResultEntry.builder().build(),
                                PutRecordsResultEntry.builder().build(),
                                PutRecordsResultEntry.builder().build())
                        .build());

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader(Kinesis2Constants.PARTITION_KEY, "fallback-key");
        exchange.getIn().setHeader(Kinesis2Constants.PARTITION_KEYS,
                Arrays.asList("key-A"));
        exchange.getIn().setBody(Arrays.asList("record1", "record2", "record3"));

        producer.process(exchange);

        ArgumentCaptor<PutRecordsRequest> captor = ArgumentCaptor.forClass(PutRecordsRequest.class);
        verify(kinesisClient).putRecords(captor.capture());

        List<PutRecordsRequestEntry> entries = captor.getValue().records();
        assertEquals(3, entries.size());
        assertEquals("key-A", entries.get(0).partitionKey());
        assertEquals("fallback-key", entries.get(1).partitionKey());
        assertEquals("fallback-key", entries.get(2).partitionKey());
    }

    @Test
    public void batchWithFewerPartitionKeysAndNoFallbackThrows() {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader(Kinesis2Constants.PARTITION_KEYS,
                Arrays.asList("key-A"));
        exchange.getIn().setBody(Arrays.asList("record1", "record2", "record3"));

        assertThrows(IllegalArgumentException.class, () -> producer.process(exchange));
    }

    @Test
    public void batchWithEmptyPartitionKeysListThrows() {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader(Kinesis2Constants.PARTITION_KEYS, List.of());
        exchange.getIn().setBody(Arrays.asList("record1"));

        assertThrows(IllegalArgumentException.class, () -> producer.process(exchange));
    }

    @Test
    public void pathBodyIsSentAsSingleRecordWithTheFileContent(@TempDir Path dir) throws Exception {
        when(kinesisClient.putRecord(any(PutRecordRequest.class)))
                .thenReturn(PutRecordResponse.builder().sequenceNumber("1").shardId("shard-1").build());

        Path file = dir.resolve("data.txt");
        Files.writeString(file, "Hello Kinesis", StandardCharsets.UTF_8);

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader(Kinesis2Constants.PARTITION_KEY, "key");
        exchange.getIn().setBody(file);

        producer.process(exchange);

        ArgumentCaptor<PutRecordRequest> captor = ArgumentCaptor.forClass(PutRecordRequest.class);
        verify(kinesisClient).putRecord(captor.capture());
        verify(kinesisClient, never()).putRecords(any(PutRecordsRequest.class));
        assertEquals("Hello Kinesis", captor.getValue().data().asUtf8String());
        assertEquals("shard-1", exchange.getMessage().getHeader(Kinesis2Constants.SHARD_ID));
    }

    @Test
    public void bodyThatDoesNotConvertToBytesFailsWithInvalidPayload() {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader(Kinesis2Constants.PARTITION_KEY, "key");
        exchange.getIn().setBody(Map.of("a", "b"));

        InvalidPayloadException e = assertThrows(InvalidPayloadException.class, () -> producer.process(exchange));
        assertTrue(e.getMessage().contains("java.util."), e.getMessage());
        verify(kinesisClient, never()).putRecord(any(PutRecordRequest.class));
    }
}
