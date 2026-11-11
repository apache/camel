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

import java.io.File;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.StreamCache;
import org.apache.camel.WrappedFile;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.springframework.vault.core.VaultKeyValueOperations;
import org.springframework.vault.core.VaultKeyValueOperationsSupport;
import org.springframework.vault.support.VaultResponse;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public class HashicorpVaultProducer extends DefaultProducer {

    private static final ObjectMapper MAPPER = JsonMapper.builderWithJackson2Defaults().build();

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
                deleteSecret(exchange);
                break;
            case listSecrets:
                listSecrets(exchange);
                break;
            default:
                throw new IllegalArgumentException("Unsupported operation");
        }
    }

    private void createSecret(Exchange exchange) throws InvalidPayloadException {
        String secretPath = getSecretPath(exchange);
        Object secret = getSecretBody(exchange);
        VaultKeyValueOperations keyValue
                = getEndpoint().getVaultTemplate().opsForKeyValue(getEnginePath(),
                        VaultKeyValueOperationsSupport.KeyValueBackend.versioned());
        keyValue.put(secretPath, secret);
    }

    private void getSecret(Exchange exchange) {
        String secretPath = getSecretPath(exchange);
        String secretVersion = null;
        if (ObjectHelper.isNotEmpty(exchange.getMessage().getHeader(HashicorpVaultConstants.SECRET_VERSION))) {
            secretVersion = exchange.getMessage().getHeader(HashicorpVaultConstants.SECRET_VERSION, String.class);
        }
        String completePath = getEnginePath() + "/" + "data" + "/" + secretPath;
        if (ObjectHelper.isNotEmpty(secretVersion)) {
            completePath = completePath + "?version=" + parseSecretVersion(secretVersion);
        }
        VaultResponse rawSecret = getEndpoint().getVaultTemplate().read(completePath);
        if (rawSecret != null) {
            exchange.getMessage().setBody(rawSecret.getData());
        } else {
            exchange.getMessage().setBody(null);
        }
    }

    private void deleteSecret(Exchange exchange) {
        String secretPath = getSecretPath(exchange);
        VaultKeyValueOperations keyValue
                = getEndpoint().getVaultTemplate().opsForKeyValue(getEnginePath(),
                        VaultKeyValueOperationsSupport.KeyValueBackend.versioned());
        keyValue.delete(secretPath);
    }

    private void listSecrets(Exchange exchange) {
        List<String> secretsList = getEndpoint().getVaultTemplate().list(getEnginePath() + "/" + "metadata" + "/");
        exchange.getMessage().setBody(secretsList);
    }

    /**
     * The path of the secrets engine: in the namespace when running on HashiCorp Cloud with a namespace, else the
     * engine itself (as the secret refresh task does).
     */
    private String getEnginePath() {
        String engine = getConfiguration().getSecretsEngine();
        if (getConfiguration().isCloud() && ObjectHelper.isNotEmpty(getConfiguration().getNamespace())) {
            return getConfiguration().getNamespace() + "/" + engine;
        }
        return engine;
    }

    /**
     * The fields of the secret to write: a Map or a POJO as is, and text, bytes, a stream or a file as a JSON object.
     */
    private static Object getSecretBody(Exchange exchange) throws InvalidPayloadException {
        // no body would write a new, empty version of the secret
        Object body = exchange.getMessage().getMandatoryBody();
        if (body instanceof CharSequence || body instanceof byte[] || body instanceof InputStream
                || body instanceof Reader || body instanceof StreamCache || body instanceof WrappedFile
                || body instanceof File || body instanceof Path) {
            String json = exchange.getMessage().getMandatoryBody(String.class);
            Map<?, ?> fields;
            try {
                fields = MAPPER.readValue(json, Map.class);
            } catch (JacksonException e) {
                // the cause is left out on purpose, as its message can quote the text of the secret
                fields = null;
            }
            if (fields == null) {
                throw new IllegalArgumentException(
                        "The body of createSecret must be a Map, a POJO or a JSON object with the fields of the secret");
            }
            return fields;
        }
        return body;
    }

    private static int parseSecretVersion(String secretVersion) {
        try {
            int version = Integer.parseInt(secretVersion.trim());
            if (version >= 0) {
                return version;
            }
        } catch (NumberFormatException e) {
            // not a number
        }
        throw new IllegalArgumentException(
                "The " + HashicorpVaultConstants.SECRET_VERSION + " header must be a version number, was: " + secretVersion);
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

    private String getSecretPath(Exchange exchange) {
        String secretPath = exchange.getMessage().getHeader(HashicorpVaultConstants.SECRET_PATH,
                getEndpoint().getConfiguration().getSecretPath(), String.class);
        if (ObjectHelper.isEmpty(secretPath)) {
            throw new IllegalArgumentException("Secret Path must be specified");
        }
        return secretPath;
    }
}
