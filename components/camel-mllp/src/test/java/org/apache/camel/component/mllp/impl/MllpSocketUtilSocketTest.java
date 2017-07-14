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
package org.apache.camel.component.mllp.impl;

import java.net.ServerSocket;
import java.net.Socket;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class MllpSocketUtilSocketTest {
    Logger logger = LoggerFactory.getLogger(this.getClass());
    ServerSocket serverSocket;
    Socket socket;

    @Before
    public void setUp() throws Exception {
        serverSocket = new ServerSocket(0);
        socket = new Socket();
    }

    @After
    public void tearDown() throws Exception {
        if (socket != null) {
            socket.close();
        }
        serverSocket.close();
    }

    @Test
    public void testSetSoTimeout() throws Exception {
        final int expected = 1000;

        connect();

        MllpSocketUtil.setSoTimeout(socket, expected, null, null);

        assertEquals(expected, socket.getSoTimeout());
    }

    @Test
    public void testSetSoTimeoutWithLogger() throws Exception {
        final int expected = 1000;

        connect();

        MllpSocketUtil.setSoTimeout(socket, expected, logger, null);

        assertEquals(expected, socket.getSoTimeout());
    }

    @Test
    public void testSetSoTimeoutWithLoggerAndReason() throws Exception {
        final int expected = 1000;

        connect();

        MllpSocketUtil.setSoTimeout(socket, expected, logger, "Testing setSoTimeout");

        assertEquals(expected, socket.getSoTimeout());
    }

    @Test
    public void testSetSoTimeoutWithUnconnectedSocket() throws Exception {
        int expected = 1000;

        MllpSocketUtil.setSoTimeout(socket, expected, logger, "Testing setSoTimeout with unconnected Socket");

        assertEquals(expected, socket.getSoTimeout());
    }

    @Test
    public void testSetSoTimeoutWithClosedSocket() throws Exception {
        int expected = 1000;

        connect();
        close();

        MllpSocketUtil.setSoTimeout(socket, expected, logger, "Testing setSoTimeout with closed Socket");

        // We can't get the SO_TIMEOUT from a closed socket (Socket.getSoTimeout() will throw a SocketException
        // assertEquals(expected, socket.getSoTimeout());
    }

    @Test
    public void testSetSoTimeoutWithResetSocket() throws Exception {
        int expected = 1000;

        connect();
        close();

        MllpSocketUtil.close(socket, null, null);

        MllpSocketUtil.setSoTimeout(socket, expected, logger, "Testing setSoTimeout with reset Socket");

        // We can't get the SO_TIMEOUT from a closed socket (Socket.getSoTimeout() will throw a SocketException
        // assertEquals(expected, socket.getSoTimeout());
    }

    @Test
    public void testClose() throws Exception {
        connect();

        MllpSocketUtil.close(socket, null, null);

        assertTrue("Socket should be closed", socket.isClosed());
    }

    @Test
    public void testCloseWithLogger() throws Exception {
        connect();

        MllpSocketUtil.close(socket, logger, null);

        assertTrue("Socket should be closed", socket.isClosed());
    }

    @Test
    public void testCloseWithLoggerAndReason() throws Exception {
        connect();

        MllpSocketUtil.close(socket, logger, "Testing close");

        assertTrue("Socket should be closed", socket.isClosed());
    }

    @Test
    public void testCloseWithUnconnectedSocket() throws Exception {
        MllpSocketUtil.close(socket, logger, "Testing close with unconnected Socket");

        assertFalse("Socket should NOT closed because it was never connected", socket.isClosed());
    }

    @Test
    public void testCloseWithClosedSocket() throws Exception {
        connect();
        close();

        MllpSocketUtil.close(socket, logger, "Testing close with closed Socket");

        assertTrue("Socket should be closed", socket.isClosed());
    }

    @Test
    public void testCloseWithResetSocket() throws Exception {
        connect();
        reset();

        MllpSocketUtil.close(socket, logger, "Testing close with reset Socket");

        assertTrue("Socket should be closed", socket.isClosed());
    }

    @Test
    public void testReset() throws Exception {
        connect();

        MllpSocketUtil.reset(socket, null, null);

        assertTrue("Socket should be closed", socket.isClosed());
    }

    @Test
    public void testResetWithLogger() throws Exception {
        connect();

        MllpSocketUtil.reset(socket, logger, null);

        assertTrue("Socket should be closed", socket.isClosed());
    }

    @Test
    public void testResetWithLoggerAndReason() throws Exception {
        connect();

        MllpSocketUtil.reset(socket, logger, "Testing reset");

        assertTrue("Socket should be closed", socket.isClosed());
    }

    @Test
    public void testResetWithUnconnectedSocket() throws Exception {
        MllpSocketUtil.reset(socket, logger, "Testing reset with unconnected Socket");

        assertFalse("Socket should NOT closed because it was never connected", socket.isClosed());
    }

    @Test
    public void testResetWithClosedSocket() throws Exception {
        connect();
        close();

        MllpSocketUtil.reset(socket, logger, "Testing reset with closed Socket");

        assertTrue("Socket should be closed", socket.isClosed());
    }

    @Test
    public void testResetWithResetSocket() throws Exception {
        connect();
        reset();

        MllpSocketUtil.reset(socket, logger, "Testing reset with reset Socket");

        assertTrue("Socket should be closed", socket.isClosed());
    }

    @Test
    public void testGetAddressString() throws Exception {
        connect();

        String address = MllpSocketUtil.getAddressString(socket);

        assertNotNull("Should have an address string", address);
    }

    @Test
    public void testGetAddressStringWithUnconnectedSocket() throws Exception {
        String address = MllpSocketUtil.getAddressString(socket);

        assertNotNull("Should have an address string", address);
    }

    @Test
    public void testGetAddressStringWithClosedSocket() throws Exception {
        connect();
        close();

        String address = MllpSocketUtil.getAddressString(socket);

        assertNotNull("Should have an address string", address);
    }

    // Utility Methods

    private void connect() throws Exception {
        if (socket != null) {
            socket = new Socket(serverSocket.getInetAddress().getHostAddress(), serverSocket.getLocalPort());
        } else {
            socket.connect(serverSocket.getLocalSocketAddress());
        }

        assertTrue("Socket should be open", socket.isConnected() && !socket.isClosed());
    }

    private void close() throws Exception {
        if (socket != null) {
            if (socket.isConnected() && !socket.isClosed()) {
                socket.close();
            }

            assertTrue("Socket should have been connected and closed", socket.isConnected() && socket.isClosed());
        }
    }

    private void reset() throws Exception {
        if (socket != null) {
            if (socket.isConnected() && !socket.isClosed()) {
                socket.setSoLinger(true, 0);
                socket.close();
            }

            assertTrue("Socket should have been connected and closed", socket.isConnected() && socket.isClosed());
        }
    }
}