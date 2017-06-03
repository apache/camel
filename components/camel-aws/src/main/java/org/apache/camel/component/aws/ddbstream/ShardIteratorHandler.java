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

import java.math.BigInteger;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBStreams;
import com.amazonaws.services.dynamodbv2.model.DescribeStreamRequest;
import com.amazonaws.services.dynamodbv2.model.DescribeStreamResult;
import com.amazonaws.services.dynamodbv2.model.GetShardIteratorRequest;
import com.amazonaws.services.dynamodbv2.model.GetShardIteratorResult;
import com.amazonaws.services.dynamodbv2.model.ListStreamsRequest;
import com.amazonaws.services.dynamodbv2.model.ListStreamsResult;
import com.amazonaws.services.dynamodbv2.model.Shard;
import com.amazonaws.services.dynamodbv2.model.ShardIteratorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ShardIteratorHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ShardIteratorHandler.class);

    private final DdbStreamEndpoint endpoint;
    private final ShardList shardList = new ShardList();

    private String currentShardIterator;
    private Shard currentShard;

    ShardIteratorHandler(DdbStreamEndpoint endpoint) {
        this.endpoint = endpoint;
    }

    String getShardIterator(String resumeFromSequenceNumber) {
        ShardIteratorType iteratorType = getEndpoint().getIteratorType();
        String sequenceNumber = getEndpoint().getSequenceNumber();
        if (resumeFromSequenceNumber != null) {
            // Reset things as we're in an error condition.
            currentShard = null;
            currentShardIterator = null;
            iteratorType = ShardIteratorType.AFTER_SEQUENCE_NUMBER;
            sequenceNumber = resumeFromSequenceNumber;
        }
        // either return a cached one or get a new one via a GetShardIterator request.
        if (currentShardIterator == null) {
            ListStreamsResult streamsListResult = getClient().listStreams(
                    new ListStreamsRequest().withTableName(getEndpoint().getTableName())
            );
            final String streamArn = streamsListResult.getStreams().get(0).getStreamArn(); // XXX assumes there is only one stream
            DescribeStreamResult streamDescriptionResult = getClient().describeStream(
                    new DescribeStreamRequest().withStreamArn(streamArn)
            );
            shardList.addAll(streamDescriptionResult.getStreamDescription().getShards());

            LOG.trace("Current shard is: {} (in {})", currentShard, shardList);
            if (currentShard == null) {
                currentShard = resolveNewShard(iteratorType, resumeFromSequenceNumber);
            } else {
                currentShard = shardList.nextAfter(currentShard);
            }
            shardList.removeOlderThan(currentShard);
            LOG.trace("Next shard is: {} (in {})", currentShard, shardList);

            GetShardIteratorResult result = getClient().getShardIterator(
                    buildGetShardIteratorRequest(streamArn, iteratorType, sequenceNumber)
            );
            currentShardIterator = result.getShardIterator();
        }
        LOG.trace("Shard Iterator is: {}", currentShardIterator);
        return currentShardIterator;
    }

    private GetShardIteratorRequest buildGetShardIteratorRequest(final String streamArn, ShardIteratorType iteratorType, String sequenceNumber) {
        GetShardIteratorRequest req = new GetShardIteratorRequest()
                .withStreamArn(streamArn)
                .withShardId(currentShard.getShardId())
                .withShardIteratorType(iteratorType);
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
            if (BigIntComparisons.Conditions.LTEQ.matches(
                    new BigInteger(currentShard.getSequenceNumberRange().getStartingSequenceNumber()),
                    new BigInteger(sequenceNumber)
            )) {
                req = req.withSequenceNumber(sequenceNumber);
            } else {
                req = req.withShardIteratorType(ShardIteratorType.TRIM_HORIZON);
            }
            break;
        default:
        }
        return req;
    }

    private Shard resolveNewShard(ShardIteratorType type, String resumeFrom) {
        switch(type) {
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

    DdbStreamEndpoint getEndpoint() {
        return endpoint;
    }
   
    private AmazonDynamoDBStreams getClient() {
        return getEndpoint().getClient();
    }
}
