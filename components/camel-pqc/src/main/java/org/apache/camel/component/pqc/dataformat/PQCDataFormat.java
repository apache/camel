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
package org.apache.camel.component.pqc.dataformat;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Set;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.camel.Exchange;
import org.apache.camel.component.pqc.PQCKeyEncapsulationAlgorithms;
import org.apache.camel.component.pqc.PQCSymmetricAlgorithms;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.spi.annotations.Dataformat;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.bouncycastle.jcajce.SecretKeyWithEncapsulation;
import org.bouncycastle.jcajce.spec.KEMExtractSpec;
import org.bouncycastle.jcajce.spec.KEMGenerateSpec;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PQCDataFormat uses Post-Quantum Cryptography Key Encapsulation Mechanisms (KEM) to securely encrypt and decrypt
 * message payloads.
 *
 * <p>
 * This DataFormat provides quantum-resistant encryption by:
 * </p>
 * <ul>
 * <li>Using KEM algorithms (ML-KEM, BIKE, etc.) to establish a fresh shared secret per message</li>
 * <li>Encrypting data with an <em>authenticated</em> symmetric cipher (AEAD) using the shared secret</li>
 * <li>Including the encapsulated key and the AEAD nonce in the output stream for decryption</li>
 * </ul>
 *
 * <p>
 * The symmetric (data-encapsulation) layer always uses authenticated encryption so that both confidentiality and
 * integrity are protected: 128-bit block ciphers use GCM and the ChaCha20 stream cipher uses ChaCha20-Poly1305. Only
 * AEAD-capable symmetric algorithms are supported (AES, ARIA, CAMELLIA, CAST6, DSTU7624, GOST3412-2015, SEED, SM4 and
 * CHACHA7539); configuring any other algorithm fails fast.
 * </p>
 *
 * <p>
 * The encrypted message format is:
 * </p>
 *
 * <pre>
 * [4 bytes: encapsulation length] [N bytes: encapsulation] [12 bytes: AEAD nonce] [M bytes: ciphertext + auth tag]
 * </pre>
 *
 * <p>
 * Example usage:
 * </p>
 *
 * <pre>
 * PQCDataFormat pqcFormat = new PQCDataFormat();
 * pqcFormat.setKeyEncapsulationAlgorithm("MLKEM");
 * pqcFormat.setSymmetricKeyAlgorithm("AES");
 * pqcFormat.setKeyPair(keyPair);
 *
 * from("direct:encrypt")
 *         .marshal(pqcFormat)
 *         .to("file:encrypted");
 *
 * from("file:encrypted")
 *         .unmarshal(pqcFormat)
 *         .to("direct:decrypted");
 * </pre>
 */
@Dataformat("pqc")
public class PQCDataFormat extends ServiceSupport implements DataFormat, DataFormatName {

    public static final String KEY_PAIR = "CamelPQCKeyPair";
    public static final String KEM_ALGORITHM = "CamelPQCKemAlgorithm";
    public static final String SYMMETRIC_ALGORITHM = "CamelPQCSymmetricAlgorithm";

    private static final Logger LOG = LoggerFactory.getLogger(PQCDataFormat.class);

    /** Authentication tag length, in bits, used for GCM. */
    private static final int GCM_TAG_LENGTH_BITS = 128;
    /** Nonce length, in bytes, used for both GCM and ChaCha20-Poly1305. */
    private static final int NONCE_LENGTH_BYTES = 12;
    /** Upper bound for the encapsulation length read from untrusted input, to guard against malformed data. */
    private static final int MAX_ENCAPSULATION_LENGTH = 1024 * 1024;

    private static final String CHACHA_ALGORITHM = "CHACHA7539";
    private static final String CHACHA_POLY1305_TRANSFORMATION = "CHACHA20-POLY1305";
    private static final String CHACHA_KEY_ALGORITHM = "ChaCha20";

    /**
     * JCE names of the 128-bit block ciphers that provide a GCM AEAD mode and can therefore be used as the symmetric
     * (data-encapsulation) layer. The ChaCha20 stream cipher is handled separately via ChaCha20-Poly1305.
     */
    private static final Set<String> GCM_CAPABLE_ALGORITHMS
            = Set.of("AES", "ARIA", "CAMELLIA", "CAST6", "DSTU7624", "GOST3412-2015", "SEED", "SM4");

