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

import java.io.IOException;
import java.io.InputStream;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;
import org.asynchttpclient.ws.WebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class WsProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(WsProducer.class);

    private static final int DEFAULT_STREAM_BUFFER_SIZE = 127;
    
    private int streamBufferSize = DEFAULT_STREAM_BUFFER_SIZE;

    public WsProducer(WsEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public WsEndpoint getEndpoint() {
        return (WsEndpoint) super.getEndpoint();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        Message in = exchange.getIn();
        Object message = in.getBody();
        if (message != null) {
            LOG.debug("Sending out {}", message);
            if (message instanceof String) {
                sendMessage(getWebSocket(), (String)message, getEndpoint().isUseStreaming());
            } else if (message instanceof byte[]) {
                sendMessage(getWebSocket(), (byte[])message, getEndpoint().isUseStreaming());
            } else if (message instanceof InputStream) {
                sendStreamMessage(getWebSocket(), (InputStream)message);
            } else {
                //TODO provide other binding option, for now use the converted string
                getWebSocket().sendTextFrame(in.getMandatoryBody(String.class));
            }
        }
    }
    
    private void sendMessage(WebSocket webSocket, String msg, boolean streaming) {
        if (streaming) {
            int p = 0;
            while (p < msg.length()) {
                if (msg.length() - p < streamBufferSize) {
                    webSocket.sendTextFrame(msg.substring(p), true, 0);
                    p = msg.length();
                } else {
                    webSocket.sendTextFrame(msg.substring(p, streamBufferSize), false, 0);
                    p += streamBufferSize;
                }
            }
        } else {
            webSocket.sendTextFrame(msg);
        }
    }
    
    private void sendMessage(WebSocket webSocket, byte[] msg, boolean streaming) {
        if (streaming) {
            int p = 0;
            byte[] writebuf = new byte[streamBufferSize];
            while (p < msg.length) {
                if (msg.length - p < streamBufferSize) {
                    int rest = msg.length - p;
                    // bug in grizzly? we need to create a byte array with the exact length
                    //webSocket.stream(msg, p, rest, true);
                    System.arraycopy(msg, p, writebuf, 0, rest);
                    byte[] tmpbuf = new byte[rest];
                    System.arraycopy(writebuf, 0, tmpbuf, 0, rest);
                    webSocket.sendBinaryFrame(tmpbuf, true, 0);
                    // ends
                    p = msg.length;
                } else {
                    // bug in grizzly? we need to create a byte array with the exact length
                    //webSocket.stream(msg, p, streamBufferSize, false);
                    System.arraycopy(msg, p, writebuf, 0, streamBufferSize);
                    webSocket.sendBinaryFrame(writebuf, false, 0);
                    // ends
                    p += streamBufferSize;
                }
            }
        } else {
            webSocket.sendBinaryFrame(msg);
        }
    }

    private void sendStreamMessage(WebSocket webSocket, InputStream in) throws IOException {
        byte[] readbuf = new byte[streamBufferSize];
        byte[] writebuf = new byte[streamBufferSize];
        int rn = 0;
        int wn = 0;
        try {
            while ((rn = in.read(readbuf, 0, readbuf.length)) != -1) {
                if (wn > 0) {
                    webSocket.sendBinaryFrame(writebuf, false, 0);
                }
                System.arraycopy(readbuf, 0, writebuf, 0, rn);
                wn = rn;
            }
            // a bug in grizzly? we need to create a byte array with the exact length
            if (wn < writebuf.length) {
                byte[] tmpbuf = writebuf;
                writebuf = new byte[wn];
                System.arraycopy(tmpbuf, 0, writebuf, 0, wn);
            } // ends
            webSocket.sendBinaryFrame(writebuf, true, 0);
        } finally {
            in.close();
        }
    }
    
    private WebSocket getWebSocket() throws Exception {
        return getEndpoint().getWebSocket();
    }
}
