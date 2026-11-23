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
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.camel.Processor;
import org.apache.camel.component.kafka.TaskHealthState;
import org.apache.camel.health.HealthCheckHelper;
import org.apache.camel.health.HealthCheckRepository;
import org.apache.camel.support.BridgeExceptionHandlerToErrorHandler;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.util.ObjectHelper;
import org.apache.kafka.clients.ClientDnsLookup;
import org.apache.kafka.clients.ClientUtils;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.AlterConfigOp;
import org.apache.kafka.clients.admin.AlterConfigsOptions;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.config.ConfigResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Consumes the records of a Kafka share group: runs consumersCount share consumers, each on its own thread.
 */
public class KafkaShareConsumer extends DefaultConsumer {

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

        // the share consumer rejects the consumer group options, fail now rather than on every attempt to create it
        Properties props = getProps();
        KafkaShareConfiguration.validateShareConsumerProperties(props);
        endpoint.checkSchemaRegistrySerde(props);

        if (ObjectHelper.isNotEmpty(configuration.getShareAutoOffsetReset())) {
            setShareAutoOffsetReset(props, configuration.getShareAutoOffsetReset().trim());
        }

        executor = endpoint.createExecutor(this);

        BridgeExceptionHandlerToErrorHandler bridge = new BridgeExceptionHandlerToErrorHandler(this);
        for (int i = 0; i < configuration.getConsumersCount(); i++) {
            KafkaShareFetchRecords task = new KafkaShareFetchRecords(this, bridge, Integer.toString(i), getProps());
            // if camel has not been fully started yet then delay starting this consumer to avoid
            // process incoming message before camel is fully started
            endpoint.getComponent().startConsumer(() -> executor.submit(task));
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

    /**
     * Sets share.auto.offset.reset of the share group with the Admin client, before the share consumers start, so it is
     * set when the group first consumes a share-partition.
     */
    void setShareAutoOffsetReset(Properties consumerProps, String value) throws Exception {
        KafkaShareConfiguration.validateShareAutoOffsetReset(value);
        String groupId = endpoint.getConfiguration().getGroupId();
        ConfigResource group = new ConfigResource(ConfigResource.Type.GROUP, groupId);
        AlterConfigOp op = new AlterConfigOp(
                new ConfigEntry(KafkaShareConfiguration.SHARE_AUTO_OFFSET_RESET_CONFIG, value), AlterConfigOp.OpType.SET);
        int timeout = endpoint.getConfiguration().getConsumerRequestTimeoutMs();

        LOG.info("Setting {}={} on share group: {}", KafkaShareConfiguration.SHARE_AUTO_OFFSET_RESET_CONFIG, value, groupId);
        try (Admin admin = endpoint.getKafkaShareClientFactory().getAdmin(adminProperties(consumerProps))) {
            admin.incrementalAlterConfigs(Map.of(group, List.of(op)), new AlterConfigsOptions().timeoutMs(timeout))
                    .all().get(timeout, TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            throw new IllegalStateException(cannotSet(value, groupId, e.getCause()), e.getCause());
        } catch (TimeoutException e) {
            throw new IllegalStateException(cannotSet(value, groupId, e), e);
        }
    }

    private static String cannotSet(String value, String groupId, Throwable cause) {
        return "Cannot set " + KafkaShareConfiguration.SHARE_AUTO_OFFSET_RESET_CONFIG + "=" + value + " on the share group "
               + groupId + " (the shareAutoOffsetReset option): " + cause.getMessage()
               + ". The principal of the consumer needs the ALTER_CONFIGS permission on the group.";
    }

    /**
     * The properties of the admin client: the properties of the share consumer (the brokers, security and additional
     * properties) without the ones that only a consumer knows, which the admin client would log as unknown.
     */
    static Properties adminProperties(Properties consumerProps) {
        Set<String> adminConfigs = AdminClientConfig.configNames();
        Set<String> consumerConfigs = ConsumerConfig.configNames();
        Properties answer = new Properties();
        consumerProps.forEach((key, value) -> {
            String name = key.toString();
            if (adminConfigs.contains(name) || !consumerConfigs.contains(name)) {
                answer.put(key, value);
            }
        });
        return answer;
    }

    public List<TaskHealthState> healthStates() {
        return tasks.stream().map(KafkaShareFetchRecords::healthState).toList();
    }

    List<KafkaShareFetchRecords> tasks() {
        return Collections.unmodifiableList(tasks);
    }
}
