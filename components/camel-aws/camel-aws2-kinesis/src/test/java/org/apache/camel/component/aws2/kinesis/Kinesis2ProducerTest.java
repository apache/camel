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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.RuntimeCamelException;
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
import software.amazon.awssdk.services.kinesis.model.KmsThrottlingException;
import software.amazon.awssdk.services.kinesis.model.PutRecordRequest;
import software.amazon.awssdk.services.kinesis.model.PutRecordResponse;
import software.amazon.awssdk.services.kinesis.model.PutRecordsRequest;
import software.amazon.awssdk.services.kinesis.model.PutRecordsRequestEntry;
import software.amazon.awssdk.services.kinesis.model.PutRecordsResponse;
import software.amazon.awssdk.services.kinesis.model.PutRecordsResultEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
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
    private final List<PutRecordsRequest> requests = new ArrayList<>();
    private final List<PutRecordsRequestEntry> written = new ArrayList<>();
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
    public void batchSendsAgainOnlyTheFailedRecordAndThenTheNextRequests() throws Exception {
        // the first record of the second request fails once
        Set<String> failedOnce = new HashSet<>();
        stubPutRecords(entry -> entry.data().asUtf8String().equals("r500") && failedOnce.add("r500"));

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader(Kinesis2Constants.PARTITION_KEY, "key");
        exchange.getIn().setBody(records(1200));

        producer.process(exchange);

        assertEquals(List.of(500, 500, 1, 200), requestSizes());
        assertEquals(List.of("r500"), data(requests.get(2).records()));
        // every record is written once
        assertEquals(1200, written.size());
        assertEquals(new HashSet<>(records(1200)), new HashSet<>(data(written)));
        assertEquals(1200, exchange.getMessage().getHeader(Kinesis2Constants.RECORD_COUNT, Integer.class));
        assertEquals(0, exchange.getMessage().getHeader(Kinesis2Constants.FAILED_RECORD_COUNT, Integer.class));
    }

    @Test
    public void batchWithARecordThatKeepsFailingThrowsAfterTheOtherRequestsAreSent() {
        stubPutRecords(entry -> entry.data().asUtf8String().equals("r100"));

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader(Kinesis2Constants.PARTITION_KEY, "key");
        exchange.getIn().setBody(records(600));

        RuntimeCamelException e = assertThrows(RuntimeCamelException.class, () -> producer.process(exchange));

        // the failed record is sent again 3 times, then the second request is sent
        assertEquals(List.of(500, 1, 1, 1, 100), requestSizes());
        assertEquals(599, written.size());
        assertEquals(600, exchange.getMessage().getHeader(Kinesis2Constants.RECORD_COUNT, Integer.class));
        assertEquals(1, exchange.getMessage().getHeader(Kinesis2Constants.FAILED_RECORD_COUNT, Integer.class));
        assertTrue(e.getMessage().contains("Failed to send 1 of 600 records"), e.getMessage());
        assertTrue(e.getMessage().contains("599 records were written"), e.getMessage());
        assertTrue(e.getMessage().contains("ProvisionedThroughputExceededException"), e.getMessage());
    }

    @Test
    public void batchSetsTheCountsWhenAPutRecordsCallThrows() {
        // a record of the second request fails, and the call that sends it again throws
        KmsThrottlingException failure = KmsThrottlingException.builder().message("Rate exceeded").build();
        when(kinesisClient.putRecords(any(PutRecordsRequest.class))).thenAnswer(invocation -> {
            PutRecordsRequest request = invocation.getArgument(0);
            if (requests.size() == 2) {
                requests.add(request);
                throw failure;
            }
            return respond(request, entry -> entry.data().asUtf8String().equals("r600"));
        });

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader(Kinesis2Constants.PARTITION_KEY, "key");
        exchange.getIn().setBody(records(1200));

        KmsThrottlingException e = assertThrows(KmsThrottlingException.class, () -> producer.process(exchange));

        // the exception of the call fails the exchange as it is, and the last request is not sent
        assertSame(failure, e);
        assertEquals(List.of(500, 500, 1), requestSizes());
        assertEquals(999, written.size());
        // the record of the call that threw and the 200 records of the request that was not sent are not written
        assertEquals(1200, exchange.getMessage().getHeader(Kinesis2Constants.RECORD_COUNT, Integer.class));
        assertEquals(201, exchange.getMessage().getHeader(Kinesis2Constants.FAILED_RECORD_COUNT, Integer.class));
    }

    @Test
    public void batchSendsAgainARecordWithoutResultInTheResponse() throws Exception {
        // the first response has no result for the last record of the request
        when(kinesisClient.putRecords(any(PutRecordsRequest.class))).thenAnswer(invocation -> {
            PutRecordsRequest request = invocation.getArgument(0);
            if (requests.isEmpty()) {
                PutRecordsResponse response = respond(request, entry -> entry.data().asUtf8String().equals("r2"));
                return response.toBuilder().failedRecordCount(0).records(response.records().subList(0, 2)).build();
            }
            return respond(request, entry -> false);
        });

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader(Kinesis2Constants.PARTITION_KEY, "key");
        exchange.getIn().setBody(records(3));

        producer.process(exchange);

        assertEquals(List.of(3, 1), requestSizes());
        assertEquals(List.of("r2"), data(requests.get(1).records()));
        assertEquals(records(3), data(written));
        assertEquals(3, exchange.getMessage().getHeader(Kinesis2Constants.RECORD_COUNT, Integer.class));
        assertEquals(0, exchange.getMessage().getHeader(Kinesis2Constants.FAILED_RECORD_COUNT, Integer.class));
    }

    @Test
    public void retryDelayDoublesAndHasARandomPart() {
        for (int retry = 0; retry < 3; retry++) {
            long base = 100L << retry;
            Set<Long> delays = new HashSet<>();
            for (int i = 0; i < 200; i++) {
                long delay = Kinesis2Producer.retryDelayMillis(retry);
                assertTrue(delay >= base && delay <= base + base / 4, "delay " + delay + " ms for retry " + retry);
                delays.add(delay);
            }
            // producers throttled at the same time do not all wait the same time before they send again
            assertTrue(delays.size() > 1, "the delay of retry " + retry + " has no random part: " + delays);
        }
    }

    @Test
    public void batchIsSplitAtTheRequestSizeLimit() throws Exception {
        stubPutRecords(entry -> false);

        // with its partition key of 2 bytes a record is 1 MiB, so 10 records fill a request of 10 MiB
        List<byte[]> body = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            body.add(new byte[1024 * 1024 - 2]);
        }
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader(Kinesis2Constants.PARTITION_KEY, "pk");
        exchange.getIn().setBody(body);

        producer.process(exchange);

        assertEquals(List.of(10, 10, 1), requestSizes());
        assertEquals(21, written.size());
        assertEquals(21, exchange.getMessage().getHeader(Kinesis2Constants.RECORD_COUNT, Integer.class));
    }

    @Test
    public void batchWithARecordAboveTheRecordLimitFailsBeforeAnyRequestIsSent() {
        // 10 MiB of data and a partition key of 2 bytes
        List<Object> body = List.of("r0", new byte[10 * 1024 * 1024]);
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader(Kinesis2Constants.PARTITION_KEY, "pk");
        exchange.getIn().setBody(body);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> producer.process(exchange));

        assertTrue(e.getMessage().contains("The record at index 1 of the batch is 10485762 bytes"), e.getMessage());
        verify(kinesisClient, never()).putRecords(any(PutRecordsRequest.class));
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

    /**
     * Kinesis writes the records of a PutRecords request, except the records the predicate selects: their result has an
     * error code, as in a partly failed request.
     */
    private void stubPutRecords(Predicate<PutRecordsRequestEntry> fails) {
        when(kinesisClient.putRecords(any(PutRecordsRequest.class)))
                .thenAnswer(invocation -> respond(invocation.getArgument(0), fails));
    }

    private PutRecordsResponse respond(PutRecordsRequest request, Predicate<PutRecordsRequestEntry> fails) {
        requests.add(request);
        List<PutRecordsResultEntry> results = new ArrayList<>();
        int failed = 0;
        for (PutRecordsRequestEntry entry : request.records()) {
            if (fails.test(entry)) {
                failed++;
                results.add(PutRecordsResultEntry.builder()
                        .errorCode("ProvisionedThroughputExceededException")
                        .errorMessage("Rate exceeded for shard shardId-000000000000")
                        .build());
            } else {
                written.add(entry);
                results.add(PutRecordsResultEntry.builder()
                        .shardId("shardId-000000000000")
                        .sequenceNumber(String.valueOf(written.size()))
                        .build());
            }
        }
        return PutRecordsResponse.builder().failedRecordCount(failed).records(results).build();
    }

    private List<Integer> requestSizes() {
        return requests.stream().map(request -> request.records().size()).toList();
    }

    private static List<String> records(int count) {
        return IntStream.range(0, count).mapToObj(i -> "r" + i).toList();
    }

    private static List<String> data(List<PutRecordsRequestEntry> entries) {
        return entries.stream().map(entry -> entry.data().asUtf8String()).toList();
    }
}
