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
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.as2.api.AS2Header;
import org.apache.camel.component.as2.api.CanonicalOutputStream;
import org.apache.camel.component.as2.api.util.EntityUtils;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.slf4j.helpers.MessageFormatter;

public abstract class ApplicationEntity extends MimeEntity {

    protected static final String CONTENT_DISPOSITION_PATTERN = "attachment; filename={}";

    protected final byte[] ediContent;

    protected ApplicationEntity(byte[] ediContent, ContentType contentType, String contentTransferEncoding,
                                boolean isMainBody, String filename) {
        super(contentType, contentTransferEncoding);
        this.ediContent = ObjectHelper.notNull(ediContent, "EDI Content");
        setMainBody(isMainBody);
        if (StringUtils.isNotBlank(filename)) {
            addHeader(AS2Header.CONTENT_DISPOSITION,
                    MessageFormatter.format(CONTENT_DISPOSITION_PATTERN, filename).getMessage());
        }
    }

    public Object getEdiMessage() {
        if (this.getContentEncoding() != null) {
            switch (this.getContentEncoding().toLowerCase()) {
                case "base64":
                case "binary":
                    return new ByteArrayInputStream(ediContent);
            }
        }
        try {
            return new String(ediContent, getCharset());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeCamelException(e);
        }
    }

    @Override
    public void writeTo(OutputStream outstream) throws IOException {
        NoCloseOutputStream ncos = new NoCloseOutputStream(outstream);
        try (CanonicalOutputStream canonicalOutstream = new CanonicalOutputStream(ncos, StandardCharsets.US_ASCII.name());
             OutputStream transferEncodedStream = EntityUtils.encode(canonicalOutstream, getContentTransferEncodingValue())) {

            // Write out mime part headers if this is not the main body of message.
            if (!isMainBody()) {
                for (Header header : getAllHeaders()) {
                    canonicalOutstream.writeln(header.toString());
                }
                canonicalOutstream.writeln(); // ensure empty line between headers and body; RFC2046 - 5.1.1
            }
            transferEncodedStream.write(ediContent);
        } catch (Exception e) {
            throw new IOException("Failed to write to output stream", e);
        }
    }

}
