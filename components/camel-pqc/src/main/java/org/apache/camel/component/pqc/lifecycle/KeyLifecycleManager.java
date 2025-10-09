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
import java.time.Duration;
import java.util.List;

/**
 * Interface for managing the lifecycle of PQC keys including generation, rotation, and expiration.
 */
public interface KeyLifecycleManager {

    /**
     * Generate a new key pair for the specified algorithm
     */
    KeyPair generateKeyPair(String algorithm, String keyId) throws Exception;

    /**
     * Generate a key pair with specific parameters
     */
    KeyPair generateKeyPair(String algorithm, String keyId, Object parameterSpec) throws Exception;

    /**
     * Export a key pair to the specified format
     */
    byte[] exportKey(KeyPair keyPair, KeyFormat format, boolean includePrivate) throws Exception;

    /**
     * Export public key only
     */
    byte[] exportPublicKey(KeyPair keyPair, KeyFormat format) throws Exception;

    /**
     * Import a key pair from bytes
     */
    KeyPair importKey(byte[] keyData, KeyFormat format, String algorithm) throws Exception;

    /**
     * Rotate a key - generates new key and deprecates old one
     */
    KeyPair rotateKey(String oldKeyId, String newKeyId, String algorithm) throws Exception;

    /**
     * Store key pair with metadata
     */
    void storeKey(String keyId, KeyPair keyPair, KeyMetadata metadata) throws Exception;

    /**
     * Retrieve key pair by ID
     */
    KeyPair getKey(String keyId) throws Exception;

    /**
     * Get key metadata
     */
    KeyMetadata getKeyMetadata(String keyId) throws Exception;

    /**
     * Update key metadata
     */
    void updateKeyMetadata(String keyId, KeyMetadata metadata) throws Exception;

    /**
     * Delete key
     */
    void deleteKey(String keyId) throws Exception;

    /**
     * List all keys
     */
    List<KeyMetadata> listKeys() throws Exception;

    /**
     * Check if a key needs rotation based on age or usage
     */
    boolean needsRotation(String keyId, Duration maxAge, long maxUsage) throws Exception;

    /**
     * Mark a key as expired
     */
    void expireKey(String keyId) throws Exception;

    /**
     * Mark a key as revoked
     */
    void revokeKey(String keyId, String reason) throws Exception;

    enum KeyFormat {
        PEM,
        DER,
        PKCS8,
        X509
    }
}
