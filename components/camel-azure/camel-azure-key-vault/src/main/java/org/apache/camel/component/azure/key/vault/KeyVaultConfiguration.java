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

import com.azure.security.keyvault.secrets.SecretClient;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.azure.common.CredentialType;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

import static org.apache.camel.component.azure.common.CredentialType.CLIENT_SECRET;

@UriParams
public class KeyVaultConfiguration implements Cloneable {

    @UriPath(description = "The name of the key vault. The endpoint builds the vault URL from it with the vault.azure.net"
                           + " domain of the Azure public cloud. Not used to connect when the secretClient option is set.")
    private String vaultName;
    @UriParam(description = "To use a custom com.azure.security.keyvault.secrets.SecretClient. When this option is set, the"
                            + " client decides the vault and the credentials, and the vaultName, credentialType, clientId,"
                            + " clientSecret and tenantId options are not used to connect.")
    @Metadata(autowired = true)
    private SecretClient secretClient;
    @UriParam(label = "security", security = "secret",
              description = "The client ID (application ID) of the Microsoft Entra application for the CLIENT_SECRET credential"
                            + " type. Required for CLIENT_SECRET, the default, unless the secretClient option is set. Not used"
                            + " for AZURE_IDENTITY.")
    private String clientId;
    @UriParam(label = "security", security = "secret",
              description = "The client secret of the Microsoft Entra application for the CLIENT_SECRET credential type."
                            + " Required for CLIENT_SECRET, the default, unless the secretClient option is set. Not used for"
                            + " AZURE_IDENTITY.")
    private String clientSecret;
    @UriParam(label = "security", security = "secret",
              description = "The ID of the Microsoft Entra tenant (directory) of the application for the CLIENT_SECRET"
                            + " credential type. Required for CLIENT_SECRET, the default, unless the secretClient option is"
                            + " set. Not used for AZURE_IDENTITY.")
    private String tenantId;
    @UriParam(label = "producer",
              description = "The operation that the producer performs. The CamelAzureKeyVaultProducerOperation header"
                            + " replaces this option. Every operation except updateSecretProperties takes the name of the"
                            + " secret from the CamelAzureKeyVaultSecretName header.")
    private KeyVaultOperation operation = KeyVaultOperation.createSecret;
    @UriParam(label = "common", enums = "CLIENT_SECRET,AZURE_IDENTITY",
              defaultValue = "CLIENT_SECRET",
              description = "How the endpoint authenticates when it creates the SecretClient. CLIENT_SECRET uses the clientId,"
                            + " clientSecret and tenantId options. AZURE_IDENTITY uses the DefaultAzureCredential of the Azure"
                            + " Identity library, which tries environment variables, workload identity, managed identity and"
                            + " developer tools such as the Azure CLI. Not used when the secretClient option is set.")
    private CredentialType credentialType = CLIENT_SECRET;

    public SecretClient getSecretClient() {
        return secretClient;
    }

    public void setSecretClient(SecretClient secretClient) {
        this.secretClient = secretClient;
    }

    public String getVaultName() {
        return vaultName;
    }

    public void setVaultName(String vaultName) {
        this.vaultName = vaultName;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public KeyVaultOperation getOperation() {
        return operation;
    }

    public void setOperation(KeyVaultOperation operation) {
        this.operation = operation;
    }

    public CredentialType getCredentialType() {
        return credentialType;
    }

    public void setCredentialType(CredentialType credentialType) {
        this.credentialType = credentialType;
    }

    // *************************************************
    //
    // *************************************************

    public KeyVaultConfiguration copy() {
        try {
            return (KeyVaultConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
