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

import java.util.List;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.springframework.vault.core.VaultKeyValueOperations;
import org.springframework.vault.core.VaultKeyValueOperationsSupport;
import org.springframework.vault.support.VaultResponse;

public class HashicorpVaultProducer extends DefaultProducer {
    public HashicorpVaultProducer(final Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        HashicorpVaultOperation operation = determineOperation(exchange);
        switch (operation) {
            case createSecret:
                createSecret(exchange);
                break;
            case getSecret:
                getSecret(exchange);
                break;
            case deleteSecret:
                deleteSecret();
                break;
            case listSecrets:
                listSecrets(exchange);
                break;
            default:
                throw new IllegalArgumentException("Unsupported operation");
        }
    }

    private void createSecret(Exchange exchange) {
        VaultKeyValueOperations keyValue
                = getEndpoint().getVaultTemplate().opsForKeyValue(getEndpoint().getConfiguration().getSecretsEngine(),
                        VaultKeyValueOperationsSupport.KeyValueBackend.versioned());
        keyValue.put(getEndpoint().getConfiguration().getSecretPath(), exchange.getMessage().getBody());
    }

    private void getSecret(Exchange exchange) {
        String secretPath;
        String secretVersion = null;
        if (ObjectHelper.isNotEmpty(exchange.getMessage().getHeader(HashicorpVaultConstants.SECRET_PATH))) {
            secretPath = exchange.getMessage().getHeader(HashicorpVaultConstants.SECRET_PATH, String.class);
        } else {
            throw new IllegalArgumentException("Secret Path must be specified");
        }
        if (ObjectHelper.isNotEmpty(exchange.getMessage().getHeader(HashicorpVaultConstants.SECRET_VERSION))) {
            secretVersion = exchange.getMessage().getHeader(HashicorpVaultConstants.SECRET_VERSION, String.class);
        }
        String completePath = getEndpoint().getConfiguration().getSecretsEngine() + "/" + "data" + "/" + secretPath;
        if (ObjectHelper.isNotEmpty(secretVersion)) {
            completePath = completePath + "?version=" + secretVersion;
        }
        VaultResponse rawSecret = getEndpoint().getVaultTemplate().read(completePath);
        exchange.getMessage().setBody(rawSecret.getData());
    }

    private void deleteSecret() {
        VaultKeyValueOperations keyValue
                = getEndpoint().getVaultTemplate().opsForKeyValue(getEndpoint().getConfiguration().getSecretsEngine(),
                        VaultKeyValueOperationsSupport.KeyValueBackend.versioned());
        keyValue.delete(getEndpoint().getConfiguration().getSecretPath());
    }

    private void listSecrets(Exchange exchange) {
        List<String> secretsList = getEndpoint().getVaultTemplate()
                .list(getEndpoint().getConfiguration().getSecretsEngine() + "/" + "metadata" + "/");
        exchange.getMessage().setBody(secretsList);
    }

    @Override
    public HashicorpVaultEndpoint getEndpoint() {
        return (HashicorpVaultEndpoint) super.getEndpoint();
    }

    public HashicorpVaultConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    public static Message getMessageForResponse(final Exchange exchange) {
        return exchange.getMessage();
    }

    private HashicorpVaultOperation determineOperation(Exchange exchange) {
        HashicorpVaultOperation operation
                = exchange.getIn().getHeader(HashicorpVaultConstants.OPERATION, HashicorpVaultOperation.class);
        if (operation == null) {
            operation = getConfiguration().getOperation();
        }
        return operation;
    }
}
