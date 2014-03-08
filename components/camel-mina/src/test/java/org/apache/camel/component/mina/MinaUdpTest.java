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
package org.apache.camel.component.mina;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

/**
 * @version 
 */
public class MinaUdpTest extends BaseMinaTest {
    protected int messageCount = 3;

    @Test
    public void testMinaRoute() throws Exception {
        MockEndpoint endpoint = getMockEndpoint("mock:result");
        endpoint.expectedBodiesReceived("Hello Message: 0", "Hello Message: 1", "Hello Message: 2");

        sendUdpMessages();

        assertMockEndpointsSatisfied();
    }

    protected void sendUdpMessages() throws Exception {
        DatagramSocket socket = new DatagramSocket();
        try {
            InetAddress address = InetAddress.getByName("127.0.0.1");
            for (int i = 0; i < messageCount; i++) {
                String text = "Hello Message: " + i;
                byte[] data = text.getBytes();

                DatagramPacket packet = new DatagramPacket(data, data.length, address, getPort());
                socket.send(packet);
                Thread.sleep(100);
            }
        } finally {
            socket.close();
        }
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                int port2 = getNextPort();

                from("mina:udp://127.0.0.1:{{port}}?sync=false").to("mina:udp://127.0.0.1:" + port2 + "?sync=false");

                from("mina:udp://127.0.0.1:" + port2 + "?sync=false").to("mock:result");
            }
        };
    }
}
