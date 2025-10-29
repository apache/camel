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
package org.apache.camel.component.ibm.secrets.manager.vault;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.ibm.secrets.manager.IBMSecretsManagerPropertiesFunction;
import org.apache.camel.spi.ContextReloadStrategy;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.spi.PropertiesFunction;
import org.apache.camel.spi.annotations.PeriodicTask;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.vault.IBMSecretsManagerVaultConfiguration;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Period task which checks if IBM secrets has been updated and can trigger Camel to be reloaded.
 */
@PeriodicTask("ibm-secret-refresh")
public class IBMEventStreamReloadTriggerTask extends ServiceSupport implements CamelContextAware, Runnable {

    private static final String CAMEL_VAULT_IBM_EVENTSTREAM_BOOTSTRAP_SERVERS_ENV
            = "CAMEL_VAULT_IBM_EVENTSTREAM_BOOTSTRAP_SERVERS";
    private static final String CAMEL_VAULT_IBM_EVENTSTREAM_TOPIC_ENV = "CAMEL_VAULT_IBM_EVENTSTREAM_TOPIC";
    private static final String CAMEL_VAULT_IBM_EVENTSTREAM_USERNAME_ENV = "CAMEL_VAULT_IBM_EVENTSTREAM_USERNAME";
    private static final String CAMEL_VAULT_IBM_EVENTSTREAM_PASSWORD_ENV = "CAMEL_VAULT_IBM_EVENTSTREAM_PASSWORD";
    private static final String CAMEL_VAULT_IBM_EVENTSTREAM_CONSUMER_GROUPID_ENV
            = "CAMEL_VAULT_IBM_EVENTSTREAM_CONSUMER_GROUP_ID";
    private static final String CAMEL_VAULT_IBM_EVENTSTREAM_CONSUMER_POLL_TIMEOUT_ENV
            = "CAMEL_VAULT_IBM_EVENTSTREAM_CONSUMER_POLL_TIMEOUT";

    private CamelContext camelContext;

    private boolean reloadEnabled = true;
    private String secrets;
    private IBMSecretsManagerPropertiesFunction propertiesFunction;
    private volatile Instant lastTime;
    private volatile Instant lastCheckTime;
    private volatile Instant lastReloadTime;
    private final Map<String, Instant> updates = new HashMap<>();
    KafkaConsumer<String, String> kafkaConsumer;
    private static final String IBM_SECRETS_MANAGER_SECRET_ROTATED_EVENT = "secret_rotated";
    protected long pollTimeout;

    private static final Logger LOG = LoggerFactory.getLogger(IBMEventStreamReloadTriggerTask.class);

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // auto-detect secrets in-use
        PropertiesComponent pc = camelContext.getPropertiesComponent();
        PropertiesFunction pf = pc.getPropertiesFunction("ibm");
        if (pf instanceof IBMSecretsManagerPropertiesFunction) {
            propertiesFunction = (IBMSecretsManagerPropertiesFunction) pf;
            LOG.debug("Auto-detecting secrets from properties-function: {}", pf.getName());
        }
        // specific secrets
        secrets = camelContext.getVaultConfiguration().ibmSecretsManager().getSecrets();
        if (ObjectHelper.isEmpty(secrets) && propertiesFunction == null) {
            throw new IllegalArgumentException("Secrets must be configured on IBM vault configuration");
        }

        String bootstrapServers = System.getenv(CAMEL_VAULT_IBM_EVENTSTREAM_BOOTSTRAP_SERVERS_ENV);
        String groupId = System.getenv(CAMEL_VAULT_IBM_EVENTSTREAM_CONSUMER_GROUPID_ENV);
        String topic = System.getenv(CAMEL_VAULT_IBM_EVENTSTREAM_TOPIC_ENV);
        String username = System.getenv(CAMEL_VAULT_IBM_EVENTSTREAM_USERNAME_ENV);
        String password = System.getenv(CAMEL_VAULT_IBM_EVENTSTREAM_PASSWORD_ENV);
        if (ObjectHelper.isNotEmpty(System.getenv(CAMEL_VAULT_IBM_EVENTSTREAM_CONSUMER_POLL_TIMEOUT_ENV))) {
            pollTimeout = Long.parseLong(System.getenv(CAMEL_VAULT_IBM_EVENTSTREAM_CONSUMER_POLL_TIMEOUT_ENV));
        } else {
            pollTimeout = getCamelContext().getVaultConfiguration().getIBMSecretsManagerVaultConfiguration()
                    .getEventStreamConsumerPollTimeout();
        }

