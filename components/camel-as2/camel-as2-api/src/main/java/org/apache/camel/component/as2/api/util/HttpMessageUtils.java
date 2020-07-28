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

import org.apache.camel.component.as2.api.AS2Header;
import org.apache.camel.component.as2.api.AS2MimeType;
import org.apache.camel.component.as2.api.entity.ApplicationEDIEntity;
import org.apache.camel.component.as2.api.entity.ApplicationPkcs7MimeCompressedDataEntity;
import org.apache.camel.component.as2.api.entity.ApplicationPkcs7MimeEnvelopedDataEntity;
import org.apache.camel.component.as2.api.entity.EntityParser;
import org.apache.camel.component.as2.api.entity.MimeEntity;
import org.apache.camel.component.as2.api.entity.MultipartSignedEntity;
import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpMessage;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.entity.ContentType;
import org.apache.http.io.SessionInputBuffer;
import org.apache.http.util.Args;
import org.apache.http.util.CharArrayBuffer;
import org.bouncycastle.cms.jcajce.ZlibExpanderProvider;

public final class HttpMessageUtils {

    private HttpMessageUtils() {
    }

    public static String getHeaderValue(HttpMessage message, String headerName) {
        Header header = message.getFirstHeader(headerName);
        return header == null ? null : header.getValue();
    }

    public static void setHeaderValue(HttpMessage message, String headerName, String headerValue) {
        Args.notNull(message, "message");
        Args.notNull(headerName, "headerName");
        if (headerValue == null) {
            message.removeHeaders(headerName);
        } else {
            message.setHeader(headerName, headerValue);
        }
    }

    public static <T> T getEntity(HttpMessage message, Class<T> type) {
        Args.notNull(message, "message");
        Args.notNull(type, "type");
        if (message instanceof HttpEntityEnclosingRequest) {
            HttpEntity entity = ((HttpEntityEnclosingRequest) message).getEntity();
            if (entity != null && type.isInstance(entity)) {
                return type.cast(entity);
            }
        } else if (message instanceof HttpResponse) {
            HttpEntity entity = ((HttpResponse) message).getEntity();
            if (entity != null && type.isInstance(entity)) {
                type.cast(entity);
            }
        }
        return null;
    }

    public static String parseBodyPartContent(SessionInputBuffer inBuffer, String boundary) throws HttpException {
        try {
            CharArrayBuffer bodyPartContentBuffer = new CharArrayBuffer(1024);
            CharArrayBuffer lineBuffer = new CharArrayBuffer(1024);
            boolean foundMultipartEndBoundary = false;
            while (inBuffer.readLine(lineBuffer) != -1) {
                if (EntityParser.isBoundaryDelimiter(lineBuffer, null, boundary)) {
                    foundMultipartEndBoundary = true;
                    // Remove previous line ending: this is associated with
                    // boundary
                    bodyPartContentBuffer.setLength(bodyPartContentBuffer.length() - 2);
                    lineBuffer.clear();
                    break;
                }
                lineBuffer.append("\r\n"); // add line delimiter
                bodyPartContentBuffer.append(lineBuffer);
                lineBuffer.clear();
            }
            if (!foundMultipartEndBoundary) {
                throw new HttpException("Failed to find end boundary delimiter for body part");
            }

            return bodyPartContentBuffer.toString();
        } catch (HttpException e) {
            throw e;
        } catch (Exception e) {
            throw new HttpException("Failed to parse body part content", e);
        }
    }

    public static String getParameterValue(HttpMessage message, String headerName, String parameterName) {
        Args.notNull(message, "message");
        Args.notNull(headerName, "headerName");
        Args.notNull(parameterName, "parameterName");
        Header header = message.getFirstHeader(headerName);
        if (header == null) {
            return null;
        }
        for (HeaderElement headerElement : header.getElements()) {
            for (NameValuePair nameValuePair : headerElement.getParameters()) {
                if (nameValuePair.getName().equalsIgnoreCase(parameterName)) {
                    return nameValuePair.getValue();
                }
            }
        }
        return null;
    }

    public static ApplicationEDIEntity extractEdiPayload(HttpMessage message, PrivateKey privateKey) throws HttpException {

        String contentTypeString = getHeaderValue(message, AS2Header.CONTENT_TYPE);
        if (contentTypeString == null) {
            throw new HttpException("Failed to create MIC: content type missing from request");
        }
        ContentType contentType = ContentType.parse(contentTypeString);

        EntityParser.parseAS2MessageEntity(message);
        ApplicationEDIEntity ediEntity = null;
        switch (contentType.getMimeType().toLowerCase()) {
            case AS2MimeType.APPLICATION_EDIFACT:
            case AS2MimeType.APPLICATION_EDI_X12:
            case AS2MimeType.APPLICATION_EDI_CONSENT: {
                ediEntity = getEntity(message, ApplicationEDIEntity.class);
                break;
            }
            case AS2MimeType.MULTIPART_SIGNED: {
                MultipartSignedEntity multipartSignedEntity = getEntity(message,
                        MultipartSignedEntity.class);
                MimeEntity mimeEntity = multipartSignedEntity.getSignedDataEntity();
                if (mimeEntity instanceof ApplicationEDIEntity) {
                    ediEntity = (ApplicationEDIEntity) mimeEntity;
                } else {
                    throw new HttpException(
                            "Failed to extract EDI payload: invalid content type '" + mimeEntity.getContentTypeValue()
                                            + "' for AS2 compressed and signed message");
                }
                break;
            }
            case AS2MimeType.APPLICATION_PKCS7_MIME: {
                switch (contentType.getParameter("smime-type")) {
                    case "compressed-data": {
                        ApplicationPkcs7MimeCompressedDataEntity compressedDataEntity
                                = getEntity(message, ApplicationPkcs7MimeCompressedDataEntity.class);
                        ediEntity = extractEdiPayloadFromCompressedEntity(compressedDataEntity);
                        break;
                    }
                    case "enveloped-data": {
                        if (privateKey == null) {
                            throw new HttpException(
                                    "Failed to extract EDI payload: private key can not be null for AS2 enveloped message");
                        }
                        ApplicationPkcs7MimeEnvelopedDataEntity envelopedDataEntity
                                = getEntity(message, ApplicationPkcs7MimeEnvelopedDataEntity.class);
                        ediEntity = extractEdiPayloadFromEnvelopedEntity(envelopedDataEntity, privateKey);
                        break;
                    }
                    default:
                        throw new HttpException(
                                "Failed to extract EDI message: unknown " + AS2MimeType.APPLICATION_PKCS7_MIME + " smime-type: "
                                                + contentType.getParameter("smime-type"));
                }
                break;
            }
            default:
                throw new HttpException(
                        "Failed to extract EDI message: invalid content type '" + contentType.getMimeType()
                                        + "' for AS2 request message");
        }

        return ediEntity;

    }

