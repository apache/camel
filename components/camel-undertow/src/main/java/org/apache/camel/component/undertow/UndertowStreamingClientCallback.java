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
package org.apache.camel.component.undertow;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

import io.undertow.client.ClientConnection;
import io.undertow.client.ClientExchange;
import io.undertow.client.ClientRequest;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.util.IOHelper;
import org.xnio.channels.StreamSinkChannel;

class UndertowStreamingClientCallback extends UndertowClientCallback {

    private InputStream bodyStream;

    UndertowStreamingClientCallback(Exchange exchange, AsyncCallback callback,
                                    UndertowEndpoint endpoint, ClientRequest request,
                                    InputStream body) {
        super(exchange, callback, endpoint, request, null);
        this.bodyStream = body;
    }

    @Override
    public void completed(ClientConnection connection) {
        // no connection closing registered as streaming continues downstream
        connection.sendRequest(request, on(this::performClientExchange));
    }

    @Override
    protected void writeRequest(ClientExchange clientExchange) {
        StreamSinkChannel requestChannel = clientExchange.getRequestChannel();
        try (ReadableByteChannel source = Channels.newChannel(bodyStream)) {
            IOHelper.transfer(source, requestChannel);
            flush(requestChannel);
        } catch (final IOException e) {
            hasFailedWith(e);
        }
    }
}
