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
package org.apache.camel.component.ahc.ws;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.ahc.AhcEndpoint;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.util.SensitiveUtils;
import org.apache.camel.util.URISupport;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.AsyncHttpClientConfig;
import org.asynchttpclient.DefaultAsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClientConfig;
import org.asynchttpclient.ws.WebSocket;
import org.asynchttpclient.ws.WebSocketListener;
import org.asynchttpclient.ws.WebSocketUpgradeHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Exchange data with external Websocket servers using <a href="http://github.com/sonatype/async-http-client">Async Http
 * Client</a>.
 */
@UriEndpoint(firstVersion = "2.14.0", scheme = "ahc-ws,ahc-wss", extendsScheme = "ahc,ahc",
             title = "Async HTTP Client (AHC) Websocket,Async HTTP Client (AHC) Secure Websocket",
             syntax = "ahc-ws:httpUri", category = { Category.WEBSOCKET })
public class WsEndpoint extends AhcEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(WsEndpoint.class);

    private final Set<WsConsumer> consumers = new HashSet<>();
    private final WsListener listener = new WsListener();
    private transient WebSocket websocket;
    private transient String sanitizedUri;

    @UriParam(label = "producer")
    private boolean useStreaming;
    @UriParam(label = "consumer")
    private boolean sendMessageOnError;

    public WsEndpoint(String endpointUri, WsComponent component) {
        super(endpointUri, component, null);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (getHttpUri() != null) {
            sanitizedUri = URISupport.sanitizeUri(getHttpUri().toASCIIString());
        }
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
        WsConsumer consumer = new WsConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
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

    public void connect() throws ExecutionException, InterruptedException {
        String uri = getHttpUri().toASCIIString();

        LOG.debug("Connecting to {}", sanitizedUri);
        websocket = getClient().prepareGet(uri).execute(
                new WebSocketUpgradeHandler.Builder()
                        .addWebSocketListener(listener).build())
                .get();
    }

    @Override
    protected void doStop() throws Exception {
        if (websocket != null && websocket.isOpen()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Disconnecting from {}", sanitizedUri);
            }
            websocket.removeWebSocketListener(listener);
            websocket.sendCloseFrame();
            websocket = null;
        }
        super.doStop();
    }

    void connect(WsConsumer wsConsumer) throws ExecutionException, InterruptedException {
        consumers.add(wsConsumer);
        reConnect();
    }

    void disconnect(WsConsumer wsConsumer) {
        consumers.remove(wsConsumer);
    }

    void reConnect() throws ExecutionException, InterruptedException {
        if (websocket == null || !websocket.isOpen()) {
            LOG.info("Reconnecting websocket: {}", sanitizedUri);
            connect();
        }
    }

    class WsListener implements WebSocketListener {

        @Override
        public void onOpen(WebSocket websocket) {
            LOG.debug("Websocket opened");
        }

        @Override
        public void onClose(WebSocket socket, int code, String reason) {
            LOG.debug("websocket closed - reconnecting");
            try {
                if (websocket != null) {
                    // set websocket to null and remove the listener
                    websocket.removeWebSocketListener(listener);
                    websocket.sendCloseFrame();
                    websocket = null;
                }
                reConnect();
            } catch (ExecutionException | InterruptedException e) {
                LOG.warn("Error re-connecting to websocket", e);
                ExceptionHandler exceptionHandler = getExceptionHandler();
                if (exceptionHandler != null) {
                    exceptionHandler.handleException("Error re-connecting to websocket", e);
                }
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
        public void onBinaryFrame(byte[] message, boolean finalFragment, int rsv) {
            LOG.debug("Received message --> {}", message);
            for (WsConsumer consumer : consumers) {
                consumer.sendMessage(message);
            }
        }

        @Override
        public void onTextFrame(String message, boolean finalFragment, int rsv) {
            LOG.debug("Received message --> {}", message);
            for (WsConsumer consumer : consumers) {
                consumer.sendMessage(message);
            }
        }

        @Override
        public void onPingFrame(byte[] payload) {
            LOG.debug("Received ping --> {}", payload);
            websocket.sendPongFrame(payload);
        }
    }

}
