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
import java.net.URI;
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
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.component.pqc.PQCKeyEncapsulationAlgorithms;
import org.apache.camel.component.pqc.PQCSignatureAlgorithms;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClientBuilder;
import software.amazon.awssdk.services.secretsmanager.model.CreateSecretRequest;
import software.amazon.awssdk.services.secretsmanager.model.DeleteSecretRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.ListSecretsRequest;
import software.amazon.awssdk.services.secretsmanager.model.ListSecretsResponse;
import software.amazon.awssdk.services.secretsmanager.model.PutSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException;
import software.amazon.awssdk.services.secretsmanager.model.SecretListEntry;
import software.amazon.awssdk.services.secretsmanager.model.Tag;

/**
 * AWS Secrets Manager-based implementation of KeyLifecycleManager. Stores keys and metadata in AWS Secrets Manager with
 * centralized secret management, audit logging, and fine-grained access control via IAM policies.
 *
 * Features: - Centralized secret management via AWS Secrets Manager - Automatic audit logging through AWS CloudTrail -
 * Fine-grained access control with IAM policies - Encryption at rest with AWS KMS - Multi-region replication support -
 * In-memory caching for performance - Industry-standard PKCS#8/X.509 key formats - Separate storage for private/public
 * keys
 *
 * Configuration: - region: AWS region (e.g., us-east-1) - accessKey: AWS access key (optional, uses default credentials
 * if not provided) - secretKey: AWS secret key (optional) - keyPrefix: Prefix for all secret names (default: pqc/keys)
 * - endpointOverride: Custom endpoint for testing with LocalStack (optional)
 *
 * This implementation uses AWS SDK v2 (software.amazon.awssdk) consistent with other Camel AWS components.
 */
public class AwsSecretsManagerKeyLifecycleManager implements KeyLifecycleManager {

    private static final Logger LOG = LoggerFactory.getLogger(AwsSecretsManagerKeyLifecycleManager.class);

    private final SecretsManagerClient secretsManagerClient;
    private final String keyPrefix;
    private final ConcurrentHashMap<String, KeyPair> keyCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, KeyMetadata> metadataCache = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Create an AwsSecretsManagerKeyLifecycleManager with an existing SecretsManagerClient
     *
     * @param secretsManagerClient Configured SecretsManagerClient instance
     * @param keyPrefix            Prefix for secret names in AWS Secrets Manager
     */
    public AwsSecretsManagerKeyLifecycleManager(SecretsManagerClient secretsManagerClient, String keyPrefix) {
        this.secretsManagerClient = secretsManagerClient;
        this.keyPrefix = keyPrefix != null ? keyPrefix : "pqc/keys";

        LOG.info("Initialized AwsSecretsManagerKeyLifecycleManager with keyPrefix: {}", this.keyPrefix);

        try {
            loadExistingKeys();
        } catch (Exception e) {
            LOG.warn("Failed to load existing keys from AWS Secrets Manager", e);
        }
    }

    /**
     * Create an AwsSecretsManagerKeyLifecycleManager with basic configuration
     *
     * @param region AWS region (e.g., us-east-1)
     */
    public AwsSecretsManagerKeyLifecycleManager(String region) {
        this(region, null, null, null);
    }

    /**
     * Create an AwsSecretsManagerKeyLifecycleManager with custom configuration
     *
     * @param region    AWS region (e.g., us-east-1)
     * @param accessKey AWS access key (optional, uses default credentials if null)
     * @param secretKey AWS secret key (optional)
     * @param keyPrefix Prefix for secret names
     */
    public AwsSecretsManagerKeyLifecycleManager(String region, String accessKey, String secretKey, String keyPrefix) {
        this(region, accessKey, secretKey, keyPrefix, null);
    }

