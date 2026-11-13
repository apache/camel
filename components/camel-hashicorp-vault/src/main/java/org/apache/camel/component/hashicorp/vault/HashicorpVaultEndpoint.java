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
package org.apache.camel.component.hashicorp.vault;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;
import org.springframework.vault.core.VaultTemplate;

/**
 * Manage secrets in HashiCorp Vault Service
 */
@UriEndpoint(firstVersion = "3.18.0", scheme = "hashicorp-vault", title = "HashiCorp Vault",
             syntax = "hashicorp-vault:secretsEngine", category = {
                     Category.CLOUD, Category.CLOUD },
             producerOnly = true,
             headersClass = HashicorpVaultConstants.class)
@Metadata(annotations = {
        "vault=hashicorp-vault",
})
@BodyTypes(producerIn = @BodyType(value = {
        Carrier.MAP, Carrier.POJO, Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE },
                                  byOption = {
                                          @BodyType.When(option = "operation", value = "getSecret", carriers = Carrier.ANY),
                                          @BodyType.When(option = "operation", value = "deleteSecret", carriers = Carrier.ANY),
                                          @BodyType.When(option = "operation", value = "listSecrets", carriers = Carrier.ANY) },
                                  description = "The fields of the secret for createSecret, the default operation: a Map of the"
                                                + " keys and values, a POJO written as JSON, or text, bytes, a stream or a file"
                                                + " with a JSON object. A List or no body fails. The other operations take the"
                                                + " secret from the secretPath option or the CamelHashicorpVaultSecretPath header"),
           producerOut = @BodyType(value = Carrier.UNCHANGED,
                                   byOption = {
                                           @BodyType.When(option = "operation", value = "getSecret",
                                                          carriers = { Carrier.MAP, Carrier.NULL }),
                                           @BodyType.When(option = "operation", value = "listSecrets",
                                                          carriers = Carrier.LIST,
                                                          javaType = "java.util.List<java.lang.String>") },
                                   description = "getSecret gives a Map with the fields of the secret under data and its version"
                                                 + " under metadata, or no body when the secret does not exist or is deleted."
                                                 + " listSecrets gives a List of the names of the secrets in the engine;"
                                                 + " createSecret and deleteSecret leave the body unchanged"))
public class HashicorpVaultEndpoint extends DefaultEndpoint {

    private VaultTemplate vaultTemplate;
    // the client the endpoint created, which it closes on shutdown (a vaultTemplate that was set is not closed)
    private HashicorpVaultClient vaultClient;

    @UriParam
    private HashicorpVaultConfiguration configuration;

    public HashicorpVaultEndpoint(final String uri, final Component component,
                                  final HashicorpVaultConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public void doInit() throws Exception {
        super.doInit();
        if (configuration.getVaultTemplate() != null) {
            vaultTemplate = configuration.getVaultTemplate();
        } else {
            vaultClient = HashicorpVaultClient.create(getCamelContext(), this, clientSettings());
            vaultTemplate = vaultClient.getVaultTemplate();
        }
    }

    @Override
    protected void doShutdown() throws Exception {
        if (vaultClient != null) {
            // stop renewing the token, and revoke the token of the login
            vaultClient.close();
            vaultClient = null;
        }
        super.doShutdown();
    }

    /**
     * Where Vault is and how to log in to it, as configured on the endpoint
     */
    HashicorpVaultClientSettings clientSettings() {
        HashicorpVaultClientSettings settings = new HashicorpVaultClientSettings();
        settings.setHost(configuration.getHost());
        settings.setPort(configuration.getPort());
        settings.setScheme(configuration.getScheme());
        settings.setCloud(configuration.isCloud());
        settings.setNamespace(configuration.getNamespace());
        settings.setAuthentication(configuration.getAuthentication());
        settings.setToken(configuration.getToken());
        settings.setRoleId(configuration.getRoleId());
        settings.setSecretId(configuration.getSecretId());
        settings.setRole(configuration.getRole());
        settings.setJwt(configuration.getJwt());
        settings.setJwtFile(configuration.getJwtFile());
        settings.setAuthMountPath(configuration.getAuthMountPath());
        settings.setTrustStore(configuration.getTrustStore());
        settings.setTrustStorePassword(configuration.getTrustStorePassword());
        settings.setKeyStore(configuration.getKeyStore());
        settings.setKeyStorePassword(configuration.getKeyStorePassword());
        settings.setAwsIamServerId(configuration.getAwsIamServerId());
        settings.setAwsRegion(configuration.getAwsRegion());
        settings.setAwsStsEndpoint(configuration.getAwsStsEndpoint());
        settings.setAzureResource(configuration.getAzureResource());
        settings.setAzureClientId(configuration.getAzureClientId());
        return settings;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new HashicorpVaultProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Consumer not supported");
    }

    /**
     * The component configurations
     */
    public HashicorpVaultConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(HashicorpVaultConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * The vault template
     */
    public VaultTemplate getVaultTemplate() {
        return vaultTemplate;
    }

    public void setVaultTemplate(VaultTemplate vaultTemplate) {
        this.vaultTemplate = vaultTemplate;
    }
}
