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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.camel.CamelException;
import org.apache.camel.component.as2.api.AS2Header;
import org.apache.camel.component.as2.api.AS2MediaType;
import org.apache.camel.component.as2.api.entity.ApplicationEDIConsentEntity;
import org.apache.camel.component.as2.api.entity.ApplicationEDIFACTEntity;
import org.apache.camel.component.as2.api.entity.ApplicationEDIX12Entity;
import org.apache.camel.component.as2.api.entity.ApplicationEntity;
import org.apache.camel.component.as2.api.entity.ApplicationXMLEntity;
import org.apache.camel.component.as2.api.entity.MimeEntity;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Base64InputStream;
import org.apache.commons.codec.binary.Base64OutputStream;
import org.apache.commons.codec.net.QuotedPrintableCodec;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpMessage;
import org.apache.http.HttpResponse;
import org.apache.http.entity.ContentType;
import org.bouncycastle.util.encoders.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EntityUtils {

    private static final Logger LOG = LoggerFactory.getLogger(EntityUtils.class);

    private static final AtomicLong partNumber = new AtomicLong();

    private EntityUtils() {
    }

    /**
     * Generated a unique value for a Multipart boundary string.
     * <p>
     * The boundary string is composed of the components:
     * "----=_Part_&lt;global_part_number&gt;_&lt;newly_created_object's_hashcode&gt;.&lt;current_time&gt;"
     * <p>
     * The generated string contains only US-ASCII characters and hence is safe for use in RFC822 headers.
     *
     * @return The generated boundary string.
     */
    public static String createBoundaryValue() {
        // TODO: ensure boundary string is limited to 70 characters or less.
        StringBuilder s = new StringBuilder();
        s.append("----=_Part_").append(partNumber.incrementAndGet()).append("_").append(s.hashCode()).append(".")
                .append(System.currentTimeMillis());
        return s.toString();
    }

    public static String appendParameter(String headerString, String parameterName, String parameterValue) {
        return headerString + "; " + parameterName + "=" + parameterValue;
    }

    public static String encode(String data, Charset charset, String encoding) throws CamelException {
        byte[] encoded = encode(data.getBytes(charset), encoding);
        return new String(encoded, charset);
    }

    public static byte[] encode(byte[] data, String encoding) throws CamelException {
        ObjectHelper.notNull(data, "Data");

        if (encoding == null) {
            // Identity encoding
            return data;
        }

        switch (encoding.toLowerCase()) {
            case "base64":
                return Base64.encode(data);
            case "quoted-printable":
                // TODO: implement QuotedPrintableOutputStream
                return QuotedPrintableCodec.encodeQuotedPrintable(null, data);
            case "binary":
            case "7bit":
            case "8bit":
                // Identity encoding
                return data;
            default:
                throw new CamelException("Unknown encoding: " + encoding);
        }
    }

    public static OutputStream encode(OutputStream os, String encoding) throws CamelException {
        ObjectHelper.notNull(os, "Output Stream");

        if (encoding == null) {
            // Identity encoding
            return os;
        }
        switch (encoding.toLowerCase()) {
            case "base64":
                return new Base64OutputStream(os, true);
            case "quoted-printable":
                // TODO: implement QuotedPrintableOutputStream
                return new Base64OutputStream(os, true);
            case "binary":
            case "7bit":
            case "8bit":
                // Identity encoding
                return os;
            default:
                throw new CamelException("Unknown encoding: " + encoding);
        }
    }

    public static String decode(String data, Charset charset, String encoding) throws CamelException, DecoderException {
        byte[] decoded = decode(data.getBytes(charset), encoding);
        return new String(decoded, charset);
    }

    public static byte[] decode(byte[] data, String encoding) throws CamelException, DecoderException {
        ObjectHelper.notNull(data, "Data");

        if (encoding == null) {
            // Identity encoding
            return data;
        }
        switch (encoding.toLowerCase()) {
            case "base64":
                return Base64.decode(data);
            case "quoted-printable":
                return QuotedPrintableCodec.decodeQuotedPrintable(data);
            case "binary":
            case "7bit":
            case "8bit":
                // Identity encoding
                return data;
            default:
                throw new CamelException("Unknown encoding: " + encoding);
        }
    }

    public static InputStream decode(InputStream is, String encoding) throws CamelException {
        ObjectHelper.notNull(is, "Input Stream");

        if (encoding == null) {
            // Identity encoding
            return is;
        }
        switch (encoding.toLowerCase()) {
            case "base64":
                return new Base64InputStream(is, false);
            case "quoted-printable":
                // TODO: implement QuotedPrintableInputStream
                return new Base64InputStream(is, false);
            case "binary":
            case "7bit":
            case "8bit":
                // Identity encoding
                return is;
            default:
                throw new CamelException("Unknown encoding: " + encoding);
        }
    }

    public static ApplicationEntity createEDIEntity(
            String ediMessage, ContentType ediMessageContentType, String contentTransferEncoding, boolean isMainBody,
            String filename)
            throws CamelException {
        ObjectHelper.notNull(ediMessage, "EDI Message");
        ObjectHelper.notNull(ediMessageContentType, "EDI Message Content Type");
        String charset = null;
        if (ediMessageContentType.getCharset() != null) {
            charset = ediMessageContentType.getCharset().toString();
        }
        switch (ediMessageContentType.getMimeType().toLowerCase()) {
            case AS2MediaType.APPLICATION_EDIFACT:
                return new ApplicationEDIFACTEntity(ediMessage, charset, contentTransferEncoding, isMainBody, filename);
            case AS2MediaType.APPLICATION_EDI_X12:
                return new ApplicationEDIX12Entity(ediMessage, charset, contentTransferEncoding, isMainBody, filename);
            case AS2MediaType.APPLICATION_EDI_CONSENT:
                return new ApplicationEDIConsentEntity(ediMessage, charset, contentTransferEncoding, isMainBody, filename);
            case AS2MediaType.APPLICATION_XML:
                return new ApplicationXMLEntity(ediMessage, charset, contentTransferEncoding, isMainBody, filename);
            default:
                throw new CamelException("Invalid EDI entity mime type: " + ediMessageContentType.getMimeType());
        }

    }

    public static byte[] getContent(HttpEntity entity) {
        try (final ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            entity.writeTo(os);
            os.flush();
            return os.toByteArray();
        } catch (Exception e) {
            LOG.debug("failed to get content", e);
            return null;
        }
    }

    public static boolean hasEntity(HttpMessage message) {
        boolean hasEntity = false;
        if (message instanceof HttpEntityEnclosingRequest httpEntityEnclosingRequest) {
            hasEntity = httpEntityEnclosingRequest.getEntity() != null;
        } else if (message instanceof HttpResponse httpResponse) {
            hasEntity = httpResponse.getEntity() != null;
        }
        return hasEntity;
    }

    public static HttpEntity getMessageEntity(HttpMessage message) {
        if (message instanceof HttpEntityEnclosingRequest httpEntityEnclosingRequest) {
            return httpEntityEnclosingRequest.getEntity();
        } else if (message instanceof HttpResponse httpResponse) {
            return httpResponse.getEntity();
        }
        return null;
    }

    public static void setMessageEntity(HttpMessage message, HttpEntity entity) {
        if (message instanceof HttpEntityEnclosingRequest httpEntityEnclosingRequest) {
            httpEntityEnclosingRequest.setEntity(entity);
        } else if (message instanceof HttpResponse httpResponse) {
            httpResponse.setEntity(entity);
        }
        Header contentTypeHeader = entity.getContentType();
        if (contentTypeHeader != null) {
            message.setHeader(contentTypeHeader);
        }
        if (entity instanceof MimeEntity mimeEntity) {
            Header contentTransferEncodingHeader = mimeEntity.getContentTransferEncoding();
            if (contentTransferEncodingHeader != null) {
                message.setHeader(contentTransferEncodingHeader);
            }
        }
        long contentLength = entity.getContentLength();
        message.setHeader(AS2Header.CONTENT_LENGTH, Long.toString(contentLength));
    }

    public static byte[] decodeTransferEncodingOfBodyPartContent(
            String bodyPartContent,
            ContentType contentType,
            String bodyPartTransferEncoding)
            throws CamelException, DecoderException {
        ObjectHelper.notNull(bodyPartContent, "bodyPartContent");
        Charset contentCharset = contentType.getCharset();
        if (contentCharset == null) {
            contentCharset = StandardCharsets.US_ASCII;
        }
        return decode(bodyPartContent.getBytes(contentCharset), bodyPartTransferEncoding);

    }

    public static void printEntity(PrintStream out, HttpEntity entity) throws IOException {
        entity.writeTo(out);
    }

    public static String printEntity(HttpEntity entity) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             PrintStream ps = new PrintStream(baos, true, StandardCharsets.UTF_8)) {
            printEntity(ps, entity);
            return baos.toString(StandardCharsets.UTF_8);
        }
    }

}
