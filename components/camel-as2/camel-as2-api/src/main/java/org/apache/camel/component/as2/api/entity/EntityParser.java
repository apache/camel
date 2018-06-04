/**
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

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.component.as2.api.AS2Charset;
import org.apache.camel.component.as2.api.AS2Header;
import org.apache.camel.component.as2.api.AS2MimeType;
import org.apache.camel.component.as2.api.io.AS2SessionInputBuffer;
import org.apache.camel.component.as2.api.util.AS2HeaderUtils;
import org.apache.camel.component.as2.api.util.ContentTypeUtils;
import org.apache.camel.component.as2.api.util.DispositionNotificationContentUtils;
import org.apache.camel.component.as2.api.util.EntityUtils;
import org.apache.camel.component.as2.api.util.HttpMessageUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpMessage;
import org.apache.http.ParseException;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.io.AbstractMessageParser;
import org.apache.http.impl.io.HttpTransportMetricsImpl;
import org.apache.http.message.BasicLineParser;
import org.apache.http.message.LineParser;
import org.apache.http.message.ParserCursor;
import org.apache.http.util.Args;
import org.apache.http.util.CharArrayBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EntityParser {

    private static final Logger LOG = LoggerFactory.getLogger(EntityParser.class);

    private static final int DEFAULT_BUFFER_SIZE = 8 * 1024;

    private static final String APPLICATION_EDI_CONTENT_TYPE_PREFIX = "application/edi";


    private EntityParser() {
    }

    public static boolean isBoundaryCloseDelimiter(final CharArrayBuffer buffer, ParserCursor cursor, String boundary) {
        Args.notNull(buffer, "Buffer");
        Args.notNull(boundary, "Boundary");

        String boundaryCloseDelimiter = "--" + boundary + "--"; // boundary
                                                                // close-delimiter
                                                                // - RFC2046
                                                                // 5.1.1

        if (cursor == null) {
            cursor = new ParserCursor(0, boundaryCloseDelimiter.length());
        }

        int indexFrom = cursor.getPos();
        int indexTo = cursor.getUpperBound();

        if ((indexFrom + boundaryCloseDelimiter.length()) > indexTo) {
            return false;
        }

        for (int i = indexFrom; i < indexTo; ++i) {
            if (buffer.charAt(i) != boundaryCloseDelimiter.charAt(i)) {
                return false;
            }
        }

        return true;
    }

    public static boolean isBoundaryDelimiter(final CharArrayBuffer buffer, ParserCursor cursor, String boundary) {
        Args.notNull(buffer, "Buffer");
        Args.notNull(boundary, "Boundary");

        String boundaryDelimiter = "--" + boundary; // boundary delimiter -
                                                    // RFC2046 5.1.1

        if (cursor == null) {
            cursor = new ParserCursor(0, boundaryDelimiter.length());
        }

        int indexFrom = cursor.getPos();
        int indexTo = cursor.getUpperBound();

        if ((indexFrom + boundaryDelimiter.length()) > indexTo) {
            return false;
        }

        for (int i = indexFrom; i < indexTo; ++i) {
            if (buffer.charAt(i) != boundaryDelimiter.charAt(i)) {
                return false;
            }
        }

        return true;
    }

    public static void skipPreambleAndStartBoundary(AS2SessionInputBuffer inbuffer, String boundary)
            throws HttpException {

        boolean foundStartBoundary;
        try {
            foundStartBoundary = false;
            CharArrayBuffer lineBuffer = new CharArrayBuffer(1024);
            while (inbuffer.readLine(lineBuffer) != -1) {
                final ParserCursor cursor = new ParserCursor(0, lineBuffer.length());
                if (isBoundaryDelimiter(lineBuffer, cursor, boundary)) {
                    foundStartBoundary = true;
                    break;
                }
                lineBuffer.clear();
            }
        } catch (Exception e) {
            throw new HttpException("Failed to read start boundary for body part", e);
        }

        if (!foundStartBoundary) {
            throw new HttpException("Failed to find start boundary for body part");
        }

    }

    public static void skipToBoundary(AS2SessionInputBuffer inbuffer, String boundary)
            throws HttpException {

        boolean foundEndBoundary;
        try {
            foundEndBoundary = false;
            CharArrayBuffer lineBuffer = new CharArrayBuffer(1024);
            while (inbuffer.readLine(lineBuffer) != -1) {
                final ParserCursor cursor = new ParserCursor(0, lineBuffer.length());
                if (isBoundaryDelimiter(lineBuffer, cursor, boundary)) {
                    foundEndBoundary = true;
                    break;
                }
                lineBuffer.clear();
            }
        } catch (Exception e) {
            throw new HttpException("Failed to read start boundary for body part", e);
        }

        if (!foundEndBoundary) {
            throw new HttpException("Failed to find start boundary for body part");
        }

    }

    public static void parseMultipartSignedEntity(HttpMessage message)
            throws HttpException {
        MultipartSignedEntity multipartSignedEntity = null;
        HttpEntity entity = Args.notNull(EntityUtils.getMessageEntity(message), "message entity");

        if (entity instanceof MultipartSignedEntity) {
            return;
        }

        Args.check(entity.isStreaming(), "Entity is not streaming");

        try {

            // Determine and validate the Content Type
            Header contentTypeHeader = entity.getContentType();
            if (contentTypeHeader == null) {
                throw new HttpException("Content-Type header is missing");
            }
            ContentType contentType = ContentType.parse(entity.getContentType().getValue());
            if (!contentType.getMimeType().equals(AS2MimeType.MULTIPART_SIGNED)) {
                throw new HttpException("Entity has invalid MIME type '" + contentType.getMimeType() + "'");
            }

            // Determine Charset
            String charsetName = AS2Charset.US_ASCII;
            Charset charset = contentType.getCharset();
            if (charset != null) {
                charsetName = charset.name();
            }

            // Determine content transfer encoding
            String contentTransferEncoding = HttpMessageUtils.getHeaderValue(message, AS2Header.CONTENT_TRANSFER_ENCODING);

            AS2SessionInputBuffer inbuffer = new AS2SessionInputBuffer(new HttpTransportMetricsImpl(), DEFAULT_BUFFER_SIZE);
            inbuffer.bind(entity.getContent());

            // Get Boundary Value
            String boundary = HttpMessageUtils.getBoundaryParameterValue(message, AS2Header.CONTENT_TYPE);
            if (boundary == null) {
                throw new HttpException("Failed to retrive boundary value");
            }

            multipartSignedEntity = parseMultipartSignedEntityBody(inbuffer, boundary, charsetName, contentTransferEncoding);
            multipartSignedEntity.setMainBody(true);

            EntityUtils.setMessageEntity(message, multipartSignedEntity);

        } catch (HttpException e) {
            throw e;
        } catch (Exception e) {
            throw new HttpException("Failed to parse entity content", e);
        }
    }

    public static void parseApplicationEDIEntity(HttpMessage message) throws HttpException {
        ApplicationEDIEntity applicationEDIEntity = null;
        HttpEntity entity = Args.notNull(EntityUtils.getMessageEntity(message), "message entity");

        if (entity instanceof ApplicationEDIEntity) {
            return;
        }

        Args.check(entity.isStreaming(), "Entity is not streaming");

        try {

            // Determine and validate the Content Type
            Header contentTypeHeader = entity.getContentType();
            if (contentTypeHeader == null) {
                throw new HttpException("Content-Type header is missing");
            }
            ContentType contentType = ContentType.parse(entity.getContentType().getValue());
            if (!contentType.getMimeType().startsWith(EntityParser.APPLICATION_EDI_CONTENT_TYPE_PREFIX)) {
                throw new HttpException("Entity has invalid MIME type '" + contentType.getMimeType() + "'");
            }

            // Determine Transfer Encoding
            Header transferEncoding = entity.getContentEncoding();
            String contentTransferEncoding = transferEncoding == null ? null : transferEncoding.getValue();

            AS2SessionInputBuffer inBuffer = new AS2SessionInputBuffer(new HttpTransportMetricsImpl(), 8 * 1024);
            inBuffer.bind(entity.getContent());

            applicationEDIEntity = parseEDIEntityBody(inBuffer, null, contentType, contentTransferEncoding);
            applicationEDIEntity.setMainBody(true);

            EntityUtils.setMessageEntity(message, applicationEDIEntity);
        } catch (HttpException e) {
            throw e;
        } catch (Exception e) {
            throw new HttpException("Failed to parse entity content", e);
        }
    }

    public static void parseMessageDispositionNotificationReportEntity(HttpMessage message)
            throws HttpException {
        DispositionNotificationMultipartReportEntity dispositionNotificationMultipartReportEntity = null;
        HttpEntity entity = Args.notNull(EntityUtils.getMessageEntity(message), "message entity");

        if (entity instanceof DispositionNotificationMultipartReportEntity) {
            return;
        }

        Args.check(entity.isStreaming(), "Entity is not streaming");

        try {

            // Determine and validate the Content Type
            Header contentTypeHeader = entity.getContentType();
            if (contentTypeHeader == null) {
                throw new HttpException("Content-Type header is missing");
            }
            ContentType contentType = ContentType.parse(entity.getContentType().getValue());
            if (!contentType.getMimeType().equals(AS2MimeType.MULTIPART_REPORT)) {
                throw new HttpException("Entity has invalid MIME type '" + contentType.getMimeType() + "'");
            }

            // Determine Charset
            String charsetName = AS2Charset.US_ASCII;
            Charset charset = contentType.getCharset();
            if (charset != null) {
                charsetName = charset.name();
            }

            // Determine content transfer encoding
            String contentTransferEncoding = HttpMessageUtils.getHeaderValue(message, AS2Header.CONTENT_TRANSFER_ENCODING);

            AS2SessionInputBuffer inbuffer = new AS2SessionInputBuffer(new HttpTransportMetricsImpl(), 8 * 1024);
            inbuffer.bind(entity.getContent());

            // Get Boundary Value
            String boundary = HttpMessageUtils.getBoundaryParameterValue(message, AS2Header.CONTENT_TYPE);
            if (boundary == null) {
                throw new HttpException("Failed to retrive boundary value");
            }

            dispositionNotificationMultipartReportEntity = parseMultipartReportEntityBody(inbuffer, boundary, charsetName, contentTransferEncoding);

            EntityUtils.setMessageEntity(message, dispositionNotificationMultipartReportEntity);

        } catch (HttpException e) {
            throw e;
        } catch (Exception e) {
            throw new HttpException("Failed to parse entity content", e);
        }
    }

    public static void parseAS2MessageEntity(HttpMessage message) throws HttpException {
        if (EntityUtils.hasEntity(message)) {
            String contentTypeStr =  HttpMessageUtils.getHeaderValue(message, AS2Header.CONTENT_TYPE);
            if (contentTypeStr != null) {
                ContentType contentType;
                try {
                    contentType = ContentType.parse(contentTypeStr);
                } catch (Exception e) {
                    LOG.debug("Failed to get content type of message", e);
                    return;
                }
                switch (contentType.getMimeType().toLowerCase()) {
                case AS2MimeType.APPLICATION_EDIFACT:
                case AS2MimeType.APPLICATION_EDI_X12:
                case AS2MimeType.APPLICATION_EDI_CONSENT:
                    parseApplicationEDIEntity(message);
                    break;
                case AS2MimeType.MULTIPART_SIGNED:
                    parseMultipartSignedEntity(message);
                    break;
                case AS2MimeType.APPLICATION_PKCS7_MIME:
                    break;
                case AS2MimeType.MULTIPART_REPORT:
                    parseMessageDispositionNotificationReportEntity(message);
                    break;
                default:
                    break;
                }
            }
        }
    }

    public static MultipartSignedEntity parseMultipartSignedEntityBody(AS2SessionInputBuffer inbuffer,
                                                                       String boundary,
                                                                       String charsetName,
                                                                       String contentTransferEncoding)
            throws ParseException {
        CharsetDecoder previousDecoder = inbuffer.getCharsetDecoder();

        try {

            if (charsetName == null) {
                charsetName = AS2Charset.US_ASCII;
            }
            Charset charset = Charset.forName(charsetName);
            CharsetDecoder charsetDecoder = charset.newDecoder();

            inbuffer.setCharsetDecoder(charsetDecoder);

            MultipartSignedEntity multipartSignedEntity = new MultipartSignedEntity(boundary, false);

            // Skip Preamble and Start Boundary line
            skipPreambleAndStartBoundary(inbuffer, boundary);

            //
            // Parse Signed Entity Part
            //

            // Read Text Report Body Part Headers
            Header[] headers = AbstractMessageParser.parseHeaders(inbuffer, -1, -1, BasicLineParser.INSTANCE,
                    new ArrayList<CharArrayBuffer>());

            // Get Content-Type and Content-Transfer-Encoding
            ContentType signedEntityContentType = null;
            String signedEntityContentTransferEncoding = null;
            for (Header header : headers) {
                switch (header.getName()) {
                case AS2Header.CONTENT_TYPE:
                    signedEntityContentType = ContentType.parse(header.getValue());
                    break;
                case AS2Header.CONTENT_TRANSFER_ENCODING:
                    signedEntityContentTransferEncoding = header.getValue();
                    break;
                default:
                    continue;
                }
            }
            if (signedEntityContentType == null) {
                throw new HttpException("Failed to find Content-Type header in signed entity body part");
            }

            MimeEntity signedEntity = parseEntityBody(inbuffer, boundary, signedEntityContentType, signedEntityContentTransferEncoding, headers);
            signedEntity.removeAllHeaders();
            signedEntity.setHeaders(headers);
            multipartSignedEntity.addPart(signedEntity);
            
            //
            // End Signed Entity Part

            //
            // Parse Signature Body Part
            //

            // Read Signature Body Part Headers
            headers = AbstractMessageParser.parseHeaders(inbuffer, -1, -1, BasicLineParser.INSTANCE,
                    new ArrayList<CharArrayBuffer>());

            // Get Content-Type and Content-Transfer-Encoding
            ContentType signatureContentType = null;
            String signatureContentTransferEncoding = null;
            for (Header header : headers) {
                switch (header.getName()) {
                case AS2Header.CONTENT_TYPE:
                    signatureContentType = ContentType.parse(header.getValue());
                    break;
                case AS2Header.CONTENT_TRANSFER_ENCODING:
                    signatureContentTransferEncoding = header.getValue();
                    break;
                default:
                    continue;
                }
            }
            if (signatureContentType == null) {
                throw new HttpException("Failed to find Content-Type header in signature body part");
            }
            if (!ContentTypeUtils.isPkcs7SignatureType(signatureContentType)) {
                throw new HttpException(
                        "Invalid content type '" + signatureContentType.getMimeType() + "' for signature body part");
            }

            ApplicationPkcs7SignatureEntity applicationPkcs7SignatureEntity = parseApplicationPkcs7SignatureEntityBody(inbuffer, boundary, signatureContentType, signatureContentTransferEncoding);
            applicationPkcs7SignatureEntity.removeAllHeaders();
            applicationPkcs7SignatureEntity.setHeaders(headers);
            multipartSignedEntity.addPart(applicationPkcs7SignatureEntity);

            //
            // End Signature Body Part

            ContentType contentType = ContentType.create(AS2MimeType.MULTIPART_SIGNED, charset);
            multipartSignedEntity.setContentType(contentType);
            multipartSignedEntity.setContentTransferEncoding(contentTransferEncoding);
            return multipartSignedEntity;

        } catch (Exception e) {
            ParseException parseException = new ParseException("failed to parse text entity");
            parseException.initCause(e);
            throw parseException;
        } finally {
            inbuffer.setCharsetDecoder(previousDecoder);
        }
    }

    public static DispositionNotificationMultipartReportEntity parseMultipartReportEntityBody(AS2SessionInputBuffer inbuffer,
                                                                                                                 String boundary,
                                                                                                                 String charsetName,
                                                                                                                 String contentTransferEncoding)
            throws ParseException {
        CharsetDecoder previousDecoder = inbuffer.getCharsetDecoder();

        try {

            if (charsetName == null) {
                charsetName = AS2Charset.US_ASCII;
            }
            Charset charset = Charset.forName(charsetName);
            CharsetDecoder charsetDecoder = charset.newDecoder();

            inbuffer.setCharsetDecoder(charsetDecoder);

            DispositionNotificationMultipartReportEntity dispositionNotificationMultipartReportEntity = new DispositionNotificationMultipartReportEntity(boundary, false);

            // Skip Preamble and Start Boundary line
            skipPreambleAndStartBoundary(inbuffer, boundary);

            //
            // Parse Text Report Body Part
            //

            // Read Text Report Body Part Headers
            Header[] headers = AbstractMessageParser.parseHeaders(inbuffer, -1, -1, BasicLineParser.INSTANCE,
                    new ArrayList<CharArrayBuffer>());

            // Get Content-Type and Content-Transfer-Encoding
            ContentType textReportContentType = null;
            String textReportContentTransferEncoding = null;
            for (Header header : headers) {
                switch (header.getName()) {
                case AS2Header.CONTENT_TYPE:
                    textReportContentType = ContentType.parse(header.getValue());
                    break;
                case AS2Header.CONTENT_TRANSFER_ENCODING:
                    textReportContentTransferEncoding = header.getValue();
                    break;
                default:
                    continue;
                }
            }
            if (textReportContentType == null) {
                throw new HttpException("Failed to find Content-Type header in EDI message body part");
            }
            if (!textReportContentType.getMimeType().equalsIgnoreCase(AS2MimeType.TEXT_PLAIN)) {
                throw new HttpException("Invalid content type '" + textReportContentType.getMimeType()
                        + "' for first body part of disposition notification");
            }

            String textReportCharsetName = textReportContentType.getCharset() == null ? AS2Charset.US_ASCII : textReportContentType.getCharset().name();
            TextPlainEntity textReportEntity = parseTextPlainEntityBody(inbuffer, boundary, textReportCharsetName, textReportContentTransferEncoding);
            textReportEntity.setHeaders(headers);
            dispositionNotificationMultipartReportEntity.addPart(textReportEntity);

            //
            // End Text Report Body Part

            //
            // Parse Disposition Notification Body Part
            //

            // Read Disposition Notification Body Part Headers
            headers = AbstractMessageParser.parseHeaders(inbuffer, -1, -1, BasicLineParser.INSTANCE,
                    new ArrayList<CharArrayBuffer>());

            // Get Content-Type and Content-Transfer-Encoding
            ContentType dispositionNotificationContentType = null;
            String dispositionNotificationContentTransferEncoding = null;
            for (Header header : headers) {
                switch (header.getName()) {
                case AS2Header.CONTENT_TYPE:
                    dispositionNotificationContentType = ContentType.parse(header.getValue());
                    break;
                case AS2Header.CONTENT_TRANSFER_ENCODING:
                    dispositionNotificationContentTransferEncoding = header.getValue();
                    break;
                default:
                    continue;
                }
            }
            if (dispositionNotificationContentType == null) {
                throw new HttpException("Failed to find Content-Type header in body part");
            }
            if (!dispositionNotificationContentType.getMimeType()
                    .equalsIgnoreCase(AS2MimeType.MESSAGE_DISPOSITION_NOTIFICATION)) {
                throw new HttpException("Invalid content type '" + dispositionNotificationContentType.getMimeType()
                        + "' for second body part of disposition notification");
            }

            String dispositionNotificationCharsetName = dispositionNotificationContentType.getCharset() == null ? AS2Charset.US_ASCII : dispositionNotificationContentType.getCharset().name();
            AS2MessageDispositionNotificationEntity messageDispositionNotificationEntity = parseMessageDispositionNotificationEntityBody(
                    inbuffer, boundary, dispositionNotificationCharsetName, dispositionNotificationContentTransferEncoding);
            messageDispositionNotificationEntity.setHeaders(headers);
            dispositionNotificationMultipartReportEntity.addPart(messageDispositionNotificationEntity);

            //
            // End Disposition Notification Body Part

            dispositionNotificationMultipartReportEntity.setContentTransferEncoding(contentTransferEncoding);
            return dispositionNotificationMultipartReportEntity;
        } catch (Exception e) {
            ParseException parseException = new ParseException("failed to parse text entity");
            parseException.initCause(e);
            throw parseException;
        } finally {
            inbuffer.setCharsetDecoder(previousDecoder);
        }

    }

    public static TextPlainEntity parseTextPlainEntityBody(AS2SessionInputBuffer inbuffer,
                                                       String boundary,
                                                       String charsetName,
                                                       String contentTransferEncoding)
            throws ParseException {
        CharsetDecoder previousDecoder = inbuffer.getCharsetDecoder();

        try {

            if (charsetName == null) {
                charsetName = AS2Charset.US_ASCII;
            }
            Charset charset = Charset.forName(charsetName);
            CharsetDecoder charsetDecoder = charset.newDecoder();

            inbuffer.setCharsetDecoder(charsetDecoder);

            String text = parseBodyPartText(inbuffer, boundary);
            if (contentTransferEncoding != null) {
                text = EntityUtils.decode(text, charset, contentTransferEncoding);
            }
            return new TextPlainEntity(text, charsetName, contentTransferEncoding, false);
        } catch (Exception e) {
            ParseException parseException = new ParseException("failed to parse text entity");
            parseException.initCause(e);
            throw parseException;
        } finally {
            inbuffer.setCharsetDecoder(previousDecoder);
        }
    }

    public static AS2MessageDispositionNotificationEntity parseMessageDispositionNotificationEntityBody(AS2SessionInputBuffer inbuffer,
                                                                                              String boundary,
                                                                                              String charsetName,
                                                                                              String contentTransferEncoding)
            throws ParseException {
        CharsetDecoder previousDecoder = inbuffer.getCharsetDecoder();

        try {

            if (charsetName == null) {
                charsetName = AS2Charset.US_ASCII;
            }
            Charset charset = Charset.forName(charsetName);
            CharsetDecoder charsetDecoder = charset.newDecoder();

            inbuffer.setCharsetDecoder(charsetDecoder);

            List<CharArrayBuffer> dispositionNotificationFields = parseBodyPartFields(inbuffer, boundary,
                    BasicLineParser.INSTANCE, new ArrayList<CharArrayBuffer>());

            AS2MessageDispositionNotificationEntity as2MessageDispositionNotificationEntity = DispositionNotificationContentUtils.parseDispositionNotification(dispositionNotificationFields);
            ContentType contentType = ContentType.create(AS2MimeType.MESSAGE_DISPOSITION_NOTIFICATION, charset);
            as2MessageDispositionNotificationEntity.setContentType(contentType);
            return as2MessageDispositionNotificationEntity;
        } catch (Exception e) {
            ParseException parseException = new ParseException("failed to parse MDN entity");
            parseException.initCause(e);
            throw parseException;
        } finally {
            inbuffer.setCharsetDecoder(previousDecoder);
        }
    }

    public static MimeEntity parseEntityBody(AS2SessionInputBuffer inbuffer,
                                             String boundary,
                                             ContentType entityContentType,
                                             String contentTransferEncoding,
                                             Header[] headers)
            throws ParseException {
        CharsetDecoder previousDecoder = inbuffer.getCharsetDecoder();

        try {
            Charset charset = entityContentType.getCharset();
            if (charset == null) {
                charset = Charset.forName(AS2Charset.US_ASCII);
            }
            CharsetDecoder charsetDecoder = charset.newDecoder();

            inbuffer.setCharsetDecoder(charsetDecoder);

            MimeEntity entity = null;
            switch (entityContentType.getMimeType().toLowerCase()) {
            case AS2MimeType.APPLICATION_EDIFACT:
            case AS2MimeType.APPLICATION_EDI_X12:
            case AS2MimeType.APPLICATION_EDI_CONSENT:
                entity = parseEDIEntityBody(inbuffer, boundary, entityContentType, contentTransferEncoding);
                break;
            case AS2MimeType.MULTIPART_SIGNED:
                String multipartSignedBoundary = AS2HeaderUtils.getBoundaryParameterValue(headers,
                        AS2Header.CONTENT_TYPE);
                entity = parseMultipartSignedEntityBody(inbuffer, multipartSignedBoundary, charset.name(),
                        contentTransferEncoding);
                skipToBoundary(inbuffer, boundary);
                break;
            case AS2MimeType.MESSAGE_DISPOSITION_NOTIFICATION:
                entity = parseMessageDispositionNotificationEntityBody(inbuffer, boundary, charset.name(),
                        contentTransferEncoding);
                break;
            case AS2MimeType.MULTIPART_REPORT:
                String multipartReportBoundary = AS2HeaderUtils.getBoundaryParameterValue(headers,
                        AS2Header.CONTENT_TYPE);
                entity = parseMultipartReportEntityBody(inbuffer, multipartReportBoundary, charset.name(),
                        contentTransferEncoding);
                skipToBoundary(inbuffer, boundary);
                break;
            case AS2MimeType.TEXT_PLAIN:
                entity = parseTextPlainEntityBody(inbuffer, boundary, charset.name(), contentTransferEncoding);
                break;
            case AS2MimeType.APPLICATION_PKCS7_SIGNATURE:
                entity = parseApplicationPkcs7SignatureEntityBody(inbuffer, boundary, entityContentType,
                        contentTransferEncoding);
                break;
            default:
                break;
            }

            return entity;

        } catch (Exception e) {
            ParseException parseException = new ParseException("failed to parse EDI entity");
            parseException.initCause(e);
            throw parseException;
        } finally {
            inbuffer.setCharsetDecoder(previousDecoder);
        }

    }

    public static ApplicationEDIEntity parseEDIEntityBody(AS2SessionInputBuffer inbuffer,
                                                          String boundary,
                                                          ContentType ediMessageContentType,
                                                          String contentTransferEncoding)
            throws ParseException {
        CharsetDecoder previousDecoder = inbuffer.getCharsetDecoder();

        try {
            Charset charset = ediMessageContentType.getCharset();
            if (charset == null) {
                charset = Charset.forName(AS2Charset.US_ASCII);
            }
            CharsetDecoder charsetDecoder = charset.newDecoder();

            inbuffer.setCharsetDecoder(charsetDecoder);

            String ediMessageBodyPartContent = parseBodyPartText(inbuffer, boundary);
            if (contentTransferEncoding != null) {
                ediMessageBodyPartContent = EntityUtils.decode(ediMessageBodyPartContent, charset, contentTransferEncoding);
            }
            ApplicationEDIEntity applicationEDIEntity = EntityUtils.createEDIEntity(ediMessageBodyPartContent,
                    ediMessageContentType, contentTransferEncoding, false);

            return applicationEDIEntity;
        } catch (Exception e) {
            ParseException parseException = new ParseException("failed to parse EDI entity");
            parseException.initCause(e);
            throw parseException;
        } finally {
            inbuffer.setCharsetDecoder(previousDecoder);
        }
    }

    public static ApplicationPkcs7SignatureEntity parseApplicationPkcs7SignatureEntityBody(AS2SessionInputBuffer inbuffer,
                                                                                           String boundary,
                                                                                           ContentType contentType,
                                                                                           String contentTransferEncoding) throws ParseException {

        CharsetDecoder previousDecoder = inbuffer.getCharsetDecoder();

        try {
            Charset charset = contentType.getCharset();
            if (charset == null) {
                charset = Charset.forName(AS2Charset.US_ASCII);
            }
            CharsetDecoder charsetDecoder = charset.newDecoder();

            inbuffer.setCharsetDecoder(charsetDecoder);

            String pkcs7SignatureBodyContent = parseBodyPartText(inbuffer, boundary);
            
            byte[] signature = EntityUtils.decode(pkcs7SignatureBodyContent.getBytes(charset), contentTransferEncoding);

            String charsetName = charset.toString();
            ApplicationPkcs7SignatureEntity applicationPkcs7SignatureEntity = new ApplicationPkcs7SignatureEntity(
                    charsetName, contentTransferEncoding, signature, false);
            return applicationPkcs7SignatureEntity;
        } catch (Exception e) {
            ParseException parseException = new ParseException("failed to parse PKCS7 Signature entity");
            parseException.initCause(e);
            throw parseException;
        } finally {
            inbuffer.setCharsetDecoder(previousDecoder);
        }
    }

    public static String parseBodyPartText(final AS2SessionInputBuffer inbuffer,
                                           final String boundary)
            throws IOException {
        CharArrayBuffer buffer = new CharArrayBuffer(DEFAULT_BUFFER_SIZE);
        CharArrayBuffer line = new CharArrayBuffer(DEFAULT_BUFFER_SIZE);
        while (true) {
            final int l = inbuffer.readLine(line);
            if (l == -1) {
                break;
            }

            if (boundary != null && isBoundaryDelimiter(line, null, boundary)) {
                // remove last CRLF from buffer which belongs to boundary
                int length = buffer.length();
                buffer.setLength(length - 2);
                break;
            }

            buffer.append(line);
            if (inbuffer.isLastLineReadTerminatedByLineFeed()) {
                buffer.append("\r\n");
            }
            line.clear();
        }

        return buffer.toString();
    }

    public static List<CharArrayBuffer> parseBodyPartFields(final AS2SessionInputBuffer inbuffer,
                                                           final String boundary,
                                                           final LineParser parser,
                                                           final List<CharArrayBuffer> fields)
            throws IOException {
        Args.notNull(parser, "parser");
        Args.notNull(fields, "fields");
        CharArrayBuffer current = null;
        CharArrayBuffer previous = null;
        while (true) {

            if (current == null) {
                current = new CharArrayBuffer(64);
            }

            final int l = inbuffer.readLine(current);
            if (l == -1 || current.length() < 1) {
                break;
            }

            if (boundary != null && isBoundaryDelimiter(current, null, boundary)) {
                break;
            }

            // check if current line part of folded headers
            if ((current.charAt(0) == ' ' || current.charAt(0) == '\t') && previous != null) {
                // we have continuation of folded header : append value
                int i = 0;
                while (i < current.length()) {
                    final char ch = current.charAt(i);
                    if (ch != ' ' && ch != '\t') {
                        break;
                    }
                    i++;
                }

                // Just append current line to previous line
                previous.append(' ');
                previous.append(current, i, current.length() - i);

                // leave current line buffer for reuse for next header
                current.clear();
            } else {
                fields.add(current);
                previous = current;
                current = null;
            }
        }
        return fields;
    }
}