        if (ObjectHelper.isEmpty(bootstrapServers) && ObjectHelper.isEmpty(groupId) && ObjectHelper.isEmpty(topic)
                && ObjectHelper.isEmpty(password)) {
            IBMSecretsManagerVaultConfiguration ibmVaultConfiguration
                    = getCamelContext().getVaultConfiguration().ibmSecretsManager();
            if (ObjectHelper.isNotEmpty(ibmVaultConfiguration)) {
                bootstrapServers = ibmVaultConfiguration.getEventStreamBootstrapServers();
                groupId = ibmVaultConfiguration.getEventStreamGroupId();
                topic = ibmVaultConfiguration.getEventStreamTopic();
                if (ObjectHelper.isEmpty(username)) {
                    if (ObjectHelper.isNotEmpty(ibmVaultConfiguration.getEventStreamUsername())) {
                        username = ibmVaultConfiguration.getEventStreamUsername();
                    } else {
                        username = "token";
                    }
                }
                password = ibmVaultConfiguration.getEventStreamPassword();
            }
        } else {
            throw new RuntimeCamelException(
                    "Using the IBM Secrets Refresh Task requires setting IBM Event Stream bootstrap servers, topic, groupId, username and password as application properties or environment variables");
        }

        // create consumer configs
        Map<String, Object> configs = new HashMap<>();
        configs.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configs.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configs.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configs.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        configs.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        configs.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        configs.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_SSL");
        configs.put(SaslConfigs.SASL_MECHANISM, "PLAIN");
        configs.put(SaslConfigs.SASL_JAAS_CONFIG,
                "org.apache.kafka.common.security.plain.PlainLoginModule required username=" + username + " password="
                                                  + password + ";");

        // create consumer
        Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
        kafkaConsumer = new KafkaConsumer<String, String>(configs);

        kafkaConsumer.subscribe(Arrays.asList(topic));
    }

    @Override
    public void run() {

        lastCheckTime = Instant.now();
        boolean triggerReloading = false;
        ObjectMapper mapper = new ObjectMapper();

        while (true) {
            ConsumerRecords<String, String> records = kafkaConsumer.poll(Duration.ofMillis(pollTimeout));

            for (ConsumerRecord<String, String> record : records) {
                JsonNode recordJson;
                String secretType;
                try {
                    recordJson = mapper.readTree(record.value());
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
                JsonNode payload = recordJson.get("data");
                if (payload != null) {
                    secretType = payload.get("event_type").asText();
                    if (secretType != null) {
                        if (secretType.equalsIgnoreCase(IBM_SECRETS_MANAGER_SECRET_ROTATED_EVENT)) {
                            ArrayNode secretsRotated = (ArrayNode) payload.get("secrets");
                            for (JsonNode secret : secretsRotated) {
                                if (secret != null) {
                                    String name = secret.get("secret_name").asText();
                                    if (matchSecret(name)) {
                                        updates.put(name, Instant.parse(secret.get("event_time").asText()));
                                        if (isReloadEnabled()) {
                                            LOG.info("Update for IBM secret: {} detected, triggering CamelContext reload",
                                                    name);
                                            triggerReloading = true;
                                        }
                                        if (triggerReloading) {
                                            ContextReloadStrategy reload = camelContext.hasService(ContextReloadStrategy.class);
                                            if (reload != null) {
                                                // trigger reload
                                                lastReloadTime = Instant.now();
                                                reload.onReload(this);
                                            }
                                            triggerReloading = false;
                                        }
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }

            }
        }

    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    /**
     * Whether Camel should be reloaded on IBM secret updated
     */
    public void setReloadEnabled(boolean reloadEnabled) {
        this.reloadEnabled = reloadEnabled;
    }

    /**
     * A map of the updated secrets with the latest updated time.
     */
    public Map<String, Instant> getUpdates() {
        return Collections.unmodifiableMap(updates);
    }

    /**
     * Last time this task checked IBM for updated secrets.
     */
    public Instant getLastCheckTime() {
        return lastCheckTime;
    }

    /**
     * Last time IBM secrets update triggered reload.
     */
    public Instant getLastReloadTime() {
        return lastReloadTime;
    }

    public boolean isReloadEnabled() {
        return reloadEnabled;
    }

    protected boolean matchSecret(String name) {
        Set<String> set = new HashSet<>();
        if (secrets != null) {
            Collections.addAll(set, secrets.split(","));
        }
        if (propertiesFunction != null) {
            set.addAll(propertiesFunction.getSecrets());
        }

        for (String part : set) {
            boolean result = name.contains(part) || PatternHelper.matchPattern(name, part);
            LOG.trace("Matching secret id: {}={} -> {}", name, part, result);
            if (result) {
                return true;
            }
        }

        return false;
    }
}