    private String keyEncapsulationAlgorithm = "MLKEM";
    private String symmetricKeyAlgorithm = "AES";
    private int symmetricKeyLength = 128;
    private KeyPair keyPair;
    private String provider;
    private KeyGenerator keyGenerator;

    public PQCDataFormat() {
    }

    public PQCDataFormat(String keyEncapsulationAlgorithm, String symmetricKeyAlgorithm, KeyPair keyPair) {
        this.keyEncapsulationAlgorithm = keyEncapsulationAlgorithm;
        this.symmetricKeyAlgorithm = symmetricKeyAlgorithm;
        this.keyPair = keyPair;
    }

    @Override
    public String getDataFormatName() {
        return "pqc";
    }

    @Override
    public void marshal(Exchange exchange, Object graph, OutputStream outputStream) throws Exception {
        KeyPair kp = getKeyPair(exchange);
        if (kp == null || kp.getPublic() == null) {
            throw new IllegalStateException(
                    "A valid KeyPair with public key is required for encryption. " +
                                            "Either configure the PQCDataFormat with a KeyPair or provide one in a header using '"
                                            + KEY_PAIR + "'");
        }

        String kemAlg = getKemAlgorithm(exchange);
        String symAlg = getSymmetricAlgorithm(exchange);
        checkAeadSupported(symAlg);

        byte[] plaintext = ExchangeHelper.convertToMandatoryType(exchange, byte[].class, graph);

        try {
            SecureRandom random = new SecureRandom();

            // Generate KEM encapsulation and the fresh shared secret
            KeyGenerator kg = getOrCreateKeyGenerator(kemAlg);
            kg.init(new KEMGenerateSpec(kp.getPublic(), symAlg, symmetricKeyLength), random);
            SecretKeyWithEncapsulation secretKey = (SecretKeyWithEncapsulation) kg.generateKey();
            byte[] encapsulation = secretKey.getEncapsulation();

            // Encrypt the payload with an authenticated cipher using a fresh random nonce
            byte[] nonce = new byte[NONCE_LENGTH_BYTES];
            random.nextBytes(nonce);
            Cipher cipher = initAeadCipher(Cipher.ENCRYPT_MODE, symAlg, secretKey.getEncoded(), nonce);
            byte[] ciphertext = cipher.doFinal(plaintext);

            // Write encapsulation length, encapsulation, nonce and the authenticated ciphertext
            DataOutputStream dataOut = new DataOutputStream(outputStream);
            dataOut.writeInt(encapsulation.length);
            dataOut.write(encapsulation);
            dataOut.write(nonce);
            dataOut.write(ciphertext);
            dataOut.flush();
        } catch (Exception e) {
            throw new IOException("Failed to encrypt data using PQC KEM", e);
        }
    }

    @Override
    public Object unmarshal(Exchange exchange, InputStream encryptedStream) throws Exception {
        if (encryptedStream == null) {
            return null;
        }

        KeyPair kp = getKeyPair(exchange);
        if (kp == null || kp.getPrivate() == null) {
            throw new IllegalStateException(
                    "A valid KeyPair with private key is required for decryption. " +
                                            "Either configure the PQCDataFormat with a KeyPair or provide one in a header using '"
                                            + KEY_PAIR + "'");
        }

        String kemAlg = getKemAlgorithm(exchange);
        String symAlg = getSymmetricAlgorithm(exchange);
        checkAeadSupported(symAlg);

        try (DataInputStream dataIn = new DataInputStream(encryptedStream)) {
            // Read encapsulation
            int encapsulationLength = dataIn.readInt();
            if (encapsulationLength < 0 || encapsulationLength > MAX_ENCAPSULATION_LENGTH) {
                throw new IOException("Invalid PQC encapsulation length: " + encapsulationLength);
            }
            byte[] encapsulation = new byte[encapsulationLength];
            dataIn.readFully(encapsulation);

            // Read the AEAD nonce
            byte[] nonce = new byte[NONCE_LENGTH_BYTES];
            dataIn.readFully(nonce);

            // The remainder is the authenticated ciphertext (including the tag)
            byte[] ciphertext = dataIn.readAllBytes();

            // Extract the shared secret from the encapsulation
            KeyGenerator kg = getOrCreateKeyGenerator(kemAlg);
            kg.init(new KEMExtractSpec(kp.getPrivate(), encapsulation, symAlg, symmetricKeyLength), new SecureRandom());
            SecretKeyWithEncapsulation secretKey = (SecretKeyWithEncapsulation) kg.generateKey();

            // Decrypt. doFinal() verifies the authentication tag and throws on tampering; this must not be done with
            // CipherInputStream, which can silently swallow an AEAD tag failure and return truncated plaintext.
            Cipher cipher = initAeadCipher(Cipher.DECRYPT_MODE, symAlg, secretKey.getEncoded(), nonce);
            return cipher.doFinal(ciphertext);
        } catch (AEADBadTagException e) {
            throw new IOException(
                    "PQC decryption failed: authentication tag mismatch. The encrypted data may have been tampered with, "
                                  + "or the wrong key or algorithm was used.",
                    e);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to decrypt data using PQC KEM", e);
        }
    }

