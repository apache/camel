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

import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.util.Objects;

import org.apache.camel.component.as2.api.AS2Header;
import org.apache.camel.component.as2.api.AS2MimeType;
import org.apache.camel.component.as2.api.entity.ApplicationEntity;
import org.apache.camel.component.as2.api.entity.ApplicationPkcs7MimeCompressedDataEntity;
import org.apache.camel.component.as2.api.entity.ApplicationPkcs7MimeEnvelopedDataEntity;
import org.apache.camel.component.as2.api.entity.EntityParser;
import org.apache.camel.component.as2.api.entity.MimeEntity;
import org.apache.camel.component.as2.api.entity.MultipartSignedEntity;
import org.apache.camel.component.as2.api.exception.AS2AuthenticationException;
import org.apache.camel.component.as2.api.exception.AS2DecryptionException;
import org.apache.camel.component.as2.api.exception.AS2InsufficientSecurityException;
import org.apache.camel.util.ObjectHelper;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HeaderElement;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpMessage;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.message.MessageSupport;
import org.bouncycastle.cms.jcajce.ZlibExpanderProvider;

public final class HttpMessageUtils {

    private HttpMessageUtils() {}

    public static String getHeaderValue(HttpMessage message, String headerName) {
        Header header = message.getFirstHeader(headerName);
        return header == null ? null : header.getValue();
    }

    public static void setHeaderValue(HttpMessage message, String headerName, String headerValue) {
        ObjectHelper.notNull(message, "message");
        ObjectHelper.notNull(headerName, "headerName");
        if (headerValue == null) {
            message.removeHeaders(headerName);
        } else {
            message.setHeader(headerName, headerValue);
        }
    }

    public static <T> T getEntity(HttpMessage message, Class<T> type) {
        ObjectHelper.notNull(message, "message");
        ObjectHelper.notNull(type, "type");
        if (message instanceof BasicClassicHttpRequest httpEntityEnclosingRequest) {
            HttpEntity entity = httpEntityEnclosingRequest.getEntity();
            if (entity != null && type.isInstance(entity)) {
                return type.cast(entity);
            }
        } else if (message instanceof BasicClassicHttpResponse httpResponse) {
            HttpEntity entity = httpResponse.getEntity();
            if (entity != null && type.isInstance(entity)) {
                type.cast(entity);
            }
        }
        return null;
    }

    public static String getParameterValue(HttpMessage message, String headerName, String parameterName) {
        ObjectHelper.notNull(message, "message");
        ObjectHelper.notNull(headerName, "headerName");
        ObjectHelper.notNull(parameterName, "parameterName");
        Header header = message.getFirstHeader(headerName);
        if (header == null) {
            return null;
        }
        for (HeaderElement headerElement : MessageSupport.parse(header)) {
            for (NameValuePair nameValuePair : headerElement.getParameters()) {
                if (nameValuePair.getName().equalsIgnoreCase(parameterName)) {
                    return nameValuePair.getValue();
                }
            }
        }
        return null;
    }

    public static ApplicationEntity extractEdiPayload(
            HttpMessage message, DecrpytingAndSigningInfo decrpytingAndSigningInfo) throws HttpException {

        String contentTypeString = getHeaderValue(message, AS2Header.CONTENT_TYPE);
        if (contentTypeString == null) {
            throw new HttpException("Failed to create MIC: content type missing from request");
        }
        ContentType contentType = ContentType.parse(contentTypeString);

        EntityParser.parseAS2MessageEntity(message);
        ApplicationEntity ediEntity;
        switch (contentType.getMimeType().toLowerCase()) {
            case AS2MimeType.APPLICATION_EDIFACT:
            case AS2MimeType.APPLICATION_EDI_X12:
            case AS2MimeType.APPLICATION_EDI_CONSENT: {
                // expect a signed entity when certificate chain is held to validate signatures
                if (decrpytingAndSigningInfo.getValidateSigningCertificateChain() != null) {
                    throw new AS2InsufficientSecurityException("Failed to validate the signature");
                }
                // expect an encrypted entity when a decryption key is held
                if (decrpytingAndSigningInfo.getDecryptingPrivateKey() != null) {
                    throw new AS2InsufficientSecurityException("Expected to be encrypted");
                }
                ediEntity = getEntity(message, ApplicationEntity.class);
                break;
            }
            case AS2MimeType.MULTIPART_SIGNED: {
                if (decrpytingAndSigningInfo.getDecryptingPrivateKey() != null) {
                    throw new AS2InsufficientSecurityException("Expected to be encrypted");
                }
                ediEntity = extractMultipartSigned(message, decrpytingAndSigningInfo);
                break;
            }
            case AS2MimeType.APPLICATION_PKCS7_MIME: {
                switch (contentType.getParameter("smime-type")) {
                    case "compressed-data": {
                        if (decrpytingAndSigningInfo.getDecryptingPrivateKey() != null) {
                            throw new AS2InsufficientSecurityException("Expected to be encrypted");
                        }
                        ediEntity = extractCompressedData(message, decrpytingAndSigningInfo);
                        break;
                    }
                    case "enveloped-data": {
                        ediEntity = extractEnvelopedData(message, decrpytingAndSigningInfo);
                        break;
                    }
                    default:
                        throw new HttpException(
                                "Failed to extract EDI message: unknown " + AS2MimeType.APPLICATION_PKCS7_MIME
                                        + " smime-type: " + contentType.getParameter("smime-type"));
                }
                break;
            }
            default:
                throw new HttpException("Failed to extract EDI message: invalid content type '"
                        + contentType.getMimeType() + "' for AS2 request message");
        }

        return ediEntity;
    }

