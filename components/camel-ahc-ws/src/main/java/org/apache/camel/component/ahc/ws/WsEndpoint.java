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
package org.apache.camel.component.ahc.ws;

import java.util.HashSet;
import java.util.Set;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.ahc.AhcEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.AsyncHttpClientConfig;
import org.asynchttpclient.DefaultAsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClientConfig;
import org.asynchttpclient.ws.DefaultWebSocketListener;
import org.asynchttpclient.ws.WebSocket;
import org.asynchttpclient.ws.WebSocketUpgradeHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * To exchange data with external Websocket servers using <a href="http://github.com/sonatype/async-http-client">Async Http Client</a>.
 */
@UriEndpoint(firstVersion = "2.14.0", scheme = "ahc-ws,ahc-wss", extendsScheme = "ahc,ahc", title = "AHC Websocket,AHC Secure Websocket",
        syntax = "ahc-ws:httpUri", consumerClass = WsConsumer.class, label = "websocket")
public class WsEndpoint extends AhcEndpoint {
    private static final transient Logger LOG = LoggerFactory.getLogger(WsEndpoint.class);

    private final Set<WsConsumer> consumers = new HashSet<WsConsumer>();
    private final WsListener listener = new WsListener();
    private transient WebSocket websocket;

    @UriParam(label = "producer")
    private boolean useStreaming;
    @UriParam(label = "consumer")
    private boolean sendMessageOnError;

    public WsEndpoint(String endpointUri, WsComponent component) {
        super(endpointUri, component, null);
    }

    @Override
    public WsComponent getComponent() {
        return (WsComponent) super.getComponent();
    }

    @Override
    public Producer createProducer() throws Exception {
        return new WsProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        return new WsConsumer(this, processor);
    }

    WebSocket getWebSocket() throws Exception {
        synchronized (this) {
            // ensure we are connected
            reConnect();
        }
        return websocket;
    }

    void setWebSocket(WebSocket websocket) {
        this.websocket = websocket;
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

    public boolean isSendMessageOnError() {
        return sendMessageOnError;
    }

    /**
     * Whether to send an message if the web-socket listener received an error.
     */
    public void setSendMessageOnError(boolean sendMessageOnError) {
        this.sendMessageOnError = sendMessageOnError;
    }

    @Override
    protected AsyncHttpClient createClient(AsyncHttpClientConfig config) {
        AsyncHttpClient client;
        if (config == null) {
            config = new DefaultAsyncHttpClientConfig.Builder().build();
            client = new DefaultAsyncHttpClient(config);
        } else {
            client = new DefaultAsyncHttpClient(config);
        }
        return client;
    }

    public void connect() throws Exception {
        String uri = getHttpUri().toASCIIString();

        LOG.debug("Connecting to {}", uri);
        websocket = getClient().prepareGet(uri).execute(
            new WebSocketUpgradeHandler.Builder()
                .addWebSocketListener(listener).build()).get();
    }

    @Override
    protected void doStop() throws Exception {
        if (websocket != null && websocket.isOpen()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Disconnecting from {}", getHttpUri().toASCIIString());
            }
            websocket.removeWebSocketListener(listener);
            websocket.close();
            websocket = null;
        }
        super.doStop();
    }

    void connect(WsConsumer wsConsumer) throws Exception {
        consumers.add(wsConsumer);
        reConnect();
    }

    void disconnect(WsConsumer wsConsumer) {
        consumers.remove(wsConsumer);
    }

    void reConnect() throws Exception {
        if (websocket == null || !websocket.isOpen()) {
            String uri = getHttpUri().toASCIIString();
            LOG.info("Reconnecting websocket: {}", uri);
            connect();
        }
    }

    class WsListener extends DefaultWebSocketListener {

        @Override
        public void onOpen(WebSocket websocket) {
            LOG.debug("Websocket opened");
        }

        @Override
        public void onClose(WebSocket websocket) {
            LOG.debug("websocket closed - reconnecting");
            try {
                reConnect();
            } catch (Exception e) {
                LOG.warn("Error re-connecting to websocket", e);
            }
        }

        @Override
        public void onError(Throwable t) {
            LOG.debug("websocket on error", t);
            if (isSendMessageOnError()) {
                for (WsConsumer consumer : consumers) {
                    consumer.sendMessage(t);
                }
            }
        }

        @Override
        public void onMessage(byte[] message) {
            LOG.debug("Received message --> {}", message);
            for (WsConsumer consumer : consumers) {
                consumer.sendMessage(message);
            }
        }

        @Override
        public void onMessage(String message) {
            LOG.debug("Received message --> {}", message);
            for (WsConsumer consumer : consumers) {
                consumer.sendMessage(message);
            }
        }

    }

}
