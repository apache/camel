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
package org.apache.camel.component.aws.ddbstream;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBStreams;
import com.amazonaws.services.dynamodbv2.model.DescribeStreamRequest;
import com.amazonaws.services.dynamodbv2.model.DescribeStreamResult;
import com.amazonaws.services.dynamodbv2.model.GetRecordsRequest;
import com.amazonaws.services.dynamodbv2.model.GetRecordsResult;
import com.amazonaws.services.dynamodbv2.model.GetShardIteratorRequest;
import com.amazonaws.services.dynamodbv2.model.GetShardIteratorResult;
import com.amazonaws.services.dynamodbv2.model.ListStreamsRequest;
import com.amazonaws.services.dynamodbv2.model.ListStreamsResult;
import com.amazonaws.services.dynamodbv2.model.Record;
import com.amazonaws.services.dynamodbv2.model.ShardIteratorType;
import com.amazonaws.services.dynamodbv2.model.Stream;
import com.amazonaws.services.dynamodbv2.model.StreamDescription;
import com.amazonaws.services.dynamodbv2.model.StreamRecord;
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
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class DdbStreamConsumerTest {

    private DdbStreamConsumer undertest;

    @Mock private AmazonDynamoDBStreams amazonDynamoDBStreams;
    @Mock private AsyncProcessor processor;
    private final CamelContext context = new DefaultCamelContext();
    private final DdbStreamComponent component = new DdbStreamComponent(context);
    private DdbStreamEndpoint endpoint = new DdbStreamEndpoint(null, "table_name", component);

    private final String[] seqNums = new String[]{"2", "9", "11", "13", "14", "21", "25", "30", "35", "40"};

    @Before
    public void setup() throws Exception {
        endpoint.setAmazonDynamoDbStreamsClient(amazonDynamoDBStreams);

        when(amazonDynamoDBStreams.listStreams(any(ListStreamsRequest.class))).thenReturn(
            new ListStreamsResult()
                .withStreams(new Stream()
                        .withStreamArn("arn:aws:dynamodb:region:12345:table/table_name/stream/timestamp")
                )
        );

        when(amazonDynamoDBStreams.describeStream(any(DescribeStreamRequest.class))).thenReturn(
            new DescribeStreamResult()
                .withStreamDescription(
                        new StreamDescription()
                        .withTableName("table_name")
                        .withShards(
                                ShardListTest.createShardsWithSequenceNumbers(null,
                                        "a", "1", "5",
                                        "b", "8", "15",
                                        "c", "16", "16",
                                        "d", "20", null
                                )
                        )
                )
        );

        when(amazonDynamoDBStreams.getShardIterator(any(GetShardIteratorRequest.class))).thenAnswer(new Answer<GetShardIteratorResult>() {
            @Override
            public GetShardIteratorResult answer(InvocationOnMock invocation) throws Throwable {
                return new GetShardIteratorResult()
                        .withShardIterator("shard_iterator_"
                                + ((GetShardIteratorRequest) invocation.getArguments()[0]).getShardId()
                                + "_000");
            }
        });

        final Map<String, String> shardIterators = new HashMap<>();
        shardIterators.put("shard_iterator_a_000", "shard_iterator_a_001");
        shardIterators.put("shard_iterator_b_000", "shard_iterator_b_001");
        shardIterators.put("shard_iterator_b_001", "shard_iterator_b_002");
        shardIterators.put("shard_iterator_c_000", "shard_iterator_c_001");
        shardIterators.put("shard_iterator_d_000", "shard_iterator_d_001");
        final Map<String, Collection<Record>> answers = new HashMap<>();
        answers.put("shard_iterator_a_001", createRecords("2"));
        answers.put("shard_iterator_b_000", createRecords("9"));
        answers.put("shard_iterator_b_001", createRecords("11", "13"));
        answers.put("shard_iterator_b_002", createRecords("14"));
        answers.put("shard_iterator_d_000", createRecords("21", "25"));
        answers.put("shard_iterator_d_001", createRecords("30", "35", "40"));
        when(amazonDynamoDBStreams.getRecords(any(GetRecordsRequest.class))).thenAnswer(new Answer<GetRecordsResult>() {
            @Override
            public GetRecordsResult answer(InvocationOnMock invocation) throws Throwable {
                final String shardIterator = ((GetRecordsRequest) invocation.getArguments()[0]).getShardIterator();
                // note that HashMap returns null when there is no entry in the map.
                // A null 'nextShardIterator' indicates that the shard has finished
                // and we should move onto the next shard.
                String nextShardIterator = shardIterators.get(shardIterator);
                Matcher m = Pattern.compile("shard_iterator_d_0*(\\d+)").matcher(shardIterator);
                Collection<Record> ans = answers.get(shardIterator);
                if (nextShardIterator == null && m.matches()) { // last shard iterates forever.
                    Integer num = Integer.parseInt(m.group(1));
                    nextShardIterator = "shard_iterator_d_" + pad(Integer.toString(num + 1), 3);
                }
                if (null == ans) { // default to an empty list of records.
                    ans = createRecords();
                }
                return new GetRecordsResult()
                        .withRecords(ans)
                        .withNextShardIterator(nextShardIterator);
            }
        });
    }

    String pad(String num, int to) {
        // lazy padding
        switch (num.length()) {
        case 1:
            return "00" + num;
        case 2:
            return "0" + num;
        default:
            return num;
        }
    }

    @Test
    public void latestOnlyUsesTheLastShard() throws Exception {
        endpoint.setIteratorType(ShardIteratorType.LATEST);
        undertest = new DdbStreamConsumer(endpoint, processor);

        undertest.poll();

        ArgumentCaptor<GetShardIteratorRequest> getIteratorCaptor = ArgumentCaptor.forClass(GetShardIteratorRequest.class);
        verify(amazonDynamoDBStreams).getShardIterator(getIteratorCaptor.capture());
        assertThat(getIteratorCaptor.getValue().getShardId(), is("d"));
    }

    @Test
    public void latestWithTwoPolls() throws Exception {
        endpoint.setIteratorType(ShardIteratorType.LATEST);
        undertest = new DdbStreamConsumer(endpoint, processor);

        undertest.poll();
        undertest.poll();

        ArgumentCaptor<GetRecordsRequest> getRecordsCaptor = ArgumentCaptor.forClass(GetRecordsRequest.class);
        verify(amazonDynamoDBStreams, times(2)).getRecords(getRecordsCaptor.capture());
        assertThat(getRecordsCaptor.getAllValues().get(0).getShardIterator(), is("shard_iterator_d_000"));
        assertThat(getRecordsCaptor.getAllValues().get(1).getShardIterator(), is("shard_iterator_d_001"));
    }

    @Test
    public void trimHorizonStartsWithTheFirstShard() throws Exception {
        endpoint.setIteratorType(ShardIteratorType.TRIM_HORIZON);
        undertest = new DdbStreamConsumer(endpoint, processor);

        undertest.poll();

        ArgumentCaptor<GetShardIteratorRequest> getIteratorCaptor = ArgumentCaptor.forClass(GetShardIteratorRequest.class);
        verify(amazonDynamoDBStreams).getShardIterator(getIteratorCaptor.capture());
        assertThat(getIteratorCaptor.getValue().getShardId(), is("a"));
    }

    @Test
    public void trimHorizonWalksAllShards() throws Exception {
        endpoint.setIteratorType(ShardIteratorType.TRIM_HORIZON);
        undertest = new DdbStreamConsumer(endpoint, processor);

        for (int i = 0; i < 9; ++i) {
            undertest.poll();
        }

        ArgumentCaptor<GetShardIteratorRequest> getIteratorCaptor = ArgumentCaptor.forClass(GetShardIteratorRequest.class);
        verify(amazonDynamoDBStreams, times(4)).getShardIterator(getIteratorCaptor.capture());
        assertThat(getIteratorCaptor.getAllValues().get(0).getShardId(), is("a"));
        assertThat(getIteratorCaptor.getAllValues().get(1).getShardId(), is("b"));
        assertThat(getIteratorCaptor.getAllValues().get(2).getShardId(), is("c"));
        assertThat(getIteratorCaptor.getAllValues().get(3).getShardId(), is("d"));

        ArgumentCaptor<Exchange> exchangeCaptor = ArgumentCaptor.forClass(Exchange.class);
        verify(processor, times(seqNums.length)).process(exchangeCaptor.capture(), any(AsyncCallback.class));

        for (int i = 0; i < seqNums.length; ++i) {
            assertThat(exchangeCaptor.getAllValues().get(i).getIn().getBody(Record.class).getDynamodb().getSequenceNumber(), is(seqNums[i]));
        }
    }

    @Test
    public void atSeqNumber12StartsWithShardB() throws Exception {
        endpoint.setIteratorType(ShardIteratorType.AT_SEQUENCE_NUMBER);
        endpoint.setSequenceNumberProvider(new StaticSequenceNumberProvider("12"));
        undertest = new DdbStreamConsumer(endpoint, processor);

        undertest.poll();

        ArgumentCaptor<GetShardIteratorRequest> getIteratorCaptor = ArgumentCaptor.forClass(GetShardIteratorRequest.class);
        verify(amazonDynamoDBStreams).getShardIterator(getIteratorCaptor.capture());
        assertThat(getIteratorCaptor.getValue().getShardId(), is("b"));
    }

    @Test
    public void afterSeqNumber16StartsWithShardC() throws Exception {
        endpoint.setIteratorType(ShardIteratorType.AT_SEQUENCE_NUMBER);
        endpoint.setSequenceNumberProvider(new StaticSequenceNumberProvider("16"));
        undertest = new DdbStreamConsumer(endpoint, processor);

        undertest.poll();

        ArgumentCaptor<GetShardIteratorRequest> getIteratorCaptor = ArgumentCaptor.forClass(GetShardIteratorRequest.class);
        verify(amazonDynamoDBStreams).getShardIterator(getIteratorCaptor.capture());
        assertThat(getIteratorCaptor.getValue().getShardId(), is("c"));
    }

    @Test
    public void atSeqNumber35GivesFirstRecordWithSeq35() throws Exception {
        endpoint.setIteratorType(ShardIteratorType.AT_SEQUENCE_NUMBER);
        endpoint.setSequenceNumberProvider(new StaticSequenceNumberProvider("35"));
        undertest = new DdbStreamConsumer(endpoint, processor);

        for (int i = 0; i < 10; ++i) { // poll lots.
            undertest.poll();
        }

        ArgumentCaptor<Exchange> exchangeCaptor = ArgumentCaptor.forClass(Exchange.class);
        verify(processor, times(2)).process(exchangeCaptor.capture(), any(AsyncCallback.class));

        assertThat(exchangeCaptor.getAllValues().get(0).getIn().getBody(Record.class).getDynamodb().getSequenceNumber(), is("35"));
        assertThat(exchangeCaptor.getAllValues().get(1).getIn().getBody(Record.class).getDynamodb().getSequenceNumber(), is("40"));
    }

    @Test
    public void afterSeqNumber35GivesFirstRecordWithSeq40() throws Exception {
        endpoint.setIteratorType(ShardIteratorType.AFTER_SEQUENCE_NUMBER);
        endpoint.setSequenceNumberProvider(new StaticSequenceNumberProvider("35"));
        undertest = new DdbStreamConsumer(endpoint, processor);

        for (int i = 0; i < 10; ++i) { // poll lots.
            undertest.poll();
        }

        ArgumentCaptor<Exchange> exchangeCaptor = ArgumentCaptor.forClass(Exchange.class);
        verify(processor, times(1)).process(exchangeCaptor.capture(), any(AsyncCallback.class));

        assertThat(exchangeCaptor.getAllValues().get(0).getIn().getBody(Record.class).getDynamodb().getSequenceNumber(), is("40"));
    }

    private static Collection<Record> createRecords(String... sequenceNumbers) {
        List<Record> results = new ArrayList<>();

        for (String seqNum : sequenceNumbers) {
            results.add(new Record()
                    .withDynamodb(new StreamRecord().withSequenceNumber(seqNum))
            );
        }

        return results;
    }
    
}