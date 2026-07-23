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
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import com.azure.core.credential.TokenCredential;
import com.azure.core.exception.ResourceNotFoundException;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;
import com.azure.security.keyvault.secrets.models.SecretProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Azure Key Vault-based implementation of KeyLifecycleManager. Stores PQC keys in Azure Key Vault as secrets, since Key
 * Vault key objects do not support post-quantum key material.
 *
 * <p>
 * Keys are stored using industry-standard formats, mirroring the AWS Secrets Manager implementation:
 * <ul>
 * <li><b>Private keys</b>: PKCS#8 format (RFC 5208), Base64-encoded in a JSON envelope</li>
 * <li><b>Public keys</b>: X.509/SubjectPublicKeyInfo format (RFC 5280), Base64-encoded in a JSON envelope</li>
 * <li><b>Metadata</b>: JSON (see {@link KeyMetadataCodec})</li>
 * </ul>
 *
 * <p>
 * Private key, public key, and metadata are stored as three distinct Key Vault secrets, so different access policies
 * can be applied to private material (restricted) and public material (read-only).
 *
 * <p>
 * Azure Key Vault secret names may only contain alphanumeric characters and dashes, so secrets are named
 * {@code {keyPrefix}-{keyId}-private}, {@code {keyPrefix}-{keyId}-public} and {@code {keyPrefix}-{keyId}-metadata},
 * with a default key prefix of {@code pqc-keys}. Key ids (and a custom key prefix) must therefore only contain
 * alphanumeric characters and dashes.
 *
 * <p>
 * Deleting a key initiates the Key Vault delete operation for the backing secrets; on vaults with soft-delete enabled
 * the secrets remain recoverable (and their names reserved) until purged according to the vault retention policy.
 */
public class AzureKeyVaultKeyLifecycleManager implements KeyLifecycleManager {

    private static final Logger LOG = LoggerFactory.getLogger(AzureKeyVaultKeyLifecycleManager.class);

    private static final Pattern NAME_PATTERN = Pattern.compile("[0-9a-zA-Z-]+");

    private final SecretClient secretClient;
    private final String keyPrefix;
    private final ConcurrentHashMap<String, KeyPair> keyCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, KeyMetadata> metadataCache = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Create an AzureKeyVaultKeyLifecycleManager with an existing SecretClient
     *
     * @param secretClient Configured SecretClient instance
     * @param keyPrefix    Prefix for secret names in Azure Key Vault (alphanumeric characters and dashes only)
     */
    public AzureKeyVaultKeyLifecycleManager(SecretClient secretClient, String keyPrefix) {
        this.secretClient = secretClient;
        this.keyPrefix = validateName(keyPrefix != null ? keyPrefix : "pqc-keys", "keyPrefix");

        LOG.info("Initialized AzureKeyVaultKeyLifecycleManager with keyPrefix: {}", this.keyPrefix);

        try {
            loadExistingKeys();
        } catch (Exception e) {
            LOG.warn("Failed to load existing keys from Azure Key Vault", e);
        }
    }

    /**
     * Create an AzureKeyVaultKeyLifecycleManager authenticating with a client secret credential
     *
     * @param vaultUrl     Key Vault URL (e.g., https://myvault.vault.azure.net)
     * @param clientId     Azure AD application client id
     * @param clientSecret Azure AD application client secret
     * @param tenantId     Azure AD tenant id
     * @param keyPrefix    Prefix for secret names (optional, defaults to "pqc-keys")
     */
    public AzureKeyVaultKeyLifecycleManager(String vaultUrl, String clientId, String clientSecret, String tenantId,
                                            String keyPrefix) {
        this(buildClient(vaultUrl, new ClientSecretCredentialBuilder()
                .clientId(clientId)
                .clientSecret(clientSecret)
                .tenantId(tenantId)
                .build()),
             keyPrefix);
    }

