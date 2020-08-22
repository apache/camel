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
package org.apache.camel.component.aws.ddbstream;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBStreams;
import com.amazonaws.services.dynamodbv2.model.DescribeStreamRequest;
import com.amazonaws.services.dynamodbv2.model.DescribeStreamResult;
import com.amazonaws.services.dynamodbv2.model.GetShardIteratorRequest;
import com.amazonaws.services.dynamodbv2.model.GetShardIteratorResult;
import com.amazonaws.services.dynamodbv2.model.ListStreamsRequest;
import com.amazonaws.services.dynamodbv2.model.ListStreamsResult;
import com.amazonaws.services.dynamodbv2.model.ShardIteratorType;
import com.amazonaws.services.dynamodbv2.model.Stream;
import com.amazonaws.services.dynamodbv2.model.StreamDescription;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ShardIteratorHandlerTest {

    private ShardIteratorHandler undertest;

    @Mock
    private AmazonDynamoDBStreams amazonDynamoDBStreams;
    private final CamelContext context = new DefaultCamelContext();
    private final DdbStreamComponent component = new DdbStreamComponent(context);
    private final DdbStreamEndpoint endpoint = new DdbStreamEndpoint(null, new DdbStreamConfiguration(), component);

    @BeforeEach
    public void setup() throws Exception {
        endpoint.getConfiguration().setAmazonDynamoDbStreamsClient(amazonDynamoDBStreams);
        endpoint.start();
        undertest = new ShardIteratorHandler(endpoint);

        lenient().when(amazonDynamoDBStreams.listStreams(any(ListStreamsRequest.class))).thenReturn(
                new ListStreamsResult()
                        .withStreams(new Stream()
                                .withStreamArn("arn:aws:dynamodb:region:12345:table/table_name/stream/timestamp")));

        lenient().when(amazonDynamoDBStreams.describeStream(any(DescribeStreamRequest.class))).thenReturn(
                new DescribeStreamResult()
                        .withStreamDescription(
                                new StreamDescription()
                                        .withTableName("table_name")
                                        .withShards(
                                                ShardListTest.createShardsWithSequenceNumbers(null,
                                                        "a", "1", "5",
                                                        "b", "8", "15",
                                                        "c", "16", "16",
                                                        "d", "20", null))));

        lenient().when(amazonDynamoDBStreams.getShardIterator(any(GetShardIteratorRequest.class)))
                .thenAnswer(new Answer<GetShardIteratorResult>() {
                    @Override
                    public GetShardIteratorResult answer(InvocationOnMock invocation) throws Throwable {
                        return new GetShardIteratorResult()
                                .withShardIterator("shard_iterator_"
                                                   + ((GetShardIteratorRequest) invocation.getArguments()[0]).getShardId()
                                                   + "_000");
                    }
                });
    }

    @Test
    public void latestOnlyUsesTheLastShard() throws Exception {
        endpoint.getConfiguration().setIteratorType(ShardIteratorType.LATEST);

        String shardIterator = undertest.getShardIterator(null);

        ArgumentCaptor<GetShardIteratorRequest> getIteratorCaptor = ArgumentCaptor.forClass(GetShardIteratorRequest.class);
        verify(amazonDynamoDBStreams).getShardIterator(getIteratorCaptor.capture());
        assertEquals("d", getIteratorCaptor.getValue().getShardId());
        assertEquals("shard_iterator_d_000", shardIterator);
    }

    @Test
    public void cachesRecentShardId() throws Exception {
        endpoint.getConfiguration().setIteratorType(ShardIteratorType.LATEST);

        undertest.updateShardIterator("bar");
        String shardIterator = undertest.getShardIterator(null);

        verify(amazonDynamoDBStreams, times(0)).getShardIterator(any(GetShardIteratorRequest.class));
        assertEquals("bar", shardIterator);
    }

    @Test
    public void trimHorizonStartsWithTheFirstShard() throws Exception {
        endpoint.getConfiguration().setIteratorType(ShardIteratorType.TRIM_HORIZON);

        String shardIterator = undertest.getShardIterator(null);

        ArgumentCaptor<GetShardIteratorRequest> getIteratorCaptor = ArgumentCaptor.forClass(GetShardIteratorRequest.class);
        verify(amazonDynamoDBStreams).getShardIterator(getIteratorCaptor.capture());
        assertEquals("a", getIteratorCaptor.getValue().getShardId());
        assertEquals("shard_iterator_a_000", shardIterator);
    }

    @Test
    public void trimHorizonWalksAllShards() throws Exception {
        endpoint.getConfiguration().setIteratorType(ShardIteratorType.TRIM_HORIZON);

        String[] shardIterators = new String[4];

        for (int i = 0; i < shardIterators.length; ++i) {
            shardIterators[i] = undertest.getShardIterator(null);
            undertest.updateShardIterator(null);
        }

        ArgumentCaptor<GetShardIteratorRequest> getIteratorCaptor = ArgumentCaptor.forClass(GetShardIteratorRequest.class);
        verify(amazonDynamoDBStreams, times(4)).getShardIterator(getIteratorCaptor.capture());
        String[] shards = new String[] { "a", "b", "c", "d" };
        for (int i = 0; i < shards.length; ++i) {
            assertEquals(shards[i], getIteratorCaptor.getAllValues().get(i).getShardId());
        }
        assertArrayEquals(
                new String[] { "shard_iterator_a_000", "shard_iterator_b_000", "shard_iterator_c_000", "shard_iterator_d_000" },
                shardIterators);

    }

    @Test
    public void atSeqNumber12StartsWithShardB() throws Exception {
        endpoint.getConfiguration().setIteratorType(ShardIteratorType.AT_SEQUENCE_NUMBER);
        endpoint.getConfiguration().setSequenceNumberProvider(new StaticSequenceNumberProvider("12"));

        String shardIterator = undertest.getShardIterator(null);

        ArgumentCaptor<GetShardIteratorRequest> getIteratorCaptor = ArgumentCaptor.forClass(GetShardIteratorRequest.class);
        verify(amazonDynamoDBStreams).getShardIterator(getIteratorCaptor.capture());
        assertEquals("b", getIteratorCaptor.getValue().getShardId());
        assertEquals("shard_iterator_b_000", shardIterator);
    }

    @Test
    public void afterSeqNumber16StartsWithShardD() throws Exception {
        endpoint.getConfiguration().setIteratorType(ShardIteratorType.AFTER_SEQUENCE_NUMBER);
        endpoint.getConfiguration().setSequenceNumberProvider(new StaticSequenceNumberProvider("16"));

        String shardIterator = undertest.getShardIterator(null);

        ArgumentCaptor<GetShardIteratorRequest> getIteratorCaptor = ArgumentCaptor.forClass(GetShardIteratorRequest.class);
        verify(amazonDynamoDBStreams).getShardIterator(getIteratorCaptor.capture());
        assertEquals("d", getIteratorCaptor.getValue().getShardId());
        assertEquals("shard_iterator_d_000", shardIterator);
    }

    @Test
    public void resumingFromSomewhereActuallyUsesTheAfterSequenceNumber() throws Exception {
        endpoint.getConfiguration().setIteratorType(ShardIteratorType.LATEST);

        String shardIterator = undertest.getShardIterator("12");

        ArgumentCaptor<GetShardIteratorRequest> getIteratorCaptor = ArgumentCaptor.forClass(GetShardIteratorRequest.class);
        verify(amazonDynamoDBStreams).getShardIterator(getIteratorCaptor.capture());
        assertEquals("b", getIteratorCaptor.getValue().getShardId());
        assertEquals("shard_iterator_b_000", shardIterator);
        assertEquals(ShardIteratorType.AFTER_SEQUENCE_NUMBER.name(), getIteratorCaptor.getValue().getShardIteratorType());
        assertEquals("12", getIteratorCaptor.getValue().getSequenceNumber());
    }
}
