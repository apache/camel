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

package org.apache.camel.component.smb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import javax.net.SocketFactory;

import com.hierynomus.smbj.SmbConfig;
import org.junit.jupiter.api.Test;

public class SmbDisconnectIT extends SmbServerTestSupport {

    public static final String SOCKET_TRACKING_SMB_CONFIG = "socketTrackingSmbConfig";

    protected String getSmbUrl() {
        return String.format(
                "smb:%s/%s/disconnect?username=%s&password=%s&smbConfig=#%s&disconnect=true&initialDelay=5000",
                service.address(),
                service.shareName(),
                service.userName(),
                service.password(),
                SOCKET_TRACKING_SMB_CONFIG);
    }

    @Test
    public void testProducerDisconnects() {
        TrackingSocketFactory socketFactory = createAndBindSocketTrackingSmbConfig();
        sendFile(getSmbUrl(), "Hello World", "hello.txt");

        assertEquals("Hello World", new String(copyFileContentFromContainer("/data/rw/disconnect/hello.txt")));
        assertSocketsAreClosed(socketFactory, 1, "There should be one tracked and closed socket (producer)");
    }

    @Test
    public void testConsumerDisconnects() {
        TrackingSocketFactory socketFactory = createAndBindSocketTrackingSmbConfig();
        sendFile(getSmbUrl(), "Hello World", "hello.txt");
        String out = consumer.receiveBody(getSmbUrl(), 5000L, String.class);

        assertEquals("Hello World", out);
        assertSocketsAreClosed(
                socketFactory, 2, "There should be two tracked and closed sockets (producer and consumer)");
    }

    private TrackingSocketFactory createAndBindSocketTrackingSmbConfig() {
        TrackingSocketFactory socketFactory = new TrackingSocketFactory();
        context.getRegistry()
                .bind(
                        SOCKET_TRACKING_SMB_CONFIG,
                        SmbConfig.builder().withSocketFactory(socketFactory).build());
        return socketFactory;
    }

    private void assertSocketsAreClosed(TrackingSocketFactory socketFactory, int expectedAmount, String message) {
        assertEquals(expectedAmount, socketFactory.getTrackedSockets().size(), message);
        socketFactory.getTrackedSockets().forEach(socket -> {
            assertTrue(socket.isClosed(), "Socket should be closed after disconnect");
        });
    }

    /**
     * SocketFactory that tracks created sockets for connection/disconnection testing.
     */
    private static class TrackingSocketFactory extends SocketFactory {
        private final SocketFactory delegate = SocketFactory.getDefault();
        private final List<Socket> trackedSockets = new ArrayList<>();

        public List<Socket> getTrackedSockets() {
            return trackedSockets;
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            Socket socket = delegate.createSocket(host, port);
            trackedSockets.add(socket);
            return socket;
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
            Socket socket = delegate.createSocket(host, port, localHost, localPort);
            trackedSockets.add(socket);
            return socket;
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            Socket socket = delegate.createSocket(host, port);
            trackedSockets.add(socket);
            return socket;
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort)
                throws IOException {
            Socket socket = delegate.createSocket(address, port, localAddress, localPort);
            trackedSockets.add(socket);
            return socket;
        }
    }
}
