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
package org.apache.camel.converter.crypto;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.Key;
import java.security.spec.AlgorithmParameterSpec;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.IvParameterSpec;

import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.spi.annotations.Dataformat;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.builder.OutputStreamBuilder;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.IOHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static javax.crypto.Cipher.DECRYPT_MODE;
import static javax.crypto.Cipher.ENCRYPT_MODE;

/**
 * <code>CryptoDataFormat</code> uses a specified key and algorithm to encrypt,
 * decrypt and verify exchange payloads. The Data format allows an
 * initialization vector to be supplied. The use of this initialization vector
 * or IV is different depending on the algorithm type block or streaming, but it
 * is desirable to be able to control it. Also in certain cases it may be
 * necessary to have access to the IV in the decryption phase and as the IV
 * doens't necessarily need to be kept secret it is ok to inline this in the
 * stream and read it out on the other side prior to decryption. For more
 * information on Initialization vectors see
 * <ul>
 * <li>http://en.wikipedia.org/wiki/Initialization_vector</li>
 * <li>http://www.herongyang.com/Cryptography/</li>
 * <li>http://en.wikipedia.org/wiki/Block_cipher_modes_of_operation</li>
 * <ul>
 * <p/>
 * To avoid attacks against the encrypted data while it is in transit the
 * {@link CryptoDataFormat} can also calculate a Message Authentication Code for
 * the encrypted exchange contents based on a configurable MAC algorithm. The
 * calculated HMAC is appended to the stream after encryption. It is separated
 * from the stream in the decryption phase. The MAC is recalculated and verified
 * against the transmitted version to insure nothing was tampered with in
 * transit.For more information on Message Authentication Codes see
 * <ul>
 * <li>http://en.wikipedia.org/wiki/HMAC</li>
 * </ul>
 */
@Dataformat("crypto")
public class CryptoDataFormat extends ServiceSupport implements DataFormat, DataFormatName {

    public static final String KEY = "CamelCryptoKey";

    private static final Logger LOG = LoggerFactory.getLogger(CryptoDataFormat.class);
    private static final String INIT_VECTOR = "CamelCryptoInitVector";
    private String algorithm;
    private String cryptoProvider;
    private Key configuredkey;
    private int bufferSize = 4096;
    private byte[] initializationVector;
    private boolean inline;
    private String macAlgorithm = "HmacSHA1";
    private boolean shouldAppendHMAC = true;
    private AlgorithmParameterSpec parameterSpec;

    public CryptoDataFormat() {
    }

    public CryptoDataFormat(String algorithm, Key key) {
        this(algorithm, key, null);
    }

    public CryptoDataFormat(String algorithm, Key key, String cryptoProvider) {
        this.algorithm = algorithm;
        this.configuredkey = key;
        this.cryptoProvider = cryptoProvider;
    }

    @Override
    public String getDataFormatName() {
        return "crypto";
    }

    private Cipher initializeCipher(int mode, Key key, byte[] iv) throws Exception {
        Cipher cipher = cryptoProvider == null ? Cipher.getInstance(algorithm) : Cipher.getInstance(algorithm, cryptoProvider);

        if (key == null) {
            throw new IllegalStateException("A valid encryption key is required. Either configure the CryptoDataFormat "
                    + "with a key or provide one in a header using the header name 'CamelCryptoKey'");
        }

        if (mode == ENCRYPT_MODE || mode == DECRYPT_MODE) {
            if (iv != null) {
                cipher.init(mode, key, new IvParameterSpec(iv));
            } else if (parameterSpec != null) {
                cipher.init(mode, key, parameterSpec);
            } else {
                cipher.init(mode, key);
            }
        }
        return cipher;
    }

    @Override
    public void marshal(Exchange exchange, Object graph, OutputStream outputStream) throws Exception {
        byte[] iv = getInitializationVector(exchange);
        Key key = getKey(exchange);

        InputStream plaintextStream = ExchangeHelper.convertToMandatoryType(exchange, InputStream.class, graph);
        HMACAccumulator hmac = getMessageAuthenticationCode(key);
        if (plaintextStream != null) {
            inlineInitVector(outputStream, iv);
            byte[] buffer = new byte[bufferSize];
            int read;
            CipherOutputStream cipherStream = null;
            try {
                cipherStream = new CipherOutputStream(outputStream, initializeCipher(ENCRYPT_MODE, key, iv));
                while ((read = plaintextStream.read(buffer)) > 0) {
                    cipherStream.write(buffer, 0, read);
                    cipherStream.flush();
                    hmac.encryptUpdate(buffer, read);
                }
                // only write if there is data to write (IBM JDK throws exception if no data)
                byte[] mac = hmac.getCalculatedMac();
                if (mac != null && mac.length > 0) {
                    cipherStream.write(mac);
                }
            } finally {
                IOHelper.close(cipherStream, "cipher", LOG);
                IOHelper.close(plaintextStream, "plaintext", LOG);
            }
        }
    }

