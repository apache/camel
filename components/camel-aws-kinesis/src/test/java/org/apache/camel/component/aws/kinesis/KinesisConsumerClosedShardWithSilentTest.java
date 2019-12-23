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
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class KinesisConsumerClosedShardWithSilentTest {

    @Mock
    private AmazonKinesis kinesisClient;
    @Mock
    private AsyncProcessor processor;

    private final CamelContext context = new DefaultCamelContext();
    private final KinesisComponent component = new KinesisComponent(context);

    private KinesisConsumer undertest;

    @Before
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
       

        when(kinesisClient.getRecords(any(GetRecordsRequest.class)))
            .thenReturn(new GetRecordsResult()
                .withNextShardIterator("nextShardIterator")
            );
        when(kinesisClient.describeStream(any(DescribeStreamRequest.class)))
            .thenReturn(new DescribeStreamResult()
                .withStreamDescription(new StreamDescription()
                    .withShards(shardList)
                )
            );
        when(kinesisClient.getShardIterator(any(GetShardIteratorRequest.class)))
            .thenReturn(new GetShardIteratorResult()
                .withShardIterator("shardIterator")
            );
    }

    @Test
    public void itObtainsAShardIteratorOnFirstPoll() throws Exception {
        undertest.poll();

        final ArgumentCaptor<DescribeStreamRequest> describeStreamReqCap = ArgumentCaptor.forClass(DescribeStreamRequest.class);
        final ArgumentCaptor<GetShardIteratorRequest> getShardIteratorReqCap = ArgumentCaptor.forClass(GetShardIteratorRequest.class);

        verify(kinesisClient).describeStream(describeStreamReqCap.capture());
        assertThat(describeStreamReqCap.getValue().getStreamName(), is("streamName"));

        verify(kinesisClient).getShardIterator(getShardIteratorReqCap.capture());
        assertThat(getShardIteratorReqCap.getValue().getStreamName(), is("streamName"));
        assertThat(getShardIteratorReqCap.getValue().getShardId(), is("shardId"));
        assertThat(getShardIteratorReqCap.getValue().getShardIteratorType(), is("LATEST"));
    }

    @Test
    public void itDoesNotMakeADescribeStreamRequestIfShardIdIsSet() throws Exception {
        undertest.getEndpoint().getConfiguration().setShardId("shardIdPassedAsUrlParam");

        undertest.poll();

        final ArgumentCaptor<GetShardIteratorRequest> getShardIteratorReqCap = ArgumentCaptor.forClass(GetShardIteratorRequest.class);

        verify(kinesisClient).getShardIterator(getShardIteratorReqCap.capture());
        assertThat(getShardIteratorReqCap.getValue().getStreamName(), is("streamName"));
        assertThat(getShardIteratorReqCap.getValue().getShardId(), is("shardIdPassedAsUrlParam"));
        assertThat(getShardIteratorReqCap.getValue().getShardIteratorType(), is("LATEST"));
    }

    @Test
    public void itObtainsAShardIteratorOnFirstPollForSequenceNumber() throws Exception {
        undertest.getEndpoint().getConfiguration().setSequenceNumber("12345");
        undertest.getEndpoint().getConfiguration().setIteratorType(ShardIteratorType.AFTER_SEQUENCE_NUMBER);

        undertest.poll();

        final ArgumentCaptor<DescribeStreamRequest> describeStreamReqCap = ArgumentCaptor.forClass(DescribeStreamRequest.class);
        final ArgumentCaptor<GetShardIteratorRequest> getShardIteratorReqCap = ArgumentCaptor.forClass(GetShardIteratorRequest.class);

        verify(kinesisClient).describeStream(describeStreamReqCap.capture());
        assertThat(describeStreamReqCap.getValue().getStreamName(), is("streamName"));

        verify(kinesisClient).getShardIterator(getShardIteratorReqCap.capture());
        assertThat(getShardIteratorReqCap.getValue().getStreamName(), is("streamName"));
        assertThat(getShardIteratorReqCap.getValue().getShardId(), is("shardId"));
        assertThat(getShardIteratorReqCap.getValue().getShardIteratorType(), is("AFTER_SEQUENCE_NUMBER"));
        assertThat(getShardIteratorReqCap.getValue().getStartingSequenceNumber(), is("12345"));

    }

    @Test
    public void itUsesTheShardIteratorOnPolls() throws Exception {
        undertest.poll();

        final ArgumentCaptor<GetRecordsRequest> getRecordsReqCap = ArgumentCaptor.forClass(GetRecordsRequest.class);
        verify(kinesisClient).getRecords(getRecordsReqCap.capture());

        assertThat(getRecordsReqCap.getValue().getShardIterator(), is("shardIterator"));
    }

    @Test
    public void itUsesTheShardIteratorOnSubsiquentPolls() throws Exception {
        undertest.poll();
        undertest.poll();

        final ArgumentCaptor<GetRecordsRequest> getRecordsReqCap = ArgumentCaptor.forClass(GetRecordsRequest.class);

        verify(kinesisClient, times(1)).describeStream(any(DescribeStreamRequest.class));
        verify(kinesisClient, times(1)).getShardIterator(any(GetShardIteratorRequest.class));
        verify(kinesisClient, times(2)).getRecords(getRecordsReqCap.capture());
        assertThat(getRecordsReqCap.getAllValues().get(0).getShardIterator(), is("shardIterator"));
        assertThat(getRecordsReqCap.getAllValues().get(1).getShardIterator(), is("nextShardIterator"));
    }

    @Test
    public void recordsAreSentToTheProcessor() throws Exception {
        when(kinesisClient.getRecords(any(GetRecordsRequest.class)))
            .thenReturn(new GetRecordsResult()
                .withNextShardIterator("nextShardIterator")
                .withRecords(new Record().withSequenceNumber("1"), new Record().withSequenceNumber("2"))
            );

        int messageCount = undertest.poll();

        assertThat(messageCount, is(2));
        final ArgumentCaptor<Exchange> exchangeCaptor = ArgumentCaptor.forClass(Exchange.class);

        verify(processor, times(2)).process(exchangeCaptor.capture(), any(AsyncCallback.class));
        assertThat(exchangeCaptor.getAllValues().get(0).getIn().getBody(Record.class).getSequenceNumber(), is("1"));
        assertThat(exchangeCaptor.getAllValues().get(1).getIn().getBody(Record.class).getSequenceNumber(), is("2"));
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
                    .withPartitionKey(partitionKey)
                )
            );

        undertest.poll();

        final ArgumentCaptor<Exchange> exchangeCaptor = ArgumentCaptor.forClass(Exchange.class);

        verify(processor).process(exchangeCaptor.capture(), any(AsyncCallback.class));
        assertThat(exchangeCaptor.getValue().getIn().getHeader(KinesisConstants.APPROX_ARRIVAL_TIME, long.class), is(42L));
        assertThat(exchangeCaptor.getValue().getIn().getHeader(KinesisConstants.PARTITION_KEY, String.class), is(partitionKey));
        assertThat(exchangeCaptor.getValue().getIn().getHeader(KinesisConstants.SEQUENCE_NUMBER, String.class), is(sequenceNumber));
    }
}
