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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.aws2.kinesis.consumer.KinesisResumeAdapter;
import org.apache.camel.resume.ResumeAware;
import org.apache.camel.resume.ResumeStrategy;
import org.apache.camel.support.ScheduledBatchPollingConsumer;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.kinesis.model.DescribeStreamRequest;
import software.amazon.awssdk.services.kinesis.model.DescribeStreamResponse;
import software.amazon.awssdk.services.kinesis.model.GetRecordsRequest;
import software.amazon.awssdk.services.kinesis.model.GetRecordsResponse;
import software.amazon.awssdk.services.kinesis.model.GetShardIteratorRequest;
import software.amazon.awssdk.services.kinesis.model.GetShardIteratorResponse;
import software.amazon.awssdk.services.kinesis.model.ListShardsRequest;
import software.amazon.awssdk.services.kinesis.model.Record;
import software.amazon.awssdk.services.kinesis.model.Shard;
import software.amazon.awssdk.services.kinesis.model.ShardIteratorType;

public class Kinesis2Consumer extends ScheduledBatchPollingConsumer implements ResumeAware<ResumeStrategy> {
    private static final Logger LOG = LoggerFactory.getLogger(Kinesis2Consumer.class);

    private KinesisConnection connection;
    private boolean isShardClosed;
    private ResumeStrategy resumeStrategy;

    public Kinesis2Consumer(Kinesis2Endpoint endpoint,
                            Processor processor) {
        super(endpoint, processor);
    }

    public KinesisConnection getConnection() {
        return connection;
    }

    public void setConnection(KinesisConnection connection) {
        this.connection = connection;
    }

