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
package org.apache.camel.component.as2.api.entity;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Base64;

import org.apache.camel.component.as2.api.AS2Header;
import org.apache.camel.component.as2.api.AS2SignatureAlgorithm;
import org.apache.camel.component.as2.api.AS2SignedDataGenerator;
import org.apache.camel.component.as2.api.Utils;
import org.apache.camel.component.as2.api.util.MicUtils;
import org.apache.camel.component.as2.api.util.SigningUtils;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.BasicHttpEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.bouncycastle.cms.CMSAlgorithm;
import org.bouncycastle.cms.CMSEnvelopedDataGenerator;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.jcajce.JceCMSContentEncryptorBuilder;
import org.bouncycastle.cms.jcajce.JceKeyTransRecipientInfoGenerator;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A signature over a multipart/signed is created over the first body part exactly as it is transmitted - its MIME
 * headers and its body in the transfer encoding the sender applied (RFC 1847, 2.1) - and the MIC of the receipt is
 * computed over the same bytes (RFC 4130, 7.1). Another AS2 implementation (openssl, Lobster, OpenAS2, ...) encodes
 * base64 or quoted-printable in its own way, e.g. with its own line length, so the part can only be verified over the
 * bytes received, never over a re-serialisation of the parsed part. These tests play such an implementation.
 */
class MultipartSignedEntityAsReceivedTest {

    private static final String EDI_MESSAGE = "UNA:+.? 'UNB+UNOC:3+4000000000001:14+4000000000002:14+261028:1200+7'"
                                              + "UNH+1+ORDERS:D:96A:UN'BGM+220+ORDER-7+9'"
                                              + "NAD+BY+++Mueller Strasse 5 Koeln'UNT+4+1'UNZ+1+7'";
    private static final String BOUNDARY = "----=_Part_as_received_7";
    private static final String MULTIPART_SIGNED_CONTENT_TYPE
            = "multipart/signed; protocol=\"application/pkcs7-signature\"; micalg=sha-256; boundary=\"" + BOUNDARY + "\"";
    private static final String DISPOSITION_NOTIFICATION_OPTIONS
            = "signed-receipt-protocol=optional, pkcs7-signature; signed-receipt-micalg=optional, sha256";

    private static KeyPair signingKP;
    private static X509Certificate signingCert;
    private static X509Certificate otherCert;

    @BeforeAll
    static void setUpKeysAndCertificates() throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", "BC");
        kpg.initialize(2048, new SecureRandom());

        String signingDN = "O=AS2 Partner, C=DE";
        signingKP = kpg.generateKeyPair();
        signingCert = Utils.makeCertificate(signingKP, signingDN, signingKP, signingDN);

