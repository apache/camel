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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
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
import software.amazon.awssdk.services.kinesis.model.ExpiredIteratorException;
import software.amazon.awssdk.services.kinesis.model.GetRecordsRequest;
import software.amazon.awssdk.services.kinesis.model.GetRecordsResponse;
import software.amazon.awssdk.services.kinesis.model.GetShardIteratorRequest;
import software.amazon.awssdk.services.kinesis.model.GetShardIteratorResponse;
import software.amazon.awssdk.services.kinesis.model.ListShardsRequest;
import software.amazon.awssdk.services.kinesis.model.ListShardsResponse;
import software.amazon.awssdk.services.kinesis.model.Record;
import software.amazon.awssdk.services.kinesis.model.Shard;
import software.amazon.awssdk.services.kinesis.model.ShardIteratorType;

public class Kinesis2Consumer extends ScheduledBatchPollingConsumer implements ResumeAware<ResumeStrategy> {
    private static final Logger LOG = LoggerFactory.getLogger(Kinesis2Consumer.class);

    private static final String UNIX_TIMESTAMP_MILLIS_REGEX = "^\\d{1,13}$";
    private static final String UNIX_TIMESTAMP_DOUBLE_REGEX = "^[-+]?\\d+(\\.\\d+)?([eE][-+]?\\d+)?$";

    // Sentinel stored in currentShardIterators to mark a shard as closed. A closed shard has no next
    // shard iterator (Kinesis returns null); ConcurrentHashMap forbids null values, so an empty
    // string is used instead and is treated as "no iterator" by the ObjectHelper.isEmpty checks.
    private static final String CLOSED_SHARD_ITERATOR = "";
    // The adapter of a resume strategy is one instance, shared by poll()'s parallel shards and by the routes that use
    // the same strategy bean, so one lock for all consumers. It is held only while a shard gets a new iterator.
    private static final Lock RESUME_LOCK = new ReentrantLock();

    private KinesisConnection connection;
    private ResumeStrategy resumeStrategy;

    // Written concurrently by the shards that poll() reads at the same time, so both must be thread-safe.
    private final Map<String, String> currentShardIterators = new ConcurrentHashMap<>();
    private final Set<String> warnLogged = ConcurrentHashMap.newKeySet();
    // The closed shards reported to the exception handler with shardClosed=fail: each one once while the consumer runs
    // (cleared when it stops), as a closed shard stays listed for the retention period of the stream
    private final Set<String> closedShardsReported = ConcurrentHashMap.newKeySet();
    // The sequence number of the last record routed from each shard: the position of a new shard iterator when the
    // cached one has expired. Like the cached iterators it is not cleared in doStop, and a route that is stopped and
    // started gets a new consumer.
    private final Map<String, String> lastSequenceNumbers = new ConcurrentHashMap<>();
    // The shards of which no record was routed yet, and whose first response with records was not routed at all as
    // the route was being stopped or suspended: the sequence number of the first record of that response, where the
    // next shard iterator starts
    private final Map<String, String> firstNotRoutedSequenceNumbers = new ConcurrentHashMap<>();
    // The time the consumer asked for the first LATEST iterator of each shard: where it continues a shard from which it
    // has not routed a record yet when that iterator has expired (a LATEST iterator again would skip the records stored
    // in between)
    private final Map<String, Instant> latestStartTimes = new ConcurrentHashMap<>();
    // Written by the shard monitor, read by poll(): the shards of the lists seen so far, and those of them that
    // appeared after the first list as the child of a listed shard (resharding).
    private final Set<String> knownShardIds = ConcurrentHashMap.newKeySet();
    private final Set<String> childShardIds = ConcurrentHashMap.newKeySet();
    // The failure of the last ListShards of the shard monitor, null once a ListShards succeeded. While the shards have
    // not been listed, poll() reports it, so that a consumer that cannot list the shards (no permission, no such
    // stream) is not seen healthy while it reads nothing.
    private volatile Exception shardListError;