    /**
     * Create an AwsSecretsManagerKeyLifecycleManager with full configuration including endpoint override
     *
     * @param region           AWS region (e.g., us-east-1)
     * @param accessKey        AWS access key (optional, uses default credentials if null)
     * @param secretKey        AWS secret key (optional)
     * @param keyPrefix        Prefix for secret names
     * @param endpointOverride Custom endpoint for testing (optional, e.g., http://localhost:4566 for LocalStack)
     */
    public AwsSecretsManagerKeyLifecycleManager(
            String region, String accessKey, String secretKey, String keyPrefix, String endpointOverride) {
        this.keyPrefix = keyPrefix != null ? keyPrefix : "pqc/keys";

        // Build SecretsManagerClient
        SecretsManagerClientBuilder clientBuilder = SecretsManagerClient.builder();

        if (region != null) {
            clientBuilder.region(Region.of(region));
        }

        if (accessKey != null && secretKey != null) {
            AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);
            clientBuilder.credentialsProvider(StaticCredentialsProvider.create(credentials));
        }

        if (endpointOverride != null) {
            clientBuilder.endpointOverride(URI.create(endpointOverride));
        }

        this.secretsManagerClient = clientBuilder.build();

        LOG.info(
                "Initialized AwsSecretsManagerKeyLifecycleManager with region: {}, keyPrefix: {}, endpointOverride: {}",
                region,
                this.keyPrefix,
                endpointOverride);

        try {
            loadExistingKeys();
        } catch (Exception e) {
            LOG.warn("Failed to load existing keys from AWS Secrets Manager", e);
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

        LOG.info("Generated key pair in AWS Secrets Manager: {}", metadata);
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

        LOG.info("Key rotation completed in AWS Secrets Manager: {} -> {}", oldKeyId, newKeyId);
        return newKeyPair;
    }

    @Override
    public void storeKey(String keyId, KeyPair keyPair, KeyMetadata metadata) throws Exception {
        // Use PKCS#8 format for private key and X.509 for public key (industry standard)
        byte[] privateKeyBytes = keyPair.getPrivate().getEncoded(); // PKCS#8 format
        byte[] publicKeyBytes = keyPair.getPublic().getEncoded(); // X.509/SubjectPublicKeyInfo format
        String privateKeyBase64 = Base64.getEncoder().encodeToString(privateKeyBytes);
        String publicKeyBase64 = Base64.getEncoder().encodeToString(publicKeyBytes);
        String metadataBase64 = serializeMetadata(metadata);

        // Store private key separately (strict IAM policy recommended in production)
        String privateSecretName = getSecretName(keyId, "private");
        String privateSecretValue =
                objectMapper.writeValueAsString(new SecretData(privateKeyBase64, "PKCS8", metadata.getAlgorithm()));

        createOrUpdateSecret(privateSecretName, privateSecretValue, "PQC Private Key: " + keyId);

        // Store public key separately (can have read-only IAM policy)
        String publicSecretName = getSecretName(keyId, "public");
        String publicSecretValue =
                objectMapper.writeValueAsString(new SecretData(publicKeyBase64, "X509", metadata.getAlgorithm()));

        createOrUpdateSecret(publicSecretName, publicSecretValue, "PQC Public Key: " + keyId);

        // Store metadata separately
        String metadataSecretName = getSecretName(keyId, "metadata");
        String metadataSecretValue =
                objectMapper.writeValueAsString(new MetadataData(metadataBase64, keyId, metadata.getAlgorithm()));

        createOrUpdateSecret(metadataSecretName, metadataSecretValue, "PQC Key Metadata: " + keyId);

        // Update caches
        keyCache.put(keyId, keyPair);
        metadataCache.put(keyId, metadata);

        LOG.debug("Stored private key, public key, and metadata separately in AWS Secrets Manager for: {}", keyId);
    }

