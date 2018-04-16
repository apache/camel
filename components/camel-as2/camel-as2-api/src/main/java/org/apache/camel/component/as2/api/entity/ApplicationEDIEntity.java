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
import java.io.OutputStream;

import org.apache.camel.component.as2.api.AS2CharSet;
import org.apache.camel.component.as2.api.CanonicalOutputStream;
import org.apache.http.Header;
import org.apache.http.HeaderIterator;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.io.AbstractMessageParser;
import org.apache.http.impl.io.HttpTransportMetricsImpl;
import org.apache.http.impl.io.SessionInputBufferImpl;
import org.apache.http.message.BasicLineParser;
import org.apache.http.util.Args;
import org.apache.http.util.CharArrayBuffer;

public abstract class ApplicationEDIEntity extends MimeEntity {
    
    private static final String APPLICATION_EDIT_CONTENT_TYPE_PREFIX = "application/edi";
    
    private final String content;
    
    public static HttpEntity parseEntity(HttpEntity entity, boolean isMainBody) throws HttpException {
        Args.notNull(entity, "Entity");
        Args.check(entity.isStreaming(), "Entity is not streaming");
        ApplicationEDIEntity applicationEDIEntity = null;
        Header[] headers = null;
        
        try {
            
            // Determine and validate the Content Type
            Header contentTypeHeader = entity.getContentType();
            if (contentTypeHeader == null) {
                throw new HttpException("Content-Type header is missing");
            }
            ContentType contentType = ContentType.parse(entity.getContentType().getValue());
            if (!contentType.getMimeType().startsWith(APPLICATION_EDIT_CONTENT_TYPE_PREFIX)) {
                throw new HttpException("Entity has invalid MIME type '" + contentType.getMimeType() + "'");
            }

            // Determine Transfer Encoding
            Header transferEncoding = entity.getContentEncoding();
            String contentTransferEncoding = transferEncoding == null ? null : transferEncoding.getValue();
        
            SessionInputBufferImpl inBuffer = new SessionInputBufferImpl(new HttpTransportMetricsImpl(), 8 * 1024);
            inBuffer.bind(entity.getContent());
            
            // Parse Headers
            if (!isMainBody) {
               headers = AbstractMessageParser.parseHeaders(
                        inBuffer,
                        -1,
                        -1,
                        BasicLineParser.INSTANCE,
                        null);
            }
            
            // Extract content from stream
            CharArrayBuffer lineBuffer = new CharArrayBuffer(1024);
            while(inBuffer.readLine(lineBuffer) != -1) {
                lineBuffer.append("\r\n"); // add line delimiter
            }
            
            // Build application EDI entity
            applicationEDIEntity = EntityUtils.createEDIEntity(lineBuffer.toString(), contentType, contentTransferEncoding, isMainBody);

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


    protected ApplicationEDIEntity(String content, ContentType contentType, String contentTransferEncoding, boolean isMainBody) {
        this.content = Args.notNull(content, "Content");
        setContentType(Args.notNull(contentType, "Content Type").toString());
        setContentTransferEncoding(contentTransferEncoding);
        setMainBody(isMainBody);
    }
    
    @Override
    public void writeTo(OutputStream outstream) throws IOException {
        NoCloseOutputStream ncos = new NoCloseOutputStream(outstream);
        try (CanonicalOutputStream canonicalOutstream = new CanonicalOutputStream(ncos, AS2CharSet.US_ASCII)) {

            // Write out mime part headers if this is not the main body of message.
            if (!isMainBody()) { 
                HeaderIterator it = headerIterator();
                while (it.hasNext()) {
                    Header header = it.nextHeader();
                    canonicalOutstream.writeln(header.toString());
                }
                canonicalOutstream.writeln(); // ensure empty line between headers and body; RFC2046 - 5.1.1
            }
            
            canonicalOutstream.write(content.getBytes(AS2CharSet.US_ASCII), 0, content.length());
        }
    }

}