    private static ApplicationEntity extractEnvelopedData(
            HttpMessage message, DecrpytingAndSigningInfo decrpytingAndSigningInfo) throws HttpException {
        ApplicationEntity ediEntity;
        if (decrpytingAndSigningInfo.getDecryptingPrivateKey() == null) {
            throw new AS2DecryptionException(
                    "Failed to extract EDI payload: private key can not be null for AS2 enveloped message");
        }
        ApplicationPkcs7MimeEnvelopedDataEntity envelopedDataEntity =
                getEntity(message, ApplicationPkcs7MimeEnvelopedDataEntity.class);

        Objects.requireNonNull(envelopedDataEntity, "Failed to extract EDI payload: the enveloped data entity is null");
        ediEntity = extractEdiPayloadFromEnvelopedEntity(envelopedDataEntity, decrpytingAndSigningInfo);
        return ediEntity;
    }

    private static ApplicationEntity extractCompressedData(
            HttpMessage message, DecrpytingAndSigningInfo decrpytingAndSigningInfo) throws HttpException {
        ApplicationEntity ediEntity;
        ApplicationPkcs7MimeCompressedDataEntity compressedDataEntity =
                getEntity(message, ApplicationPkcs7MimeCompressedDataEntity.class);

        Objects.requireNonNull(
                compressedDataEntity, "Failed to extract the EDI payload: the compressed data entity is null");

        ediEntity = extractEdiPayloadFromCompressedEntity(compressedDataEntity, decrpytingAndSigningInfo, false);
        return ediEntity;
    }

    private static ApplicationEntity extractMultipartSigned(
            HttpMessage message, DecrpytingAndSigningInfo decrpytingAndSigningInfo) throws HttpException {
        ApplicationEntity ediEntity;
        MultipartSignedEntity multipartSignedEntity = getEntity(message, MultipartSignedEntity.class);

        Objects.requireNonNull(
                multipartSignedEntity, "Failed to extract EDI payload: the multipart signed entity is null");

        if (decrpytingAndSigningInfo.getValidateSigningCertificateChain() != null
                && !SigningUtils.isValid(
                        multipartSignedEntity, decrpytingAndSigningInfo.getValidateSigningCertificateChain())) {
            throw new AS2AuthenticationException("Failed to validate the signature");
        }

        MimeEntity mimeEntity = multipartSignedEntity.getSignedDataEntity();
        if (mimeEntity instanceof ApplicationEntity) {
            ediEntity = (ApplicationEntity) mimeEntity;
        } else if (mimeEntity instanceof ApplicationPkcs7MimeCompressedDataEntity compressedDataEntity) {
            ediEntity = extractEdiPayloadFromCompressedEntity(compressedDataEntity, decrpytingAndSigningInfo, true);
        } else {
            throw new HttpException("Failed to extract EDI payload: invalid content type '"
                    + mimeEntity.getContentType() + "' for AS2 compressed and signed message");
        }
        return ediEntity;
    }

