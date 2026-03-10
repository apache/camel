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
package org.apache.camel.component.as2.api.io;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;

import org.apache.camel.component.as2.api.entity.EntityParser;
import org.apache.camel.component.as2.api.util.EntityUtils;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentLengthStrategy;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.impl.io.DefaultBHttpServerConnection;
import org.apache.hc.core5.http.io.HttpMessageParserFactory;
import org.apache.hc.core5.http.io.HttpMessageWriterFactory;
import org.apache.hc.core5.http.io.entity.BasicHttpEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AS2BHttpServerConnection extends DefaultBHttpServerConnection {

    private static final Logger LOG = LoggerFactory.getLogger(AS2BHttpServerConnection.class);

    /**
     * The raw (unparsed) request body bytes captured before MIME parsing. This allows downstream handlers to perform
     * signature verification against the original bytes, which may differ from the re-serialized parsed entity due to
     * MIME header folding, whitespace normalization, and line ending changes.
     */
    private byte[] rawRequestBody;

    public AS2BHttpServerConnection(Http1Config http1Config) {
        this(http1Config, null, null);
    }

    public AS2BHttpServerConnection(Http1Config http1Config,
                                    CharsetDecoder chardecoder,
                                    CharsetEncoder charencoder) {
        this(http1Config, chardecoder, charencoder, null, null, null, null);
    }

    public AS2BHttpServerConnection(Http1Config http1Config,
                                    CharsetDecoder chardecoder,
                                    CharsetEncoder charencoder,
                                    ContentLengthStrategy incomingContentStrategy,
                                    ContentLengthStrategy outgoingContentStrategy,
                                    HttpMessageParserFactory<ClassicHttpRequest> requestParserFactory,
                                    HttpMessageWriterFactory<ClassicHttpResponse> responseWriterFactory) {
        super(null, http1Config, chardecoder, charencoder, incomingContentStrategy,
              outgoingContentStrategy, requestParserFactory, responseWriterFactory);
    }

    /**
     * Returns the raw (unparsed) request body bytes captured before MIME parsing.
     *
     * @return the raw body bytes, or null if no entity was received
     */
    public byte[] getRawRequestBody() {
        return rawRequestBody;
    }

    @Override
    public void receiveRequestEntity(ClassicHttpRequest request) throws HttpException, IOException {
        super.receiveRequestEntity(request);
        // Capture raw body bytes before parsing modifies them.
        // This is essential for signature verification of async MDNs, where the parsed
        // MIME entity may have different bytes due to header folding and whitespace normalization.
        if (request.getEntity() != null) {
            try {
                rawRequestBody = EntityUtils.getContent(request.getEntity());
                // Reset the entity with the captured bytes so it can be parsed
                ContentType contentType = ContentType.parse(request.getEntity().getContentType());
                request.setEntity(
                        new BasicHttpEntity(new ByteArrayInputStream(rawRequestBody), rawRequestBody.length, contentType));
            } catch (Exception e) {
                LOG.debug("Failed to capture raw request body bytes", e);
            }
        }
        EntityParser.parseAS2MessageEntity(request);
    }

}
