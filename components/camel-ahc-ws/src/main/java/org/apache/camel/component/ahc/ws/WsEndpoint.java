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

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.AsyncHttpProvider;
import com.ning.http.client.providers.grizzly.GrizzlyAsyncHttpProvider;
import com.ning.http.client.ws.WebSocket;
import com.ning.http.client.ws.WebSocketByteListener;
import com.ning.http.client.ws.WebSocketTextListener;
import com.ning.http.client.ws.WebSocketUpgradeHandler;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.ahc.AhcEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@UriEndpoint(scheme = "ahc-ws,ahc-wss", extendsScheme = "ahc,ahc", title = "AHC Websocket,AHC Secure Websocket",
        syntax = "ahc-ws:httpUri", consumerClass = WsConsumer.class, label = "websocket")
public class WsEndpoint extends AhcEndpoint {
    private static final transient Logger LOG = LoggerFactory.getLogger(WsEndpoint.class);

    // for using websocket streaming/fragments
    private static final boolean GRIZZLY_AVAILABLE = 
        probeClass("com.ning.http.client.providers.grizzly.GrizzlyAsyncHttpProvider");

    private final Set<WsConsumer> consumers  = new HashSet<WsConsumer>();

    private WebSocket websocket;
    @UriParam
    private boolean useStreaming;
    
    public WsEndpoint(String endpointUri, WsComponent component) {
        super(endpointUri, component, null);
    }

    private static boolean probeClass(String name) {
        try {
            Class.forName(name, true, WsEndpoint.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
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
        return new WsConsumer(this, processor);
    }

    WebSocket getWebSocket() throws Exception {
        synchronized (this) {
            if (websocket == null) {
                connect();
            }
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

    @Override
    protected AsyncHttpClient createClient(AsyncHttpClientConfig config) {
        AsyncHttpClient client;
        if (config == null) {
            config = new AsyncHttpClientConfig.Builder().build();
        }
        AsyncHttpProvider ahp = getAsyncHttpProvider(config);
        if (ahp == null) {
            client = new AsyncHttpClient(config);
        } else {
            client = new AsyncHttpClient(ahp, config);
        }
        return client; 
    }

    public void connect() throws InterruptedException, ExecutionException, IOException {
        websocket = getClient().prepareGet(getHttpUri().toASCIIString()).execute(
            new WebSocketUpgradeHandler.Builder()
                .addWebSocketListener(new WsListener()).build()).get();
    }
    
    @Override
    protected void doStop() throws Exception {
        if (websocket != null && websocket.isOpen()) {
            websocket.close();
            websocket = null;
        }
        super.doStop();
    }

    void connect(WsConsumer wsConsumer) {
        consumers.add(wsConsumer);
    }

    void disconnect(WsConsumer wsConsumer) {
        consumers.remove(wsConsumer);
    }
    
    class WsListener implements WebSocketTextListener, WebSocketByteListener {
                
        @Override
        public void onOpen(WebSocket websocket) {
            LOG.debug("websocket opened");
        }

        @Override
        public void onClose(WebSocket websocket) {
            LOG.debug("websocket closed");
        }

        @Override
        public void onError(Throwable t) {
            LOG.error("websocket on error", t);
        }

        @Override
        public void onMessage(byte[] message) {
            LOG.debug("received message --> {}", message);
            for (WsConsumer consumer : consumers) {
                consumer.sendMessage(message);
            }
        }

        @Override
        public void onMessage(String message) {
            LOG.debug("received message --> {}", message);
            for (WsConsumer consumer : consumers) {
                consumer.sendMessage(message);
            }
        }

    }
    
    protected AsyncHttpProvider getAsyncHttpProvider(AsyncHttpClientConfig config) {
        if (GRIZZLY_AVAILABLE) {
            return new GrizzlyAsyncHttpProvider(config);
        }
        return null;
    }
}
