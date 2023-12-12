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

import java.util.HashSet;
import java.util.Set;

import com.azure.core.credential.TokenCredential;
import com.azure.core.exception.ResourceNotFoundException;
import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.PropertiesFunction;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.vault.AzureVaultConfiguration;

/**
 * A {@link PropertiesFunction} that lookup the property value from Azure Key Vault service.
 * <p/>
 * The credentials to access Key vault is defined using three environment variables representing the static credentials:
 * <ul>
 * <li><tt>CAMEL_VAULT_AZURE_VAULT_NAME</tt></li>
 * <li><tt>CAMEL_VAULT_AZURE_CLIENT_ID</tt></li>
 * <li><tt>CAMEL_VAULT_AZURE_CLIENT_SECRET</tt></li>
 * <li><tt>CAMEL_VAULT_AZURE_TENANT_ID</tt></li>
 * <li><tt>CAMEL_VAULT_AZURE_IDENTITY_ENABLED</tt></li>
 * </ul>
 * <p/>
 *
 * Otherwise it is possible to specify the credentials as properties:
 *
 * <ul>
 * <li><tt>camel.vault.azure.vaultName</tt></li>
 * <li><tt>camel.vault.azure.clientId</tt></li>
 * <li><tt>camel.vault.azure.clientSecret</tt></li>
 * <li><tt>camel.vault.azure.tenantId</tt></li>
 * <li><tt>camel.vault.azure.azureIdentityEnabled</tt></li>
 * </ul>
 * <p/>
 *
 * This implementation is to return the secret value associated with a key. The properties related to this kind of
 * Properties Function are all prefixed with <tt>azure:</tt>. For example asking for <tt>azure:token</tt>, will return
 * the secret value associated to the secret named token on Azure Key Vault.
 *
 * Another way of retrieving a secret value is using the following notation <tt>azure:database/username</tt>: in this
 * case the field username of the secret database will be returned. As a fallback, the user could provide a default
 * value, which will be returned in case the secret doesn't exist, the secret has been marked for deletion or, for
 * example, if a particular field of the secret doesn't exist. For using this feature, the user could use the following
 * notation <tt>azure:database/username:admin</tt>. The admin value will be returned as default value, if the conditions
 * above were all met.
 */

@org.apache.camel.spi.annotations.PropertiesFunction("azure")
public class KeyVaultPropertiesFunction extends ServiceSupport implements PropertiesFunction, CamelContextAware {

    private static final String CAMEL_VAULT_AZURE_VAULT_NAME = "CAMEL_VAULT_AZURE_VAULT_NAME";
    private static final String CAMEL_VAULT_AZURE_CLIENT_ID = "CAMEL_VAULT_AZURE_CLIENT_ID";
    private static final String CAMEL_VAULT_AZURE_CLIENT_SECRET = "CAMEL_VAULT_AZURE_CLIENT_SECRET";
    private static final String CAMEL_VAULT_AZURE_TENANT_ID = "CAMEL_VAULT_AZURE_TENANT_ID";

