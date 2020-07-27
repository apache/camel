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
package org.apache.camel.component.aws.kinesis;

import java.util.ArrayList;
import java.util.Date;

import com.amazonaws.services.kinesis.AmazonKinesis;
import com.amazonaws.services.kinesis.model.DescribeStreamRequest;
import com.amazonaws.services.kinesis.model.DescribeStreamResult;
import com.amazonaws.services.kinesis.model.GetRecordsRequest;
import com.amazonaws.services.kinesis.model.GetRecordsResult;
import com.amazonaws.services.kinesis.model.GetShardIteratorRequest;
import com.amazonaws.services.kinesis.model.GetShardIteratorResult;
import com.amazonaws.services.kinesis.model.Record;
import com.amazonaws.services.kinesis.model.SequenceNumberRange;
import com.amazonaws.services.kinesis.model.Shard;
import com.amazonaws.services.kinesis.model.ShardIteratorType;
import com.amazonaws.services.kinesis.model.StreamDescription;
import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class KinesisConsumerClosedShardWithSilentTest {

    @Mock
    private AmazonKinesis kinesisClient;
    @Mock
    private AsyncProcessor processor;

    private final CamelContext context = new DefaultCamelContext();
    private final KinesisComponent component = new KinesisComponent(context);

    private KinesisConsumer undertest;

    @BeforeEach
    public void setup() throws Exception {
        KinesisConfiguration configuration = new KinesisConfiguration();
        configuration.setAmazonKinesisClient(kinesisClient);
        configuration.setIteratorType(ShardIteratorType.LATEST);
        configuration.setShardClosed(KinesisShardClosedStrategyEnum.silent);
        configuration.setStreamName("streamName");
        KinesisEndpoint endpoint = new KinesisEndpoint(null, configuration, component);
        endpoint.start();
        undertest = new KinesisConsumer(endpoint, processor);

        SequenceNumberRange range = new SequenceNumberRange().withEndingSequenceNumber("20");
        Shard shard = new Shard().withShardId("shardId").withSequenceNumberRange(range);
        ArrayList<Shard> shardList = new ArrayList<>();
        shardList.add(shard);

        lenient().when(kinesisClient.getRecords(any(GetRecordsRequest.class)))
                .thenReturn(new GetRecordsResult()
                        .withNextShardIterator("nextShardIterator"));
        lenient().when(kinesisClient.describeStream(any(DescribeStreamRequest.class)))
                .thenReturn(new DescribeStreamResult()
                        .withStreamDescription(new StreamDescription()
                                .withShards(shardList)));
        lenient().when(kinesisClient.getShardIterator(any(GetShardIteratorRequest.class)))
                .thenReturn(new GetShardIteratorResult()
                        .withShardIterator("shardIterator"));
    }

    @Test
    public void itObtainsAShardIteratorOnFirstPoll() throws Exception {
        undertest.poll();

        final ArgumentCaptor<DescribeStreamRequest> describeStreamReqCap = ArgumentCaptor.forClass(DescribeStreamRequest.class);
        final ArgumentCaptor<GetShardIteratorRequest> getShardIteratorReqCap
                = ArgumentCaptor.forClass(GetShardIteratorRequest.class);

        verify(kinesisClient).describeStream(describeStreamReqCap.capture());
        assertEquals("streamName", describeStreamReqCap.getValue().getStreamName());

        verify(kinesisClient).getShardIterator(getShardIteratorReqCap.capture());
        assertEquals("streamName", getShardIteratorReqCap.getValue().getStreamName());
        assertEquals("shardId", getShardIteratorReqCap.getValue().getShardId());
        assertEquals("LATEST", getShardIteratorReqCap.getValue().getShardIteratorType());
    }

    @Test
    public void itDoesNotMakeADescribeStreamRequestIfShardIdIsSet() throws Exception {
        undertest.getEndpoint().getConfiguration().setShardId("shardIdPassedAsUrlParam");

        undertest.poll();

        final ArgumentCaptor<GetShardIteratorRequest> getShardIteratorReqCap
                = ArgumentCaptor.forClass(GetShardIteratorRequest.class);

        verify(kinesisClient).getShardIterator(getShardIteratorReqCap.capture());
        assertEquals("streamName", getShardIteratorReqCap.getValue().getStreamName());
        assertEquals("shardIdPassedAsUrlParam", getShardIteratorReqCap.getValue().getShardId());
        assertEquals("LATEST", getShardIteratorReqCap.getValue().getShardIteratorType());
    }

    @Test
    public void itObtainsAShardIteratorOnFirstPollForSequenceNumber() throws Exception {
        undertest.getEndpoint().getConfiguration().setSequenceNumber("12345");
        undertest.getEndpoint().getConfiguration().setIteratorType(ShardIteratorType.AFTER_SEQUENCE_NUMBER);

        undertest.poll();

        final ArgumentCaptor<DescribeStreamRequest> describeStreamReqCap = ArgumentCaptor.forClass(DescribeStreamRequest.class);
        final ArgumentCaptor<GetShardIteratorRequest> getShardIteratorReqCap
                = ArgumentCaptor.forClass(GetShardIteratorRequest.class);

        verify(kinesisClient).describeStream(describeStreamReqCap.capture());
        assertEquals("streamName", describeStreamReqCap.getValue().getStreamName());

        verify(kinesisClient).getShardIterator(getShardIteratorReqCap.capture());
        assertEquals("streamName", getShardIteratorReqCap.getValue().getStreamName());
        assertEquals("shardId", getShardIteratorReqCap.getValue().getShardId());
        assertEquals("AFTER_SEQUENCE_NUMBER", getShardIteratorReqCap.getValue().getShardIteratorType());
        assertEquals("12345", getShardIteratorReqCap.getValue().getStartingSequenceNumber());

    }

    @Test
    public void itUsesTheShardIteratorOnPolls() throws Exception {
        undertest.poll();

        final ArgumentCaptor<GetRecordsRequest> getRecordsReqCap = ArgumentCaptor.forClass(GetRecordsRequest.class);
        verify(kinesisClient).getRecords(getRecordsReqCap.capture());

        assertEquals("shardIterator", getRecordsReqCap.getValue().getShardIterator());
    }

    @Test
    public void itUsesTheShardIteratorOnSubsiquentPolls() throws Exception {
        undertest.poll();
        undertest.poll();

        final ArgumentCaptor<GetRecordsRequest> getRecordsReqCap = ArgumentCaptor.forClass(GetRecordsRequest.class);

        verify(kinesisClient, times(1)).describeStream(any(DescribeStreamRequest.class));
        verify(kinesisClient, times(1)).getShardIterator(any(GetShardIteratorRequest.class));
        verify(kinesisClient, times(2)).getRecords(getRecordsReqCap.capture());
        assertEquals("shardIterator", getRecordsReqCap.getAllValues().get(0).getShardIterator());
        assertEquals("nextShardIterator", getRecordsReqCap.getAllValues().get(1).getShardIterator());
    }

    @Test
    public void recordsAreSentToTheProcessor() throws Exception {
        when(kinesisClient.getRecords(any(GetRecordsRequest.class)))
                .thenReturn(new GetRecordsResult()
                        .withNextShardIterator("nextShardIterator")
                        .withRecords(new Record().withSequenceNumber("1"), new Record().withSequenceNumber("2")));

        int messageCount = undertest.poll();

        assertEquals(2, messageCount);
        final ArgumentCaptor<Exchange> exchangeCaptor = ArgumentCaptor.forClass(Exchange.class);

        verify(processor, times(2)).process(exchangeCaptor.capture(), any(AsyncCallback.class));
        assertEquals("1", exchangeCaptor.getAllValues().get(0).getIn().getBody(Record.class).getSequenceNumber());
        assertEquals("2", exchangeCaptor.getAllValues().get(1).getIn().getBody(Record.class).getSequenceNumber());
    }

    @Test
    public void exchangePropertiesAreSet() throws Exception {
        String partitionKey = "partitionKey";
        String sequenceNumber = "1";
        when(kinesisClient.getRecords(any(GetRecordsRequest.class)))
                .thenReturn(new GetRecordsResult()
                        .withNextShardIterator("nextShardIterator")
                        .withRecords(new Record()
                                .withSequenceNumber(sequenceNumber)
                                .withApproximateArrivalTimestamp(new Date(42))
                                .withPartitionKey(partitionKey)));

        undertest.poll();

        final ArgumentCaptor<Exchange> exchangeCaptor = ArgumentCaptor.forClass(Exchange.class);

        verify(processor).process(exchangeCaptor.capture(), any(AsyncCallback.class));
        assertEquals(42L, exchangeCaptor.getValue().getIn().getHeader(KinesisConstants.APPROX_ARRIVAL_TIME, long.class));
        assertEquals(partitionKey, exchangeCaptor.getValue().getIn().getHeader(KinesisConstants.PARTITION_KEY, String.class));
        assertEquals(sequenceNumber,
                exchangeCaptor.getValue().getIn().getHeader(KinesisConstants.SEQUENCE_NUMBER, String.class));
    }
}
