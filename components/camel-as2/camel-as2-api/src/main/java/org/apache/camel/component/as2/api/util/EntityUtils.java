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

import org.apache.camel.component.as2.api.AS2Charset;
import org.apache.camel.component.as2.api.AS2Header;
import org.apache.camel.component.as2.api.AS2MediaType;
import org.apache.camel.component.as2.api.entity.ApplicationEDIConsentEntity;
import org.apache.camel.component.as2.api.entity.ApplicationEDIEntity;
import org.apache.camel.component.as2.api.entity.ApplicationEDIFACTEntity;
import org.apache.camel.component.as2.api.entity.ApplicationEDIX12Entity;
import org.apache.camel.component.as2.api.entity.MimeEntity;
import org.apache.commons.codec.binary.Base64InputStream;
import org.apache.commons.codec.binary.Base64OutputStream;
import org.apache.commons.codec.net.QuotedPrintableCodec;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpMessage;
import org.apache.http.HttpResponse;
import org.apache.http.entity.ContentType;
import org.apache.http.util.Args;
import org.bouncycastle.util.encoders.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EntityUtils {

    private static final Logger LOG = LoggerFactory.getLogger(EntityUtils.class);

    private static AtomicLong partNumber = new AtomicLong();

    private EntityUtils() {
    }

    /**
     * Generated a unique value for a Multipart boundary string.
     * <p>
     * The boundary string is composed of the components:
     * "----=_Part_&lt;global_part_number&gt;_&lt;newly_created_object's_hashcode&gt;.&lt;current_time&gt;"
     * <p>
     * The generated string contains only US-ASCII characters and hence is safe
     * for use in RFC822 headers.
     *
     * @return The generated boundary string.
     */
    public static String createBoundaryValue() {
        // TODO: ensure boundary string is limited to 70 characters or less.
        StringBuffer s = new StringBuffer();
        s.append("----=_Part_").append(partNumber.incrementAndGet()).append("_").append(s.hashCode()).append(".")
                .append(System.currentTimeMillis());
        return s.toString();
    }

    public static boolean validateBoundaryValue(String boundaryValue) {
        return true; // TODO: add validation logic.
    }

    public static String appendParameter(String headerString, String parameterName, String parameterValue) {
        return headerString + "; " + parameterName + "=" + parameterValue;
    }

    public static String encode(String data, Charset charset, String encoding) throws Exception {
        byte[] encoded = encode(data.getBytes(charset), encoding);
        return new String(encoded, charset);
    }

    public static byte[] encode(byte[] data, String encoding) throws Exception {
        Args.notNull(data, "Data");

        if (encoding == null) {
            // Identity encoding
            return data;
        }

        switch(encoding.toLowerCase()) {
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
                throw new Exception("Unknown encoding: " + encoding);
        }
    }

    public static OutputStream encode(OutputStream os, String encoding) throws Exception {
        Args.notNull(os, "Output Stream");

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
                throw new Exception("Unknown encoding: " + encoding);
        }
    }

    public static String decode(String data, Charset charset, String encoding) throws Exception {
        byte[] decoded = decode(data.getBytes(charset), encoding);
        return new String(decoded, charset);
    }

    public static byte[] decode(byte[] data, String encoding) throws Exception {
        Args.notNull(data, "Data");

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
                throw new Exception("Unknown encoding: " + encoding);
        }
    }

    public static InputStream decode(InputStream is, String encoding) throws Exception {
        Args.notNull(is, "Input Stream");

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
                throw new Exception("Unknown encoding: " + encoding);
        }
    }

    public static ApplicationEDIEntity createEDIEntity(String ediMessage, ContentType ediMessageContentType, String contentTransferEncoding, boolean isMainBody) throws Exception {
        Args.notNull(ediMessage, "EDI Message");
        Args.notNull(ediMessageContentType, "EDI Message Content Type");
        String charset = ediMessageContentType.getCharset() == null ? AS2Charset.US_ASCII : ediMessageContentType.getCharset().toString();
        switch(ediMessageContentType.getMimeType().toLowerCase()) {
            case AS2MediaType.APPLICATION_EDIFACT:
                return new ApplicationEDIFACTEntity(ediMessage, charset, contentTransferEncoding, isMainBody);
            case AS2MediaType.APPLICATION_EDI_X12:
                return new ApplicationEDIX12Entity(ediMessage, charset, contentTransferEncoding, isMainBody);
            case AS2MediaType.APPLICATION_EDI_CONSENT:
                return new ApplicationEDIConsentEntity(ediMessage, charset, contentTransferEncoding, isMainBody);
            default:
                throw new Exception("Invalid EDI entity mime type: " + ediMessageContentType.getMimeType());
        }

    }

    public static byte[] getContent(HttpEntity entity) {
        try {
            final ByteArrayOutputStream outstream = new ByteArrayOutputStream();
            entity.writeTo(outstream);
            outstream.flush();
            return outstream.toByteArray();
        } catch (Exception e) {
            LOG.debug("failed to get content", e);
            return null;
        }
    }

    public static boolean hasEntity(HttpMessage message) {
        boolean hasEntity = false;
        if (message instanceof HttpEntityEnclosingRequest) {
            hasEntity = ((HttpEntityEnclosingRequest) message).getEntity() != null;
        } else if (message instanceof HttpResponse) {
            hasEntity = ((HttpResponse) message).getEntity() != null;
        }
        return hasEntity;
    }

    public static HttpEntity getMessageEntity(HttpMessage message) {
        if (message instanceof HttpEntityEnclosingRequest) {
            return ((HttpEntityEnclosingRequest) message).getEntity();
        } else if (message instanceof HttpResponse) {
            return ((HttpResponse) message).getEntity();
        }
        return null;
    }

    public static void setMessageEntity(HttpMessage message, HttpEntity entity) {
        if (message instanceof HttpEntityEnclosingRequest) {
            ((HttpEntityEnclosingRequest) message).setEntity(entity);
        } else if (message instanceof HttpResponse) {
            ((HttpResponse) message).setEntity(entity);
        }
        Header contentTypeHeader = entity.getContentType();
        if (contentTypeHeader != null) {
            message.setHeader(contentTypeHeader);
        }
        if (entity instanceof MimeEntity) {
            Header contentTransferEncodingHeader = ((MimeEntity)entity).getContentTransferEncoding();
            if (contentTransferEncodingHeader != null) {
                message.setHeader(contentTransferEncodingHeader);
            }
        }
        long contentLength = entity.getContentLength();
        message.setHeader(AS2Header.CONTENT_LENGTH, Long.toString(contentLength));
    }

    public static byte[] decodeTransferEncodingOfBodyPartContent(String bodyPartContent,
                                                                 ContentType contentType,
                                                                 String bodyPartTransferEncoding)
            throws Exception {
        Args.notNull(bodyPartContent, "bodyPartContent");
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
             PrintStream ps = new PrintStream(baos, true, "utf-8")) {
            printEntity(ps, entity);
            String content = baos.toString(StandardCharsets.UTF_8.name());
            return content;
        }
    }

}
