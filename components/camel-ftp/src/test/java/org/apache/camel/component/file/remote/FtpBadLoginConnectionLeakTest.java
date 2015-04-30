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
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.SocketFactory;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.junit.Test;

public class FtpBadLoginConnectionLeakTest extends FtpServerTestSupport {

    private final AtomicInteger exceptionCount = new AtomicInteger(0);

    private String getFtpUrl() {
        return "ftp://dummy@localhost:" + getPort() + "/badlogin?password=cantremeber&maximumReconnectAttempts=0" +
                "&throwExceptionOnConnectFailed=false&ftpClient.socketFactory=#sf";
    }

    /**
     * Mapping of socket hashcode to two element tab ([connect() called, close() called])
     */
    private Map<Integer, boolean[]> socketAudits = new HashMap<Integer, boolean[]>();

    @Override

    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();

        final SocketFactory defaultSocketFactory = SocketFactory.getDefault();
        SocketFactory sf = new AuditingSocketFactory();
        jndi.bind("sf", sf);
        return jndi;
    }

    @Test
    public void testConnectionLeak() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        // let's have several login attempts
        Thread.sleep(3000L);

        stopCamelContext();

        for (Map.Entry<Integer, boolean[]> socketStats : socketAudits.entrySet()) {
            assertTrue("Socket should be connected", socketStats.getValue()[0]);
            assertEquals("Socket should be closed", socketStats.getValue()[1], socketStats.getValue()[0]);
        }

        mock.assertIsSatisfied();
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(getFtpUrl()).to("mock:result");
            }
        };
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
            socketAudits.put(System.identityHashCode(socket), new boolean[] { false, false });
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
            socketAudits.get(System.identityHashCode(this))[0] = true;
        }

        @Override
        public synchronized void close() throws IOException {
            super.close();
            socketAudits.get(System.identityHashCode(this))[1] = true;
        }
    }

}