    /**
     * Create an AzureKeyVaultKeyLifecycleManager authenticating through the Default Azure Credential chain (managed
     * identity, environment variables, Azure CLI, etc.)
     *
     * @param vaultUrl  Key Vault URL (e.g., https://myvault.vault.azure.net)
     * @param keyPrefix Prefix for secret names (optional, defaults to "pqc-keys")
     */
    public AzureKeyVaultKeyLifecycleManager(String vaultUrl, String keyPrefix) {
        this(buildClient(vaultUrl, new DefaultAzureCredentialBuilder().build()), keyPrefix);
    }

    private static SecretClient buildClient(String vaultUrl, TokenCredential credential) {
        return new SecretClientBuilder()
                .vaultUrl(vaultUrl)
                .credential(credential)
                .buildClient();
    }

    @Override
    public KeyPair generateKeyPair(String algorithm, String keyId) throws Exception {
        return generateKeyPair(algorithm, keyId, null);
    }

    @Override
    public KeyPair generateKeyPair(String algorithm, String keyId, Object parameterSpec) throws Exception {
        LOG.info("Generating key pair for algorithm: {}, keyId: {}", algorithm, keyId);

        KeyPair keyPair = KeyAlgorithmSupport.generateKeyPair(algorithm, parameterSpec);

        // Create metadata
        KeyMetadata metadata = new KeyMetadata(keyId, algorithm);
        metadata.setDescription("Generated on " + new Date());

        // Store the key
        storeKey(keyId, keyPair, metadata);

        LOG.info("Generated key pair in Azure Key Vault: {}", metadata);
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
            PrivateKey privateKey
                    = KeyFormatConverter.importPrivateKey(keyData, format, KeyAlgorithmSupport.getAlgorithmName(algorithm));
            LOG.warn("Importing private key only - public key derivation may be needed");
            return new KeyPair(null, privateKey);
        } catch (Exception e) {
            // Try as public key only
            PublicKey publicKey
                    = KeyFormatConverter.importPublicKey(keyData, format, KeyAlgorithmSupport.getAlgorithmName(algorithm));
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

        LOG.info("Key rotation completed in Azure Key Vault: {} -> {}", oldKeyId, newKeyId);
        return newKeyPair;
    }

    @Override
    public void storeKey(String keyId, KeyPair keyPair, KeyMetadata metadata) throws Exception {
        // Use PKCS#8 format for private key and X.509 for public key (industry standard)
        byte[] privateKeyBytes = keyPair.getPrivate().getEncoded(); // PKCS#8 format
        byte[] publicKeyBytes = keyPair.getPublic().getEncoded(); // X.509/SubjectPublicKeyInfo format
        String privateKeyBase64 = Base64.getEncoder().encodeToString(privateKeyBytes);
        String publicKeyBase64 = Base64.getEncoder().encodeToString(publicKeyBytes);

        // Store private key separately (restricted access policy recommended in production)
        String privateSecretName = getSecretName(keyId, "private");
        String privateSecretValue = objectMapper.writeValueAsString(new SecretData(
                privateKeyBase64,
                "PKCS8",
                metadata.getAlgorithm()));

        secretClient.setSecret(privateSecretName, privateSecretValue);

        // Store public key separately (can have read-only access policy)
        String publicSecretName = getSecretName(keyId, "public");
        String publicSecretValue = objectMapper.writeValueAsString(new SecretData(
                publicKeyBase64,
                "X509",
                metadata.getAlgorithm()));

        secretClient.setSecret(publicSecretName, publicSecretValue);

        // Store metadata separately as JSON (see KeyMetadataCodec)
        String metadataSecretName = getSecretName(keyId, "metadata");
        String metadataSecretValue = KeyMetadataCodec.toJson(metadata);

        secretClient.setSecret(metadataSecretName, metadataSecretValue);

        // Update caches
        keyCache.put(keyId, keyPair);
        metadataCache.put(keyId, metadata);

        LOG.debug("Stored private key, public key, and metadata separately in Azure Key Vault for: {}", keyId);
    }