    private static final String CAMEL_VAULT_AZURE_IDENTITY_ENABLED = "CAMEL_VAULT_AZURE_IDENTITY_ENABLED";
    private CamelContext camelContext;
    private SecretClient client;
    private final Set<String> secrets = new HashSet<>();

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        String vaultName = System.getenv(CAMEL_VAULT_AZURE_VAULT_NAME);
        String clientId = System.getenv(CAMEL_VAULT_AZURE_CLIENT_ID);
        String clientSecret = System.getenv(CAMEL_VAULT_AZURE_CLIENT_SECRET);
        String tenantId = System.getenv(CAMEL_VAULT_AZURE_TENANT_ID);
        boolean azureIdentityEnabled = Boolean.parseBoolean(System.getenv(CAMEL_VAULT_AZURE_IDENTITY_ENABLED));
        if (ObjectHelper.isEmpty(vaultName) && ObjectHelper.isEmpty(clientId) && ObjectHelper.isEmpty(clientSecret)
                && ObjectHelper.isEmpty(tenantId)) {
            AzureVaultConfiguration azureVaultConfiguration = getCamelContext().getVaultConfiguration().azure();
            if (ObjectHelper.isNotEmpty(azureVaultConfiguration)) {
                vaultName = azureVaultConfiguration.getVaultName();
                clientId = azureVaultConfiguration.getClientId();
                clientSecret = azureVaultConfiguration.getClientSecret();
                tenantId = azureVaultConfiguration.getTenantId();
                azureIdentityEnabled = azureVaultConfiguration.isAzureIdentityEnabled();
            }
        }
        if (ObjectHelper.isNotEmpty(vaultName) && ObjectHelper.isNotEmpty(clientId) && ObjectHelper.isNotEmpty(clientSecret)
                && ObjectHelper.isNotEmpty(tenantId) && !azureIdentityEnabled) {
            String keyVaultUri = "https://" + vaultName + ".vault.azure.net";

            // Credential
            ClientSecretCredential credential = new ClientSecretCredentialBuilder()
                    .tenantId(tenantId)
                    .clientId(clientId)
                    .clientSecret(clientSecret)
                    .build();

            // Build Client
            client = new SecretClientBuilder()
                    .vaultUrl(keyVaultUri)
                    .credential(credential)
                    .buildClient();
        } else if (ObjectHelper.isNotEmpty(vaultName) && azureIdentityEnabled) {
            String keyVaultUri = "https://" + vaultName + ".vault.azure.net";

            // Credential
            TokenCredential credential = new DefaultAzureCredentialBuilder().build();

            // Build Client
            client = new SecretClientBuilder()
                    .vaultUrl(keyVaultUri)
                    .credential(credential)
                    .buildClient();
        } else {
            throw new RuntimeCamelException(
                    "Using the Azure Key Vault Properties Function requires setting Azure credentials as application properties or environment variables or enable the Azure Identity Authentication mechanism");
        }
    }

    @Override
    protected void doStop() throws Exception {
        secrets.clear();
        super.doStop();
    }

    @Override
    public String getName() {
        return "azure";
    }

    @Override
    public String apply(String remainder) {
        String key = remainder;
        String subkey = null;
        String returnValue = null;
        String defaultValue = null;
        String version = null;
        if (remainder.contains("/")) {
            key = StringHelper.before(remainder, "/");
            subkey = StringHelper.after(remainder, "/");
            defaultValue = StringHelper.after(subkey, ":");
            if (ObjectHelper.isNotEmpty(defaultValue)) {
                if (defaultValue.contains("@")) {
                    version = StringHelper.after(defaultValue, "@");
                    defaultValue = StringHelper.before(defaultValue, "@");
                }
            }
            if (subkey.contains(":")) {
                subkey = StringHelper.before(subkey, ":");
            }
            if (subkey.contains("@")) {
                version = StringHelper.after(subkey, "@");
                subkey = StringHelper.before(subkey, "@");
            }
        } else if (remainder.contains(":")) {
            key = StringHelper.before(remainder, ":");
            defaultValue = StringHelper.after(remainder, ":");
            if (remainder.contains("@")) {
                version = StringHelper.after(remainder, "@");
                defaultValue = StringHelper.before(defaultValue, "@");
            }
        } else {
            if (remainder.contains("@")) {
                key = StringHelper.before(remainder, "@");
                version = StringHelper.after(remainder, "@");
            }
        }

        if (key != null) {
            try {
                returnValue = getSecretFromSource(key, subkey, defaultValue, version);
            } catch (JsonProcessingException e) {
                throw new RuntimeCamelException("Something went wrong while recovering " + key + " from vault");
            }
        }

        return returnValue;
    }

    private String getSecretFromSource(
            String key, String subkey, String defaultValue, String version)
            throws JsonProcessingException {
        String returnValue;

        // capture name of secret
        secrets.add(key);

        try {
            KeyVaultSecret secret = client.getSecret(key, ObjectHelper.isNotEmpty(version) ? version : "");
            returnValue = secret.getValue();
            if (ObjectHelper.isNotEmpty(subkey)) {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode actualObj = mapper.readTree(returnValue);
                JsonNode field = actualObj.get(subkey);
                if (ObjectHelper.isNotEmpty(field)) {
                    returnValue = field.textValue();
                } else {
                    returnValue = null;
                }
            }
            if (ObjectHelper.isEmpty(returnValue)) {
                returnValue = defaultValue;
            }
        } catch (ResourceNotFoundException ex) {
            if (ObjectHelper.isNotEmpty(defaultValue)) {
                returnValue = defaultValue;
            } else {
                throw ex;
            }
        }
        return returnValue;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    /**
     * Ids of the secrets in use
     */
    public Set<String> getSecrets() {
        return secrets;
    }
}
