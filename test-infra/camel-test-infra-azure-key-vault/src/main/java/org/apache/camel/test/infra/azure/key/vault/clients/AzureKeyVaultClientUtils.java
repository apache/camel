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

package org.apache.camel.test.infra.azure.key.vault.clients;

import java.net.URL;
import java.time.OffsetDateTime;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenCredential;
import com.azure.core.http.HttpPipelineCallContext;
import com.azure.core.http.HttpRequest;
import com.azure.core.http.policy.HttpPipelineSyncPolicy;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import org.apache.camel.test.infra.azure.common.AzureConfigs;
import org.apache.camel.test.infra.azure.key.vault.AzureKeyVaultConfigs;
import reactor.core.publisher.Mono;

public final class AzureKeyVaultClientUtils {

    // Floci AZ accepts any bearer token
    private static final TokenCredential FLOCI_AZ_CREDENTIAL
            = request -> Mono.just(new AccessToken("floci-az", OffsetDateTime.now().plusHours(1)));

    private AzureKeyVaultClientUtils() {

    }

    /**
     * A client for the Key Vault of Floci AZ, or with {@code -Dazure.instance.type=remote} for the vault and the
     * service principal of the {@link AzureKeyVaultConfigs} system properties
     */
    public static SecretClient getClient() {
        String instanceType = System.getProperty("azure.instance.type");

        if (instanceType == null || instanceType.equals("local-azure-container")) {
            return getLocalClient(String.format("https://%s:%s/%s-keyvault", System.getProperty(AzureConfigs.HOST),
                    System.getProperty(AzureConfigs.PORT), System.getProperty(AzureConfigs.ACCOUNT_NAME)));
        }

        return new SecretClientBuilder()
                .vaultUrl(String.format("https://%s.vault.azure.net", System.getProperty(AzureKeyVaultConfigs.VAULT_NAME)))
                .credential(new ClientSecretCredentialBuilder()
                        .tenantId(System.getProperty(AzureKeyVaultConfigs.TENANT_ID))
                        .clientId(System.getProperty(AzureKeyVaultConfigs.CLIENT_ID))
                        .clientSecret(System.getProperty(AzureKeyVaultConfigs.CLIENT_SECRET))
                        .build())
                .buildClient();
    }

    /**
     * The Key Vault SDK sends a token only to an https address, and only for the resource of the 401 challenge of Key
     * Vault that matches the host of the vault. Floci AZ serves http, and its challenge names the resource
     * {@code https://vault.azure.net}: the client is built for the https address of the vault, with a token that Floci
     * AZ accepts and without the check of the resource, and a policy sends its requests over http.
     */
    private static SecretClient getLocalClient(String vaultUrl) {
        return new SecretClientBuilder()
                .vaultUrl(vaultUrl)
                .credential(FLOCI_AZ_CREDENTIAL)
                .addPolicy(new HttpSchemePolicy())
                .disableChallengeResourceVerification()
                .buildClient();
    }

    /**
     * Sends a request over http. A policy added to the builder comes after the credential policy, which then still sees
     * the https address it requires.
     */
    private static final class HttpSchemePolicy extends HttpPipelineSyncPolicy {
        @Override
        protected void beforeSendingRequest(HttpPipelineCallContext context) {
            HttpRequest request = context.getHttpRequest();

            URL url = request.getUrl();
            if ("https".equals(url.getProtocol())) {
                request.setUrl("http" + url.toString().substring("https".length()));
            }
        }
    }
}
