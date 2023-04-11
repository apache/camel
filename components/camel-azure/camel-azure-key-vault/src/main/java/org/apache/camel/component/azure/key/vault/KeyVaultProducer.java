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

import com.azure.core.util.polling.SyncPoller;
import com.azure.security.keyvault.secrets.models.DeletedSecret;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;

public class KeyVaultProducer extends DefaultProducer {
    public KeyVaultProducer(final Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        KeyVaultOperation operation = determineOperation(exchange);
        switch (operation) {
            case createSecret:
                createSecret(exchange);
                break;
            case getSecret:
                getSecret(exchange);
                break;
            case deleteSecret:
                deleteSecret(exchange);
                break;
            case purgeDeletedSecret:
                purgeDeletedSecret(exchange);
                break;
            default:
                throw new IllegalArgumentException("Unsupported operation");
        }
    }

    private void createSecret(Exchange exchange) throws InvalidPayloadException {
        final String secretName = exchange.getMessage().getHeader(KeyVaultConstants.SECRET_NAME, String.class);
        if (ObjectHelper.isEmpty(secretName)) {
            throw new IllegalArgumentException("Secret Name must be specified for createSecret Operation");
        }
        KeyVaultSecret p = getEndpoint().getSecretClient()
                .setSecret(new KeyVaultSecret(secretName, exchange.getMessage().getMandatoryBody(String.class)));
        Message message = getMessageForResponse(exchange);
        message.setBody(p);
    }

    private void getSecret(Exchange exchange) {
        final String secretName = exchange.getMessage().getHeader(KeyVaultConstants.SECRET_NAME, String.class);
        if (ObjectHelper.isEmpty(secretName)) {
            throw new IllegalArgumentException("Secret Name must be specified for createSecret Operation");
        }
        KeyVaultSecret p = getEndpoint().getSecretClient()
                .getSecret(secretName);
        Message message = getMessageForResponse(exchange);
        message.setBody(p.getValue());
    }

    private void deleteSecret(Exchange exchange) {
        final String secretName = exchange.getMessage().getHeader(KeyVaultConstants.SECRET_NAME, String.class);
        if (ObjectHelper.isEmpty(secretName)) {
            throw new IllegalArgumentException("Secret Name must be specified for createSecret Operation");
        }
        SyncPoller<DeletedSecret, Void> p = getEndpoint().getSecretClient()
                .beginDeleteSecret(secretName);
        p.waitForCompletion();
        Message message = getMessageForResponse(exchange);
        message.setBody(p.getFinalResult());
    }

    private void purgeDeletedSecret(Exchange exchange) {
        final String secretName = exchange.getMessage().getHeader(KeyVaultConstants.SECRET_NAME, String.class);
        if (ObjectHelper.isEmpty(secretName)) {
            throw new IllegalArgumentException("Secret Name must be specified for createSecret Operation");
        }
        getEndpoint().getSecretClient()
                .purgeDeletedSecret(secretName);
    }

    @Override
    public KeyVaultEndpoint getEndpoint() {
        return (KeyVaultEndpoint) super.getEndpoint();
    }

    public KeyVaultConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    public static Message getMessageForResponse(final Exchange exchange) {
        return exchange.getMessage();
    }

    private KeyVaultOperation determineOperation(Exchange exchange) {
        KeyVaultOperation operation = exchange.getIn().getHeader(KeyVaultConstants.OPERATION, KeyVaultOperation.class);
        if (operation == null) {
            operation = getConfiguration().getOperation();
        }
        return operation;
    }
}
