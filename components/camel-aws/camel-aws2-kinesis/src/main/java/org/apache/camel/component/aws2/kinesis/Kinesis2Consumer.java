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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.aws2.kinesis.consumer.KinesisResumeAction;
import org.apache.camel.resume.ResumeAction;
import org.apache.camel.resume.ResumeActionAware;
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

    private static final String UNIX_TIMESTAMP_MILLIS_REGEX = "^\\d{1,13}$";
    private static final String UNIX_TIMESTAMP_DOUBLE_REGEX = "^[-+]?\\d+(\\.\\d+)?([eE][-+]?\\d+)?$";

    private KinesisConnection connection;
    private ResumeStrategy resumeStrategy;

    private final Map<String, String> currentShardIterators = new java.util.HashMap<>();
    private final Set<String> warnLogged = new HashSet<>();

    private volatile List<Shard> currentShardList = List.of();
    private static final String SHARD_MONITOR_EXECUTOR_NAME = "Kinesis_shard_monitor";
    private ScheduledExecutorService shardMonitorExecutor;

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

    public boolean isShardClosed(String shardId) {
        return currentShardIterators.get(shardId) == null && currentShardIterators.containsKey(shardId);
    }

    @Override
    protected int poll() throws Exception {
        var processedExchangeCount = new AtomicInteger(0);

        String shardId = getEndpoint().getConfiguration().getShardId();
        if (!shardId.isEmpty()) {
            // skip if the shard is closed
            if (isShardClosed(shardId)) {
                // There was previously a shardIterator but shard is now closed
                handleClosedShard(shardId);
                return 0;
            }

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
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return 0;
                } catch (ExecutionException e) {
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
            getCurrentShardList()
                    .parallelStream()
                    .forEach(shard -> fetchAndPrepareRecordsForCamel(shard, connection, processedExchangeCount));
        }

        // okay we have some response from aws so lets mark the consumer as ready
        forceConsumerAsReady();

        return processedExchangeCount.get();
    }

    private void fetchAndPrepareRecordsForCamel(
            final Shard shard,
            final KinesisConnection kinesisConnection,
            AtomicInteger processedExchangeCount) {
        String shardIterator;
        try {
            shardIterator = getShardIterator(shard, kinesisConnection);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }

        if (shardIterator == null) {
            // Unable to get an interator so shard must be closed
            processedExchangeCount.set(0);
            return;
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
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            }
        } else {
            result = kinesisConnection
                    .getClient(getEndpoint())
                    .getRecords(req);
        }

        try {
            Queue<Exchange> exchanges = createExchanges(shard, result.records());
            processedExchangeCount.getAndSet(processBatch(CastUtils.cast(exchanges)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // May cache the last successful sequence number, and pass it to the
        // getRecords request. That way, on the next poll, we start from where
        // we left off, however, I don't know what happens to subsequent
        // exchanges when an earlier exchange fails.
        updateShardIterator(shard, result.nextShardIterator());
    }

    private void updateShardIterator(Shard shard, String nextShardIterator) {
        currentShardIterators.put(shard.shardId(), nextShardIterator);
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
        // either return a cached one or get a new one via a GetShardIterator
        // request.

        var shardId = shard.shardId();

        if (currentShardIterators.get(shardId) == null) {
            if (currentShardIterators.containsKey(shardId)) {
                // There was previously a shardIterator but shard is now closed
                handleClosedShard(shardId);
                return null; // we cannot get the shard again as its closed
            }

            GetShardIteratorRequest.Builder request = GetShardIteratorRequest.builder()
                    .streamName(getEndpoint().getConfiguration().getStreamName()).shardId(shardId)
                    .shardIteratorType(getEndpoint().getConfiguration().getIteratorType());

            if (hasSequenceNumber()) {
                request.startingSequenceNumber(getEndpoint().getConfiguration().getSequenceNumber());
            }

            if (hasMessageTimestamp()) {
                String messageTimestamp = getEndpoint().getConfiguration().getMessageTimestamp();
                request.timestamp(parseMessageTimestamp(messageTimestamp));
            }

            resume(shardId, request);

            GetShardIteratorResponse result;
            if (getEndpoint().getConfiguration().isAsyncClient()) {
                try {
                    result = kinesisConnection
                            .getAsyncClient(getEndpoint())
                            .getShardIterator(request.build())
                            .get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                } catch (ExecutionException e) {
                    throw new RuntimeException(e);
                }
            } else {
                result = kinesisConnection
                        .getClient(getEndpoint())
                        .getShardIterator(request.build());
            }

            currentShardIterators.put(shardId, result.shardIterator());
            LOG.debug("Obtained new ShardIterator {} for shard {} on stream {}", result.shardIterator(), shardId,
                    getEndpoint().getConfiguration().getStreamName());
        }

        return currentShardIterators.get(shardId);
    }

    private void handleClosedShard(String shardId) {
        switch (getEndpoint().getConfiguration().getShardClosed()) {
            case ignore:
                if (warnLogged.add(shardId)) {
                    LOG.warn("The shard with id={} on stream {} reached CLOSE status",
                            shardId, getEndpoint().getConfiguration().getStreamName());
                }
                break;
            case silent:
                break;
            case fail:
                LOG.info("The shard with id={} on stream {} reached CLOSE status",
                        shardId, getEndpoint().getConfiguration().getStreamName());
                throw new IllegalStateException(
                        new ReachedClosedStatusException(
                                getEndpoint().getConfiguration().getStreamName(), shardId));
            default:
                throw new IllegalArgumentException("Unsupported shard closed strategy");
        }
    }

    private void resume(String shardId, GetShardIteratorRequest.Builder req) {
        if (resumeStrategy == null) {
            return;
        }

        ResumeActionAware adapter = resumeStrategy.getAdapter(ResumeActionAware.class);
        if (adapter == null) {
            LOG.warn("There is a resume strategy setup, but no adapter configured or the type is incorrect");

            return;
        }

        final ResumeAction action = resolveResumeAction(shardId, req);
        adapter.setResumeAction(action);
        adapter.resume();
    }

    private KinesisResumeAction resolveResumeAction(String shardId, GetShardIteratorRequest.Builder req) {
        KinesisResumeAction action
                = getEndpoint().getCamelContext().getRegistry().lookupByNameAndType(Kinesis2Constants.RESUME_ACTION,
                        KinesisResumeAction.class);
        if (action == null) {
            action = new KinesisResumeAction(req);
        } else {
            action.setBuilder(req);
        }

        action.setShardId(shardId);
        action.setStreamName(getEndpoint().getConfiguration().getStreamName());
        return action;
    }

    private Queue<Exchange> createExchanges(Shard shard, List<Record> records) {
        Queue<Exchange> exchanges = new ArrayDeque<>();
        for (Record dataRecord : records) {
            exchanges.add(createExchange(shard, dataRecord));
        }
        return exchanges;
    }

    protected Exchange createExchange(Shard shard, Record dataRecord) {
        LOG.debug("Received Kinesis record with partition_key={}", dataRecord.partitionKey());
        Exchange exchange = createExchange(true);
        exchange.getIn().setBody(dataRecord.data().asByteArray());
        exchange.getIn().setHeader(Kinesis2Constants.APPROX_ARRIVAL_TIME, dataRecord.approximateArrivalTimestamp());
        exchange.getIn().setHeader(Kinesis2Constants.PARTITION_KEY, dataRecord.partitionKey());
        exchange.getIn().setHeader(Kinesis2Constants.SEQUENCE_NUMBER, dataRecord.sequenceNumber());
        exchange.getIn().setHeader(Kinesis2Constants.SHARD_ID, shard.shardId());
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

    private boolean hasMessageTimestamp() {
        return !getEndpoint().getConfiguration().getMessageTimestamp().isEmpty()
                && getEndpoint().getConfiguration().getIteratorType().equals(ShardIteratorType.AT_TIMESTAMP);
    }

    private Instant parseMessageTimestamp(String messageTimestamp) {
        if (messageTimestamp == null) {
            throw new IllegalArgumentException("Timestamp can't be null");
        }
        // Milliseconds format
        if (messageTimestamp.matches(UNIX_TIMESTAMP_MILLIS_REGEX)) {
            long epochMilli = Long.parseLong(messageTimestamp);
            return Instant.ofEpochMilli(epochMilli);
        }
        // Double format of seconds with fractional part. (1732882967.573, 1.732882967573E9 etc.)
        if (messageTimestamp.matches(UNIX_TIMESTAMP_DOUBLE_REGEX)) {
            // Using BigDecimal to better precision
            BigDecimal decimalTime = new BigDecimal(messageTimestamp);
            long seconds = decimalTime.longValue();
            BigDecimal fractionalPart = decimalTime.subtract(new BigDecimal(seconds));
            int nanos = fractionalPart.multiply(BigDecimal.valueOf(1_000_000_000)).intValue();
            return Instant.ofEpochSecond(seconds, nanos);
        }
        // ISO 8601 format
        try {
            return Instant.parse(messageTimestamp);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid timestamp format: " + messageTimestamp);
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        ObjectHelper.notNull(connection, "connection", this);
        this.shardMonitorExecutor = getEndpoint().getCamelContext().getExecutorServiceManager()
                .newSingleThreadScheduledExecutor(this, SHARD_MONITOR_EXECUTOR_NAME);
        this.shardMonitorExecutor.scheduleAtFixedRate(new ShardMonitor(),
                0, getConfiguration().getShardMonitorInterval(), TimeUnit.MILLISECONDS);

        if (resumeStrategy != null) {
            resumeStrategy.loadCache();
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (this.shardMonitorExecutor != null) {
            getEndpoint().getCamelContext().getExecutorServiceManager().shutdown(this.shardMonitorExecutor);
            this.shardMonitorExecutor = null;
        }

        super.doStop();
    }

    protected Kinesis2Configuration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    protected List<Shard> getCurrentShardList() {
        return this.currentShardList;
    }

    private void setCurrentShardList(List<Shard> latestShardList) {
        this.currentShardList = List.copyOf(latestShardList);
    }

    private class ShardMonitor implements Runnable {
        @Override
        public void run() {
            try {
                List<Shard> latestShardList = getShardList(connection);
                if (latestShardList != null) {
                    setCurrentShardList(latestShardList);
                }
            } catch (Exception e) {
                LOG.warn("Exception getting latest shard list", e);
            }
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
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                } catch (ExecutionException e) {
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
}
