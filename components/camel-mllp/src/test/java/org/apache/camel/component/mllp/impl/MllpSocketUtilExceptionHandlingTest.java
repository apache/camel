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

import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertTrue;

public class MllpSocketUtilExceptionHandlingTest {
    Logger logger = LoggerFactory.getLogger(this.getClass());
    Socket socket;

    @Before
    public void setUp() throws Exception {
        socket = new FakeSocket();
    }

    @Test
    public void testClose() throws Exception {
        MllpSocketUtil.close(socket, null, null);

        assertTrue("Socket should be closed", socket.isClosed());
    }

    @Test
    public void testCloseWithLogger() throws Exception {
        MllpSocketUtil.close(socket, logger, null);

        assertTrue("Socket should be closed", socket.isClosed());
    }

    @Test
    public void testCloseWithLoggerAndReason() throws Exception {
        MllpSocketUtil.close(socket, logger, "Testing " + this.getClass().getSimpleName() + ".close(...)");

        assertTrue("Socket should be closed", socket.isClosed());
    }


    @Test
    public void testReset() throws Exception {
        MllpSocketUtil.reset(socket, null, null);

        assertTrue("Socket should be closed", socket.isClosed());
    }

    @Test
    public void testResetWithLogger() throws Exception {
        MllpSocketUtil.reset(socket, logger, null);

        assertTrue("Socket should be closed", socket.isClosed());
    }

    @Test
    public void testResetWithLoggerAndReason() throws Exception {
        MllpSocketUtil.reset(socket, logger, "Testing " + this.getClass().getSimpleName() + ".reset(...)");

        assertTrue("Socket should be closed", socket.isClosed());
    }


    // Utility Methods

    class FakeSocket extends Socket {
        boolean connected = true;
        boolean closed;

        FakeSocket() {
        }

        @Override
        public boolean isInputShutdown() {
            return false;
        }

        @Override
        public boolean isOutputShutdown() {
            return false;
        }

        @Override
        public void setSoLinger(boolean on, int linger) throws SocketException {
            throw new SocketException("Faking a setSoLinger failure");
        }

        @Override
        public void shutdownInput() throws IOException {
            throw new IOException("Faking a shutdownInput failure");
        }

        @Override
        public void shutdownOutput() throws IOException {
            throw new IOException("Faking a shutdownOutput failure");
        }

        @Override
        public boolean isConnected() {
            return connected;
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public synchronized void close() throws IOException {
            closed = true;
            throw new IOException("Faking a close failure");
        }
    }
}
