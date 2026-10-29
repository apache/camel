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
package org.apache.camel.component.azure.cosmosdb;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.azure.cosmos.ChangeFeedProcessor;
import com.azure.cosmos.implementation.apachecommons.lang.RandomStringUtils;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.azure.cosmosdb.client.CosmosAsyncClientWrapper;
import org.apache.camel.component.azure.cosmosdb.operations.CosmosDbContainerOperations;
import org.apache.camel.component.azure.cosmosdb.operations.CosmosDbOperationsBuilder;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.SynchronizationAdapter;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

public class CosmosDbConsumer extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(CosmosDbConsumer.class);

    // the backoff between the attempts to start the change feed processor
    private static final Duration START_RETRY_MIN_BACKOFF = Duration.ofSeconds(1);
    private static final Duration START_RETRY_MAX_BACKOFF = Duration.ofSeconds(30);

    private final Synchronization onCompletion = new ConsumerOnCompletion();
    private CosmosAsyncClientWrapper clientWrapper;
    // the change feed processor, the attempts to start it and stop() hand it over under this lock: a start that
    // completes after the consumer stopped is stopped by the attempt that started it, see doStop
    private final Object lock = new Object();
    // incremented by doStart and doStop: an attempt to start of another generation belongs to a consumer that stopped
    private long generation;
    // replaced after a failed start, see startChangeFeedProcessor
    private ChangeFeedProcessor changeFeedProcessor;
    // the start() of changeFeedProcessor has not completed yet
    private boolean starting;
    private Disposable startSubscription;
    // the number of attempts to start the change feed processor that failed (logged when it starts)
    private volatile long failedStarts;

    public CosmosDbConsumer(final CosmosDbEndpoint endpoint, final Processor processor) {
        super(endpoint, processor);
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();
        ChangeFeedProcessor processor = createChangeFeedProcessor();
        synchronized (lock) {
            changeFeedProcessor = processor;
        }
    }

    // Visible for testing.
    ChangeFeedProcessor createChangeFeedProcessor() {
        this.clientWrapper = new CosmosAsyncClientWrapper(getEndpoint().getCosmosAsyncClient());
        return getContainerOperations().captureEventsWithChangeFeed(
                getLeaseContainerOperations().getContainer(), getHostName(),
                this::onEventListener, getConfiguration().getChangeFeedProcessorOptions());
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        failedStarts = 0;
        final long startGeneration;
        synchronized (lock) {
            startGeneration = ++generation;
        }

        // start our changeFeedProcessor. The start reads the lease and feed containers, so it fails when Cosmos DB
        // cannot be reached yet, the lease container does not exist yet or a role assignment is not applied yet: try
        // again with a backoff until it starts or the consumer is stopped, or the consumer stays started without ever
        // receiving a change
        final Disposable subscription = Mono.defer(() -> startChangeFeedProcessor(startGeneration))
                // building a change feed processor blocks (block(), and with the create*IfNotExists options it creates
                // the containers), and the start fails on a thread of the Cosmos DB client or of a Reactor scheduler,
                // where blocking is not allowed: each attempt runs on a thread that may block. A failure to build the
                // processor is retried as a failure to start it
                .subscribeOn(Schedulers.boundedElastic())
                .retryWhen(Retry.backoff(Long.MAX_VALUE, START_RETRY_MIN_BACKOFF)
                        .maxBackoff(START_RETRY_MAX_BACKOFF)
                        // one line per failed attempt, as it is tried again for as long as it fails: the stack trace
                        // at DEBUG
                        .doBeforeRetry(signal -> {
                            String uri = URISupport.sanitizeUri(getEndpoint().getEndpointUri());
                            LOG.warn("Cannot start the change feed processor of {} (attempt {}), trying again: {}", uri,
                                    signal.totalRetries() + 1, signal.failure().toString());
                            LOG.debug("Cannot start the change feed processor of {}", uri, signal.failure());
                            failedStarts = signal.totalRetries() + 1;
                        }))
                .doOnSuccess(aVoid -> {
                    if (failedStarts > 0) {
                        LOG.info("Started the change feed processor of {} after {} failed attempt(s)",
                                URISupport.sanitizeUri(getEndpoint().getEndpointUri()), failedStarts);
                    }
                })
                // the retries are not limited: this is only called when the retry itself fails (for example a
                // scheduler rejects the delay before the next attempt), and the consumer then receives nothing
                .subscribe(aVoid -> {
                }, this::onErrorListener);
        synchronized (lock) {
            startSubscription = subscription;
        }
    }

    // one attempt to start the change feed processor, on a thread that may block
    private Mono<Void> startChangeFeedProcessor(final long startGeneration) {
        ChangeFeedProcessor processor;
        synchronized (lock) {
            if (startGeneration != generation) {
                return Mono.empty();
            }
            processor = changeFeedProcessor;
        }
        if (processor == null) {
            // the previous attempt failed: try again with a new change feed processor, which starts from a clean state
            // (azure-cosmos marks the partition load balancer as started before the leases are initialized, and a
            // start() after a failure there throws "Partition load balancer already started")
            try {
                processor = createChangeFeedProcessor();
            } catch (RuntimeException e) {
                if (isStale(startGeneration)) {
                    // stopped while it was built (stop interrupts this thread): not a failure to report
                    LOG.debug("Stopped while the change feed processor was built", e);
                    return Mono.empty();
                }
                throw e;
            }
        }
        synchronized (lock) {
            if (startGeneration != generation) {
                // stopped meanwhile: never started, there is nothing to stop
                return Mono.empty();
            }
            changeFeedProcessor = processor;
            starting = true;
        }
        final ChangeFeedProcessor started = processor;
        // doStop does not cancel the start(), so its end is always observed here. Until azure-cosmos (4.75) has built
        // the partition manager, isStarted() is false and stop() throws, so doStop cannot stop the processor: a
        // processor whose start ends after the consumer stopped is stopped then, if it started (also after a failure,
        // as the workers of the leases loaded before it failed run outside the returned Mono)
        return Mono.create(sink -> {
            if (isStale(startGeneration)) {
                return;
            }
            try {
                started.start().subscribe(aVoid -> {
                }, error -> onStartFailed(startGeneration, started, error, sink),
                        () -> onStarted(startGeneration, started, sink));
            } catch (RuntimeException e) {
                // the real one throws "Partition load balancer already started" at once
                onStartFailed(startGeneration, started, e, sink);
            }
        });
    }

    // the end of the start is signalled under the lock, so either before doStop (which then stops the processor) or
    // not at all (the processor is stopped here)
    private void onStarted(final long startGeneration, final ChangeFeedProcessor processor, final MonoSink<Void> sink) {
        synchronized (lock) {
            if (startGeneration == generation) {
                starting = false;
                sink.success();
                return;
            }
        }
        // the consumer stopped while it was starting, and left it to us
        LOG.debug("Stopping the change feed processor that started after the consumer stopped");
        stopIfStarted(processor);
    }

    private void onStartFailed(
            final long startGeneration, final ChangeFeedProcessor processor, final Throwable error,
            final MonoSink<Void> sink) {
        // a start that failed while it loaded the leases has started the workers of the leases loaded before, and
        // isStarted() is true: stop them, or they go on next to the next processor, or after the consumer stopped
        stopIfStarted(processor);
        synchronized (lock) {
            if (startGeneration == generation) {
                starting = false;
                if (changeFeedProcessor == processor) {
                    // the next attempt builds a new one
                    changeFeedProcessor = null;
                }
                sink.error(error);
                return;
            }
        }
        LOG.debug("The change feed processor could not start after the consumer stopped", error);
    }

    private static void stopIfStarted(final ChangeFeedProcessor processor) {
        try {
            if (processor.isStarted()) {
                processor.stop().subscribe(aVoid -> {
                }, error -> LOG.warn("Cannot stop the change feed processor", error));
            }
        } catch (IllegalStateException e) {
            // stopped meanwhile (doStop and the end of the start can both stop it)
            LOG.debug("The change feed processor is not started", e);
        } catch (RuntimeException e) {
            LOG.warn("Cannot stop the change feed processor", e);
        }
    }

    private boolean isStale(final long startGeneration) {
        synchronized (lock) {
            return startGeneration != generation;
        }
    }

    @Override
    protected void doStop() throws Exception {
        final Disposable subscription;
        ChangeFeedProcessor processor;
        ChangeFeedProcessor startingProcessor = null;
        synchronized (lock) {
            // any attempt to start that is still running is of an older generation now: it does not start a processor
            // (or stops it when its start ends, if this comes just as it calls start())
            generation++;
            subscription = startSubscription;
            startSubscription = null;
            processor = changeFeedProcessor;
            if (starting) {
                // its start() has not ended: stop it now if it can be stopped already (it is loading the leases), and
                // the attempt that started it stops it when the start ends, see startChangeFeedProcessor. A restart
                // builds a new one
                starting = false;
                changeFeedProcessor = null;
                startingProcessor = processor;
                processor = null;
            }
        }
        if (startingProcessor != null) {
            stopIfStarted(startingProcessor);
        }
        if (subscription != null) {
            // no further attempt to start
            subscription.dispose();
        }
        // stop() fails on a change feed processor that has not started, for example one that could not start yet
        if (processor != null && processor.isStarted()) {
            // we wait until it stops
            processor.stop().block();
        }

        super.doStop();
    }

    public CosmosDbConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    @Override
    public CosmosDbEndpoint getEndpoint() {
        return (CosmosDbEndpoint) super.getEndpoint();
    }

    private void onEventListener(final List<Map<String, ?>> recordList) {
        processBatch(createAzureCosmosDbExchange(recordList));
    }

    // Visible for testing.
    void processBatch(final Exchange exchange) {
        // add exchange callback
        exchange.getExchangeExtension().addOnCompletion(onCompletion);

        // Process the batch synchronously: the Azure ChangeFeedProcessor advances (checkpoints) the lease as
        // soon as this handler returns, so processing must complete before we return. On failure we rethrow,
        // so the lease is not advanced and the batch is redelivered on the next feed poll (at-least-once)
        // instead of being silently lost.
        try {
            getProcessor().process(exchange);
        } catch (Exception e) {
            exchange.setException(e);
        }

        final Exception cause = exchange.getException();
        if (cause != null) {
            throw new RuntimeCamelException("Error processing Azure CosmosDB change feed batch", cause);
        }
    }

    private Exchange createAzureCosmosDbExchange(final List<Map<String, ?>> recordList) {
        final Exchange exchange = createExchange(true);
        final Message message = exchange.getIn();

        message.setBody(recordList);

        return exchange;
    }

    private void onErrorListener(final Throwable error) {
        getExceptionHandler().handleException("Error processing exchange", error);
    }

    private CosmosDbContainerOperations getContainerOperations() {
        return CosmosDbOperationsBuilder.withClient(clientWrapper)
                .withDatabaseName(getConfiguration().getDatabaseName())
                .withCreateDatabaseIfNotExist(getConfiguration().isCreateDatabaseIfNotExists())
                .withContainerName(getConfiguration().getContainerName())
                .withContainerPartitionKeyPath(getConfiguration().getContainerPartitionKeyPath())
                .withCreateContainerIfNotExist(getConfiguration().isCreateContainerIfNotExists())
                .withThroughputProperties(getConfiguration().getThroughputProperties())
                .buildContainerOperations();
    }

    private CosmosDbContainerOperations getLeaseContainerOperations() {
        final String leaseDatabaseName;
        // Lease container need to be created with 'id' path
        final String leaseContainerPartitionKeyPath = "/id";

        if (ObjectHelper.isEmpty(getConfiguration().getLeaseDatabaseName())) {
            leaseDatabaseName = getConfiguration().getDatabaseName();
        } else {
            leaseDatabaseName = getConfiguration().getLeaseDatabaseName();
        }

        return CosmosDbOperationsBuilder.withClient(clientWrapper)
                .withDatabaseName(leaseDatabaseName)
                .withCreateDatabaseIfNotExist(getConfiguration().isCreateLeaseDatabaseIfNotExists())
                .withContainerName(getConfiguration().getLeaseContainerName())
                .withContainerPartitionKeyPath(leaseContainerPartitionKeyPath)
                .withCreateContainerIfNotExist(getConfiguration().isCreateLeaseContainerIfNotExists())
                .withThroughputProperties(getConfiguration().getThroughputProperties())
                .buildContainerOperations();
    }

    private String getHostName() {
        if (ObjectHelper.isEmpty(getConfiguration().getHostName())) {
            return RandomStringUtils.randomAlphabetic(10);
        }

        return getConfiguration().getHostName();
    }

    private class ConsumerOnCompletion extends SynchronizationAdapter {

        @Override
        public void onFailure(Exchange exchange) {
            final Exception cause = exchange.getException();
            if (cause != null) {
                getExceptionHandler().handleException("Error during processing exchange.", exchange, cause);
            }
        }
    }
}
