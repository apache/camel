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

package org.apache.camel.test.infra.azure.key.vault.services;

import org.apache.camel.spi.annotations.InfraService;
import org.apache.camel.test.infra.azure.common.services.FlociAzInfraService;

/**
 * A local Key Vault: Floci AZ serves the Key Vault of the storage account under {@code /<account>-keyvault}, over http.
 * It accepts any bearer token, and answers a request without one with the 401 challenge of Key Vault:
 * {@code AzureKeyVaultClientUtils} builds a client that gets through it.
 */
@InfraService(service = AzureKeyVaultInfraService.class,
              description = "Local Azure Key Vault service with Floci AZ",
              serviceAlias = "azure", serviceImplementationAlias = "key-vault")
public class AzureKeyVaultLocalContainerInfraService extends FlociAzInfraService implements AzureKeyVaultInfraService {

    /**
     * The name of the vault, the account: Floci AZ gives the secrets the identifiers of
     * {@code https://<account>.vault.azure.net}
     */
    @Override
    public String vaultName() {
        return accountName();
    }

    @Override
    public String vaultUrl() {
        return String.format("%s/%s-keyvault", getContainer().serviceAddress(), accountName());
    }
}
