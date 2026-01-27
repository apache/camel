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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.component.pqc.PQCKeyEncapsulationAlgorithms;
import org.apache.camel.component.pqc.PQCSignatureAlgorithms;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultKeyValueOperations;
import org.springframework.vault.core.VaultKeyValueOperationsSupport;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

/**
 * HashiCorp Vault-based implementation of KeyLifecycleManager using Spring Vault. Stores keys and metadata in Vault's
 * KV secrets engine with centralized secret management, audit logging, and fine-grained access control.
 *
 * Features: - Centralized secret management via HashiCorp Vault - Automatic audit logging - Fine-grained access control
 * with Vault policies - Encryption at rest - High availability support - In-memory caching for performance
 *
 * Configuration: - host: Vault server host (e.g., localhost) - port: Vault server port (default: 8200) - scheme:
 * http/https (default: https) - token: Vault authentication token - secretsEngine: KV secrets engine name (default:
 * secret) - keyPrefix: Prefix for all key paths in Vault (default: pqc/keys)
 *
 * This implementation uses Spring Vault (spring-vault-core) consistent with the camel-hashicorp-vault component.
 */
public class HashicorpVaultKeyLifecycleManager implements KeyLifecycleManager {

    private static final Logger LOG = LoggerFactory.getLogger(HashicorpVaultKeyLifecycleManager.class);

    private final VaultTemplate vaultTemplate;
    private final String secretsEngine;
    private final String keyPrefix;
    private final boolean cloud;
    private final String namespace;
    private final ConcurrentHashMap<String, KeyPair> keyCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, KeyMetadata> metadataCache = new ConcurrentHashMap<>();

    /**
     * Create a HashicorpVaultKeyLifecycleManager with an existing VaultTemplate
     *
     * @param vaultTemplate Configured VaultTemplate instance
     * @param secretsEngine KV secrets engine name
     * @param keyPrefix     Prefix for key paths in Vault
     */
    public HashicorpVaultKeyLifecycleManager(VaultTemplate vaultTemplate, String secretsEngine, String keyPrefix) {
        this(vaultTemplate, secretsEngine, keyPrefix, false, null);
    }

    /**
     * Create a HashicorpVaultKeyLifecycleManager with an existing VaultTemplate including HCP Vault support
     *
     * @param vaultTemplate Configured VaultTemplate instance
     * @param secretsEngine KV secrets engine name
     * @param keyPrefix     Prefix for key paths in Vault
     * @param cloud         Whether Vault is deployed on HashiCorp Cloud Platform
     * @param namespace     Namespace for HCP Vault (required if cloud is true)
     */
    public HashicorpVaultKeyLifecycleManager(VaultTemplate vaultTemplate, String secretsEngine, String keyPrefix,
                                             boolean cloud, String namespace) {
        this.vaultTemplate = vaultTemplate;
        this.secretsEngine = secretsEngine != null ? secretsEngine : "secret";
        this.keyPrefix = keyPrefix != null ? keyPrefix : "pqc/keys";
        this.cloud = cloud;
        this.namespace = namespace;

        LOG.info(
                "Initialized HashicorpVaultKeyLifecycleManager with secretsEngine: {}, keyPrefix: {}, cloud: {}, namespace: {}",
                this.secretsEngine, this.keyPrefix, this.cloud, this.namespace);

        try {
            loadExistingKeys();
        } catch (Exception e) {
            LOG.warn("Failed to load existing keys from Vault", e);
        }
    }

    /**
     * Create a HashicorpVaultKeyLifecycleManager with default settings
     *
     * @param host   Vault server host
     * @param port   Vault server port
     * @param scheme Vault scheme (http/https)
     * @param token  Vault token for authentication
     */
    public HashicorpVaultKeyLifecycleManager(String host, int port, String scheme, String token) {
        this(host, port, scheme, token, "secret", "pqc/keys", false, null);
    }

