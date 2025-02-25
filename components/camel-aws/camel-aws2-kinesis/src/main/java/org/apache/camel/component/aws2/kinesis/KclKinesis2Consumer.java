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

import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClientBuilder;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClientBuilder;
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient;
import software.amazon.kinesis.common.ConfigsBuilder;
import software.amazon.kinesis.coordinator.Scheduler;
import software.amazon.kinesis.exceptions.InvalidStateException;
import software.amazon.kinesis.exceptions.ShutdownException;
import software.amazon.kinesis.lifecycle.events.InitializationInput;
import software.amazon.kinesis.lifecycle.events.LeaseLostInput;
import software.amazon.kinesis.lifecycle.events.ProcessRecordsInput;
import software.amazon.kinesis.lifecycle.events.ShardEndedInput;
import software.amazon.kinesis.lifecycle.events.ShutdownRequestedInput;
import software.amazon.kinesis.metrics.MetricsLevel;
import software.amazon.kinesis.metrics.NullMetricsFactory;
import software.amazon.kinesis.processor.ShardRecordProcessor;
import software.amazon.kinesis.processor.ShardRecordProcessorFactory;
import software.amazon.kinesis.retrieval.KinesisClientRecord;
import software.amazon.kinesis.retrieval.polling.PollingConfig;

