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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.AsyncHttpClientConfig;
import org.asynchttpclient.DefaultAsyncHttpClient;
import org.asynchttpclient.ws.WebSocket;
import org.asynchttpclient.ws.WebSocketListener;
import org.asynchttpclient.ws.WebSocketUpgradeHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestClient {
    private static final Logger LOG = LoggerFactory.getLogger(TestClient.class);
    
    private List<Object> received;
    private CountDownLatch latch;
    private AsyncHttpClient client;
    private WebSocket websocket;
    private String url;
    
    public TestClient(String url, AsyncHttpClientConfig conf) {
        this(url, conf, 1);
    }

    public TestClient(String url, int count) {
        this(url, null, count);
    }

    public TestClient(String url) {
        this(url, null, 1);
    }

    public TestClient(String url, AsyncHttpClientConfig conf, int count) {
        this.received = new ArrayList<>();
        this.latch = new CountDownLatch(count);
        this.client = conf == null ? new DefaultAsyncHttpClient() : new DefaultAsyncHttpClient(conf);
        this.url = url;
    }
    
    public void connect() throws InterruptedException, ExecutionException, IOException {
        websocket = client.prepareGet(url).execute(
            new WebSocketUpgradeHandler.Builder()
                .addWebSocketListener(new TestWebSocketListener()).build()).get();
    }

    public void sendTextMessage(String message) {
        websocket.sendTextFrame(message);
    }

    public void sendBytesMessage(byte[] message) {
        websocket.sendBinaryFrame(message);
    }

    public boolean await(int secs) throws InterruptedException {
        return latch.await(secs, TimeUnit.SECONDS);
    }

    public void reset(int count) {
        latch = new CountDownLatch(count);
        received.clear();
    }

    public List<Object> getReceived() {
        return received;
    }

    public <T> List<T> getReceived(Class<T> cls) {
        List<T> list = new ArrayList<>();
        for (Object o : received) {
            list.add(getValue(o, cls));
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    private static <T> T getValue(Object o, Class<T> cls) {
        if (cls.isInstance(o)) {
            return (T)o;
        } else if (cls == String.class) {
            if (o instanceof byte[]) {
                return (T)new String((byte[])o);
            } else {
                return (T)o.toString();
            }
        } else if (cls == byte[].class) {
            if (o instanceof String) {
                return (T)((String)o).getBytes();
            }
        }
        return null;
    }
    
    public void close() throws IOException {
        websocket.sendCloseFrame();
        client.close();
    }

    private class TestWebSocketListener implements WebSocketListener {

        @Override
        public void onOpen(WebSocket websocket) {
            LOG.info("[ws] opened");
        }

        @Override
        public void onClose(WebSocket websocket, int code, String reason) {
            LOG.info("[ws] closed");
        }

        @Override
        public void onError(Throwable t) {
            LOG.error("[ws] error", t);
        }

        @Override
        public void onBinaryFrame(byte[] message, boolean finalFragment, int rsv) {
            received.add(message);
            LOG.info("[ws] received bytes --> " + Arrays.toString(message));
            latch.countDown();
        }

        
        @Override
        public void onTextFrame(String message, boolean finalFragment, int rsv) {
            received.add(message);
            LOG.info("[ws] received --> " + message);
            latch.countDown();
        }

       
        
    }
}
