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
package org.apache.camel.component.netty;

import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class NettyUDPLargeMessageInOnlyTest extends BaseNettyTest {

    private byte[] getMessageBytes(int messageSize) {
        byte[] msgBytes = new byte[messageSize];
        for (int i = 0; i < messageSize; i++) {
            msgBytes[i] = 'A';
        }
        return msgBytes;
    }

    private void sendMessage(int messageSize) throws Exception {
        byte[] msgBytes = getMessageBytes(messageSize);

        assertEquals(msgBytes.length, messageSize);
        String message = new String(msgBytes);

        getMockEndpoint("mock:result").expectedBodiesReceived(message);
        template.sendBody("netty:udp://localhost:{{port}}?sync=false", message);
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSend512Message() throws Exception {
        sendMessage(512);
    }

    @Test
    public void testSend768Message() throws Exception {
        sendMessage(768);
    }

    @Test
    public void testSend1024Message() throws Exception {
        sendMessage(1024);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("netty:udp://localhost:{{port}}?receiveBufferSizePredictor=2048&sync=false")
                    .to("mock:result");
            }
        };
    }
}
