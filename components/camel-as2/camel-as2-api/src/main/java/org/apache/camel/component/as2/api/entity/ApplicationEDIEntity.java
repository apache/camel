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

import java.io.IOException;
import java.io.OutputStream;

import org.apache.camel.component.as2.api.AS2Charset;
import org.apache.camel.component.as2.api.CanonicalOutputStream;
import org.apache.camel.component.as2.api.util.EntityUtils;
import org.apache.http.Header;
import org.apache.http.HeaderIterator;
import org.apache.http.entity.ContentType;
import org.apache.http.util.Args;

public abstract class ApplicationEDIEntity extends MimeEntity {

    private final String ediMessage;

    protected ApplicationEDIEntity(String ediMessage, ContentType contentType, String contentTransferEncoding, boolean isMainBody) {
        this.ediMessage = Args.notNull(ediMessage, "EDI Message");
        setContentType(Args.notNull(contentType, "Content Type").toString());
        setContentTransferEncoding(contentTransferEncoding);
        setMainBody(isMainBody);
    }

    public String getEdiMessage() {
        return ediMessage;
    }


    @Override
    public void writeTo(OutputStream outstream) throws IOException {
        NoCloseOutputStream ncos = new NoCloseOutputStream(outstream);
        try (CanonicalOutputStream canonicalOutstream = new CanonicalOutputStream(ncos, AS2Charset.US_ASCII);
            OutputStream transferEncodedStream = EntityUtils.encode(canonicalOutstream, getContentTransferEncodingValue())) {

            // Write out mime part headers if this is not the main body of message.
            if (!isMainBody()) {
                HeaderIterator it = headerIterator();
                while (it.hasNext()) {
                    Header header = it.nextHeader();
                    canonicalOutstream.writeln(header.toString());
                }
                canonicalOutstream.writeln(); // ensure empty line between headers and body; RFC2046 - 5.1.1
            }

            transferEncodedStream.write(ediMessage.getBytes(getCharset()), 0, ediMessage.length());
        } catch (Exception e) {
            throw new IOException("Failed to write to output stream", e);
        }
    }

}
