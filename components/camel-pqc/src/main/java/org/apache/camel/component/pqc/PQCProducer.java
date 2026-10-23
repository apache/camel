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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.apache.camel.Message;
import org.apache.camel.StreamCache;
import org.apache.camel.TypeConverter;
import org.apache.camel.WrappedFile;
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

    private static final int STREAM_BUFFER_SIZE = 8192;

    private Signature signer;
    // Set only when this producer created the Signature itself, so it knows how to create another one.
    // Left null when the user configured an instance, which then has to be shared and locked instead.
    private String signerAlgorithm;
    private String signerProvider;
    private KeyGenerator keyGenerator;
    // The same for the KEM KeyGenerator. The provider is kept as an object, so creating another KeyGenerator does not
    // depend on it still being registered in the JVM
    private String keyGeneratorAlgorithm;
    private Provider keyGeneratorProvider;
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
                keyGeneratorAlgorithm = kemAlg.getAlgorithm();
                keyGeneratorProvider = keyGenerator.getProvider();
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
            throws Exception {
        Signature signerForExchange = signerForExchange();
        byte[] signature;
        synchronized (signerForExchange) {
            signerForExchange.initSign(keyPair.getPrivate());
            updateSignatureFromBody(signerForExchange, exchange.getMessage());
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
            throws InvalidPayloadException, InvalidKeyException, SignatureException, IOException,
            NoSuchAlgorithmException, NoSuchProviderException {
        Signature signerForExchange = signerForExchange();
        boolean verified;
        synchronized (signerForExchange) {
            signerForExchange.initVerify(keyPair.getPublic());
            updateSignatureFromBody(signerForExchange, exchange.getMessage());
            verified = signerForExchange.verify(exchange.getMessage().getHeader(PQCConstants.SIGNATURE, byte[].class));
        }
        exchange.getMessage().setHeader(PQCConstants.VERIFY, verified);
    }


    /**
     * Feeds the message body into the given {@link Signature} without materialising the whole payload as a String. A
     * {@code byte[]} body is used directly, an {@link InputStream} body is read in chunks (and reset afterwards when it
     * is a re-readable {@link StreamCache} so downstream processors still see the payload), and a file body is read in
     * chunks from the file (see {@link #fileContent(Object)}). A {@code String} or {@link CharSequence} body is encoded
     * as UTF-8, and any other body is read through {@link #rawBytes(Message, Object)}, which either gets its bytes or
     * rejects it.
     */
    private static void updateSignatureFromBody(Signature signature, Message message)
            throws InvalidPayloadException, SignatureException, IOException {
        Object body = fileContent(message.getBody());
        if (body instanceof byte[] bytes) {
            signature.update(bytes);
        } else if (body instanceof Path path) {
            try (InputStream stream = Files.newInputStream(path)) {
                updateSignatureFromStream(signature, stream);
            }
        } else if (body instanceof InputStream stream) {
            try {
                updateSignatureFromStream(signature, stream);
            } finally {
                if (body instanceof StreamCache streamCache) {
                    streamCache.reset();
                }
            }
        } else if (body instanceof CharSequence text) {
            signature.update(textToBytes(text));
        } else {
            signature.update(rawBytes(message, body));
        }
    }

    /**
     * Encodes a text body as UTF-8. The JVM default charset is platform dependent, so signing and verifying on JVMs
     * with a different default would disagree on the bytes for a non-ASCII payload; UTF-8 keeps them in step.
     */
    private static byte[] textToBytes(CharSequence text) {
        return text.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Returns the bytes of a body that is neither a {@code byte[]}, an {@link InputStream}, a file nor a
     * {@link CharSequence}, by asking the type converter registry for a registered converter to {@code byte[]} - which
     * covers bodies with a binary form such as a {@link java.nio.ByteBuffer}, a {@link java.io.ByteArrayOutputStream},
     * a Netty {@code ByteBuf} or a Vert.x {@code Buffer}, so they are signed over their bytes rather than over a text
     * rendering. A body with no such converter (for example a {@code Map} or a POJO) has no well-defined bytes to sign,
     * so it is rejected with {@link InvalidPayloadException} rather than signed over an incidental text form. Fallback
     * converters are not consulted, so the result does not depend on which components are on the classpath.
     */
    private static byte[] rawBytes(Message message, Object body) throws InvalidPayloadException {
        Exchange exchange = message.getExchange();
        if (body != null) {
            // lookup finds only a registered converter, so a body with a binary form (ByteBuffer, ByteArrayOutputStream,
            // Netty ByteBuf, Vert.x Buffer, ...) is converted, while a Map or POJO - which would only convert through a
            // fallback converter, if any component provides one - is left for the InvalidPayloadException below, so the
            // outcome does not depend on the classpath. tryConvertTo returns null instead of throwing on a miss.
            TypeConverter converter = exchange.getContext().getTypeConverterRegistry().lookup(byte[].class, body.getClass());
            if (converter != null) {
                byte[] bytes = converter.tryConvertTo(byte[].class, exchange, body);
                if (bytes != null) {
                    return bytes;
                }
            }
        }
        throw new InvalidPayloadException(exchange, byte[].class, message);
    }

    private static void updateSignatureFromStream(Signature signature, InputStream stream)
            throws SignatureException, IOException {
        byte[] buffer = new byte[STREAM_BUFFER_SIZE];
        int read;
        while ((read = stream.read(buffer)) != -1) {
            signature.update(buffer, 0, read);
        }
    }

    /**
     * Resolves a file body to the content to sign: a {@link WrappedFile} (the body of the file based consumers) gives
     * its content, and a {@link File} is turned into its {@link Path}, so that the bytes of the file are read as they
     * are. The content of a {@link WrappedFile} is a {@link File} for the local file system, otherwise the content the
     * consumer has already loaded, such as a {@code byte[]} or an {@link InputStream}. Other bodies are not changed.
     */
    private static Object fileContent(Object body) {
        if (body instanceof WrappedFile<?> wrappedFile) {
            Object content = wrappedFile.getBody();
            if (content != null) {
                body = content;
            }
        }
        if (body instanceof File file) {
            return file.toPath();
        }
        return body;
    }

    /**
     * Returns the KEM {@link KeyGenerator} to use for a single exchange.
     * <p>
     * A KEM {@code KeyGenerator} keeps the spec passed to {@code init()} until {@code generateKey()} reads it and is
     * not thread-safe, so the two calls of one exchange must not interleave with those of another. As with
     * {@link #signerForExchange()}, an instance this producer created is simply created again, while a configured one -
     * an autowired bean can be shared by every endpoint, and {@code PQCDefault*Material.keyGenerator} JVM-wide - is
     * handed back as-is, and the caller serializes on whatever it gets.
     */
    private KeyGenerator keyGeneratorForExchange() throws NoSuchAlgorithmException {
        if (keyGeneratorAlgorithm == null) {
            return keyGenerator;
        }
        return KeyGenerator.getInstance(keyGeneratorAlgorithm, keyGeneratorProvider);
    }

    private void generateEncapsulation(Exchange exchange)
            throws InvalidAlgorithmParameterException, NoSuchAlgorithmException {
        KeyGenerator generator = keyGeneratorForExchange();
        SecretKeyWithEncapsulation secEnc1;
        synchronized (generator) {
            // initialise for creating an encapsulation and shared secret.
            generator.init(
                    new KEMGenerateSpec(
                            keyPair.getPublic(),
                            getEndpoint().getConfiguration().getSymmetricKeyAlgorithm(),
                            getEndpoint().getConfiguration().getSymmetricKeyLength()),
                    new SecureRandom());
            // SecretKeyWithEncapsulation is the class to use as the secret key, it has additional
            // methods on it for recovering the encapsulation as well.
            secEnc1 = (SecretKeyWithEncapsulation) generator.generateKey();
        }

        exchange.getMessage().setBody(secEnc1, SecretKeyWithEncapsulation.class);
    }

    private void extractEncapsulation(Exchange exchange)
            throws InvalidAlgorithmParameterException,
            InvalidPayloadException, NoSuchAlgorithmException {
        // initialise for creating an encapsulation and shared secret.
        SecretKeyWithEncapsulation payload = exchange.getMessage().getMandatoryBody(SecretKeyWithEncapsulation.class);

        if (ObjectHelper.isEmpty(getConfiguration().getSymmetricKeyAlgorithm())) {
            throw new IllegalArgumentException("Symmetric Algorithm needs to be specified");
        }

        KeyGenerator generator = keyGeneratorForExchange();
        SecretKeyWithEncapsulation secEnc2;
        synchronized (generator) {
            generator.init(
                    new KEMExtractSpec(
                            keyPair.getPrivate(), payload.getEncapsulation(),
                            PQCSymmetricAlgorithms.valueOf(getConfiguration().getSymmetricKeyAlgorithm()).getAlgorithm(),
                            getEndpoint().getConfiguration().getSymmetricKeyLength()),
                    new SecureRandom());

            // initialise for extracting the shared secret from the encapsulation.
            secEnc2 = (SecretKeyWithEncapsulation) generator.generateKey();
        }

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
