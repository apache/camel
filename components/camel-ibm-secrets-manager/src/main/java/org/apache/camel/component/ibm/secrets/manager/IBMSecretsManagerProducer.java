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
            throw new IllegalArgumentException("Secret Name must be specified");
        }
        arbitrarySecretResourceBuilder.payload(exchange.getMessage().getBody(String.class));
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
            throw new IllegalArgumentException("Secret Name must be specified");
        }
        kvSecretResourceBuilder.data(exchange.getMessage().getBody(Map.class));
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
            throw new IllegalArgumentException("Secret ID must be specified");
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
            default:
                throw new IllegalArgumentException("Unsupported Secret Type");
        }
    }

    private void deleteSecret(Exchange exchange) {
        DeleteSecretOptions.Builder deleteSecretOptionsBuilder = new DeleteSecretOptions.Builder();
        if (ObjectHelper.isNotEmpty(exchange.getMessage().getHeader(IBMSecretsManagerConstants.SECRET_ID, String.class))) {
            deleteSecretOptionsBuilder.id(exchange.getMessage().getHeader(IBMSecretsManagerConstants.SECRET_ID, String.class));
        } else {
            throw new IllegalArgumentException("Secret ID must be specified");
        }
        getEndpoint().getSecretManager().deleteSecret(deleteSecretOptionsBuilder.build()).execute();
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
