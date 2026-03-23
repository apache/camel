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
package org.apache.camel.main;

import java.io.ByteArrayInputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * Generates a self-signed certificate for development use. This allows enabling HTTPS with minimal configuration when
 * no keystore is provided.
 *
 * The generated certificate is NOT suitable for production use.
 */
final class SelfSignedCertificateGenerator {

    private SelfSignedCertificateGenerator() {
    }

    /**
     * Generates a PKCS12 KeyStore containing a self-signed certificate.
     *
     * @param  password  the password for the keystore and key entry
     * @return           a KeyStore containing the self-signed certificate
     * @throws Exception if certificate generation fails
     */
    static KeyStore generateKeyStore(String password) throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048, new SecureRandom());
        KeyPair keyPair = keyGen.generateKeyPair();

        X509Certificate cert = generateCertificate(keyPair);

        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, password.toCharArray());
        ks.setKeyEntry("camel-self-signed", keyPair.getPrivate(), password.toCharArray(),
                new X509Certificate[] { cert });

        return ks;
    }

    @SuppressWarnings("restriction")
    private static X509Certificate generateCertificate(KeyPair keyPair) throws Exception {
        PublicKey publicKey = keyPair.getPublic();
        PrivateKey privateKey = keyPair.getPrivate();

        Instant now = Instant.now();
        Date notBefore = Date.from(now);
        Date notAfter = Date.from(now.plus(365, ChronoUnit.DAYS));

        // Build self-signed X.509 certificate using DER encoding
        byte[] encoded = buildSelfSignedCertificateDer(publicKey, privateKey, notBefore, notAfter);

        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(encoded));
    }

    private static byte[] buildSelfSignedCertificateDer(
            PublicKey publicKey, PrivateKey privateKey,
            Date notBefore, Date notAfter)
            throws Exception {

        // DN: CN=localhost, O=Apache Camel (self-signed)
        byte[] issuerDn = buildDn();

        // TBS Certificate
        byte[] tbsCertificate = buildTbsCertificate(publicKey, issuerDn, notBefore, notAfter);

        // Sign the TBS certificate
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(privateKey);
        sig.update(tbsCertificate);
        byte[] signature = sig.sign();

        // Build the full certificate: SEQUENCE { tbsCertificate, signatureAlgorithm, signature }
        byte[] signatureAlgorithm = sha256WithRsaAlgorithmIdentifier();
        byte[] signatureBitString = wrapBitString(signature);

        return wrapSequence(concat(tbsCertificate, signatureAlgorithm, signatureBitString));
    }

    private static byte[] buildTbsCertificate(
            PublicKey publicKey, byte[] dn,
            Date notBefore, Date notAfter)
            throws Exception {

        // Version: v3 (2)
        byte[] version = wrapExplicitTag(0, wrapInteger(new byte[] { 2 }));

        // Serial number
        byte[] serialBytes = new byte[16];
        new SecureRandom().nextBytes(serialBytes);
        serialBytes[0] &= 0x7F; // ensure positive
        byte[] serial = wrapInteger(serialBytes);

        // Signature algorithm
        byte[] signatureAlgorithm = sha256WithRsaAlgorithmIdentifier();

        // Issuer DN
        byte[] issuer = dn;

        // Validity
        byte[] validity = wrapSequence(concat(encodeUtcTime(notBefore), encodeUtcTime(notAfter)));

        // Subject DN (same as issuer for self-signed)
        byte[] subject = dn;

        // Subject Public Key Info (from the encoded public key)
        byte[] subjectPublicKeyInfo = publicKey.getEncoded();

        return wrapSequence(concat(version, serial, signatureAlgorithm, issuer, validity, subject, subjectPublicKeyInfo));
    }

    // Builds DN: CN=localhost, O=Apache Camel (self-signed)
    private static byte[] buildDn() {
        byte[] cn = buildRdn(new byte[] { 0x55, 0x04, 0x03 }, "localhost");
        byte[] o = buildRdn(new byte[] { 0x55, 0x04, 0x0A }, "Apache Camel (self-signed)");
        return wrapSequence(concat(wrapSet(cn), wrapSet(o)));
    }

    private static byte[] buildRdn(byte[] oidBytes, String value) {
        byte[] oid = new byte[2 + oidBytes.length];
        oid[0] = 0x06; // OID tag
        oid[1] = (byte) oidBytes.length;
        System.arraycopy(oidBytes, 0, oid, 2, oidBytes.length);

        byte[] valueBytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] utf8String = new byte[2 + valueBytes.length];
        utf8String[0] = 0x0C; // UTF8String tag
        utf8String[1] = (byte) valueBytes.length;
        System.arraycopy(valueBytes, 0, utf8String, 2, valueBytes.length);

        return wrapSequence(concat(oid, utf8String));
    }

    private static byte[] sha256WithRsaAlgorithmIdentifier() {
        // OID 1.2.840.113549.1.1.11 (sha256WithRSAEncryption) + NULL parameters
        byte[] oid = new byte[] {
                0x06, 0x09, 0x2A, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xF7, 0x0D, 0x01, 0x01, 0x0B };
        byte[] nullParam = new byte[] { 0x05, 0x00 };
        return wrapSequence(concat(oid, nullParam));
    }

    @SuppressWarnings("deprecation")
    private static byte[] encodeUtcTime(Date date) {
        // UTCTime format: YYMMDDHHmmSSZ
        String utc = String.format("%02d%02d%02d%02d%02d%02dZ",
                date.getYear() % 100, date.getMonth() + 1, date.getDate(),
                date.getHours(), date.getMinutes(), date.getSeconds());
        byte[] timeBytes = utc.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        byte[] result = new byte[2 + timeBytes.length];
        result[0] = 0x17; // UTCTime tag
        result[1] = (byte) timeBytes.length;
        System.arraycopy(timeBytes, 0, result, 2, timeBytes.length);
        return result;
    }

    private static byte[] wrapSequence(byte[] content) {
        return wrapTag(0x30, content);
    }

    private static byte[] wrapSet(byte[] content) {
        return wrapTag(0x31, content);
    }

    private static byte[] wrapInteger(byte[] value) {
        return wrapTag(0x02, value);
    }

    private static byte[] wrapBitString(byte[] content) {
        // BitString: tag + length + 0x00 (no unused bits) + content
        byte[] padded = new byte[1 + content.length];
        padded[0] = 0x00;
        System.arraycopy(content, 0, padded, 1, content.length);
        return wrapTag(0x03, padded);
    }

    private static byte[] wrapExplicitTag(int tagNumber, byte[] content) {
        return wrapTag(0xA0 | tagNumber, content);
    }

    private static byte[] wrapTag(int tag, byte[] content) {
        byte[] lengthBytes = encodeLength(content.length);
        byte[] result = new byte[1 + lengthBytes.length + content.length];
        result[0] = (byte) tag;
        System.arraycopy(lengthBytes, 0, result, 1, lengthBytes.length);
        System.arraycopy(content, 0, result, 1 + lengthBytes.length, content.length);
        return result;
    }

    private static byte[] encodeLength(int length) {
        if (length < 128) {
            return new byte[] { (byte) length };
        } else if (length < 256) {
            return new byte[] { (byte) 0x81, (byte) length };
        } else if (length < 65536) {
            return new byte[] { (byte) 0x82, (byte) (length >> 8), (byte) length };
        } else {
            return new byte[] { (byte) 0x83, (byte) (length >> 16), (byte) (length >> 8), (byte) length };
        }
    }

    private static byte[] concat(byte[]... arrays) {
        int totalLength = 0;
        for (byte[] array : arrays) {
            totalLength += array.length;
        }
        byte[] result = new byte[totalLength];
        int offset = 0;
        for (byte[] array : arrays) {
            System.arraycopy(array, 0, result, offset, array.length);
            offset += array.length;
        }
        return result;
    }
}
