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
import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.camel.component.pqc.PQCKeyEncapsulationAlgorithms;
import org.apache.camel.component.pqc.PQCSignatureAlgorithms;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * File-based implementation of KeyLifecycleManager. Stores private keys in PKCS#8 format, public keys in X.509 format,
 * and metadata as JSON. This is consistent with the encoding used by {@link AwsSecretsManagerKeyLifecycleManager} and
 * {@link HashicorpVaultKeyLifecycleManager}.
 * <p/>
 * For backward compatibility, keys stored in the legacy Java serialization format are automatically migrated to the new
 * standard format on first read.
 */
public class FileBasedKeyLifecycleManager implements KeyLifecycleManager {

    private static final Logger LOG = LoggerFactory.getLogger(FileBasedKeyLifecycleManager.class);

    private final Path keyDirectory;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, KeyPair> keyCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, KeyMetadata> metadataCache = new ConcurrentHashMap<>();

    public FileBasedKeyLifecycleManager(String keyDirectoryPath) throws IOException {
        this.keyDirectory = Paths.get(keyDirectoryPath);
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
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
            if (parameterSpec instanceof AlgorithmParameterSpec algorithmParamSpec) {
                generator.initialize(algorithmParamSpec, new SecureRandom());
            } else if (parameterSpec instanceof Integer keySize) {
                generator.initialize(keySize, new SecureRandom());
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
        // Store private key in PKCS#8 format
        Path privateKeyFile = getPrivateKeyFile(keyId);
        byte[] privateKeyBytes = keyPair.getPrivate().getEncoded();
        String privateKeyBase64 = Base64.getEncoder().encodeToString(privateKeyBytes);
        KeyFileData privateData = new KeyFileData(privateKeyBase64, "PKCS8", metadata.getAlgorithm());
        Files.writeString(privateKeyFile, objectMapper.writeValueAsString(privateData),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        // Store public key in X.509 format
        Path publicKeyFile = getPublicKeyFile(keyId);
        byte[] publicKeyBytes = keyPair.getPublic().getEncoded();
        String publicKeyBase64 = Base64.getEncoder().encodeToString(publicKeyBytes);
        KeyFileData publicData = new KeyFileData(publicKeyBase64, "X509", metadata.getAlgorithm());
        Files.writeString(publicKeyFile, objectMapper.writeValueAsString(publicData),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        // Store metadata as JSON
        Path metadataFile = getMetadataFile(keyId);
        MetadataFileData metadataData = MetadataFileData.fromKeyMetadata(metadata);
        Files.writeString(metadataFile, objectMapper.writeValueAsString(metadataData),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        // Remove legacy .key file if it exists (migration cleanup)
        Files.deleteIfExists(getLegacyKeyFile(keyId));

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

        Path privateKeyFile = getPrivateKeyFile(keyId);
        Path publicKeyFile = getPublicKeyFile(keyId);

        // Check for new format first
        if (Files.exists(privateKeyFile) && Files.exists(publicKeyFile)) {
            KeyPair keyPair = readStandardKeyPair(keyId, privateKeyFile, publicKeyFile);
            keyCache.put(keyId, keyPair);
            return keyPair;
        }

        // Fall back to legacy format for migration
        Path legacyKeyFile = getLegacyKeyFile(keyId);
        if (Files.exists(legacyKeyFile)) {
            return migrateLegacyKey(keyId);
        }

        throw new IllegalArgumentException("Key not found: " + keyId);
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

        String content = Files.readString(metadataFile, StandardCharsets.UTF_8);

        // Detect format: JSON starts with '{', legacy Java serialization starts with binary
        if (content.trim().startsWith("{")) {
            MetadataFileData data = objectMapper.readValue(content, MetadataFileData.class);
            KeyMetadata metadata = data.toKeyMetadata();
            metadataCache.put(keyId, metadata);
            return metadata;
        } else {
            // Legacy format - read via ObjectInputStream and migrate
            return migrateLegacyMetadata(keyId);
        }
    }

    @Override
    public void updateKeyMetadata(String keyId, KeyMetadata metadata) throws Exception {
        Path metadataFile = getMetadataFile(keyId);
        MetadataFileData metadataData = MetadataFileData.fromKeyMetadata(metadata);
        Files.writeString(metadataFile, objectMapper.writeValueAsString(metadataData),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        metadataCache.put(keyId, metadata);
    }

    @Override
    public void deleteKey(String keyId) throws Exception {
        Files.deleteIfExists(getPrivateKeyFile(keyId));
        Files.deleteIfExists(getPublicKeyFile(keyId));
        Files.deleteIfExists(getMetadataFile(keyId));
        Files.deleteIfExists(getLegacyKeyFile(keyId));
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

    private KeyPair readStandardKeyPair(String keyId, Path privateKeyFile, Path publicKeyFile) throws Exception {
        String privateJson = Files.readString(privateKeyFile, StandardCharsets.UTF_8);
        KeyFileData privateData = objectMapper.readValue(privateJson, KeyFileData.class);
        byte[] privateKeyBytes = Base64.getDecoder().decode(privateData.key());
        PKCS8EncodedKeySpec privateSpec = new PKCS8EncodedKeySpec(privateKeyBytes);

        String publicJson = Files.readString(publicKeyFile, StandardCharsets.UTF_8);
        KeyFileData publicData = objectMapper.readValue(publicJson, KeyFileData.class);
        byte[] publicKeyBytes = Base64.getDecoder().decode(publicData.key());
        X509EncodedKeySpec publicSpec = new X509EncodedKeySpec(publicKeyBytes);

        String algorithm = privateData.algorithm();
        String algorithmName = getAlgorithmName(algorithm);
        String provider = determineProvider(algorithm);

        KeyFactory keyFactory;
        if (provider != null) {
            keyFactory = KeyFactory.getInstance(algorithmName, provider);
        } else {
            keyFactory = KeyFactory.getInstance(algorithmName);
        }

        PrivateKey privateKey = keyFactory.generatePrivate(privateSpec);
        PublicKey publicKey = keyFactory.generatePublic(publicSpec);
        return new KeyPair(publicKey, privateKey);
    }

    /**
     * Migrates a legacy Java-serialized key file to the new PKCS#8/X.509 JSON format.
     */
    @SuppressWarnings("java:S4508")
    private KeyPair migrateLegacyKey(String keyId) throws Exception {
        LOG.info("Migrating legacy key format to PKCS#8/X.509 for keyId: {}", keyId);
        Path legacyKeyFile = getLegacyKeyFile(keyId);

        KeyPair keyPair;
        try (ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(Files.newInputStream(legacyKeyFile)))) {
            keyPair = (KeyPair) ois.readObject();
        }

        // Read or migrate metadata
        KeyMetadata metadata = getKeyMetadata(keyId);
        if (metadata == null) {
            metadata = new KeyMetadata(keyId, "UNKNOWN");
            metadata.setDescription("Migrated from legacy format");
        }

        // Re-store in the new format (this also removes the legacy .key file)
        storeKey(keyId, keyPair, metadata);
        LOG.info("Successfully migrated key to PKCS#8/X.509 format: {}", keyId);
        return keyPair;
    }

    /**
     * Migrates a legacy Java-serialized metadata file to JSON format.
     */
    @SuppressWarnings("java:S4508")
    private KeyMetadata migrateLegacyMetadata(String keyId) throws Exception {
        LOG.info("Migrating legacy metadata format to JSON for keyId: {}", keyId);
        Path metadataFile = getMetadataFile(keyId);

        KeyMetadata metadata;
        try (ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(Files.newInputStream(metadataFile)))) {
            metadata = (KeyMetadata) ois.readObject();
        }

        // Re-store in JSON format
        MetadataFileData metadataData = MetadataFileData.fromKeyMetadata(metadata);
        Files.writeString(metadataFile, objectMapper.writeValueAsString(metadataData),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        metadataCache.put(keyId, metadata);
        LOG.info("Successfully migrated metadata to JSON format: {}", keyId);
        return metadata;
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

    private Path getPrivateKeyFile(String keyId) {
        return keyDirectory.resolve(keyId + ".private.json");
    }

    private Path getPublicKeyFile(String keyId) {
        return keyDirectory.resolve(keyId + ".public.json");
    }

    private Path getMetadataFile(String keyId) {
        return keyDirectory.resolve(keyId + ".metadata");
    }

    private Path getLegacyKeyFile(String keyId) {
        return keyDirectory.resolve(keyId + ".key");
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

    /**
     * JSON structure for storing key data (private or public) in files.
     */
    record KeyFileData(
            @JsonProperty("key") String key,
            @JsonProperty("format") String format,
            @JsonProperty("algorithm") String algorithm) {

        @JsonCreator
        KeyFileData {
        }
    }

    /**
     * JSON structure for storing key metadata in files.
     */
    static final class MetadataFileData {
        @JsonProperty("keyId")
        String keyId;
        @JsonProperty("algorithm")
        String algorithm;
        @JsonProperty("createdAt")
        String createdAt;
        @JsonProperty("lastUsedAt")
        String lastUsedAt;
        @JsonProperty("expiresAt")
        String expiresAt;
        @JsonProperty("nextRotationAt")
        String nextRotationAt;
        @JsonProperty("usageCount")
        long usageCount;
        @JsonProperty("status")
        String status;
        @JsonProperty("description")
        String description;

        MetadataFileData() {
        }

        static MetadataFileData fromKeyMetadata(KeyMetadata metadata) {
            MetadataFileData data = new MetadataFileData();
            data.keyId = metadata.getKeyId();
            data.algorithm = metadata.getAlgorithm();
            data.createdAt = metadata.getCreatedAt().toString();
            data.lastUsedAt = metadata.getLastUsedAt() != null ? metadata.getLastUsedAt().toString() : null;
            data.expiresAt = metadata.getExpiresAt() != null ? metadata.getExpiresAt().toString() : null;
            data.nextRotationAt = metadata.getNextRotationAt() != null ? metadata.getNextRotationAt().toString() : null;
            data.usageCount = metadata.getUsageCount();
            data.status = metadata.getStatus().name();
            data.description = metadata.getDescription();
            return data;
        }

        KeyMetadata toKeyMetadata() {
            KeyMetadata metadata = new KeyMetadata(keyId, algorithm, Instant.parse(createdAt));
            if (lastUsedAt != null) {
                metadata.setLastUsedAt(Instant.parse(lastUsedAt));
            }
            if (expiresAt != null) {
                metadata.setExpiresAt(Instant.parse(expiresAt));
            }
            if (nextRotationAt != null) {
                metadata.setNextRotationAt(Instant.parse(nextRotationAt));
            }
            metadata.setUsageCount(usageCount);
            metadata.setStatus(KeyMetadata.KeyStatus.valueOf(status));
            metadata.setDescription(description);
            return metadata;
        }
    }
}
