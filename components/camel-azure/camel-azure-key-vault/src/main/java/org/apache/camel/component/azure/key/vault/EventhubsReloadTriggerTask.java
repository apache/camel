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
package org.apache.camel.component.azure.key.vault;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.azure.core.amqp.AmqpTransportType;
import com.azure.messaging.eventhubs.EventProcessorClient;
import com.azure.messaging.eventhubs.EventProcessorClientBuilder;
import com.azure.messaging.eventhubs.checkpointstore.blob.BlobCheckpointStore;
import com.azure.messaging.eventhubs.models.ErrorContext;
import com.azure.messaging.eventhubs.models.EventContext;
import com.azure.storage.blob.BlobContainerAsyncClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.azure.storage.common.StorageSharedKeyCredential;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.ContextReloadStrategy;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.spi.PropertiesFunction;
import org.apache.camel.spi.annotations.PeriodicTask;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.vault.AzureVaultConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Period task which checks if Azure Key Vaults secrets has been updated and can trigger Camel to be reloaded.
 */
@PeriodicTask("azure-secret-refresh")
public class EventhubsReloadTriggerTask extends ServiceSupport implements CamelContextAware, Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(EventhubsReloadTriggerTask.class);

    private static final String BLOB_SERVICE_URI_SEGMENT = ".blob.core.windows.net";
    private static final String SECRET_VERSION_ADD = "Microsoft.KeyVault.SecretNewVersionCreated";

    private CamelContext camelContext;
    private boolean reloadEnabled = true;
    private String secrets;
    private EventProcessorClient eventProcessorClient;
    private KeyVaultPropertiesFunction propertiesFunction;
    private volatile Instant lastCheckTime;
    private volatile Instant lastReloadTime;
    private final Map<String, Instant> updates = new HashMap<>();

    public EventhubsReloadTriggerTask() {
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
     * Whether Camel should be reloaded on Azure Key Vault secret updated
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
     * Last time this task checked Azure Key Vault for updated secrets.
     */
    public Instant getLastCheckTime() {
        return lastCheckTime;
    }

    /**
     * Last time Azure Key Vault secrets update triggered reload.
     */
    public Instant getLastReloadTime() {
        return lastReloadTime;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // auto-detect secrets in-use
        PropertiesComponent pc = camelContext.getPropertiesComponent();
        PropertiesFunction pf = pc.getPropertiesFunction("azure");
        if (pf instanceof KeyVaultPropertiesFunction) {
            propertiesFunction = (KeyVaultPropertiesFunction) pf;
            LOG.debug("Auto-detecting secrets from properties-function: {}", pf.getName());
        }
        // specific secrets
        secrets = camelContext.getVaultConfiguration().azure().getSecrets();
        if (ObjectHelper.isEmpty(secrets) && propertiesFunction == null) {
            throw new IllegalArgumentException("Secrets must be configured on Azure Key vault configuration");
        }

        String eventhubConnectionString = null;
        String blobAccessKey = null;
        String blobAccountName = null;
        String blobContainerName = null;
        AzureVaultConfiguration azureVaultConfiguration = getCamelContext().getVaultConfiguration().azure();
        if (ObjectHelper.isNotEmpty(azureVaultConfiguration)) {
            eventhubConnectionString = azureVaultConfiguration.getEventhubConnectionString();
            blobAccessKey = azureVaultConfiguration.getBlobAccessKey();
            blobAccountName = azureVaultConfiguration.getBlobAccountName();
            blobContainerName = azureVaultConfiguration.getBlobContainerName();
        }
        if (ObjectHelper.isNotEmpty(eventhubConnectionString) && ObjectHelper.isNotEmpty(blobAccessKey)
                && ObjectHelper.isNotEmpty(blobAccountName) && ObjectHelper.isNotEmpty(blobContainerName)) {
            BlobContainerAsyncClient c = new BlobContainerClientBuilder()
                    .endpoint(String.format(Locale.ROOT, "https://%s" + BLOB_SERVICE_URI_SEGMENT, blobAccountName))
                    .containerName(blobContainerName)
                    .credential(new StorageSharedKeyCredential(blobAccountName, blobAccessKey)).buildAsyncClient();

            EventProcessorClientBuilder eventProcessorClientBuilder = new EventProcessorClientBuilder()
                    .checkpointStore(new BlobCheckpointStore(c)).consumerGroup("$Default")
                    .connectionString(eventhubConnectionString).processEvent(this::onEventListener)
                    .processError(this::onErrorListener).transportType(AmqpTransportType.AMQP);

            eventProcessorClient = eventProcessorClientBuilder.buildEventProcessorClient();
            eventProcessorClient.start();
        } else {
            throw new RuntimeCamelException(
                    "Using the Azure Key Vault Secret refresh task requires setting Eventhub connection String, Blob Account Name, Blob Access Key and Blob Container Name  as application properties ");
        }
    }

    @Override
    protected void doShutdown() throws Exception {
        super.doShutdown();

        if (eventProcessorClient != null) {
            try {
                eventProcessorClient.stop();
            } catch (Exception e) {
                // ignore
            }
            eventProcessorClient = null;
        }

        updates.clear();
    }

    @Override
    public void run() {
        lastCheckTime = Instant.now();
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
        return "Azure Secrets Refresh Task";
    }

    protected void onEventListener(final EventContext eventContext) {
        boolean triggerReloading = false;

        ObjectMapper mapper = new ObjectMapper();
        final JsonNode actualObj = retrieveEventData(eventContext, mapper);

        for (int i = 0; i < actualObj.size(); i++) {
            String secret = actualObj.get(i).get("subject").textValue();
            String eventType = actualObj.get(i).get("eventType").textValue();
            if (ObjectHelper.isNotEmpty(secret) && ObjectHelper.isNotEmpty(eventType)) {
                if (eventType.equalsIgnoreCase(SECRET_VERSION_ADD)) {
                    if (matchSecret(secret)) {
                        if (ObjectHelper.isNotEmpty(eventContext.getEventData().getEnqueuedTime())) {
                            updates.put(secret, eventContext.getEventData().getEnqueuedTime());
                        }
                        if (isReloadEnabled()) {
                            LOG.info("Update for Azure secret: {} detected, triggering CamelContext reload", secret);
                            triggerReloading = true;
                        }
                    }
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
    }

    private static JsonNode retrieveEventData(EventContext eventContext, ObjectMapper mapper) {
        try {
            return mapper.readTree(eventContext.getEventData().getBodyAsString());
        } catch (JsonProcessingException e) {
            LOG.warn("Unable to process event data body: {}", e.getMessage(), e);
            throw new RuntimeCamelException(e);
        }
    }

    public void onErrorListener(final ErrorContext errorContext) {
        // NOOP
    }
}
