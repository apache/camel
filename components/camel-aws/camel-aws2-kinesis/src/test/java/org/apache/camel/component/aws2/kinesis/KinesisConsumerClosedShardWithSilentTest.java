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

import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.DescribeStreamRequest;
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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class KinesisConsumerClosedShardWithSilentTest {

    @Mock
    private KinesisClient kinesisClient;
    @Mock
    private AsyncProcessor processor;

    private final CamelContext context = new DefaultCamelContext();
    private final Kinesis2Component component = new Kinesis2Component(context);

    private Kinesis2Consumer underTest;

    @BeforeEach
    public void setup() {
        Kinesis2Configuration configuration = new Kinesis2Configuration();
        configuration.setAmazonKinesisClient(kinesisClient);
        configuration.setIteratorType(ShardIteratorType.LATEST);
        configuration.setShardClosed(Kinesis2ShardClosedStrategyEnum.silent);
        configuration.setStreamName("streamName");
        Kinesis2Endpoint endpoint = new Kinesis2Endpoint("aws2-kinesis:foo", configuration, component);
        endpoint.start();
        underTest = new Kinesis2Consumer(endpoint, processor);

        SequenceNumberRange range = SequenceNumberRange.builder().endingSequenceNumber("20").build();
        Shard shard = Shard.builder().shardId("shardId").sequenceNumberRange(range).build();
        ArrayList<Shard> shardList = new ArrayList<>();
        shardList.add(shard);

        when(kinesisClient.getRecords(any(GetRecordsRequest.class))).thenReturn(GetRecordsResponse.builder()
                .nextShardIterator("shardIterator")
                .records(
                        Record.builder().sequenceNumber("1").data(SdkBytes.fromString("Hello", Charset.defaultCharset()))
                                .build(),
                        Record.builder().sequenceNumber("2").data(SdkBytes.fromString("Hello", Charset.defaultCharset()))
                                .build())
                .build());
        when(kinesisClient.getShardIterator(any(GetShardIteratorRequest.class)))
                .thenReturn(GetShardIteratorResponse.builder().shardIterator("shardIterator").build());
        when(kinesisClient.listShards(any(ListShardsRequest.class)))
                .thenReturn(ListShardsResponse.builder().shards(shardList).build());

        context.start();
        underTest.start();
    }

    @Test
    public void itObtainsAShardIteratorOnFirstPoll() throws Exception {
        underTest.poll();

        final ArgumentCaptor<DescribeStreamRequest> describeStreamReqCap = ArgumentCaptor.forClass(DescribeStreamRequest.class);
        final ArgumentCaptor<GetShardIteratorRequest> getShardIteratorReqCap
                = ArgumentCaptor.forClass(GetShardIteratorRequest.class);

        verify(kinesisClient).getShardIterator(getShardIteratorReqCap.capture());
        assertThat(getShardIteratorReqCap.getValue().streamName(), is("streamName"));
        assertThat(getShardIteratorReqCap.getValue().shardId(), is("shardId"));
        assertThat(getShardIteratorReqCap.getValue().shardIteratorType(), is(ShardIteratorType.LATEST));
    }

    @Test
    public void itDoesNotMakeADescribeStreamRequestIfShardIdIsSet() throws Exception {
        underTest.getEndpoint().getConfiguration().setShardId("shardId");

        underTest.poll();

        final ArgumentCaptor<GetShardIteratorRequest> getShardIteratorReqCap
                = ArgumentCaptor.forClass(GetShardIteratorRequest.class);

        verify(kinesisClient).getShardIterator(getShardIteratorReqCap.capture());
        assertThat(getShardIteratorReqCap.getValue().streamName(), is("streamName"));
        assertThat(getShardIteratorReqCap.getValue().shardId(), is("shardId"));
        assertThat(getShardIteratorReqCap.getValue().shardIteratorType(), is(ShardIteratorType.LATEST));
    }

    @Test
    public void itObtainsAShardIteratorOnFirstPollForSequenceNumber() throws Exception {
        underTest.getEndpoint().getConfiguration().setSequenceNumber("12345");
        underTest.getEndpoint().getConfiguration().setIteratorType(ShardIteratorType.AFTER_SEQUENCE_NUMBER);

        underTest.poll();

        final ArgumentCaptor<DescribeStreamRequest> describeStreamReqCap = ArgumentCaptor.forClass(DescribeStreamRequest.class);
        final ArgumentCaptor<GetShardIteratorRequest> getShardIteratorReqCap
                = ArgumentCaptor.forClass(GetShardIteratorRequest.class);

        verify(kinesisClient).getShardIterator(getShardIteratorReqCap.capture());
        assertThat(getShardIteratorReqCap.getValue().streamName(), is("streamName"));
        assertThat(getShardIteratorReqCap.getValue().shardId(), is("shardId"));
        assertThat(getShardIteratorReqCap.getValue().shardIteratorType(), is(ShardIteratorType.AFTER_SEQUENCE_NUMBER));
        assertThat(getShardIteratorReqCap.getValue().startingSequenceNumber(), is("12345"));

    }

    @Test
    public void itUsesTheShardIteratorOnPolls() throws Exception {
        underTest.poll();

        final ArgumentCaptor<GetRecordsRequest> getRecordsReqCap = ArgumentCaptor.forClass(GetRecordsRequest.class);
        verify(kinesisClient).getRecords(getRecordsReqCap.capture());

        assertThat(getRecordsReqCap.getValue().shardIterator(), is("shardIterator"));
    }

    @Test
    public void itUsesTheShardIteratorOnSubsiquentPolls() throws Exception {
        underTest.poll();
        underTest.poll();

        final ArgumentCaptor<GetRecordsRequest> getRecordsReqCap = ArgumentCaptor.forClass(GetRecordsRequest.class);

        verify(kinesisClient, times(2)).getShardIterator(any(GetShardIteratorRequest.class));
        verify(kinesisClient, times(2)).getRecords(getRecordsReqCap.capture());
        assertThat(getRecordsReqCap.getAllValues().get(0).shardIterator(), is("shardIterator"));
        assertThat(getRecordsReqCap.getAllValues().get(1).shardIterator(), is("shardIterator"));
    }
}
