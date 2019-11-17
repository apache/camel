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
package org.apache.camel.component.spring.ws.bean;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ws.WebServiceMessage;
import org.springframework.ws.WebServiceMessageFactory;
import org.springframework.ws.transport.WebServiceConnection;

/**
 * Passes wsa:replyTo message back to the camel routing
 */
public class CamelDirectConnection implements WebServiceConnection {
    private static final Logger LOG = LoggerFactory.getLogger(CamelDirectConnection.class);
    private CamelContext camelContext;
    private URI destination;

    public CamelDirectConnection(CamelContext camelContext, URI uri) throws URISyntaxException {
        this.camelContext = camelContext;
        destination = new URI("direct:" + uri);
    }

    @Override
    public void send(WebServiceMessage message) throws IOException {
        try {
            camelContext.createProducerTemplate().sendBody(destination.toString(), message);
        } catch (CamelExecutionException e) {
            // simply discard replyTo message
            LOG.warn("Could not found any camel endpoint [" + destination + "] for wsa:ReplyTo camel mapping.", e);
        }
    }

    @Override
    public WebServiceMessage receive(WebServiceMessageFactory messageFactory) throws IOException {
        return null;
    }

    @Override
    public URI getUri() throws URISyntaxException {
        return destination;
    }

    @Override
    public boolean hasError() throws IOException {
        return false;
    }

    @Override
    public String getErrorMessage() throws IOException {
        return null;
    }

    @Override
    public void close() throws IOException {

    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

}
