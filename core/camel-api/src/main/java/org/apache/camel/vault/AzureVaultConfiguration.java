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

/**
 * Configuration for access to Azure Key Vault.
 */
public class AzureVaultConfiguration extends VaultConfiguration {

    @Metadata
    private String vaultName;
    @Metadata(secret = true)
    private String clientId;
    @Metadata(secret = true)
    private String clientSecret;
    @Metadata(secret = true)
    private String tenantId;

    public String getVaultName() {
        return vaultName;
    }

    /**
     * The vault Name in Azure Key Vault
     */
    public void setVaultName(String vaultName) {
        this.vaultName = vaultName;
    }

    public String getClientId() {
        return clientId;
    }

    /**
     * The client Id for accessing Azure Key Vault
     */
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    /**
     * The client Secret for accessing Azure Key Vault
     */
    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getTenantId() {
        return tenantId;
    }

    /**
     * The Tenant Id for accessing Azure Key Vault
     */
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }
}
