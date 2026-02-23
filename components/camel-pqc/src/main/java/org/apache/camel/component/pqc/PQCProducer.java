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

import javax.crypto.KeyAgreement;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.component.pqc.crypto.hybrid.HybridKEM;
import org.apache.camel.component.pqc.crypto.hybrid.HybridSignature;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.bouncycastle.jcajce.SecretKeyWithEncapsulation;
import org.bouncycastle.jcajce.spec.KEMExtractSpec;
import org.bouncycastle.jcajce.spec.KEMGenerateSpec;

/**
 * A Producer which sign or verify a payload
 */
public class PQCProducer extends DefaultProducer {

    private Signature signer;
    private KeyGenerator keyGenerator;
    private KeyPair keyPair;

    // Hybrid cryptography fields
    private Signature classicalSigner;
    private KeyAgreement classicalKeyAgreement;
    private KeyPair classicalKeyPair;

    public PQCProducer(Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        switch (determineOperation(exchange)) {
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

    @Override
    public PQCEndpoint getEndpoint() {
        return (PQCEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

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
    }

    private void signature(Exchange exchange)
            throws InvalidPayloadException, InvalidKeyException, SignatureException {
        String payload = exchange.getMessage().getMandatoryBody(String.class);

        signer.initSign(keyPair.getPrivate());
        signer.update(payload.getBytes());

        byte[] signature = signer.sign();
        exchange.getMessage().setHeader(PQCConstants.SIGNATURE, signature);
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

}
