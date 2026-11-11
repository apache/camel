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

import java.io.File;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;

import javax.xml.transform.Source;

import com.azure.core.util.polling.SyncPoller;
import com.azure.security.keyvault.secrets.models.DeletedSecret;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;
import com.azure.security.keyvault.secrets.models.SecretProperties;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.StreamCache;
import org.apache.camel.WrappedFile;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

public class KeyVaultProducer extends DefaultProducer {

    public static final String MISSING_SECRET_NAME = "Secret Name must be specified";
    public static final String MISSING_SECRET_PROPERTIES = "Secret Properties must be specified";

    // a date or a time, such as an OffsetDateTime in a Map, is written as ISO-8601 text
    private static final ObjectMapper JSON_MAPPER = JsonMapper.builderWithJackson2Defaults()
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

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
            case updateSecretProperties:
                updateSecretProperties(exchange);
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
            throw new IllegalArgumentException(MISSING_SECRET_NAME);
        }
        KeyVaultSecret p = getEndpoint().getSecretClient()
                .setSecret(new KeyVaultSecret(secretName, getSecretValue(exchange.getMessage())));
        Message message = getMessageForResponse(exchange);
        message.setBody(p);
    }

    /**
     * The value of the secret to create. Text, bytes, a stream or a file is read as text. A Map, a List or a POJO is
     * stored as JSON, of which the azure properties function can read a field. Another value, such as a number, a UUID
     * or an object that Jackson cannot serialize, is read as text, as before.
     */
    private static String getSecretValue(Message message) throws InvalidPayloadException {
        final Object body = message.getBody();
        if (body instanceof Map || body instanceof Collection) {
            try {
                return JSON_MAPPER.writeValueAsString(body);
            } catch (JacksonException e) {
                throw new IllegalArgumentException(
                        "The " + body.getClass().getName() + " body of createSecret cannot be stored as JSON", e);
            }
        }
        if (body != null && !isPayload(body)) {
            final JsonNode json = toJson(body);
            // Jackson writes a value, such as a UUID, as a JSON string: it is stored as its text
            if (json != null && json.isContainer()) {
                return json.toString();
            }
        }
        return message.getMandatoryBody(String.class);
    }

    /**
     * Whether the body is a payload, which is read as text: text, bytes, a stream, a file, or an XML Source, which
     * Jackson would write as a bean and not as the XML.
     */
    private static boolean isPayload(Object body) {
        return body instanceof CharSequence || body instanceof byte[]
                || body instanceof InputStream || body instanceof Reader || body instanceof StreamCache
                || body instanceof File || body instanceof Path || body instanceof WrappedFile
                || body instanceof Source;
    }

    private static JsonNode toJson(Object body) {
        try {
            return JSON_MAPPER.valueToTree(body);
        } catch (JacksonException e) {
            // not a POJO that Jackson can serialize, such as an object without properties
            return null;
        }
    }

    private void getSecret(Exchange exchange) {
        final String secretName = exchange.getMessage().getHeader(KeyVaultConstants.SECRET_NAME, String.class);
        if (ObjectHelper.isEmpty(secretName)) {
            throw new IllegalArgumentException(MISSING_SECRET_NAME);
        }
        KeyVaultSecret p = getEndpoint().getSecretClient()
                .getSecret(secretName);
        Message message = getMessageForResponse(exchange);
        message.setBody(p.getValue());
    }

    private void updateSecretProperties(Exchange exchange) {
        final SecretProperties secretProperties
                = exchange.getMessage().getHeader(KeyVaultConstants.SECRET_PROPERTIES, SecretProperties.class);
        if (ObjectHelper.isEmpty(secretProperties)) {
            throw new IllegalArgumentException(MISSING_SECRET_PROPERTIES);
        }
        SecretProperties p = getEndpoint().getSecretClient()
                .updateSecretProperties(secretProperties);
        Message message = getMessageForResponse(exchange);
        message.setBody(p);
    }

    private void deleteSecret(Exchange exchange) {
        final String secretName = exchange.getMessage().getHeader(KeyVaultConstants.SECRET_NAME, String.class);
        if (ObjectHelper.isEmpty(secretName)) {
            throw new IllegalArgumentException(MISSING_SECRET_NAME);
        }
        SyncPoller<DeletedSecret, Void> p = getEndpoint().getSecretClient()
                .beginDeleteSecret(secretName);
        // the final result of the poller is Void: the deleted secret is the value of the last poll response
        DeletedSecret deletedSecret = p.waitForCompletion().getValue();
        Message message = getMessageForResponse(exchange);
        message.setBody(deletedSecret);
    }

    private void purgeDeletedSecret(Exchange exchange) {
        final String secretName = exchange.getMessage().getHeader(KeyVaultConstants.SECRET_NAME, String.class);
        if (ObjectHelper.isEmpty(secretName)) {
            throw new IllegalArgumentException(MISSING_SECRET_NAME);
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
