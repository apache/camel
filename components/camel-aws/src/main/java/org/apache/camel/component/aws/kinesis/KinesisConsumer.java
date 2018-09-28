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
package org.apache.camel.component.aws.kinesis;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;

import com.amazonaws.services.kinesis.AmazonKinesis;
import com.amazonaws.services.kinesis.model.DescribeStreamRequest;
import com.amazonaws.services.kinesis.model.DescribeStreamResult;
import com.amazonaws.services.kinesis.model.GetRecordsRequest;
import com.amazonaws.services.kinesis.model.GetRecordsResult;
import com.amazonaws.services.kinesis.model.GetShardIteratorRequest;
import com.amazonaws.services.kinesis.model.GetShardIteratorResult;
import com.amazonaws.services.kinesis.model.Record;
import com.amazonaws.services.kinesis.model.Shard;
import com.amazonaws.services.kinesis.model.ShardIteratorType;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.ScheduledBatchPollingConsumer;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.ObjectHelper;

public class KinesisConsumer extends ScheduledBatchPollingConsumer {

    private String currentShardIterator;
    private boolean isShardClosed;

    public KinesisConsumer(KinesisEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    @Override
    protected int poll() throws Exception {
        GetRecordsRequest req = new GetRecordsRequest().withShardIterator(getShardItertor()).withLimit(getEndpoint().getConfiguration().getMaxResultsPerRequest());
        GetRecordsResult result = getClient().getRecords(req);

        Queue<Exchange> exchanges = createExchanges(result.getRecords());
        int processedExchangeCount = processBatch(CastUtils.cast(exchanges));

        // May cache the last successful sequence number, and pass it to the
        // getRecords request. That way, on the next poll, we start from where
        // we left off, however, I don't know what happens to subsequent
        // exchanges when an earlier echangee fails.

        currentShardIterator = result.getNextShardIterator();
        if (isShardClosed) {
            switch (getEndpoint().getConfiguration().getShardClosed()) {
            case ignore:
                log.warn("The shard {} is in closed state");
                break;
            case silent:
                break;
            case fail:
                log.info("Shard Iterator reaches CLOSE status:", getEndpoint().getConfiguration().getStreamName(), getEndpoint().getConfiguration().getShardId());
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

            log.trace("Processing exchange [{}] started.", exchange);
            getAsyncProcessor().process(exchange, new AsyncCallback() {
                @Override
                public void done(boolean doneSync) {
                    log.trace("Processing exchange [{}] done.", exchange);
                }
            });
            processedExchanges++;
        }
        return processedExchanges;
    }

    private AmazonKinesis getClient() {
        return getEndpoint().getClient();
    }

    @Override
    public KinesisEndpoint getEndpoint() {
        return (KinesisEndpoint)super.getEndpoint();
    }

    private String getShardItertor() {
        // either return a cached one or get a new one via a GetShardIterator
        // request.
        if (currentShardIterator == null) {
            String shardId;

            // If ShardId supplied use it, else choose first one
            if (!getEndpoint().getConfiguration().getShardId().isEmpty()) {
                shardId = getEndpoint().getConfiguration().getShardId();
                DescribeStreamRequest req1 = new DescribeStreamRequest().withStreamName(getEndpoint().getConfiguration().getStreamName());
                DescribeStreamResult res1 = getClient().describeStream(req1);
                Iterator it = res1.getStreamDescription().getShards().iterator();
                while (it.hasNext()) {
                    Shard shard = (Shard)it.next();
                    if (shard.getShardId().equalsIgnoreCase(getEndpoint().getConfiguration().getShardId())) {
                        if (shard.getSequenceNumberRange().getEndingSequenceNumber() == null) {
                            isShardClosed = false;
                        } else {
                            isShardClosed = true;
                        }
                    }
                }

            } else {
                DescribeStreamRequest req1 = new DescribeStreamRequest().withStreamName(getEndpoint().getConfiguration().getStreamName());
                DescribeStreamResult res1 = getClient().describeStream(req1);
                shardId = res1.getStreamDescription().getShards().get(0).getShardId();
                if (res1.getStreamDescription().getShards().get(0).getSequenceNumberRange().getEndingSequenceNumber() == null) {
                    isShardClosed = false;
                } else {
                    isShardClosed = true;
                }
            }
            log.debug("ShardId is: {}", shardId);

            GetShardIteratorRequest req = new GetShardIteratorRequest().withStreamName(getEndpoint().getConfiguration().getStreamName()).withShardId(shardId)
                .withShardIteratorType(getEndpoint().getConfiguration().getIteratorType());

            if (hasSequenceNumber()) {
                req.withStartingSequenceNumber(getEndpoint().getConfiguration().getSequenceNumber());
            }

            GetShardIteratorResult result = getClient().getShardIterator(req);
            currentShardIterator = result.getShardIterator();
        }
        log.debug("Shard Iterator is: {}", currentShardIterator);
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
