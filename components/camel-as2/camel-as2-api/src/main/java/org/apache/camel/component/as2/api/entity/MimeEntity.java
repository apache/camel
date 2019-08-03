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
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

import org.apache.camel.component.as2.api.AS2Charset;
import org.apache.camel.component.as2.api.AS2Header;
import org.apache.http.Header;
import org.apache.http.HeaderIterator;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.HeaderGroup;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.Args;

public abstract class MimeEntity extends AbstractHttpEntity {

    /**
     * An OuputStream wrapper that doesn't close its underlying output stream.
     * <p>
     * Instances of this stream are used by entities to attach encoding streams
     * to underlying output stream in order to write out their encoded content
     * and then flush and close these encoding streams without closing the
     * underlying output stream.
     */
    protected static class NoCloseOutputStream extends FilterOutputStream {
        public NoCloseOutputStream(OutputStream os) {
            super(os);
        }

        @Override
        public void close() {
            // do nothing
        }
    }

    protected static final long UNKNOWN_CONTENT_LENGTH = -1;

    protected static final long RECALCULATE_CONTENT_LENGTH = -2;

    protected boolean isMainBody;

    protected Header contentTransferEncoding;

    protected long contentLength = RECALCULATE_CONTENT_LENGTH;

    private final HeaderGroup headergroup = new HeaderGroup();

    protected MimeEntity() {
    }

    public boolean isMainBody() {
        return isMainBody;
    }

    public void setMainBody(boolean isMainBody) {
        this.isMainBody = isMainBody;
    }

    public String getContentTypeValue() {
        Header contentTypeHeader = getContentType();
        if (contentTypeHeader != null) {
            return contentTypeHeader.getValue();
        }
        return null;
    }

    public void setContentType(ContentType contentType) {
        super.setContentType(contentType == null ? null : contentType.toString());
    }

    @Override
    public void setContentType(Header contentType) {
        super.setContentType(contentType);
        if (contentType != null) {
            addHeader(contentType);
        } else {
            removeHeaders(AS2Header.CONTENT_TYPE);
        }
    }

    public String getContentEncodingValue() {
        Header contentEncodingHeader = getContentEncoding();
        if (contentEncodingHeader != null) {
            return contentEncodingHeader.getValue();
        }
        return null;
    }

    @Override
    public void setContentEncoding(Header contentEncoding) {
        super.setContentEncoding(contentEncoding);
        if (contentEncoding != null) {
            addHeader(contentEncoding);
        } else {
            removeHeaders(HTTP.CONTENT_ENCODING);
        }
    }

    public String getContentTransferEncodingValue() {
        Header contentTransferEncodingHeader = getContentTransferEncoding();
        if (contentTransferEncodingHeader != null) {
            return contentTransferEncodingHeader.getValue();
        }
        return null;
    }

    /**
     * Obtains the Content-Transfer-Encoding header.
     * The default implementation returns the value of the
     * {@link #contentEncoding contentEncoding} attribute.
     *
     * @return  the Content-Transfer-Encoding header, or {@code null}
     */
    public Header getContentTransferEncoding() {
        return this.contentTransferEncoding;
    }

    /**
     * Specifies the Content-Transfer-Encoding header.
     * The default implementation sets the value of the
     * {@link #contentTransferEncoding contentTransferEncoding} attribute.
     *
     * @param contentTransferEncoding   the new Content-Transfer-Encoding header, or
     *                          {@code null} to unset
     */
    public void setContentTransferEncoding(final Header contentTransferEncoding) {
        this.contentTransferEncoding = contentTransferEncoding;
        if (contentTransferEncoding != null) {
            addHeader(contentTransferEncoding);
        } else {
            removeHeaders(AS2Header.CONTENT_TRANSFER_ENCODING);
        }
    }

    /**
     * Specifies the Content-Transfer-Encoding header, as a string.
     * The default implementation calls
     * {@link #setContentTransferEncoding(Header) setContentTransferEncoding(Header)}.
     *
     * @param contentTranserEncoding - the new Content-Transfer-Encoding header, or
     *                     {@code null} to unset
     */
    public void setContentTransferEncoding(final String contentTranserEncoding) {
        Header h = null;
        if (contentTranserEncoding != null) {
            h = new BasicHeader(AS2Header.CONTENT_TRANSFER_ENCODING, contentTranserEncoding);
        }
        setContentTransferEncoding(h);
    }



    public boolean containsHeader(final String name) {
        return this.headergroup.containsHeader(name);
    }

    public Header[] getHeaders(final String name) {
        return this.headergroup.getHeaders(name);
    }

    public Header getFirstHeader(final String name) {
        return this.headergroup.getFirstHeader(name);
    }

    public Header getLastHeader(final String name) {
        return this.headergroup.getLastHeader(name);
    }

    public Header[] getAllHeaders() {
        return this.headergroup.getAllHeaders();
    }

    public void addHeader(final Header header) {
        this.headergroup.addHeader(header);
    }

    public void addHeader(final String name, final String value) {
        Args.notNull(name, "Header name");
        this.headergroup.addHeader(new BasicHeader(name, value));
    }

    public void setHeader(final Header header) {
        this.headergroup.updateHeader(header);
    }

    public void setHeader(final String name, final String value) {
        Args.notNull(name, "Header name");
        this.headergroup.updateHeader(new BasicHeader(name, value));
    }

    public void setHeaders(final Header[] headers) {
        this.headergroup.setHeaders(headers);
    }

    public void removeHeader(final Header header) {
        this.headergroup.removeHeader(header);
    }

    public void removeHeaders(final String name) {
        if (name == null) {
            return;
        }
        for (final HeaderIterator i = this.headergroup.iterator(); i.hasNext();) {
            final Header header = i.nextHeader();
            if (name.equalsIgnoreCase(header.getName())) {
                i.remove();
            }
        }
    }

    public void removeAllHeaders() {
        this.headergroup.clear();
    }

    public HeaderIterator headerIterator() {
        return this.headergroup.iterator();
    }

    public HeaderIterator headerIterator(final String name) {
        return this.headergroup.iterator(name);
    }

    @Override
    public boolean isRepeatable() {
        return true;
    }

    @Override
    public boolean isStreaming() {
        return !isRepeatable();
    }

    @Override
    public long getContentLength() {
        if (contentLength == RECALCULATE_CONTENT_LENGTH) {
            // Calculate content length
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            try {
                writeTo(out);
                contentLength = out.toByteArray().length;
            } catch (IOException e) {
                contentLength = MimeEntity.UNKNOWN_CONTENT_LENGTH;
            }
        }
        return contentLength;
    }

    @Override
    public InputStream getContent() throws IOException, UnsupportedOperationException {
        final ByteArrayOutputStream outstream = new ByteArrayOutputStream();
        writeTo(outstream);
        outstream.flush();
        return new ByteArrayInputStream(outstream.toByteArray());
    }

    public String getCharset() {
        if (getContentType() == null) {
            return AS2Charset.US_ASCII;
        }
        ContentType contentType = ContentType.parse(getContentType().getValue());
        Charset charset = contentType.getCharset();
        if (charset != null) {
            return charset.name();
        }
        return AS2Charset.US_ASCII;
    }

}