        String otherDN = "O=Somebody Else, C=DE";
        KeyPair otherKP = kpg.generateKeyPair();
        otherCert = Utils.makeCertificate(otherKP, otherDN, otherKP, otherDN);
    }

    /**
     * The part as the sender transmitted it: base64 wrapped at 64 characters (openssl's width; camel-as2 re-encodes at
     * 76), quoted-printable that escapes the apostrophes (allowed, decodes to the same payload; camel-as2 would
     * re-encode without), or binary.
     */
    private static byte[] signedPart(String transferEncoding) {
        String body;
        switch (transferEncoding) {
            case "base64":
                body = wrap(Base64.getEncoder().encodeToString(EDI_MESSAGE.getBytes(StandardCharsets.UTF_8)), 64);
                break;
            case "quoted-printable":
                body = EDI_MESSAGE.replace("'", "=27");
                break;
            default:
                body = EDI_MESSAGE;
        }
        String part = "Content-Type: application/edifact\r\n"
                      + "Content-Transfer-Encoding: " + transferEncoding + "\r\n"
                      + "\r\n"
                      + body;
        return part.getBytes(StandardCharsets.UTF_8);
    }

    /** The decoded payload of a parsed EDI entity, whatever form the entity holds it in. */
    private static String payloadOf(ApplicationEntity ediEntity) throws Exception {
        Object ediMessage = ediEntity.getEdiMessage();
        if (ediMessage instanceof String text) {
            return text;
        }
        if (ediMessage instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        if (ediMessage instanceof InputStream stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        return String.valueOf(ediMessage);
    }

    private static String wrap(String s, int width) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i += width) {
            if (i > 0) {
                sb.append("\r\n");
            }
            sb.append(s, i, Math.min(s.length(), i + width));
        }
        return sb.toString();
    }

    /** A detached PKCS#7 signature over exactly the given bytes, as openssl cms -sign produces it. */
    private static byte[] sign(byte[] signedPart) throws Exception {
        AS2SignedDataGenerator generator = SigningUtils.createSigningGenerator(AS2SignatureAlgorithm.SHA256WITHRSA,
                new Certificate[] { signingCert }, signingKP.getPrivate());
        return generator.generate(new CMSProcessableByteArray(signedPart), false).getEncoded();
    }

    /** The body of a multipart/signed as transmitted: the part, then the signature, both ended by the boundary. */
    private static byte[] multipartSignedBody(byte[] signedPart, byte[] signature) throws Exception {
        return multipartSignedBody(signedPart, signature, "\r\n");
    }

    /**
     * The same with the sender's line ending around the boundaries: CRLF as MIME asks, or a bare LF as openssl writes
     * it unless told -crlfeol - the line break before a delimiter belongs to the boundary either way.
     */
    private static byte[] multipartSignedBody(byte[] signedPart, byte[] signature, String lineEnding) throws Exception {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + BOUNDARY + lineEnding).getBytes(StandardCharsets.US_ASCII));
        body.write(signedPart);
        body.write((lineEnding + "--" + BOUNDARY + lineEnding
                    + "Content-Type: application/pkcs7-signature; name=smime.p7s" + lineEnding
                    + "Content-Transfer-Encoding: base64" + lineEnding
                    + lineEnding)
                .getBytes(StandardCharsets.US_ASCII));
        body.write(wrap(Base64.getEncoder().encodeToString(signature), 76).getBytes(StandardCharsets.US_ASCII));
        body.write((lineEnding + "--" + BOUNDARY + "--" + lineEnding).getBytes(StandardCharsets.US_ASCII));
        return body.toByteArray();
    }

    /** The whole multipart/signed entity with its own headers, as it sits inside an encrypted message. */
    private static byte[] multipartSignedEntity(byte[] signedPart, byte[] signature) throws Exception {
        ByteArrayOutputStream entity = new ByteArrayOutputStream();
        entity.write(("Content-Type: " + MULTIPART_SIGNED_CONTENT_TYPE + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        entity.write(multipartSignedBody(signedPart, signature));
        return entity.toByteArray();
    }

    @ParameterizedTest
    @ValueSource(strings = { "base64", "quoted-printable", "binary" })
    void signatureIsVerifiedOverThePartAsReceived(String transferEncoding) throws Exception {
        byte[] signedPart = signedPart(transferEncoding);
        byte[] signature = sign(signedPart);

        MimeEntity entity = EntityParser.parseEntity(multipartSignedEntity(signedPart, signature));

        MultipartSignedEntity multipartSignedEntity = assertInstanceOf(MultipartSignedEntity.class, entity);
        assertArrayEquals(signedPart, multipartSignedEntity.getSignedDataRaw(),
                "the parser must keep the signed part exactly as received");
        assertTrue(SigningUtils.isValid(multipartSignedEntity, new Certificate[] { signingCert }),
                "a signature over the part as transmitted must verify");
        assertFalse(SigningUtils.isValid(multipartSignedEntity, new Certificate[] { otherCert }),
                "a signature must not verify against another certificate");

        ApplicationEntity ediEntity = assertInstanceOf(ApplicationEntity.class, multipartSignedEntity.getSignedDataEntity());
        assertEquals(EDI_MESSAGE, payloadOf(ediEntity), "the decoded payload must be unchanged");
    }

    @ParameterizedTest
    @ValueSource(strings = { "base64", "quoted-printable" })
    void reserialisingTheParsedPartDoesNotGiveTheSignedBytes(String transferEncoding) throws Exception {
        byte[] signedPart = signedPart(transferEncoding);
        byte[] signature = sign(signedPart);

        MultipartSignedEntity multipartSignedEntity = (MultipartSignedEntity) EntityParser
                .parseEntity(multipartSignedEntity(signedPart, signature));

        ByteArrayOutputStream reserialised = new ByteArrayOutputStream();
        multipartSignedEntity.getSignedDataEntity().writeTo(reserialised);
        assertFalse(Arrays.equals(signedPart, reserialised.toByteArray()),
                "the re-serialised part differs from what the sender transmitted - verifying over it is wrong");
        assertFalse(SigningUtils.isValidSigned(reserialised.toByteArray(), signature, new Certificate[] { signingCert }),
                "the signature does not verify over the re-serialised part");
    }

    @ParameterizedTest
    @ValueSource(strings = { "base64", "quoted-printable", "binary" })
    void micIsComputedOverThePartAsReceived(String transferEncoding) throws Exception {
        byte[] signedPart = signedPart(transferEncoding);
        byte[] signature = sign(signedPart);

        BasicClassicHttpRequest request = new BasicClassicHttpRequest("POST", "/");
        request.addHeader(AS2Header.CONTENT_TYPE, MULTIPART_SIGNED_CONTENT_TYPE);
        request.addHeader(AS2Header.DISPOSITION_NOTIFICATION_OPTIONS, DISPOSITION_NOTIFICATION_OPTIONS);
        request.setEntity(new BasicHttpEntity(
                new ByteArrayInputStream(multipartSignedBody(signedPart, signature)),
                ContentType.parse(MULTIPART_SIGNED_CONTENT_TYPE)));

        MicUtils.ReceivedContentMic mic
                = MicUtils.createReceivedContentMic(request, new Certificate[] { signingCert }, null);

        assertNotNull(mic, "a MIC must be created");
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(signedPart);
        assertEquals(Base64.getEncoder().encodeToString(digest), mic.getEncodedMessageDigest(),
                "the MIC must cover the signed part as transmitted - the sender computes it over the same bytes");
    }

    @ParameterizedTest
    @ValueSource(strings = { "base64", "binary" })
    void senderFramingTheBoundariesWithBareLineFeedsIsVerifiedAndParsedAlike(String transferEncoding) throws Exception {
        // openssl cms -sign without -crlfeol: a bare LF before each delimiter line. The signature covers the part up
        // to its last byte - and so must the parsed payload, which once lost that byte to a CRLF assumption
        byte[] signedPart = signedPart(transferEncoding);
        byte[] signature = sign(signedPart);
        byte[] body = multipartSignedBody(signedPart, signature, "\n");

        ByteArrayOutputStream entityBytes = new ByteArrayOutputStream();
        entityBytes.write(("Content-Type: " + MULTIPART_SIGNED_CONTENT_TYPE + "\n\n").getBytes(StandardCharsets.US_ASCII));
        entityBytes.write(body);
        MultipartSignedEntity multipartSignedEntity
                = assertInstanceOf(MultipartSignedEntity.class, EntityParser.parseEntity(entityBytes.toByteArray()));

        assertArrayEquals(signedPart, multipartSignedEntity.getSignedDataRaw(), "the part as received, to its last byte");
        assertTrue(SigningUtils.isValid(multipartSignedEntity, new Certificate[] { signingCert }));
        ApplicationEntity ediEntity = assertInstanceOf(ApplicationEntity.class, multipartSignedEntity.getSignedDataEntity());
        assertEquals(EDI_MESSAGE, payloadOf(ediEntity), "the parsed payload is the whole part, its last byte included");

        BasicClassicHttpRequest request = new BasicClassicHttpRequest("POST", "/");
        request.addHeader(AS2Header.CONTENT_TYPE, MULTIPART_SIGNED_CONTENT_TYPE);
        request.addHeader(AS2Header.DISPOSITION_NOTIFICATION_OPTIONS, DISPOSITION_NOTIFICATION_OPTIONS);
        request.setEntity(
                new BasicHttpEntity(new ByteArrayInputStream(body), ContentType.parse(MULTIPART_SIGNED_CONTENT_TYPE)));
        MicUtils.ReceivedContentMic mic = MicUtils.createReceivedContentMic(request, new Certificate[] { signingCert }, null);
        assertEquals(Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(signedPart)),
                mic.getEncodedMessageDigest(), "the MIC covers the same bytes");
    }

    @Test
    void signatureInsideAnEncryptedMessageIsVerifiedOverThePartAsReceived() throws Exception {
        byte[] signedPart = signedPart("base64");
        byte[] signature = sign(signedPart);

        // encrypted for the receiver, as a partner's software does: the multipart/signed with its headers is the
        // enveloped content
        CMSEnvelopedDataGenerator envelopedDataGenerator = new CMSEnvelopedDataGenerator();
        envelopedDataGenerator.addRecipientInfoGenerator(new JceKeyTransRecipientInfoGenerator(signingCert));
        byte[] encrypted = envelopedDataGenerator.generate(
                new CMSProcessableByteArray(multipartSignedEntity(signedPart, signature)),
                new JceCMSContentEncryptorBuilder(CMSAlgorithm.AES256_CBC).build()).getEncoded();

        ApplicationPkcs7MimeEnvelopedDataEntity envelopedEntity
                = new ApplicationPkcs7MimeEnvelopedDataEntity(encrypted, "binary", true);
        MimeEntity decrypted = envelopedEntity.getEncryptedEntity(signingKP.getPrivate());

        MultipartSignedEntity multipartSignedEntity = assertInstanceOf(MultipartSignedEntity.class, decrypted);
        assertArrayEquals(signedPart, multipartSignedEntity.getSignedDataRaw());
        assertTrue(SigningUtils.isValid(multipartSignedEntity, new Certificate[] { signingCert }),
                "a signature over the part as transmitted must verify after decryption too");
    }

    @Test
    void entityBuiltLocallyIsVerifiedOverItsSerialisation() throws Exception {
        // no "as received" bytes exist for an entity camel-as2 built itself: its serialisation is what is signed
        ApplicationEDIFACTEntity ediEntity = new ApplicationEDIFACTEntity(
                EDI_MESSAGE.getBytes(StandardCharsets.US_ASCII), StandardCharsets.US_ASCII.name(), "base64", false,
                "orders.edi");
        AS2SignedDataGenerator generator = SigningUtils.createSigningGenerator(AS2SignatureAlgorithm.SHA256WITHRSA,
                new Certificate[] { signingCert }, signingKP.getPrivate());
        MultipartSignedEntity multipartSignedEntity
                = new MultipartSignedEntity(ediEntity, generator, StandardCharsets.US_ASCII.name(), "base64", true, null);

        assertNull(multipartSignedEntity.getSignedDataRaw(), "nothing was received for a locally built entity");
        assertNotNull(multipartSignedEntity.getSignedDataContent());
        assertTrue(SigningUtils.isValid(multipartSignedEntity, new Certificate[] { signingCert }));
    }
}
