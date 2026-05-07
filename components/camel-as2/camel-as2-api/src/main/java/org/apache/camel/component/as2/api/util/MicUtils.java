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
package org.apache.camel.component.as2.api.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.cert.Certificate;

import org.apache.camel.component.as2.api.AS2Header;
import org.apache.camel.component.as2.api.AS2MicAlgorithm;
import org.apache.camel.component.as2.api.AS2MimeType;
import org.apache.camel.component.as2.api.entity.ApplicationPkcs7MimeCompressedDataEntity;
import org.apache.camel.component.as2.api.entity.ApplicationPkcs7MimeEnvelopedDataEntity;
import org.apache.camel.component.as2.api.entity.DispositionNotificationOptions;
import org.apache.camel.component.as2.api.entity.DispositionNotificationOptionsParser;
import org.apache.camel.component.as2.api.entity.EntityParser;
import org.apache.camel.component.as2.api.entity.MimeEntity;
import org.apache.camel.component.as2.api.entity.MultipartSignedEntity;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MicUtils {
    private static final Logger LOG = LoggerFactory.getLogger(MicUtils.class);

    private MicUtils() {
    }

    public static class ReceivedContentMic {
        private final String digestAlgorithmId;
        private final String encodedMessageDigest;

        public ReceivedContentMic(String digestAlgorithmId, byte[] messageDigest) throws Exception {
            this.digestAlgorithmId = digestAlgorithmId;
            messageDigest = EntityUtils.encode(messageDigest, "base64");
            this.encodedMessageDigest = new String(messageDigest, StandardCharsets.US_ASCII);
        }

        // Used when parsing received content MIC from received string
        protected ReceivedContentMic(String digestAlgorithmId, String encodedMessageDigest) {
            this.digestAlgorithmId = digestAlgorithmId;
            this.encodedMessageDigest = encodedMessageDigest;
        }

        public String getDigestAlgorithmId() {
            return digestAlgorithmId;
        }

        public String getEncodedMessageDigest() {
            return encodedMessageDigest;
        }

        @Override
        public String toString() {
            return encodedMessageDigest + "," + digestAlgorithmId;
        }
    }

    public static byte[] createMic(byte[] content, String algorithmId) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance(algorithmId, "BC");
            return messageDigest.digest(content);
        } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
            LOG.debug("failed to get message digets '{}'", algorithmId);
            return null;
        }
    }

    public static ReceivedContentMic createReceivedContentMic(
            ClassicHttpRequest request, Certificate[] validateSigningCertificateChain, PrivateKey decryptingPrivateKey)
            throws HttpException {

        String dispositionNotificationOptionsString
                = HttpMessageUtils.getHeaderValue(request, AS2Header.DISPOSITION_NOTIFICATION_OPTIONS);
        if (dispositionNotificationOptionsString == null) {
            LOG.debug("do not create MIC: no disposition notification options in request");
            return null;
        }
        DispositionNotificationOptions dispositionNotificationOptions = DispositionNotificationOptionsParser
                .parseDispositionNotificationOptions(dispositionNotificationOptionsString, null);
        String micJdkAlgorithmName
                = getMicJdkAlgorithmName(dispositionNotificationOptions.getSignedReceiptMicalg().getValues());
        if (micJdkAlgorithmName == null) {
            LOG.debug("do not create MIC: no matching MIC algorithms found");
            return null;
        }

        // Compute MIC over the correct content per RFC 5402:
        // - For compress-before-sign: MIC covers the compressed entity (what was signed)
        // - For sign-before-compress or no compression: MIC covers the EDI payload
        HttpEntity micEntity = findMicEntity(request, validateSigningCertificateChain, decryptingPrivateKey);

        byte[] content = EntityUtils.getContent(micEntity);

        String micAS2AlgorithmName = AS2MicAlgorithm.getAS2AlgorithmName(micJdkAlgorithmName);
        byte[] mic = createMic(content, micJdkAlgorithmName);
        try {
            return new ReceivedContentMic(micAS2AlgorithmName, mic);
        } catch (Exception e) {
            throw new HttpException("Failed to encode MIC", e);
        }
    }

    /**
     * Finds the correct entity to compute the MIC over, per RFC 5402.
     * <p>
     * For compress-before-sign, the MIC must cover the compressed entity (what was signed). For sign-before-compress or
     * no compression, the MIC covers the EDI payload.
     */
    private static HttpEntity findMicEntity(
            ClassicHttpRequest request, Certificate[] validateSigningCertificateChain, PrivateKey decryptingPrivateKey)
            throws HttpException {

        EntityParser.parseAS2MessageEntity(request);

        String contentTypeString = HttpMessageUtils.getHeaderValue(request, AS2Header.CONTENT_TYPE);
        if (contentTypeString == null) {
            throw new HttpException("Failed to create MIC: content type missing from request");
        }
        ContentType contentType = ContentType.parse(contentTypeString);

        // Navigate the message structure to find the signed data entity
        HttpEntity entity = findSignedDataEntity(request, contentType, decryptingPrivateKey);
        if (entity != null) {
            return entity;
        }

        // No multipart/signed found, fall back to extracting the EDI payload
        return HttpMessageUtils.extractEdiPayload(request,
                new HttpMessageUtils.DecrpytingAndSigningInfo(validateSigningCertificateChain, decryptingPrivateKey));
    }

    /**
     * Navigate the entity hierarchy to find the signed data entity (part 0 of multipart/signed). Returns the signed
     * data entity which is what the MIC should be computed over, or null if no multipart/signed is found.
     */
    private static HttpEntity findSignedDataEntity(
            ClassicHttpRequest request, ContentType contentType, PrivateKey decryptingPrivateKey)
            throws HttpException {

        String mimeType = contentType.getMimeType().toLowerCase();

        if (AS2MimeType.MULTIPART_SIGNED.equals(mimeType)) {
            // Top-level signed: MIC is over the signed data entity (part 0)
            MultipartSignedEntity multipartSignedEntity
                    = HttpMessageUtils.getEntity(request, MultipartSignedEntity.class);
            if (multipartSignedEntity != null) {
                return multipartSignedEntity.getSignedDataEntity();
            }
        } else if (AS2MimeType.APPLICATION_PKCS7_MIME.equals(mimeType)) {
            String smimeType = contentType.getParameter("smime-type");
            if ("compressed-data".equals(smimeType)) {
                // Sign-before-compress: decompress first, then find the signed entity inside
                ApplicationPkcs7MimeCompressedDataEntity compressedEntity
                        = HttpMessageUtils.getEntity(request, ApplicationPkcs7MimeCompressedDataEntity.class);
                if (compressedEntity != null) {
                    MimeEntity inner = compressedEntity
                            .getCompressedEntity(new org.bouncycastle.cms.jcajce.ZlibExpanderProvider());
                    if (inner instanceof MultipartSignedEntity signedEntity) {
                        return signedEntity.getSignedDataEntity();
                    }
                }
            } else if ("enveloped-data".equals(smimeType) && decryptingPrivateKey != null) {
                // Encrypted message: decrypt first, then look for signed entity
                ApplicationPkcs7MimeEnvelopedDataEntity envelopedEntity
                        = HttpMessageUtils.getEntity(request, ApplicationPkcs7MimeEnvelopedDataEntity.class);
                if (envelopedEntity != null) {
                    MimeEntity decryptedEntity = envelopedEntity.getEncryptedEntity(decryptingPrivateKey);
                    String decryptedContentType = decryptedEntity.getContentType();
                    if (decryptedContentType != null) {
                        ContentType decryptedCt = ContentType.parse(decryptedContentType);
                        String decryptedMime = decryptedCt.getMimeType().toLowerCase();
                        if (AS2MimeType.MULTIPART_SIGNED.equals(decryptedMime)
                                && decryptedEntity instanceof MultipartSignedEntity signedEntity) {
                            return signedEntity.getSignedDataEntity();
                        }
                    }
                }
            }
        }

        return null;
    }

    public static String getMicJdkAlgorithmName(String[] micAs2AlgorithmNames) {
        if (micAs2AlgorithmNames == null) {
            return AS2MicAlgorithm.SHA_1.getJdkAlgorithmName();
        }
        for (String micAs2AlgorithmName : micAs2AlgorithmNames) {
            String micJdkAlgorithmName = AS2MicAlgorithm.getJdkAlgorithmName(micAs2AlgorithmName);
            if (micJdkAlgorithmName != null) {
                return micJdkAlgorithmName;
            }
        }
        return AS2MicAlgorithm.SHA_1.getJdkAlgorithmName();
    }
}
