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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * With sync=false the endpoints are one-way: the producer keeps the message of an InOut exchange, and the consumer does
 * not write a reply.
 */
public class MinaSyncFalseTest extends BaseMinaTest {

    private int udpPort;

    @Test
    public void testInOutProducerKeepsBodyAndHeaders() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:tcp");
        mock.expectedBodiesReceived("Hello World");

        Exchange out = template.request(String.format("mina:tcp://localhost:%d?sync=false&textline=true", getPort()), e -> {
            e.getMessage().setBody("Hello World");
            e.getMessage().setHeader("foo", "bar");
        });

        MockEndpoint.assertIsSatisfied(context);
        assertEquals("Hello World", out.getMessage().getBody());
        assertEquals("bar", out.getMessage().getHeader("foo"));
    }

    @Test
    public void testTcpConsumerDoesNotReply() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:tcp");
        mock.expectedBodiesReceived("Hello World");

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", getPort()));
            socket.setSoTimeout(1000);
            OutputStream os = socket.getOutputStream();
            os.write("Hello World\n".getBytes(StandardCharsets.US_ASCII));
            os.flush();

            MockEndpoint.assertIsSatisfied(context);

            // no reply is written back, and the session stays open
            BufferedReader reader
                    = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
            assertThrows(SocketTimeoutException.class, reader::readLine);
        }
    }

    @Test
    public void testUdpConsumerDoesNotReply() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:udp");
        mock.expectedBodiesReceived("Hello World");

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(1000);
            InetAddress address = InetAddress.getByName("127.0.0.1");
            byte[] data = "Hello World".getBytes(StandardCharsets.US_ASCII);
            socket.send(new DatagramPacket(data, data.length, address, udpPort));

            MockEndpoint.assertIsSatisfied(context);

            // no datagram is sent back
            byte[] buf = new byte[128];
            DatagramPacket receive = new DatagramPacket(buf, buf.length);
            assertThrows(SocketTimeoutException.class, () -> socket.receive(receive));
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                udpPort = getNextPort();

                fromF("mina:tcp://localhost:%d?sync=false&textline=true", getPort()).to("mock:tcp");
                fromF("mina:udp://127.0.0.1:%d?sync=false", udpPort).convertBodyTo(String.class).to("mock:udp");
            }
        };
    }
}
