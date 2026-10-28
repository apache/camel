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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private final Set<String> pendingClosedShards = new HashSet<>();

    private String streamArn;
    private boolean initialized;
    private Map<String, String> currentShardIterators = new HashMap<>();

    ShardIteratorHandler(Ddb2StreamEndpoint endpoint) {
        this.endpoint = endpoint;
    }

    Map<String, String> getShardIterators() {
        if (streamArn == null) {
            streamArn = getStreamArn();
        }
        // The configured iterator type is the start position of the consumer: it is used once, and not again when
        // every shard that was being read has closed (that would read the stream again from its start, or skip the
        // records of the new shards with FROM_LATEST)
        if (!initialized) {
            shardTree.populate(describeStreamPaginated());

            StreamIteratorType streamIteratorType = getEndpoint().getConfiguration().getStreamIteratorType();
            currentShardIterators = getCurrentShardIterators(streamIteratorType);
            // no shard to read yet: try again at the next poll
            initialized = !currentShardIterators.isEmpty();
        } else if (!pendingClosedShards.isEmpty()) {
            // A shard is left only when it was read to its end (updateShardIterator got no next iterator): a closed
            // shard, which already has children, can still hold records that were not read. Refresh the shard tree,
            // so that children created by DynamoDB's shard rotation become visible, and read them from their start.
            shardTree.populate(describeStreamPaginated());

            Map<String, String> childShardIterators = new HashMap<>(currentShardIterators);
            Set<String> followed = new HashSet<>();
            for (String closedShardId : pendingClosedShards) {
                List<Shard> children = shardTree.getChildren(closedShardId);
                for (Shard child : children) {
                    if (!childShardIterators.containsKey(child.shardId())) {
                        String shardIterator = getShardIterator(child.shardId(), ShardIteratorType.TRIM_HORIZON);
                        childShardIterators.put(child.shardId(), shardIterator);
                    }
                }
                if (!children.isEmpty()) {
                    followed.add(closedShardId);
                }
            }
            // a shard whose children are not listed yet is looked up again at the next poll
            pendingClosedShards.removeAll(followed);

            currentShardIterators = childShardIterators;
        }
        LOG.trace("Shard Iterators are: {}", currentShardIterators);
        return new HashMap<>(currentShardIterators);
    }

    void updateShardIterator(String shardId, String nextShardIterator) {
        if (nextShardIterator == null) { // Shard has become inactive and all records have been consumed.
            currentShardIterators.remove(shardId);
            pendingClosedShards.add(shardId);
        } else {
            currentShardIterators.put(shardId, nextShardIterator);
        }
    }

    String requestFreshShardIterator(String shardId, String lastSeenSequenceNumber) {
        ShardIteratorType type = lastSeenSequenceNumber != null
                ? ShardIteratorType.AFTER_SEQUENCE_NUMBER
                : ShardIteratorType.TRIM_HORIZON;
        String shardIterator = getShardIterator(shardId, type, lastSeenSequenceNumber);
        currentShardIterators.put(shardId, shardIterator);
        return shardIterator;
    }

    /**
     * Requests a shard iterator that starts at the record with the given sequence number (and not after it).
     */
    String requestFreshShardIteratorAt(String shardId, String sequenceNumber) {
        String shardIterator = getShardIterator(shardId, ShardIteratorType.AT_SEQUENCE_NUMBER, sequenceNumber);
        currentShardIterators.put(shardId, shardIterator);
        return shardIterator;
    }

    Ddb2StreamEndpoint getEndpoint() {
        return endpoint;
    }

    private List<Shard> describeStreamPaginated() {
        List<Shard> allShards = new ArrayList<>();
        String lastEvaluatedShardId = null;
        do {
            DescribeStreamRequest.Builder builder = DescribeStreamRequest.builder().streamArn(streamArn);
            if (lastEvaluatedShardId != null) {
                builder.exclusiveStartShardId(lastEvaluatedShardId);
            }
            DescribeStreamResponse response = getClient().describeStream(builder.build());
            allShards.addAll(response.streamDescription().shards());
            lastEvaluatedShardId = response.streamDescription().lastEvaluatedShardId();
        } while (lastEvaluatedShardId != null);
        return allShards;
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
