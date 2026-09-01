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

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.bouncycastle.jcajce.SecretKeyWithEncapsulation;
import org.bouncycastle.jcajce.spec.KEMExtractSpec;
import org.bouncycastle.jcajce.spec.KEMGenerateSpec;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Producer which sign or verify a payload
 */
public class PQCProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(PQCProducer.class);

    private Signature signer;
    // Set only when this producer created the Signature itself, so it knows how to create another one.
    // Left null when the user configured an instance, which then has to be shared and locked instead.
    private String signerAlgorithm;
    private String signerProvider;
    private KeyGenerator keyGenerator;
    private KeyPair keyPair;

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
            case generateSecretKeyEncapsulation:
                generateEncapsulation(exchange);
                break;
            case extractSecretKeyEncapsulation:
                extractEncapsulation(exchange);
                break;
            case extractSecretKeyFromEncapsulation:
                extractSecretKeyFromEncapsulation(exchange);
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
                signerAlgorithm = sigAlg.getAlgorithm();
                signerProvider = sigAlg.getBcProvider();
                signer = Signature.getInstance(signerAlgorithm, signerProvider);
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

        // On JDK 25+, a JKS KeyStore (or user-supplied KeyPair) may contain JDK-native PQC keys
        // (e.g. ML-DSA, ML-KEM) that Bouncy Castle's Signature / KeyGenerator SPI does not recognise,
        // causing InvalidKeyException at initSign / initVerify time. Re-encoding through BC's KeyFactory
        // transparently converts JDK-native keys into the BC types the rest of the component expects,
        // and is a no-op for keys that are already BC instances.
        if (keyPair != null) {
            keyPair = ensureBcKeyPair(keyPair);
        }
    }

    private void signature(Exchange exchange)
            throws InvalidPayloadException, InvalidKeyException, SignatureException, NoSuchAlgorithmException,
            NoSuchProviderException {
        String payload = exchange.getMessage().getMandatoryBody(String.class);

        Signature signerForExchange = signerForExchange();
        byte[] signature;
        synchronized (signerForExchange) {
            signerForExchange.initSign(keyPair.getPrivate());
            signerForExchange.update(payload.getBytes(StandardCharsets.UTF_8));
            signature = signerForExchange.sign();
        }
        exchange.getMessage().setHeader(PQCConstants.SIGNATURE, signature);
    }

    /**
     * java.security.Signature is stateful and not thread safe, so a single instance cannot serve concurrent exchanges.
     * A signer this producer created is rebuilt per exchange; one the user configured cannot be recreated, so it is
     * shared and the caller locks on it.
     */
    private Signature signerForExchange() throws NoSuchAlgorithmException, NoSuchProviderException {
        if (signerAlgorithm == null) {
            return signer;
        }
        return Signature.getInstance(signerAlgorithm, signerProvider);
    }

    private void verification(Exchange exchange)
            throws InvalidPayloadException, InvalidKeyException, SignatureException, NoSuchAlgorithmException,
            NoSuchProviderException {
        String payload = exchange.getMessage().getMandatoryBody(String.class);

        Signature signerForExchange = signerForExchange();
        boolean verified;
        synchronized (signerForExchange) {
            signerForExchange.initVerify(keyPair.getPublic());
            signerForExchange.update(payload.getBytes(StandardCharsets.UTF_8));
            verified = signerForExchange.verify(exchange.getMessage().getHeader(PQCConstants.SIGNATURE, byte[].class));
        }
        exchange.getMessage().setHeader(PQCConstants.VERIFY, verified);
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

        // Use the mapped JCE algorithm name (as extractEncapsulation does), not the raw enum name: for algorithms
        // whose enum name differs from the JCE name (for example GOST3412_2015 -> GOST3412-2015) the raw name is not a
        // resolvable cipher algorithm, so the restored key would carry an unusable algorithm label
        SecretKey restoredKey = new SecretKeySpec(
                payload.getEncoded(),
                PQCSymmetricAlgorithms.valueOf(getConfiguration().getSymmetricKeyAlgorithm()).getAlgorithm());

        if (!getConfiguration().isStoreExtractedSecretKeyAsHeader()) {
            exchange.getMessage().setBody(restoredKey, SecretKey.class);
        } else {
            exchange.getMessage().setHeader(PQCConstants.SECRET_KEY, restoredKey);
        }
    }

    /**
     * Ensures both keys in the pair are Bouncy Castle key instances.
     * <p>
     * On JDK 25+, a JKS {@link KeyStore} may deserialise standardised PQC keys (ML-DSA, ML-KEM) into JDK-native key
     * objects that Bouncy Castle's {@link Signature} / {@link KeyGenerator} SPI does not recognise, causing
     * {@link InvalidKeyException} at {@code initSign} / {@code initVerify} time.
     * <p>
     * Re-encoding through BC's {@link KeyFactory} is a no-op for keys that are already BC instances and transparently
     * converts JDK-native ones into the BC types the rest of the component expects.
     */
    private static KeyPair ensureBcKeyPair(KeyPair kp) {
        PrivateKey priv = kp.getPrivate();
        PublicKey pub = kp.getPublic();

        boolean privIsBc = priv == null || priv.getClass().getName().startsWith("org.bouncycastle.");
        boolean pubIsBc = pub == null || pub.getClass().getName().startsWith("org.bouncycastle.");
        if (privIsBc && pubIsBc) {
            return kp;
        }

        try {
            String alg = priv != null ? priv.getAlgorithm() : pub.getAlgorithm();
            KeyFactory kf = getBcKeyFactory(alg);

            if (!privIsBc) {
                priv = kf.generatePrivate(new PKCS8EncodedKeySpec(priv.getEncoded()));
            }
            if (!pubIsBc) {
                pub = kf.generatePublic(new X509EncodedKeySpec(pub.getEncoded()));
            }
            return new KeyPair(pub, priv);
        } catch (Exception e) {
            // If conversion fails (e.g. algorithm not known to BC), return the original pair
            // and let the caller deal with any resulting exception from the crypto operation
            LOG.debug("Could not convert KeyPair to Bouncy Castle key types: {}", e.getMessage());
            return kp;
        }
    }

    /**
     * Returns a BC {@link KeyFactory} for the given JCE algorithm name, trying the main BC provider first and falling
     * back to the BC PQC provider.
     */
    private static KeyFactory getBcKeyFactory(String algorithm)
            throws NoSuchAlgorithmException, NoSuchProviderException {
        try {
            return KeyFactory.getInstance(algorithm, BouncyCastleProvider.PROVIDER_NAME);
        } catch (NoSuchAlgorithmException e) {
            return KeyFactory.getInstance(algorithm, BouncyCastlePQCProvider.PROVIDER_NAME);
        }
    }

}