    @Override
    protected int poll() throws Exception {
        var processedExchangeCount = new AtomicInteger(0);

        if (!getEndpoint().getConfiguration().getShardId().isEmpty()) {
            var request = DescribeStreamRequest
                    .builder()
                    .streamName(getEndpoint().getConfiguration().getStreamName())
                    .build();
            DescribeStreamResponse response;
            if (getEndpoint().getConfiguration().isAsyncClient()) {
                try {
                    response = connection
                            .getAsyncClient(getEndpoint())
                            .describeStream(request)
                            .get();
                } catch (ExecutionException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
            } else {
                response = connection
                        .getClient(getEndpoint())
                        .describeStream(request);
            }

            var shard = response
                    .streamDescription()
                    .shards()
                    .stream()
                    .filter(shardItem -> shardItem
                            .shardId()
                            .equalsIgnoreCase(getEndpoint()
                                    .getConfiguration()
                                    .getShardId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("The shard can't be found"));

            fetchAndPrepareRecordsForCamel(shard, connection, processedExchangeCount);

        } else {
            getShardList(connection)
                    .parallelStream()
                    .forEach(shard -> {
                        fetchAndPrepareRecordsForCamel(shard, connection, processedExchangeCount);
                    });
        }

        return processedExchangeCount.get();
    }

    private void fetchAndPrepareRecordsForCamel(
            final Shard shard,
            final KinesisConnection kinesisConnection,
            AtomicInteger processedExchangeCount) {
        String shardIterator;
        try {
            shardIterator = getShardIterator(shard, kinesisConnection);
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }

        if (shardIterator == null) {
            // probably closed. Returning 0 as nothing was processed
            processedExchangeCount.set(0);
        }

        GetRecordsRequest req = GetRecordsRequest
                .builder()
                .shardIterator(shardIterator)
                .limit(getEndpoint()
                        .getConfiguration()
                        .getMaxResultsPerRequest())
                .build();

        GetRecordsResponse result;
        if (getEndpoint().getConfiguration().isAsyncClient()) {
            try {
                result = kinesisConnection
                        .getAsyncClient(getEndpoint())
                        .getRecords(req)
                        .get();
            } catch (ExecutionException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        } else {
            result = kinesisConnection
                    .getClient(getEndpoint())
                    .getRecords(req);
        }

        try {
            Queue<Exchange> exchanges = createExchanges(result.records());
            processedExchangeCount.getAndSet(processBatch(CastUtils.cast(exchanges)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // May cache the last successful sequence number, and pass it to the
        // getRecords request. That way, on the next poll, we start from where
        // we left off, however, I don't know what happens to subsequent
        // exchanges when an earlier exchange fails.

        var currentShardIterator = result.nextShardIterator();
        if (isShardClosed) {
            switch (getEndpoint().getConfiguration().getShardClosed()) {
                case ignore:
                    LOG.warn("The shard {} is in closed state", currentShardIterator);
                    break;
                case silent:
                    break;
                case fail:
                    LOG.info("Shard Iterator reaches CLOSE status:{} {}",
                            getEndpoint().getConfiguration().getStreamName(),
                            getEndpoint().getConfiguration().getShardId());
                    throw new IllegalStateException(
                            new ReachedClosedStatusException(
                                    getEndpoint().getConfiguration().getStreamName(), shard.shardId()));
                default:
                    throw new IllegalArgumentException("Unsupported shard closed strategy");
            }
        }
    }

    @Override
    public int processBatch(Queue<Object> exchanges) throws Exception {
        int processedExchanges = 0;
        while (!exchanges.isEmpty()) {
            final Exchange exchange = ObjectHelper.cast(Exchange.class, exchanges.poll());

            // use default consumer callback
            AsyncCallback cb = defaultConsumerCallback(exchange, true);
            getAsyncProcessor().process(exchange, cb);
            processedExchanges++;
        }
        return processedExchanges;
    }

    @Override
    public Kinesis2Endpoint getEndpoint() {
        return (Kinesis2Endpoint) super.getEndpoint();
    }

    private String getShardIterator(
            final Shard shard,
            final KinesisConnection kinesisConnection)
            throws ExecutionException, InterruptedException {

        var shardId = shard.shardId();
        isShardClosed = shard.sequenceNumberRange().endingSequenceNumber() != null;
        LOG.debug("ShardId is: {}", shardId);

        GetShardIteratorRequest.Builder request = GetShardIteratorRequest.builder()
                .streamName(getEndpoint().getConfiguration().getStreamName()).shardId(shardId)
                .shardIteratorType(getEndpoint().getConfiguration().getIteratorType());

        if (hasSequenceNumber()) {
            request.startingSequenceNumber(getEndpoint().getConfiguration().getSequenceNumber());
        }

        resume(request);

        GetShardIteratorResponse result;
        if (getEndpoint().getConfiguration().isAsyncClient()) {
            try {
                result = kinesisConnection
                        .getAsyncClient(getEndpoint())
                        .getShardIterator(request.build())
                        .get();
            } catch (ExecutionException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        } else {
            result = kinesisConnection
                    .getClient(getEndpoint())
                    .getShardIterator(request.build());
        }

        return result.shardIterator();
    }

    private void resume(GetShardIteratorRequest.Builder req) {
        if (resumeStrategy == null) {
            return;
        }

        KinesisResumeAdapter adapter = resumeStrategy.getAdapter(KinesisResumeAdapter.class);
        if (adapter == null) {
            LOG.warn("There is a resume strategy setup, but no adapter configured or the type is incorrect");

            return;
        }

        adapter.setRequestBuilder(req);
        adapter.setStreamName(getEndpoint().getConfiguration().getStreamName());
        adapter.resume();
    }

    private Queue<Exchange> createExchanges(List<Record> records) {
        Queue<Exchange> exchanges = new ArrayDeque<>();
        for (Record dataRecord : records) {
            exchanges.add(createExchange(dataRecord));
        }
        return exchanges;
    }

    protected Exchange createExchange(Record dataRecord) {
        Exchange exchange = createExchange(true);
        exchange.getIn().setBody(dataRecord.data().asInputStream());
        exchange.getIn().setHeader(Kinesis2Constants.APPROX_ARRIVAL_TIME, dataRecord.approximateArrivalTimestamp());
        exchange.getIn().setHeader(Kinesis2Constants.PARTITION_KEY, dataRecord.partitionKey());
        exchange.getIn().setHeader(Kinesis2Constants.SEQUENCE_NUMBER, dataRecord.sequenceNumber());
        if (dataRecord.approximateArrivalTimestamp() != null) {
            long ts = dataRecord.approximateArrivalTimestamp().getEpochSecond() * 1000;
            exchange.getIn().setHeader(Kinesis2Constants.MESSAGE_TIMESTAMP, ts);
        }
        return exchange;
    }

    @Override
    public void setResumeStrategy(ResumeStrategy resumeStrategy) {
        this.resumeStrategy = resumeStrategy;
    }

    @Override
    public ResumeStrategy getResumeStrategy() {
        return resumeStrategy;
    }

    private boolean hasSequenceNumber() {
        return !getEndpoint().getConfiguration().getSequenceNumber().isEmpty()
                && (getEndpoint().getConfiguration().getIteratorType().equals(ShardIteratorType.AFTER_SEQUENCE_NUMBER)
                        || getEndpoint().getConfiguration().getIteratorType().equals(ShardIteratorType.AT_SEQUENCE_NUMBER));
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        ObjectHelper.notNull(connection, "connection", this);

        if (resumeStrategy != null) {
            resumeStrategy.loadCache();
        }
    }

    protected Kinesis2Configuration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    private List<Shard> getShardList(final KinesisConnection kinesisConnection) {
        var request = ListShardsRequest
                .builder()
                .streamName(getEndpoint().getConfiguration().getStreamName())
                .build();

        List<Shard> shardList;
        if (getEndpoint().getConfiguration().isAsyncClient()) {
            try {
                shardList = kinesisConnection
                        .getAsyncClient(getEndpoint())
                        .listShards(request)
                        .get()
                        .shards();
            } catch (ExecutionException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        } else {
            shardList = kinesisConnection
                    .getClient(getEndpoint())
                    .listShards(request)
                    .shards();
        }

        return shardList;
    }

}