    @Override
    public Object unmarshal(final Exchange exchange, final InputStream encryptedStream) throws Exception {
        if (encryptedStream != null) {
            byte[] iv = getInlinedInitializationVector(exchange, encryptedStream);
            Key key = getKey(exchange);
            CipherInputStream cipherStream = null;
            OutputStreamBuilder osb = null;
            try {
                cipherStream = new CipherInputStream(encryptedStream, initializeCipher(DECRYPT_MODE, key, iv));
                osb = OutputStreamBuilder.withExchange(exchange);
                HMACAccumulator hmac = getMessageAuthenticationCode(key);
                byte[] buffer = new byte[bufferSize];
                hmac.attachStream(osb);
                int read;
                while ((read = cipherStream.read(buffer)) >= 0) {
                    hmac.decryptUpdate(buffer, read);
                }
                hmac.validate();
                return osb.build();
            } finally {
                IOHelper.close(cipherStream, "cipher", LOG);
                IOHelper.close(osb, "plaintext", LOG);
            }
        }
        return null;
    }

    @Override
    protected void doStart() throws Exception {
        // noop
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }

    private void inlineInitVector(OutputStream outputStream, byte[] iv) throws IOException {
        if (inline) {
            if (iv == null) {
                throw new IllegalStateException("Inlining cannot be performed, as no initialization vector was specified");
            }

            DataOutputStream dout = new DataOutputStream(outputStream);
            dout.writeInt(iv.length);
            outputStream.write(iv);
            outputStream.flush();
        }
    }

    private byte[] getInlinedInitializationVector(Exchange exchange, InputStream encryptedStream) throws IOException {
        byte[] iv = getInitializationVector(exchange);
        if (inline) {
            try {
                int ivLength = new DataInputStream(encryptedStream).readInt();
                iv = new byte[ivLength];
                int read = encryptedStream.read(iv);
                if (read != ivLength) {
                    throw new IOException(String.format("Attempted to read a '%d' byte initialization vector from inputStream but only"
                            + " '%d' bytes were retrieved", ivLength, read));
                }
            } catch (IOException e) {
                throw new IOException("Error reading initialization vector from encrypted stream", e);
            }
        }
        return iv;
    }

    private HMACAccumulator getMessageAuthenticationCode(Key key) throws Exception {
        // return an actual Hmac Calculator or a 'Null' noop version.
        return shouldAppendHMAC ? new HMACAccumulator(key, macAlgorithm, cryptoProvider, bufferSize) : new HMACAccumulator() {
            byte[] empty = new byte[0];

            public void encryptUpdate(byte[] buffer, int read) {
            }

            public void decryptUpdate(byte[] buffer, int read) throws IOException {
                outputStream.write(buffer, 0, read);
            }

            public void validate() {
            }

            public byte[] getCalculatedMac() {
                return empty;
            }
        };
    }

    private byte[] getInitializationVector(Exchange exchange) {
        byte[] iv = exchange.getIn().getHeader(INIT_VECTOR, byte[].class);
        if (iv == null) {
            iv = initializationVector;
        }
        return iv;
    }

    private Key getKey(Exchange exchange) {
        Key key = exchange.getIn().getHeader(KEY, Key.class);
        if (key != null) {
            exchange.getIn().setHeader(KEY, null);
        } else {
            key = configuredkey;
        }
        return key;
    }

    public void setInitializationVector(byte[] initializationVector) {
        if (initializationVector != null) {
            this.initializationVector = initializationVector;
        }
    }

    /**
     * Meant for use with a Symmetric block Cipher and specifies that the
     * initialization vector should be written to the cipher stream ahead of the
     * encrypted ciphertext. When the payload is to be decrypted this
     * initialization vector will need to be read from the stream. Requires that
     * the formatter has been configured with an init vector that is valid for
     * the given algorithm.
     *
     * @param inline true if the initialization vector should be inlined in the stream.
     */
    public void setShouldInlineInitializationVector(boolean inline) {
        this.inline = inline;
    }

    /**
     * Sets the JCE name of the Encryption Algorithm that should be used
     */
    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    /**
     * Sets a custom {@link AlgorithmParameterSpec} that should be used to
     * configure the Cipher. Note that if an Initalization vector is provided
     * then the IvParameterSpec will be used and any value set here will be
     * ignored
     */
    public void setAlgorithmParameterSpec(AlgorithmParameterSpec parameterSpec) {
        this.parameterSpec = parameterSpec;
    }

    /**
     * Sets the name of the JCE provider e.g. SUN or BC for Bouncy
     */
    public void setCryptoProvider(String cryptoProvider) {
        this.cryptoProvider = cryptoProvider;
    }

    /**
     * Sets the algorithm used to create the Hash-based Message Authentication
     * Code (HMAC) appended to the stream.
     */
    public void setMacAlgorithm(String macAlgorithm) {
        this.macAlgorithm = macAlgorithm;
    }

    /**
     * Whether a Hash-based Message Authentication Code (HMAC) should be
     * calculated and appended to the stream.
     */
    public void setShouldAppendHMAC(boolean shouldAppendHMAC) {
        this.shouldAppendHMAC = shouldAppendHMAC;
    }

    /**
     * Set the key that should be used to encrypt or decrypt incoming encrypted exchanges.
     */
    public void setKey(Key key) {
        this.configuredkey = key;
    }

    /**
     * Set the size of the buffer used to
     */
    public void setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize;
    }
}
