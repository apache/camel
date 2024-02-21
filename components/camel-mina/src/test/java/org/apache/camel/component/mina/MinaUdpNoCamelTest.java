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

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;

import org.apache.camel.test.AvailablePortFinder;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.codec.textline.LineDelimiter;
import org.apache.mina.filter.logging.LoggingFilter;
import org.apache.mina.transport.socket.DatagramSessionConfig;
import org.apache.mina.transport.socket.nio.NioDatagramAcceptor;
import org.apache.mina.transport.socket.nio.NioDatagramConnector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class MinaUdpNoCamelTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(MinaUdpNoCamelTest.class);
    final Charset charset = Charset.defaultCharset();
    final LineDelimiter delimiter = LineDelimiter.DEFAULT;
    final MinaTextLineCodecFactory codecFactory = new MinaTextLineCodecFactory(charset, delimiter);
    UDPServer server;
    private final int port = AvailablePortFinder.getNextAvailable();

    // Create the UDPServer before the test is run
    @BeforeEach
    public void setupUDPAcceptor() throws IOException {
        server = new UDPServer("127.0.0.1", port);
        server.listen();
    }

    @AfterEach
    public void closeUDPAcceptor() {
        server.close();
    }

    @Test
    public void testMinaUDPWithNoCamel() {
        UDPClient client = new UDPClient();
        client.connect("127.0.0.1", port);
        for (int i = 0; i < 222; i++) {
            client.sendNoMina("Hello Mina " + i + System.lineSeparator());
        }

        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(222, server.numMessagesReceived));
    }

    /*
     * Mina UDP Server
     */
    private final class UDPServer extends IoHandlerAdapter {

        private final String host;
        private final int port;
        private final NioDatagramAcceptor acceptor;
        private int numMessagesReceived;

        private UDPServer(String host, int port) {
            this.host = host;
            this.port = port;
            acceptor = new NioDatagramAcceptor();
            DatagramSessionConfig sessionConfig = acceptor.getSessionConfig();
            sessionConfig.setReuseAddress(true);
            acceptor.getFilterChain().addLast("codec", new ProtocolCodecFilter(codecFactory));
            acceptor.getFilterChain().addLast("logger", new LoggingFilter());
            acceptor.setHandler(this);

        }

        public void listen() throws IOException {
            acceptor.bind(new InetSocketAddress(host, port));

        }

        public void close() {
            acceptor.unbind();
        }

        @Override
        public void messageReceived(IoSession session, Object message) {
            LOGGER.debug("UDPServer Received body: {}", message);
            numMessagesReceived++;
        }

        @Override
        public void exceptionCaught(IoSession session, Throwable cause) {
            LOGGER.error("Ooops! Something went wrong :|", cause);
        }
    }

    private final class UDPClient extends IoHandlerAdapter {

        /**
         * Three optional arguments can be provided (defaults in brackets): path, host (localhost) and port.
         *
         * @param args The command line args.
         */
        private final NioDatagramConnector connector;
        private DatagramSocket socket;
        private InetAddress address;
        private int localPort;
        private String localHost = "127.0.0.1";

        private UDPClient() {
            connector = new NioDatagramConnector();
            connector.getFilterChain().addLast("codec", new ProtocolCodecFilter(codecFactory));
            connector.setHandler(this);

        }

        public void connect(String host, int port) {
            localPort = port;
            localHost = host;
            try {
                socket = new DatagramSocket();
                address = InetAddress.getByName(localHost);

            } catch (UnknownHostException ex) {
                LOGGER.warn(null, ex);
            } catch (SocketException ex) {
                LOGGER.warn(null, ex);
            }
        }

        public void sendNoMina(String msg) {
            try {
                DatagramPacket packet = new DatagramPacket(msg.getBytes(), msg.getBytes().length, address, localPort);
                socket.send(packet);
            } catch (IOException ex) {
                LOGGER.warn(null, ex);
            }
        }

        @Override
        public void messageReceived(IoSession session, Object message) {
            LOGGER.debug("Client Received body: {}", message);
        }

        @Override
        public void exceptionCaught(IoSession session, Throwable cause) {
            LOGGER.error("Ooops! Something went wrong :|", cause);
        }
    }
}