    /**
     * Create a HashicorpVaultKeyLifecycleManager with custom settings
     *
     * @param host          Vault server host
     * @param port          Vault server port
     * @param scheme        Vault scheme (http/https)
     * @param token         Vault token for authentication
     * @param secretsEngine KV secrets engine name
     * @param keyPrefix     Prefix for key paths in Vault
     */
    public HashicorpVaultKeyLifecycleManager(String host, int port, String scheme, String token, String secretsEngine,
                                             String keyPrefix) {
        this(host, port, scheme, token, secretsEngine, keyPrefix, false, null);
    }

    /**
     * Create a HashicorpVaultKeyLifecycleManager with full settings including HCP Vault support
     *
     * @param host          Vault server host
     * @param port          Vault server port
     * @param scheme        Vault scheme (http/https)
     * @param token         Vault token for authentication
     * @param secretsEngine KV secrets engine name
     * @param keyPrefix     Prefix for key paths in Vault
     * @param cloud         Whether Vault is deployed on HashiCorp Cloud Platform
     * @param namespace     Namespace for HCP Vault (required if cloud is true)
     */
    public HashicorpVaultKeyLifecycleManager(String host, int port, String scheme, String token, String secretsEngine,
                                             String keyPrefix, boolean cloud, String namespace) {
        this.secretsEngine = secretsEngine != null ? secretsEngine : "secret";
        this.keyPrefix = keyPrefix != null ? keyPrefix : "pqc/keys";
        this.cloud = cloud;
        this.namespace = namespace;

        // Create VaultEndpoint
        VaultEndpoint vaultEndpoint = new VaultEndpoint();
        vaultEndpoint.setHost(host);
        vaultEndpoint.setPort(port);
        vaultEndpoint.setScheme(scheme != null ? scheme : "https");

        // Create VaultTemplate with TokenAuthentication
        this.vaultTemplate = new VaultTemplate(vaultEndpoint, new TokenAuthentication(token));

        LOG.info(
                "Initialized HashicorpVaultKeyLifecycleManager with Vault at: {}://{}:{}, secretsEngine: {}, keyPrefix: {}, cloud: {}, namespace: {}",
                scheme, host, port, this.secretsEngine, this.keyPrefix, this.cloud, this.namespace);

        try {
            loadExistingKeys();
        } catch (Exception e) {
            LOG.warn("Failed to load existing keys from Vault", e);
        }
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

        LOG.info("Generated key pair in Vault: {}", metadata);
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

        LOG.info("Key rotation completed in Vault: {} -> {}", oldKeyId, newKeyId);
        return newKeyPair;
    }

    @Override
    public void storeKey(String keyId, KeyPair keyPair, KeyMetadata metadata) throws Exception {
        // Use PKCS#8 format for private key and X.509 for public key (industry standard)
        // This is more secure than Java serialization
        byte[] privateKeyBytes = keyPair.getPrivate().getEncoded(); // PKCS#8 format
        byte[] publicKeyBytes = keyPair.getPublic().getEncoded(); // X.509/SubjectPublicKeyInfo format
        String privateKeyBase64 = Base64.getEncoder().encodeToString(privateKeyBytes);
        String publicKeyBase64 = Base64.getEncoder().encodeToString(publicKeyBytes);
        String metadataBase64 = serializeMetadata(metadata);

        VaultKeyValueOperations keyValue = vaultTemplate.opsForKeyValue(secretsEngine,
                VaultKeyValueOperationsSupport.KeyValueBackend.versioned());

        // Store private key separately (strict ACL recommended in production)
        Map<String, Object> privateKeyData = new HashMap<>();
        privateKeyData.put("key", privateKeyBase64);
        privateKeyData.put("format", "PKCS8");
        privateKeyData.put("algorithm", metadata.getAlgorithm());
        keyValue.put(getKeyPath(keyId) + "/private", privateKeyData);

        // Store public key separately (can have read-only ACL)
        Map<String, Object> publicKeyData = new HashMap<>();
        publicKeyData.put("key", publicKeyBase64);
        publicKeyData.put("format", "X509");
        publicKeyData.put("algorithm", metadata.getAlgorithm());
        keyValue.put(getKeyPath(keyId) + "/public", publicKeyData);

        // Store metadata separately
        Map<String, Object> metadataData = new HashMap<>();
        metadataData.put("metadata", metadataBase64);
        metadataData.put("keyId", keyId);
        metadataData.put("algorithm", metadata.getAlgorithm());
        keyValue.put(getKeyPath(keyId) + "/metadata", metadataData);

        // Update caches
        keyCache.put(keyId, keyPair);
        metadataCache.put(keyId, metadata);

        LOG.debug("Stored private key, public key, and metadata separately in Vault for: {}", keyId);
    }

