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

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
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
import software.amazon.awssdk.services.kinesis.model.ShardIteratorType;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies that polling a stream with more than one open shard is handled correctly: every shard is fetched and the
 * number of processed exchanges returned by {@link Kinesis2Consumer#poll()} is the sum across all shards rather than
 * the count of whichever shard happened to finish last.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class KinesisConsumerMultiShardTest {
    @Mock
    private KinesisClient kinesisClient;
    @Mock
    private AsyncProcessor processor;

    private final CamelContext context = new DefaultCamelContext();
    private final Kinesis2Component component = new Kinesis2Component(context);

    private Kinesis2Consumer underTest;

    @BeforeEach
    public void setup() {
        SequenceNumberRange range = SequenceNumberRange.builder().endingSequenceNumber("2").build();
        ArrayList<Shard> shardList = new ArrayList<>();
        shardList.add(Shard.builder().shardId("shardId1").sequenceNumberRange(range).build());
        shardList.add(Shard.builder().shardId("shardId2").sequenceNumberRange(range).build());

        // Two records per getRecords call, with a non-null next iterator so both shards stay open across the poll.
        var records = GetRecordsResponse.builder()
                .nextShardIterator("nextIterator")
                .records(
                        Record.builder().sequenceNumber("1")
                                .data(SdkBytes.fromString("Hello", Charset.defaultCharset()))
                                .build(),
                        Record.builder().sequenceNumber("2")
                                .data(SdkBytes.fromString("Bye", Charset.defaultCharset()))
                                .build())
                .build();
        when(kinesisClient.getRecords(any(GetRecordsRequest.class))).thenReturn(records);
        when(kinesisClient.getShardIterator(any(GetShardIteratorRequest.class)))
                .thenReturn(GetShardIteratorResponse.builder().shardIterator("shardIterator").build());
        when(kinesisClient.listShards(any(ListShardsRequest.class)))
                .thenReturn(ListShardsResponse.builder().shards(shardList).build());

        component.start();

        Kinesis2Configuration configuration = new Kinesis2Configuration();
        configuration.setAmazonKinesisClient(kinesisClient);
        configuration.setIteratorType(ShardIteratorType.LATEST);
        configuration.setStreamName("streamName");

        Kinesis2Endpoint endpoint = new Kinesis2Endpoint("aws2-kinesis:foo", configuration, component);
        endpoint.start();
        underTest = new Kinesis2Consumer(endpoint, processor);
        underTest.setConnection(component.getConnection());
        underTest.start();
        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> underTest.getCurrentShardList().size() == 2);
    }

    @Test
    public void pollAccumulatesTheRecordCountAcrossAllShards() throws Exception {
        // Two shards, two records each -> poll must report all four, not just the last shard's two.
        int processed = underTest.poll();

        Assertions.assertEquals(4, processed, "poll() must sum the processed records across every shard");
        verify(kinesisClient, times(2)).getRecords(any(GetRecordsRequest.class));
    }
}