    private static ApplicationEDIEntity extractEdiPayloadFromEnvelopedEntity(
            ApplicationPkcs7MimeEnvelopedDataEntity envelopedDataEntity, PrivateKey privateKey)
            throws HttpException {
        ApplicationEDIEntity ediEntity = null;

        MimeEntity entity = envelopedDataEntity.getEncryptedEntity(privateKey);
        String contentTypeString = entity.getContentTypeValue();
        if (contentTypeString == null) {
            throw new HttpException("Failed to extract EDI message: content type missing from encrypted entity");
        }
        ContentType contentType = ContentType.parse(contentTypeString);

        switch (contentType.getMimeType().toLowerCase()) {
            case AS2MimeType.APPLICATION_EDIFACT:
            case AS2MimeType.APPLICATION_EDI_X12:
            case AS2MimeType.APPLICATION_EDI_CONSENT: {
                ediEntity = (ApplicationEDIEntity) entity;
                break;
            }
            case AS2MimeType.MULTIPART_SIGNED: {
                MultipartSignedEntity multipartSignedEntity = (MultipartSignedEntity) entity;
                MimeEntity mimeEntity = multipartSignedEntity.getSignedDataEntity();
                if (mimeEntity instanceof ApplicationEDIEntity) {
                    ediEntity = (ApplicationEDIEntity) mimeEntity;
                } else {

                    throw new HttpException(
                            "Failed to extract EDI payload: invalid content type '" + mimeEntity.getContentTypeValue()
                                            + "' for AS2 compressed and signed entity");
                }
                break;
            }
            case AS2MimeType.APPLICATION_PKCS7_MIME: {
                if (!"compressed-data".equals(contentType.getParameter("smime-type"))) {
                    throw new HttpException(
                            "Failed to extract EDI payload: invalid mime type '" + contentType.getParameter("smime-type")
                                            + "' for AS2 enveloped entity");
                }
                ApplicationPkcs7MimeCompressedDataEntity compressedDataEntity
                        = (ApplicationPkcs7MimeCompressedDataEntity) entity;
                ediEntity = extractEdiPayloadFromCompressedEntity(compressedDataEntity);
                break;
            }
            default:
                throw new HttpException(
                        "Failed to extract EDI payload: invalid content type '" + contentType.getMimeType()
                                        + "' for AS2 enveloped entity");
        }

        return ediEntity;
    }

    public static ApplicationEDIEntity extractEdiPayloadFromCompressedEntity(
            ApplicationPkcs7MimeCompressedDataEntity compressedDataEntity)
            throws HttpException {
        ApplicationEDIEntity ediEntity = null;

        MimeEntity entity = compressedDataEntity.getCompressedEntity(new ZlibExpanderProvider());
        String contentTypeString = entity.getContentTypeValue();
        if (contentTypeString == null) {
            throw new HttpException("Failed to extract EDI payload: content type missing from compressed entity");
        }
        ContentType contentType = ContentType.parse(contentTypeString);

        switch (contentType.getMimeType().toLowerCase()) {
            case AS2MimeType.APPLICATION_EDIFACT:
            case AS2MimeType.APPLICATION_EDI_X12:
            case AS2MimeType.APPLICATION_EDI_CONSENT: {
                ediEntity = (ApplicationEDIEntity) entity;
                break;
            }
            case AS2MimeType.MULTIPART_SIGNED: {
                MultipartSignedEntity multipartSignedEntity = (MultipartSignedEntity) entity;
                MimeEntity mimeEntity = multipartSignedEntity.getSignedDataEntity();
                if (mimeEntity instanceof ApplicationEDIEntity) {
                    ediEntity = (ApplicationEDIEntity) mimeEntity;
                } else {

                    throw new HttpException(
                            "Failed to extract EDI payload: invalid content type '" + mimeEntity.getContentTypeValue()
                                            + "' for AS2 compressed and signed entity");
                }
                break;
            }
            default:
                throw new HttpException(
                        "Failed to extract EDI payload: invalid content type '" + contentType.getMimeType()
                                        + "' for AS2 compressed entity");
        }

        return ediEntity;
    }

}
