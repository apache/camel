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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.eclipse.jetty.websocket.WebSocket;
import org.eclipse.jetty.websocket.WebSocketServlet;

public class TestServlet extends WebSocketServlet {
    private static final byte OPCODE_CONTINUATION = 0x00;
    private static final byte OPCODE_TEXT = 0x01;
    private static final byte OPCODE_BINARY = 0x02;
    private static final byte FLAGS_FINAL = 0x08;
    
    private static final long serialVersionUID = 1L;
    
    private List<Object> messages;
    
    public TestServlet(List<Object> messages) {
        this.messages = messages;
    }

    @Override
    public WebSocket doWebSocketConnect(HttpServletRequest request, String protocol) {
        return new TestWsSocket();
    }

    private class TestWsSocket implements WebSocket.OnTextMessage, WebSocket.OnBinaryMessage,
        WebSocket.OnFrame {
        protected Connection con;
        protected ByteArrayOutputStream frameBuffer = new ByteArrayOutputStream();
        
        @Override
        public void onOpen(Connection connection) {
            con = connection;
        }

        @Override
        public void onClose(int i, String s) {
        }

        @Override
        public void onMessage(String data) {
            try {
                messages.add(data);
                con.sendMessage(data);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onMessage(byte[] data, int offset, int length) {
            try {
                if (length < data.length) {
                    byte[] odata = data;
                    data = new byte[length];
                    System.arraycopy(odata, offset, data, 0, length);
                    offset = 0;
                }
                messages.add(data);
                con.sendMessage(data, offset, length);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        public void onMessage(byte[] data, int offset, int length, boolean last) {
            frameBuffer.write(data, offset, length);
            if (last) {
                data = frameBuffer.toByteArray();
                frameBuffer.reset();
                messages.add(data);
                try {
                    con.sendMessage(data, 0, data.length);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        
        @Override
        public boolean onFrame(byte flags, byte opcode, byte[] data, int offset, int length) {
            if (OPCODE_TEXT == opcode || OPCODE_BINARY == opcode) {
                if (0 != (FLAGS_FINAL & flags)) {
                    // a non-framed text or binary
                    return false;
                }
                onMessage(data, offset, length, false);
                return true;
            } else if (OPCODE_CONTINUATION == opcode) {
                boolean f = 0 != (FLAGS_FINAL & flags);
                onMessage(data, offset, length, f);
                return !f;
            }
            return false;
        }

        @Override
        public void onHandshake(FrameConnection connection) {
        }
    }
}
