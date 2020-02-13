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

import java.math.BigInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.dynamodb.model.DescribeStreamRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeStreamResponse;
import software.amazon.awssdk.services.dynamodb.model.GetShardIteratorRequest;
import software.amazon.awssdk.services.dynamodb.model.GetShardIteratorResponse;
import software.amazon.awssdk.services.dynamodb.model.ListStreamsRequest;
import software.amazon.awssdk.services.dynamodb.model.ListStreamsResponse;
import software.amazon.awssdk.services.dynamodb.model.Shard;
import software.amazon.awssdk.services.dynamodb.model.ShardIteratorType;
import software.amazon.awssdk.services.dynamodb.streams.DynamoDbStreamsClient;

class ShardIteratorHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ShardIteratorHandler.class);

    private final Ddb2StreamEndpoint endpoint;
    private final ShardList shardList = new ShardList();

    private String currentShardIterator;
    private Shard currentShard;

    ShardIteratorHandler(Ddb2StreamEndpoint endpoint) {
        this.endpoint = endpoint;
    }

    String getShardIterator(String resumeFromSequenceNumber) {
        ShardIteratorType iteratorType = getEndpoint().getConfiguration().getIteratorType();
        String sequenceNumber = getEndpoint().getSequenceNumber();
        if (resumeFromSequenceNumber != null) {
            // Reset things as we're in an error condition.
            currentShard = null;
            currentShardIterator = null;
            iteratorType = ShardIteratorType.AFTER_SEQUENCE_NUMBER;
            sequenceNumber = resumeFromSequenceNumber;
        }
        // either return a cached one or get a new one via a GetShardIterator
        // request.
        if (currentShardIterator == null) {
            ListStreamsResponse streamsListResult = getClient().listStreams(ListStreamsRequest.builder().tableName(getEndpoint().getConfiguration().getTableName()).build());
            final String streamArn = streamsListResult.streams().get(0).streamArn(); // XXX
            // assumes
            // there
            // is
            // only
            // one
            // stream
            DescribeStreamResponse streamDescriptionResult = getClient().describeStream(DescribeStreamRequest.builder().streamArn(streamArn).build());
            shardList.addAll(streamDescriptionResult.streamDescription().shards());

            LOG.trace("Current shard is: {} (in {})", currentShard, shardList);
            if (currentShard == null) {
                currentShard = resolveNewShard(iteratorType, resumeFromSequenceNumber);
            } else {
                currentShard = shardList.nextAfter(currentShard);
            }
            shardList.removeOlderThan(currentShard);
            LOG.trace("Next shard is: {} (in {})", currentShard, shardList);

            GetShardIteratorResponse result = getClient().getShardIterator(buildGetShardIteratorRequest(streamArn, iteratorType, sequenceNumber));
            currentShardIterator = result.shardIterator();
        }
        LOG.trace("Shard Iterator is: {}", currentShardIterator);
        return currentShardIterator;
    }

    private GetShardIteratorRequest buildGetShardIteratorRequest(final String streamArn, ShardIteratorType iteratorType, String sequenceNumber) {
        GetShardIteratorRequest.Builder req = GetShardIteratorRequest.builder().streamArn(streamArn).shardId(currentShard.shardId()).shardIteratorType(iteratorType);
        switch (iteratorType) {
            case AFTER_SEQUENCE_NUMBER:
            case AT_SEQUENCE_NUMBER:
                // if you request with a sequence number that is LESS than the
                // start of the shard, you get a HTTP 400 from AWS.
                // So only add the sequence number if the endpoints
                // sequence number is less than or equal to the starting
                // sequence for the shard.
                // Otherwise change the shart iterator type to trim_horizon
                // because we get a 400 when we use one of the
                // {at,after}_sequence_number iterator types and don't supply
                // a sequence number.
                if (BigIntComparisons.Conditions.LTEQ.matches(new BigInteger(currentShard.sequenceNumberRange().startingSequenceNumber()), new BigInteger(sequenceNumber))) {
                    req.sequenceNumber(sequenceNumber);
                } else {
                    req.shardIteratorType(ShardIteratorType.TRIM_HORIZON);
                }
                break;
            default:
        }
        return req.build();
    }

    private Shard resolveNewShard(ShardIteratorType type, String resumeFrom) {
        switch (type) {
            case AFTER_SEQUENCE_NUMBER:
                return shardList.afterSeq(resumeFrom != null ? resumeFrom : getEndpoint().getSequenceNumber());
            case AT_SEQUENCE_NUMBER:
                return shardList.atSeq(getEndpoint().getSequenceNumber());
            case TRIM_HORIZON:
                return shardList.first();
            case LATEST:
            default:
                return shardList.last();
        }
    }

    void updateShardIterator(String nextShardIterator) {
        this.currentShardIterator = nextShardIterator;
    }

    Ddb2StreamEndpoint getEndpoint() {
        return endpoint;
    }

    private DynamoDbStreamsClient getClient() {
        return getEndpoint().getClient();
    }
}
