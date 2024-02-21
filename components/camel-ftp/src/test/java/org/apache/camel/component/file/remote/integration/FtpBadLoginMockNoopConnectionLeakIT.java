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
package org.apache.camel.component.file.remote.integration;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.net.SocketFactory;

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.remote.FtpEndpoint;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.commons.net.ftp.FTPClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test which checks leaking connections when FTP server returns correct status for NOOP operation.
 */
public class FtpBadLoginMockNoopConnectionLeakIT extends FtpServerTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(FtpBadLoginMockNoopConnectionLeakIT.class);

    /**
     * Mapping of socket hashcode to two element tab ([connect() called, close() called])
     */
    private final Map<Integer, boolean[]> socketAudits = new HashMap<>();

    @BindToRegistry("sf")
    private final SocketFactory sf = new AuditingSocketFactory();

    private String getFtpUrl() {
        return "ftp://dummy@localhost:{{ftp.server.port}}/badlogin?password=cantremeber&maximumReconnectAttempts=3"
               + "&throwExceptionOnConnectFailed=false&ftpClient.socketFactory=#sf";
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();

        FtpEndpoint<?> endpoint = context.getEndpoint(getFtpUrl(), FtpEndpoint.class);
        endpoint.setFtpClient(new FTPClient() {
            @Override
            public boolean sendNoOp() {
                // return true as long as connection is established
                return this.isConnected();
            }
        });
    }

    @Test
    public void testConnectionLeak() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        // let's have several login attempts
        await().atMost(3, TimeUnit.SECONDS).until(() -> socketAudits.size() > 0);

        stopCamelContext();

        for (Map.Entry<Integer, boolean[]> socketStats : socketAudits.entrySet()) {
            assertTrue(socketStats.getValue()[0], "Socket should be connected");
            assertEquals(socketStats.getValue()[0], socketStats.getValue()[1], "Socket should be closed");
        }

        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(getFtpUrl()).to("mock:result");
            }
        };
    }

    /**
     * {@link SocketFactory} which creates {@link Socket}s that expose statistics about
     * {@link Socket#connect(SocketAddress)}/{@link Socket#close()} invocations
     */
    private class AuditingSocketFactory extends SocketFactory {

        @Override
        public Socket createSocket(String s, int i) {
            return null;
        }

        @Override
        public Socket createSocket(String s, int i, InetAddress inetAddress, int i1) {
            return null;
        }

        @Override
        public Socket createSocket(InetAddress inetAddress, int i) {
            return null;
        }

        @Override
        public Socket createSocket() {
            AuditingSocket socket = new AuditingSocket();
            socketAudits.put(System.identityHashCode(socket), new boolean[] { false, false });
            return socket;
        }

        @Override
        public Socket createSocket(InetAddress inetAddress, int i, InetAddress inetAddress1, int i1) {
            return null;
        }
    }

    /**
     * {@link Socket} which counts connect()/close() invocations
     */
    private class AuditingSocket extends Socket {

        @Override
        public void connect(SocketAddress endpoint, int timeout) throws IOException {
            LOG.info("Connecting socket {}", System.identityHashCode(this));
            super.connect(endpoint, timeout);
            boolean[] value = socketAudits.get(System.identityHashCode(this));
            value[0] = true;
        }

        @Override
        public synchronized void close() throws IOException {
            LOG.info("Disconnecting socket {}", System.identityHashCode(this));
            super.close();
            boolean[] value = socketAudits.get(System.identityHashCode(this));
            value[1] = true;
        }
    }

}
