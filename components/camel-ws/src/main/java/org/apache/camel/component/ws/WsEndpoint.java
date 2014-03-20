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
package org.apache.camel.component.ws;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.CharArrayReader;
import java.io.IOException;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import javax.net.ssl.SSLContext;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.AsyncHttpProvider;
import com.ning.http.client.providers.grizzly.GrizzlyAsyncHttpProvider;
import com.ning.http.client.websocket.WebSocket;
import com.ning.http.client.websocket.WebSocketByteListener;
import com.ning.http.client.websocket.WebSocketTextListener;
import com.ning.http.client.websocket.WebSocketUpgradeHandler;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.util.jsse.SSLContextParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class WsEndpoint extends DefaultEndpoint {
    private static final transient Logger LOG = LoggerFactory.getLogger(WsEndpoint.class);

    private static final boolean GRIZZLY_AVAILABLE = 
        probeClass("com.ning.http.client.providers.grizzly.GrizzlyAsyncHttpProvider");
    
    private AsyncHttpClient client;
    private AsyncHttpClientConfig clientConfig;
    private WebSocket websocket;
    private Set<WsConsumer> consumers;
    private URI wsUri;
    private boolean throwExceptionOnFailure = true;
    private boolean transferException;
    private SSLContextParameters sslContextParameters;
    private boolean useStreaming;

    private static boolean probeClass(String name) {
        try {
            Class.forName(name, true, WsEndpoint.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
    
    public WsEndpoint(String endpointUri, WsComponent component) {
        super(endpointUri, component);
        this.consumers = new HashSet<WsConsumer>();
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


    @Override
    public boolean isSingleton() {
        return true;
    }

    WebSocket getWebSocket() {
        synchronized (this) {
            if (websocket == null) {
                try { 
                    connect();
                } catch (Exception e) {
                    // TODO add the throw exception in the method 
                    e.printStackTrace();
                }
            }
        }
        return websocket;
    }

    void setWebSocket(WebSocket websocket) {
        this.websocket = websocket;
    }

    public AsyncHttpClientConfig getClientConfig() {
        return clientConfig;
    }

    public void setClientConfig(AsyncHttpClientConfig clientConfig) {
        this.clientConfig = clientConfig;
    }

    public boolean isThrowExceptionOnFailure() {
        return throwExceptionOnFailure;
    }

    public void setThrowExceptionOnFailure(boolean throwExceptionOnFailure) {
        this.throwExceptionOnFailure = throwExceptionOnFailure;
    }

    public boolean isTransferException() {
        return transferException;
    }

    public void setTransferException(boolean transferException) {
        this.transferException = transferException;
    }
    
    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }

    public URI getWsUri() {
        return wsUri;
    }

    public void setWsUri(URI wsUri) {
        this.wsUri = wsUri;
    }

    /**
     * @return the useStreaming
     */
    public boolean isUseStreaming() {
        return useStreaming;
    }

    /**
     * @param useStreaming the useStreaming to set
     */
    public void setUseStreaming(boolean useStreaming) {
        this.useStreaming = useStreaming;
    }

    public void connect() throws InterruptedException, ExecutionException, IOException {
        websocket = client.prepareGet(wsUri.toASCIIString()).execute(
            new WebSocketUpgradeHandler.Builder()
                .addWebSocketListener(new WsListener()).build()).get();
    }
    
    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (client == null) {
            
            AsyncHttpClientConfig config = null;
            
            if (clientConfig != null) {
                AsyncHttpClientConfig.Builder builder = WsComponent.cloneConfig(clientConfig);
                
                if (sslContextParameters != null) {
                    SSLContext ssl = sslContextParameters.createSSLContext();
                    builder.setSSLContext(ssl);
                }
                
                config = builder.build();
            } else {
                if (sslContextParameters != null) {
                    AsyncHttpClientConfig.Builder builder = new AsyncHttpClientConfig.Builder();
                    SSLContext ssl = sslContextParameters.createSSLContext();
                    builder.setSSLContext(ssl);
                    config = builder.build();
                }
            }
            
            if (config == null) {
                config = new AsyncHttpClientConfig.Builder().build();
            }
            
            AsyncHttpProvider ahp = getAsyncHttpProvider(config);
            if (ahp == null) {
                client = new AsyncHttpClient(config);
            } else {
                client = new AsyncHttpClient(ahp, config);
            }
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (websocket != null && websocket.isOpen()) {
            websocket.close();
        }
        if (client != null && !client.isClosed()) {
            client.close();
        }
        client = null;
    }

    void connect(WsConsumer wsConsumer) {
        consumers.add(wsConsumer);
    }

    void disconnect(WsConsumer wsConsumer) {
        consumers.remove(wsConsumer);
    }
    
    class WsListener implements WebSocketTextListener, WebSocketByteListener {
        private ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
        private StringBuffer textBuffer = new StringBuffer();
        
        @Override
        public void onOpen(WebSocket websocket) {
            LOG.info("websocket opened");
        }

        @Override
        public void onClose(WebSocket websocket) {
            LOG.info("websocket closed");
        }

        @Override
        public void onError(Throwable t) {
            LOG.error("websocket on error", t);
        }

        @Override
        public void onMessage(byte[] message) {
            LOG.info("received message --> {}", message);
            for (WsConsumer consumer : consumers) {
                consumer.sendMessage(message);
            }
        }

        @Override
        public void onFragment(byte[] fragment, boolean last) {
            if (LOG.isInfoEnabled()) {
                LOG.info("received fragment({}) --> {}", last, fragment);
            }
            // for now, construct a memory based stream. In future, we provide a fragmented stream that can
            // be consumed before the final fragment is added.
            synchronized (byteBuffer) {
                try {
                    byteBuffer.write(fragment);
                } catch (IOException e) {
                    //ignore
                }
                if (last) {
                    //REVIST avoid using baos/bais that waste memory
                    byte[] msg = byteBuffer.toByteArray();
                    for (WsConsumer consumer : consumers) {
                        consumer.sendMessage(new ByteArrayInputStream(msg));
                    }
                    byteBuffer.reset();
                }
            }
        }


        @Override
        public void onMessage(String message) {
            LOG.info("received message --> {}", message);
            for (WsConsumer consumer : consumers) {
                consumer.sendMessage(message);
            }
        }

        @Override
        public void onFragment(String fragment, boolean last) {
            if (LOG.isInfoEnabled()) {
                LOG.info("received fragment({}) --> {}", last, fragment);
            }
            // for now, construct a memory based stream. In future, we provide a fragmented stream that can
            // be consumed before the final fragment is added.
            synchronized (textBuffer) {
                textBuffer.append(fragment);
                if (last) {
                    //REVIST avoid using sb/car that waste memory
                    char[] msg = new char[textBuffer.length()];
                    textBuffer.getChars(0, msg.length, msg, 0);
                    for (WsConsumer consumer : consumers) {
                        consumer.sendMessage(new CharArrayReader(msg));
                    }
                    textBuffer.setLength(0);
                }
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
