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

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Processor;
import org.apache.camel.support.ScheduledBatchPollingConsumer;
import org.apache.camel.util.CastUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.dynamodb.model.ExpiredIteratorException;
import software.amazon.awssdk.services.dynamodb.model.GetRecordsRequest;
import software.amazon.awssdk.services.dynamodb.model.GetRecordsResponse;
import software.amazon.awssdk.services.dynamodb.model.Record;

public class Ddb2StreamConsumer extends ScheduledBatchPollingConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(Ddb2StreamConsumer.class);

    private final ShardIteratorHandler shardIteratorHandler;
    private final Map<String, String> lastSeenSequenceNumbers = new HashMap<>();

    public Ddb2StreamConsumer(Ddb2StreamEndpoint endpoint, Processor processor) {
        this(endpoint, processor, new ShardIteratorHandler(endpoint));
    }

    Ddb2StreamConsumer(Ddb2StreamEndpoint endpoint, Processor processor, ShardIteratorHandler shardIteratorHandler) {
        super(endpoint, processor);
        this.shardIteratorHandler = shardIteratorHandler;
    }

    @Override
    protected int poll() throws Exception {
        int processedExchangeCount = 0;
        Map<String, String> shardIterators = shardIteratorHandler.getShardIterators();
        for (Entry<String, String> shardIteratorEntry : shardIterators.entrySet()) {
            int limitPerRecordsRequest = Math.max(1,
                    getEndpoint().getConfiguration().getMaxResultsPerRequest() / shardIterators.size());
            String shardId = shardIteratorEntry.getKey();
            String shardIterator = shardIteratorEntry.getValue();
            GetRecordsResponse result;
            try {
                GetRecordsRequest req = GetRecordsRequest.builder()
                        .shardIterator(shardIterator)
                        .limit(limitPerRecordsRequest)
                        .build();
                result = getEndpoint().getClient().getRecords(req);
            } catch (ExpiredIteratorException e) {
                String lastSeenSequenceNumber = lastSeenSequenceNumbers.get(shardId);
                LOG.warn("Expired Shard Iterator, attempting to resume from {}", lastSeenSequenceNumber, e);
                GetRecordsRequest req = GetRecordsRequest.builder()
                        .shardIterator(shardIteratorHandler.requestFreshShardIterator(shardId, lastSeenSequenceNumber))
                        .limit(limitPerRecordsRequest)
                        .build();
                result = getEndpoint().getClient().getRecords(req);
            }
            List<Record> records = result.records();
            Queue<Exchange> exchanges = new ArrayDeque<>();
            for (Record record : records) {
                exchanges.add(createExchange(record));
            }
            processedExchangeCount += processBatch(CastUtils.cast(exchanges));

            shardIteratorHandler.updateShardIterator(shardId, result.nextShardIterator());
            if (!records.isEmpty()) {
                lastSeenSequenceNumbers.put(shardId, records.get(records.size() - 1).dynamodb().sequenceNumber());
            }
        }
        return processedExchangeCount;
    }

    @Override
    public int processBatch(Queue<Object> exchanges) throws Exception {
        int total = exchanges.size();
        int answer = 0;

        for (int index = 0; index < total && isBatchAllowed(); index++) {
            // only loop if we are started (allowed to run)
            // use poll to remove the head so it does not consume memory even
            // after we have processed it
            Exchange exchange = (Exchange) exchanges.poll();
            // add current index and total as properties
            exchange.setProperty(ExchangePropertyKey.BATCH_INDEX, index);
            exchange.setProperty(ExchangePropertyKey.BATCH_SIZE, total);
            exchange.setProperty(ExchangePropertyKey.BATCH_COMPLETE, index == total - 1);

            // update pending number of exchanges
            pendingExchanges = total - index - 1;

            // use default consumer callback
            AsyncCallback cb = defaultConsumerCallback(exchange, true);
            getAsyncProcessor().process(exchange, cb);
            answer++;
        }

        return answer;
    }

    protected Exchange createExchange(Record record) {
        Exchange ex = createExchange(true);
        ex.getIn().setBody(record, Record.class);
        return ex;
    }

    @Override
    public Ddb2StreamEndpoint getEndpoint() {
        return (Ddb2StreamEndpoint) super.getEndpoint();
    }
}
