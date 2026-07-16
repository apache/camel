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
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * In-memory implementation of {@link KeyLifecycleManager}. Keys and metadata are held in {@link ConcurrentHashMap}s and
 * are lost when the application stops - nothing is written to disk or to a remote store.
 * <p/>
 * Because it performs no I/O it is a good fit for tests, development and ephemeral workloads. All operations are
 * thread-safe. In addition to the {@link KeyLifecycleManager} contract it offers {@link #clear()} and {@link #size()},
 * which are convenient for resetting state between tests.
 */
public class InMemoryKeyLifecycleManager implements KeyLifecycleManager {

    private static final Logger LOG = LoggerFactory.getLogger(InMemoryKeyLifecycleManager.class);

    private final Map<String, KeyPair> keys = new ConcurrentHashMap<>();
    private final Map<String, KeyMetadata> metadata = new ConcurrentHashMap<>();

    @Override
    public KeyPair generateKeyPair(String algorithm, String keyId) throws Exception {
        return generateKeyPair(algorithm, keyId, null);
    }

    @Override
    public KeyPair generateKeyPair(String algorithm, String keyId, Object parameterSpec) throws Exception {
        LOG.info("Generating key pair for algorithm: {}, keyId: {}", algorithm, keyId);

        KeyPair keyPair = KeyAlgorithmSupport.generateKeyPair(algorithm, parameterSpec);

        KeyMetadata keyMetadata = new KeyMetadata(keyId, algorithm);
        keyMetadata.setDescription("Generated on " + new Date());
        storeKey(keyId, keyPair, keyMetadata);

        LOG.info("Generated key pair: {}", keyMetadata);
        return keyPair;
    }

    @Override
    public byte[] exportKey(KeyPair keyPair, KeyFormat format, boolean includePrivate) throws Exception {
        return KeyFormatConverter.exportKeyPair(keyPair, format, includePrivate);
    }

    @Override
    public byte[] exportPublicKey(KeyPair keyPair, KeyFormat format) throws Exception {
        return KeyFormatConverter.exportPublicKey(keyPair.getPublic(), format);
    }

    @Override
    public KeyPair importKey(byte[] keyData, KeyFormat format, String algorithm) throws Exception {
        String algorithmName = KeyAlgorithmSupport.getAlgorithmName(algorithm);
        // Try to import as a private key first, and fall back to a public key
        try {
            PrivateKey privateKey = KeyFormatConverter.importPrivateKey(keyData, format, algorithmName);
            LOG.warn("Importing private key only - public key derivation may be needed");
            return new KeyPair(null, privateKey);
        } catch (Exception e) {
            PublicKey publicKey = KeyFormatConverter.importPublicKey(keyData, format, algorithmName);
            return new KeyPair(publicKey, null);
        }
    }

    @Override
    public KeyPair rotateKey(String oldKeyId, String newKeyId, String algorithm) throws Exception {
        LOG.info("Rotating key from {} to {}", oldKeyId, newKeyId);

        KeyMetadata oldMetadata = getKeyMetadata(oldKeyId);
        if (oldMetadata == null) {
            throw new IllegalArgumentException("Old key not found: " + oldKeyId);
        }

        // Deprecate the old key, then generate its replacement
        oldMetadata.setStatus(KeyMetadata.KeyStatus.DEPRECATED);
        updateKeyMetadata(oldKeyId, oldMetadata);

        KeyPair newKeyPair = generateKeyPair(algorithm, newKeyId);

        LOG.info("Key rotation completed: {} -> {}", oldKeyId, newKeyId);
        return newKeyPair;
    }

    @Override
    public void storeKey(String keyId, KeyPair keyPair, KeyMetadata keyMetadata) {
        keys.put(keyId, keyPair);
        metadata.put(keyId, keyMetadata);
    }

    @Override
    public KeyPair getKey(String keyId) {
        KeyPair keyPair = keys.get(keyId);
        if (keyPair == null) {
            throw new IllegalArgumentException("Key not found: " + keyId);
        }
        return keyPair;
    }

    @Override
    public KeyMetadata getKeyMetadata(String keyId) {
        return metadata.get(keyId);
    }

    @Override
    public void updateKeyMetadata(String keyId, KeyMetadata keyMetadata) {
        metadata.put(keyId, keyMetadata);
    }

    @Override
    public void deleteKey(String keyId) {
        keys.remove(keyId);
        metadata.remove(keyId);
        LOG.info("Deleted key: {}", keyId);
    }

    @Override
    public List<KeyMetadata> listKeys() {
        return new ArrayList<>(metadata.values());
    }

    @Override
    public boolean needsRotation(String keyId, Duration maxAge, long maxUsage) {
        KeyMetadata keyMetadata = metadata.get(keyId);
        if (keyMetadata == null) {
            return false;
        }
        if (keyMetadata.needsRotation()) {
            return true;
        }
        if (maxAge != null && keyMetadata.getAgeInDays() > maxAge.toDays()) {
            return true;
        }
        return maxUsage > 0 && keyMetadata.getUsageCount() >= maxUsage;
    }

    @Override
    public void expireKey(String keyId) {
        KeyMetadata keyMetadata = metadata.get(keyId);
        if (keyMetadata != null) {
            keyMetadata.setStatus(KeyMetadata.KeyStatus.EXPIRED);
            updateKeyMetadata(keyId, keyMetadata);
            LOG.info("Expired key: {}", keyId);
        }
    }

    @Override
    public void revokeKey(String keyId, String reason) {
        KeyMetadata keyMetadata = metadata.get(keyId);
        if (keyMetadata != null) {
            keyMetadata.setStatus(KeyMetadata.KeyStatus.REVOKED);
            keyMetadata.setDescription(
                    (keyMetadata.getDescription() != null ? keyMetadata.getDescription() + "; " : "")
                                       + "Revoked: " + reason);
            updateKeyMetadata(keyId, keyMetadata);
            LOG.info("Revoked key: {} - {}", keyId, reason);
        }
    }

    /**
     * Removes every key and its metadata. Convenient for resetting the manager between tests.
     */
    public void clear() {
        keys.clear();
        metadata.clear();
    }

    /**
     * The number of keys currently held by this manager.
     */
    public int size() {
        return metadata.size();
    }
}
