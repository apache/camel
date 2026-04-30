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
package org.apache.camel.component.pqc;

import java.security.*;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.crypto.KeyAgreement;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.pqc.crypto.hybrid.HybridKEM;
import org.apache.camel.component.pqc.crypto.hybrid.HybridSignature;
import org.apache.camel.component.pqc.lifecycle.KeyLifecycleManager;
import org.apache.camel.component.pqc.lifecycle.KeyMetadata;
import org.apache.camel.component.pqc.stateful.StatefulKeyState;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckHelper;
import org.apache.camel.health.WritableHealthCheckRepository;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.bouncycastle.jcajce.SecretKeyWithEncapsulation;
import org.bouncycastle.jcajce.spec.KEMExtractSpec;
import org.bouncycastle.jcajce.spec.KEMGenerateSpec;
import org.bouncycastle.pqc.jcajce.interfaces.LMSPrivateKey;
import org.bouncycastle.pqc.jcajce.interfaces.XMSSMTPrivateKey;
import org.bouncycastle.pqc.jcajce.interfaces.XMSSPrivateKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Producer which sign or verify a payload
 */
public class PQCProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(PQCProducer.class);

    // Valid symmetric key lengths per algorithm for startup validation.
    // RC5 is excluded because it supports arbitrary key lengths (0-2040 bits).
    private static final Map<String, int[]> VALID_SYMMETRIC_KEY_LENGTHS = Map.ofEntries(
            Map.entry(PQCSymmetricAlgorithms.AES.name(), new int[] { 128, 192, 256 }),
            Map.entry(PQCSymmetricAlgorithms.RC2.name(), new int[] { 40, 64, 128 }),
            Map.entry(PQCSymmetricAlgorithms.ARIA.name(), new int[] { 128, 192, 256 }),
            Map.entry(PQCSymmetricAlgorithms.CAMELLIA.name(), new int[] { 128, 192, 256 }),
            Map.entry(PQCSymmetricAlgorithms.CAST5.name(), new int[] { 40, 64, 128 }),
            Map.entry(PQCSymmetricAlgorithms.CAST6.name(), new int[] { 128, 160, 192, 224, 256 }),
            Map.entry(PQCSymmetricAlgorithms.CHACHA7539.name(), new int[] { 256 }),
            Map.entry(PQCSymmetricAlgorithms.DSTU7624.name(), new int[] { 128, 256, 512 }),
            Map.entry(PQCSymmetricAlgorithms.GOST28147.name(), new int[] { 256 }),
            Map.entry(PQCSymmetricAlgorithms.GOST3412_2015.name(), new int[] { 256 }),
            Map.entry(PQCSymmetricAlgorithms.GRAIN128.name(), new int[] { 128 }),
            Map.entry(PQCSymmetricAlgorithms.HC128.name(), new int[] { 128 }),
            Map.entry(PQCSymmetricAlgorithms.HC256.name(), new int[] { 256 }),
            Map.entry(PQCSymmetricAlgorithms.SALSA20.name(), new int[] { 128, 256 }),
            Map.entry(PQCSymmetricAlgorithms.SEED.name(), new int[] { 128 }),
            Map.entry(PQCSymmetricAlgorithms.SM4.name(), new int[] { 128 }),
            Map.entry(PQCSymmetricAlgorithms.DESEDE.name(), new int[] { 128, 192 }));

    // Signature algorithms not part of NIST FIPS 204/205 post-quantum standards.
    // XMSS/XMSSMT are NIST SP 800-208 but are stateful and not in FIPS 204/205.
    private static final Set<String> NON_FIPS_PQ_SIGNATURES = Set.of(
            PQCSignatureAlgorithms.XMSS.name(),
            PQCSignatureAlgorithms.XMSSMT.name(),
            PQCSignatureAlgorithms.DILITHIUM.name(),
            PQCSignatureAlgorithms.FALCON.name(),
            PQCSignatureAlgorithms.PICNIC.name(),
            PQCSignatureAlgorithms.SNOVA.name(),
            PQCSignatureAlgorithms.MAYO.name(),
            PQCSignatureAlgorithms.SPHINCSPLUS.name());

    // KEM algorithms not part of NIST FIPS 203 post-quantum standard.
    private static final Set<String> NON_FIPS_PQ_KEMS = Set.of(
            PQCKeyEncapsulationAlgorithms.BIKE.name(),
            PQCKeyEncapsulationAlgorithms.HQC.name(),
            PQCKeyEncapsulationAlgorithms.CMCE.name(),
            PQCKeyEncapsulationAlgorithms.SABER.name(),
            PQCKeyEncapsulationAlgorithms.FRODO.name(),
            PQCKeyEncapsulationAlgorithms.NTRU.name(),
            PQCKeyEncapsulationAlgorithms.NTRULPRime.name(),
            PQCKeyEncapsulationAlgorithms.SNTRUPrime.name(),
            PQCKeyEncapsulationAlgorithms.KYBER.name());

    private Signature signer;
    private KeyGenerator keyGenerator;
    private KeyPair keyPair;

    // Hybrid cryptography fields
    private Signature classicalSigner;
    private KeyAgreement classicalKeyAgreement;
    private KeyPair classicalKeyPair;

    // Health check fields
    private HealthCheck producerHealthCheck;
    private WritableHealthCheckRepository healthCheckRepository;

    public PQCProducer(Endpoint endpoint) {
        super(endpoint);
    }

    /**
     * Returns the runtime key pair used by this producer. Used by the health check to read actual key state rather than
     * the configuration snapshot.
     */
    KeyPair getRuntimeKeyPair() {
        return keyPair;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        PQCOperations operation = determineOperation(exchange);
        enforceKeyStatus(exchange, operation);
        switch (operation) {
            case sign:
                signature(exchange);
                break;
            case verify:
                verification(exchange);
                break;
            case hybridSign:
                hybridSignature(exchange);
                break;
            case hybridVerify:
                hybridVerification(exchange);
                break;
            case generateSecretKeyEncapsulation:
                generateEncapsulation(exchange);
                break;
            case extractSecretKeyEncapsulation:
                extractEncapsulation(exchange);
                break;
            case extractSecretKeyFromEncapsulation:
                extractSecretKeyFromEncapsulation(exchange);
                break;
            case hybridGenerateSecretKeyEncapsulation:
                hybridGenerateEncapsulation(exchange);
                break;
            case hybridExtractSecretKeyEncapsulation:
                hybridExtractEncapsulation(exchange);
                break;
            case hybridExtractSecretKeyFromEncapsulation:
                hybridExtractSecretKeyFromEncapsulation(exchange);
                break;
            case generateKeyPair:
                lifecycleGenerateKeyPair(exchange);
                break;
            case exportKey:
                lifecycleExportKey(exchange);
                break;
            case importKey:
                lifecycleImportKey(exchange);
                break;
            case rotateKey:
                lifecycleRotateKey(exchange);
                break;
            case getKeyMetadata:
                lifecycleGetKeyMetadata(exchange);
                break;
            case listKeys:
                lifecycleListKeys(exchange);
                break;
            case expireKey:
                lifecycleExpireKey(exchange);
                break;
            case revokeKey:
                lifecycleRevokeKey(exchange);
                break;
            case getRemainingSignatures:
                statefulGetRemainingSignatures(exchange);
                break;
            case getKeyState:
                statefulGetKeyState(exchange);
                break;
            case deleteKeyState:
                statefulDeleteKeyState(exchange);
                break;
            default:
                throw new IllegalArgumentException("Unsupported operation");
        }
    }

    private PQCOperations determineOperation(Exchange exchange) {
        PQCOperations operation = exchange.getIn().getHeader(PQCConstants.OPERATION, PQCOperations.class);
        if (operation == null) {
            operation = getConfiguration().getOperation();
        }
        return operation;
    }

    protected PQCConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    /**
     * Enforces key status checks before cryptographic operations when strict key lifecycle mode is enabled. Looks up
     * the key metadata via the configured {@link KeyLifecycleManager} using the {@code CamelPQCKeyId} header.
     *
     * <ul>
     * <li>REVOKED keys are rejected for all operations (sign, verify, encapsulate, extract).</li>
     * <li>EXPIRED keys are rejected for signing/encapsulation but allowed for verification/extraction.</li>
     * <li>DEPRECATED keys produce a WARN log but still function (transition period).</li>
     * <li>ACTIVE and PENDING_ROTATION keys are always allowed.</li>
     * </ul>
     */
    private void enforceKeyStatus(Exchange exchange, PQCOperations operation) throws Exception {
        if (!getConfiguration().isStrictKeyLifecycle()) {
            return;
        }

        KeyLifecycleManager klm = getConfiguration().getKeyLifecycleManager();
        if (klm == null) {
            return;
        }

        if (!isCryptographicOperation(operation)) {
            return;
        }

        String keyId = exchange.getMessage().getHeader(PQCConstants.KEY_ID, String.class);
        if (ObjectHelper.isEmpty(keyId)) {
            return;
        }

        KeyMetadata metadata = klm.getKeyMetadata(keyId);
        if (metadata == null) {
            return;
        }

        KeyMetadata.KeyStatus status = metadata.getStatus();

        switch (status) {
            case REVOKED:
                throw new IllegalStateException(
                        "Key '" + keyId + "' has been revoked and cannot be used for any cryptographic operation. "
                                                + "Reason: " + (metadata.getDescription() != null
                                                        ? metadata.getDescription() : "not specified"));
            case EXPIRED:
                if (isProducingOperation(operation)) {
                    throw new IllegalStateException(
                            "Key '" + keyId + "' has expired and cannot be used for " + operation
                                                    + ". Expired keys can only be used for verification or extraction operations.");
                }
                LOG.info("Using expired key '{}' for {} operation (verification/extraction of existing data)", keyId,
                        operation);
                break;
            case DEPRECATED:
                LOG.warn("Key '{}' is deprecated. Consider rotating to an active key. Operation: {}", keyId, operation);
                break;
            case ACTIVE:
            case PENDING_ROTATION:
            default:
                break;
        }
    }

    private boolean isCryptographicOperation(PQCOperations operation) {
        switch (operation) {
            case sign:
            case verify:
            case hybridSign:
            case hybridVerify:
            case generateSecretKeyEncapsulation:
            case extractSecretKeyEncapsulation:
            case extractSecretKeyFromEncapsulation:
            case hybridGenerateSecretKeyEncapsulation:
            case hybridExtractSecretKeyEncapsulation:
            case hybridExtractSecretKeyFromEncapsulation:
                return true;
            default:
                return false;
        }
    }

    private boolean isProducingOperation(PQCOperations operation) {
        switch (operation) {
            case sign:
            case hybridSign:
            case generateSecretKeyEncapsulation:
            case hybridGenerateSecretKeyEncapsulation:
                return true;
            default:
                return false;
        }
    }

    @Override
    public PQCEndpoint getEndpoint() {
        return (PQCEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        validateConfiguration();

        if (getConfiguration().getOperation().equals(PQCOperations.sign)
                || getConfiguration().getOperation().equals(PQCOperations.verify)) {
            signer = getEndpoint().getConfiguration().getSigner();

            if (ObjectHelper.isEmpty(signer)) {
                PQCSignatureAlgorithms sigAlg = PQCSignatureAlgorithms.valueOf(getConfiguration().getSignatureAlgorithm());
                signer = Signature.getInstance(sigAlg.getAlgorithm(), sigAlg.getBcProvider());
            }
        }

        if (getConfiguration().getOperation().equals(PQCOperations.generateSecretKeyEncapsulation)
                || getConfiguration().getOperation().equals(PQCOperations.extractSecretKeyEncapsulation)) {
            keyGenerator = getEndpoint().getConfiguration().getKeyGenerator();

            if (ObjectHelper.isEmpty(keyGenerator)) {
                PQCKeyEncapsulationAlgorithms kemAlg
                        = PQCKeyEncapsulationAlgorithms.valueOf(getConfiguration().getKeyEncapsulationAlgorithm());
                keyGenerator = KeyGenerator.getInstance(kemAlg.getAlgorithm(), kemAlg.getBcProvider());
            }
        }

        if (ObjectHelper.isNotEmpty(getConfiguration().getKeyStore())
                && ObjectHelper.isNotEmpty(getConfiguration().getKeyPairAlias())
                && ObjectHelper.isNotEmpty(getConfiguration().getKeyStorePassword())) {
            KeyStore keyStore = getConfiguration().getKeyStore();
            PrivateKey privateKey = (PrivateKey) keyStore.getKey(getConfiguration().getKeyPairAlias(),
                    getConfiguration().getKeyStorePassword().toCharArray());
            Certificate cert = keyStore.getCertificate(getConfiguration().getKeyPairAlias());
            PublicKey publicKey = cert.getPublicKey();
            keyPair = new KeyPair(publicKey, privateKey);
        } else {
            keyPair = getConfiguration().getKeyPair();
        }

        // Initialize hybrid signature operations
        if (getConfiguration().getOperation().equals(PQCOperations.hybridSign)
                || getConfiguration().getOperation().equals(PQCOperations.hybridVerify)) {
            // Initialize PQC signer
            signer = getEndpoint().getConfiguration().getSigner();
            if (ObjectHelper.isEmpty(signer) && ObjectHelper.isNotEmpty(getConfiguration().getSignatureAlgorithm())) {
                PQCSignatureAlgorithms sigAlg = PQCSignatureAlgorithms.valueOf(getConfiguration().getSignatureAlgorithm());
                signer = Signature.getInstance(sigAlg.getAlgorithm(), sigAlg.getBcProvider());
            }

            // Initialize classical signer
            classicalSigner = getEndpoint().getConfiguration().getClassicalSigner();
            if (ObjectHelper.isEmpty(classicalSigner)
                    && ObjectHelper.isNotEmpty(getConfiguration().getClassicalSignatureAlgorithm())) {
                PQCClassicalSignatureAlgorithms classAlg
                        = PQCClassicalSignatureAlgorithms.valueOf(getConfiguration().getClassicalSignatureAlgorithm());
                classicalSigner = Signature.getInstance(classAlg.getAlgorithm());
            }

            // Initialize classical key pair
            classicalKeyPair = getConfiguration().getClassicalKeyPair();
        }

        // Initialize hybrid KEM operations
        if (getConfiguration().getOperation().equals(PQCOperations.hybridGenerateSecretKeyEncapsulation)
                || getConfiguration().getOperation().equals(PQCOperations.hybridExtractSecretKeyEncapsulation)
                || getConfiguration().getOperation().equals(PQCOperations.hybridExtractSecretKeyFromEncapsulation)) {
            // Initialize PQC KEM
            keyGenerator = getEndpoint().getConfiguration().getKeyGenerator();
            if (ObjectHelper.isEmpty(keyGenerator)
                    && ObjectHelper.isNotEmpty(getConfiguration().getKeyEncapsulationAlgorithm())) {
                PQCKeyEncapsulationAlgorithms kemAlg
                        = PQCKeyEncapsulationAlgorithms.valueOf(getConfiguration().getKeyEncapsulationAlgorithm());
                keyGenerator = KeyGenerator.getInstance(kemAlg.getAlgorithm(), kemAlg.getBcProvider());
            }

            // Initialize classical key agreement
            classicalKeyAgreement = getEndpoint().getConfiguration().getClassicalKeyAgreement();
            if (ObjectHelper.isEmpty(classicalKeyAgreement)
                    && ObjectHelper.isNotEmpty(getConfiguration().getClassicalKEMAlgorithm())) {
                PQCClassicalKEMAlgorithms classKemAlg
                        = PQCClassicalKEMAlgorithms.valueOf(getConfiguration().getClassicalKEMAlgorithm());
                classicalKeyAgreement = KeyAgreement.getInstance(classKemAlg.getAlgorithm());
            }

            // Initialize classical key pair
            classicalKeyPair = getConfiguration().getClassicalKeyPair();
        }

        // Register health check for stateful key monitoring
        healthCheckRepository = HealthCheckHelper.getHealthCheckRepository(
                getEndpoint().getCamelContext(),
                "producers",
                WritableHealthCheckRepository.class);

        if (ObjectHelper.isNotEmpty(healthCheckRepository)) {
            String id = getEndpoint().getId();
            producerHealthCheck = new PQCStatefulKeyHealthCheck(getEndpoint(), this, id);
            producerHealthCheck.setEnabled(getEndpoint().getComponent().isHealthCheckProducerEnabled());
            healthCheckRepository.addHealthCheck(producerHealthCheck);
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (ObjectHelper.isNotEmpty(healthCheckRepository) && ObjectHelper.isNotEmpty(producerHealthCheck)) {
            healthCheckRepository.removeHealthCheck(producerHealthCheck);
            producerHealthCheck = null;
        }
        super.doStop();
    }

    private void signature(Exchange exchange)
            throws Exception {
        checkStatefulKeyBeforeSign();

        String payload = exchange.getMessage().getMandatoryBody(String.class);

        signer.initSign(keyPair.getPrivate());
        signer.update(payload.getBytes());

        byte[] signature = signer.sign();
        exchange.getMessage().setHeader(PQCConstants.SIGNATURE, signature);

        persistStatefulKeyStateAfterSign(exchange);
    }

    private void verification(Exchange exchange)
            throws InvalidPayloadException, InvalidKeyException, SignatureException {
        String payload = exchange.getMessage().getMandatoryBody(String.class);

        signer.initVerify(keyPair.getPublic());
        signer.update(payload.getBytes());
        if (signer.verify(exchange.getMessage().getHeader(PQCConstants.SIGNATURE, byte[].class))) {
            exchange.getMessage().setHeader(PQCConstants.VERIFY, true);
        } else {
            exchange.getMessage().setHeader(PQCConstants.VERIFY, false);
        }
    }

    private void generateEncapsulation(Exchange exchange)
            throws InvalidAlgorithmParameterException {
        // initialise for creating an encapsulation and shared secret.
        keyGenerator.init(
                new KEMGenerateSpec(
                        keyPair.getPublic(),
                        getEndpoint().getConfiguration().getSymmetricKeyAlgorithm(),
                        getEndpoint().getConfiguration().getSymmetricKeyLength()),
                new SecureRandom());
        // SecretKeyWithEncapsulation is the class to use as the secret key, it has additional
        // methods on it for recovering the encapsulation as well.
        SecretKeyWithEncapsulation secEnc1 = (SecretKeyWithEncapsulation) keyGenerator.generateKey();

        exchange.getMessage().setBody(secEnc1, SecretKeyWithEncapsulation.class);
    }

    private void extractEncapsulation(Exchange exchange)
            throws InvalidAlgorithmParameterException,
            InvalidPayloadException {
        // initialise for creating an encapsulation and shared secret.
        SecretKeyWithEncapsulation payload = exchange.getMessage().getMandatoryBody(SecretKeyWithEncapsulation.class);

        if (ObjectHelper.isEmpty(getConfiguration().getSymmetricKeyAlgorithm())) {
            throw new IllegalArgumentException("Symmetric Algorithm needs to be specified");
        }

        keyGenerator.init(
                new KEMExtractSpec(
                        keyPair.getPrivate(), payload.getEncapsulation(),
                        PQCSymmetricAlgorithms.valueOf(getConfiguration().getSymmetricKeyAlgorithm()).getAlgorithm(),
                        getEndpoint().getConfiguration().getSymmetricKeyLength()),
                new SecureRandom());

        // initialise for extracting the shared secret from the encapsulation.
        SecretKeyWithEncapsulation secEnc2 = (SecretKeyWithEncapsulation) keyGenerator.generateKey();

        exchange.getMessage().setBody(secEnc2, SecretKeyWithEncapsulation.class);
    }

    private void extractSecretKeyFromEncapsulation(Exchange exchange)
            throws InvalidPayloadException {
        // initialise for creating an encapsulation and shared secret.
        SecretKeyWithEncapsulation payload = exchange.getMessage().getMandatoryBody(SecretKeyWithEncapsulation.class);

        if (ObjectHelper.isEmpty(getConfiguration().getSymmetricKeyAlgorithm())) {
            throw new IllegalArgumentException("Symmetric Algorithm needs to be specified");
        }

        SecretKey restoredKey = new SecretKeySpec(payload.getEncoded(), getConfiguration().getSymmetricKeyAlgorithm());

        if (!getConfiguration().isStoreExtractedSecretKeyAsHeader()) {
            exchange.getMessage().setBody(restoredKey, SecretKey.class);
        } else {
            exchange.getMessage().setHeader(PQCConstants.SECRET_KEY, restoredKey);
        }
    }

    // ========== Hybrid Signature Operations ==========

    private void hybridSignature(Exchange exchange)
            throws InvalidPayloadException, InvalidKeyException, SignatureException {
        checkStatefulKeyBeforeSign();

        String payload = exchange.getMessage().getMandatoryBody(String.class);
        byte[] data = payload.getBytes();

        if (classicalSigner == null || classicalKeyPair == null) {
            throw new IllegalStateException(
                    "Classical signer and key pair must be configured for hybrid signature operations");
        }
        if (signer == null || keyPair == null) {
            throw new IllegalStateException("PQC signer and key pair must be configured for hybrid signature operations");
        }

        // Create hybrid signature
        byte[] hybridSig = HybridSignature.sign(
                data,
                classicalKeyPair.getPrivate(),
                classicalSigner,
                keyPair.getPrivate(),
                signer);

        // Parse to get individual signatures for headers
        HybridSignature.HybridSignatureComponents components = HybridSignature.parse(hybridSig);

        exchange.getMessage().setHeader(PQCConstants.HYBRID_SIGNATURE, hybridSig);
        exchange.getMessage().setHeader(PQCConstants.CLASSICAL_SIGNATURE, components.classicalSignature());
        exchange.getMessage().setHeader(PQCConstants.PQC_SIGNATURE, components.pqcSignature());

        try {
            persistStatefulKeyStateAfterSign(exchange);
        } catch (Exception e) {
            throw new RuntimeCamelException("Failed to persist stateful key state after hybrid signing", e);
        }
    }

    private void hybridVerification(Exchange exchange)
            throws InvalidPayloadException, InvalidKeyException, SignatureException {
        String payload = exchange.getMessage().getMandatoryBody(String.class);
        byte[] data = payload.getBytes();

        byte[] hybridSig = exchange.getMessage().getHeader(PQCConstants.HYBRID_SIGNATURE, byte[].class);
        if (hybridSig == null) {
            throw new IllegalArgumentException("Hybrid signature not found in header: " + PQCConstants.HYBRID_SIGNATURE);
        }

        if (classicalSigner == null || classicalKeyPair == null) {
            throw new IllegalStateException(
                    "Classical signer and key pair must be configured for hybrid verification operations");
        }
        if (signer == null || keyPair == null) {
            throw new IllegalStateException("PQC signer and key pair must be configured for hybrid verification operations");
        }

        // Verify hybrid signature (both must pass)
        boolean valid = HybridSignature.verify(
                data,
                hybridSig,
                classicalKeyPair.getPublic(),
                classicalSigner,
                keyPair.getPublic(),
                signer);

        exchange.getMessage().setHeader(PQCConstants.HYBRID_VERIFY, valid);
        exchange.getMessage().setHeader(PQCConstants.VERIFY, valid);
    }

    // ========== Hybrid KEM Operations ==========

    private void hybridGenerateEncapsulation(Exchange exchange) throws Exception {
        if (classicalKeyAgreement == null || classicalKeyPair == null) {
            throw new IllegalStateException(
                    "Classical key agreement and key pair must be configured for hybrid KEM operations");
        }
        if (keyGenerator == null || keyPair == null) {
            throw new IllegalStateException("PQC key generator and key pair must be configured for hybrid KEM operations");
        }
        if (ObjectHelper.isEmpty(getConfiguration().getSymmetricKeyAlgorithm())) {
            throw new IllegalArgumentException("Symmetric Algorithm needs to be specified");
        }

        String symmetricAlgorithm = getConfiguration().getSymmetricKeyAlgorithm();
        int keyLength = getConfiguration().getSymmetricKeyLength();
        String kdfAlgorithm = getConfiguration().getHybridKdfAlgorithm();

        // Generate hybrid encapsulation
        HybridKEM.HybridEncapsulationResult result = HybridKEM.encapsulate(
                classicalKeyPair.getPublic(),
                keyPair.getPublic(),
                classicalKeyAgreement,
                keyGenerator,
                symmetricAlgorithm,
                keyLength,
                kdfAlgorithm);

        // Set results
        exchange.getMessage().setBody(result.sharedSecret(), SecretKey.class);
        exchange.getMessage().setHeader(PQCConstants.HYBRID_ENCAPSULATION, result.encapsulation());
        exchange.getMessage().setHeader(PQCConstants.HYBRID_SECRET_KEY, result.sharedSecret());

        // Parse for individual encapsulations
        HybridKEM.HybridEncapsulationComponents components = HybridKEM.parse(result.encapsulation());
        exchange.getMessage().setHeader(PQCConstants.CLASSICAL_ENCAPSULATION, components.classicalEncapsulation());
        exchange.getMessage().setHeader(PQCConstants.PQC_ENCAPSULATION, components.pqcEncapsulation());
    }

    private void hybridExtractEncapsulation(Exchange exchange) throws Exception {
        byte[] hybridEncapsulation = exchange.getMessage().getHeader(PQCConstants.HYBRID_ENCAPSULATION, byte[].class);
        if (hybridEncapsulation == null) {
            throw new IllegalArgumentException(
                    "Hybrid encapsulation not found in header: " + PQCConstants.HYBRID_ENCAPSULATION);
        }

        if (classicalKeyAgreement == null || classicalKeyPair == null) {
            throw new IllegalStateException(
                    "Classical key agreement and key pair must be configured for hybrid KEM extraction");
        }
        if (keyGenerator == null || keyPair == null) {
            throw new IllegalStateException("PQC key generator and key pair must be configured for hybrid KEM extraction");
        }
        if (ObjectHelper.isEmpty(getConfiguration().getSymmetricKeyAlgorithm())) {
            throw new IllegalArgumentException("Symmetric Algorithm needs to be specified");
        }

        String symmetricAlgorithm = getConfiguration().getSymmetricKeyAlgorithm();
        int keyLength = getConfiguration().getSymmetricKeyLength();
        String kdfAlgorithm = getConfiguration().getHybridKdfAlgorithm();

        // Extract shared secret from hybrid encapsulation
        SecretKey sharedSecret = HybridKEM.extract(
                hybridEncapsulation,
                classicalKeyPair.getPrivate(),
                keyPair.getPrivate(),
                classicalKeyAgreement,
                keyGenerator,
                symmetricAlgorithm,
                keyLength,
                kdfAlgorithm);

        exchange.getMessage().setBody(sharedSecret, SecretKey.class);
        exchange.getMessage().setHeader(PQCConstants.HYBRID_SECRET_KEY, sharedSecret);
    }

    private void hybridExtractSecretKeyFromEncapsulation(Exchange exchange) throws Exception {
        // Get the hybrid secret key from header or body
        SecretKey hybridSecretKey = exchange.getMessage().getHeader(PQCConstants.HYBRID_SECRET_KEY, SecretKey.class);
        if (hybridSecretKey == null) {
            hybridSecretKey = exchange.getMessage().getBody(SecretKey.class);
        }
        if (hybridSecretKey == null) {
            throw new IllegalArgumentException("Hybrid secret key not found in header or body");
        }

        if (ObjectHelper.isEmpty(getConfiguration().getSymmetricKeyAlgorithm())) {
            throw new IllegalArgumentException("Symmetric Algorithm needs to be specified");
        }

        // Create a new SecretKeySpec with the correct algorithm
        SecretKey restoredKey = new SecretKeySpec(hybridSecretKey.getEncoded(), getConfiguration().getSymmetricKeyAlgorithm());

        if (!getConfiguration().isStoreExtractedSecretKeyAsHeader()) {
            exchange.getMessage().setBody(restoredKey, SecretKey.class);
        } else {
            exchange.getMessage().setHeader(PQCConstants.SECRET_KEY, restoredKey);
        }
    }

    // ========== Key Lifecycle Operations ==========

    private KeyLifecycleManager getRequiredKeyLifecycleManager() {
        KeyLifecycleManager klm = getConfiguration().getKeyLifecycleManager();
        if (klm == null) {
            throw new IllegalStateException(
                    "A KeyLifecycleManager must be configured to use key lifecycle operations. "
                                            + "Set the keyLifecycleManager option on the PQC endpoint.");
        }
        return klm;
    }

    private void lifecycleGenerateKeyPair(Exchange exchange) throws Exception {
        KeyLifecycleManager klm = getRequiredKeyLifecycleManager();
        String algorithm = exchange.getMessage().getHeader(PQCConstants.ALGORITHM, String.class);
        String keyId = exchange.getMessage().getHeader(PQCConstants.KEY_ID, String.class);

        if (ObjectHelper.isEmpty(algorithm)) {
            throw new IllegalArgumentException(
                    "Algorithm header (" + PQCConstants.ALGORITHM + ") is required for generateKeyPair");
        }
        if (ObjectHelper.isEmpty(keyId)) {
            throw new IllegalArgumentException("Key ID header (" + PQCConstants.KEY_ID + ") is required for generateKeyPair");
        }

        KeyPair generated = klm.generateKeyPair(algorithm, keyId);
        exchange.getMessage().setHeader(PQCConstants.KEY_PAIR, generated);
    }

    private void lifecycleExportKey(Exchange exchange) throws Exception {
        KeyLifecycleManager klm = getRequiredKeyLifecycleManager();
        String formatStr = exchange.getMessage().getHeader(PQCConstants.KEY_FORMAT, String.class);
        Boolean includePrivate = exchange.getMessage().getHeader(PQCConstants.INCLUDE_PRIVATE, Boolean.class);

        KeyLifecycleManager.KeyFormat format = formatStr != null
                ? KeyLifecycleManager.KeyFormat.valueOf(formatStr)
                : KeyLifecycleManager.KeyFormat.DER;
        boolean inclPriv = includePrivate != null ? includePrivate : false;

        byte[] exported = klm.exportKey(keyPair, format, inclPriv);
        exchange.getMessage().setHeader(PQCConstants.EXPORTED_KEY, exported);
    }

    private void lifecycleImportKey(Exchange exchange) throws Exception {
        KeyLifecycleManager klm = getRequiredKeyLifecycleManager();
        byte[] keyData = exchange.getMessage().getMandatoryBody(byte[].class);
        String formatStr = exchange.getMessage().getHeader(PQCConstants.KEY_FORMAT, String.class);
        String algorithm = exchange.getMessage().getHeader(PQCConstants.ALGORITHM, String.class);

        KeyLifecycleManager.KeyFormat format = formatStr != null
                ? KeyLifecycleManager.KeyFormat.valueOf(formatStr)
                : KeyLifecycleManager.KeyFormat.DER;

        if (ObjectHelper.isEmpty(algorithm)) {
            throw new IllegalArgumentException("Algorithm header (" + PQCConstants.ALGORITHM + ") is required for importKey");
        }

        KeyPair imported = klm.importKey(keyData, format, algorithm);
        exchange.getMessage().setHeader(PQCConstants.KEY_PAIR, imported);
    }

    private void lifecycleRotateKey(Exchange exchange) throws Exception {
        KeyLifecycleManager klm = getRequiredKeyLifecycleManager();
        String oldKeyId = exchange.getMessage().getHeader(PQCConstants.KEY_ID, String.class);
        String newKeyId = exchange.getMessage().getHeader("CamelPQCNewKeyId", String.class);
        String algorithm = exchange.getMessage().getHeader(PQCConstants.ALGORITHM, String.class);

        if (ObjectHelper.isEmpty(oldKeyId)) {
            throw new IllegalArgumentException("Key ID header (" + PQCConstants.KEY_ID + ") is required for rotateKey");
        }
        if (ObjectHelper.isEmpty(newKeyId)) {
            throw new IllegalArgumentException("New Key ID header (CamelPQCNewKeyId) is required for rotateKey");
        }
        if (ObjectHelper.isEmpty(algorithm)) {
            throw new IllegalArgumentException("Algorithm header (" + PQCConstants.ALGORITHM + ") is required for rotateKey");
        }

        KeyPair rotated = klm.rotateKey(oldKeyId, newKeyId, algorithm);
        exchange.getMessage().setHeader(PQCConstants.KEY_PAIR, rotated);
    }

    private void lifecycleGetKeyMetadata(Exchange exchange) throws Exception {
        KeyLifecycleManager klm = getRequiredKeyLifecycleManager();
        String keyId = exchange.getMessage().getHeader(PQCConstants.KEY_ID, String.class);

        if (ObjectHelper.isEmpty(keyId)) {
            throw new IllegalArgumentException("Key ID header (" + PQCConstants.KEY_ID + ") is required for getKeyMetadata");
        }

        KeyMetadata metadata = klm.getKeyMetadata(keyId);
        exchange.getMessage().setHeader(PQCConstants.KEY_METADATA, metadata);
    }

    private void lifecycleListKeys(Exchange exchange) throws Exception {
        KeyLifecycleManager klm = getRequiredKeyLifecycleManager();
        List<KeyMetadata> keys = klm.listKeys();
        exchange.getMessage().setHeader(PQCConstants.KEY_LIST, keys);
    }

    private void lifecycleExpireKey(Exchange exchange) throws Exception {
        KeyLifecycleManager klm = getRequiredKeyLifecycleManager();
        String keyId = exchange.getMessage().getHeader(PQCConstants.KEY_ID, String.class);

        if (ObjectHelper.isEmpty(keyId)) {
            throw new IllegalArgumentException("Key ID header (" + PQCConstants.KEY_ID + ") is required for expireKey");
        }

        klm.expireKey(keyId);
    }

    private void lifecycleRevokeKey(Exchange exchange) throws Exception {
        KeyLifecycleManager klm = getRequiredKeyLifecycleManager();
        String keyId = exchange.getMessage().getHeader(PQCConstants.KEY_ID, String.class);
        String reason = exchange.getMessage().getHeader(PQCConstants.REVOCATION_REASON, String.class);

        if (ObjectHelper.isEmpty(keyId)) {
            throw new IllegalArgumentException("Key ID header (" + PQCConstants.KEY_ID + ") is required for revokeKey");
        }

        klm.revokeKey(keyId, reason);
    }

    // ========== Stateful Key Operations ==========

    private void statefulGetRemainingSignatures(Exchange exchange) {
        if (keyPair == null || keyPair.getPrivate() == null) {
            throw new IllegalStateException("A KeyPair with a private key is required for getRemainingSignatures");
        }

        PrivateKey privateKey = keyPair.getPrivate();
        long remaining = getStatefulKeyRemaining(privateKey);

        if (remaining < 0) {
            throw new IllegalArgumentException(
                    "getRemainingSignatures is only supported for stateful signature schemes (XMSS, XMSSMT, LMS/HSS). "
                                               + "Key type: " + privateKey.getClass().getName());
        }

        exchange.getMessage().setHeader(PQCConstants.REMAINING_SIGNATURES, remaining);
    }

    private void statefulGetKeyState(Exchange exchange) {
        if (keyPair == null || keyPair.getPrivate() == null) {
            throw new IllegalStateException("A KeyPair with a private key is required for getKeyState");
        }

        PrivateKey privateKey = keyPair.getPrivate();
        long remaining = getStatefulKeyRemaining(privateKey);

        if (remaining < 0) {
            throw new IllegalArgumentException(
                    "getKeyState is only supported for stateful signature schemes (XMSS, XMSSMT, LMS/HSS). "
                                               + "Key type: " + privateKey.getClass().getName());
        }

        long index = getStatefulKeyIndex(privateKey);
        StatefulKeyState state = new StatefulKeyState(privateKey.getAlgorithm(), index, remaining);

        exchange.getMessage().setHeader(PQCConstants.KEY_STATE, state);
    }

    private void statefulDeleteKeyState(Exchange exchange) throws Exception {
        String keyId = exchange.getMessage().getHeader(PQCConstants.KEY_ID, String.class);

        if (ObjectHelper.isEmpty(keyId)) {
            throw new IllegalArgumentException("Key ID header (" + PQCConstants.KEY_ID + ") is required for deleteKeyState");
        }

        KeyLifecycleManager klm = getConfiguration().getKeyLifecycleManager();
        if (klm != null) {
            klm.deleteKey(keyId);
        }
    }

    /**
     * Checks whether the current key is a stateful signature key (XMSS, XMSSMT, LMS/HSS) and if so, validates that it
     * has remaining signatures available. Logs a warning when remaining signatures fall below the configured threshold.
     *
     * @throws IllegalStateException if the key has zero remaining signatures
     */
    private void checkStatefulKeyBeforeSign() {
        if (keyPair == null || keyPair.getPrivate() == null) {
            return;
        }

        PrivateKey privateKey = keyPair.getPrivate();
        long remaining = getStatefulKeyRemaining(privateKey);
        if (remaining < 0) {
            // Not a stateful key
            return;
        }

        if (remaining == 0) {
            throw new IllegalStateException(
                    "Stateful key (" + privateKey.getAlgorithm() + ") is exhausted with 0 remaining signatures. "
                                            + "The key must not be reused — generate a new key pair.");
        }

        double threshold = getConfiguration().getStatefulKeyWarningThreshold();
        if (threshold > 0) {
            long totalCapacity = getStatefulKeyIndex(privateKey) + remaining;
            if (totalCapacity > 0) {
                double fractionRemaining = (double) remaining / totalCapacity;
                if (fractionRemaining <= threshold) {
                    LOG.warn(
                            "Stateful key ({}) is approaching exhaustion: {} signatures remaining out of {} total ({} remaining). "
                             + "Consider generating a new key pair.",
                            privateKey.getAlgorithm(), remaining, totalCapacity,
                            String.format("%.1f%%", fractionRemaining * 100));
                }
            }
        }
    }

    /**
     * Persists stateful key state after a signing operation through the KeyLifecycleManager, if configured. This
     * ensures the key index is tracked across restarts.
     */
    private void persistStatefulKeyStateAfterSign(Exchange exchange) throws Exception {
        if (keyPair == null || keyPair.getPrivate() == null) {
            return;
        }

        PrivateKey privateKey = keyPair.getPrivate();
        long remaining = getStatefulKeyRemaining(privateKey);
        if (remaining < 0) {
            // Not a stateful key
            return;
        }

        KeyLifecycleManager klm = getConfiguration().getKeyLifecycleManager();
        if (klm == null) {
            return;
        }

        String keyId = exchange.getMessage().getHeader(PQCConstants.KEY_ID, String.class);
        if (ObjectHelper.isEmpty(keyId)) {
            return;
        }

        // Update metadata with current usage
        KeyMetadata metadata = klm.getKeyMetadata(keyId);
        if (metadata == null) {
            LOG.warn(
                    "No metadata found for stateful key '{}'. The key index has been advanced by the signing operation "
                     + "but cannot be persisted — on restart this index advance will be lost, which may lead to key reuse. "
                     + "Ensure a KeyLifecycleManager is properly configured and the key is stored with metadata.",
                    keyId);
            return;
        }

        metadata.updateLastUsed();
        klm.updateKeyMetadata(keyId, metadata);

        // Persist the updated key (with new index) so state survives restarts
        klm.storeKey(keyId, keyPair, metadata);
    }

    /**
     * Returns the remaining signatures for a stateful private key, or -1 if the key is not stateful.
     */
    static long getStatefulKeyRemaining(PrivateKey privateKey) {
        if (privateKey instanceof XMSSPrivateKey xmssPrivateKey) {
            return xmssPrivateKey.getUsagesRemaining();
        } else if (privateKey instanceof XMSSMTPrivateKey xmssmtPrivateKey) {
            return xmssmtPrivateKey.getUsagesRemaining();
        } else if (privateKey instanceof LMSPrivateKey lmsPrivateKey) {
            return lmsPrivateKey.getUsagesRemaining();
        }
        return -1;
    }

    /**
     * Returns the current index (number of signatures already produced) for a stateful private key, or 0 if the key is
     * not stateful.
     */
    static long getStatefulKeyIndex(PrivateKey privateKey) {
        if (privateKey instanceof XMSSPrivateKey xmssPrivateKey) {
            return xmssPrivateKey.getIndex();
        } else if (privateKey instanceof XMSSMTPrivateKey xmssmtPrivateKey) {
            return xmssmtPrivateKey.getIndex();
        } else if (privateKey instanceof LMSPrivateKey lmsPrivateKey) {
            return lmsPrivateKey.getIndex();
        }
        return 0;
    }

    // ========== Configuration Validation ==========

    /**
     * Validates the producer configuration at startup to catch invalid or non-recommended algorithm combinations and
     * key sizes early, before any cryptographic operation is attempted.
     */
    private void validateConfiguration() {
        PQCConfiguration config = getConfiguration();
        PQCOperations op = config.getOperation();

        validateSymmetricKeyLength(config, op);
        warnHybridCombinations(config, op);
        logNistRecommendations(config);
    }

    private void validateSymmetricKeyLength(PQCConfiguration config, PQCOperations op) {
        if (!isKEMOperation(op)) {
            return;
        }
        String symAlg = config.getSymmetricKeyAlgorithm();
        if (ObjectHelper.isEmpty(symAlg)) {
            return;
        }
        int keyLen = config.getSymmetricKeyLength();
        int[] validLengths = VALID_SYMMETRIC_KEY_LENGTHS.get(symAlg);
        if (validLengths != null) {
            boolean valid = false;
            for (int len : validLengths) {
                if (len == keyLen) {
                    valid = true;
                    break;
                }
            }
            if (!valid) {
                throw new IllegalArgumentException(
                        "Invalid symmetric key length " + keyLen + " for algorithm " + symAlg
                                                   + ". Valid key lengths: " + Arrays.toString(validLengths));
            }
        }
    }

    private void warnHybridCombinations(PQCConfiguration config, PQCOperations op) {
        if (op == PQCOperations.hybridSign || op == PQCOperations.hybridVerify) {
            String classicalAlg = config.getClassicalSignatureAlgorithm();
            if (ObjectHelper.isNotEmpty(classicalAlg)) {
                try {
                    PQCClassicalSignatureAlgorithms classical = PQCClassicalSignatureAlgorithms.valueOf(classicalAlg);
                    if (classical.isRSA()) {
                        LOG.warn("Using RSA ({}) in hybrid signature mode. ECDSA or EdDSA (Ed25519/Ed448) "
                                 + "is recommended for new hybrid deployments due to smaller signature sizes "
                                 + "and better performance.",
                                classicalAlg);
                    }
                } catch (IllegalArgumentException e) {
                    // Unknown classical algorithm - will fail later during init
                }
            }
            String pqcAlg = config.getSignatureAlgorithm();
            if (ObjectHelper.isNotEmpty(pqcAlg) && NON_FIPS_PQ_SIGNATURES.contains(pqcAlg)) {
                LOG.warn("PQC signature algorithm {} is not part of NIST FIPS 204/205. Consider using "
                         + "ML-DSA (FIPS 204) or SLH-DSA (FIPS 205) for production hybrid deployments.",
                        pqcAlg);
            }
        }

        if (op == PQCOperations.hybridGenerateSecretKeyEncapsulation
                || op == PQCOperations.hybridExtractSecretKeyEncapsulation
                || op == PQCOperations.hybridExtractSecretKeyFromEncapsulation) {
            String pqcAlg = config.getKeyEncapsulationAlgorithm();
            if (ObjectHelper.isNotEmpty(pqcAlg) && NON_FIPS_PQ_KEMS.contains(pqcAlg)) {
                LOG.warn("PQC KEM algorithm {} is not part of NIST FIPS 203. Consider using "
                         + "ML-KEM (FIPS 203) for production hybrid deployments.",
                        pqcAlg);
            }
        }
    }

    private void logNistRecommendations(PQCConfiguration config) {
        String kemAlg = config.getKeyEncapsulationAlgorithm();
        if (PQCKeyEncapsulationAlgorithms.MLKEM.name().equals(kemAlg)) {
            LOG.info("Using ML-KEM (NIST FIPS 203). Available parameter sets: "
                     + "ML-KEM-512 (Level 1), ML-KEM-768 (Level 3, recommended), ML-KEM-1024 (Level 5)");
        }

        String sigAlg = config.getSignatureAlgorithm();
        if (PQCSignatureAlgorithms.MLDSA.name().equals(sigAlg)) {
            LOG.info("Using ML-DSA (NIST FIPS 204). Available parameter sets: "
                     + "ML-DSA-44 (Level 2), ML-DSA-65 (Level 3, recommended), ML-DSA-87 (Level 5)");
        } else if (PQCSignatureAlgorithms.SLHDSA.name().equals(sigAlg)) {
            LOG.info("Using SLH-DSA (NIST FIPS 205). Stateless hash-based signature scheme suitable for "
                     + "applications where stateful key management is not feasible");
        }
    }

    private boolean isKEMOperation(PQCOperations op) {
        switch (op) {
            case generateSecretKeyEncapsulation:
            case extractSecretKeyEncapsulation:
            case extractSecretKeyFromEncapsulation:
            case hybridGenerateSecretKeyEncapsulation:
            case hybridExtractSecretKeyEncapsulation:
            case hybridExtractSecretKeyFromEncapsulation:
                return true;
            default:
                return false;
        }
    }

}
