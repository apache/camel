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

import java.util.Map;

import com.ibm.cloud.sdk.core.http.Response;
import com.ibm.cloud.secrets_manager_sdk.secrets_manager.v2.model.*;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;

public class IBMSecretsManagerProducer extends DefaultProducer {
    public IBMSecretsManagerProducer(final Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        IBMSecretsManagerOperation operation = determineOperation(exchange);
        switch (operation) {
            case createArbitrarySecret:
                createArbitratySecret(exchange);
                break;
            case createKVSecret:
                createKVSecret(exchange);
                break;
            case getSecret:
                getSecret(exchange);
                break;
            case deleteSecret:
                deleteSecret(exchange);
                break;
            case listSecrets:
                listSecrets(exchange);
                break;
            case updateSecret:
                updateSecret(exchange);
                break;
            case listSecretVersions:
                listSecretVersions(exchange);
                break;
            default:
                throw new IllegalArgumentException("Unsupported operation");
        }
    }

    private void createArbitratySecret(Exchange exchange) {
        ArbitrarySecretPrototype.Builder arbitrarySecretResourceBuilder = new ArbitrarySecretPrototype.Builder();
        if (ObjectHelper.isNotEmpty(exchange.getMessage().getHeader(IBMSecretsManagerConstants.SECRET_NAME, String.class))) {
            arbitrarySecretResourceBuilder
                    .name(exchange.getMessage().getHeader(IBMSecretsManagerConstants.SECRET_NAME, String.class));
        } else {
            throw new IllegalArgumentException(
                    "Secret Name must be specified. Set the header '" + IBMSecretsManagerConstants.SECRET_NAME
                                               + "' with the desired secret name.");
        }
        String payload = exchange.getMessage().getBody(String.class);
        if (ObjectHelper.isEmpty(payload)) {
            throw new IllegalArgumentException(
                    "Secret payload must be provided in the message body for createArbitrarySecret operation.");
        }
        arbitrarySecretResourceBuilder.payload(payload);
        arbitrarySecretResourceBuilder.secretType(ArbitrarySecretPrototype.SecretType.ARBITRARY);
        ArbitrarySecretPrototype arbitrarySecretResource = arbitrarySecretResourceBuilder.build();

        CreateSecretOptions createSecretOptions = new CreateSecretOptions.Builder()
                .secretPrototype(arbitrarySecretResource)
                .build();
        Response<Secret> createResp = getEndpoint().getSecretManager().createSecret(createSecretOptions).execute();

        exchange.getMessage().setBody(createResp.getResult().getId());
    }

    private void createKVSecret(Exchange exchange) {
        KVSecretPrototype.Builder kvSecretResourceBuilder = new KVSecretPrototype.Builder();
        if (ObjectHelper.isNotEmpty(exchange.getMessage().getHeader(IBMSecretsManagerConstants.SECRET_NAME, String.class))) {
            kvSecretResourceBuilder
                    .name(exchange.getMessage().getHeader(IBMSecretsManagerConstants.SECRET_NAME, String.class));
        } else {
            throw new IllegalArgumentException(
                    "Secret Name must be specified. Set the header '" + IBMSecretsManagerConstants.SECRET_NAME
                                               + "' with the desired secret name.");
        }
        Map<String, Object> data = exchange.getMessage().getBody(Map.class);
        if (ObjectHelper.isEmpty(data)) {
            throw new IllegalArgumentException(
                    "Secret data must be provided as a Map in the message body for createKVSecret operation.");
        }
        kvSecretResourceBuilder.data(data);
        kvSecretResourceBuilder.secretType(KVSecretPrototype.SecretType.KV);
        KVSecretPrototype kvSecretResource = kvSecretResourceBuilder.build();

        CreateSecretOptions createSecretOptions = new CreateSecretOptions.Builder()
                .secretPrototype(kvSecretResource)
                .build();
        Response<Secret> createResp = getEndpoint().getSecretManager().createSecret(createSecretOptions).execute();

        exchange.getMessage().setBody(createResp.getResult().getId());
    }

