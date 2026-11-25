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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.ExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.GetRecordsRequest;
import software.amazon.awssdk.services.kinesis.model.GetRecordsResponse;
import software.amazon.awssdk.services.kinesis.model.GetShardIteratorRequest;
import software.amazon.awssdk.services.kinesis.model.GetShardIteratorResponse;
import software.amazon.awssdk.services.kinesis.model.ListShardsRequest;
import software.amazon.awssdk.services.kinesis.model.ListShardsResponse;
import software.amazon.awssdk.services.kinesis.model.SequenceNumberRange;
import software.amazon.awssdk.services.kinesis.model.Shard;
import software.amazon.awssdk.services.kinesis.model.ShardIteratorType;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class KinesisConsumerClosedShardWithFailTest {

    @Mock
    private KinesisClient kinesisClient;
    @Mock
    private AsyncProcessor processor;

    private final CamelContext context = new DefaultCamelContext();
    private final Kinesis2Component component = new Kinesis2Component(context);

    private final List<Throwable> handled = new CopyOnWriteArrayList<>();

    private Kinesis2Consumer underTest;

    @AfterEach
    public void tearDown() {
        // the consumer polls on a thread of its own: stop it, so it does not go on polling during the later tests of
        // the module (a poll reads the clock, which the DST test of the module controls while it runs)
        underTest.stop();
        context.stop();
    }

    @BeforeEach
    public void setup() {
        SequenceNumberRange range = SequenceNumberRange.builder().endingSequenceNumber("20").build();
        Shard shard = Shard.builder().shardId("shardId").sequenceNumberRange(range).build();
        ArrayList<Shard> shardList = new ArrayList<>();
        shardList.add(shard);

        when(kinesisClient
                .getRecords(any(GetRecordsRequest.class)))
                .thenReturn(GetRecordsResponse.builder().nextShardIterator(null).build());
        when(kinesisClient
                .getShardIterator(any(GetShardIteratorRequest.class)))
                .thenReturn(GetShardIteratorResponse.builder().shardIterator("shardIterator").build());
        when(kinesisClient
                .listShards(any(ListShardsRequest.class)))
                .thenReturn(ListShardsResponse.builder().shards(shardList).build());

        component.start();

        Kinesis2Configuration configuration = new Kinesis2Configuration();
        configuration.setAmazonKinesisClient(kinesisClient);
        configuration.setIteratorType(ShardIteratorType.LATEST);
        configuration.setShardClosed(Kinesis2ShardClosedStrategyEnum.fail);
        configuration.setStreamName("streamName");

        Kinesis2Endpoint endpoint = new Kinesis2Endpoint("aws2-kinesis:foo", configuration, component);
        endpoint.start();
        underTest = new Kinesis2Consumer(endpoint, processor);
        underTest.setConnection(component.getConnection());
        underTest.setExceptionHandler(new RecordingExceptionHandler());
        // the test calls poll()
        underTest.setStartScheduler(false);
        underTest.start();
        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> !(underTest.getCurrentShardList().isEmpty()));
    }

    @Test
    public void itObtainsAShardIteratorOnFirstPoll() throws Exception {
        underTest.poll(); // reads the shard to its end
        underTest.poll(); // the shard is now closed: reported to the exception handler, the poll does not fail

        final ArgumentCaptor<GetShardIteratorRequest> getShardIteratorReqCap
                = ArgumentCaptor.forClass(GetShardIteratorRequest.class);
        final ArgumentCaptor<ListShardsRequest> getListShardsCap
                = ArgumentCaptor.forClass(ListShardsRequest.class);

        verify(kinesisClient).getShardIterator(getShardIteratorReqCap.capture());
        assertThat(getShardIteratorReqCap.getValue().streamName(), is("streamName"));
        assertThat(getShardIteratorReqCap.getValue().shardId(), is("shardId"));
        assertThat(getShardIteratorReqCap.getValue().shardIteratorType(), is(ShardIteratorType.LATEST));

        verify(kinesisClient, atLeastOnce()).listShards(getListShardsCap.capture());
        assertThat(getListShardsCap.getValue().streamName(), is("streamName"));

        assertReportedOnce();
    }

    @Test
    public void closedShardIsReportedOnceAndThenSkipped() throws Exception {
        assertThat(underTest.poll(), is(0)); // reads the shard to its end
        assertThat(underTest.isShardClosed("shardId"), is(true));
        assertThat(handled.isEmpty(), is(true));

        // the closed shard stays in the shard list: later polls skip it, and report it only once
        assertThat(underTest.poll(), is(0));
        assertThat(underTest.poll(), is(0));
        assertThat(underTest.poll(), is(0));

        assertReportedOnce();
        verify(kinesisClient, times(1)).getRecords(any(GetRecordsRequest.class));
    }

    @Test
    public void closedShardIsReportedOnceAndThenSkippedWithShardId() throws Exception {
        underTest.getEndpoint().getConfiguration().setShardId("shardId");

        assertThat(underTest.poll(), is(0)); // reads the shard to its end
        assertThat(handled.isEmpty(), is(true));

        // with shardId the consumer has nothing more to read once the shard is closed
        assertThat(underTest.poll(), is(0));
        assertThat(underTest.poll(), is(0));
        assertThat(underTest.poll(), is(0));

        assertReportedOnce();
        verify(kinesisClient, times(1)).getRecords(any(GetRecordsRequest.class));
    }

    @Test
    public void closedShardIsReportedAgainAfterRestart() throws Exception {
        underTest.poll();
        underTest.poll();
        assertReportedOnce();

        underTest.stop();
        underTest.start();
        underTest.poll();
        underTest.poll();

        assertThat(handled.size(), is(2));
    }

    private void assertReportedOnce() {
        assertThat(handled.size(), is(1));
        assertThat(handled.get(0), instanceOf(ReachedClosedStatusException.class));
        ReachedClosedStatusException e = (ReachedClosedStatusException) handled.get(0);
        assertThat(e.getShardId(), is("shardId"));
        assertThat(e.getStreamName(), is("streamName"));
    }

    private final class RecordingExceptionHandler implements ExceptionHandler {
        @Override
        public void handleException(Throwable exception) {
            handled.add(exception);
        }

        @Override
        public void handleException(String message, Throwable exception) {
            handled.add(exception);
        }

        @Override
        public void handleException(String message, Exchange exchange, Throwable exception) {
            handled.add(exception);
        }
    }
}
