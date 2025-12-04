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

import java.io.IOException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;

import org.apache.camel.component.as2.api.entity.EntityParser;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentLengthStrategy;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.impl.io.DefaultBHttpServerConnection;
import org.apache.hc.core5.http.io.HttpMessageParserFactory;
import org.apache.hc.core5.http.io.HttpMessageWriterFactory;

public class AS2BHttpServerConnection extends DefaultBHttpServerConnection {

    public AS2BHttpServerConnection(Http1Config http1Config) {
        this(http1Config, null, null);
    }

    public AS2BHttpServerConnection(Http1Config http1Config, CharsetDecoder chardecoder, CharsetEncoder charencoder) {
        this(http1Config, chardecoder, charencoder, null, null, null, null);
    }

    public AS2BHttpServerConnection(
            Http1Config http1Config,
            CharsetDecoder chardecoder,
            CharsetEncoder charencoder,
            ContentLengthStrategy incomingContentStrategy,
            ContentLengthStrategy outgoingContentStrategy,
            HttpMessageParserFactory<ClassicHttpRequest> requestParserFactory,
            HttpMessageWriterFactory<ClassicHttpResponse> responseWriterFactory) {
        super(
                null,
                http1Config,
                chardecoder,
                charencoder,
                incomingContentStrategy,
                outgoingContentStrategy,
                requestParserFactory,
                responseWriterFactory);
    }

    @Override
    public void receiveRequestEntity(ClassicHttpRequest request) throws HttpException, IOException {
        super.receiveRequestEntity(request);
        EntityParser.parseAS2MessageEntity(request);
    }
}
