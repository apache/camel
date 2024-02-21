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
package org.apache.camel.component.google.secret.manager.vault;

import java.io.InputStream;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PubsubMessage;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.google.secret.manager.GoogleSecretManagerPropertiesFunction;
import org.apache.camel.spi.ContextReloadStrategy;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.spi.PropertiesFunction;
import org.apache.camel.spi.annotations.PeriodicTask;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.vault.GcpVaultConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Period task which checks if Google secrets has been updated and can trigger Camel to be reloaded.
 */
@PeriodicTask("gcp-secret-refresh")
public class PubsubReloadTriggerTask extends ServiceSupport implements CamelContextAware, Runnable {

    private static final String CAMEL_VAULT_GCP_SERVICE_ACCOUNT_KEY = "CAMEL_VAULT_GCP_SERVICE_ACCOUNT_KEY";
    private static final String CAMEL_VAULT_GCP_PROJECT_ID = "CAMEL_VAULT_GCP_PROJECT_ID";
    private static final String CAMEL_VAULT_GCP_USE_DEFAULT_INSTANCE = "CAMEL_VAULT_GCP_USE_DEFAULT_INSTANCE";
    private static final String CAMEL_VAULT_GCP_SUBSCRIPTION_NAME = "CAMEL_VAULT_GCP_SUBSCRIPTION_NAME";

    private static final Logger LOG = LoggerFactory.getLogger(PubsubReloadTriggerTask.class);

    private CamelContext camelContext;
    private boolean reloadEnabled = true;
    private String secrets;
    private Subscriber subscriber;
    private GoogleSecretManagerPropertiesFunction propertiesFunction;
    private volatile Instant lastCheckTime;
    private volatile Instant lastReloadTime;
    private final Map<String, Instant> updates = new HashMap<>();

    public PubsubReloadTriggerTask() {
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public boolean isReloadEnabled() {
        return reloadEnabled;
    }

    /**
     * Whether Camel should be reloaded on AWS secret updated
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
     * Last time this task checked GCP for updated secrets.
     */
    public Instant getLastCheckTime() {
        return lastCheckTime;
    }

    /**
     * Last time GCP secrets update triggered reload.
     */
    public Instant getLastReloadTime() {
        return lastReloadTime;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // auto-detect secrets in-use
        PropertiesComponent pc = camelContext.getPropertiesComponent();
        PropertiesFunction pf = pc.getPropertiesFunction("gcp");
        if (pf instanceof GoogleSecretManagerPropertiesFunction) {
            propertiesFunction = (GoogleSecretManagerPropertiesFunction) pf;
            LOG.debug("Auto-detecting secrets from properties-function: {}", pf.getName());
        }
        // specific secrets
        secrets = camelContext.getVaultConfiguration().aws().getSecrets();
        if (ObjectHelper.isEmpty(secrets) && propertiesFunction == null) {
            throw new IllegalArgumentException("Secrets must be configured on GCP vault configuration");
        }

        String serviceAccountKey = System.getenv(CAMEL_VAULT_GCP_SERVICE_ACCOUNT_KEY);
        boolean useDefaultInstance = Boolean.parseBoolean(System.getenv(CAMEL_VAULT_GCP_USE_DEFAULT_INSTANCE));
        String projectId = System.getenv(CAMEL_VAULT_GCP_PROJECT_ID);
        String subscription = System.getenv(CAMEL_VAULT_GCP_SUBSCRIPTION_NAME);
        if (ObjectHelper.isEmpty(serviceAccountKey) && ObjectHelper.isEmpty(projectId) && ObjectHelper.isEmpty(subscription)) {
            GcpVaultConfiguration gcpVaultConfiguration = getCamelContext().getVaultConfiguration().gcp();
            if (ObjectHelper.isNotEmpty(gcpVaultConfiguration)) {
                serviceAccountKey = gcpVaultConfiguration.getServiceAccountKey();
                projectId = gcpVaultConfiguration.getProjectId();
                useDefaultInstance = gcpVaultConfiguration.isUseDefaultInstance();
                subscription = gcpVaultConfiguration.getSubscriptionName();
            }
        }
        if (ObjectHelper.isNotEmpty(serviceAccountKey) && ObjectHelper.isNotEmpty(projectId)
                && ObjectHelper.isNotEmpty(subscription)) {
            InputStream resolveMandatoryResourceAsInputStream
                    = ResourceHelper.resolveMandatoryResourceAsInputStream(getCamelContext(), serviceAccountKey);
            Credentials myCredentials = ServiceAccountCredentials
                    .fromStream(resolveMandatoryResourceAsInputStream);
            ProjectSubscriptionName subscriptionName = ProjectSubscriptionName.of(projectId, subscription);
            Subscriber.Builder builder = Subscriber.newBuilder(subscriptionName, new FilteringEventMessageReceiver());
            builder.setCredentialsProvider(FixedCredentialsProvider.create(myCredentials));
            subscriber = builder.build();
        } else if (useDefaultInstance && ObjectHelper.isNotEmpty(projectId) && ObjectHelper.isNotEmpty(subscription)) {
            ProjectSubscriptionName subscriptionName = ProjectSubscriptionName.of(projectId, subscription);
            Subscriber.Builder builder = Subscriber.newBuilder(subscriptionName, new FilteringEventMessageReceiver());
            builder.setCredentialsProvider(FixedCredentialsProvider.create(GoogleCredentials.getApplicationDefault()));
            subscriber = builder.build();
        } else {
            throw new RuntimeCamelException(
                    "Using the GCP Secret refresh task requires setting GCP service account key, project Id and Google Pubsub subscription name as application properties or environment variables");
        }
    }

    @Override
    protected void doShutdown() throws Exception {
        super.doShutdown();

        if (subscriber != null) {
            try {
                subscriber.stopAsync();
            } catch (Exception e) {
                // ignore
            }
            subscriber = null;
        }

        updates.clear();
    }

    @Override
    public void run() {
        lastCheckTime = Instant.now();

        subscriber.startAsync().awaitRunning();
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

    @Override
    public String toString() {
        return "Google Secrets Refresh Task";
    }

    public class FilteringEventMessageReceiver implements MessageReceiver {

        private static final String SECRET_UPDATE = "SECRET_UPDATE";
        private static final String SECRET_VERSION_ADD = "SECRET_VERSION_ADD";

        private boolean triggerReloading;

        @Override
        public void receiveMessage(PubsubMessage message, AckReplyConsumer consumer) {
            String secretId = message.getAttributesMap().get("secretId");
            String eventType = message.getAttributesMap().get("eventType");
            if (eventType.equalsIgnoreCase(SECRET_UPDATE) || eventType.equalsIgnoreCase(SECRET_VERSION_ADD)) {
                if (matchSecret(secretId)) {
                    int secretNameBeginInd = secretId.lastIndexOf("/") + 1;
                    updates.put(secretId.substring(secretNameBeginInd),
                            Instant.ofEpochSecond(message.getPublishTime().getSeconds(), message.getPublishTime().getNanos()));
                    if (isReloadEnabled()) {
                        LOG.info("Update for GCP secret: {} detected, triggering CamelContext reload", secretId);
                        triggerReloading = true;
                    }
                }
            }
            if (triggerReloading) {
                ContextReloadStrategy reload = camelContext.hasService(ContextReloadStrategy.class);
                if (reload != null) {
                    // trigger reload
                    lastReloadTime = Instant.now();
                    reload.onReload(this);
                }
            }
            consumer.ack();

        }

    }
}
