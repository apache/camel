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
package org.apache.camel.component.kafka;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.camel.Processor;
import org.apache.camel.Suspendable;
import org.apache.camel.component.kafka.consumer.errorhandler.KafkaConsumerListener;
import org.apache.camel.health.HealthCheckAware;
import org.apache.camel.health.HealthCheckHelper;
import org.apache.camel.health.HealthCheckRepository;
import org.apache.camel.resume.ConsumerListenerAware;
import org.apache.camel.resume.ResumeAware;
import org.apache.camel.resume.ResumeStrategy;
import org.apache.camel.spi.StateRepository;
import org.apache.camel.support.BridgeExceptionHandlerToErrorHandler;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.kafka.clients.ClientDnsLookup;
import org.apache.kafka.clients.ClientUtils;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KafkaConsumer extends DefaultConsumer
        implements ResumeAware<ResumeStrategy>, HealthCheckAware, ConsumerListenerAware<KafkaConsumerListener>,
        Suspendable {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaConsumer.class);

    protected ExecutorService executor;
    private final KafkaEndpoint endpoint;
    private KafkaConsumerHealthCheck consumerHealthCheck;
    private HealthCheckRepository healthCheckRepository;
    // This list helps to work around the infinite loop of KAFKA-1894
    private final List<KafkaFetchRecords> tasks = new ArrayList<>();
    private volatile boolean stopOffsetRepo;
    private ResumeStrategy resumeStrategy;
    private KafkaConsumerListener consumerListener;

    public KafkaConsumer(KafkaEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    @Override
    public void setResumeStrategy(ResumeStrategy resumeStrategy) {
        this.resumeStrategy = resumeStrategy;
    }

    @Override
    public ResumeStrategy getResumeStrategy() {
        return resumeStrategy;
    }

    @Override
    public KafkaConsumerListener getConsumerListener() {
        return consumerListener;
    }

    @Override
    public void setConsumerListener(KafkaConsumerListener consumerListener) {
        this.consumerListener = consumerListener;
    }

    @Override
    public KafkaEndpoint getEndpoint() {
        return (KafkaEndpoint) super.getEndpoint();
    }

    private String randomUUID() {
        return UUID.randomUUID().toString();
    }

    Properties getProps() {
        KafkaConfiguration configuration = endpoint.getConfiguration();

        Properties props = configuration.createConsumerProperties();
        endpoint.updateClassProperties(props);

        ObjectHelper.ifNotEmpty(endpoint.getKafkaClientFactory().getBrokers(configuration),
                v -> props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, v));

        String groupId = ObjectHelper.supplyIfEmpty(configuration.getGroupId(), this::randomUUID);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);

        ObjectHelper.ifNotEmpty(configuration.getGroupInstanceId(),
                v -> props.put(ConsumerConfig.GROUP_INSTANCE_ID_CONFIG, v));

        return props;
    }

    @Override
    protected void doStart() throws Exception {
        LOG.info("Starting Kafka consumer on topic: {} with breakOnFirstError: {}", endpoint.getConfiguration().getTopic(),
                endpoint.getConfiguration().isBreakOnFirstError());
        super.doStart();

        // health-check is optional so discover and resolve
        healthCheckRepository = HealthCheckHelper.getHealthCheckRepository(
                endpoint.getCamelContext(),
                "consumers",
                HealthCheckRepository.class);

        if (healthCheckRepository != null) {
            consumerHealthCheck = new KafkaConsumerHealthCheck(this, getRouteId());
            consumerHealthCheck.setEnabled(getEndpoint().getComponent().isHealthCheckConsumerEnabled());
            setHealthCheck(consumerHealthCheck);
        }

        // is the offset repository already started?
        StateRepository<String, String> repo = endpoint.getConfiguration().getOffsetRepository();
        if (repo instanceof ServiceSupport) {
            boolean started = ((ServiceSupport) repo).isStarted();
            // if not already started then we would do that and also stop it
            if (!started) {
                stopOffsetRepo = true;
                LOG.debug("Starting OffsetRepository: {}", repo);
                ServiceHelper.startService(endpoint.getConfiguration().getOffsetRepository());
            }
        }

        executor = endpoint.createExecutor();

        String topic = endpoint.getConfiguration().getTopic();
        Pattern pattern = null;
        if (endpoint.getConfiguration().isTopicIsPattern()) {
            pattern = Pattern.compile(topic);
        }

        // validate configuration eager in case bad configuration
        if (endpoint.getConfiguration().isPreValidateHostAndPort()) {
            String brokers = getEndpoint().getConfiguration().getBrokers();
            if (ObjectHelper.isEmpty(brokers)) {
                throw new IllegalArgumentException("URL to the Kafka brokers must be configured with the brokers option.");
            }
            ClientUtils.parseAndValidateAddresses(List.of(brokers.split(",")), ClientDnsLookup.USE_ALL_DNS_IPS.toString());
        }

        BridgeExceptionHandlerToErrorHandler bridge = new BridgeExceptionHandlerToErrorHandler(this);
        for (int i = 0; i < endpoint.getConfiguration().getConsumersCount(); i++) {
            KafkaFetchRecords task = new KafkaFetchRecords(
                    this, bridge, topic, pattern, Integer.toString(i), getProps(), consumerListener);

            executor.submit(task);

            tasks.add(task);
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (endpoint.getConfiguration().isTopicIsPattern()) {
            LOG.info("Stopping Kafka consumer on topic pattern: {}", endpoint.getConfiguration().getTopic());
        } else {
            LOG.info("Stopping Kafka consumer on topic: {}", endpoint.getConfiguration().getTopic());
        }

        if (healthCheckRepository != null && consumerHealthCheck != null) {
            consumerHealthCheck = null;
        }

        if (executor != null) {
            if (getEndpoint() != null && getEndpoint().getCamelContext() != null) {
                // signal kafka consumer to stop
                for (KafkaFetchRecords task : tasks) {
                    task.stop();
                }
                int timeout = getEndpoint().getConfiguration().getShutdownTimeout();
                LOG.debug("Shutting down Kafka consumer worker threads with timeout {} millis", timeout);
                getEndpoint().getCamelContext().getExecutorServiceManager().shutdownGraceful(executor, timeout);
            } else {
                executor.shutdown();

                int timeout = endpoint.getConfiguration().getShutdownTimeout();
                LOG.debug("Shutting down Kafka consumer worker threads with timeout {} millis", timeout);
                if (!executor.awaitTermination(timeout, TimeUnit.MILLISECONDS)) {
                    LOG.warn("Shutting down Kafka {} consumer worker threads did not finish within {} millis",
                            tasks.size(), timeout);
                }
            }

            if (!executor.isTerminated()) {
                tasks.forEach(KafkaFetchRecords::stop);
                executor.shutdownNow();
            }
        }
        tasks.clear();
        executor = null;

        if (stopOffsetRepo) {
            StateRepository<String, String> repo = endpoint.getConfiguration().getOffsetRepository();
            LOG.debug("Stopping OffsetRepository: {}", repo);
            ServiceHelper.stopAndShutdownService(repo);
        }

        super.doStop();
    }

    @Override
    protected void doSuspend() throws Exception {
        for (KafkaFetchRecords task : tasks) {
            LOG.info("Pausing Kafka record fetcher task running client ID {}", task.healthState().getClientId());
            task.pause();
        }

        super.doSuspend();
    }

    @Override
    protected void doResume() throws Exception {
        for (KafkaFetchRecords task : tasks) {
            LOG.info("Resuming Kafka record fetcher task running client ID {}", task.healthState().getClientId());
            task.resume();
        }

        super.doResume();
    }

    public List<TaskHealthState> healthStates() {
        return tasks.stream().map(t -> t.healthState()).collect(Collectors.toList());
    }

    @Override
    public String adapterFactoryService() {
        return "kafka-adapter-factory";
    }
}
