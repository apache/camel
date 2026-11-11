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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.vault.core.VaultKeyValueOperations;
import org.springframework.vault.core.VaultKeyValueOperationsSupport.KeyValueBackend;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The producer operations against a mocked {@link VaultTemplate}: the paths it reads, lists and writes, and the body it
 * writes.
 */
class HashicorpVaultProducerTest extends CamelTestSupport {

    private final VaultTemplate vault = mock(VaultTemplate.class);
    private final VaultKeyValueOperations keyValue = mock(VaultKeyValueOperations.class);

    @BeforeEach
    void bindVault() {
        context.getRegistry().bind("vault", vault);
        when(vault.opsForKeyValue(anyString(), any(KeyValueBackend.class))).thenReturn(keyValue);
        VaultResponse response = new VaultResponse();
        response.setData(Map.of("data", Map.of("id", "123")));
        when(vault.read(anyString())).thenReturn(response);
        when(vault.list(anyString())).thenReturn(List.of("hello"));
    }

    @Test
    void getSecretOnCloudWithoutNamespace() {
        Exchange exchange = send("getSecret&cloud=true", null);

        assertNull(exchange.getException());
        verify(vault).read("secret/data/hello");
        assertEquals(Map.of("data", Map.of("id", "123")), exchange.getMessage().getBody());
    }

    @Test
    void getSecretOnCloudWithNamespace() {
        Exchange exchange = send("getSecret&cloud=true&namespace=admin", null);

        assertNull(exchange.getException());
        verify(vault).read("admin/secret/data/hello");
    }

    @Test
    void getSecretOnPremiseIgnoresNamespace() {
        Exchange exchange = send("getSecret&namespace=admin", null);

        assertNull(exchange.getException());
        verify(vault).read("secret/data/hello");
    }

    @Test
    void listSecretsOnCloudWithoutNamespace() {
        Exchange exchange = send("listSecrets&cloud=true", null);

        assertNull(exchange.getException());
        verify(vault).list("secret/metadata/");
        assertEquals(List.of("hello"), exchange.getMessage().getBody());
    }

    @Test
    void listSecretsOnCloudWithNamespace() {
        Exchange exchange = send("listSecrets&cloud=true&namespace=admin", null);

        assertNull(exchange.getException());
        verify(vault).list("admin/secret/metadata/");
    }

    @Test
    void createAndDeleteSecretOnCloudWithNamespace() {
        assertNull(send("createSecret&cloud=true&namespace=admin", Map.of("id", "123")).getException());
        assertNull(send("deleteSecret&cloud=true&namespace=admin", null).getException());

        // the same engine path as getSecret and listSecrets read
        verify(vault, times(2)).opsForKeyValue("admin/secret", KeyValueBackend.versioned());
        verify(keyValue).put("hello", Map.of("id", "123"));
        verify(keyValue).delete("hello");
    }

    @Test
    void getSecretWithVersion() {
        Exchange exchange = template.request("hashicorp-vault:secret?vaultTemplate=#vault&secretPath=hello"
                                             + "&operation=getSecret",
                e -> e.getMessage().setHeader(HashicorpVaultConstants.SECRET_VERSION, "2"));

        assertNull(exchange.getException());
        verify(vault).read("secret/data/hello?version=2");
    }

    @Test
    void getSecretWithInvalidVersion() {
        Exchange exchange = template.request("hashicorp-vault:secret?vaultTemplate=#vault&secretPath=hello"
                                             + "&operation=getSecret",
                e -> e.getMessage().setHeader(HashicorpVaultConstants.SECRET_VERSION, "2&list=true"));

        IllegalArgumentException cause = assertInstanceOf(IllegalArgumentException.class, exchange.getException());
        assertTrue(cause.getMessage().contains(HashicorpVaultConstants.SECRET_VERSION), cause.getMessage());
        verify(vault, never()).read(anyString());
    }

    @Test
    void createSecretFromMap() {
        assertNull(send("createSecret", Map.of("id", "123")).getException());

        verify(keyValue).put("hello", Map.of("id", "123"));
    }

    @Test
    void createSecretFromJsonText() {
        assertNull(send("createSecret", "{\"id\":\"123\"}").getException());

        verify(keyValue).put("hello", Map.of("id", "123"));
    }

    @Test
    void createSecretFromJsonBytes() {
        assertNull(send("createSecret", "{\"id\":\"123\"}".getBytes(StandardCharsets.UTF_8)).getException());

        verify(keyValue).put("hello", Map.of("id", "123"));
    }

    @Test
    void createSecretFromJsonStream() {
        Exchange exchange = send("createSecret",
                new ByteArrayInputStream("{\"id\":\"123\"}".getBytes(StandardCharsets.UTF_8)));

        assertNull(exchange.getException());
        verify(keyValue).put("hello", Map.of("id", "123"));
    }

    @Test
    void createSecretFromTextThatIsNotAJsonObject() {
        Exchange exchange = send("createSecret", "my-password");

        IllegalArgumentException cause = assertInstanceOf(IllegalArgumentException.class, exchange.getException());
        // the text may be the secret itself, so it is not in the message
        assertFalse(cause.getMessage().contains("my-password"), cause.getMessage());
        assertNull(cause.getCause());
        verify(keyValue, never()).put(anyString(), any());
    }

    @Test
    void createSecretFromJsonArray() {
        Exchange exchange = send("createSecret", "[\"123\"]");

        assertInstanceOf(IllegalArgumentException.class, exchange.getException());
        verify(keyValue, never()).put(anyString(), any());
    }

    @Test
    void createSecretWithoutBody() {
        Exchange exchange = send("createSecret", null);

        // no new, empty version of the secret
        assertInstanceOf(InvalidPayloadException.class, exchange.getException());
        verify(keyValue, never()).put(anyString(), any());
    }

    private Exchange send(String operationAndOptions, Object body) {
        return template.request("hashicorp-vault:secret?vaultTemplate=#vault&secretPath=hello&operation="
                                + operationAndOptions,
                e -> e.getMessage().setBody(body));
    }
}
