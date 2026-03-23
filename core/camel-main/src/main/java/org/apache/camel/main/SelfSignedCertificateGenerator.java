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
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

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
     * Generates a PKCS12 KeyStore containing a self-signed certificate with Subject Alternative Names for localhost and
     * 127.0.0.1.
     *
     * @param  password  the password for the keystore and key entry
     * @return           a KeyStore containing the self-signed certificate
     * @throws Exception if certificate generation fails
     */
    static KeyStore generateKeyStore(String password) throws Exception {
        SecureRandom random = new SecureRandom();
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048, random);
        KeyPair keyPair = keyGen.generateKeyPair();

        X509Certificate cert = generateCertificate(keyPair, random);

        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, password.toCharArray());
        ks.setKeyEntry("camel-self-signed", keyPair.getPrivate(), password.toCharArray(),
                new X509Certificate[] { cert });

        return ks;
    }

    private static X509Certificate generateCertificate(KeyPair keyPair, SecureRandom random) throws Exception {
        PublicKey publicKey = keyPair.getPublic();
        PrivateKey privateKey = keyPair.getPrivate();

        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        ZonedDateTime expiry = now.plusDays(365);

        // Build self-signed X.509 certificate using DER encoding
        byte[] encoded = buildSelfSignedCertificateDer(publicKey, privateKey, now, expiry, random);

        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(encoded));
    }

    private static byte[] buildSelfSignedCertificateDer(
            PublicKey publicKey, PrivateKey privateKey,
            ZonedDateTime notBefore, ZonedDateTime notAfter,
            SecureRandom random)
            throws Exception {

        // DN: CN=localhost, O=Apache Camel (self-signed)
        byte[] issuerDn = buildDn();

        // TBS Certificate
        byte[] tbsCertificate = buildTbsCertificate(publicKey, issuerDn, notBefore, notAfter, random);

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
            ZonedDateTime notBefore, ZonedDateTime notAfter,
            SecureRandom random) {

        // Version: v3 (2)
        byte[] version = wrapExplicitTag(0, wrapInteger(new byte[] { 2 }));

        // Serial number
        byte[] serialBytes = new byte[16];
        random.nextBytes(serialBytes);
        serialBytes[0] &= 0x7F; // ensure positive
        byte[] serial = wrapInteger(serialBytes);

        // Signature algorithm
        byte[] signatureAlgorithm = sha256WithRsaAlgorithmIdentifier();

        // Validity
        byte[] validity = wrapSequence(concat(encodeUtcTime(notBefore), encodeUtcTime(notAfter)));

        // Subject Public Key Info (from the encoded public key)
        byte[] subjectPublicKeyInfo = publicKey.getEncoded();

        // Extensions: Subject Alternative Name (localhost, 127.0.0.1)
        byte[] extensions = wrapExplicitTag(3, wrapSequence(buildSanExtension()));

        return wrapSequence(
                concat(version, serial, signatureAlgorithm, dn, validity, dn, subjectPublicKeyInfo, extensions));
    }

    // Builds DN: CN=localhost, O=Apache Camel (self-signed)
    private static byte[] buildDn() {
        byte[] cn = buildRdn(new byte[] { 0x55, 0x04, 0x03 }, "localhost");
        byte[] o = buildRdn(new byte[] { 0x55, 0x04, 0x0A }, "Apache Camel (self-signed)");
        return wrapSequence(concat(wrapSet(cn), wrapSet(o)));
    }

    private static byte[] buildRdn(byte[] oidBytes, String value) {
        byte[] oid = wrapTag(0x06, oidBytes);
        byte[] utf8String = wrapTag(0x0C, value.getBytes(StandardCharsets.UTF_8));
        return wrapSequence(concat(oid, utf8String));
    }

    // Builds SAN extension with DNS:localhost and IP:127.0.0.1
    private static byte[] buildSanExtension() {
        // OID 2.5.29.17 (subjectAltName)
        byte[] sanOid = wrapTag(0x06, new byte[] { 0x55, 0x1D, 0x11 });

        // SAN value: SEQUENCE { [2] "localhost", [7] 127.0.0.1 }
        byte[] dnsName = wrapTag(0x82, "localhost".getBytes(StandardCharsets.US_ASCII)); // context [2] = dNSName
        byte[] ipAddress = wrapTag(0x87, new byte[] { 127, 0, 0, 1 }); // context [7] = iPAddress
        byte[] sanSequence = wrapSequence(concat(dnsName, ipAddress));

        // Wrap the SAN value as an OCTET STRING (extension value must be wrapped)
        byte[] sanOctetString = wrapTag(0x04, sanSequence);

        return wrapSequence(concat(sanOid, sanOctetString));
    }

    private static byte[] sha256WithRsaAlgorithmIdentifier() {
        // OID 1.2.840.113549.1.1.11 (sha256WithRSAEncryption) + NULL parameters
        byte[] oid = new byte[] {
                0x06, 0x09, 0x2A, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xF7, 0x0D, 0x01, 0x01, 0x0B };
        byte[] nullParam = new byte[] { 0x05, 0x00 };
        return wrapSequence(concat(oid, nullParam));
    }

    private static byte[] encodeUtcTime(ZonedDateTime dateTime) {
        // UTCTime format: YYMMDDHHmmSSZ
        String utc = String.format("%02d%02d%02d%02d%02d%02dZ",
                dateTime.getYear() % 100, dateTime.getMonthValue(), dateTime.getDayOfMonth(),
                dateTime.getHour(), dateTime.getMinute(), dateTime.getSecond());
        byte[] timeBytes = utc.getBytes(StandardCharsets.US_ASCII);
        return wrapTag(0x17, timeBytes);
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
