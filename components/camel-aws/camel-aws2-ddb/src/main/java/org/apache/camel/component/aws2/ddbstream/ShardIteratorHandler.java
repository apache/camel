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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.camel.component.aws2.ddbstream.Ddb2StreamConfiguration.StreamIteratorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.dynamodb.model.DescribeStreamRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeStreamResponse;
import software.amazon.awssdk.services.dynamodb.model.GetShardIteratorRequest;
import software.amazon.awssdk.services.dynamodb.model.ListStreamsRequest;
import software.amazon.awssdk.services.dynamodb.model.ListStreamsResponse;
import software.amazon.awssdk.services.dynamodb.model.Shard;
import software.amazon.awssdk.services.dynamodb.model.ShardIteratorType;
import software.amazon.awssdk.services.dynamodb.streams.DynamoDbStreamsClient;

class ShardIteratorHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ShardIteratorHandler.class);

    private final Ddb2StreamEndpoint endpoint;
    private final ShardTree shardTree = new ShardTree();

    private String streamArn;
    private Map<String, String> currentShardIterators = new HashMap<>();

    ShardIteratorHandler(Ddb2StreamEndpoint endpoint) {
        this.endpoint = endpoint;
    }

    Map<String, String> getShardIterators() {
        if (streamArn == null) {
            streamArn = getStreamArn();
        }
        // Either return cached ones or get new ones via GetShardIterator requests.
        if (currentShardIterators.isEmpty()) {
            DescribeStreamResponse streamDescriptionResult
                    = getClient().describeStream(DescribeStreamRequest.builder().streamArn(streamArn).build());
            shardTree.populate(streamDescriptionResult.streamDescription().shards());

            StreamIteratorType streamIteratorType = getEndpoint().getConfiguration().getStreamIteratorType();
            currentShardIterators = getCurrentShardIterators(streamIteratorType);
        } else {
            Map<String, String> childShardIterators = new HashMap<>();
            for (Entry<String, String> currentShardIterator : currentShardIterators.entrySet()) {
                List<Shard> children = shardTree.getChildren(currentShardIterator.getKey());
                if (children.isEmpty()) { // This is still an active leaf shard, reuse it.
                    childShardIterators.put(currentShardIterator.getKey(), currentShardIterator.getValue());
                } else {
                    for (Shard child : children) { // Inactive shard, move down to its children.
                        String shardIterator = getShardIterator(child.shardId(), ShardIteratorType.TRIM_HORIZON);
                        childShardIterators.put(child.shardId(), shardIterator);
                    }
                }
            }
            currentShardIterators = childShardIterators;
        }
        LOG.trace("Shard Iterators are: {}", currentShardIterators);
        return currentShardIterators;
    }

    void updateShardIterator(String shardId, String nextShardIterator) {
        if (nextShardIterator == null) { // Shard has become inactive and all records have been consumed.
            currentShardIterators.remove(shardId);
        } else {
            currentShardIterators.put(shardId, nextShardIterator);
        }
    }

    String requestFreshShardIterator(String shardId, String lastSeenSequenceNumber) {
        String shardIterator = getShardIterator(shardId, ShardIteratorType.AFTER_SEQUENCE_NUMBER, lastSeenSequenceNumber);
        currentShardIterators.put(shardId, shardIterator);
        return shardIterator;
    }

    Ddb2StreamEndpoint getEndpoint() {
        return endpoint;
    }

    private String getStreamArn() {
        ListStreamsResponse streamsListResult = getClient().listStreams(
                ListStreamsRequest.builder().tableName(getEndpoint().getConfiguration().getTableName()).build());
        if (streamsListResult.streams().isEmpty()) {
            throw new IllegalArgumentException(
                    "There is no stream associated with table configured. Please create one.");
        }
        return streamsListResult.streams().get(0).streamArn(); // XXX assumes there is only one stream
    }

    private Map<String, String> getCurrentShardIterators(StreamIteratorType streamIteratorType) {
        List<Shard> currentShards;
        ShardIteratorType shardIteratorType;
        switch (streamIteratorType) {
            case FROM_START:
                currentShards = shardTree.getRoots();
                shardIteratorType = ShardIteratorType.TRIM_HORIZON;
                break;
            case FROM_LATEST:
            default:
                currentShards = shardTree.getLeaves();
                shardIteratorType = ShardIteratorType.LATEST;
        }

        Map<String, String> shardIterators = new HashMap<>();
        for (Shard currentShard : currentShards) {
            String shardIterator = getShardIterator(currentShard.shardId(), shardIteratorType);
            shardIterators.put(currentShard.shardId(), shardIterator);
        }
        return shardIterators;
    }

    private String getShardIterator(String shardId, ShardIteratorType shardIteratorType) {
        return getShardIterator(shardId, shardIteratorType, null);
    }

    private String getShardIterator(String shardId, ShardIteratorType shardIteratorType, String lastSeenSequenceNumber) {
        GetShardIteratorRequest request = GetShardIteratorRequest.builder()
                .streamArn(streamArn)
                .shardId(shardId)
                .shardIteratorType(shardIteratorType)
                .sequenceNumber(lastSeenSequenceNumber)
                .build();
        return getClient().getShardIterator(request).shardIterator();
    }

    private DynamoDbStreamsClient getClient() {
        return getEndpoint().getClient();
    }
}
