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
package org.apache.camel.component.kafka.share;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Processor;
import org.apache.camel.component.kafka.TaskHealthState;
import org.apache.camel.health.HealthCheckAware;
import org.apache.camel.health.HealthCheckHelper;
import org.apache.camel.health.HealthCheckRepository;
import org.apache.camel.support.BridgeExceptionHandlerToErrorHandler;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.util.ObjectHelper;
import org.apache.kafka.clients.ClientDnsLookup;
import org.apache.kafka.clients.ClientUtils;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Consumes the records of a Kafka share group: runs consumersCount share consumers, each on its own thread.
 */
public class KafkaShareConsumer extends DefaultConsumer implements HealthCheckAware {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaShareConsumer.class);

    private final KafkaShareEndpoint endpoint;
    private final List<KafkaShareFetchRecords> tasks = new ArrayList<>();
    private ExecutorService executor;

    public KafkaShareConsumer(KafkaShareEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    @Override
    public KafkaShareEndpoint getEndpoint() {
        return (KafkaShareEndpoint) super.getEndpoint();
    }

    Properties getProps() {
        KafkaShareConfiguration configuration = endpoint.getConfiguration();

        Properties props = configuration.createShareConsumerProperties();
        endpoint.updateClassProperties(props);

        ObjectHelper.ifNotEmpty(endpoint.getKafkaShareClientFactory().getBrokers(configuration),
                v -> props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, v));

        return props;
    }

    @Override
    protected void doStart() throws Exception {
        KafkaShareConfiguration configuration = endpoint.getConfiguration();
        LOG.info("Starting Kafka share consumer on topic: {} with share group: {}", configuration.getTopic(),
                configuration.getGroupId());
        super.doStart();

        if (ObjectHelper.isEmpty(configuration.getGroupId())) {
            throw new IllegalArgumentException("The share group must be configured with the groupId option.");
        }

        // health-check is optional so discover and resolve
        HealthCheckRepository healthCheckRepository = HealthCheckHelper.getHealthCheckRepository(
                endpoint.getCamelContext(), "consumers", HealthCheckRepository.class);
        if (healthCheckRepository != null) {
            KafkaShareConsumerHealthCheck healthCheck = new KafkaShareConsumerHealthCheck(this, getRouteId());
            healthCheck.setEnabled(getEndpoint().getComponent().isHealthCheckConsumerEnabled());
            setHealthCheck(healthCheck);
        }

        // validate configuration eager in case bad configuration
        if (configuration.isPreValidateHostAndPort()) {
            String brokers = configuration.getBrokers();
            if (ObjectHelper.isEmpty(brokers)) {
                throw new IllegalArgumentException("URL to the Kafka brokers must be configured with the brokers option.");
            }
            ClientUtils.parseAndValidateAddresses(List.of(brokers.split(",")), ClientDnsLookup.USE_ALL_DNS_IPS.toString());
        }

        executor = endpoint.createExecutor(this);

        BridgeExceptionHandlerToErrorHandler bridge = new BridgeExceptionHandlerToErrorHandler(this);
        for (int i = 0; i < configuration.getConsumersCount(); i++) {
            KafkaShareFetchRecords task = new KafkaShareFetchRecords(this, bridge, Integer.toString(i), getProps());
            if (!endpoint.getCamelContext().isStarted()) {
                // if camel has not been fully started yet then delay starting this consumer to avoid
                // process incoming message before camel is fully started
                endpoint.getComponent().pendingConsumer(() -> executor.submit(task));
            } else {
                executor.submit(task);
            }
            tasks.add(task);
        }
    }

    @Override
    protected void doStop() throws Exception {
        LOG.info("Stopping Kafka share consumer on topic: {}", endpoint.getConfiguration().getTopic());

        if (executor != null) {
            // signal the share consumers to stop
            for (KafkaShareFetchRecords task : tasks) {
                task.stop();
            }
            int timeout = endpoint.getConfiguration().getShutdownTimeout();
            LOG.debug("Shutting down Kafka share consumer worker threads with timeout {} millis", timeout);
            if (endpoint.getCamelContext() != null) {
                endpoint.getCamelContext().getExecutorServiceManager().shutdownGraceful(executor, timeout);
            } else {
                executor.shutdown();
                if (!executor.awaitTermination(timeout, TimeUnit.MILLISECONDS)) {
                    LOG.warn("Shutting down Kafka {} share consumer worker threads did not finish within {} millis",
                            tasks.size(), timeout);
                }
            }
            if (!executor.isTerminated()) {
                tasks.forEach(KafkaShareFetchRecords::stop);
                executor.shutdownNow();
            }
        }
        tasks.clear();
        executor = null;

        super.doStop();
    }

    public List<TaskHealthState> healthStates() {
        return tasks.stream().map(KafkaShareFetchRecords::healthState).toList();
    }

    List<KafkaShareFetchRecords> tasks() {
        return Collections.unmodifiableList(tasks);
    }
}