public class KclKinesis2Consumer extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(KclKinesis2Consumer.class);

    private final Processor processor;
    private ExecutorService executor;
    private Scheduler schedulerKcl;

    public KclKinesis2Consumer(Kinesis2Endpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.processor = processor;
    }

    @Override
    public Kinesis2Endpoint getEndpoint() {
        return (Kinesis2Endpoint) super.getEndpoint();
    }

    private class CamelKinesisRecordProcessorFactory implements ShardRecordProcessorFactory {
        public ShardRecordProcessor shardRecordProcessor() {
            return new CamelKinesisRecordProcessor(getEndpoint());
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        LOG.debug("Starting KCL Consumer");
        DynamoDbAsyncClient dynamoDbAsyncClient = null;
        CloudWatchAsyncClient cloudWatchAsyncClient = null;
        KinesisAsyncClient kinesisAsyncClient = getEndpoint().getConfiguration().getAmazonKinesisAsyncClient();
        Kinesis2Configuration configuration = getEndpoint().getConfiguration();
        if (ObjectHelper.isEmpty(getEndpoint().getConfiguration().getDynamoDbAsyncClient())) {
            DynamoDbAsyncClientBuilder clientBuilder = DynamoDbAsyncClient.builder();
            if (ObjectHelper.isNotEmpty(configuration.getAccessKey())
                    && ObjectHelper.isNotEmpty(configuration.getSecretKey())) {
                clientBuilder = clientBuilder.credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(configuration.getAccessKey(), configuration.getSecretKey())));
            } else if (ObjectHelper.isNotEmpty(configuration.getProfileCredentialsName())) {
                clientBuilder = clientBuilder
                        .credentialsProvider(
                                ProfileCredentialsProvider.create(configuration.getProfileCredentialsName()));
            } else if (ObjectHelper.isNotEmpty(configuration.getAccessKey())
                    && ObjectHelper.isNotEmpty(configuration.getSecretKey())
                    && ObjectHelper.isNotEmpty(configuration.getSessionToken())) {
                clientBuilder = clientBuilder.credentialsProvider(StaticCredentialsProvider
                        .create(AwsSessionCredentials.create(configuration.getAccessKey(), configuration.getSecretKey(),
                                configuration.getSessionToken())));
            }
            if (ObjectHelper.isNotEmpty(configuration.getRegion())) {
                clientBuilder = clientBuilder.region(Region.of(configuration.getRegion()));
            }
            dynamoDbAsyncClient
                    = clientBuilder.build();
        } else {
            dynamoDbAsyncClient = getEndpoint().getConfiguration().getDynamoDbAsyncClient();
        }
        if (ObjectHelper.isEmpty(getEndpoint().getConfiguration().getCloudWatchAsyncClient())) {
            CloudWatchAsyncClientBuilder clientBuilder = CloudWatchAsyncClient.builder();
            if (ObjectHelper.isNotEmpty(configuration.getAccessKey())
                    && ObjectHelper.isNotEmpty(configuration.getSecretKey())) {
                clientBuilder = clientBuilder.credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(configuration.getAccessKey(), configuration.getSecretKey())));
            } else if (ObjectHelper.isNotEmpty(configuration.getProfileCredentialsName())) {
                clientBuilder = clientBuilder
                        .credentialsProvider(
                                ProfileCredentialsProvider.create(configuration.getProfileCredentialsName()));
            } else if (ObjectHelper.isNotEmpty(configuration.getAccessKey())
                    && ObjectHelper.isNotEmpty(configuration.getSecretKey())
                    && ObjectHelper.isNotEmpty(configuration.getSessionToken())) {
                clientBuilder = clientBuilder.credentialsProvider(StaticCredentialsProvider
                        .create(AwsSessionCredentials.create(configuration.getAccessKey(), configuration.getSecretKey(),
                                configuration.getSessionToken())));
            }
            if (ObjectHelper.isNotEmpty(configuration.getRegion())) {
                clientBuilder = clientBuilder.region(Region.of(configuration.getRegion()));
            }
            cloudWatchAsyncClient = clientBuilder.build();
        } else {
            cloudWatchAsyncClient = getEndpoint().getConfiguration().getCloudWatchAsyncClient();
        }
        this.executor = this.getEndpoint().createExecutor();
        this.executor.submit(new KclKinesisConsumingTask(
                configuration.getStreamName(), configuration.getApplicationName(), kinesisAsyncClient, dynamoDbAsyncClient,
                cloudWatchAsyncClient, configuration.isKclDisableCloudwatchMetricsExport()));
    }

    @Override
    protected void doStop() throws Exception {
        LOG.debug("Stopping KCL Consumer");
        if (this.executor != null) {
            if (this.getEndpoint() != null && this.getEndpoint().getCamelContext() != null) {
                this.getEndpoint().getCamelContext().getExecutorServiceManager().shutdownNow(this.executor);
            } else {
                this.executor.shutdownNow();
            }
        }
        if (this.schedulerKcl != null) {
            Future<Boolean> gracefulShutdownFuture = schedulerKcl.startGracefulShutdown();
            LOG.info("Waiting up to 20 seconds for scheduler shutdown to complete.");
            try {
                gracefulShutdownFuture.get(20, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                LOG.debug("Interrupted while waiting for graceful shutdown. Continuing.");
            } catch (ExecutionException e) {
                LOG.debug("Exception while executing graceful shutdown.", e);
            } catch (TimeoutException e) {
                LOG.debug("Timeout while waiting for shutdown.  Scheduler may not have exited.");
            }
            LOG.debug("Completed, shutting down now.");
        }
        this.executor = null;
        super.doStop();
    }

    class CamelKinesisRecordProcessor implements ShardRecordProcessor {

        private static final Logger LOG = LoggerFactory.getLogger(CamelKinesisRecordProcessor.class);

        private String shardId;
        private Kinesis2Endpoint endpoint;

        public CamelKinesisRecordProcessor(Kinesis2Endpoint endpoint) {
            this.endpoint = endpoint;
        }

        @Override
        public void initialize(InitializationInput initializationInput) {
            shardId = initializationInput.shardId();
            LOG.debug("Initializing @ Sequence: {}", initializationInput.extendedSequenceNumber());
        }

        @Override
        public void processRecords(ProcessRecordsInput processRecordsInput) {
            try {
                LOG.debug("Processing {} record(s)", processRecordsInput.records().size());
                processRecordsInput.records()
                        .forEach(r -> {
                            try {
                                processor.process(createExchange(r, shardId));
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        });
            } catch (Throwable t) {
                LOG.error("Caught throwable while processing records. Aborting.");
            }
        }

        @Override
        public void leaseLost(LeaseLostInput leaseLostInput) {
            LOG.debug("Lost lease, so terminating.");
        }

        @Override
        public void shardEnded(ShardEndedInput shardEndedInput) {
            try {
                LOG.debug("Reached shard end checkpointing.");
                shardEndedInput.checkpointer().checkpoint();
            } catch (ShutdownException | InvalidStateException e) {
                LOG.error("Exception while checkpointing at shard end. Giving up.", e);
            }
        }

        @Override
        public void shutdownRequested(ShutdownRequestedInput shutdownRequestedInput) {
            try {
                LOG.debug("Scheduler is shutting down, checkpointing.");
                shutdownRequestedInput.checkpointer().checkpoint();
            } catch (ShutdownException | InvalidStateException e) {
                LOG.error("Exception while checkpointing at requested shutdown. Giving up.", e);
            }
        }

        protected Exchange createExchange(KinesisClientRecord dataRecord, String shardId) {
            Exchange exchange = endpoint.createExchange();
            exchange.getMessage().setBody(dataRecord.data());
            exchange.getMessage().setHeader(Kinesis2Constants.APPROX_ARRIVAL_TIME, dataRecord.approximateArrivalTimestamp());
            exchange.getMessage().setHeader(Kinesis2Constants.PARTITION_KEY, dataRecord.partitionKey());
            exchange.getMessage().setHeader(Kinesis2Constants.SEQUENCE_NUMBER, dataRecord.sequenceNumber());
            exchange.getMessage().setHeader(Kinesis2Constants.SHARD_ID, shardId);
            if (dataRecord.approximateArrivalTimestamp() != null) {
                long ts = dataRecord.approximateArrivalTimestamp().getEpochSecond() * 1000;
                exchange.getMessage().setHeader(Kinesis2Constants.MESSAGE_TIMESTAMP, ts);
            }
            return exchange;
        }
    }

    class KclKinesisConsumingTask implements Runnable {

        private final KinesisAsyncClient kinesisAsyncClient;
        private final DynamoDbAsyncClient dynamoDbAsyncClient;
        private final CloudWatchAsyncClient cloudWatchAsyncClient;
        private final String streamName;
        private final String applicationName;
        private final boolean disableMetricsExport;

        KclKinesisConsumingTask(String streamName, String applicationName, KinesisAsyncClient kinesisAsyncClient,
                                DynamoDbAsyncClient dynamoDbAsyncClient, CloudWatchAsyncClient cloudWatchAsyncClient,
                                boolean disableMetricsExport) {
            this.cloudWatchAsyncClient = cloudWatchAsyncClient;
            this.dynamoDbAsyncClient = dynamoDbAsyncClient;
            this.kinesisAsyncClient = kinesisAsyncClient;
            this.streamName = streamName;
            this.applicationName = applicationName != null ? applicationName : streamName;
            this.disableMetricsExport = disableMetricsExport;
        }

        @Override
        public void run() {
            try {
                ConfigsBuilder configsBuilder = new ConfigsBuilder(
                        streamName, applicationName,
                        kinesisAsyncClient, dynamoDbAsyncClient, cloudWatchAsyncClient,
                        "KclKinesisConsumingTask-" + UUID.randomUUID().toString(),
                        new CamelKinesisRecordProcessorFactory());

                Scheduler scheduler = new Scheduler(
                        configsBuilder.checkpointConfig(),
                        configsBuilder.coordinatorConfig(),
                        configsBuilder.leaseManagementConfig(),
                        configsBuilder.lifecycleConfig(),
                        disableMetricsExport
                                ? configsBuilder.metricsConfig().metricsLevel(MetricsLevel.NONE)
                                        .metricsFactory(new NullMetricsFactory())
                                : configsBuilder.metricsConfig(),
                        configsBuilder.processorConfig(),
                        configsBuilder.retrievalConfig().retrievalSpecificConfig(new PollingConfig(
                                getEndpoint().getConfiguration().getStreamName(), kinesisAsyncClient)));

                schedulerKcl = scheduler;
                Thread schedulerThread = new Thread(scheduler);
                schedulerThread.start();
            } catch (final Exception e) {
                KclKinesis2Consumer.this.getExceptionHandler().handleException("Error during processing", e);
            }

        }
    }
}
