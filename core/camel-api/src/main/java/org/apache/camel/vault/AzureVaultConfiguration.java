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
package org.apache.camel.vault;

import org.apache.camel.spi.Metadata;
import org.jspecify.annotations.Nullable;

/**
 * Configuration for access to Azure Key Vault.
 */
public class AzureVaultConfiguration extends VaultConfiguration {

    @Metadata
    private @Nullable String vaultName;
    @Metadata(security = "secret")
    private @Nullable String clientId;
    @Metadata(security = "secret")
    private @Nullable String clientSecret;
    @Metadata(security = "secret")
    private @Nullable String tenantId;
    @Metadata
    private boolean azureIdentityEnabled;
    @Metadata
    private boolean refreshEnabled;
    @Metadata(defaultValue = "30000")
    private long refreshPeriod = 30000;
    @Metadata
    private @Nullable String secrets;
    @Metadata(security = "secret")
    private @Nullable String eventhubConnectionString;
    @Metadata(security = "secret")
    private @Nullable String blobAccessKey;
    @Metadata
    private @Nullable String blobAccountName;
    @Metadata
    private @Nullable String blobContainerName;

    public @Nullable String getVaultName() {
        return vaultName;
    }

    /**
     * The vault Name in Azure Key Vault
     */
    public void setVaultName(@Nullable String vaultName) {
        this.vaultName = vaultName;
    }

    public @Nullable String getClientId() {
        return clientId;
    }

    /**
     * The client Id for accessing Azure Key Vault
     */
    public void setClientId(@Nullable String clientId) {
        this.clientId = clientId;
    }

    public @Nullable String getClientSecret() {
        return clientSecret;
    }

    /**
     * The client Secret for accessing Azure Key Vault
     */
    public void setClientSecret(@Nullable String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public @Nullable String getTenantId() {
        return tenantId;
    }

    /**
     * The Tenant Id for accessing Azure Key Vault
     */
    public void setTenantId(@Nullable String tenantId) {
        this.tenantId = tenantId;
    }

    public boolean isAzureIdentityEnabled() {
        return azureIdentityEnabled;
    }

    /**
     * Whether the Azure Identity Authentication should be used or not.
     */
    public void setAzureIdentityEnabled(boolean azureIdentityEnabled) {
        this.azureIdentityEnabled = azureIdentityEnabled;
    }

    public boolean isRefreshEnabled() {
        return refreshEnabled;
    }

    /**
     * Whether to automatically reload Camel upon secrets being updated in Azure.
     */
    public void setRefreshEnabled(boolean refreshEnabled) {
        this.refreshEnabled = refreshEnabled;
    }

    public long getRefreshPeriod() {
        return refreshPeriod;
    }

    /**
     * The period (millis) between checking Azure for updated secrets.
     */
    public void setRefreshPeriod(long refreshPeriod) {
        this.refreshPeriod = refreshPeriod;
    }

    public @Nullable String getSecrets() {
        return secrets;
    }

    /**
     * Specify the secret names (or pattern) to check for updates. Multiple secrets can be separated by comma.
     */
    public void setSecrets(@Nullable String secrets) {
        this.secrets = secrets;
    }

    public @Nullable String getEventhubConnectionString() {
        return eventhubConnectionString;
    }

    /**
     * The Eventhubs connection String for Key Vault Secret events notifications
     */
    public void setEventhubConnectionString(@Nullable String eventhubConnectionString) {
        this.eventhubConnectionString = eventhubConnectionString;
    }

    public @Nullable String getBlobAccessKey() {
        return blobAccessKey;
    }

    /**
     * The Eventhubs Blob Access Key for CheckpointStore purpose
     */
    public void setBlobAccessKey(@Nullable String blobAccessKey) {
        this.blobAccessKey = blobAccessKey;
    }

    public @Nullable String getBlobAccountName() {
        return blobAccountName;
    }

    /**
     * The Eventhubs Blob Account Name for CheckpointStore purpose
     */
    public void setBlobAccountName(@Nullable String blobAccountName) {
        this.blobAccountName = blobAccountName;
    }

    public @Nullable String getBlobContainerName() {
        return blobContainerName;
    }

    /**
     * The Eventhubs Blob Container Name for CheckpointStore purpose
     */
    public void setBlobContainerName(@Nullable String blobContainerName) {
        this.blobContainerName = blobContainerName;
    }
}
