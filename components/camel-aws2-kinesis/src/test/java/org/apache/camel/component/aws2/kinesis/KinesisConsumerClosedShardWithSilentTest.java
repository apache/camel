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

import java.time.Instant;
import java.util.ArrayList;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.DescribeStreamRequest;
import software.amazon.awssdk.services.kinesis.model.DescribeStreamResponse;
import software.amazon.awssdk.services.kinesis.model.GetRecordsRequest;
import software.amazon.awssdk.services.kinesis.model.GetRecordsResponse;
import software.amazon.awssdk.services.kinesis.model.GetShardIteratorRequest;
import software.amazon.awssdk.services.kinesis.model.GetShardIteratorResponse;
import software.amazon.awssdk.services.kinesis.model.Record;
import software.amazon.awssdk.services.kinesis.model.SequenceNumberRange;
import software.amazon.awssdk.services.kinesis.model.Shard;
import software.amazon.awssdk.services.kinesis.model.ShardIteratorType;
import software.amazon.awssdk.services.kinesis.model.StreamDescription;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class KinesisConsumerClosedShardWithSilentTest {

    @Mock
    private KinesisClient kinesisClient;
    @Mock
    private AsyncProcessor processor;

    private final CamelContext context = new DefaultCamelContext();
    private final Kinesis2Component component = new Kinesis2Component(context);

    private Kinesis2Consumer undertest;

    @Before
    public void setup() throws Exception {
        Kinesis2Configuration configuration = new Kinesis2Configuration();
        configuration.setAmazonKinesisClient(kinesisClient);
        configuration.setIteratorType(ShardIteratorType.LATEST);
        configuration.setShardClosed(Kinesis2ShardClosedStrategyEnum.silent);
        configuration.setStreamName("streamName");
        Kinesis2Endpoint endpoint = new Kinesis2Endpoint(null, configuration, component);
        endpoint.start();
        undertest = new Kinesis2Consumer(endpoint, processor);

        SequenceNumberRange range = SequenceNumberRange.builder().endingSequenceNumber("20").build();
        Shard shard = Shard.builder().shardId("shardId").sequenceNumberRange(range).build();
        ArrayList<Shard> shardList = new ArrayList<>();
        shardList.add(shard);

        when(kinesisClient.getRecords(any(GetRecordsRequest.class))).thenReturn(GetRecordsResponse.builder().nextShardIterator("nextShardIterator").build());
        when(kinesisClient.describeStream(any(DescribeStreamRequest.class)))
            .thenReturn(DescribeStreamResponse.builder().streamDescription(StreamDescription.builder().shards(shardList).build()).build());
        when(kinesisClient.getShardIterator(any(GetShardIteratorRequest.class))).thenReturn(GetShardIteratorResponse.builder().shardIterator("shardIterator").build());
    }

    @Test
    public void itObtainsAShardIteratorOnFirstPoll() throws Exception {
        undertest.poll();

        final ArgumentCaptor<DescribeStreamRequest> describeStreamReqCap = ArgumentCaptor.forClass(DescribeStreamRequest.class);
        final ArgumentCaptor<GetShardIteratorRequest> getShardIteratorReqCap = ArgumentCaptor.forClass(GetShardIteratorRequest.class);

        verify(kinesisClient).describeStream(describeStreamReqCap.capture());
        assertThat(describeStreamReqCap.getValue().streamName(), is("streamName"));

        verify(kinesisClient).getShardIterator(getShardIteratorReqCap.capture());
        assertThat(getShardIteratorReqCap.getValue().streamName(), is("streamName"));
        assertThat(getShardIteratorReqCap.getValue().shardId(), is("shardId"));
        assertThat(getShardIteratorReqCap.getValue().shardIteratorType(), is(ShardIteratorType.LATEST));
    }

    @Test
    public void itDoesNotMakeADescribeStreamRequestIfShardIdIsSet() throws Exception {
        undertest.getEndpoint().getConfiguration().setShardId("shardIdPassedAsUrlParam");

        undertest.poll();

        final ArgumentCaptor<GetShardIteratorRequest> getShardIteratorReqCap = ArgumentCaptor.forClass(GetShardIteratorRequest.class);

        verify(kinesisClient).getShardIterator(getShardIteratorReqCap.capture());
        assertThat(getShardIteratorReqCap.getValue().streamName(), is("streamName"));
        assertThat(getShardIteratorReqCap.getValue().shardId(), is("shardIdPassedAsUrlParam"));
        assertThat(getShardIteratorReqCap.getValue().shardIteratorType(), is(ShardIteratorType.LATEST));
    }

    @Test
    public void itObtainsAShardIteratorOnFirstPollForSequenceNumber() throws Exception {
        undertest.getEndpoint().getConfiguration().setSequenceNumber("12345");
        undertest.getEndpoint().getConfiguration().setIteratorType(ShardIteratorType.AFTER_SEQUENCE_NUMBER);

        undertest.poll();

        final ArgumentCaptor<DescribeStreamRequest> describeStreamReqCap = ArgumentCaptor.forClass(DescribeStreamRequest.class);
        final ArgumentCaptor<GetShardIteratorRequest> getShardIteratorReqCap = ArgumentCaptor.forClass(GetShardIteratorRequest.class);

        verify(kinesisClient).describeStream(describeStreamReqCap.capture());
        assertThat(describeStreamReqCap.getValue().streamName(), is("streamName"));

        verify(kinesisClient).getShardIterator(getShardIteratorReqCap.capture());
        assertThat(getShardIteratorReqCap.getValue().streamName(), is("streamName"));
        assertThat(getShardIteratorReqCap.getValue().shardId(), is("shardId"));
        assertThat(getShardIteratorReqCap.getValue().shardIteratorType(), is(ShardIteratorType.AFTER_SEQUENCE_NUMBER));
        assertThat(getShardIteratorReqCap.getValue().startingSequenceNumber(), is("12345"));

    }

    @Test
    public void itUsesTheShardIteratorOnPolls() throws Exception {
        undertest.poll();

        final ArgumentCaptor<GetRecordsRequest> getRecordsReqCap = ArgumentCaptor.forClass(GetRecordsRequest.class);
        verify(kinesisClient).getRecords(getRecordsReqCap.capture());

        assertThat(getRecordsReqCap.getValue().shardIterator(), is("shardIterator"));
    }

    @Test
    public void itUsesTheShardIteratorOnSubsiquentPolls() throws Exception {
        undertest.poll();
        undertest.poll();

        final ArgumentCaptor<GetRecordsRequest> getRecordsReqCap = ArgumentCaptor.forClass(GetRecordsRequest.class);

        verify(kinesisClient, times(1)).describeStream(any(DescribeStreamRequest.class));
        verify(kinesisClient, times(1)).getShardIterator(any(GetShardIteratorRequest.class));
        verify(kinesisClient, times(2)).getRecords(getRecordsReqCap.capture());
        assertThat(getRecordsReqCap.getAllValues().get(0).shardIterator(), is("shardIterator"));
        assertThat(getRecordsReqCap.getAllValues().get(1).shardIterator(), is("nextShardIterator"));
    }

    @Test
    public void recordsAreSentToTheProcessor() throws Exception {
        when(kinesisClient.getRecords(any(GetRecordsRequest.class))).thenReturn(GetRecordsResponse.builder().nextShardIterator("nextShardIterator")
            .records(Record.builder().sequenceNumber("1").build(), Record.builder().sequenceNumber("2").build()).build());

        int messageCount = undertest.poll();

        assertThat(messageCount, is(2));
        final ArgumentCaptor<Exchange> exchangeCaptor = ArgumentCaptor.forClass(Exchange.class);

        verify(processor, times(2)).process(exchangeCaptor.capture(), any(AsyncCallback.class));
        assertThat(exchangeCaptor.getAllValues().get(0).getIn().getBody(Record.class).sequenceNumber(), is("1"));
        assertThat(exchangeCaptor.getAllValues().get(1).getIn().getBody(Record.class).sequenceNumber(), is("2"));
    }

    @Test
    public void exchangePropertiesAreSet() throws Exception {
        String partitionKey = "partitionKey";
        String sequenceNumber = "1";
        when(kinesisClient.getRecords(any(GetRecordsRequest.class))).thenReturn(GetRecordsResponse.builder().nextShardIterator("nextShardIterator")
            .records(Record.builder().sequenceNumber(sequenceNumber).approximateArrivalTimestamp(Instant.now()).partitionKey(partitionKey).build()).build());

        undertest.poll();

        final ArgumentCaptor<Exchange> exchangeCaptor = ArgumentCaptor.forClass(Exchange.class);

        verify(processor).process(exchangeCaptor.capture(), any(AsyncCallback.class));
        assertThat(exchangeCaptor.getValue().getIn().getHeader(Kinesis2Constants.PARTITION_KEY, String.class), is(partitionKey));
        assertThat(exchangeCaptor.getValue().getIn().getHeader(Kinesis2Constants.SEQUENCE_NUMBER, String.class), is(sequenceNumber));
    }
}
