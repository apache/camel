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
import java.io.PushbackInputStream;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.PublicKey;
import java.security.Security;
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
import org.apache.camel.component.pqc.BouncyCastleKemSupport;
import org.apache.camel.component.pqc.BouncyCastleSupport;
import org.apache.camel.component.pqc.PQCAlgorithmId;
import org.apache.camel.component.pqc.PQCKemLocks;
import org.apache.camel.component.pqc.PQCKeyEncapsulationAlgorithms;
import org.apache.camel.component.pqc.PQCSymmetricAlgorithms;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.spi.annotations.Dataformat;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.SecureRandomHelper;
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
 * Wire format v2 (written by this implementation):
 * </p>
 *
 * <pre>
 * [8 bytes: header] [4 bytes: encap len] [N bytes: encapsulation] [12 bytes: AEAD nonce] [M bytes: ciphertext + auth tag]
 *
 * Header layout (8 bytes):
 *   byte 0: magic 0x50 ('P')
 *   byte 1: magic 0x51 ('Q')
 *   byte 2: version  0x02
 *   byte 3: format   0x03 (KEM data encryption)
 *   byte 4-5: 16-bit KEM algorithm ID (big-endian, from {@link PQCAlgorithmId})
 *   byte 6-7: 16-bit symmetric algorithm ID (big-endian, from {@link PQCAlgorithmId})
 * </pre>
 *
 * <p>
 * Wire format v1 (legacy, read-only backward compatibility):
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
@BodyTypes(marshalIn = @BodyType(value = { Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE },
                                 description = "The data to encrypt, read whole into memory: text in the charset of the"
                                               + " exchange, bytes, a stream or a file"),
           marshalOut = @BodyType(value = Carrier.STREAM, format = "binary",
                                  description = "The encrypted message in the stream cache, or a byte array when stream caching"
                                                + " is off"),
           unmarshalIn = @BodyType(value = { Carrier.BYTES, Carrier.STREAM, Carrier.FILE }, format = "binary",
                                   description = "An encrypted message as marshal writes it"),
           unmarshalOut = @BodyType(value = Carrier.BYTES, javaType = "byte[]",
                                    description = "The decrypted data, as a byte array"))
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

    // Wire format v2 header constants
    /** Magic byte 0: 'P' (0x50) — identifies a PQC wire format v2 message. */
    static final byte MAGIC_BYTE_0 = 0x50;
    /** Magic byte 1: 'Q' (0x51) — identifies a PQC wire format v2 message. */
    static final byte MAGIC_BYTE_1 = 0x51;
    /** Wire format version: 0x02 (shared across all PQC v2 wire formats). */
    static final byte WIRE_VERSION = 0x02;
    /** Format type: KEM data encryption (0x03). */
    static final byte FORMAT_TYPE_KEM = 0x03;
    /** Total byte length of the v2 header. */
    static final int HEADER_LENGTH = 8;

    /**
     * JCE names of the 128-bit block ciphers that provide a GCM AEAD mode and can therefore be used as the symmetric
     * (data-encapsulation) layer. The ChaCha20 stream cipher is handled separately via ChaCha20-Poly1305.
     */
    private static final Set<String> GCM_CAPABLE_ALGORITHMS
            = Set.of("AES", "ARIA", "CAMELLIA", "CAST6", "DSTU7624", "GOST3412-2015", "SEED", "SM4");

    private String keyEncapsulationAlgorithm = "MLKEM";
    private String symmetricKeyAlgorithm = "AES";
    private int symmetricKeyLength = 256;
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
            // Generate KEM encapsulation and the fresh shared secret
            BouncyCastleKemSupport.EncapsulationResult encapsulated = encapsulateKem(
                    kemAlg, kp.getPublic(), symAlg);
            byte[] encapsulation = encapsulated.encapsulation();

            // Compute algorithm IDs for the header
            int kemAlgId = PQCAlgorithmId.fromJcaName(kemAlg).getId();
            if (kemAlgId == PQCAlgorithmId.UNKNOWN.getId()) {
                // Try looking up by enum name (e.g. "MLKEM" enum → "ML-KEM" JCA name)
                kemAlgId = lookupKemAlgId(kemAlg);
            }
            int symAlgId = PQCAlgorithmId.fromJcaName(symAlg).getId();

            // Build the 8-byte header as a byte array for both writing and AAD binding
            byte[] header = new byte[HEADER_LENGTH];
            header[0] = MAGIC_BYTE_0;
            header[1] = MAGIC_BYTE_1;
            header[2] = WIRE_VERSION;
            header[3] = FORMAT_TYPE_KEM;
            header[4] = (byte) (kemAlgId >> 8);
            header[5] = (byte) (kemAlgId & 0xFF);
            header[6] = (byte) (symAlgId >> 8);
            header[7] = (byte) (symAlgId & 0xFF);

            // Encrypt the payload with an authenticated cipher using a fresh random nonce.
            // The header is bound as AAD so that any modification to the header is detected
            // by the AEAD tag verification on decryption.
            byte[] nonce = new byte[NONCE_LENGTH_BYTES];
            SecureRandomHelper.getSecureRandom().nextBytes(nonce);
            Cipher cipher = initAeadCipher(Cipher.ENCRYPT_MODE, symAlg, encapsulated.secretKey().getEncoded(), nonce);
            cipher.updateAAD(header);
            byte[] ciphertext = cipher.doFinal(plaintext);

            // Write header, encapsulation, nonce and the authenticated ciphertext
            DataOutputStream dataOut = new DataOutputStream(outputStream);
            dataOut.write(header);
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

        try {
            // Wrap in PushbackInputStream so we can peek at the first two bytes without consuming them.
            // v2 starts with magic bytes 0x50 0x51 ('P' 'Q').
            // v1 starts with a 4-byte big-endian encapsulation length, which cannot be 0x5051xxxx
            // for any realistic KEM (that would require a >1.3 GB encapsulation).
            PushbackInputStream pbIn = new PushbackInputStream(encryptedStream, 2);
            byte[] peek = pbIn.readNBytes(2);
            if (peek.length < 2) {
                throw new IOException("PQC encrypted stream too short");
            }
            pbIn.unread(peek);

            DataInputStream dataIn = new DataInputStream(pbIn);
            if (peek[0] == MAGIC_BYTE_0 && peek[1] == MAGIC_BYTE_1) {
                return unmarshalV2(dataIn, kp, kemAlg, symAlg);
            } else {
                return unmarshalV1(dataIn, kp, kemAlg, symAlg);
            }
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

    /**
     * Decrypts a v2-format message (8-byte self-describing header).
     * <p>
     * Layout: [2B magic][1B version][1B format][2B KEM alg ID][2B sym alg ID][4B encap len][encap][12B
     * nonce][ciphertext]
     */
    private Object unmarshalV2(DataInputStream dataIn, KeyPair kp, String kemAlg, String symAlg) throws Exception {
        // Read the 8-byte header into a byte array for AAD binding
        byte[] headerBytes = new byte[HEADER_LENGTH];
        dataIn.readFully(headerBytes);

        byte magic0 = headerBytes[0];
        byte magic1 = headerBytes[1];
        byte version = headerBytes[2];
        byte format = headerBytes[3];
        int kemAlgId = ((headerBytes[4] & 0xFF) << 8) | (headerBytes[5] & 0xFF);
        int symAlgId = ((headerBytes[6] & 0xFF) << 8) | (headerBytes[7] & 0xFF);

        LOG.debug("PQC wire format v{} type={} kemAlgId=0x{} symAlgId=0x{}",
                version, format, Integer.toHexString(kemAlgId), Integer.toHexString(symAlgId));

        if (magic0 != MAGIC_BYTE_0 || magic1 != MAGIC_BYTE_1) {
            throw new IOException("Invalid PQC wire format: unexpected magic bytes");
        }
        if (version != WIRE_VERSION) {
            throw new IOException("Unsupported PQC wire format version: " + version);
        }
        if (format != FORMAT_TYPE_KEM) {
            throw new IOException("Unsupported PQC wire format type: " + format);
        }

        // Check that the algorithm IDs in the header match the configured algorithms (if known)
        if (kemAlgId != PQCAlgorithmId.UNKNOWN.getId()) {
            int configuredKemId = PQCAlgorithmId.fromJcaName(kemAlg).getId();
            if (configuredKemId == PQCAlgorithmId.UNKNOWN.getId()) {
                configuredKemId = lookupKemAlgId(kemAlg);
            }
            if (configuredKemId != PQCAlgorithmId.UNKNOWN.getId() && kemAlgId != configuredKemId) {
                PQCAlgorithmId headerAlg = PQCAlgorithmId.fromId(kemAlgId);
                throw new IOException(
                        "PQC data was encrypted with KEM algorithm " + headerAlg.getJcaName()
                                      + " (0x" + Integer.toHexString(kemAlgId)
                                      + ") but the configured algorithm is " + kemAlg);
            }
        }
        if (symAlgId != PQCAlgorithmId.UNKNOWN.getId()) {
            int configuredSymId = PQCAlgorithmId.fromJcaName(symAlg).getId();
            if (configuredSymId != PQCAlgorithmId.UNKNOWN.getId() && symAlgId != configuredSymId) {
                PQCAlgorithmId headerAlg = PQCAlgorithmId.fromId(symAlgId);
                throw new IOException(
                        "PQC data was encrypted with symmetric algorithm " + headerAlg.getJcaName()
                                      + " (0x" + Integer.toHexString(symAlgId)
                                      + ") but the configured algorithm is " + symAlg);
            }
        }

        return decryptPayload(dataIn, kp, kemAlg, symAlg, headerBytes);
    }

    /**
     * Decrypts a v1-format message (legacy 4-byte length prefix, no self-describing header).
     * <p>
     * Layout: [4B encap len][encap][12B nonce][ciphertext]
     */
    private Object unmarshalV1(DataInputStream dataIn, KeyPair kp, String kemAlg, String symAlg) throws Exception {
        LOG.debug("PQC wire format v1 (legacy) detected, decrypting with algorithm {} / {}", kemAlg, symAlg);
        return decryptPayload(dataIn, kp, kemAlg, symAlg, null);
    }

    /**
     * Common decryption path shared by both v1 and v2: reads [4B encap len][encap][12B nonce][ciphertext].
     *
     * @param headerBytes the 8-byte v2 header to bind as AAD (null for v1, which has no AAD)
     */
    private Object decryptPayload(
            DataInputStream dataIn, KeyPair kp, String kemAlg, String symAlg, byte[] headerBytes)
            throws Exception {
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
        SecretKey secretKey = decapsulateKem(kemAlg, kp.getPrivate(), encapsulation, symAlg);

        // Decrypt. doFinal() verifies the authentication tag and throws on tampering; this must not be done with
        // CipherInputStream, which can silently swallow an AEAD tag failure and return truncated plaintext.
        Cipher cipher = initAeadCipher(Cipher.DECRYPT_MODE, symAlg, secretKey.getEncoded(), nonce);
        if (headerBytes != null) {
            cipher.updateAAD(headerBytes);
        }
        return cipher.doFinal(ciphertext);
    }

    @Override
    protected void doStart() throws Exception {
        // Fail fast if the configured symmetric algorithm cannot provide authenticated encryption. Per-message
        // overrides via the CamelPQCSymmetricAlgorithm header are validated at marshal/unmarshal time.
        if (symmetricKeyAlgorithm != null) {
            checkAeadSupported(PQCSymmetricAlgorithms.toJceName(symmetricKeyAlgorithm));
        }

        if ("CMCE".equals(keyEncapsulationAlgorithm)) {
            LOG.warn("The CMCE (Classic McEliece) key encapsulation algorithm is deprecated: it is believed to be unsafe"
                     + " to use and will be removed in a future release. Use MLKEM instead.");
        }

        if (keyEncapsulationAlgorithm != null && keyGenerator == null) {
            // Validate the selected standard KEM or legacy KeyGenerator service without requiring the latter
            // when the provider implements only the Java 21 KEM API.
            resolveKemProvider(keyEncapsulationAlgorithm);
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

        String cipherProvider = provider != null ? provider : BouncyCastleSupport.getProviderName("Cipher", transformation);
        LOG.debug("Using AEAD transformation {} (provider {})", transformation, cipherProvider);

        Cipher cipher = Cipher.getInstance(transformation, cipherProvider);
        cipher.init(mode, key, spec);
        return cipher;
    }

    /**
     * Looks up the wire-format algorithm ID for a KEM algorithm name that may be a configured enum name (e.g. "MLKEM")
     * rather than a JCA name (e.g. "ML-KEM"). Falls back to {@link PQCAlgorithmId#UNKNOWN} if not found.
     */
    private static int lookupKemAlgId(String kemAlg) {
        try {
            PQCKeyEncapsulationAlgorithms enumVal = PQCKeyEncapsulationAlgorithms.valueOf(kemAlg);
            PQCAlgorithmId algId = PQCAlgorithmId.fromJcaName(enumVal.getAlgorithm());
            if (algId != PQCAlgorithmId.UNKNOWN) {
                return algId.getId();
            }
        } catch (IllegalArgumentException ignored) {
            // Not a known enum value; fall through to UNKNOWN
        }
        return PQCAlgorithmId.UNKNOWN.getId();
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
            return PQCSymmetricAlgorithms.toJceName(alg);
        }
        return PQCSymmetricAlgorithms.toJceName(this.symmetricKeyAlgorithm);
    }

    /**
     * Runs a KEM encapsulation. An explicitly supplied KeyGenerator remains supported; otherwise the selected
     * provider's KeyGenerator service is preferred for Bouncy Castle 1.86 interoperability, with the standard Java 21
     * KEM service used only when that provider does not advertise KeyGenerator.
     */
    private BouncyCastleKemSupport.EncapsulationResult encapsulateKem(
            String kemAlgorithm, PublicKey publicKey, String symmetricAlgorithm)
            throws Exception {
        if (keyGenerator != null) {
            synchronized (PQCKemLocks.lockFor(keyGenerator, publicKey)) {
                return BouncyCastleKemSupport.encapsulate(
                        keyGenerator, publicKey, symmetricAlgorithm, symmetricKeyLength, SecureRandomHelper.getSecureRandom());
            }
        }
        PQCKeyEncapsulationAlgorithms algorithm = PQCKeyEncapsulationAlgorithms.valueOf(kemAlgorithm);
        return BouncyCastleKemSupport.encapsulate(
                algorithm.getAlgorithm(), resolveKemProvider(kemAlgorithm), publicKey,
                symmetricAlgorithm, symmetricKeyLength, SecureRandomHelper.getSecureRandom());
    }

    /** Runs a KEM decapsulation, preserving the configured custom KeyGenerator path. */
    private SecretKey decapsulateKem(
            String kemAlgorithm, PrivateKey privateKey, byte[] encapsulation, String symmetricAlgorithm)
            throws Exception {
        if (keyGenerator != null) {
            synchronized (PQCKemLocks.lockFor(keyGenerator, privateKey)) {
                return BouncyCastleKemSupport.decapsulate(
                        keyGenerator, privateKey, encapsulation, symmetricAlgorithm,
                        symmetricKeyLength, SecureRandomHelper.getSecureRandom());
            }
        }
        PQCKeyEncapsulationAlgorithms algorithm = PQCKeyEncapsulationAlgorithms.valueOf(kemAlgorithm);
        return BouncyCastleKemSupport.decapsulate(
                algorithm.getAlgorithm(), resolveKemProvider(kemAlgorithm), privateKey, encapsulation,
                symmetricAlgorithm, symmetricKeyLength, SecureRandomHelper.getSecureRandom());
    }

    /**
     * Resolves the configured provider, preferring KeyGenerator when it is available so BC 1.86 uses the same
     * encapsulation/derivation path as Camel 4.22 and explicitly configured generators. Providers that expose only the
     * standard JDK KEM service remain supported as a fallback.
     */
    private Provider resolveKemProvider(String kemAlgorithm) throws GeneralSecurityException {
        PQCKeyEncapsulationAlgorithms algorithm = PQCKeyEncapsulationAlgorithms.valueOf(kemAlgorithm);
        String jceAlgorithm = algorithm.getAlgorithm();
        if (provider != null) {
            Provider selected = Security.getProvider(provider);
            if (selected == null) {
                throw new NoSuchProviderException("Configured PQC provider '" + provider + "' is not installed");
            }
            if (selected.getService("KEM", jceAlgorithm) == null
                    && selected.getService("KeyGenerator", jceAlgorithm) == null) {
                throw new NoSuchAlgorithmException(
                        "Configured provider '" + provider
                                                   + "' advertises neither KEM nor KeyGenerator for '" + jceAlgorithm + "'");
            }
            return selected;
        }

        try {
            return BouncyCastleSupport.getProvider("KeyGenerator", jceAlgorithm);
        } catch (NoSuchAlgorithmException noKeyGenerator) {
            return BouncyCastleSupport.getProvider("KEM", jceAlgorithm);
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
     * Sets the length (in bits) of the symmetric key. Default is 256. SEED and SM4 only accept 128; GOST3412-2015 and
     * CHACHA7539 only accept 256.
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
     * Sets the JCE security provider to use for both KEM and symmetric-cipher operations. For KEM, the provider may
     * expose the standard Java 21 KEM service or the legacy KeyGenerator service.
     */
    public void setProvider(String provider) {
        this.provider = provider;
    }

    public KeyGenerator getKeyGenerator() {
        return keyGenerator;
    }

    /**
     * Sets a custom KeyGenerator for KEM operations. When configured, this exact generator is always used instead of
     * automatic Java 21 KEM service selection. Its init()/generateKey() sequence is serialized when shared by
     * concurrent exchanges. When no generator is configured, the data format selects the standard KEM service when
     * available and falls back to the provider's legacy KeyGenerator service otherwise.
     */
    public void setKeyGenerator(KeyGenerator keyGenerator) {
        this.keyGenerator = keyGenerator;
    }
}