    @Override
    public KeyPair getKey(String keyId) throws Exception {
        // Check cache first
        if (keyCache.containsKey(keyId)) {
            return keyCache.get(keyId);
        }

        // Read private key from Vault
        String privateKeyPath = buildDataPath(getKeyPath(keyId) + "/private");
        VaultResponse privateResponse = vaultTemplate.read(privateKeyPath);

        if (privateResponse == null || privateResponse.getData() == null) {
            throw new IllegalArgumentException("Private key not found in Vault: " + keyId);
        }

        // Read public key from Vault
        String publicKeyPath = buildDataPath(getKeyPath(keyId) + "/public");
        VaultResponse publicResponse = vaultTemplate.read(publicKeyPath);

        if (publicResponse == null || publicResponse.getData() == null) {
            throw new IllegalArgumentException("Public key not found in Vault: " + keyId);
        }

        // For KV v2 (versioned), the response has a nested structure where actual data is under "data" key
        Map<String, Object> privateResponseData = privateResponse.getData();
        @SuppressWarnings("unchecked")
        Map<String, Object> privateData = (Map<String, Object>) privateResponseData.get("data");

        Map<String, Object> publicResponseData = publicResponse.getData();
        @SuppressWarnings("unchecked")
        Map<String, Object> publicData = (Map<String, Object>) publicResponseData.get("data");

        if (privateData == null || publicData == null) {
            throw new IllegalArgumentException("Key data not found in Vault: " + keyId);
        }

        String privateKeyBase64 = (String) privateData.get("key");
        String publicKeyBase64 = (String) publicData.get("key");
        String algorithm = (String) privateData.get("algorithm");

        byte[] privateKeyBytes = Base64.getDecoder().decode(privateKeyBase64);
        byte[] publicKeyBytes = Base64.getDecoder().decode(publicKeyBase64);

        // Use KeyFormatConverter to reconstruct keys from standard formats
        PrivateKey privateKey = KeyFormatConverter.importPrivateKey(privateKeyBytes,
                KeyLifecycleManager.KeyFormat.DER, getAlgorithmName(algorithm));
        PublicKey publicKey = KeyFormatConverter.importPublicKey(publicKeyBytes,
                KeyLifecycleManager.KeyFormat.DER, getAlgorithmName(algorithm));

        KeyPair keyPair = new KeyPair(publicKey, privateKey);

        // Cache it
        keyCache.put(keyId, keyPair);
        return keyPair;
    }

    @Override
    public KeyMetadata getKeyMetadata(String keyId) throws Exception {
        // Check cache first
        if (metadataCache.containsKey(keyId)) {
            return metadataCache.get(keyId);
        }

        // Read metadata from Vault
        String metadataPath = buildDataPath(getKeyPath(keyId) + "/metadata");
        VaultResponse response = vaultTemplate.read(metadataPath);

        if (response == null || response.getData() == null) {
            return null;
        }

        // For KV v2 (versioned), the response has a nested structure where actual data is under "data" key
        Map<String, Object> responseData = response.getData();
        @SuppressWarnings("unchecked")
        Map<String, Object> secretData = (Map<String, Object>) responseData.get("data");

        if (secretData == null) {
            return null;
        }

        String metadataBase64 = (String) secretData.get("metadata");
        KeyMetadata metadata = deserializeMetadata(metadataBase64);

        // Cache it
        metadataCache.put(keyId, metadata);
        return metadata;
    }

    @Override
    public void updateKeyMetadata(String keyId, KeyMetadata metadata) throws Exception {
        // Read existing key pair
        KeyPair keyPair = getKey(keyId);

        // Store updated metadata with existing key pair
        storeKey(keyId, keyPair, metadata);
    }

