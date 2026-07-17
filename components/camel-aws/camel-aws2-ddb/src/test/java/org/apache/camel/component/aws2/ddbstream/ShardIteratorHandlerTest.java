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
package org.apache.camel.component.aws2.ddbstream;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.component.aws2.ddbstream.Ddb2StreamConfiguration.StreamIteratorType;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.SequenceNumberRange;
import software.amazon.awssdk.services.dynamodb.model.Shard;
import software.amazon.awssdk.services.dynamodb.model.ShardIteratorType;

import static org.apache.camel.component.aws2.ddbstream.ShardFixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShardIteratorHandlerTest extends CamelTestSupport {

    private static final String SHARD_ITERATOR_0 = STREAM_ARN + "|1|hash-0";
    private static final String SHARD_ITERATOR_1 = STREAM_ARN + "|1|hash-1";
    private static final String SHARD_ITERATOR_2 = STREAM_ARN + "|1|hash-2";
    private static final String SHARD_ITERATOR_3 = STREAM_ARN + "|1|hash-3";
    private static final String SHARD_ITERATOR_4 = STREAM_ARN + "|1|hash-4";
    private static final String SHARD_ITERATOR_5 = STREAM_ARN + "|1|hash-5";
    private static final String SHARD_ITERATOR_6 = STREAM_ARN + "|1|hash-6";

    private Ddb2StreamComponent component;
    private AmazonDDBStreamsClientMock dynamoDbStreamsClient;

    @BeforeEach
    void setup() {
        component = context.getComponent("aws2-ddbstream", Ddb2StreamComponent.class);
        dynamoDbStreamsClient = new AmazonDDBStreamsClientMock();
        component.getConfiguration().setAmazonDynamoDbStreamsClient(dynamoDbStreamsClient);
        dynamoDbStreamsClient.setMockedShardAndIteratorResponse(SHARD_0, SHARD_ITERATOR_0);
        dynamoDbStreamsClient.setMockedShardAndIteratorResponse(SHARD_1, SHARD_ITERATOR_1);
        dynamoDbStreamsClient.setMockedShardAndIteratorResponse(SHARD_2, SHARD_ITERATOR_2);
        dynamoDbStreamsClient.setMockedShardAndIteratorResponse(SHARD_3, SHARD_ITERATOR_3);
        dynamoDbStreamsClient.setMockedShardAndIteratorResponse(SHARD_4, SHARD_ITERATOR_4);
        dynamoDbStreamsClient.setMockedShardAndIteratorResponse(SHARD_5, SHARD_ITERATOR_5);
        dynamoDbStreamsClient.setMockedShardAndIteratorResponse(SHARD_6, SHARD_ITERATOR_6);
    }

    @Test
    void shouldReturnLeafShardIterators() throws Exception {
        component.getConfiguration().setStreamIteratorType(StreamIteratorType.FROM_LATEST);
        Ddb2StreamEndpoint endpoint = (Ddb2StreamEndpoint) component.createEndpoint("aws2-ddbstreams://myTable");
        ShardIteratorHandler underTest = new ShardIteratorHandler(endpoint);
        endpoint.doStart();

        Map<String, String> expectedShardIterators = new HashMap<>();
        expectedShardIterators.put(SHARD_3.shardId(), SHARD_ITERATOR_3);
        expectedShardIterators.put(SHARD_4.shardId(), SHARD_ITERATOR_4);
        expectedShardIterators.put(SHARD_5.shardId(), SHARD_ITERATOR_5);
        expectedShardIterators.put(SHARD_6.shardId(), SHARD_ITERATOR_6);
        assertEquals(expectedShardIterators, underTest.getShardIterators());
    }

    @Test
    void shouldReturnRootShardIterator() throws Exception {
        component.getConfiguration().setStreamIteratorType(StreamIteratorType.FROM_START);
        Ddb2StreamEndpoint endpoint = (Ddb2StreamEndpoint) component.createEndpoint("aws2-ddbstreams://myTable");
        ShardIteratorHandler underTest = new ShardIteratorHandler(endpoint);
        endpoint.doStart();

        assertEquals(Collections.singletonMap(SHARD_0.shardId(), SHARD_ITERATOR_0), underTest.getShardIterators());
    }

    @Test
    void shouldProgressThroughTreeWhenShardIteratorsAreRetrievedRepeatedly() throws Exception {
        component.getConfiguration().setStreamIteratorType(StreamIteratorType.FROM_START);
        Ddb2StreamEndpoint endpoint = (Ddb2StreamEndpoint) component.createEndpoint("aws2-ddbstreams://myTable");
        ShardIteratorHandler underTest = new ShardIteratorHandler(endpoint);
        endpoint.doStart();

        assertEquals(Collections.singletonMap(SHARD_0.shardId(), SHARD_ITERATOR_0), underTest.getShardIterators());
        Map<String, String> expectedShardIterators1 = new HashMap<>();
        expectedShardIterators1.put(SHARD_1.shardId(), SHARD_ITERATOR_1);
        expectedShardIterators1.put(SHARD_2.shardId(), SHARD_ITERATOR_2);
        assertEquals(expectedShardIterators1, underTest.getShardIterators());
        Map<String, String> expectedShardIterators2 = new HashMap<>();
        expectedShardIterators2.put(SHARD_3.shardId(), SHARD_ITERATOR_3);
        expectedShardIterators2.put(SHARD_4.shardId(), SHARD_ITERATOR_4);
        expectedShardIterators2.put(SHARD_5.shardId(), SHARD_ITERATOR_5);
        expectedShardIterators2.put(SHARD_6.shardId(), SHARD_ITERATOR_6);
        assertEquals(expectedShardIterators2, underTest.getShardIterators());
        Map<String, String> expectedShardIterators3 = expectedShardIterators2;
        assertEquals(expectedShardIterators3, underTest.getShardIterators());
    }

    @Test
    void shouldUpdateShardIterator() throws Exception {
        component.getConfiguration().setStreamIteratorType(StreamIteratorType.FROM_LATEST);
        Ddb2StreamEndpoint endpoint = (Ddb2StreamEndpoint) component.createEndpoint("aws2-ddbstreams://myTable");
        ShardIteratorHandler underTest = new ShardIteratorHandler(endpoint);
        endpoint.doStart();

        underTest.getShardIterators();
        String updatedShardIterator5 = STREAM_ARN + "|1|hash-5-new";
        underTest.updateShardIterator(SHARD_5.shardId(), updatedShardIterator5);

        assertEquals(updatedShardIterator5, underTest.getShardIterators().get(SHARD_5.shardId()));
    }

    @Test
    void shouldRemoveShardIfNullIteratorIsProvided() throws Exception {
        component.getConfiguration().setStreamIteratorType(StreamIteratorType.FROM_LATEST);
        Ddb2StreamEndpoint endpoint = (Ddb2StreamEndpoint) component.createEndpoint("aws2-ddbstreams://myTable");
        ShardIteratorHandler underTest = new ShardIteratorHandler(endpoint);
        endpoint.doStart();

        underTest.getShardIterators();
        underTest.updateShardIterator(SHARD_3.shardId(), null);

        assertFalse(underTest.getShardIterators().containsKey(SHARD_3.shardId()));
    }

    @Test
    void shouldRequestAndCacheFreshShardIterator() throws Exception {
        component.getConfiguration().setStreamIteratorType(StreamIteratorType.FROM_LATEST);
        Ddb2StreamEndpoint endpoint = (Ddb2StreamEndpoint) component.createEndpoint("aws2-ddbstreams://myTable");
        ShardIteratorHandler underTest = new ShardIteratorHandler(endpoint);
        endpoint.doStart();

        underTest.getShardIterators();
        String freshShardIterator4 = STREAM_ARN + "|1|hash-4-fresh";
        dynamoDbStreamsClient.setMockedShardAndIteratorResponse(SHARD_4, freshShardIterator4);

        assertEquals(freshShardIterator4, underTest.requestFreshShardIterator(SHARD_4.shardId(), freshShardIterator4));
        assertEquals(freshShardIterator4, underTest.getShardIterators().get(SHARD_4.shardId()));
    }

    @Test
    void shouldThrowIllegalArgumentExceptionIfNoStreamsAreReturned() throws Exception {
        AmazonDDBStreamlessClientMock dynamoDbStreamsClient = new AmazonDDBStreamlessClientMock();
        component.getConfiguration().setAmazonDynamoDbStreamsClient(dynamoDbStreamsClient);

        Ddb2StreamEndpoint endpoint = (Ddb2StreamEndpoint) component.createEndpoint("aws2-ddbstreams://myTable");
        ShardIteratorHandler underTest = new ShardIteratorHandler(endpoint);
        endpoint.doStart();

        assertThrows(IllegalArgumentException.class, () -> underTest.getShardIterators());
    }

    @Test
    void shouldDiscoverNewShardsAfterResharding() throws Exception {
        // Fresh mock with only two active shards
        AmazonDDBStreamsClientMock reshardingMock = new AmazonDDBStreamsClientMock();
        component.getConfiguration().setAmazonDynamoDbStreamsClient(reshardingMock);

        Shard shardA = Shard.builder()
                .shardId("SHARD_A")
                .sequenceNumberRange(SequenceNumberRange.builder()
                        .startingSequenceNumber("100").build())
                .build();
        Shard shardB = Shard.builder()
                .shardId("SHARD_B")
                .sequenceNumberRange(SequenceNumberRange.builder()
                        .startingSequenceNumber("200").build())
                .build();
        String iterA = STREAM_ARN + "|iter-A";
        String iterB = STREAM_ARN + "|iter-B";

        reshardingMock.setMockedShardAndIteratorResponse(shardA, iterA);
        reshardingMock.setMockedShardAndIteratorResponse(shardB, iterB);

        component.getConfiguration().setStreamIteratorType(StreamIteratorType.FROM_LATEST);
        Ddb2StreamEndpoint endpoint = (Ddb2StreamEndpoint) component.createEndpoint("aws2-ddbstreams://myTable");
        ShardIteratorHandler underTest = new ShardIteratorHandler(endpoint);
        endpoint.doStart();

        // Initial poll: both leaves are returned
        Map<String, String> iter1 = underTest.getShardIterators();
        assertEquals(2, iter1.size());
        assertTrue(iter1.containsKey("SHARD_A"));
        assertTrue(iter1.containsKey("SHARD_B"));

        // Shard A closes (consumer drained it)
        underTest.updateShardIterator("SHARD_A", null);

        // Simulate resharding: A becomes closed, children A1 and A2 appear
        Shard closedA = Shard.builder()
                .shardId("SHARD_A")
                .sequenceNumberRange(SequenceNumberRange.builder()
                        .startingSequenceNumber("100").endingSequenceNumber("150").build())
                .build();
        Shard shardA1 = Shard.builder()
                .shardId("SHARD_A1")
                .parentShardId("SHARD_A")
                .sequenceNumberRange(SequenceNumberRange.builder()
                        .startingSequenceNumber("151").build())
                .build();
        Shard shardA2 = Shard.builder()
                .shardId("SHARD_A2")
                .parentShardId("SHARD_A")
                .sequenceNumberRange(SequenceNumberRange.builder()
                        .startingSequenceNumber("152").build())
                .build();
        String iterA1 = STREAM_ARN + "|iter-A1";
        String iterA2 = STREAM_ARN + "|iter-A2";
        reshardingMock.setMockedShardAndIteratorResponse(closedA, iterA);
        reshardingMock.setMockedShardAndIteratorResponse(shardA1, iterA1);
        reshardingMock.setMockedShardAndIteratorResponse(shardA2, iterA2);

        // Next poll: tree is refreshed, children of A are discovered
        Map<String, String> iter2 = underTest.getShardIterators();
        assertEquals(3, iter2.size());
        assertTrue(iter2.containsKey("SHARD_A1"));
        assertTrue(iter2.containsKey("SHARD_A2"));
        assertTrue(iter2.containsKey("SHARD_B"));
        assertFalse(iter2.containsKey("SHARD_A"));
    }

    @Test
    void shouldReturnDefensiveCopyOfShardIterators() throws Exception {
        component.getConfiguration().setStreamIteratorType(StreamIteratorType.FROM_LATEST);
        Ddb2StreamEndpoint endpoint = (Ddb2StreamEndpoint) component.createEndpoint("aws2-ddbstreams://myTable");
        ShardIteratorHandler underTest = new ShardIteratorHandler(endpoint);
        endpoint.doStart();

        Map<String, String> first = underTest.getShardIterators();
        Map<String, String> second = underTest.getShardIterators();

        // Mutating the returned map must not affect internal state
        first.put("EXTRA_SHARD", "EXTRA_ITERATOR");
        assertFalse(second.containsKey("EXTRA_SHARD"));
    }

    @Test
    void shouldUseTrimHorizonWhenSequenceNumberIsNull() throws Exception {
        component.getConfiguration().setStreamIteratorType(StreamIteratorType.FROM_LATEST);
        Ddb2StreamEndpoint endpoint = (Ddb2StreamEndpoint) component.createEndpoint("aws2-ddbstreams://myTable");
        ShardIteratorHandler underTest = new ShardIteratorHandler(endpoint);
        endpoint.doStart();

        underTest.getShardIterators();
        dynamoDbStreamsClient.getShardIteratorRequests().clear();

        underTest.requestFreshShardIterator(SHARD_3.shardId(), null);

        assertEquals(1, dynamoDbStreamsClient.getShardIteratorRequests().size());
        assertEquals(ShardIteratorType.TRIM_HORIZON,
                dynamoDbStreamsClient.getShardIteratorRequests().get(0).shardIteratorType());
    }

}
