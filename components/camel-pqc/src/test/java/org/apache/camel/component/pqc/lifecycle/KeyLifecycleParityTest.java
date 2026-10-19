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

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Security;
import java.util.List;

import org.bouncycastle.jcajce.spec.MLDSAParameterSpec;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behaviour the five KeyLifecycleManager implementations are expected to share, but did not.
 */
class KeyLifecycleParityTest {

    @TempDir
    Path keyDirectory;

    @BeforeEach
    void addProviders() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        if (Security.getProvider(BouncyCastlePQCProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastlePQCProvider());
        }
    }

    private static KeyPair mldsaKeyPair() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("ML-DSA", "BC");
        kpg.initialize(MLDSAParameterSpec.ml_dsa_65, new SecureRandom());
        return kpg.generateKeyPair();
    }

    @Test
    void fileBasedListKeysSeesAKeyWrittenAfterStartup() throws Exception {
        FileBasedKeyLifecycleManager writer = new FileBasedKeyLifecycleManager(keyDirectory.toString());
        FileBasedKeyLifecycleManager reader = new FileBasedKeyLifecycleManager(keyDirectory.toString());

        assertTrue(reader.listKeys().isEmpty(), "nothing stored yet");

        // a second process, a restored backup or a sidecar writing into the same directory
        writer.generateKeyPair("MLDSA", "late-key");

        List<KeyMetadata> keys = reader.listKeys();
        assertEquals(1, keys.size(), "listKeys must scan the directory, not an in-process cache");
        assertEquals("late-key", keys.get(0).getKeyId());
    }

    @Test
    void fileBasedListKeysIgnoresNonMetadataFiles() throws Exception {
        FileBasedKeyLifecycleManager manager = new FileBasedKeyLifecycleManager(keyDirectory.toString());
        manager.generateKeyPair("MLDSA", "a-key");
        Files.writeString(keyDirectory.resolve("notes.txt"), "not a key");

        List<KeyMetadata> keys = manager.listKeys();
        assertEquals(1, keys.size(), "only .metadata files describe a key");
        assertEquals("a-key", keys.get(0).getKeyId());
    }

    @Test
    void aCombinedPemExportCanBeImportedBack() throws Exception {
        KeyPair original = mldsaKeyPair();

        byte[] pem = KeyFormatConverter.exportKeyPair(original, KeyLifecycleManager.KeyFormat.PEM, true);
        String text = new String(pem, java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(text.contains("-----BEGIN PUBLIC KEY-----"), "the export must carry the public block");
        assertTrue(text.contains("-----BEGIN PRIVATE KEY-----"), "the export must carry the private block");

        // before the fix, stripping only the PRIVATE armour left the PUBLIC armour in the Base64 input
        assertNotNull(KeyFormatConverter.importPrivateKey(pem, KeyLifecycleManager.KeyFormat.PEM, "ML-DSA"));
        assertNotNull(KeyFormatConverter.importPublicKey(pem, KeyLifecycleManager.KeyFormat.PEM, "ML-DSA"));

        assertEquals(original.getPrivate(),
                KeyFormatConverter.importPrivateKey(pem, KeyLifecycleManager.KeyFormat.PEM, "ML-DSA"));
        assertEquals(original.getPublic(),
                KeyFormatConverter.importPublicKey(pem, KeyLifecycleManager.KeyFormat.PEM, "ML-DSA"));
    }

    @Test
    void aPublicOnlyPemStillRoundTrips() throws Exception {
        KeyPair original = mldsaKeyPair();

        byte[] pem = KeyFormatConverter.exportKeyPair(original, KeyLifecycleManager.KeyFormat.PEM, false);

        assertEquals(original.getPublic(),
                KeyFormatConverter.importPublicKey(pem, KeyLifecycleManager.KeyFormat.PEM, "ML-DSA"));
    }

    @Test
    void updatingMetadataDoesNotRewriteTheKeyFiles() throws Exception {
        FileBasedKeyLifecycleManager manager = new FileBasedKeyLifecycleManager(keyDirectory.toString());
        manager.generateKeyPair("MLDSA", "a-key");

        Path privateKeyFile = Files.list(keyDirectory)
                .filter(p -> p.getFileName().toString().contains("private"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no private key file was written"));
        long before = Files.getLastModifiedTime(privateKeyFile).toMillis();

        KeyMetadata metadata = manager.getKeyMetadata("a-key");
        metadata.setStatus(KeyMetadata.KeyStatus.PENDING_ROTATION);
        manager.updateKeyMetadata("a-key", metadata);

        assertEquals(before, Files.getLastModifiedTime(privateKeyFile).toMillis(),
                "a metadata update must not rewrite the private key");
        assertEquals(KeyMetadata.KeyStatus.PENDING_ROTATION,
                new FileBasedKeyLifecycleManager(keyDirectory.toString()).getKeyMetadata("a-key").getStatus(),
                "the new status must be readable by another instance");
    }
}