    @Override
    public KeyPair getKey(String keyId) throws Exception {
        // Check cache first
        if (keyCache.containsKey(keyId)) {
            return keyCache.get(keyId);
        }

        // Read private key from Azure Key Vault
        KeyVaultSecret privateSecret = secretClient.getSecret(getSecretName(keyId, "private"));

        // Read public key from Azure Key Vault
        KeyVaultSecret publicSecret = secretClient.getSecret(getSecretName(keyId, "public"));

        // Parse secret values
        SecretData privateData = objectMapper.readValue(privateSecret.getValue(), SecretData.class);
        SecretData publicData = objectMapper.readValue(publicSecret.getValue(), SecretData.class);

        byte[] privateKeyBytes = Base64.getDecoder().decode(privateData.getKey());
        byte[] publicKeyBytes = Base64.getDecoder().decode(publicData.getKey());

        // Use KeyFormatConverter to reconstruct keys from standard formats
        PrivateKey privateKey = KeyFormatConverter.importPrivateKey(privateKeyBytes,
                KeyLifecycleManager.KeyFormat.DER, KeyAlgorithmSupport.getAlgorithmName(privateData.getAlgorithm()));
        PublicKey publicKey = KeyFormatConverter.importPublicKey(publicKeyBytes,
                KeyLifecycleManager.KeyFormat.DER, KeyAlgorithmSupport.getAlgorithmName(publicData.getAlgorithm()));

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

        try {
            KeyVaultSecret secret = secretClient.getSecret(getSecretName(keyId, "metadata"));
            KeyMetadata metadata = KeyMetadataCodec.fromJson(secret.getValue());

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

        LOG.info("Deleted private key, public key, and metadata from Azure Key Vault: {}", keyId);
    }

    @Override
    public List<KeyMetadata> listKeys() throws Exception {
        // List all secrets with the key prefix, only processing metadata secrets to avoid duplicates
        List<KeyMetadata> metadataList = new ArrayList<>();

        for (SecretProperties properties : secretClient.listPropertiesOfSecrets()) {
            String secretName = properties.getName();
            if (secretName.startsWith(keyPrefix + "-") && secretName.endsWith("-metadata")) {
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
            LOG.info("Expired key in Azure Key Vault: {}", keyId);
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
            LOG.info("Revoked key in Azure Key Vault: {} - {}", keyId, reason);
        }
    }

    private void loadExistingKeys() throws Exception {
        List<KeyMetadata> keys = listKeys();
        if (!keys.isEmpty()) {
            LOG.info("Found {} existing keys in Azure Key Vault", keys.size());
            for (KeyMetadata metadata : keys) {
                LOG.debug("Loaded existing key from Azure Key Vault: {}", metadata);
            }
        }
    }

    private String getSecretName(String keyId, String type) {
        validateName(keyId, "keyId");
        return keyPrefix + "-" + keyId + "-" + type;
    }

    private String extractKeyIdFromSecretName(String secretName) {
        // Extract keyId from pattern: {keyPrefix}-{keyId}-metadata
        return secretName.substring(keyPrefix.length() + 1, secretName.length() - "-metadata".length());
    }

    private void deleteSecret(String secretName) {
        try {
            secretClient.beginDeleteSecret(secretName);
            LOG.debug("Deletion initiated for secret: {}", secretName);
        } catch (ResourceNotFoundException e) {
            LOG.debug("Secret not found, skipping deletion: {}", secretName);
        }
    }

    private static String validateName(String value, String what) {
        if (value == null || !NAME_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    what + " must contain only alphanumeric characters and dashes to be usable in Azure Key Vault secret names: "
                                               + value);
        }
        return value;
    }

    /**
     * Get the underlying SecretClient for advanced operations
     */
    public SecretClient getSecretClient() {
        return secretClient;
    }

    /**
     * Helper class for storing secret data in JSON format
     */
    private static class SecretData {
        private String key;
        private String format;
        private String algorithm;

        public SecretData() {
        }

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
}
