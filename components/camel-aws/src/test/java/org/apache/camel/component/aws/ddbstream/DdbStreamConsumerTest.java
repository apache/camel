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
import com.amazonaws.services.dynamodbv2.model.ExpiredIteratorException;
import com.amazonaws.services.dynamodbv2.model.GetRecordsRequest;
import com.amazonaws.services.dynamodbv2.model.GetRecordsResult;
import com.amazonaws.services.dynamodbv2.model.Record;
import com.amazonaws.services.dynamodbv2.model.ShardIteratorType;
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
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class DdbStreamConsumerTest {

    private DdbStreamConsumer undertest;

    @Mock
    private AmazonDynamoDBStreams amazonDynamoDBStreams;
    @Mock
    private AsyncProcessor processor;
    @Mock
    private ShardIteratorHandler shardIteratorHandler;

    private final CamelContext context = new DefaultCamelContext();
    private final DdbStreamComponent component = new DdbStreamComponent(context);
    private final DdbStreamEndpoint endpoint = new DdbStreamEndpoint(null, "table_name", component);
    private GetRecordsAnswer recordsAnswer;

    @Before
    public void setup() throws Exception {
        endpoint.setAmazonDynamoDbStreamsClient(amazonDynamoDBStreams);

        undertest = new DdbStreamConsumer(endpoint, processor, shardIteratorHandler);

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
        recordsAnswer = new GetRecordsAnswer(shardIterators, answers);
        when(amazonDynamoDBStreams.getRecords(any(GetRecordsRequest.class))).thenAnswer(recordsAnswer);
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
    public void itResumesFromAfterTheLastSeenSequenceNumberWhenAShardIteratorHasExpired() throws Exception {
        endpoint.setIteratorType(ShardIteratorType.LATEST);
        when(shardIteratorHandler.getShardIterator(ArgumentMatchers.isNull())).thenReturn("shard_iterator_b_000", "shard_iterator_b_001");
        when(shardIteratorHandler.getShardIterator(ArgumentMatchers.anyString())).thenReturn("shard_iterator_b_001");
        when(amazonDynamoDBStreams.getRecords(any(GetRecordsRequest.class)))
                .thenAnswer(recordsAnswer)
                .thenThrow(new ExpiredIteratorException("expired shard"))
                .thenAnswer(recordsAnswer);

        undertest.poll();
        undertest.poll();

        ArgumentCaptor<Exchange> exchangeCaptor = ArgumentCaptor.forClass(Exchange.class);
        verify(processor, times(3)).process(exchangeCaptor.capture(), any(AsyncCallback.class));
        verify(shardIteratorHandler, times(2)).getShardIterator(null); // first poll. Second poll, getRecords fails with an expired shard.
        verify(shardIteratorHandler).getShardIterator("9"); // second poll, with a resumeFrom.
        assertThat(exchangeCaptor.getAllValues().get(0).getIn().getBody(Record.class).getDynamodb().getSequenceNumber(), is("9"));
        assertThat(exchangeCaptor.getAllValues().get(1).getIn().getBody(Record.class).getDynamodb().getSequenceNumber(), is("11"));
        assertThat(exchangeCaptor.getAllValues().get(2).getIn().getBody(Record.class).getDynamodb().getSequenceNumber(), is("13"));
    }

    @Test
    public void atSeqNumber35GivesFirstRecordWithSeq35() throws Exception {
        endpoint.setIteratorType(ShardIteratorType.AT_SEQUENCE_NUMBER);
        endpoint.setSequenceNumberProvider(new StaticSequenceNumberProvider("35"));
        when(shardIteratorHandler.getShardIterator(ArgumentMatchers.isNull())).thenReturn("shard_iterator_d_001", "shard_iterator_d_002");

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
        when(shardIteratorHandler.getShardIterator(ArgumentMatchers.isNull())).thenReturn("shard_iterator_d_001", "shard_iterator_d_002");

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

    private class GetRecordsAnswer implements Answer<GetRecordsResult> {

        private final Map<String, String> shardIterators;
        private final Map<String, Collection<Record>> answers;
        private final Pattern shardIteratorPattern = Pattern.compile("shard_iterator_d_0*(\\d+)");

        GetRecordsAnswer(Map<String, String> shardIterators, Map<String, Collection<Record>> answers) {
            this.shardIterators = shardIterators;
            this.answers = answers;
        }

        @Override
        public GetRecordsResult answer(InvocationOnMock invocation) throws Throwable {
            final String shardIterator = ((GetRecordsRequest) invocation.getArguments()[0]).getShardIterator();
            // note that HashMap returns null when there is no entry in the map.
            // A null 'nextShardIterator' indicates that the shard has finished
            // and we should move onto the next shard.
            String nextShardIterator = shardIterators.get(shardIterator);
            Matcher m = shardIteratorPattern.matcher(shardIterator);
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
    }
}