    @Override
    protected void doStart() throws Exception {
        // Fail fast if the configured symmetric algorithm cannot provide authenticated encryption. Per-message
        // overrides via the CamelPQCSymmetricAlgorithm header are validated at marshal/unmarshal time.
        if (symmetricKeyAlgorithm != null) {
            checkAeadSupported(toJceAlgorithm(symmetricKeyAlgorithm));
        }

        if (keyEncapsulationAlgorithm != null && keyGenerator == null) {
            PQCKeyEncapsulationAlgorithms kemEnum = PQCKeyEncapsulationAlgorithms.valueOf(keyEncapsulationAlgorithm);
            String algorithm = kemEnum.getAlgorithm();
            String bcProvider = kemEnum.getBcProvider();

            if (provider != null) {
                keyGenerator = KeyGenerator.getInstance(algorithm, provider);
            } else if (bcProvider != null) {
                keyGenerator = KeyGenerator.getInstance(algorithm, bcProvider);
            } else {
                keyGenerator = KeyGenerator.getInstance(algorithm);
            }
        }
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }

    /**
     * Builds and initialises an AEAD {@link Cipher} for the given symmetric algorithm. 128-bit block ciphers use GCM;
     * ChaCha20 uses ChaCha20-Poly1305. Both use a 12-byte nonce.
     */
    private Cipher initAeadCipher(int mode, String jceAlgorithm, byte[] keyBytes, byte[] nonce) throws Exception {
        boolean chacha = CHACHA_ALGORITHM.equalsIgnoreCase(jceAlgorithm);
        String transformation = chacha ? CHACHA_POLY1305_TRANSFORMATION : jceAlgorithm + "/GCM/NoPadding";
        SecretKey key = new SecretKeySpec(keyBytes, chacha ? CHACHA_KEY_ALGORITHM : jceAlgorithm);
        AlgorithmParameterSpec spec
                = chacha ? new IvParameterSpec(nonce) : new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce);

        String cipherProvider = provider != null ? provider : BouncyCastleProvider.PROVIDER_NAME;
        LOG.debug("Using AEAD transformation {} (provider {})", transformation, cipherProvider);

