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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.AsyncProcessorSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.GetRecordsRequest;
import software.amazon.awssdk.services.kinesis.model.GetRecordsResponse;
import software.amazon.awssdk.services.kinesis.model.GetShardIteratorRequest;
import software.amazon.awssdk.services.kinesis.model.GetShardIteratorResponse;
import software.amazon.awssdk.services.kinesis.model.ListShardsRequest;
import software.amazon.awssdk.services.kinesis.model.ListShardsResponse;
import software.amazon.awssdk.services.kinesis.model.Record;
import software.amazon.awssdk.services.kinesis.model.SequenceNumberRange;
import software.amazon.awssdk.services.kinesis.model.Shard;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * The Limit of the GetRecords requests of the polling consumer: {@code maxResultsPerRequest} records from each shard in
 * each poll, 1000 by default.
 */
@ExtendWith(MockitoExtension.class)
public class KinesisConsumerGetRecordsLimitTest {

    private static final int BACKLOG = 1500;

    @Mock
    private KinesisClient kinesisClient;

    private final CamelContext context = new DefaultCamelContext();
    private final ConcurrentLinkedQueue<Integer> limits = new ConcurrentLinkedQueue<>();
    private final AtomicInteger position = new AtomicInteger();
    private final AtomicInteger routed = new AtomicInteger();

    private Kinesis2Consumer consumer;

    @BeforeEach
    public void setup() {
        Shard shard = Shard.builder().shardId("shard-0")
                .sequenceNumberRange(SequenceNumberRange.builder().startingSequenceNumber("0").build()).build();
        when(kinesisClient.listShards(any(ListShardsRequest.class)))
                .thenReturn(ListShardsResponse.builder().shards(shard).build());
        when(kinesisClient.getShardIterator(any(GetShardIteratorRequest.class)))
                .thenReturn(GetShardIteratorResponse.builder().shardIterator("iterator").build());
        // one shard with a backlog of BACKLOG records: a GetRecords returns as many of them as its Limit asks for
        when(kinesisClient.getRecords(any(GetRecordsRequest.class))).thenAnswer(invocation -> {
            GetRecordsRequest request = invocation.getArgument(0);
            limits.add(request.limit());
            List<Record> records = new ArrayList<>();
            while (records.size() < request.limit() && position.get() < BACKLOG) {
                int sequenceNumber = position.getAndIncrement();
                records.add(Record.builder().sequenceNumber(String.valueOf(sequenceNumber)).partitionKey("key")
                        .data(SdkBytes.fromString("record " + sequenceNumber, StandardCharsets.UTF_8)).build());
            }
            return GetRecordsResponse.builder().records(records).nextShardIterator("iterator").build();
        });

        context.getRegistry().bind("kinesisClient", kinesisClient);
        context.start();
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (consumer != null) {
            consumer.stop();
        }
        context.stop();
    }

    @Test
    public void defaultLimitIsOneThousandRecords() throws Exception {
        startConsumer("aws2-kinesis://stream?amazonKinesisClient=#kinesisClient");

        assertEquals(1000, consumer.poll(), "the first poll routes 1000 records of the backlog");
        assertEquals(500, consumer.poll(), "the second poll routes the rest of the backlog");

        assertEquals(List.of(1000, 1000), List.copyOf(limits));
        assertEquals(BACKLOG, routed.get());
    }

    @Test
    public void maxResultsPerRequestOneFetchesOneRecordPerPoll() throws Exception {
        startConsumer("aws2-kinesis://stream?amazonKinesisClient=#kinesisClient&maxResultsPerRequest=1");

        assertEquals(1, consumer.poll());
        assertEquals(1, consumer.poll());

        assertEquals(List.of(1, 1), List.copyOf(limits));
        assertEquals(2, routed.get());
    }

    private void startConsumer(String uri) throws Exception {
        Kinesis2Endpoint endpoint = context.getEndpoint(uri, Kinesis2Endpoint.class);
        consumer = (Kinesis2Consumer) endpoint.createConsumer(new AsyncProcessorSupport() {
            @Override
            public boolean process(Exchange exchange, AsyncCallback callback) {
                routed.incrementAndGet();
                callback.done(true);
                return true;
            }
        });
        // the test calls poll()
        consumer.setStartScheduler(false);
        consumer.start();
        await().atMost(10, TimeUnit.SECONDS).until(() -> consumer.getCurrentShardList().size() == 1);
    }
}
