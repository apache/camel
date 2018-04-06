package org.apache.camel.component.as2.api.entity;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.component.as2.api.AS2CharSet;
import org.apache.camel.component.as2.api.AS2Header;
import org.apache.camel.component.as2.api.AS2MediaType;
import org.apache.camel.component.as2.api.AS2MimeType;
import org.apache.camel.component.as2.api.io.AS2SessionInputBuffer;
import org.apache.camel.component.as2.api.util.DispositionNotificationContentUtils;
import org.apache.camel.component.as2.api.util.EntityUtils;
import org.apache.camel.component.as2.api.util.HttpMessageUtils;
import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpMessage;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.ParseException;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.io.AbstractMessageParser;
import org.apache.http.impl.io.HttpTransportMetricsImpl;
import org.apache.http.impl.io.SessionInputBufferImpl;
import org.apache.http.message.BasicLineParser;
import org.apache.http.message.LineParser;
import org.apache.http.message.ParserCursor;
import org.apache.http.util.Args;
import org.apache.http.util.CharArrayBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EntityParser {
    
    private static final Logger LOG = LoggerFactory.getLogger(EntityParser.class);

    private static final int DEFAULT_BUFFER_SIZE = 8 * 1024;

    private static final String APPLICATION_EDI_CONTENT_TYPE_PREFIX = "application/edi";

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

    public static boolean isEDIMessageContentType(ContentType ediMessageContentType) {
        switch (ediMessageContentType.getMimeType().toLowerCase()) {
        case AS2MediaType.APPLICATION_EDIFACT:
            return true;
        case AS2MediaType.APPLICATION_EDI_X12:
            return true;
        case AS2MediaType.APPLICATION_EDI_CONSENT:
            return true;
        default:
            return false;
        }
    }

    public static boolean isPkcs7SignatureType(ContentType pcks7SignatureType) {
        switch (pcks7SignatureType.getMimeType().toLowerCase()) {
        case AS2MimeType.APPLICATION_PKCS7_SIGNATURE:
            return true;
        default:
            return false;
        }
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

    public static void skipPreambleAndStartBoundary(SessionInputBufferImpl inBuffer, String boundary)
            throws HttpException {

        boolean foundStartBoundary;
        try {
            foundStartBoundary = false;
            CharArrayBuffer lineBuffer = new CharArrayBuffer(1024);
            while (inBuffer.readLine(lineBuffer) != -1) {
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

    public static HttpEntity parseMultipartSignedEntity(HttpMessage message, HttpEntity entity, boolean isMainBody)
            throws HttpException {
        Args.notNull(entity, "Entity");
        MultipartSignedEntity multipartSignedEntity = null;
        Header[] headers = null;

        if (entity instanceof MultipartSignedEntity) {
            return entity;
        }

        Args.check(entity.isStreaming(), "Entity is not streaming");

        try {
            // Determine and validate the Content Type
            Header contentTypeHeader = entity.getContentType();
            if (contentTypeHeader == null) {
                throw new HttpException("Content-Type header is missing");
            }
            ContentType multipartSignedContentType = ContentType.parse(entity.getContentType().getValue());
            if (!multipartSignedContentType.getMimeType().equals(AS2MimeType.MULTIPART_SIGNED)) {
                throw new HttpException(
                        "Entity has invalid MIME type '" + multipartSignedContentType.getMimeType() + "'");
            }

            SessionInputBufferImpl inBuffer = new SessionInputBufferImpl(new HttpTransportMetricsImpl(), 8 * 1024);
            inBuffer.bind(entity.getContent());

            // Parse Headers
            if (!isMainBody) {
                headers = AbstractMessageParser.parseHeaders(inBuffer, -1, -1, BasicLineParser.INSTANCE,
                        new ArrayList<CharArrayBuffer>());
            }

            // Get Boundary Value
            String boundary = multipartSignedContentType.getParameter("boundary");
            if (boundary == null) {
                throw new HttpException("Failed to retrive boundary value");
            }

            multipartSignedEntity = new MultipartSignedEntity(boundary, true);
            if (headers != null) {
                multipartSignedEntity.setHeaders(headers);
            }

            //
            // Parse EDI Message Body Part
            //

            // Skip Preamble and Start Boundary line
            skipPreambleAndStartBoundary(inBuffer, boundary);

            // Read EDI Message Body Part Headers
            headers = AbstractMessageParser.parseHeaders(inBuffer, -1, -1, BasicLineParser.INSTANCE,
                    new ArrayList<CharArrayBuffer>());

            // Get Content-Type and Content-Transfer-Encoding
            ContentType ediMessageContentType = null;
            String ediMessageContentTransferEncoding = null;
            for (Header header : headers) {
                switch (header.getName()) {
                case AS2Header.CONTENT_TYPE:
                    ediMessageContentType = ContentType.parse(header.getValue());
                    break;
                case AS2Header.CONTENT_TRANSFER_ENCODING:
                    ediMessageContentTransferEncoding = header.getValue();
                    break;
                }
            }
            if (ediMessageContentType == null) {
                throw new HttpException("Failed to find Content-Type header in EDI message body part");
            }
            if (!isEDIMessageContentType(ediMessageContentType)) {
                throw new HttpException(
                        "Invalid content type '" + ediMessageContentType.getMimeType() + "' for EDI message body part");
            }

            // - Read EDI Message Body Part Content
            String ediMessageBodyPartContent = parseBodyPartContent(inBuffer, boundary);

            // Decode Content
            byte[] bytes = EntityUtils.decodeTransferEncodingOfBodyPartContent(ediMessageBodyPartContent, ediMessageContentType,
                    ediMessageContentTransferEncoding);
            ediMessageBodyPartContent = new String(bytes, ediMessageContentType.getCharset());

            // Build application EDI entity and add to multipart.
            ApplicationEDIEntity applicationEDIEntity = EntityUtils.createEDIEntity(ediMessageBodyPartContent,
                    ediMessageContentType, ediMessageContentTransferEncoding, false);
            applicationEDIEntity.removeAllHeaders();
            applicationEDIEntity.setHeaders(headers);
            multipartSignedEntity.addPart(applicationEDIEntity);

            //
            // End EDI Message Body Part

            //
            // Parse Signature Body Part
            //

            // Read Signature Body Part Headers
            headers = AbstractMessageParser.parseHeaders(inBuffer, -1, -1, BasicLineParser.INSTANCE,
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
                }
            }
            if (signatureContentType == null) {
                throw new HttpException("Failed to find Content-Type header in signature body part");
            }
            if (!isPkcs7SignatureType(signatureContentType)) {
                throw new HttpException(
                        "Invalid content type '" + ediMessageContentType.getMimeType() + "' for signature body part");
            }

            // Read Signature Body Part Content
            String signatureBodyPartContent = parseBodyPartContent(inBuffer, boundary);

            // Decode content
            byte[] signature = EntityUtils.decodeTransferEncodingOfBodyPartContent(signatureBodyPartContent, signatureContentType,
                    signatureContentTransferEncoding);

            // Build application Pkcs7 Signature entity and add to multipart.
            ApplicationPkcs7SignatureEntity applicationPkcs7SignatureEntity = new ApplicationPkcs7SignatureEntity(
                    signatureContentType.getCharset().toString(), signatureContentTransferEncoding, signature, false);
            multipartSignedEntity.addPart(applicationPkcs7SignatureEntity);

            //
            // End Signature Body Parts

            return multipartSignedEntity;
        } catch (HttpException e) {
            throw e;
        } catch (Exception e) {
            throw new HttpException("Failed to parse entity content", e);
        }
    }

    public static HttpEntity parseApplicationEDIEntity(HttpMessage message, HttpEntity entity, boolean isMainBody)
            throws HttpException {
        Args.notNull(entity, "Entity");
        Args.check(entity.isStreaming(), "Entity is not streaming");
        ApplicationEDIEntity applicationEDIEntity = null;
        Header[] headers = null;

        if (entity instanceof ApplicationEDIEntity) {
            return entity;
        }

        Args.check(entity.isStreaming(), "Entity is not streaming");

        try {

            // Determine and validate the Content Type
            Header contentTypeHeader = entity.getContentType();
            if (contentTypeHeader == null) {
                throw new HttpException("Content-Type header is missing");
            }
            ContentType contentType = ContentType.parse(entity.getContentType().getValue());
            if (!contentType.getMimeType().startsWith(EntityParser.APPLICATION_EDIT_CONTENT_TYPE_PREFIX)) {
                throw new HttpException("Entity has invalid MIME type '" + contentType.getMimeType() + "'");
            }

            // Determine Transfer Encoding
            Header transferEncoding = entity.getContentEncoding();
            String contentTransferEncoding = transferEncoding == null ? null : transferEncoding.getValue();

            SessionInputBufferImpl inBuffer = new SessionInputBufferImpl(new HttpTransportMetricsImpl(), 8 * 1024);
            inBuffer.bind(entity.getContent());

            // Parse Headers
            if (!isMainBody) {
                headers = AbstractMessageParser.parseHeaders(inBuffer, -1, -1, BasicLineParser.INSTANCE,
                        new ArrayList<CharArrayBuffer>());
            }

            // Extract content from stream
            CharArrayBuffer lineBuffer = new CharArrayBuffer(1024);
            while (inBuffer.readLine(lineBuffer) != -1) {
                lineBuffer.append("\r\n"); // add line delimiter
            }

            // Build application EDI entity
            applicationEDIEntity = EntityUtils.createEDIEntity(lineBuffer.toString(), contentType,
                    contentTransferEncoding, isMainBody);

            if (headers != null) {
                applicationEDIEntity.setHeaders(headers);
            }

            return applicationEDIEntity;
        } catch (HttpException e) {
            throw e;
        } catch (Exception e) {
            throw new HttpException("Failed to parse entity content", e);
        }
    }

    public static HttpEntity parseMessageDispositionNotificationReportEntity(HttpMessage message,
                                                                             HttpEntity entity,
                                                                             boolean isMainBody)
            throws HttpException {
        Args.notNull(entity, "entity");
        DispositionNotificationMultipartReportEntity dispositionNotificationMultipartReportEntity = null;
        Header[] headers = null;

        if (entity instanceof DispositionNotificationMultipartReportEntity) {
            return entity;
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
            String charsetName = AS2CharSet.US_ASCII;
            Charset charset = contentType.getCharset();
            if (charset != null) {
                charsetName = charset.name();
            }
            
            // Determine content transfer encoding
            String contentTransferEncoding = HttpMessageUtils.getHeaderValue(message, AS2Header.CONTENT_TRANSFER_ENCODING);

            AS2SessionInputBuffer inbuffer = new AS2SessionInputBuffer(new HttpTransportMetricsImpl(), 8 * 1024);
            inbuffer.bind(entity.getContent());

            // Parse Headers
            if (!isMainBody) {
                headers = AbstractMessageParser.parseHeaders(inbuffer, -1, -1, BasicLineParser.INSTANCE,
                        new ArrayList<CharArrayBuffer>());
            }

            // Get Boundary Value
            String boundary = null;
            if (isMainBody) {
                boundary = getBoundaryParameterValue(message, AS2Header.REPORT_TYPE);
            } else if (headers != null) {
                boundary = getBoundaryParameterValue(headers, AS2Header.REPORT_TYPE);
            }
            if (boundary == null) {
                throw new HttpException("Failed to retrive boundary value");
            }
            
            dispositionNotificationMultipartReportEntity = parseDispositionNotificationMultipartReportEntityBody(inbuffer, boundary, charsetName, contentTransferEncoding);

            if (headers != null) {
                dispositionNotificationMultipartReportEntity.setHeaders(headers);
            }

            return dispositionNotificationMultipartReportEntity;

        } catch (HttpException e) {
            throw e;
        } catch (Exception e) {
            throw new HttpException("Failed to parse entity content", e);
        }
    }

    public static void parseAS2MessageEntity(HttpMessage message) throws HttpException {
        HttpEntity entity = null;
        if (EntityUtils.hasEntity(message)) {
            entity = EntityUtils.getMessageEntity(message);
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
                    entity = parseApplicationEDIEntity(message, entity, true);
                    setMessageEntity(message, entity);
                    break;
                case AS2MimeType.MULTIPART_SIGNED:
                    entity = parseMultipartSignedEntity(message, entity, true);
                    setMessageEntity(message, entity);
                    break;
                case AS2MimeType.APPLICATION_PKCS7_MIME:
                    break;
                case AS2MimeType.MULTIPART_REPORT:
                    entity = parseMessageDispositionNotificationReportEntity(message, entity, true);
                    setMessageEntity(message, entity);
                    break;
                default:
                    break;
                }
            }
        }
    }

    public static DispositionNotificationMultipartReportEntity parseDispositionNotificationMultipartReportEntityBody(AS2SessionInputBuffer inbuffer,
                                                                                                                 String boundary,
                                                                                                                 String charsetName,
                                                                                                                 String contentTransferEncoding)
            throws ParseException {
        try {

            if (charsetName == null) {
                charsetName = AS2CharSet.US_ASCII;
            }
            Charset charset = Charset.forName(charsetName);
            CharsetDecoder charsetDecoder = charset.newDecoder();

            inbuffer.setCharsetDecoder(charsetDecoder);
            inbuffer.setTransferEncoding(contentTransferEncoding);

            DispositionNotificationMultipartReportEntity dispositionNotificationMultipartReportEntity = new DispositionNotificationMultipartReportEntity(boundary, false);
            ContentType contentType = ContentType.create(AS2MimeType.MULTIPART_REPORT, charset);
            dispositionNotificationMultipartReportEntity.setContentType(contentType);

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
                }
            }
            if (textReportContentType == null) {
                throw new HttpException("Failed to find Content-Type header in EDI message body part");
            }
            if (!textReportContentType.getMimeType().equalsIgnoreCase(AS2MimeType.TEXT_PLAIN)) {
                throw new HttpException("Invalid content type '" + textReportContentType.getMimeType()
                        + "' for first body part of disposition notification");
            }
            
            String textReportCharsetName = textReportContentType.getCharset() == null ? AS2CharSet.US_ASCII : textReportContentType.getCharset().name();
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

            String dispositionNotificationCharsetName = dispositionNotificationContentType.getCharset() == null ? AS2CharSet.US_ASCII : dispositionNotificationContentType.getCharset().name();
            AS2MessageDispositionNotificationEntity messageDispositionNotificationEntity = parseMessageDispositionNotificationEntityBody(
                    inbuffer, boundary, dispositionNotificationCharsetName, dispositionNotificationContentTransferEncoding);
            messageDispositionNotificationEntity.setHeaders(headers);
            dispositionNotificationMultipartReportEntity.addPart(messageDispositionNotificationEntity);

            //
            // End Disposition Notification Body Part
          
            return dispositionNotificationMultipartReportEntity;
        } catch (Exception e) {
            ParseException parseException = new ParseException("failed to parse text entity");
            parseException.initCause(e);
            throw parseException;
        }

    }

    public static TextPlainEntity parseTextPlainEntityBody(AS2SessionInputBuffer inbuffer,
                                                       String boundary,
                                                       String charsetName,
                                                       String contentTransferEncoding)
            throws ParseException {

        try {

            if (charsetName == null) {
                charsetName = AS2CharSet.US_ASCII;
            }
            Charset charset = Charset.forName(charsetName);
            CharsetDecoder charsetDecoder = charset.newDecoder();

            inbuffer.setCharsetDecoder(charsetDecoder);
            inbuffer.setTransferEncoding(contentTransferEncoding);
            
            String text = parseBodyPartText(inbuffer, boundary, BasicLineParser.INSTANCE,
                    new ArrayList<CharArrayBuffer>());
            return new TextPlainEntity(text, charsetName, contentTransferEncoding, false);
        } catch (Exception e) {
            ParseException parseException = new ParseException("failed to parse text entity");
            parseException.initCause(e);
            throw parseException;
        }
    }

    public static AS2MessageDispositionNotificationEntity parseMessageDispositionNotificationEntityBody(AS2SessionInputBuffer inbuffer,
                                                                                              String boundary,
                                                                                              String charsetName,
                                                                                              String contentTransferEncoding)
            throws ParseException {

        try {

            if (charsetName == null) {
                charsetName = AS2CharSet.US_ASCII;
            }
            Charset charset = Charset.forName(charsetName);
            CharsetDecoder charsetDecoder = charset.newDecoder();

            inbuffer.setCharsetDecoder(charsetDecoder);
            inbuffer.setTransferEncoding(contentTransferEncoding);

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
        }
    }
    
    public static String parseBodyPartText(final AS2SessionInputBuffer inbuffer,
                                           final String boundary,
                                           final LineParser parser,
                                           final List<CharArrayBuffer> headerLines)
            throws IOException {
        Args.notNull(parser, "parser");
        Args.notNull(headerLines, "headerLines");
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
            buffer.append("\r\n");
            line.clear();
        }
        
        return buffer.toString();
    }

    public static List<CharArrayBuffer> parseBodyPartFields(final AS2SessionInputBuffer inbuffer,
                                                           final String boundary,
                                                           final LineParser parser,
                                                           final List<CharArrayBuffer> headerLines)
            throws IOException {
        Args.notNull(parser, "parser");
        Args.notNull(headerLines, "headerLines");
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
                headerLines.add(current);
                previous = current;
                current = null;
            }
        }
        return headerLines;
    }

}
