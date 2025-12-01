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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import org.apache.camel.component.pqc.PQCKeyEncapsulationAlgorithms;
import org.apache.camel.component.pqc.PQCSignatureAlgorithms;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * File-based implementation of KeyLifecycleManager. Stores keys and metadata in a specified directory with secure
 * permissions.
 */
public class FileBasedKeyLifecycleManager implements KeyLifecycleManager {

    private static final Logger LOG = LoggerFactory.getLogger(FileBasedKeyLifecycleManager.class);

    private final Path keyDirectory;
    private final ConcurrentHashMap<String, KeyPair> keyCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, KeyMetadata> metadataCache = new ConcurrentHashMap<>();

    public FileBasedKeyLifecycleManager(String keyDirectoryPath) throws IOException {
        this.keyDirectory = Paths.get(keyDirectoryPath);
        Files.createDirectories(keyDirectory);
        LOG.info("Initialized FileBasedKeyLifecycleManager with directory: {}", keyDirectory);
        loadExistingKeys();
    }

    @Override
    public KeyPair generateKeyPair(String algorithm, String keyId) throws Exception {
        return generateKeyPair(algorithm, keyId, null);
    }

    @Override
    public KeyPair generateKeyPair(String algorithm, String keyId, Object parameterSpec) throws Exception {
        LOG.info("Generating key pair for algorithm: {}, keyId: {}", algorithm, keyId);

        KeyPairGenerator generator;
        String provider = determineProvider(algorithm);

        if (provider != null) {
            generator = KeyPairGenerator.getInstance(getAlgorithmName(algorithm), provider);
        } else {
            generator = KeyPairGenerator.getInstance(getAlgorithmName(algorithm));
        }

        // Initialize with parameter spec if provided
        if (parameterSpec != null) {
            if (parameterSpec instanceof AlgorithmParameterSpec) {
                generator.initialize((AlgorithmParameterSpec) parameterSpec, new SecureRandom());
            } else if (parameterSpec instanceof Integer) {
                generator.initialize((Integer) parameterSpec, new SecureRandom());
            }
        } else {
            // Use default parameter spec for the algorithm
            AlgorithmParameterSpec defaultSpec = getDefaultParameterSpec(algorithm);
            if (defaultSpec != null) {
                generator.initialize(defaultSpec, new SecureRandom());
            } else {
                generator.initialize(getDefaultKeySize(algorithm), new SecureRandom());
            }
        }

        KeyPair keyPair = generator.generateKeyPair();

        // Create metadata
        KeyMetadata metadata = new KeyMetadata(keyId, algorithm);
        metadata.setDescription("Generated on " + new Date());

        // Store the key
        storeKey(keyId, keyPair, metadata);

        LOG.info("Generated key pair: {}", metadata);
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
        // Try to import as private key first (which includes public key)
        try {
            PrivateKey privateKey = KeyFormatConverter.importPrivateKey(keyData, format, getAlgorithmName(algorithm));
            // For PQC algorithms, we need to derive the public key
            // This is algorithm-specific and may require regeneration
            LOG.warn("Importing private key only - public key derivation may be needed");
            return new KeyPair(null, privateKey);
        } catch (Exception e) {
            // Try as public key only
            PublicKey publicKey = KeyFormatConverter.importPublicKey(keyData, format, getAlgorithmName(algorithm));
            return new KeyPair(publicKey, null);
        }
    }

    @Override
    public KeyPair rotateKey(String oldKeyId, String newKeyId, String algorithm) throws Exception {
        LOG.info("Rotating key from {} to {}", oldKeyId, newKeyId);

        // Get old key metadata
        KeyMetadata oldMetadata = getKeyMetadata(oldKeyId);
        if (oldMetadata == null) {
            throw new IllegalArgumentException("Old key not found: " + oldKeyId);
        }

        // Mark old key as deprecated
        oldMetadata.setStatus(KeyMetadata.KeyStatus.DEPRECATED);
        updateKeyMetadata(oldKeyId, oldMetadata);

        // Generate new key
        KeyPair newKeyPair = generateKeyPair(algorithm, newKeyId);

        LOG.info("Key rotation completed: {} -> {}", oldKeyId, newKeyId);
        return newKeyPair;
    }

