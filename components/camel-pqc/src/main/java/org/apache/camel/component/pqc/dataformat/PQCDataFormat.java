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

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.KeyGenerator;

import org.apache.camel.Exchange;
import org.apache.camel.component.pqc.PQCKeyEncapsulationAlgorithms;
import org.apache.camel.component.pqc.PQCSymmetricAlgorithms;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.spi.annotations.Dataformat;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.builder.OutputStreamBuilder;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.IOHelper;
import org.bouncycastle.jcajce.SecretKeyWithEncapsulation;
import org.bouncycastle.jcajce.spec.KEMExtractSpec;
import org.bouncycastle.jcajce.spec.KEMGenerateSpec;
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
 * <li>Using KEM algorithms (ML-KEM, BIKE, etc.) to establish a shared secret</li>
 * <li>Encrypting data with symmetric algorithms (AES, Camellia, etc.) using the shared secret</li>
 * <li>Including the encapsulated key in the output stream for decryption</li>
 * </ul>
 *
 * <p>
 * The encrypted message format is:
 * </p>
 *
 * <pre>
 * [4 bytes: encapsulation length] [N bytes: encapsulation] [M bytes: encrypted data]
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

    private String keyEncapsulationAlgorithm = "MLKEM";
    private String symmetricKeyAlgorithm = "AES";
    private int symmetricKeyLength = 128;
    private KeyPair keyPair;
    private int bufferSize = 4096;
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

        InputStream plaintextStream = ExchangeHelper.convertToMandatoryType(exchange, InputStream.class, graph);

        try {
            // Generate KEM encapsulation and shared secret
            KeyGenerator kg = getOrCreateKeyGenerator(kemAlg);
            kg.init(
                    new KEMGenerateSpec(kp.getPublic(), symAlg, symmetricKeyLength),
                    new SecureRandom());

            SecretKeyWithEncapsulation secretKey = (SecretKeyWithEncapsulation) kg.generateKey();
            byte[] encapsulation = secretKey.getEncapsulation();

            // Write encapsulation length and data
            DataOutputStream dataOut = new DataOutputStream(outputStream);
            dataOut.writeInt(encapsulation.length);
            outputStream.write(encapsulation);
            outputStream.flush();

            // Encrypt data with the shared secret
            Cipher cipher = Cipher.getInstance(symAlg);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);

            byte[] buffer = new byte[bufferSize];
            int read;
            CipherOutputStream cipherStream = null;
            try {
                cipherStream = new CipherOutputStream(outputStream, cipher);
                while ((read = plaintextStream.read(buffer)) > 0) {
                    cipherStream.write(buffer, 0, read);
                    cipherStream.flush();
                }
            } finally {
                IOHelper.close(cipherStream, "cipher", LOG);
                IOHelper.close(plaintextStream, "plaintext", LOG);
            }
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

        CipherInputStream cipherStream = null;
        OutputStreamBuilder osb = null;

        try {
            // Read encapsulation from stream
            DataInputStream dataIn = new DataInputStream(encryptedStream);
            int encapsulationLength = dataIn.readInt();
            byte[] encapsulation = new byte[encapsulationLength];
            int read = encryptedStream.read(encapsulation);
            if (read != encapsulationLength) {
                throw new IOException(
                        String.format("Expected to read %d bytes of encapsulation but got %d bytes",
                                encapsulationLength, read));
            }

            // Extract secret key from encapsulation
            KeyGenerator kg = getOrCreateKeyGenerator(kemAlg);
            kg.init(
                    new KEMExtractSpec(kp.getPrivate(), encapsulation, symAlg, symmetricKeyLength),
                    new SecureRandom());

            SecretKeyWithEncapsulation secretKey = (SecretKeyWithEncapsulation) kg.generateKey();

            // Decrypt data with the extracted secret
            Cipher cipher = Cipher.getInstance(symAlg);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);

            cipherStream = new CipherInputStream(encryptedStream, cipher);
            osb = OutputStreamBuilder.withExchange(exchange);
            byte[] buffer = new byte[bufferSize];
            while ((read = cipherStream.read(buffer)) >= 0) {
                osb.write(buffer, 0, read);
            }

            return osb.build();
        } catch (Exception e) {
            throw new IOException("Failed to decrypt data using PQC KEM", e);
        } finally {
            IOHelper.close(cipherStream, "cipher", LOG);
            IOHelper.close(osb, "plaintext", LOG);
        }
    }

    @Override
    protected void doStart() throws Exception {
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
            return alg;
        }

        // Convert to BC algorithm name if it's an enum value
        try {
            PQCSymmetricAlgorithms symEnum = PQCSymmetricAlgorithms.valueOf(this.symmetricKeyAlgorithm);
            return symEnum.getAlgorithm();
        } catch (IllegalArgumentException e) {
            // Not an enum, return as-is
            return this.symmetricKeyAlgorithm;
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
     * Sets the symmetric encryption algorithm to use with the shared secret. Supported values: AES, ARIA, RC2, RC5,
     * CAMELLIA, CAST5, CAST6, CHACHA7539, etc.
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

    public int getBufferSize() {
        return bufferSize;
    }

    /**
     * Sets the buffer size for streaming encryption/decryption. Default is 4096 bytes.
     */
    public void setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize;
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
