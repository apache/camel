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
package org.apache.camel.component.mina;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class MinaUdpConcurrentTest extends BaseMinaTest {

    protected int messageCount = 3;

    public MinaUdpConcurrentTest() {
    }

    @Test
    public void testMinaRoute() throws Exception {
        MockEndpoint endpoint = getMockEndpoint("mock:result");
        endpoint.expectedBodiesReceivedInAnyOrder("Hello Message: 0", "Hello Message: 1", "Hello Message: 2");

        sendUdpMessages();

        assertMockEndpointsSatisfied();
    }

    protected void sendUdpMessages() throws Exception {
        DatagramSocket socket = new DatagramSocket();
        try {
            InetAddress address = InetAddress.getByName("127.0.0.1");
            for (int i = 0; i < messageCount; i++) {
                String text = "Hello Message: " + Integer.toString(i);
                byte[] data = text.getBytes();

                DatagramPacket packet = new DatagramPacket(data, data.length, address, getPort());
                socket.send(packet);
            }
            Thread.sleep(2000);
        } finally {
            socket.close();
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // we use un-ordered to allow processing the UDP messages in any order from same client
                from("mina:udp://127.0.0.1:" + getPort() + "?sync=false&minaLogger=true&orderedThreadPoolExecutor=false")
                        .delay(1000)
                        .to("mock:result");
            }
        };
    }
}
