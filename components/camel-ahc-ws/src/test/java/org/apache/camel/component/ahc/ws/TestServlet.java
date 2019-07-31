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
import java.nio.ByteBuffer;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;

@WebSocket
public class TestServlet {
    private Session session;

    public TestServlet() {
    }

    @OnWebSocketConnect
    public void handleConnect(Session session) {
        this.session = session;
    }

    @OnWebSocketMessage
    public void handleMessage(String message) {
        TestMessages.getInstance().addMessage(message);
        // send back same data
        send(message);
    }

    @OnWebSocketMessage
    public void handleMessage(byte[] message, int offset, int length) {
        TestMessages.getInstance().addMessage(message);
        // send back same data
        send(message);
    }

    private void send(String message) {
        try {
            if (session.isOpen()) {
                session.getRemote().sendString(message);
            }
        } catch (IOException e) {
            // ignore
        }
    }

    private void send(byte[] bytes) {
        try {
            if (session.isOpen()) {
                session.getRemote().sendBytes(ByteBuffer.wrap(bytes));
            }
        } catch (IOException e) {
            // ignore
        }
    }

}
