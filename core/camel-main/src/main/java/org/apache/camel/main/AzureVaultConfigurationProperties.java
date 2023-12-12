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
package org.apache.camel.main;

import org.apache.camel.spi.BootstrapCloseable;
import org.apache.camel.spi.Configurer;
import org.apache.camel.vault.AzureVaultConfiguration;

/**
 * Configuration for access to Azure Key Vault Secret.
 */
@Configurer(bootstrap = true)
public class AzureVaultConfigurationProperties extends AzureVaultConfiguration implements BootstrapCloseable {

    private MainConfigurationProperties parent;

    public AzureVaultConfigurationProperties(MainConfigurationProperties parent) {
        this.parent = parent;
    }

    public MainConfigurationProperties end() {
        return parent;
    }

    @Override
    public void close() {
        parent = null;
    }

    // getter and setters
    // --------------------------------------------------------------

    // these are inherited from the parent class

    // fluent builders
    // --------------------------------------------------------------

    /**
     * The Vault Name
     */
    public AzureVaultConfigurationProperties withVaultName(String vaultName) {
        setVaultName(vaultName);
        return this;
    }

    /**
     * The Azure Key Vault Client ID
     */
    public AzureVaultConfigurationProperties withClientId(String clientId) {
        setClientId(clientId);
        return this;
    }

    /**
     * The Azure Key Vault Client Secret
     */
    public AzureVaultConfigurationProperties withClientSecret(String clientSecret) {
        setClientSecret(clientSecret);
        return this;
    }

    /**
     * The Azure Key Vault Tenant Id
     */
    public AzureVaultConfigurationProperties withTenantId(String tenantId) {
        setTenantId(tenantId);
        return this;
    }

    /**
     * Whether the Azure Identity Authentication should be used or not.
     */
    public AzureVaultConfigurationProperties withAzureIdentityEnabled(boolean azureIdentityEnabled) {
        setAzureIdentityEnabled(azureIdentityEnabled);
        return this;
    }

    /**
     * Whether to automatically reload Camel upon secrets being updated in Azure.
     */
    public AzureVaultConfigurationProperties withRefreshEnabled(boolean refreshEnabled) {
        setRefreshEnabled(refreshEnabled);
        return this;
    }

    /**
     * The period (millis) between checking Azure for updated secrets.
     */
    public AzureVaultConfigurationProperties withRefreshPeriod(long refreshPeriod) {
        setRefreshPeriod(refreshPeriod);
        return this;
    }

    /**
     * Specify the secret names (or pattern) to check for updates. Multiple secrets can be separated by comma.
     */
    public AzureVaultConfigurationProperties withSecrets(String secrets) {
        setSecrets(secrets);
        return this;
    }

    /**
     * The Azure Eventhub connection String for Key Vault Event notification
     */
    public AzureVaultConfigurationProperties withEventhubConnectionString(String connectionString) {
        setEventhubConnectionString(connectionString);
        return this;
    }

    /**
     * The Azure Eventhub Blob Access Key for Checkpoint Store purpose
     */
    public AzureVaultConfigurationProperties withBlobAccessKey(String accessKey) {
        setBlobAccessKey(accessKey);
        return this;
    }

    /**
     * The Azure Eventhub Blob Account Name for Checkpoint Store purpose
     */
    public AzureVaultConfigurationProperties withBlobAccountName(String accountName) {
        setBlobAccountName(accountName);
        return this;
    }

    /**
     * The Azure Eventhub Blob Container Name for Checkpoint Store purpose
     */
    public AzureVaultConfigurationProperties withBlobContainerName(String containerName) {
        setBlobContainerName(containerName);
        return this;
    }
}
