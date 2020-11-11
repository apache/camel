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

import com.amazonaws.services.kinesis.AmazonKinesis;
import com.amazonaws.services.kinesis.model.DescribeStreamRequest;
import com.amazonaws.services.kinesis.model.DescribeStreamResult;
import com.amazonaws.services.kinesis.model.GetRecordsRequest;
import com.amazonaws.services.kinesis.model.GetRecordsResult;
import com.amazonaws.services.kinesis.model.GetShardIteratorRequest;
import com.amazonaws.services.kinesis.model.GetShardIteratorResult;
import com.amazonaws.services.kinesis.model.SequenceNumberRange;
import com.amazonaws.services.kinesis.model.Shard;
import com.amazonaws.services.kinesis.model.ShardIteratorType;
import com.amazonaws.services.kinesis.model.StreamDescription;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class KinesisConsumerClosedShardWithFailTest {

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
        configuration.setShardClosed(KinesisShardClosedStrategyEnum.fail);
        configuration.setStreamName("streamName");
        KinesisEndpoint endpoint = new KinesisEndpoint(null, configuration, component);
        endpoint.start();
        undertest = new KinesisConsumer(endpoint, processor);

        SequenceNumberRange range = new SequenceNumberRange().withEndingSequenceNumber("20");
        Shard shard = new Shard().withShardId("shardId").withSequenceNumberRange(range);
        ArrayList<Shard> shardList = new ArrayList<>();
        shardList.add(shard);

        when(kinesisClient.getRecords(any(GetRecordsRequest.class)))
                .thenReturn(new GetRecordsResult().withNextShardIterator("nextShardIterator"));
        when(kinesisClient.describeStream(any(DescribeStreamRequest.class)))
                .thenReturn(new DescribeStreamResult().withStreamDescription(new StreamDescription().withShards(shardList)));
        when(kinesisClient.getShardIterator(any(GetShardIteratorRequest.class)))
                .thenReturn(new GetShardIteratorResult().withShardIterator("shardIterator"));
    }

    @Test
    public void itObtainsAShardIteratorOnFirstPoll() throws Exception {
        try {
            undertest.poll();

            final ArgumentCaptor<DescribeStreamRequest> describeStreamReqCap
                    = ArgumentCaptor.forClass(DescribeStreamRequest.class);
            final ArgumentCaptor<GetShardIteratorRequest> getShardIteratorReqCap
                    = ArgumentCaptor.forClass(GetShardIteratorRequest.class);

            verify(kinesisClient).describeStream(describeStreamReqCap.capture());
            assertEquals("streamName", describeStreamReqCap.getValue().getStreamName());

            verify(kinesisClient).getShardIterator(getShardIteratorReqCap.capture());
            assertEquals("streamName", getShardIteratorReqCap.getValue().getStreamName());
            assertEquals("shardId", getShardIteratorReqCap.getValue().getShardId());
            assertEquals("LATEST", getShardIteratorReqCap.getValue().getShardIteratorType());

            fail("Expected ReachedClosedStatusException");
        } catch (ReachedClosedStatusException e) {
            // expected
        }
    }
}