    private volatile List<Shard> currentShardList = List.of();
    private static final String SHARD_MONITOR_EXECUTOR_NAME = "Kinesis_shard_monitor";
    private ScheduledExecutorService shardMonitorExecutor;
    // Reads the shards of a poll at the same time (maxConcurrentShards), and routes their records
    private static final String SHARD_READER_EXECUTOR_NAME = "Kinesis_shard_reader";
    private volatile ExecutorService shardReaderExecutor;

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
        return ObjectHelper.isEmpty(currentShardIterators.get(shardId)) && currentShardIterators.containsKey(shardId);
    }

    @Override
    protected int poll() throws Exception {
        var processedExchangeCount = new AtomicInteger(0);

        List<Shard> shardList = getCurrentShardList();
        if (shardList.isEmpty()) {
            // the shard monitor has not listed the shards yet, so there is nothing to read
            Exception error = shardListError;
            if (error != null) {
                throw new IllegalStateException(
                        "Cannot list the shards of stream " + getEndpoint().getConfiguration().getStreamName() + ": "
                                                + error,
                        error);
            }
            return 0;
        }

        String shardId = getEndpoint().getConfiguration().getShardId();
        if (!shardId.isEmpty()) {
            // skip if the shard is closed
            if (isShardClosed(shardId)) {
                // There was previously a shardIterator but shard is now closed
                handleClosedShard(shardId);
                return 0;
            }

            // Use the cached shard list from ShardMonitor instead of calling DescribeStream
            // on every poll. ShardMonitor uses ListShards (paginated, 100 TPS limit) vs
            // DescribeStream (unpaginated, 10 TPS limit).
            Shard shard = shardList.stream()
                    .filter(s -> s.shardId().equalsIgnoreCase(shardId))
                    .findFirst()
                    .orElse(null);

            if (shard == null) {
                LOG.warn("Configured shardId {} not found in shard list for stream {}",
                        shardId, getEndpoint().getConfiguration().getStreamName());
                return 0;
            }

            fetchAndPrepareRecordsForCamel(shard, connection, processedExchangeCount);

        } else {
            readShards(shardList, processedExchangeCount);
        }

        // okay we have some response from aws so lets mark the consumer as ready
        forceConsumerAsReady();

        return processedExchangeCount.get();
    }

    /**
     * Reads the shards of the list, up to maxConcurrentShards at the same time on the shard reader thread pool (with
     * one shard, or without that pool, one after the other on the thread of the poll), and returns once all of them
     * have been read. Each shard catches its own failure, so that the other shards are read and routed all the same,
     * and the next poll does not start while a shard of this one is still being read: the poll then fails with the
     * failure of the first shard that failed, and those of the other shards as suppressed exceptions.
     */
    private void readShards(List<Shard> shardList, AtomicInteger processedExchangeCount) throws InterruptedException {
        Queue<RuntimeException> failures = new ConcurrentLinkedQueue<>();
        ExecutorService executor = shardReaderExecutor;
        if (executor == null || shardList.size() == 1) {
            for (Shard shard : shardList) {
                readShard(shard, processedExchangeCount, failures);
            }
        } else {
            List<Future<?>> reads = new ArrayList<>(shardList.size());
            try {
                for (Shard shard : shardList) {
                    reads.add(executor.submit(() -> readShard(shard, processedExchangeCount, failures)));
                }
            } catch (RejectedExecutionException e) {
                // the consumer is being stopped
                failures.add(e);
            }
            awaitShardReads(reads, failures);
        }
        RuntimeException failure = failures.poll();
        if (failure != null) {
            for (RuntimeException other : failures) {
                if (other != failure) {
                    failure.addSuppressed(other);
                }
            }
            throw failure;
        }
    }

    private void readShard(Shard shard, AtomicInteger processedExchangeCount, Queue<RuntimeException> failures) {
        try {
            fetchAndPrepareRecordsForCamel(shard, connection, processedExchangeCount);
        } catch (RuntimeException e) {
            failures.add(e);
        }
    }

    private static void awaitShardReads(List<Future<?>> reads, Queue<RuntimeException> failures)
            throws InterruptedException {
        Error error = null;
        try {
            for (Future<?> read : reads) {
                try {
                    read.get();
                } catch (CancellationException e) {
                    // not started before the consumer was stopped
                } catch (ExecutionException e) {
                    // readShard keeps the exceptions of a shard, so this is an Error
                    if (e.getCause() instanceof Error err) {
                        if (error == null) {
                            error = err;
                        } else {
                            error.addSuppressed(err);
                        }
                    } else {
                        failures.add(RuntimeCamelException.wrapRuntimeException(e.getCause()));
                    }
                }
            }
        } catch (InterruptedException e) {
            // a forced stop interrupts the thread of the poll: interrupt the reads of the shards too, which run on
            // threads of their own
            for (Future<?> read : reads) {
                read.cancel(true);
            }
            throw e;
        }
        if (error != null) {
            throw error;
        }
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

        if (ObjectHelper.isEmpty(shardIterator)) {
            // Unable to get an iterator so shard must be closed; contribute nothing to the poll count
            return;
        }

        GetRecordsResponse result;
        try {
            result = getRecords(shardIterator, kinesisConnection);
        } catch (ExpiredIteratorException e) {
            LOG.warn("Shard iterator expired for shard {} on stream {}, requesting a fresh one",
                    shard.shardId(), getEndpoint().getConfiguration().getStreamName());
            currentShardIterators.remove(shard.shardId());
            try {
                shardIterator = getShardIterator(shard, kinesisConnection);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(ie);
            } catch (ExecutionException ee) {
                throw new RuntimeException(ee);
            }
            if (ObjectHelper.isEmpty(shardIterator)) {
                return;
            }
            result = getRecords(shardIterator, kinesisConnection);
        }

        List<Record> records = result.records();
        Queue<Exchange> exchanges = createExchanges(shard, records);
        int routed;
        try {
            routed = processBatch(CastUtils.cast(exchanges));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        processedExchangeCount.addAndGet(routed);

        if (routed < records.size()) {
            // the route is being stopped or suspended, and processBatch did not route the rest of the records
            batchCutShort(shard.shardId(), records, routed, exchanges);
            return;
        }

        // remember the last record of the batch, to continue after it when the next shard iterator has expired
        // before it is used (an iterator is valid for 5 minutes)
        if (!records.isEmpty()) {
            lastSequenceNumbers.put(shard.shardId(), records.get(records.size() - 1).sequenceNumber());
            firstNotRoutedSequenceNumbers.remove(shard.shardId());
        }
        updateShardIterator(shard, result.nextShardIterator());
    }

    /**
     * Does not move past the records of the response that were not routed, so that the next poll of this consumer
     * (after a resume) routes them: the next shard iterator of the response is not kept (a null one, of a closed shard,
     * would mark the shard closed before its last records are routed), and the next poll requests a new iterator at the
     * first record that was not routed.
     */
    private void batchCutShort(String shardId, List<Record> records, int routed, Queue<Exchange> notRouted) {
        if (routed > 0) {
            // AFTER_SEQUENCE_NUMBER of the last routed record
            lastSequenceNumbers.put(shardId, records.get(routed - 1).sequenceNumber());
            firstNotRoutedSequenceNumbers.remove(shardId);
        } else if (!lastSequenceNumbers.containsKey(shardId)) {
            // nothing routed from the shard yet: AT_SEQUENCE_NUMBER of the first record of the response, and not the
            // configured iterator type again (it would skip these records with LATEST)
            firstNotRoutedSequenceNumbers.put(shardId, records.get(0).sequenceNumber());
        }
        currentShardIterators.remove(shardId);
        LOG.debug("Routed {} of the {} records of shard {} on stream {} before the consumer stopped or was suspended",
                routed, records.size(), shardId, getEndpoint().getConfiguration().getStreamName());
        // processBatch removes the exchanges it routes: those left are never routed, and nothing else gives them back
        // to the exchange factory
        for (Exchange exchange : notRouted) {
            releaseExchange(exchange, true);
        }
    }

    private GetRecordsResponse getRecords(String shardIterator, KinesisConnection kinesisConnection) {
        GetRecordsRequest req = GetRecordsRequest
                .builder()
                .shardIterator(shardIterator)
                .limit(getEndpoint()
                        .getConfiguration()
                        .getMaxResultsPerRequest())
                .build();

        if (getEndpoint().getConfiguration().isAsyncClient()) {
            try {
                return kinesisConnection
                        .getAsyncClient(getEndpoint())
                        .getRecords(req)
                        .get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            } catch (ExecutionException e) {
                if (e.getCause() instanceof ExpiredIteratorException ex) {
                    throw ex;
                }
                throw new RuntimeException(e);
            }
        } else {
            return kinesisConnection
                    .getClient(getEndpoint())
                    .getRecords(req);
        }
    }

    private void updateShardIterator(Shard shard, String nextShardIterator) {
        // A null next shard iterator means the shard is now closed; store the sentinel rather than
        // null (ConcurrentHashMap forbids null values) so isShardClosed keeps detecting it.
        currentShardIterators.put(shard.shardId(), nextShardIterator == null ? CLOSED_SHARD_ITERATOR : nextShardIterator);
    }

    /**
     * Routes the exchanges one after the other, and stops when the route is being stopped or suspended (unless its
     * shutdownRunningTask is CompleteAllTasks): the exchanges that are not routed stay in the queue.
     */
    @Override
    public int processBatch(Queue<Object> exchanges) throws Exception {
        int processedExchanges = 0;
        while (!exchanges.isEmpty() && isBatchAllowed()) {
            final Exchange exchange = ObjectHelper.cast(Exchange.class, exchanges.poll());

            // use default consumer callback
            AsyncCallback cb = defaultConsumerCallback(exchange, true);
            getAsyncProcessor().process(exchange, cb);
            processedExchanges++;
        }
        return processedExchanges;
    }

    /**
     * Not ready before the shard monitor has listed the shards of the stream: until then a poll reads nothing.
     */
    @Override
    public boolean isConsumerReady() {
        return !getCurrentShardList().isEmpty() && super.isConsumerReady();
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

        if (ObjectHelper.isEmpty(currentShardIterators.get(shardId))) {
            if (currentShardIterators.containsKey(shardId)) {
                // There was previously a shardIterator but shard is now closed
                handleClosedShard(shardId);
                return null; // we cannot get the shard again as its closed
            }

            GetShardIteratorRequest.Builder request = GetShardIteratorRequest.builder()
                    .streamName(getEndpoint().getConfiguration().getStreamName()).shardId(shardId);

            String lastSequenceNumber = lastSequenceNumbers.get(shardId);
            if (lastSequenceNumber != null) {
                // the cached iterator of this shard has expired: continue after the last record that was routed.
                // The configured iterator type is where the consumer starts: it would route the records of the
                // shard again (TRIM_HORIZON, AT_TIMESTAMP, a sequence number) or skip the new ones (LATEST)
                request.shardIteratorType(ShardIteratorType.AFTER_SEQUENCE_NUMBER)
                        .startingSequenceNumber(lastSequenceNumber);
            } else if (firstNotRoutedSequenceNumbers.containsKey(shardId)) {
                // no record of this shard was routed, and the consumer stopped routing the first response with
                // records when the route was suspended: start at the first record of that response
                request.shardIteratorType(ShardIteratorType.AT_SEQUENCE_NUMBER)
                        .startingSequenceNumber(firstNotRoutedSequenceNumbers.get(shardId));
            } else if (latestStartTimes.containsKey(shardId)) {
                // the shard started at LATEST and the expired iterator returned no record: continue from the time of
                // that start, so the records stored since then are routed (Kinesis compares this time of the consumer's
                // clock with the arrival time it gave each record)
                request.shardIteratorType(ShardIteratorType.AT_TIMESTAMP).timestamp(latestStartTimes.get(shardId));
            } else if (childShardIds.contains(shardId)) {
                // a shard created by resharding while the consumer was reading its parent: all its records are new
                request.shardIteratorType(ShardIteratorType.TRIM_HORIZON);
            } else {
                request.shardIteratorType(getEndpoint().getConfiguration().getIteratorType());

                if (hasSequenceNumber()) {
                    request.startingSequenceNumber(getEndpoint().getConfiguration().getSequenceNumber());
                }

                if (hasMessageTimestamp()) {
                    String messageTimestamp = getEndpoint().getConfiguration().getMessageTimestamp();
                    request.timestamp(parseMessageTimestamp(messageTimestamp));
                }
            }

            resume(shardId, request);

            GetShardIteratorRequest iteratorRequest = request.build();
            Instant requestedAt = Instant.now();
            GetShardIteratorResponse result;
            if (getEndpoint().getConfiguration().isAsyncClient()) {
                try {
                    result = kinesisConnection
                            .getAsyncClient(getEndpoint())
                            .getShardIterator(iteratorRequest)
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
                        .getShardIterator(iteratorRequest);
            }
            if (iteratorRequest.shardIteratorType() == ShardIteratorType.LATEST) {
                // the iterator points after the records stored before Kinesis handled the request, which was after
                // requestedAt
                latestStartTimes.putIfAbsent(shardId, requestedAt);
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
                // reported once and then skipped, like silent: a closed shard stays in the shard list for the
                // retention period of the stream, and the consumer goes on with the other shards (the child shards of a
                // split or a merge)
                if (closedShardsReported.add(shardId)) {
                    String streamName = getEndpoint().getConfiguration().getStreamName();
                    getExceptionHandler().handleException(
                            "The shard with id=" + shardId + " on stream " + streamName + " reached CLOSE status",
                            new ReachedClosedStatusException(streamName, shardId));
                }
                break;
            default:
                throw new IllegalArgumentException("Unsupported shard closed strategy");
        }
    }

    private void resume(String shardId, GetShardIteratorRequest.Builder req) {
        if (ObjectHelper.isEmpty(resumeStrategy)) {
            return;
        }

        ResumeActionAware adapter = resumeStrategy.getAdapter(ResumeActionAware.class);
        if (ObjectHelper.isEmpty(adapter)) {
            LOG.warn("There is a resume strategy setup, but no adapter configured or the type is incorrect");

            return;
        }

        final ResumeAction action = resolveResumeAction(shardId, req);
        RESUME_LOCK.lock();
        try {
            adapter.setResumeAction(action);
            adapter.resume();
        } finally {
            RESUME_LOCK.unlock();
        }
    }

    private KinesisResumeAction resolveResumeAction(String shardId, GetShardIteratorRequest.Builder req) {
        KinesisResumeAction template = getEndpoint().getCamelContext().getRegistry()
                .lookupByNameAndType(Kinesis2Constants.RESUME_ACTION, KinesisResumeAction.class);
        KinesisResumeAction action;
        if (ObjectHelper.isEmpty(template)) {
            action = new KinesisResumeAction(req);
        } else {
            action = (KinesisResumeAction) getEndpoint().getCamelContext().getInjector()
                    .newInstance(template.getClass());
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
        exchange.getIn().setHeader(Kinesis2Constants.STREAM_NAME, getEndpoint().getConfiguration().getStreamName());
        if (ObjectHelper.isNotEmpty(dataRecord.approximateArrivalTimestamp())) {
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
        if (ObjectHelper.isEmpty(messageTimestamp)) {
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

        int maxConcurrentShards = getConfiguration().getMaxConcurrentShards();
        if (getConfiguration().getShardId().isEmpty() && maxConcurrentShards > 1) {
            // a pool managed by Camel, and not the common fork-join pool of the JVM: Camel thread names, one size per
            // endpoint, and shut down with the consumer
            this.shardReaderExecutor = getEndpoint().getCamelContext().getExecutorServiceManager()
                    .newFixedThreadPool(this, SHARD_READER_EXECUTOR_NAME, maxConcurrentShards);
        }

        if (ObjectHelper.isNotEmpty(resumeStrategy)) {
            resumeStrategy.loadCache();
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (ObjectHelper.isNotEmpty(this.shardMonitorExecutor)) {
            getEndpoint().getCamelContext().getExecutorServiceManager().shutdown(this.shardMonitorExecutor);
            this.shardMonitorExecutor = null;
        }
        ExecutorService executor = shardReaderExecutor;
        if (executor != null) {
            shardReaderExecutor = null;
            // interrupts the shards of a poll that are still being read (a forced stop), and cancels those not started
            // yet, so that the poll does not wait for them
            List<Runnable> notStarted = getEndpoint().getCamelContext().getExecutorServiceManager().shutdownNow(executor);
            if (notStarted != null) {
                for (Runnable read : notStarted) {
                    if (read instanceof Future<?> future) {
                        future.cancel(false);
                    }
                }
            }
        }
        closedShardsReported.clear();

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

    // package-private for tests, which run the monitor themselves
    class ShardMonitor implements Runnable {
        @Override
        public void run() {
            try {
                List<Shard> latestShardList = getShardList(connection);
                shardListError = null;
                if (ObjectHelper.isNotEmpty(latestShardList)) {
                    // before the list is published, so that poll() knows the child shards of the list it reads
                    trackChildShards(latestShardList);
                    setCurrentShardList(latestShardList);
                }
            } catch (Exception e) {
                LOG.warn("Exception getting latest shard list", e);
                shardListError = e;
            }
        }

        /**
         * The shards of the first list are read from the configured iterator type. A shard that appears in a later list
         * and descends from a known shard was created by a split or a merge while the consumer was reading the stream,
         * so it is read from its first record.
         */
        private void trackChildShards(List<Shard> latestShardList) {
            if (!knownShardIds.isEmpty()) {
                Set<String> parents = new HashSet<>(knownShardIds);
                boolean found = true;
                // repeated, as the list is not required to have a new shard before the shards created from it
                while (found) {
                    found = false;
                    for (Shard shard : latestShardList) {
                        if (!parents.contains(shard.shardId()) && hasParentIn(shard, parents)) {
                            childShardIds.add(shard.shardId());
                            parents.add(shard.shardId());
                            found = true;
                        }
                    }
                }
            }
            for (Shard shard : latestShardList) {
                knownShardIds.add(shard.shardId());
            }
        }

        private boolean hasParentIn(Shard shard, Set<String> parents) {
            return shard.parentShardId() != null && parents.contains(shard.parentShardId())
                    || shard.adjacentParentShardId() != null && parents.contains(shard.adjacentParentShardId());
        }

        private List<Shard> getShardList(final KinesisConnection kinesisConnection) {
            List<Shard> allShards = new ArrayList<>();
            String nextToken = null;

            do {
                ListShardsRequest.Builder requestBuilder = ListShardsRequest.builder();
                if (nextToken != null) {
                    requestBuilder.nextToken(nextToken);
                } else {
                    requestBuilder.streamName(getEndpoint().getConfiguration().getStreamName());
                }
                ListShardsRequest request = requestBuilder.build();

                ListShardsResponse response;
                if (getEndpoint().getConfiguration().isAsyncClient()) {
                    try {
                        response = kinesisConnection
                                .getAsyncClient(getEndpoint())
                                .listShards(request)
                                .get();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    } catch (ExecutionException e) {
                        throw new RuntimeException(e);
                    }
                } else {
                    response = kinesisConnection
                            .getClient(getEndpoint())
                            .listShards(request);
                }

                allShards.addAll(response.shards());
                nextToken = response.nextToken();
            } while (nextToken != null);

            return allShards;
        }
    }
}
