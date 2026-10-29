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
package org.apache.camel.component.ibm.secrets.manager;

import com.ibm.cloud.sdk.core.security.IamAuthenticator;
import com.ibm.cloud.secrets_manager_sdk.secrets_manager.v2.SecretsManager;
import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;

/**
 * Manage secrets in IBM Secrets Manager Service
 */
@UriEndpoint(firstVersion = "4.11.0", scheme = "ibm-secrets-manager", title = "IBM Secrets Manager",
             syntax = "ibm-secrets-manager:label", category = {
                     Category.CLOUD, Category.CLOUD },
             producerOnly = true,
             headersClass = IBMSecretsManagerConstants.class)
@BodyTypes(producerIn = @BodyType(value = { Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE },
                                  byOption = {
                                          @BodyType.When(option = "operation", value = "createKVSecret",
                                                         carriers = Carrier.MAP),
                                          @BodyType.When(option = "operation", value = "updateSecret",
                                                         carriers = {
                                                                 Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE,
                                                                 Carrier.MAP }),
                                          @BodyType.When(option = "operation", value = "getSecret", carriers = Carrier.ANY),
                                          @BodyType.When(option = "operation", value = "deleteSecret", carriers = Carrier.ANY),
                                          @BodyType.When(option = "operation", value = "listSecrets", carriers = Carrier.ANY),
                                          @BodyType.When(option = "operation", value = "listSecretVersions",
                                                         carriers = Carrier.ANY) },
                                  description = "The payload of the secret for createArbitrarySecret, the default operation, as"
                                                + " text: bytes, a stream or a file are read as text, a Map or a POJO is stored as"
                                                + " its toString(), and no body fails. createKVSecret needs a Map of the keys and"
                                                + " values: a JSON text fails, so unmarshal it first. updateSecret takes text for"
                                                + " an arbitrary secret and a Map for a kv secret, unless the"
                                                + " CamelIbmSecretsManagerSecretPayload or CamelIbmSecretsManagerSecretData header"
                                                + " is set. The other operations take the secret id from a header"),
           producerOut = @BodyType(value = Carrier.TEXT, javaType = "java.lang.String",
                                   byOption = {
                                           @BodyType.When(option = "operation", value = "getSecret",
                                                          carriers = { Carrier.TEXT, Carrier.MAP, Carrier.POJO }),
                                           @BodyType.When(option = "operation", value = "deleteSecret",
                                                          carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "operation", value = "listSecrets",
                                                          carriers = Carrier.POJO,
                                                          javaType = "com.ibm.cloud.secrets_manager_sdk.secrets_manager.v2.model.SecretMetadataPaginatedCollection"),
                                           @BodyType.When(option = "operation", value = "updateSecret",
                                                          carriers = Carrier.POJO),
                                           @BodyType.When(option = "operation", value = "listSecretVersions",
                                                          carriers = Carrier.POJO,
                                                          javaType = "com.ibm.cloud.secrets_manager_sdk.secrets_manager.v2.model.SecretVersionMetadataCollection") },
                                   description = "The id of the new secret for createArbitrarySecret and createKVSecret. getSecret"
                                                 + " gives the payload text of an arbitrary secret, the Map of a kv secret, or the"
                                                 + " Secret of the IBM SDK for service credentials, and fails for other secret"
                                                 + " types. updateSecret gives the SecretVersion of the IBM SDK, the list"
                                                 + " operations the collection of the IBM SDK, and deleteSecret leaves the body"
                                                 + " unchanged"))
public class IBMSecretsManagerEndpoint extends DefaultEndpoint {

    private SecretsManager secretManager;

    @UriParam
    private IBMSecretsManagerConfiguration configuration;

    public IBMSecretsManagerEndpoint(final String uri, final Component component,
                                     final IBMSecretsManagerConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public void doInit() throws Exception {
        super.doInit();
        secretManager = createSecretManager();
    }

    private SecretsManager createSecretManager() {
        SecretsManager secretsManager;

        IamAuthenticator iamAuthenticator = new IamAuthenticator.Builder()
                .apikey(getConfiguration().getToken())
                .build();
        secretsManager = new SecretsManager("Camel Secrets Manager Service", iamAuthenticator);
        secretsManager.setServiceUrl(getConfiguration().getServiceUrl());

        return secretsManager;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new IBMSecretsManagerProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Consumer not supported");
    }

    /**
     * The component configurations
     */
    public IBMSecretsManagerConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(IBMSecretsManagerConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * The Secrets Manager
     */
    public SecretsManager getSecretManager() {
        return secretManager;
    }

    public void setSecretManager(SecretsManager secretManager) {
        this.secretManager = secretManager;
    }
}
