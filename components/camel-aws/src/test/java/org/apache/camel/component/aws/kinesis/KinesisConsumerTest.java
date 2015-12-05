/**
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

import com.amazonaws.services.kinesis.AmazonKinesis;
import com.amazonaws.services.kinesis.model.DescribeStreamRequest;
import com.amazonaws.services.kinesis.model.DescribeStreamResult;
import com.amazonaws.services.kinesis.model.GetRecordsRequest;
import com.amazonaws.services.kinesis.model.GetRecordsResult;
import com.amazonaws.services.kinesis.model.GetShardIteratorRequest;
import com.amazonaws.services.kinesis.model.GetShardIteratorResult;
import com.amazonaws.services.kinesis.model.Record;
import com.amazonaws.services.kinesis.model.Shard;
import com.amazonaws.services.kinesis.model.ShardIteratorType;
import com.amazonaws.services.kinesis.model.StreamDescription;
import java.util.Date;
import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import static org.hamcrest.CoreMatchers.is;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import static org.mockito.Mockito.*;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class KinesisConsumerTest {

    @Mock private AmazonKinesis kinesisClient;
    @Mock private AsyncProcessor processor;

    private final CamelContext context = new DefaultCamelContext();
    private final KinesisComponent component = new KinesisComponent(context);

    private KinesisConsumer undertest;

    @Before
    public void setup() throws Exception {
        KinesisEndpoint endpoint = new KinesisEndpoint(null, "streamName", component);
        endpoint.setAmazonKinesisClient(kinesisClient);
        endpoint.setIteratorType(ShardIteratorType.LATEST);
        undertest = new KinesisConsumer(endpoint, processor);

        when(kinesisClient.getRecords(any(GetRecordsRequest.class)))
                .thenReturn(new GetRecordsResult()
                        .withNextShardIterator("nextShardIterator")
                );
        when(kinesisClient.describeStream(any(DescribeStreamRequest.class)))
                .thenReturn(new DescribeStreamResult()
                        .withStreamDescription(new StreamDescription()
                                .withShards(new Shard().withShardId("shardId"))
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

        when(kinesisClient.getRecords(any(GetRecordsRequest.class)))
                .thenReturn(new GetRecordsResult()
                        .withNextShardIterator("nextShardIterator")
                        .withRecords(new Record()
                                .withSequenceNumber("1")
                                .withApproximateArrivalTimestamp(new Date(42))
                                .withPartitionKey("shardId")
                        )
                );

        undertest.poll();

        final ArgumentCaptor<Exchange> exchangeCaptor = ArgumentCaptor.forClass(Exchange.class);

        verify(processor).process(exchangeCaptor.capture(), any(AsyncCallback.class));
        assertThat(exchangeCaptor.getValue().getProperty(KinesisConstants.APPROX_ARRIVAL_TIME, long.class), is(42L));
        assertThat(exchangeCaptor.getValue().getProperty(KinesisConstants.PARTITION_KEY, String.class), is("shardId"));
        assertThat(exchangeCaptor.getValue().getProperty(KinesisConstants.SEQUENCE_NUMBER, String.class), is("1"));
        assertThat(exchangeCaptor.getValue().getProperty(KinesisConstants.SHARD_ID, String.class), is("shardId"));
    }
}