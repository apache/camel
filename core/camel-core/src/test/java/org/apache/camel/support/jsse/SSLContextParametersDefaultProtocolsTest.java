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
package org.apache.camel.support.jsse;

import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SSLContextParametersDefaultProtocolsTest {

    @Test
    public void testDefaultProtocolsAreTls12OrNewer() throws Exception {
        SSLContext context = new SSLContextParameters().createSSLContext(null);

        SSLEngine engine = context.createSSLEngine();
        SSLSocket socket = (SSLSocket) context.getSocketFactory().createSocket();
        SSLServerSocket serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();

        for (String[] protocols : List.of(engine.getEnabledProtocols(), socket.getEnabledProtocols(),
                serverSocket.getEnabledProtocols())) {
            List<String> list = List.of(protocols);
            assertFalse(list.isEmpty());
            assertFalse(list.contains("TLSv1"), list.toString());
            assertFalse(list.contains("TLSv1.1"), list.toString());
            assertTrue(list.contains("TLSv1.2") || list.contains("TLSv1.3"), list.toString());
        }

        // the server socket uses the same protocols and cipher suites as the socket and engine
        assertEquals(List.of(engine.getEnabledProtocols()), List.of(serverSocket.getEnabledProtocols()));
        assertEquals(List.of(engine.getEnabledCipherSuites()), List.of(serverSocket.getEnabledCipherSuites()));
    }

    @Test
    public void testExplicitOlderProtocol() throws Exception {
        SSLContextParameters scp = new SSLContextParameters();
        SecureSocketProtocolsParameters protocols = new SecureSocketProtocolsParameters();
        protocols.setSecureSocketProtocol(List.of("TLSv1.2"));
        scp.setSecureSocketProtocols(protocols);

        SSLEngine engine = scp.createSSLContext(null).createSSLEngine();
        assertEquals(List.of("TLSv1.2"), List.of(engine.getEnabledProtocols()));
    }
}