    private void getSecret(Exchange exchange) {
        GetSecretOptions.Builder getSecretOptionsBuilder = new GetSecretOptions.Builder();
        if (ObjectHelper.isNotEmpty(exchange.getMessage().getHeader(IBMSecretsManagerConstants.SECRET_ID, String.class))) {
            getSecretOptionsBuilder.id(exchange.getMessage().getHeader(IBMSecretsManagerConstants.SECRET_ID, String.class));
        } else {
            throw new IllegalArgumentException(
                    "Secret ID must be specified. Set the header '" + IBMSecretsManagerConstants.SECRET_ID
                                               + "' with the secret ID.");
        }
        Response<Secret> getResp = getEndpoint().getSecretManager().getSecret(getSecretOptionsBuilder.build()).execute();

        String secretType = getResp.getResult().getSecretType();
        switch (secretType) {
            case "arbitrary":
                exchange.getMessage().setBody(getResp.getResult().getPayload());
                break;
            case "kv":
                exchange.getMessage().setBody(getResp.getResult().getData());
                break;
            case "service_credentials":
                exchange.getMessage().setBody(getResp.getResult());
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported secret type '" + secretType
                                                   + "'. Supported types are: arbitrary, kv, service_credentials.");
        }
    }

    private void deleteSecret(Exchange exchange) {
        DeleteSecretOptions.Builder deleteSecretOptionsBuilder = new DeleteSecretOptions.Builder();
        if (ObjectHelper.isNotEmpty(exchange.getMessage().getHeader(IBMSecretsManagerConstants.SECRET_ID, String.class))) {
            deleteSecretOptionsBuilder.id(exchange.getMessage().getHeader(IBMSecretsManagerConstants.SECRET_ID, String.class));
        } else {
            throw new IllegalArgumentException(
                    "Secret ID must be specified. Set the header '" + IBMSecretsManagerConstants.SECRET_ID
                                               + "' with the secret ID to delete.");
        }
        getEndpoint().getSecretManager().deleteSecret(deleteSecretOptionsBuilder.build()).execute();
    }

    private void listSecrets(Exchange exchange) {
        Response<SecretMetadataPaginatedCollection> result = getEndpoint().getSecretManager().listSecrets().execute();
        exchange.getMessage().setBody(result.getResult());
    }

    private void updateSecret(Exchange exchange) {
        String secretId = exchange.getMessage().getHeader(IBMSecretsManagerConstants.SECRET_ID, String.class);
        if (ObjectHelper.isEmpty(secretId)) {
            throw new IllegalArgumentException("Secret ID must be specified for update operation");
        }

        // First, get the current secret to determine its type
        GetSecretOptions getSecretOptions = new GetSecretOptions.Builder()
                .id(secretId)
                .build();
        Response<Secret> getResp = getEndpoint().getSecretManager().getSecret(getSecretOptions).execute();
        String secretType = getResp.getResult().getSecretType();

        SecretVersionPrototype secretVersionPrototype;
        switch (secretType) {
            case "arbitrary":
                String payload = exchange.getMessage().getHeader(IBMSecretsManagerConstants.SECRET_PAYLOAD, String.class);
                if (ObjectHelper.isEmpty(payload)) {
                    payload = exchange.getMessage().getBody(String.class);
                }
                if (ObjectHelper.isEmpty(payload)) {
                    throw new IllegalArgumentException("Secret payload must be specified for arbitrary secret update");
                }
                ArbitrarySecretVersionPrototype.Builder arbitraryBuilder = new ArbitrarySecretVersionPrototype.Builder();
                arbitraryBuilder.payload(payload);
                secretVersionPrototype = arbitraryBuilder.build();
                break;
            case "kv":
                Map<String, Object> data = exchange.getMessage().getHeader(IBMSecretsManagerConstants.SECRET_DATA, Map.class);
                if (ObjectHelper.isEmpty(data)) {
                    data = exchange.getMessage().getBody(Map.class);
                }
                if (ObjectHelper.isEmpty(data)) {
                    throw new IllegalArgumentException("Secret data must be specified for KV secret update");
                }
                KVSecretVersionPrototype.Builder kvBuilder = new KVSecretVersionPrototype.Builder();
                kvBuilder.data(data);
                secretVersionPrototype = kvBuilder.build();
                break;
            default:
                throw new IllegalArgumentException("Unsupported secret type for update: " + secretType);
        }

        CreateSecretVersionOptions createSecretVersionOptions = new CreateSecretVersionOptions.Builder()
                .secretId(secretId)
                .secretVersionPrototype(secretVersionPrototype)
                .build();

        Response<SecretVersion> updateResp = getEndpoint().getSecretManager().createSecretVersion(createSecretVersionOptions)
                .execute();
        exchange.getMessage().setBody(updateResp.getResult());
    }

    private void listSecretVersions(Exchange exchange) {
        String secretId = exchange.getMessage().getHeader(IBMSecretsManagerConstants.SECRET_ID, String.class);
        if (ObjectHelper.isEmpty(secretId)) {
            throw new IllegalArgumentException("Secret ID must be specified for listSecretVersions operation");
        }

        ListSecretVersionsOptions listSecretVersionsOptions = new ListSecretVersionsOptions.Builder()
                .secretId(secretId)
                .build();

        Response<SecretVersionMetadataCollection> result
                = getEndpoint().getSecretManager().listSecretVersions(listSecretVersionsOptions).execute();
        exchange.getMessage().setBody(result.getResult());
    }

    @Override
    public IBMSecretsManagerEndpoint getEndpoint() {
        return (IBMSecretsManagerEndpoint) super.getEndpoint();
    }

    public IBMSecretsManagerConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    private IBMSecretsManagerOperation determineOperation(Exchange exchange) {
        IBMSecretsManagerOperation operation
                = exchange.getIn().getHeader(IBMSecretsManagerConstants.OPERATION, IBMSecretsManagerOperation.class);
        if (operation == null) {
            operation = getConfiguration().getOperation();
        }
        return operation;
    }
}