    @Override
    public void deleteKey(String keyId) throws Exception {
        VaultKeyValueOperations keyValue = vaultTemplate.opsForKeyValue(secretsEngine,
                VaultKeyValueOperationsSupport.KeyValueBackend.versioned());

        // Delete private key, public key, and metadata separately
        keyValue.delete(getKeyPath(keyId) + "/private");
        keyValue.delete(getKeyPath(keyId) + "/public");
        keyValue.delete(getKeyPath(keyId) + "/metadata");

        keyCache.remove(keyId);
        metadataCache.remove(keyId);

        LOG.info("Deleted private key, public key, and metadata from Vault: {}", keyId);
    }

    @Override
    public List<KeyMetadata> listKeys() throws Exception {
        // List all keys under the key prefix
        String metadataPath = buildMetadataPath(keyPrefix);
        List<String> keyIds = vaultTemplate.list(metadataPath);

        List<KeyMetadata> metadataList = new ArrayList<>();
        if (keyIds != null) {
            for (String keyId : keyIds) {
                try {
                    // Remove trailing slash if present
                    String cleanKeyId = keyId.endsWith("/") ? keyId.substring(0, keyId.length() - 1) : keyId;
                    KeyMetadata metadata = getKeyMetadata(cleanKeyId);
                    if (metadata != null) {
                        metadataList.add(metadata);
                    }
                } catch (Exception e) {
                    LOG.warn("Failed to load metadata for key: {}", keyId, e);
                }
            }
        }

        return metadataList;
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
            LOG.info("Expired key in Vault: {}", keyId);
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
            LOG.info("Revoked key in Vault: {} - {}", keyId, reason);
        }
    }

    private void loadExistingKeys() throws Exception {
        String metadataPath = buildMetadataPath(keyPrefix);
        List<String> keyIds = vaultTemplate.list(metadataPath);

        if (keyIds != null) {
            LOG.info("Found {} existing keys in Vault", keyIds.size());

            for (String keyId : keyIds) {
                try {
                    // Remove trailing slash if present
                    String cleanKeyId = keyId.endsWith("/") ? keyId.substring(0, keyId.length() - 1) : keyId;
                    KeyMetadata metadata = getKeyMetadata(cleanKeyId);
                    if (metadata != null) {
                        LOG.debug("Loaded existing key from Vault: {}", metadata);
                    }
                } catch (Exception e) {
                    LOG.warn("Failed to load key from Vault: {}", keyId, e);
                }
            }
        }
    }

    private String getKeyPath(String keyId) {
        return keyPrefix + "/" + keyId;
    }

    /**
     * Build the data path for reading/writing secrets, following HCP Vault pattern from camel-hashicorp-vault
     */
    private String buildDataPath(String secretPath) {
        if (!cloud) {
            return secretsEngine + "/data/" + secretPath;
        } else {
            if (namespace != null && !namespace.isEmpty()) {
                return namespace + "/" + secretsEngine + "/data/" + secretPath;
            } else {
                return secretsEngine + "/data/" + secretPath;
            }
        }
    }

    /**
     * Build the metadata path for listing secrets, following HCP Vault pattern from camel-hashicorp-vault
     */
    private String buildMetadataPath(String secretPath) {
        if (!cloud) {
            return secretsEngine + "/metadata/" + secretPath;
        } else {
            if (namespace != null && !namespace.isEmpty()) {
                return namespace + "/" + secretsEngine + "/metadata/" + secretPath;
            } else {
                return secretsEngine + "/metadata/" + secretPath;
            }
        }
    }

    private String serializeMetadata(KeyMetadata metadata) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(metadata);
        }
        return Base64.getEncoder().encodeToString(baos.toByteArray());
    }

    private KeyMetadata deserializeMetadata(String base64) throws Exception {
        byte[] data = Base64.getDecoder().decode(base64);
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        try (ObjectInputStream ois = new ObjectInputStream(bais)) {
            return (KeyMetadata) ois.readObject();
        }
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
     * Get the underlying VaultTemplate for advanced operations
     */
    public VaultTemplate getVaultTemplate() {
        return vaultTemplate;
    }
}
