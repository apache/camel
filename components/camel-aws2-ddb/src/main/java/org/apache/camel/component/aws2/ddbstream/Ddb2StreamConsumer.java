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
import software.amazon.awssdk.services.dynamodb.model.ExpiredIteratorException;
import software.amazon.awssdk.services.dynamodb.model.GetRecordsRequest;
import software.amazon.awssdk.services.dynamodb.model.GetRecordsResponse;
import software.amazon.awssdk.services.dynamodb.model.Record;
import software.amazon.awssdk.services.dynamodb.streams.DynamoDbStreamsClient;

public class Ddb2StreamConsumer extends ScheduledBatchPollingConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(Ddb2StreamConsumer.class);

    private final ShardIteratorHandler shardIteratorHandler;
    private String lastSeenSequenceNumber;

    public Ddb2StreamConsumer(Ddb2StreamEndpoint endpoint, Processor processor) {
        this(endpoint, processor, new ShardIteratorHandler(endpoint));
    }

    Ddb2StreamConsumer(Ddb2StreamEndpoint endpoint, Processor processor, ShardIteratorHandler shardIteratorHandler) {
        super(endpoint, processor);
        this.shardIteratorHandler = shardIteratorHandler;
    }

    @Override
    protected int poll() throws Exception {
        GetRecordsResponse result;
        try {
            GetRecordsRequest.Builder req = GetRecordsRequest.builder().shardIterator(shardIteratorHandler.getShardIterator(null))
                    .limit(getEndpoint().getConfiguration().getMaxResultsPerRequest());
            result = getClient().getRecords(req.build());
        } catch (ExpiredIteratorException e) {
            LOG.warn("Expired Shard Iterator, attempting to resume from {}", lastSeenSequenceNumber, e);
            GetRecordsRequest.Builder req = GetRecordsRequest.builder().shardIterator(shardIteratorHandler.getShardIterator(lastSeenSequenceNumber))
                    .limit(getEndpoint().getConfiguration().getMaxResultsPerRequest());
            result = getClient().getRecords(req.build());
        }
        List<Record> records = result.records();

        Queue<Exchange> exchanges = createExchanges(records, lastSeenSequenceNumber);
        int processedExchangeCount = processBatch(CastUtils.cast(exchanges));

        shardIteratorHandler.updateShardIterator(result.nextShardIterator());
        if (!records.isEmpty()) {
            lastSeenSequenceNumber = records.get(records.size() - 1).dynamodb().sequenceNumber();
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

    private DynamoDbStreamsClient getClient() {
        return getEndpoint().getClient();
    }

    @Override
    public Ddb2StreamEndpoint getEndpoint() {
        return (Ddb2StreamEndpoint)super.getEndpoint();
    }

    private Queue<Exchange> createExchanges(List<Record> records, String lastSeenSequenceNumber) {
        Queue<Exchange> exchanges = new ArrayDeque<>();
        BigIntComparisons condition = null;
        BigInteger providedSeqNum = null;
        if (lastSeenSequenceNumber != null) {
            providedSeqNum = new BigInteger(lastSeenSequenceNumber);
            condition = BigIntComparisons.Conditions.LT;
        }
        switch (getEndpoint().getConfiguration().getIteratorType()) {
            case AFTER_SEQUENCE_NUMBER:
                condition = BigIntComparisons.Conditions.LT;
                providedSeqNum = new BigInteger(getEndpoint().getConfiguration().getSequenceNumberProvider().getSequenceNumber());
                break;
            case AT_SEQUENCE_NUMBER:
                condition = BigIntComparisons.Conditions.LTEQ;
                providedSeqNum = new BigInteger(getEndpoint().getConfiguration().getSequenceNumberProvider().getSequenceNumber());
                break;
            default:
        }
        for (Record record : records) {
            BigInteger recordSeqNum = new BigInteger(record.dynamodb().sequenceNumber());
            if (condition == null || condition.matches(providedSeqNum, recordSeqNum)) {
                exchanges.add(getEndpoint().createExchange(record));
            }
        }
        return exchanges;
    }
}
