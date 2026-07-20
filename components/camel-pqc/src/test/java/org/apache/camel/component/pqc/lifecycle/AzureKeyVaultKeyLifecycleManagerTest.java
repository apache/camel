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
package org.apache.camel.component.pqc.lifecycle;

import java.security.KeyPair;
import java.security.Security;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.azure.core.exception.ResourceNotFoundException;
import com.azure.core.http.rest.PagedIterable;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;
import com.azure.security.keyvault.secrets.models.SecretProperties;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test for AzureKeyVaultKeyLifecycleManager backed by a mocked SecretClient simulating the Key Vault secret store.
 */
class AzureKeyVaultKeyLifecycleManagerTest {

    private Map<String, String> store;
    private SecretClient client;
    private AzureKeyVaultKeyLifecycleManager manager;

    @BeforeAll
    static void setupProviders() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        if (Security.getProvider(BouncyCastlePQCProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastlePQCProvider());
        }
    }

    @BeforeEach
    void setup() {
        store = new ConcurrentHashMap<>();
        client = mockSecretClient(store);
        manager = new AzureKeyVaultKeyLifecycleManager(client, "pqc-keys");
    }

    @SuppressWarnings("unchecked")
    private static SecretClient mockSecretClient(Map<String, String> store) {
        SecretClient client = mock(SecretClient.class);
        when(client.setSecret(anyString(), anyString())).thenAnswer(inv -> {
            String name = inv.getArgument(0);
            String value = inv.getArgument(1);
            store.put(name, value);
            return new KeyVaultSecret(name, value);
        });
        when(client.getSecret(anyString())).thenAnswer(inv -> {
            String name = inv.getArgument(0);
            String value = store.get(name);
            if (value == null) {
                throw new ResourceNotFoundException("Secret not found: " + name, null);
            }
            return new KeyVaultSecret(name, value);
        });
        when(client.listPropertiesOfSecrets()).thenAnswer(inv -> {
            List<SecretProperties> properties = new ArrayList<>();
            for (String name : store.keySet()) {
                SecretProperties p = mock(SecretProperties.class);
                when(p.getName()).thenReturn(name);
                properties.add(p);
            }
            PagedIterable<SecretProperties> iterable = mock(PagedIterable.class);
            when(iterable.iterator()).thenReturn(properties.iterator());
            return iterable;
        });
        when(client.beginDeleteSecret(anyString())).thenAnswer(inv -> {
            String name = inv.getArgument(0);
            if (store.remove(name) == null) {
                throw new ResourceNotFoundException("Secret not found: " + name, null);
            }
            return null;
        });
        return client;
    }

    @Test
    void testGenerateStoresKeyPairAndMetadataAsSeparateSecrets() throws Exception {
        KeyPair keyPair = manager.generateKeyPair("MLDSA", "signing-key");

        assertNotNull(keyPair);
        assertTrue(store.containsKey("pqc-keys-signing-key-private"));
        assertTrue(store.containsKey("pqc-keys-signing-key-public"));
        assertTrue(store.containsKey("pqc-keys-signing-key-metadata"));
        assertEquals(3, store.size());
    }

    @Test
    void testKeyPairAndMetadataRoundTripThroughTheVault() throws Exception {
        KeyPair generated = manager.generateKeyPair("MLDSA", "roundtrip-key");

        // A fresh manager over the same vault has empty caches, so it must read back from the (mocked) store
        AzureKeyVaultKeyLifecycleManager fresh = new AzureKeyVaultKeyLifecycleManager(client, "pqc-keys");
        KeyPair loaded = fresh.getKey("roundtrip-key");

        assertArrayEquals(generated.getPrivate().getEncoded(), loaded.getPrivate().getEncoded());
        assertArrayEquals(generated.getPublic().getEncoded(), loaded.getPublic().getEncoded());

        KeyMetadata metadata = fresh.getKeyMetadata("roundtrip-key");
        assertNotNull(metadata);
        assertEquals("roundtrip-key", metadata.getKeyId());
        assertEquals("MLDSA", metadata.getAlgorithm());
        assertEquals(KeyMetadata.KeyStatus.ACTIVE, metadata.getStatus());
    }

    @Test
    void testListKeysReturnsOneEntryPerKey() throws Exception {
        manager.generateKeyPair("MLDSA", "key-one");
        manager.generateKeyPair("MLDSA", "key-two");

        List<KeyMetadata> keys = manager.listKeys();

        assertEquals(2, keys.size());
    }

    @Test
    void testDeleteKeyRemovesAllSecrets() throws Exception {
        manager.generateKeyPair("MLDSA", "doomed-key");
        assertEquals(3, store.size());

        manager.deleteKey("doomed-key");

        assertTrue(store.isEmpty());
        assertNull(manager.getKeyMetadata("doomed-key"));
    }

    @Test
    void testRotateKeyDeprecatesOldKey() throws Exception {
        manager.generateKeyPair("MLDSA", "old-key");

        KeyPair rotated = manager.rotateKey("old-key", "new-key", "MLDSA");

        assertNotNull(rotated);
        assertEquals(KeyMetadata.KeyStatus.DEPRECATED, manager.getKeyMetadata("old-key").getStatus());
        assertEquals(KeyMetadata.KeyStatus.ACTIVE, manager.getKeyMetadata("new-key").getStatus());
    }

    @Test
    void testExpireAndRevoke() throws Exception {
        manager.generateKeyPair("MLDSA", "state-key");

        manager.expireKey("state-key");
        assertEquals(KeyMetadata.KeyStatus.EXPIRED, manager.getKeyMetadata("state-key").getStatus());

        manager.revokeKey("state-key", "compromised");
        KeyMetadata metadata = manager.getKeyMetadata("state-key");
        assertEquals(KeyMetadata.KeyStatus.REVOKED, metadata.getStatus());
        assertTrue(metadata.getDescription().contains("Revoked: compromised"));
    }

    @Test
    void testNeedsRotationForUnknownKeyIsFalse() throws Exception {
        assertFalse(manager.needsRotation("no-such-key", null, 0));
    }

    @Test
    void testGetKeyMetadataReturnsNullWhenMissing() throws Exception {
        assertNull(manager.getKeyMetadata("missing-key"));
    }

    @Test
    void testKeyIdWithInvalidCharactersIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> manager.generateKeyPair("MLDSA", "bad/key"));
        assertThrows(IllegalArgumentException.class, () -> new AzureKeyVaultKeyLifecycleManager(client, "bad/prefix"));
    }
}
