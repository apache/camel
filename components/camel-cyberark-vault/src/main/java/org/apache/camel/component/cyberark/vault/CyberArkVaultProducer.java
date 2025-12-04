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

package org.apache.camel.component.cyberark.vault;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.cyberark.vault.client.ConjurClient;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The CyberArk Vault producer.
 */
public class CyberArkVaultProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(CyberArkVaultProducer.class);

    public CyberArkVaultProducer(Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        // Determine operation: Header > URI parameter
        CyberArkVaultOperations operation =
                exchange.getMessage().getHeader(CyberArkVaultConstants.OPERATION, CyberArkVaultOperations.class);
        if (operation == null) {
            operation = getConfiguration().getOperation();
        }

        // Priority: Header > URI parameter
        String secretId = exchange.getMessage().getHeader(CyberArkVaultConstants.SECRET_ID, String.class);
        if (ObjectHelper.isEmpty(secretId)) {
            secretId = getConfiguration().getSecretId();
        }

        if (ObjectHelper.isEmpty(secretId)) {
            throw new IllegalArgumentException(
                    "Secret ID must be specified either as a URI parameter (secretId=...) or as a message header ("
                            + CyberArkVaultConstants.SECRET_ID + ")");
        }

        ConjurClient client = getEndpoint().getConjurClient();
        Message message = exchange.getMessage();

        switch (operation) {
            case getSecret:
                String version = message.getHeader(CyberArkVaultConstants.SECRET_VERSION, String.class);
                LOG.trace("Retrieving secret from CyberArk Conjur with id: {}", secretId);
                String secretValue = client.retrieveSecret(secretId, version);
                message.setBody(secretValue);
                message.setHeader(CyberArkVaultConstants.SECRET_ID, secretId);
                message.setHeader(CyberArkVaultConstants.SECRET_VALUE, secretValue);
                if (version != null) {
                    message.setHeader(CyberArkVaultConstants.SECRET_VERSION, version);
                }
                break;
            case createSecret:
                String secretValueToCreate = message.getHeader(CyberArkVaultConstants.SECRET_VALUE, String.class);
                if (ObjectHelper.isEmpty(secretValueToCreate)) {
                    secretValueToCreate = message.getBody(String.class);
                }
                if (ObjectHelper.isEmpty(secretValueToCreate)) {
                    throw new IllegalArgumentException(
                            "Secret value must be specified either as message body or as a message header ("
                                    + CyberArkVaultConstants.SECRET_VALUE + ")");
                }
                LOG.trace("Creating/updating secret in CyberArk Conjur with id: {}", secretId);
                client.createSecret(secretId, secretValueToCreate);
                message.setHeader(CyberArkVaultConstants.SECRET_ID, secretId);
                break;
            default:
                throw new IllegalArgumentException("Unsupported operation: " + operation);
        }
    }

    @Override
    public CyberArkVaultEndpoint getEndpoint() {
        return (CyberArkVaultEndpoint) super.getEndpoint();
    }

    private CyberArkVaultConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }
}