    @Override
    public void storeKey(String keyId, KeyPair keyPair, KeyMetadata metadata) throws Exception {
        // Store key pair
        Path keyFile = getKeyFile(keyId);
        try (ObjectOutputStream oos = new ObjectOutputStream(
                new BufferedOutputStream(
                        Files.newOutputStream(keyFile, StandardOpenOption.CREATE,
                                StandardOpenOption.TRUNCATE_EXISTING)))) {
            oos.writeObject(keyPair);
        }

        // Store metadata
        Path metadataFile = getMetadataFile(keyId);
        try (ObjectOutputStream oos = new ObjectOutputStream(
                new BufferedOutputStream(
                        Files.newOutputStream(metadataFile, StandardOpenOption.CREATE,
                                StandardOpenOption.TRUNCATE_EXISTING)))) {
            oos.writeObject(metadata);
        }

        // Update caches
        keyCache.put(keyId, keyPair);
        metadataCache.put(keyId, metadata);

        LOG.debug("Stored key and metadata for: {}", keyId);
    }

    @Override
    public KeyPair getKey(String keyId) throws Exception {
        if (keyCache.containsKey(keyId)) {
            return keyCache.get(keyId);
        }

        Path keyFile = getKeyFile(keyId);
        if (!Files.exists(keyFile)) {
            throw new IllegalArgumentException("Key not found: " + keyId);
        }

        try (ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(Files.newInputStream(keyFile)))) {
            KeyPair keyPair = (KeyPair) ois.readObject();
            keyCache.put(keyId, keyPair);
            return keyPair;
        }
    }

    @Override
    public KeyMetadata getKeyMetadata(String keyId) throws Exception {
        if (metadataCache.containsKey(keyId)) {
            return metadataCache.get(keyId);
        }

        Path metadataFile = getMetadataFile(keyId);
        if (!Files.exists(metadataFile)) {
            return null;
        }

        try (ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(Files.newInputStream(metadataFile)))) {
            KeyMetadata metadata = (KeyMetadata) ois.readObject();
            metadataCache.put(keyId, metadata);
            return metadata;
        }
    }

    @Override
    public void updateKeyMetadata(String keyId, KeyMetadata metadata) throws Exception {
        Path metadataFile = getMetadataFile(keyId);
        try (ObjectOutputStream oos = new ObjectOutputStream(
                new BufferedOutputStream(
                        Files.newOutputStream(metadataFile, StandardOpenOption.CREATE,
                                StandardOpenOption.TRUNCATE_EXISTING)))) {
            oos.writeObject(metadata);
        }
        metadataCache.put(keyId, metadata);
    }

    @Override
    public void deleteKey(String keyId) throws Exception {
        Files.deleteIfExists(getKeyFile(keyId));
        Files.deleteIfExists(getMetadataFile(keyId));
        keyCache.remove(keyId);
        metadataCache.remove(keyId);
        LOG.info("Deleted key: {}", keyId);
    }

    @Override
    public List<KeyMetadata> listKeys() throws Exception {
        return new ArrayList<>(metadataCache.values());
    }

    @Override
    public boolean needsRotation(String keyId, Duration maxAge, long maxUsage) throws Exception {
        KeyMetadata metadata = getKeyMetadata(keyId);
        if (metadata == null) {
            return false;
        }

        if (metadata.needsRotation()) {
            return true;
        }

        if (maxAge != null && metadata.getAgeInDays() > maxAge.toDays()) {
            return true;
        }

        if (maxUsage > 0 && metadata.getUsageCount() >= maxUsage) {
            return true;
        }

        return false;
    }

    @Override
    public void expireKey(String keyId) throws Exception {
        KeyMetadata metadata = getKeyMetadata(keyId);
        if (metadata != null) {
            metadata.setStatus(KeyMetadata.KeyStatus.EXPIRED);
            updateKeyMetadata(keyId, metadata);
            LOG.info("Expired key: {}", keyId);
        }
    }

    @Override
    public void revokeKey(String keyId, String reason) throws Exception {
        KeyMetadata metadata = getKeyMetadata(keyId);
        if (metadata != null) {
            metadata.setStatus(KeyMetadata.KeyStatus.REVOKED);
            metadata.setDescription((metadata.getDescription() != null ? metadata.getDescription() + "; " : "")
                                    + "Revoked: " + reason);
            updateKeyMetadata(keyId, metadata);
            LOG.info("Revoked key: {} - {}", keyId, reason);
        }
    }

    private void loadExistingKeys() {
        try (Stream<Path> files = Files.list(keyDirectory)) {
            files.filter(path -> path.toString().endsWith(".metadata"))
                    .forEach(path -> {
                        try {
                            String keyId = path.getFileName().toString().replace(".metadata", "");
                            KeyMetadata metadata = getKeyMetadata(keyId);
                            if (metadata != null) {
                                LOG.debug("Loaded existing key: {}", metadata);
                            }
                        } catch (Exception e) {
                            LOG.warn("Failed to load key metadata: {}", path, e);
                        }
                    });
        } catch (IOException e) {
            LOG.warn("Failed to list existing keys", e);
        }
    }

    private Path getKeyFile(String keyId) {
        return keyDirectory.resolve(keyId + ".key");
    }

    private Path getMetadataFile(String keyId) {
        return keyDirectory.resolve(keyId + ".metadata");
    }

    private String determineProvider(String algorithm) {
        try {
            PQCSignatureAlgorithms sigAlg = PQCSignatureAlgorithms.valueOf(algorithm);
            return sigAlg.getBcProvider();
        } catch (IllegalArgumentException e1) {
            try {
                PQCKeyEncapsulationAlgorithms kemAlg = PQCKeyEncapsulationAlgorithms.valueOf(algorithm);
                return kemAlg.getBcProvider();
            } catch (IllegalArgumentException e2) {
                return null;
            }
        }
    }

    private String getAlgorithmName(String algorithm) {
        try {
            return PQCSignatureAlgorithms.valueOf(algorithm).getAlgorithm();
        } catch (IllegalArgumentException e1) {
            try {
                return PQCKeyEncapsulationAlgorithms.valueOf(algorithm).getAlgorithm();
            } catch (IllegalArgumentException e2) {
                return algorithm;
            }
        }
    }

    private AlgorithmParameterSpec getDefaultParameterSpec(String algorithm) {
        // Provide default parameter specs for PQC algorithms
        try {
            switch (algorithm) {
                case "DILITHIUM":
                    return org.bouncycastle.pqc.jcajce.spec.DilithiumParameterSpec.dilithium2;
                case "MLDSA":
                case "SLHDSA":
                    // These use default initialization
                    return null;
                case "FALCON":
                    return org.bouncycastle.pqc.jcajce.spec.FalconParameterSpec.falcon_512;
                case "SPHINCSPLUS":
                    return org.bouncycastle.pqc.jcajce.spec.SPHINCSPlusParameterSpec.sha2_128s;
                case "XMSS":
                    return new org.bouncycastle.pqc.jcajce.spec.XMSSParameterSpec(
                            10,
                            org.bouncycastle.pqc.jcajce.spec.XMSSParameterSpec.SHA256);
                case "XMSSMT":
                    return org.bouncycastle.pqc.jcajce.spec.XMSSMTParameterSpec.XMSSMT_SHA2_20d2_256;
                case "LMS":
                case "HSS":
                    return new org.bouncycastle.pqc.jcajce.spec.LMSKeyGenParameterSpec(
                            org.bouncycastle.pqc.crypto.lms.LMSigParameters.lms_sha256_n32_h10,
                            org.bouncycastle.pqc.crypto.lms.LMOtsParameters.sha256_n32_w4);
                case "MLKEM":
                case "KYBER":
                    // These use default initialization
                    return null;
                case "NTRU":
                    return org.bouncycastle.pqc.jcajce.spec.NTRUParameterSpec.ntruhps2048509;
                case "NTRULPRime":
                    return org.bouncycastle.pqc.jcajce.spec.NTRULPRimeParameterSpec.ntrulpr653;
                case "SNTRUPrime":
                    return org.bouncycastle.pqc.jcajce.spec.SNTRUPrimeParameterSpec.sntrup761;
                case "SABER":
                    return org.bouncycastle.pqc.jcajce.spec.SABERParameterSpec.lightsaberkem128r3;
                case "FRODO":
                    return org.bouncycastle.pqc.jcajce.spec.FrodoParameterSpec.frodokem640aes;
                case "BIKE":
                    return org.bouncycastle.pqc.jcajce.spec.BIKEParameterSpec.bike128;
                case "HQC":
                    return org.bouncycastle.pqc.jcajce.spec.HQCParameterSpec.hqc128;
                case "CMCE":
                    return org.bouncycastle.pqc.jcajce.spec.CMCEParameterSpec.mceliece348864;
                default:
                    return null;
            }
        } catch (Exception e) {
            LOG.warn("Failed to create default parameter spec for algorithm: {}", algorithm, e);
            return null;
        }
    }

    private int getDefaultKeySize(String algorithm) {
        // Default key sizes for different algorithms
        // For PQC algorithms, key size is usually determined by parameter specs
        return 256;
    }
}
