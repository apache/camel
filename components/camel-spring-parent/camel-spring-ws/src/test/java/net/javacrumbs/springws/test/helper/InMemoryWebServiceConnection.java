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
package net.javacrumbs.springws.test.helper;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import org.springframework.ws.WebServiceMessage;
import org.springframework.ws.WebServiceMessageFactory;
import org.springframework.ws.context.DefaultMessageContext;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.transport.WebServiceConnection;
import org.springframework.ws.transport.WebServiceMessageReceiver;

/**
 * Connection that calls a {@link WebServiceMessageReceiver} in memory.
 * <p>
 * Re-homed into Camel from the abandoned {@code net.javacrumbs:spring-ws-test} library (last released 2011, Apache
 * License 2.0) so camel-spring-ws no longer depends on it. See CAMEL-23440.
 */
class InMemoryWebServiceConnection implements WebServiceConnection {

    private final URI uri;

    private final WebServiceMessageFactory messageFactory;

    private final WebServiceMessageReceiver webServiceMessageReceiver;

    private MessageContext context;

    InMemoryWebServiceConnection(URI uri, WebServiceMessageFactory messageFactory,
                                 WebServiceMessageReceiver messageReceiver) {
        this.uri = uri;
        this.messageFactory = messageFactory;
        this.webServiceMessageReceiver = messageReceiver;
    }

    @Override
    public void send(WebServiceMessage message) throws IOException {
        context = createMessageContext(message);
        try {
            getWebServiceMessageReceiver().receive(context);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    @Override
    public WebServiceMessage receive(WebServiceMessageFactory messageFactory) throws IOException {
        return context.getResponse();
    }

    protected DefaultMessageContext createMessageContext(WebServiceMessage message) {
        return new DefaultMessageContext(message, messageFactory);
    }

    @Override
    public void close() throws IOException {
    }

    @Override
    public String getErrorMessage() throws IOException {
        return null;
    }

    @Override
    public URI getUri() throws URISyntaxException {
        return uri;
    }

    @Override
    public boolean hasError() throws IOException {
        return false;
    }

    public WebServiceMessageFactory getMessageFactory() {
        return messageFactory;
    }

    public WebServiceMessageReceiver getWebServiceMessageReceiver() {
        return webServiceMessageReceiver;
    }

    public MessageContext getContext() {
        return context;
    }
}