    private static ApplicationEntity extractEdiPayloadFromEnvelopedEntity(
            ApplicationPkcs7MimeEnvelopedDataEntity envelopedDataEntity,
            DecrpytingAndSigningInfo decrpytingAndSigningInfo)
            throws HttpException {
        ApplicationEntity ediEntity;

        MimeEntity entity = envelopedDataEntity.getEncryptedEntity(decrpytingAndSigningInfo.getDecryptingPrivateKey());
        String contentTypeString = entity.getContentType();
        if (contentTypeString == null) {
            throw new HttpException("Failed to extract EDI message: content type missing from encrypted entity");
        }
        ContentType contentType = ContentType.parse(contentTypeString);

        switch (contentType.getMimeType().toLowerCase()) {
            case AS2MimeType.APPLICATION_EDIFACT:
            case AS2MimeType.APPLICATION_EDI_X12:
            case AS2MimeType.APPLICATION_EDI_CONSENT: {
                if (decrpytingAndSigningInfo.getValidateSigningCertificateChain() != null) {
                    throw new AS2InsufficientSecurityException("Failed to validate the signature");
                }
                ediEntity = (ApplicationEntity) entity;
                break;
            }
            case AS2MimeType.MULTIPART_SIGNED: {
                MultipartSignedEntity multipartSignedEntity = (MultipartSignedEntity) entity;
                if (decrpytingAndSigningInfo.getValidateSigningCertificateChain() != null
                        && !SigningUtils.isValid(
                                multipartSignedEntity, decrpytingAndSigningInfo.getValidateSigningCertificateChain())) {
                    throw new AS2AuthenticationException("Failed to validate the signature");
                }

                MimeEntity mimeEntity = multipartSignedEntity.getSignedDataEntity();
                if (mimeEntity instanceof ApplicationEntity) {
                    ediEntity = (ApplicationEntity) mimeEntity;
                } else if (mimeEntity instanceof ApplicationPkcs7MimeCompressedDataEntity compressedDataEntity) {
                    ediEntity =
                            extractEdiPayloadFromCompressedEntity(compressedDataEntity, decrpytingAndSigningInfo, true);
                } else {

                    throw new HttpException("Failed to extract EDI payload: invalid content type '"
                            + mimeEntity.getContentType() + "' for AS2 compressed and signed entity");
                }
                break;
            }
            case AS2MimeType.APPLICATION_PKCS7_MIME: {
                if (!"compressed-data".equals(contentType.getParameter("smime-type"))) {
                    throw new HttpException("Failed to extract EDI payload: invalid mime type '"
                            + contentType.getParameter("smime-type") + "' for AS2 enveloped entity");
                }
                ApplicationPkcs7MimeCompressedDataEntity compressedDataEntity =
                        (ApplicationPkcs7MimeCompressedDataEntity) entity;
                ediEntity =
                        extractEdiPayloadFromCompressedEntity(compressedDataEntity, decrpytingAndSigningInfo, false);
                break;
            }
            default:
                throw new HttpException("Failed to extract EDI payload: invalid content type '"
                        + contentType.getMimeType() + "' for AS2 enveloped entity");
        }

        return ediEntity;
    }

    public static ApplicationEntity extractEdiPayloadFromCompressedEntity(
            ApplicationPkcs7MimeCompressedDataEntity compressedDataEntity,
            DecrpytingAndSigningInfo decrpytingAndSigningInfo,
            boolean hasValidSignature)
            throws HttpException {
        ApplicationEntity ediEntity;

        MimeEntity entity = compressedDataEntity.getCompressedEntity(new ZlibExpanderProvider());
        String contentTypeString = entity.getContentType();
        if (contentTypeString == null) {
            throw new HttpException("Failed to extract EDI payload: content type missing from compressed entity");
        }
        ContentType contentType = ContentType.parse(contentTypeString);

        switch (contentType.getMimeType().toLowerCase()) {
            case AS2MimeType.APPLICATION_EDIFACT:
            case AS2MimeType.APPLICATION_EDI_X12:
            case AS2MimeType.APPLICATION_EDI_CONSENT: {
                if (!hasValidSignature && decrpytingAndSigningInfo.getValidateSigningCertificateChain() != null) {
                    // fail auth if signature not already verified, e.g. for compressed-signed
                    throw new AS2InsufficientSecurityException("Failed to validate the signature");
                }
                ediEntity = (ApplicationEntity) entity;
                break;
            }
            case AS2MimeType.MULTIPART_SIGNED: {
                MultipartSignedEntity multipartSignedEntity = (MultipartSignedEntity) entity;
                if (decrpytingAndSigningInfo.getValidateSigningCertificateChain() != null
                        && !SigningUtils.isValid(
                                multipartSignedEntity, decrpytingAndSigningInfo.getValidateSigningCertificateChain())) {
                    throw new AS2AuthenticationException("Failed to validate the signature");
                }

                MimeEntity mimeEntity = multipartSignedEntity.getSignedDataEntity();
                if (mimeEntity instanceof ApplicationEntity applicationEntity) {
                    ediEntity = applicationEntity;
                } else {

                    throw new HttpException("Failed to extract EDI payload: invalid content type '"
                            + mimeEntity.getContentType() + "' for AS2 compressed and signed entity");
                }
                break;
            }
            default:
                throw new HttpException("Failed to extract EDI payload: invalid content type '"
                        + contentType.getMimeType() + "' for AS2 compressed entity");
        }

        return ediEntity;
    }

    public static class DecrpytingAndSigningInfo {
        private final Certificate[] validateSigningCertificateChain;
        private final PrivateKey decryptingPrivateKey;

        public DecrpytingAndSigningInfo(
                Certificate[] validateSigningCertificateChain, PrivateKey decryptingPrivateKey) {
            this.validateSigningCertificateChain = validateSigningCertificateChain;
            this.decryptingPrivateKey = decryptingPrivateKey;
        }

        public Certificate[] getValidateSigningCertificateChain() {
            return validateSigningCertificateChain;
        }

        public PrivateKey getDecryptingPrivateKey() {
            return decryptingPrivateKey;
        }
    }
}
