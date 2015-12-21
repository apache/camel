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
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

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
import com.amazonaws.services.dynamodbv2.model.Shard;
import com.amazonaws.services.dynamodbv2.model.ShardIteratorType;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.ScheduledBatchPollingConsumer;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DdbStreamConsumer extends ScheduledBatchPollingConsumer {
    private static final Logger LOG = LoggerFactory.getLogger(DdbStreamConsumer.class);

    private String currentShardIterator;
    private Shard currentShard;
    private final ShardList shardList = new ShardList();

    public DdbStreamConsumer(DdbStreamEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    @Override
    protected int poll() throws Exception {
        GetRecordsRequest req = new GetRecordsRequest()
                .withShardIterator(getShardItertor())
                .withLimit(getEndpoint().getMaxResultsPerRequest());
        GetRecordsResult result = getClient().getRecords(req);

        Queue<Exchange> exchanges = createExchanges(result.getRecords());
        int processedExchangeCount = processBatch(CastUtils.cast(exchanges));

        currentShardIterator = result.getNextShardIterator();

        return processedExchangeCount;
    }

    @Override
    public int processBatch(Queue<Object> exchanges) throws Exception {
        int processedExchanges = 0;
        while (!exchanges.isEmpty()) {
            final Exchange exchange = ObjectHelper.cast(Exchange.class, exchanges.poll());

            LOG.trace("Processing exchange [{}] started.", exchange);
            getAsyncProcessor().process(exchange, new AsyncCallback() {
                @Override
                public void done(boolean doneSync) {
                    LOG.trace("Processing exchange [{}] done.", exchange);
                }
            });
            processedExchanges++;
        }
        return processedExchanges;
    }

    private AmazonDynamoDBStreams getClient() {
        return getEndpoint().getClient();
    }

    @Override
    public DdbStreamEndpoint getEndpoint() {
        return (DdbStreamEndpoint) super.getEndpoint();
    }

    private String getShardItertor() {
        // either return a cached one or get a new one via a GetShardIterator request.
        if (currentShardIterator == null) {
            ListStreamsRequest req0 = new ListStreamsRequest()
                    .withTableName(getEndpoint().getTableName());
            ListStreamsResult res0 = getClient().listStreams(req0);
            final String streamArn = res0.getStreams().get(0).getStreamArn(); // XXX assumes there is only one stream
            DescribeStreamRequest req1 = new DescribeStreamRequest()
                    .withStreamArn(streamArn);
            DescribeStreamResult res1 = getClient().describeStream(req1);
            shardList.addAll(res1.getStreamDescription().getShards());

            LOG.trace("Current shard is: {} (in {})", currentShard, shardList);
            if (currentShard == null) {
                switch(getEndpoint().getIteratorType()) {
                case AFTER_SEQUENCE_NUMBER:
                    currentShard = shardList.afterSeq(getEndpoint().getSequenceNumber());
                    break;
                case AT_SEQUENCE_NUMBER:
                    currentShard = shardList.atSeq(getEndpoint().getSequenceNumber());
                    break;
                case TRIM_HORIZON:
                    currentShard = shardList.first();
                    break;
                case LATEST:
                default:
                    currentShard = shardList.last();
                    break;
                }
            } else {
                currentShard = shardList.nextAfter(currentShard);
            }
            shardList.removeOlderThan(currentShard);
            LOG.trace("Next shard is: {} (in {})", currentShard, shardList);

            GetShardIteratorRequest req = new GetShardIteratorRequest()
                    .withStreamArn(streamArn)
                    .withShardId(currentShard.getShardId())
                    .withShardIteratorType(getEndpoint().getIteratorType());
            switch(getEndpoint().getIteratorType()) {
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
                        new BigInteger(getEndpoint().getSequenceNumber())
                )) {
                    req = req.withSequenceNumber(getEndpoint().getSequenceNumber());
                } else {
                    req = req.withShardIteratorType(ShardIteratorType.TRIM_HORIZON);
                }
                break;
            default:
            }
            GetShardIteratorResult result = getClient().getShardIterator(req);
            currentShardIterator = result.getShardIterator();
        }
        LOG.trace("Shard Iterator is: {}", currentShardIterator);
        return currentShardIterator;
    }

    private Queue<Exchange> createExchanges(List<Record> records) {
        Queue<Exchange> exchanges = new ArrayDeque<>();
        BigIntComparisons condition;
        BigInteger providedSeqNum = null;
        switch(getEndpoint().getIteratorType()) {
        case AFTER_SEQUENCE_NUMBER:
            condition = BigIntComparisons.Conditions.LT;
            providedSeqNum = new BigInteger(getEndpoint().getSequenceNumberProvider().getSequenceNumber());
            break;
        case AT_SEQUENCE_NUMBER:
            condition = BigIntComparisons.Conditions.LTEQ;
            providedSeqNum = new BigInteger(getEndpoint().getSequenceNumberProvider().getSequenceNumber());
            break;
        default:
            condition = null;
        }
        for (Record record : records) {
            BigInteger recordSeqNum = new BigInteger(record.getDynamodb().getSequenceNumber());
            if (condition == null || condition.matches(providedSeqNum, recordSeqNum)) {
                exchanges.add(getEndpoint().createExchange(record));
            }
        }
        return exchanges;
    }
}
