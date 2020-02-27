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

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.support.ScheduledBatchPollingConsumer;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.DescribeStreamRequest;
import software.amazon.awssdk.services.kinesis.model.DescribeStreamResponse;
import software.amazon.awssdk.services.kinesis.model.GetRecordsRequest;
import software.amazon.awssdk.services.kinesis.model.GetRecordsResponse;
import software.amazon.awssdk.services.kinesis.model.GetShardIteratorRequest;
import software.amazon.awssdk.services.kinesis.model.GetShardIteratorResponse;
import software.amazon.awssdk.services.kinesis.model.Record;
import software.amazon.awssdk.services.kinesis.model.Shard;
import software.amazon.awssdk.services.kinesis.model.ShardIteratorType;

public class Kinesis2Consumer extends ScheduledBatchPollingConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(Kinesis2Consumer.class);

    private String currentShardIterator;
    private boolean isShardClosed;

    public Kinesis2Consumer(Kinesis2Endpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    @Override
    protected int poll() throws Exception {
        GetRecordsRequest req = GetRecordsRequest.builder().shardIterator(getShardItertor()).limit(getEndpoint().getConfiguration().getMaxResultsPerRequest()).build();
        GetRecordsResponse result = getClient().getRecords(req);

        Queue<Exchange> exchanges = createExchanges(result.records());
        int processedExchangeCount = processBatch(CastUtils.cast(exchanges));

        // May cache the last successful sequence number, and pass it to the
        // getRecords request. That way, on the next poll, we start from where
        // we left off, however, I don't know what happens to subsequent
        // exchanges when an earlier echangee fails.

        currentShardIterator = result.nextShardIterator();
        if (isShardClosed) {
            switch (getEndpoint().getConfiguration().getShardClosed()) {
                case ignore:
                    LOG.warn("The shard {} is in closed state", currentShardIterator);
                    break;
                case silent:
                    break;
                case fail:
                    LOG.info("Shard Iterator reaches CLOSE status:{} {}", getEndpoint().getConfiguration().getStreamName(), getEndpoint().getConfiguration().getShardId());
                    throw new ReachedClosedStatusException(getEndpoint().getConfiguration().getStreamName(), getEndpoint().getConfiguration().getShardId());
                default:
                    throw new IllegalArgumentException("Unsupported shard closed strategy");
            }
        }

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

    private KinesisClient getClient() {
        return getEndpoint().getClient();
    }

    @Override
    public Kinesis2Endpoint getEndpoint() {
        return (Kinesis2Endpoint)super.getEndpoint();
    }

    private String getShardItertor() {
        // either return a cached one or get a new one via a GetShardIterator
        // request.
        if (currentShardIterator == null) {
            String shardId;

            // If ShardId supplied use it, else choose first one
            if (!getEndpoint().getConfiguration().getShardId().isEmpty()) {
                shardId = getEndpoint().getConfiguration().getShardId();
                DescribeStreamRequest req1 = DescribeStreamRequest.builder().streamName(getEndpoint().getConfiguration().getStreamName()).build();
                DescribeStreamResponse res1 = getClient().describeStream(req1);
                for (Shard shard : res1.streamDescription().shards()) {
                    if (shard.shardId().equalsIgnoreCase(getEndpoint().getConfiguration().getShardId())) {
                        isShardClosed = shard.sequenceNumberRange().endingSequenceNumber() != null;
                    }
                }

            } else {
                DescribeStreamRequest req1 = DescribeStreamRequest.builder().streamName(getEndpoint().getConfiguration().getStreamName()).build();
                DescribeStreamResponse res1 = getClient().describeStream(req1);
                shardId = res1.streamDescription().shards().get(0).shardId();
                isShardClosed = res1.streamDescription().shards().get(0).sequenceNumberRange().endingSequenceNumber() != null;
            }
            LOG.debug("ShardId is: {}", shardId);

            GetShardIteratorRequest.Builder req = GetShardIteratorRequest.builder().streamName(getEndpoint().getConfiguration().getStreamName()).shardId(shardId)
                .shardIteratorType(getEndpoint().getConfiguration().getIteratorType());

            if (hasSequenceNumber()) {
                req.startingSequenceNumber(getEndpoint().getConfiguration().getSequenceNumber());
            }

            GetShardIteratorResponse result = getClient().getShardIterator(req.build());
            currentShardIterator = result.shardIterator();
        }
        LOG.debug("Shard Iterator is: {}", currentShardIterator);
        return currentShardIterator;
    }

    private Queue<Exchange> createExchanges(List<Record> records) {
        Queue<Exchange> exchanges = new ArrayDeque<>();
        for (Record record : records) {
            exchanges.add(getEndpoint().createExchange(record));
        }
        return exchanges;
    }

    private boolean hasSequenceNumber() {
        return !getEndpoint().getConfiguration().getSequenceNumber().isEmpty()
               && (getEndpoint().getConfiguration().getIteratorType().equals(ShardIteratorType.AFTER_SEQUENCE_NUMBER)
                   || getEndpoint().getConfiguration().getIteratorType().equals(ShardIteratorType.AT_SEQUENCE_NUMBER));
    }
}
