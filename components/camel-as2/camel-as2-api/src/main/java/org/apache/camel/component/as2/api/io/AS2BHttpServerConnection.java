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
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.config.MessageConstraints;
import org.apache.http.entity.ContentLengthStrategy;
import org.apache.http.impl.DefaultBHttpServerConnection;
import org.apache.http.io.HttpMessageParserFactory;
import org.apache.http.io.HttpMessageWriterFactory;

public class AS2BHttpServerConnection extends DefaultBHttpServerConnection {

    public AS2BHttpServerConnection(int buffersize) {
        super(buffersize);
    }

    public AS2BHttpServerConnection(int buffersize,
                                    CharsetDecoder chardecoder,
                                    CharsetEncoder charencoder,
                                    MessageConstraints constraints) {
        super(buffersize, chardecoder, charencoder, constraints);
    }

    public AS2BHttpServerConnection(int buffersize,
                                    int fragmentSizeHint,
                                    CharsetDecoder chardecoder,
                                    CharsetEncoder charencoder,
                                    MessageConstraints constraints,
                                    ContentLengthStrategy incomingContentStrategy,
                                    ContentLengthStrategy outgoingContentStrategy,
                                    HttpMessageParserFactory<HttpRequest> requestParserFactory,
                                    HttpMessageWriterFactory<HttpResponse> responseWriterFactory) {
        super(buffersize, fragmentSizeHint, chardecoder, charencoder, constraints, incomingContentStrategy,
                outgoingContentStrategy, requestParserFactory, responseWriterFactory);
    }

    @Override
    public void receiveRequestEntity(HttpEntityEnclosingRequest request) throws HttpException, IOException {
        super.receiveRequestEntity(request);
        EntityParser.parseAS2MessageEntity(request);
    }

}