        Cipher cipher = Cipher.getInstance(transformation, cipherProvider);
        cipher.init(mode, key, spec);
        return cipher;
    }

    /**
     * Verifies that the given (JCE) symmetric algorithm supports authenticated encryption (AEAD). PQCDataFormat only
     * supports AEAD ciphers so that integrity is always protected.
     */
    private static void checkAeadSupported(String jceAlgorithm) {
        if (CHACHA_ALGORITHM.equalsIgnoreCase(jceAlgorithm) || GCM_CAPABLE_ALGORITHMS.contains(jceAlgorithm)) {
            return;
        }
        throw new IllegalArgumentException(
                "Symmetric algorithm '" + jceAlgorithm
                                           + "' does not support authenticated encryption (AEAD) and cannot be used with PQCDataFormat, "
                                           + "which requires an authenticated cipher to protect both confidentiality and integrity. "
                                           + "Use one of the AEAD-capable algorithms: AES, ARIA, CAMELLIA, CAST6, DSTU7624, GOST3412-2015, "
                                           + "SEED, SM4 (GCM) or CHACHA7539 (ChaCha20-Poly1305).");
    }

    private KeyPair getKeyPair(Exchange exchange) {
        KeyPair kp = exchange.getIn().getHeader(KEY_PAIR, KeyPair.class);
        if (kp != null) {
            return kp;
        }
        if (this.keyPair != null) {
            return this.keyPair;
        }
        // Try to lookup from registry
        return exchange.getContext().getRegistry().lookupByNameAndType("pqcKeyPair", KeyPair.class);
    }

    private String getKemAlgorithm(Exchange exchange) {
        String alg = exchange.getIn().getHeader(KEM_ALGORITHM, String.class);
        return alg != null ? alg : this.keyEncapsulationAlgorithm;
    }

    private String getSymmetricAlgorithm(Exchange exchange) {
        String alg = exchange.getIn().getHeader(SYMMETRIC_ALGORITHM, String.class);
        if (alg != null) {
            return toJceAlgorithm(alg);
        }
        return toJceAlgorithm(this.symmetricKeyAlgorithm);
    }

    /**
     * Maps a {@link PQCSymmetricAlgorithms} enum name to its JCE algorithm name, returning the input unchanged when it
     * is not an enum constant (e.g. it is already a JCE name).
     */
    private static String toJceAlgorithm(String algorithm) {
        try {
            return PQCSymmetricAlgorithms.valueOf(algorithm).getAlgorithm();
        } catch (IllegalArgumentException e) {
            return algorithm;
        }
    }

    private KeyGenerator getOrCreateKeyGenerator(String kemAlgorithm) throws Exception {
        if (keyGenerator != null) {
            return keyGenerator;
        }

        // Create a temporary key generator for this operation
        PQCKeyEncapsulationAlgorithms kemEnum = PQCKeyEncapsulationAlgorithms.valueOf(kemAlgorithm);
        String algorithm = kemEnum.getAlgorithm();
        String bcProvider = kemEnum.getBcProvider();

        if (provider != null) {
            return KeyGenerator.getInstance(algorithm, provider);
        } else if (bcProvider != null) {
            return KeyGenerator.getInstance(algorithm, bcProvider);
        } else {
            return KeyGenerator.getInstance(algorithm);
        }
    }

    // Getters and Setters

    public String getKeyEncapsulationAlgorithm() {
        return keyEncapsulationAlgorithm;
    }

    /**
     * Sets the Post-Quantum KEM algorithm to use for key encapsulation. Supported values: MLKEM, BIKE, HQC, CMCE,
     * SABER, FRODO, NTRU, NTRULPRime, SNTRUPrime, KYBER
     */
    public void setKeyEncapsulationAlgorithm(String keyEncapsulationAlgorithm) {
        this.keyEncapsulationAlgorithm = keyEncapsulationAlgorithm;
    }

    public String getSymmetricKeyAlgorithm() {
        return symmetricKeyAlgorithm;
    }

    /**
     * Sets the symmetric encryption algorithm to use with the shared secret. Only algorithms that support authenticated
     * encryption (AEAD) are allowed: AES, ARIA, CAMELLIA, CAST6, DSTU7624, GOST3412-2015, SEED, SM4 (encrypted with
     * GCM) and CHACHA7539 (encrypted with ChaCha20-Poly1305). The default is AES.
     */
    public void setSymmetricKeyAlgorithm(String symmetricKeyAlgorithm) {
        this.symmetricKeyAlgorithm = symmetricKeyAlgorithm;
    }

    public int getSymmetricKeyLength() {
        return symmetricKeyLength;
    }

    /**
     * Sets the length (in bits) of the symmetric key. Default is 128.
     */
    public void setSymmetricKeyLength(int symmetricKeyLength) {
        this.symmetricKeyLength = symmetricKeyLength;
    }

    public KeyPair getKeyPair() {
        return keyPair;
    }

    /**
     * Sets the KeyPair to use for KEM operations. The public key is used for encryption (marshal), the private key for
     * decryption (unmarshal).
     */
    public void setKeyPair(KeyPair keyPair) {
        this.keyPair = keyPair;
    }

    public String getProvider() {
        return provider;
    }

    /**
     * Sets the JCE security provider to use. If not set, will use the provider from the KEM algorithm enum.
     */
    public void setProvider(String provider) {
        this.provider = provider;
    }

    public KeyGenerator getKeyGenerator() {
        return keyGenerator;
    }

    /**
     * Sets a custom KeyGenerator for KEM operations. If not set, one will be created based on the KEM algorithm.
     */
    public void setKeyGenerator(KeyGenerator keyGenerator) {
        this.keyGenerator = keyGenerator;
    }
}
