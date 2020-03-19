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
package org.apache.camel.component.atmosphere.websocket;

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.servlet.ServletEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;

/**
 * To exchange data with external Websocket clients using Atmosphere.
 */
@UriEndpoint(firstVersion = "2.14.0", scheme = "atmosphere-websocket", extendsScheme = "servlet", title = "Atmosphere Websocket",
        syntax = "atmosphere-websocket:servicePath", label = "websocket")
@Metadata(excludeProperties = "httpUri,contextPath,authMethod,authMethodPriority,authUsername,authPassword,authDomain,authHost"
                + "proxyAuthScheme,proxyAuthMethod,proxyAuthUsername,proxyAuthPassword,proxyAuthHost,proxyAuthPort,proxyAuthDomain")
public class WebsocketEndpoint extends ServletEndpoint {

    private WebSocketStore store;
    private WebsocketConsumer websocketConsumer;

    @UriPath(description = "Name of websocket endpoint") @Metadata(required = true)
    private String servicePath;
    @UriParam
    private boolean sendToAll;
    @UriParam
    private boolean useStreaming;
    
    public WebsocketEndpoint(String endPointURI, WebsocketComponent component, URI httpUri) throws URISyntaxException {
        super(endPointURI, component, httpUri);

        //TODO find a better way of assigning the store
        int idx = endPointURI.indexOf('?');
        String name = idx > -1 ? endPointURI.substring(0, idx) : endPointURI;

        this.servicePath = name;
        this.store = component.getWebSocketStore(servicePath);
    }
    
    @Override
    public Producer createProducer() throws Exception {
        return new WebsocketProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        websocketConsumer = new WebsocketConsumer(this, processor);
        return websocketConsumer;
    }

    public boolean isSendToAll() {
        return sendToAll;
    }

    /**
     * Whether to send to all (broadcast) or send to a single receiver.
     */
    public void setSendToAll(boolean sendToAll) {
        this.sendToAll = sendToAll;
    }
    
    public boolean isUseStreaming() {
        return useStreaming;
    }

    /**
     * To enable streaming to send data as multiple text fragments.
     */
    public void setUseStreaming(boolean useStreaming) {
        this.useStreaming = useStreaming;
    }

    WebSocketStore getWebSocketStore() {
        return store;
    }

    public WebsocketConsumer getWebsocketConsumer() {
        return websocketConsumer;
    }
}