    @Override
    public KeyPair getKey(String keyId) throws Exception {
        // Check cache first
        if (keyCache.containsKey(keyId)) {
            return keyCache.get(keyId);
        }

        // Read private key from AWS Secrets Manager
        String privateSecretName = getSecretName(keyId, "private");
        GetSecretValueResponse privateResponse = getSecret(privateSecretName);

        // Read public key from AWS Secrets Manager
        String publicSecretName = getSecretName(keyId, "public");
        GetSecretValueResponse publicResponse = getSecret(publicSecretName);

        // Parse secret values
        SecretData privateData = objectMapper.readValue(privateResponse.secretString(), SecretData.class);
        SecretData publicData = objectMapper.readValue(publicResponse.secretString(), SecretData.class);

        byte[] privateKeyBytes = Base64.getDecoder().decode(privateData.getKey());
        byte[] publicKeyBytes = Base64.getDecoder().decode(publicData.getKey());

        // Use KeyFormatConverter to reconstruct keys from standard formats
        PrivateKey privateKey = KeyFormatConverter.importPrivateKey(
                privateKeyBytes, KeyLifecycleManager.KeyFormat.DER, getAlgorithmName(privateData.getAlgorithm()));
        PublicKey publicKey = KeyFormatConverter.importPublicKey(
                publicKeyBytes, KeyLifecycleManager.KeyFormat.DER, getAlgorithmName(publicData.getAlgorithm()));

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

        // Read metadata from AWS Secrets Manager
        String metadataSecretName = getSecretName(keyId, "metadata");

        try {
            GetSecretValueResponse response = getSecret(metadataSecretName);
            MetadataData metadataData = objectMapper.readValue(response.secretString(), MetadataData.class);
            KeyMetadata metadata = deserializeMetadata(metadataData.getMetadata());

            // Cache it
            metadataCache.put(keyId, metadata);
            return metadata;
        } catch (ResourceNotFoundException e) {
            return null;
        }
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
        // Delete private key, public key, and metadata separately
        deleteSecret(getSecretName(keyId, "private"));
        deleteSecret(getSecretName(keyId, "public"));
        deleteSecret(getSecretName(keyId, "metadata"));

        keyCache.remove(keyId);
        metadataCache.remove(keyId);

        LOG.info("Deleted private key, public key, and metadata from AWS Secrets Manager: {}", keyId);
    }

