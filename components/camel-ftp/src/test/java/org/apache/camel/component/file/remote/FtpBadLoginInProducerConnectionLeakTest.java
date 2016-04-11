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
package org.apache.camel.component.file.remote;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Map;
import javax.net.SocketFactory;

import org.apache.camel.impl.JndiRegistry;
import org.junit.Test;

public class FtpBadLoginInProducerConnectionLeakTest extends FtpServerTestSupport {

    /**
     * Mapping of socket hashcode to two element tab ([connect() called, close() called])
     */
    private Map<Integer, boolean[]> socketAudits = new HashMap<Integer, boolean[]>();

    private String getFtpUrl() {
        return "ftp://dummy@localhost:" + getPort() + "/badlogin?password=cantremeber&maximumReconnectAttempts=3"
            + "&throwExceptionOnConnectFailed=false&ftpClient.socketFactory=#sf";
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();

        SocketFactory sf = new AuditingSocketFactory();
        jndi.bind("sf", sf);
        return jndi;
    }

    @Test
    public void testConnectionLeak() throws Exception {
        for (String filename : new String[] {"claus.txt", "grzegorz.txt"}) {
            try {
                sendFile(getFtpUrl(), "Hello World", filename);
            } catch (Exception ignored) {
                // expected
            }
        }

        // maximumReconnectAttempts is related to TCP connects, not to FTP login attempts
        // but having this parameter > 0 leads to two connection attempts
        assertEquals("Expected 4 socket connections to be created", 4, socketAudits.size());

        for (Map.Entry<Integer, boolean[]> socketStats : socketAudits.entrySet()) {
            assertTrue("Socket should be connected", socketStats.getValue()[0]);
            assertEquals("Socket should be closed", socketStats.getValue()[0], socketStats.getValue()[1]);
        }
    }

    /**
     * {@link SocketFactory} which creates {@link Socket}s that expose statistics about {@link Socket#connect(SocketAddress)}/{@link Socket#close()}
     * invocations
     */
    private class AuditingSocketFactory extends SocketFactory {

        @Override
        public Socket createSocket(String s, int i) throws IOException {
            return null;
        }

        @Override
        public Socket createSocket(String s, int i, InetAddress inetAddress, int i1) throws IOException {
            return null;
        }

        @Override
        public Socket createSocket(InetAddress inetAddress, int i) throws IOException {
            return null;
        }

        @Override
        public Socket createSocket() throws IOException {
            AuditingSocket socket = new AuditingSocket();
            socketAudits.put(System.identityHashCode(socket), new boolean[] {false, false});
            return socket;
        }

        @Override
        public Socket createSocket(InetAddress inetAddress, int i, InetAddress inetAddress1, int i1) throws IOException {
            return null;
        }
    }

    /**
     * {@link Socket} which counts connect()/close() invocations
     */
    private class AuditingSocket extends Socket {

        @Override
        public void connect(SocketAddress endpoint, int timeout) throws IOException {
            super.connect(endpoint, timeout);
            boolean[] value = socketAudits.get(System.identityHashCode(this));
            value[0] = true;
        }

        @Override
        public synchronized void close() throws IOException {
            super.close();
            boolean[] value = socketAudits.get(System.identityHashCode(this));
            value[1] = true;
        }
    }

}