    @Override
    public List<KeyMetadata> listKeys() throws Exception {
        // List all secrets with the key prefix
        List<KeyMetadata> metadataList = new ArrayList<>();
        String nextToken = null;

        do {
            ListSecretsRequest.Builder requestBuilder =
                    ListSecretsRequest.builder().maxResults(100);

            if (nextToken != null) {
                requestBuilder.nextToken(nextToken);
            }

            ListSecretsResponse response = secretsManagerClient.listSecrets(requestBuilder.build());

            for (SecretListEntry secret : response.secretList()) {
                String secretName = secret.name();
                // Only process metadata secrets to avoid duplicates
                if (secretName.startsWith(keyPrefix) && secretName.endsWith("/metadata")) {
                    String keyId = extractKeyIdFromSecretName(secretName);
                    try {
                        KeyMetadata metadata = getKeyMetadata(keyId);
                        if (metadata != null) {
                            metadataList.add(metadata);
                        }
                    } catch (Exception e) {
                        LOG.warn("Failed to load metadata for key: {}", keyId, e);
                    }
                }
            }

            nextToken = response.nextToken();
        } while (nextToken != null);

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
            LOG.info("Expired key in AWS Secrets Manager: {}", keyId);
        }
    }

    @Override
    public void revokeKey(String keyId, String reason) throws Exception {
        KeyMetadata metadata = getKeyMetadata(keyId);
        if (metadata != null) {
            metadata.setStatus(KeyMetadata.KeyStatus.REVOKED);
            metadata.setDescription(
                    (metadata.getDescription() != null ? metadata.getDescription() + "; " : "") + "Revoked: " + reason);
            updateKeyMetadata(keyId, metadata);
            LOG.info("Revoked key in AWS Secrets Manager: {} - {}", keyId, reason);
        }
    }

    private void loadExistingKeys() throws Exception {
        List<KeyMetadata> keys = listKeys();
        if (!keys.isEmpty()) {
            LOG.info("Found {} existing keys in AWS Secrets Manager", keys.size());
            for (KeyMetadata metadata : keys) {
                LOG.debug("Loaded existing key from AWS Secrets Manager: {}", metadata);
            }
        }
    }

    private String getSecretName(String keyId, String type) {
        return keyPrefix + "/" + keyId + "/" + type;
    }

    private String extractKeyIdFromSecretName(String secretName) {
        // Extract keyId from pattern: pqc/keys/{keyId}/metadata
        String withoutPrefix = secretName.substring(keyPrefix.length() + 1);
        int lastSlash = withoutPrefix.lastIndexOf('/');
        return withoutPrefix.substring(0, lastSlash);
    }

    private void createOrUpdateSecret(String secretName, String secretValue, String description) {
        try {
            // Try to update existing secret
            PutSecretValueRequest putRequest = PutSecretValueRequest.builder()
                    .secretId(secretName)
                    .secretString(secretValue)
                    .build();
            secretsManagerClient.putSecretValue(putRequest);
            LOG.debug("Updated secret: {}", secretName);
        } catch (ResourceNotFoundException e) {
            // Secret doesn't exist, create it
            CreateSecretRequest createRequest = CreateSecretRequest.builder()
                    .name(secretName)
                    .secretString(secretValue)
                    .description(description)
                    .tags(Tag.builder().key("ManagedBy").value("camel-pqc").build())
                    .build();
            secretsManagerClient.createSecret(createRequest);
            LOG.debug("Created secret: {}", secretName);
        }
    }

    private GetSecretValueResponse getSecret(String secretName) {
        GetSecretValueRequest request =
                GetSecretValueRequest.builder().secretId(secretName).build();
        return secretsManagerClient.getSecretValue(request);
    }

    private void deleteSecret(String secretName) {
        try {
            DeleteSecretRequest request = DeleteSecretRequest.builder()
                    .secretId(secretName)
                    .forceDeleteWithoutRecovery(true)
                    .build();
            secretsManagerClient.deleteSecret(request);
            LOG.debug("Deleted secret: {}", secretName);
        } catch (ResourceNotFoundException e) {
            LOG.debug("Secret not found, skipping deletion: {}", secretName);
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
                            10, org.bouncycastle.pqc.jcajce.spec.XMSSParameterSpec.SHA256);
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
     * Get the underlying SecretsManagerClient for advanced operations
     */
    public SecretsManagerClient getSecretsManagerClient() {
        return secretsManagerClient;
    }

    /**
     * Helper class for storing secret data in JSON format
     */
    private static class SecretData {
        private String key;
        private String format;
        private String algorithm;

        public SecretData() {}

        public SecretData(String key, String format, String algorithm) {
            this.key = key;
            this.format = format;
            this.algorithm = algorithm;
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getFormat() {
            return format;
        }

        public void setFormat(String format) {
            this.format = format;
        }

        public String getAlgorithm() {
            return algorithm;
        }

        public void setAlgorithm(String algorithm) {
            this.algorithm = algorithm;
        }
    }

    /**
     * Helper class for storing metadata in JSON format
     */
    private static class MetadataData {
        private String metadata;
        private String keyId;
        private String algorithm;

        public MetadataData() {}

        public MetadataData(String metadata, String keyId, String algorithm) {
            this.metadata = metadata;
            this.keyId = keyId;
            this.algorithm = algorithm;
        }

        public String getMetadata() {
            return metadata;
        }

        public void setMetadata(String metadata) {
            this.metadata = metadata;
        }

        public String getKeyId() {
            return keyId;
        }

        public void setKeyId(String keyId) {
            this.keyId = keyId;
        }

        public String getAlgorithm() {
            return algorithm;
        }

        public void setAlgorithm(String algorithm) {
            this.algorithm = algorithm;
        }
    }
}
