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

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;

import org.apache.camel.CamelContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;
import org.junit.jupiter.api.parallel.Isolated;

import static org.junit.jupiter.api.Assertions.*;

@Isolated("This test is regularly flaky")
public class SSLContextParametersTest extends AbstractJsseParametersTest {

    @Test
    public void testFilter() {
        SSLContextParameters parameters = new SSLContextParameters();

        Collection<String> result;
        result = parameters.filter(null, Arrays.asList("SSLv3", "TLSv1", "TLSv1.1"), List.of(Pattern.compile("TLS.*")),
                List.of());
        assertEquals(2, result.size());
        assertStartsWith(result, "TLS");

        result = parameters.filter(null, Arrays.asList("SSLv3", "TLSv1", "TLSv1.1"), List.of(Pattern.compile(".*")),
                List.of(Pattern.compile("SSL.*")));
        assertEquals(2, result.size());
        assertStartsWith(result, "TLS");

        AssertionError error
                = assertThrows(AssertionError.class, () -> assertStartsWith((String[]) null, "TLS"),
                        "We should got an exception here!");
        assertTrue(error.getMessage().contains("The values should not be null"), "Get a wrong message");
    }

    @Test
    public void testPropertyPlaceholders() throws Exception {

        CamelContext camelContext = this.createPropertiesPlaceholderAwareContext();

        KeyStoreParameters ksp = new KeyStoreParameters();
        ksp.setCamelContext(camelContext);

        ksp.setType("{{keyStoreParameters.type}}");
        ksp.setProvider("{{keyStoreParameters.provider}}");
        ksp.setResource("{{keyStoreParameters.resource}}");
        ksp.setPassword("{{keyStoreParameters.password}}");

        KeyManagersParameters kmp = new KeyManagersParameters();
        kmp.setCamelContext(camelContext);
        kmp.setKeyStore(ksp);

        kmp.setKeyPassword("{{keyManagersParameters.keyPassword}}");
        kmp.setAlgorithm("{{keyManagersParameters.algorithm}}");
        kmp.setProvider("{{keyManagersParameters.provider}}");

        TrustManagersParameters tmp = new TrustManagersParameters();
        tmp.setCamelContext(camelContext);
        tmp.setKeyStore(ksp);

        tmp.setAlgorithm("{{trustManagersParameters.algorithm}}");
        tmp.setProvider("{{trustManagersParameters.provider}}");

        CipherSuitesParameters csp = new CipherSuitesParameters();
        csp.setCipherSuite(Collections.singletonList("{{cipherSuite.0}}"));

        SecureSocketProtocolsParameters sspp = new SecureSocketProtocolsParameters();
        sspp.setSecureSocketProtocol(Collections.singletonList("{{secureSocketProtocol.0}}"));

        SSLContextServerParameters scsp = new SSLContextServerParameters();
        scsp.setCamelContext(camelContext);
        scsp.setClientAuthentication("{{sslContextServerParameters.clientAuthentication}}");

        SSLContextParameters scp = new SSLContextParameters();
        scp.setCamelContext(camelContext);
        scp.setKeyManagers(kmp);
        scp.setTrustManagers(tmp);
        scp.setServerParameters(scsp);

        scp.setProvider("{{sslContextParameters.provider}}");
        scp.setSecureSocketProtocol("{{sslContextParameters.protocol}}");
        scp.setSessionTimeout("{{sslContextParameters.sessionTimeout}}");

        scp.setCipherSuites(csp);
        scp.setSecureSocketProtocols(sspp);

        SSLContext context = scp.createSSLContext(null);
        SSLServerSocket serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();
        assertTrue(serverSocket.getNeedClientAuth());
        context.getSocketFactory().createSocket();
        context.createSSLEngine();
    }

    @Test
    public void testServerParametersClientAuthentication() throws Exception {
        SSLContext controlContext = SSLContext.getInstance("TLS");
        controlContext.init(null, null, null);
        SSLEngine controlEngine = controlContext.createSSLEngine();
        SSLServerSocket controlServerSocket = (SSLServerSocket) controlContext.getServerSocketFactory().createServerSocket();

        SSLContextParameters scp = new SSLContextParameters();
        SSLContextServerParameters scsp = new SSLContextServerParameters();

        scp.setServerParameters(scsp);
        SSLContext context = scp.createSSLContext(null);

        SSLEngine engine = context.createSSLEngine();
        SSLServerSocket serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();

        assertEquals(controlServerSocket.getWantClientAuth(), serverSocket.getWantClientAuth());
        assertEquals(controlServerSocket.getNeedClientAuth(), serverSocket.getNeedClientAuth());
        assertEquals(controlEngine.getWantClientAuth(), engine.getWantClientAuth());
        assertEquals(controlEngine.getNeedClientAuth(), engine.getNeedClientAuth());

        // ClientAuthentication - NONE
        scsp.setClientAuthentication(ClientAuthentication.NONE.name());
        context = scp.createSSLContext(null);
        engine = context.createSSLEngine();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();

        assertFalse(serverSocket.getWantClientAuth());
        assertFalse(serverSocket.getNeedClientAuth());
        assertFalse(engine.getWantClientAuth());
        assertFalse(engine.getNeedClientAuth());

        // ClientAuthentication - WANT
        scsp.setClientAuthentication(ClientAuthentication.WANT.name());
        context = scp.createSSLContext(null);
        engine = context.createSSLEngine();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();

        assertTrue(serverSocket.getWantClientAuth());
        assertFalse(serverSocket.getNeedClientAuth());
        assertTrue(engine.getWantClientAuth());
        assertFalse(engine.getNeedClientAuth());

        // ClientAuthentication - REQUIRE
        scsp.setClientAuthentication(ClientAuthentication.REQUIRE.name());
        context = scp.createSSLContext(null);
        engine = context.createSSLEngine();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();

        assertFalse(serverSocket.getWantClientAuth());
        assertTrue(serverSocket.getNeedClientAuth());
        assertFalse(engine.getWantClientAuth());
        assertTrue(engine.getNeedClientAuth());
    }

    @Test
    public void testServerParameters() throws Exception {
        SSLContext controlContext = SSLContext.getInstance("TLSv1.3");
        controlContext.init(null, null, null);
        SSLEngine controlEngine = controlContext.createSSLEngine();
        SSLSocket controlSocket = (SSLSocket) controlContext.getSocketFactory().createSocket();
        SSLServerSocket controlServerSocket = (SSLServerSocket) controlContext.getServerSocketFactory().createServerSocket();

        SSLContextParameters scp = new SSLContextParameters();
        SSLContextServerParameters scsp = new SSLContextServerParameters();

        scp.setServerParameters(scsp);
        SSLContext context = scp.createSSLContext(null);

        SSLEngine engine = context.createSSLEngine();
        SSLSocket socket = (SSLSocket) context.getSocketFactory().createSocket();
        SSLServerSocket serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();

        assertStartsWith(engine.getEnabledProtocols(), "TLS");
        assertStartsWith(socket.getEnabledProtocols(), "TLS");
        assertStartsWith(serverSocket.getEnabledProtocols(), "TLS");

        assertArrayEquals(controlEngine.getEnabledCipherSuites(), engine.getEnabledCipherSuites());
        assertArrayEquals(controlSocket.getEnabledCipherSuites(), socket.getEnabledCipherSuites());
        assertArrayEquals(this.getDefaultCipherSuiteIncludes(controlServerSocket.getSupportedCipherSuites()),
                serverSocket.getEnabledCipherSuites());
        assertEquals(controlServerSocket.getWantClientAuth(), serverSocket.getWantClientAuth());
        assertEquals(controlServerSocket.getNeedClientAuth(), serverSocket.getNeedClientAuth());

        // No csp or filter on server params passes through shared config
        scp.setCipherSuites(new CipherSuitesParameters());
        context = scp.createSSLContext(null);
        engine = context.createSSLEngine();
        socket = (SSLSocket) context.getSocketFactory().createSocket();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();

        assertEquals(0, engine.getEnabledCipherSuites().length);
        assertEquals(0, socket.getEnabledCipherSuites().length);
        assertEquals(0, serverSocket.getEnabledCipherSuites().length);

        // Csp on server params
        scp.setCipherSuites(null);
        CipherSuitesParameters csp = new CipherSuitesParameters();
        scsp.setCipherSuites(csp);
        context = scp.createSSLContext(null);
        engine = context.createSSLEngine();
        socket = (SSLSocket) context.getSocketFactory().createSocket();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();

        assertArrayEquals(controlEngine.getEnabledCipherSuites(), engine.getEnabledCipherSuites());
        assertArrayEquals(controlSocket.getEnabledCipherSuites(), socket.getEnabledCipherSuites());
        assertEquals(0, serverSocket.getEnabledCipherSuites().length);

        // Cipher suites filter on server params
        FilterParameters filter = new FilterParameters();
        filter.getExclude().add(".*");
        scsp.setCipherSuites(null);
        scsp.setCipherSuitesFilter(filter);
        context = scp.createSSLContext(null);
        engine = context.createSSLEngine();
        socket = (SSLSocket) context.getSocketFactory().createSocket();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();

        assertArrayEquals(controlEngine.getEnabledCipherSuites(), engine.getEnabledCipherSuites());
        assertArrayEquals(controlSocket.getEnabledCipherSuites(), socket.getEnabledCipherSuites());
        assertEquals(0, serverSocket.getEnabledCipherSuites().length);

        // Csp on server overrides cipher suites filter on server
        filter.getInclude().add(".*");
        filter.getExclude().clear();
        scsp.setCipherSuites(csp);
        context = scp.createSSLContext(null);
        engine = context.createSSLEngine();
        socket = (SSLSocket) context.getSocketFactory().createSocket();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();

        assertArrayEquals(controlEngine.getEnabledCipherSuites(), engine.getEnabledCipherSuites());
        assertArrayEquals(controlSocket.getEnabledCipherSuites(), socket.getEnabledCipherSuites());
        assertEquals(0, serverSocket.getEnabledCipherSuites().length);

        // Sspp on server params
        SecureSocketProtocolsParameters sspp = new SecureSocketProtocolsParameters();
        scsp.setSecureSocketProtocols(sspp);
        context = scp.createSSLContext(null);
        engine = context.createSSLEngine();
        socket = (SSLSocket) context.getSocketFactory().createSocket();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();

        assertStartsWith(engine.getEnabledProtocols(), "TLS");
        assertStartsWith(socket.getEnabledProtocols(), "TLS");
        assertEquals(0, serverSocket.getEnabledProtocols().length);

        // Secure socket protocols filter on client params
        filter = new FilterParameters();
        filter.getExclude().add(".*");
        scsp.setSecureSocketProtocols(null);
        scsp.setSecureSocketProtocolsFilter(filter);
        engine = context.createSSLEngine();
        socket = (SSLSocket) context.getSocketFactory().createSocket();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();

        assertStartsWith(engine.getEnabledProtocols(), "TLS");
        assertStartsWith(socket.getEnabledProtocols(), "TLS");
        assertEquals(0, serverSocket.getEnabledProtocols().length);

        // Sspp on client params overrides secure socket protocols filter on
        // client
        filter.getInclude().add(".*");
        filter.getExclude().clear();
        scsp.setSecureSocketProtocols(sspp);
        context = scp.createSSLContext(null);
        engine = context.createSSLEngine();
        socket = (SSLSocket) context.getSocketFactory().createSocket();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();

        assertStartsWith(engine.getEnabledProtocols(), "TLS");
        assertStartsWith(socket.getEnabledProtocols(), "TLS");
        assertEquals(0, serverSocket.getEnabledProtocols().length);

        // Server session timeout only affects server session configuration
        scsp.setSessionTimeout("12345");
        context = scp.createSSLContext(null);
        engine = context.createSSLEngine();
        socket = (SSLSocket) context.getSocketFactory().createSocket();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();

        assertEquals(controlContext.getClientSessionContext().getSessionTimeout(),
                context.getClientSessionContext().getSessionTimeout());
        assertEquals(12345, context.getServerSessionContext().getSessionTimeout());
    }

    private void checkProtocols(String[] control, String[] configured) {
        // With the IBM JDK, an "default" unconfigured control socket is more
        // restricted than with the Sun JDK. For example, with
        // SSLContext.getInstance("TLS"), on Sun, you get
        // TLSv1, SSLv3, SSLv2Hello
        // but with IBM, you only get:
        // TLSv1
        // We'll check to make sure the "default" protocols are amongst the list
        // that are in after configuration.
        assertTrue(Arrays.asList(configured).containsAll(Arrays.asList(control)));
    }

    @Test
    public void testClientParameters() throws Exception {
        SSLContext controlContext = SSLContext.getInstance("TLSv1.3");
        controlContext.init(null, null, null);
        SSLEngine controlEngine = controlContext.createSSLEngine();
        SSLSocket controlSocket = (SSLSocket) controlContext.getSocketFactory().createSocket();
        SSLServerSocket controlServerSocket = (SSLServerSocket) controlContext.getServerSocketFactory().createServerSocket();

        SSLContextParameters scp = new SSLContextParameters();
        SSLContextClientParameters sccp = new SSLContextClientParameters();

        scp.setClientParameters(sccp);
        SSLContext context = scp.createSSLContext(null);

        SSLEngine engine = context.createSSLEngine();
        SSLSocket socket = (SSLSocket) context.getSocketFactory().createSocket();
        SSLServerSocket serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();

        assertStartsWith(engine.getEnabledProtocols(), "TLS");
        assertStartsWith(socket.getEnabledProtocols(), "TLS");
        assertStartsWith(serverSocket.getEnabledProtocols(), "TLS");

        assertArrayEquals(controlEngine.getEnabledCipherSuites(), engine.getEnabledCipherSuites());
        assertArrayEquals(controlSocket.getEnabledCipherSuites(), socket.getEnabledCipherSuites());
        assertArrayEquals(this.getDefaultCipherSuiteIncludes(controlServerSocket.getSupportedCipherSuites()),
                serverSocket.getEnabledCipherSuites());

        // No csp or filter on client params passes through shared config
        scp.setCipherSuites(new CipherSuitesParameters());
        context = scp.createSSLContext(null);
        engine = context.createSSLEngine();
        socket = (SSLSocket) context.getSocketFactory().createSocket();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();

        assertEquals(0, socket.getEnabledCipherSuites().length);

        // Csp on client params
        scp.setCipherSuites(null);
        CipherSuitesParameters csp = new CipherSuitesParameters();
        sccp.setCipherSuites(csp);
        context = scp.createSSLContext(null);
        engine = context.createSSLEngine();
        socket = (SSLSocket) context.getSocketFactory().createSocket();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();

        assertArrayEquals(controlEngine.getEnabledCipherSuites(), engine.getEnabledCipherSuites());
        assertEquals(0, socket.getEnabledCipherSuites().length);
        assertArrayEquals(this.getDefaultCipherSuiteIncludes(controlServerSocket.getSupportedCipherSuites()),
                serverSocket.getEnabledCipherSuites());

        // Cipher suites filter on client params
        FilterParameters filter = new FilterParameters();
        filter.getExclude().add(".*");
        sccp.setCipherSuites(null);
        sccp.setCipherSuitesFilter(filter);
        context = scp.createSSLContext(null);
        engine = context.createSSLEngine();
        socket = (SSLSocket) context.getSocketFactory().createSocket();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();

        assertArrayEquals(controlEngine.getEnabledCipherSuites(), engine.getEnabledCipherSuites());
        assertEquals(0, socket.getEnabledCipherSuites().length);
        assertArrayEquals(this.getDefaultCipherSuiteIncludes(controlServerSocket.getSupportedCipherSuites()),
                serverSocket.getEnabledCipherSuites());

        // Csp on client overrides cipher suites filter on client
        filter.getInclude().add(".*");
        filter.getExclude().clear();
        sccp.setCipherSuites(csp);
        context = scp.createSSLContext(null);
        engine = context.createSSLEngine();
        socket = (SSLSocket) context.getSocketFactory().createSocket();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();

        assertArrayEquals(controlEngine.getEnabledCipherSuites(), engine.getEnabledCipherSuites());
        assertEquals(0, socket.getEnabledCipherSuites().length);
        assertArrayEquals(this.getDefaultCipherSuiteIncludes(controlServerSocket.getSupportedCipherSuites()),
                serverSocket.getEnabledCipherSuites());

        // Sspp on client params
        SecureSocketProtocolsParameters sspp = new SecureSocketProtocolsParameters();
        sccp.setSecureSocketProtocols(sspp);
        context = scp.createSSLContext(null);
        engine = context.createSSLEngine();
        socket = (SSLSocket) context.getSocketFactory().createSocket();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();

        assertStartsWith(engine.getEnabledProtocols(), "TLS");
        assertEquals(0, socket.getEnabledProtocols().length);
        assertStartsWith(serverSocket.getEnabledProtocols(), "TLS");

        // Secure socket protocols filter on client params
        filter = new FilterParameters();
        filter.getExclude().add(".*");
        sccp.setSecureSocketProtocols(null);
        sccp.setSecureSocketProtocolsFilter(filter);
        engine = context.createSSLEngine();
        socket = (SSLSocket) context.getSocketFactory().createSocket();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();

        assertStartsWith(engine.getEnabledProtocols(), "TLS");
        assertEquals(0, socket.getEnabledProtocols().length);
        assertStartsWith(serverSocket.getEnabledProtocols(), "TLS");

        // Sspp on client params overrides secure socket protocols filter on
        // client
        filter.getInclude().add(".*");
        filter.getExclude().clear();
        sccp.setSecureSocketProtocols(sspp);
        context = scp.createSSLContext(null);
        engine = context.createSSLEngine();
        socket = (SSLSocket) context.getSocketFactory().createSocket();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();

        assertStartsWith(engine.getEnabledProtocols(), "TLS");
        assertEquals(0, socket.getEnabledProtocols().length);
        assertStartsWith(serverSocket.getEnabledProtocols(), "TLS");

        // Client session timeout only affects client session configuration
        sccp.setSessionTimeout("12345");
        context = scp.createSSLContext(null);
        engine = context.createSSLEngine();
        socket = (SSLSocket) context.getSocketFactory().createSocket();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();

        assertEquals(controlContext.getServerSessionContext().getSessionTimeout(),
                context.getServerSessionContext().getSessionTimeout());
        assertEquals(12345, context.getClientSessionContext().getSessionTimeout());
    }

    @Test
    public void testCipherSuites() throws Exception {
        SSLContext controlContext = SSLContext.getInstance("TLSv1.3");
        controlContext.init(null, null, null);
        SSLEngine controlEngine = controlContext.createSSLEngine();
        SSLSocket controlSocket = (SSLSocket) controlContext.getSocketFactory().createSocket();
        SSLServerSocket controlServerSocket = (SSLServerSocket) controlContext.getServerSocketFactory().createServerSocket();

        // default
        SSLContextParameters scp = new SSLContextParameters();

        SSLContext context = scp.createSSLContext(null);

        SSLEngine engine = context.createSSLEngine();
        SSLSocket socket = (SSLSocket) context.getSocketFactory().createSocket();
        SSLServerSocket serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();

        assertArrayEquals(controlEngine.getEnabledCipherSuites(), engine.getEnabledCipherSuites());
        assertArrayEquals(controlSocket.getEnabledCipherSuites(), socket.getEnabledCipherSuites());
        assertArrayEquals(this.getDefaultCipherSuiteIncludes(controlServerSocket.getSupportedCipherSuites()),
                serverSocket.getEnabledCipherSuites());

        // empty csp

        CipherSuitesParameters csp = new CipherSuitesParameters();
        scp.setCipherSuites(csp);
        context = scp.createSSLContext(null);
        engine = context.createSSLEngine();
        socket = (SSLSocket) context.getSocketFactory().createSocket();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();

        assertEquals(0, engine.getEnabledCipherSuites().length);
        assertEquals(0, socket.getEnabledCipherSuites().length);
        assertEquals(0, serverSocket.getEnabledCipherSuites().length);

        // explicit csp

        csp.setCipherSuite(Collections.singletonList(controlEngine.getEnabledCipherSuites()[0]));
        context = scp.createSSLContext(null);
        engine = context.createSSLEngine();
        socket = (SSLSocket) context.getSocketFactory().createSocket();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();

        assertEquals(1, engine.getEnabledCipherSuites().length);
        assertEquals(controlEngine.getEnabledCipherSuites()[0], engine.getEnabledCipherSuites()[0]);
        assertEquals(1, socket.getEnabledCipherSuites().length);
        assertEquals(controlEngine.getEnabledCipherSuites()[0], socket.getEnabledCipherSuites()[0]);
        assertEquals(1, serverSocket.getEnabledCipherSuites().length);
        assertEquals(controlEngine.getEnabledCipherSuites()[0], serverSocket.getEnabledCipherSuites()[0]);

        // explicit csp overrides filter

        FilterParameters filter = new FilterParameters();
        filter.getInclude().add(".*");
        scp.setCipherSuitesFilter(filter);
        context = scp.createSSLContext(null);
        engine = context.createSSLEngine();
        socket = (SSLSocket) context.getSocketFactory().createSocket();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();

        assertEquals(1, engine.getEnabledCipherSuites().length);
        assertEquals(controlEngine.getEnabledCipherSuites()[0], engine.getEnabledCipherSuites()[0]);
        assertEquals(1, socket.getEnabledCipherSuites().length);
        assertEquals(controlEngine.getEnabledCipherSuites()[0], socket.getEnabledCipherSuites()[0]);
        assertEquals(1, socket.getEnabledCipherSuites().length);
        assertEquals(controlEngine.getEnabledCipherSuites()[0], serverSocket.getEnabledCipherSuites()[0]);
    }

    @Test
    public void testCipherSuitesFilter() throws Exception {
        SSLContext controlContext = SSLContext.getInstance("TLSv1.3");
        controlContext.init(null, null, null);
        SSLEngine controlEngine = controlContext.createSSLEngine();
        SSLSocket controlSocket = (SSLSocket) controlContext.getSocketFactory().createSocket();
        SSLServerSocket controlServerSocket = (SSLServerSocket) controlContext.getServerSocketFactory().createServerSocket();

        // default
        SSLContextParameters scp = new SSLContextParameters();

        SSLContext context = scp.createSSLContext(null);

        CipherSuitesParameters csp = new CipherSuitesParameters();
        scp.setCipherSuites(csp);

        SSLEngine engine = context.createSSLEngine();
        SSLSocket socket = (SSLSocket) context.getSocketFactory().createSocket();
        SSLServerSocket serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();

        assertArrayEquals(controlEngine.getEnabledCipherSuites(), engine.getEnabledCipherSuites());
        assertArrayEquals(controlSocket.getEnabledCipherSuites(), socket.getEnabledCipherSuites());
        assertArrayEquals(this.getDefaultCipherSuiteIncludes(controlServerSocket.getSupportedCipherSuites()),
                serverSocket.getEnabledCipherSuites());

        // empty filter
        FilterParameters filter = new FilterParameters();
        scp.setCipherSuitesFilter(filter);
        context = scp.createSSLContext(null);
        engine = context.createSSLEngine();
        socket = (SSLSocket) context.getSocketFactory().createSocket();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();

        assertEquals(0, engine.getEnabledCipherSuites().length);
        assertEquals(0, socket.getEnabledCipherSuites().length);
        assertEquals(0, serverSocket.getEnabledCipherSuites().length);

        // explicit filter
        filter.getInclude().add(".*");
        context = scp.createSSLContext(null);
        engine = context.createSSLEngine();
        socket = (SSLSocket) context.getSocketFactory().createSocket();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();

        assertEquals(0, engine.getEnabledCipherSuites().length);
        assertEquals(0, socket.getEnabledCipherSuites().length);
        assertEquals(0, serverSocket.getEnabledCipherSuites().length);

        // explicit filter with excludes (excludes overrides)
        filter.getExclude().add(".*");
        context = scp.createSSLContext(null);
        engine = context.createSSLEngine();
        socket = (SSLSocket) context.getSocketFactory().createSocket();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();

        assertEquals(0, engine.getEnabledCipherSuites().length);
        assertEquals(0, socket.getEnabledCipherSuites().length);
        assertEquals(0, serverSocket.getEnabledCipherSuites().length);

        // explicit filter single include

        filter.getInclude().clear();
        filter.getExclude().clear();
        csp.setCipherSuite(Collections.singletonList("TLS_RSA_WITH_AES_128_CBC_SHA"));
        filter.getInclude().add("TLS.*");
        context = scp.createSSLContext(null);
        engine = context.createSSLEngine();
        socket = (SSLSocket) context.getSocketFactory().createSocket();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();

        // not all platforms/JDKs have these cipher suites
        if (!isPlatform("aix")) {
            assertTrue(engine.getEnabledCipherSuites().length >= 1);
            assertStartsWith(engine.getEnabledCipherSuites(), "TLS");
            assertTrue(socket.getEnabledCipherSuites().length >= 1);
            assertStartsWith(socket.getEnabledCipherSuites(), "TLS");
            assertTrue(serverSocket.getEnabledCipherSuites().length >= 1);
            assertStartsWith(serverSocket.getEnabledCipherSuites(), "TLS");
        }
    }

    @Test
    public void testSecureSocketProtocols() throws Exception {
        SSLContext controlContext = SSLContext.getInstance("TLS");
        controlContext.init(null, null, null);

        // default
        SSLContextParameters scp = new SSLContextParameters();

        SSLContext context = scp.createSSLContext(null);

        SSLEngine engine = context.createSSLEngine();
        SSLSocket socket = (SSLSocket) context.getSocketFactory().createSocket();
        SSLServerSocket serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();

        // default disable the SSL* protocols
        assertStartsWith(engine.getEnabledProtocols(), "TLS");
        assertStartsWith(socket.getEnabledProtocols(), "TLS");
        assertStartsWith(serverSocket.getEnabledProtocols(), "TLS");
        // checkProtocols(controlServerSocket.getEnabledProtocols(),
        // serverSocket.getEnabledProtocols());

        // empty sspp

        SecureSocketProtocolsParameters sspp = new SecureSocketProtocolsParameters();
        scp.setSecureSocketProtocols(sspp);
        context = scp.createSSLContext(null);
        engine = context.createSSLEngine();
        socket = (SSLSocket) context.getSocketFactory().createSocket();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();

        assertEquals(0, engine.getEnabledProtocols().length);
        assertEquals(0, socket.getEnabledProtocols().length);
        assertEquals(0, serverSocket.getEnabledProtocols().length);

        // explicit sspp

        sspp.setSecureSocketProtocol(Collections.singletonList("TLSv1"));
        context = scp.createSSLContext(null);
        engine = context.createSSLEngine();
        socket = (SSLSocket) context.getSocketFactory().createSocket();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();

        assertEquals(1, engine.getEnabledProtocols().length);
        assertEquals("TLSv1", engine.getEnabledProtocols()[0]);
        assertEquals(1, socket.getEnabledProtocols().length);
        assertEquals("TLSv1", socket.getEnabledProtocols()[0]);
        assertEquals(1, serverSocket.getEnabledProtocols().length);
        assertEquals("TLSv1", serverSocket.getEnabledProtocols()[0]);

        // explicit sspp overrides filter

        FilterParameters filter = new FilterParameters();
        filter.getInclude().add(".*");
        scp.setSecureSocketProtocolsFilter(filter);
        context = scp.createSSLContext(null);
        engine = context.createSSLEngine();
        socket = (SSLSocket) context.getSocketFactory().createSocket();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();

        // not all platforms/JDKs have these cipher suites
        if (!isPlatform("aix")) {
            assertEquals(1, engine.getEnabledProtocols().length);
            assertEquals("TLSv1", engine.getEnabledProtocols()[0]);
            assertEquals(1, socket.getEnabledProtocols().length);
            assertEquals("TLSv1", socket.getEnabledProtocols()[0]);
            assertEquals(1, socket.getEnabledProtocols().length);
            assertEquals("TLSv1", serverSocket.getEnabledProtocols()[0]);
        }
    }

    @Test
    public void testSecureSocketProtocolsFilter() throws Exception {
        SSLContext controlContext = SSLContext.getInstance("TLSv1.3");
        controlContext.init(null, null, null);
        SSLEngine controlEngine = controlContext.createSSLEngine();
        SSLSocket controlSocket = (SSLSocket) controlContext.getSocketFactory().createSocket();
        SSLServerSocket controlServerSocket = (SSLServerSocket) controlContext.getServerSocketFactory().createServerSocket();

        // default
        SSLContextParameters scp = new SSLContextParameters();

        SSLContext context = scp.createSSLContext(null);

        SSLEngine engine = context.createSSLEngine();
        SSLSocket socket = (SSLSocket) context.getSocketFactory().createSocket();
        SSLServerSocket serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();

        // default disable the SSL* protocols
        assertStartsWith(engine.getEnabledProtocols(), "TLS");
        assertStartsWith(socket.getEnabledProtocols(), "TLS");
        assertStartsWith(serverSocket.getEnabledProtocols(), "TLS");

        // empty filter

        FilterParameters filter = new FilterParameters();
        scp.setSecureSocketProtocolsFilter(filter);
        context = scp.createSSLContext(null);
        engine = context.createSSLEngine();
        socket = (SSLSocket) context.getSocketFactory().createSocket();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();

        assertEquals(0, engine.getEnabledProtocols().length);
        assertEquals(0, socket.getEnabledProtocols().length);
        assertEquals(0, serverSocket.getEnabledProtocols().length);

        // explicit filter

        filter.getInclude().add(".*");
        context = scp.createSSLContext(null);
        engine = context.createSSLEngine();
        socket = (SSLSocket) context.getSocketFactory().createSocket();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();

        assertArrayEquals(controlEngine.getEnabledProtocols(), engine.getEnabledProtocols());
        assertArrayEquals(controlSocket.getEnabledProtocols(), socket.getEnabledProtocols());
        checkProtocols(controlServerSocket.getEnabledProtocols(), serverSocket.getEnabledProtocols());

        // explicit filter with excludes (excludes overrides)
        filter.getExclude().add(".*");
        context = scp.createSSLContext(null);
        engine = context.createSSLEngine();
        socket = (SSLSocket) context.getSocketFactory().createSocket();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();

        assertEquals(0, engine.getEnabledProtocols().length);
        assertEquals(0, socket.getEnabledProtocols().length);
        assertEquals(0, serverSocket.getEnabledProtocols().length);

        // explicit filter single include
        filter.getInclude().clear();
        filter.getExclude().clear();
        filter.getInclude().add("TLS.*");
        context = scp.createSSLContext(null);
        engine = context.createSSLEngine();
        socket = (SSLSocket) context.getSocketFactory().createSocket();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();

        // not all platforms/JDKs have these cipher suites
        if (!isPlatform("aix")) {
            assertTrue(engine.getEnabledProtocols().length >= 1);
            assertStartsWith(engine.getEnabledProtocols(), "TLS");
            assertTrue(socket.getEnabledProtocols().length >= 1);
            assertStartsWith(socket.getEnabledProtocols(), "TLS");
            assertTrue(socket.getEnabledProtocols().length >= 1);
            assertStartsWith(serverSocket.getEnabledProtocols(), "TLS");
        }
    }

    @Test
    @EnabledForJreRange(min = JRE.JAVA_21)
    public void testNamedGroups() throws Exception {
        SSLContext controlContext = SSLContext.getInstance("TLSv1.3");
        controlContext.init(null, null, null);
        SSLEngine controlEngine = controlContext.createSSLEngine();
        String[] controlNamedGroups = getNamedGroups(controlEngine.getSSLParameters());

        // default - no named groups configured
        // When PQC groups are available (JDK 25+), auto-configuration reorders named groups
        SSLContextParameters scp = new SSLContextParameters();
        SSLContext context = scp.createSSLContext(null);

        SSLEngine engine = context.createSSLEngine();
        SSLSocket socket = (SSLSocket) context.getSocketFactory().createSocket();
        SSLServerSocket serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();

        if (Arrays.asList(controlNamedGroups).contains("X25519MLKEM768")) {
            // PQC auto-configuration reorders groups with X25519MLKEM768 first
            assertEquals("X25519MLKEM768", getNamedGroups(engine.getSSLParameters())[0]);
            assertEquals("X25519MLKEM768", getNamedGroups(socket.getSSLParameters())[0]);
            assertEquals("X25519MLKEM768", getNamedGroups(serverSocket.getSSLParameters())[0]);
        } else {
            // No PQC available, should keep JVM defaults
            assertArrayEquals(controlNamedGroups, getNamedGroups(engine.getSSLParameters()));
            assertArrayEquals(controlNamedGroups, getNamedGroups(socket.getSSLParameters()));
            assertArrayEquals(controlNamedGroups, getNamedGroups(serverSocket.getSSLParameters()));
        }

        // empty ngp - sets empty list
        NamedGroupsParameters ngp = new NamedGroupsParameters();
        scp.setNamedGroups(ngp);
        context = scp.createSSLContext(null);
        engine = context.createSSLEngine();
        socket = (SSLSocket) context.getSocketFactory().createSocket();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();

        assertEquals(0, getNamedGroups(engine.getSSLParameters()).length);
        assertEquals(0, getNamedGroups(socket.getSSLParameters()).length);
        assertEquals(0, getNamedGroups(serverSocket.getSSLParameters()).length);

        // explicit named group
        ngp.setNamedGroup(Collections.singletonList(controlNamedGroups[0]));
        context = scp.createSSLContext(null);
        engine = context.createSSLEngine();
        socket = (SSLSocket) context.getSocketFactory().createSocket();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();

        assertEquals(1, getNamedGroups(engine.getSSLParameters()).length);
        assertEquals(controlNamedGroups[0], getNamedGroups(engine.getSSLParameters())[0]);
        assertEquals(1, getNamedGroups(socket.getSSLParameters()).length);
        assertEquals(controlNamedGroups[0], getNamedGroups(socket.getSSLParameters())[0]);
        assertEquals(1, getNamedGroups(serverSocket.getSSLParameters()).length);
        assertEquals(controlNamedGroups[0], getNamedGroups(serverSocket.getSSLParameters())[0]);

        // explicit named groups override filter
        FilterParameters filter = new FilterParameters();
        filter.getInclude().add(".*");
        scp.setNamedGroupsFilter(filter);
        context = scp.createSSLContext(null);
        engine = context.createSSLEngine();
        socket = (SSLSocket) context.getSocketFactory().createSocket();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();

        assertEquals(1, getNamedGroups(engine.getSSLParameters()).length);
        assertEquals(controlNamedGroups[0], getNamedGroups(engine.getSSLParameters())[0]);
        assertEquals(1, getNamedGroups(socket.getSSLParameters()).length);
        assertEquals(controlNamedGroups[0], getNamedGroups(socket.getSSLParameters())[0]);
        assertEquals(1, getNamedGroups(serverSocket.getSSLParameters()).length);
        assertEquals(controlNamedGroups[0], getNamedGroups(serverSocket.getSSLParameters())[0]);
    }

    @Test
    @EnabledForJreRange(min = JRE.JAVA_21)
    public void testNamedGroupsFilter() throws Exception {
        SSLContext controlContext = SSLContext.getInstance("TLSv1.3");
        controlContext.init(null, null, null);
        SSLEngine controlEngine = controlContext.createSSLEngine();
        String[] controlNamedGroups = getNamedGroups(controlEngine.getSSLParameters());

        // default - no filter, keeps defaults (or PQC-reordered defaults on JDK 25+)
        SSLContextParameters scp = new SSLContextParameters();
        SSLContext context = scp.createSSLContext(null);

        SSLEngine engine = context.createSSLEngine();
        SSLSocket socket = (SSLSocket) context.getSocketFactory().createSocket();
        SSLServerSocket serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();

        if (Arrays.asList(controlNamedGroups).contains("X25519MLKEM768")) {
            assertEquals("X25519MLKEM768", getNamedGroups(engine.getSSLParameters())[0]);
            assertEquals("X25519MLKEM768", getNamedGroups(socket.getSSLParameters())[0]);
            assertEquals("X25519MLKEM768", getNamedGroups(serverSocket.getSSLParameters())[0]);
        } else {
            assertArrayEquals(controlNamedGroups, getNamedGroups(engine.getSSLParameters()));
            assertArrayEquals(controlNamedGroups, getNamedGroups(socket.getSSLParameters()));
            assertArrayEquals(controlNamedGroups, getNamedGroups(serverSocket.getSSLParameters()));
        }

        // empty filter - no includes means no groups match
        FilterParameters filter = new FilterParameters();
        scp.setNamedGroupsFilter(filter);
        context = scp.createSSLContext(null);
        engine = context.createSSLEngine();
        socket = (SSLSocket) context.getSocketFactory().createSocket();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();

        assertEquals(0, getNamedGroups(engine.getSSLParameters()).length);
        assertEquals(0, getNamedGroups(socket.getSSLParameters()).length);
        assertEquals(0, getNamedGroups(serverSocket.getSSLParameters()).length);

        // include all
        filter.getInclude().add(".*");
        context = scp.createSSLContext(null);
        engine = context.createSSLEngine();
        socket = (SSLSocket) context.getSocketFactory().createSocket();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();

        assertArrayEquals(controlNamedGroups, getNamedGroups(engine.getSSLParameters()));
        assertArrayEquals(controlNamedGroups, getNamedGroups(socket.getSSLParameters()));
        assertArrayEquals(controlNamedGroups, getNamedGroups(serverSocket.getSSLParameters()));

        // include all but exclude all (excludes win)
        filter.getExclude().add(".*");
        context = scp.createSSLContext(null);
        engine = context.createSSLEngine();
        socket = (SSLSocket) context.getSocketFactory().createSocket();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();

        assertEquals(0, getNamedGroups(engine.getSSLParameters()).length);
        assertEquals(0, getNamedGroups(socket.getSSLParameters()).length);
        assertEquals(0, getNamedGroups(serverSocket.getSSLParameters()).length);

        // include only x* groups (e.g. x25519, x448)
        filter.getInclude().clear();
        filter.getExclude().clear();
        filter.getInclude().add("x.*");
        context = scp.createSSLContext(null);
        engine = context.createSSLEngine();
        socket = (SSLSocket) context.getSocketFactory().createSocket();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();

        assertTrue(getNamedGroups(engine.getSSLParameters()).length >= 1);
        for (String group : getNamedGroups(engine.getSSLParameters())) {
            assertTrue(group.startsWith("x"), "Expected group starting with 'x' but got: " + group);
        }
        assertTrue(getNamedGroups(socket.getSSLParameters()).length >= 1);
        for (String group : getNamedGroups(socket.getSSLParameters())) {
            assertTrue(group.startsWith("x"), "Expected group starting with 'x' but got: " + group);
        }
        assertTrue(getNamedGroups(serverSocket.getSSLParameters()).length >= 1);
        for (String group : getNamedGroups(serverSocket.getSSLParameters())) {
            assertTrue(group.startsWith("x"), "Expected group starting with 'x' but got: " + group);
        }
    }

    @Test
    @EnabledForJreRange(min = JRE.JAVA_21)
    public void testNamedGroupsDoNotAffectCipherSuitesOrProtocols() throws Exception {
        SSLContext controlContext = SSLContext.getInstance("TLSv1.3");
        controlContext.init(null, null, null);
        SSLEngine controlEngine = controlContext.createSSLEngine();

        // setting named groups should not change cipher suites or protocols
        SSLContextParameters scp = new SSLContextParameters();
        NamedGroupsParameters ngp = new NamedGroupsParameters();
        ngp.setNamedGroup(Collections.singletonList("x25519"));
        scp.setNamedGroups(ngp);

        SSLContext context = scp.createSSLContext(null);
        SSLEngine engine = context.createSSLEngine();

        assertArrayEquals(controlEngine.getEnabledCipherSuites(), engine.getEnabledCipherSuites());
        assertArrayEquals(controlEngine.getEnabledProtocols(), engine.getEnabledProtocols());
        assertEquals(1, getNamedGroups(engine.getSSLParameters()).length);
        assertEquals("x25519", getNamedGroups(engine.getSSLParameters())[0]);
    }

    @Test
    @EnabledForJreRange(min = JRE.JAVA_21)
    public void testSignatureSchemes() throws Exception {
        SSLContext controlContext = SSLContext.getInstance("TLSv1.3");
        controlContext.init(null, null, null);
        SSLEngine controlEngine = controlContext.createSSLEngine();
        String[] controlSignatureSchemes = getSignatureSchemes(controlEngine.getSSLParameters());

        // default - no signature schemes configured, should keep defaults
        SSLContextParameters scp = new SSLContextParameters();
        SSLContext context = scp.createSSLContext(null);

        SSLEngine engine = context.createSSLEngine();
        SSLSocket socket = (SSLSocket) context.getSocketFactory().createSocket();
        SSLServerSocket serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();

        assertArrayEquals(controlSignatureSchemes, getSignatureSchemes(engine.getSSLParameters()));
        assertArrayEquals(controlSignatureSchemes, getSignatureSchemes(socket.getSSLParameters()));
        assertArrayEquals(controlSignatureSchemes, getSignatureSchemes(serverSocket.getSSLParameters()));

        // empty ssp - sets empty list
        SignatureSchemesParameters ssp = new SignatureSchemesParameters();
        scp.setSignatureSchemes(ssp);
        context = scp.createSSLContext(null);
        engine = context.createSSLEngine();
        socket = (SSLSocket) context.getSocketFactory().createSocket();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();

        assertEquals(0, getSignatureSchemes(engine.getSSLParameters()).length);
        assertEquals(0, getSignatureSchemes(socket.getSSLParameters()).length);
        assertEquals(0, getSignatureSchemes(serverSocket.getSSLParameters()).length);

        // explicit signature scheme
        ssp.setSignatureScheme(Collections.singletonList("rsa_pss_rsae_sha256"));
        context = scp.createSSLContext(null);
        engine = context.createSSLEngine();
        socket = (SSLSocket) context.getSocketFactory().createSocket();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();

        assertEquals(1, getSignatureSchemes(engine.getSSLParameters()).length);
        assertEquals("rsa_pss_rsae_sha256", getSignatureSchemes(engine.getSSLParameters())[0]);
        assertEquals(1, getSignatureSchemes(socket.getSSLParameters()).length);
        assertEquals("rsa_pss_rsae_sha256", getSignatureSchemes(socket.getSSLParameters())[0]);
        assertEquals(1, getSignatureSchemes(serverSocket.getSSLParameters()).length);
        assertEquals("rsa_pss_rsae_sha256", getSignatureSchemes(serverSocket.getSSLParameters())[0]);

        // explicit signature schemes override filter
        FilterParameters filter = new FilterParameters();
        filter.getInclude().add(".*");
        scp.setSignatureSchemesFilter(filter);
        context = scp.createSSLContext(null);
        engine = context.createSSLEngine();
        socket = (SSLSocket) context.getSocketFactory().createSocket();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();

        assertEquals(1, getSignatureSchemes(engine.getSSLParameters()).length);
        assertEquals("rsa_pss_rsae_sha256", getSignatureSchemes(engine.getSSLParameters())[0]);
        assertEquals(1, getSignatureSchemes(socket.getSSLParameters()).length);
        assertEquals("rsa_pss_rsae_sha256", getSignatureSchemes(socket.getSSLParameters())[0]);
        assertEquals(1, getSignatureSchemes(serverSocket.getSSLParameters()).length);
        assertEquals("rsa_pss_rsae_sha256", getSignatureSchemes(serverSocket.getSSLParameters())[0]);
    }

    @Test
    @EnabledForJreRange(min = JRE.JAVA_21)
    public void testSignatureSchemesFilter() throws Exception {
        // Note: SSLParameters.getSignatureSchemes() returns null by default (unlike getNamedGroups()),
        // so filters operate on explicitly provided schemes rather than JDK defaults.

        // default - no filter, keeps defaults (null)
        SSLContextParameters scp = new SSLContextParameters();
        SSLContext context = scp.createSSLContext(null);

        SSLEngine engine = context.createSSLEngine();
        SSLSocket socket = (SSLSocket) context.getSocketFactory().createSocket();
        SSLServerSocket serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();

        assertNull(getSignatureSchemes(engine.getSSLParameters()));
        assertNull(getSignatureSchemes(socket.getSSLParameters()));
        assertNull(getSignatureSchemes(serverSocket.getSSLParameters()));

        // empty filter - no includes means no schemes match (empty array)
        FilterParameters filter = new FilterParameters();
        scp.setSignatureSchemesFilter(filter);
        context = scp.createSSLContext(null);
        engine = context.createSSLEngine();
        socket = (SSLSocket) context.getSocketFactory().createSocket();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();

        assertEquals(0, getSignatureSchemes(engine.getSSLParameters()).length);
        assertEquals(0, getSignatureSchemes(socket.getSSLParameters()).length);
        assertEquals(0, getSignatureSchemes(serverSocket.getSSLParameters()).length);

        // explicit schemes override filter - filter ignored when schemes are set
        SignatureSchemesParameters ssp = new SignatureSchemesParameters();
        List<String> allSchemes = new LinkedList<>();
        allSchemes.add("ecdsa_secp256r1_sha256");
        allSchemes.add("ecdsa_secp384r1_sha384");
        allSchemes.add("rsa_pss_rsae_sha256");
        allSchemes.add("ed25519");
        ssp.setSignatureScheme(allSchemes);
        scp.setSignatureSchemes(ssp);

        filter.getInclude().add("ecdsa_.*");
        context = scp.createSSLContext(null);
        engine = context.createSSLEngine();

        // explicit schemes take precedence over filter
        assertEquals(4, getSignatureSchemes(engine.getSSLParameters()).length);

        // clear explicit schemes, keep filter - now filter applies to empty JDK defaults
        scp.setSignatureSchemes(null);
        filter.getInclude().clear();
        filter.getInclude().add(".*");
        context = scp.createSSLContext(null);
        engine = context.createSSLEngine();
        socket = (SSLSocket) context.getSocketFactory().createSocket();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();

        // JDK defaults are null → filtering null gives empty array
        assertEquals(0, getSignatureSchemes(engine.getSSLParameters()).length);
        assertEquals(0, getSignatureSchemes(socket.getSSLParameters()).length);
        assertEquals(0, getSignatureSchemes(serverSocket.getSSLParameters()).length);
    }

    @Test
    @EnabledForJreRange(min = JRE.JAVA_21)
    public void testSignatureSchemesDoNotAffectOtherSettings() throws Exception {
        SSLContext controlContext = SSLContext.getInstance("TLSv1.3");
        controlContext.init(null, null, null);
        SSLEngine controlEngine = controlContext.createSSLEngine();

        // setting signature schemes should not change cipher suites, protocols, or named groups
        SSLContextParameters scp = new SSLContextParameters();
        SignatureSchemesParameters ssp = new SignatureSchemesParameters();
        ssp.setSignatureScheme(Collections.singletonList("rsa_pss_rsae_sha256"));
        scp.setSignatureSchemes(ssp);

        SSLContext context = scp.createSSLContext(null);
        SSLEngine engine = context.createSSLEngine();

        assertArrayEquals(controlEngine.getEnabledCipherSuites(), engine.getEnabledCipherSuites());
        assertArrayEquals(controlEngine.getEnabledProtocols(), engine.getEnabledProtocols());
        // Named groups may be reordered by PQC auto-configuration, but same groups should be present
        String[] controlGroups = getNamedGroups(controlEngine.getSSLParameters());
        String[] engineGroups = getNamedGroups(engine.getSSLParameters());
        if (controlGroups != null && Arrays.asList(controlGroups).contains("X25519MLKEM768")) {
            assertEquals("X25519MLKEM768", engineGroups[0]);
            assertEquals(controlGroups.length, engineGroups.length);
        } else {
            assertArrayEquals(controlGroups, engineGroups);
        }
        assertEquals(1, getSignatureSchemes(engine.getSSLParameters()).length);
        assertEquals("rsa_pss_rsae_sha256", getSignatureSchemes(engine.getSSLParameters())[0]);
    }

    @Test
    public void testSessionTimeout() throws Exception {
        SSLContextParameters scp = new SSLContextParameters();
        scp.setSessionTimeout("60");

        SSLContext context = scp.createSSLContext(null);

        assertEquals(60, context.getClientSessionContext().getSessionTimeout());
        assertEquals(60, context.getServerSessionContext().getSessionTimeout());

        scp.setSessionTimeout("0");

        context = scp.createSSLContext(null);

        assertEquals(0, context.getClientSessionContext().getSessionTimeout());
        assertEquals(0, context.getServerSessionContext().getSessionTimeout());

    }

    @Test
    public void testDefaultSecureSocketProtocol() throws Exception {
        SSLContextParameters scp = new SSLContextParameters();

        SSLContext context = scp.createSSLContext(null);

        assertEquals("TLSv1.3", context.getProtocol());

        SSLEngine engine = context.createSSLEngine();
        SSLSocket socket = (SSLSocket) context.getSocketFactory().createSocket();
        SSLServerSocket serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();

        // default disable the SSL* protocols
        assertStartsWith(engine.getEnabledProtocols(), "TLS");
        assertStartsWith(socket.getEnabledProtocols(), "TLS");
        assertStartsWith(serverSocket.getEnabledProtocols(), "TLS");
    }

    @Test
    public void testSecureSocketProtocol() throws Exception {
        SSLContextParameters scp = new SSLContextParameters();
        scp.setSecureSocketProtocol("SSLv3");

        SSLContext context = scp.createSSLContext(null);

        assertEquals("SSLv3", context.getProtocol());

        SSLEngine engine = context.createSSLEngine();
        SSLSocket socket = (SSLSocket) context.getSocketFactory().createSocket();
        SSLServerSocket serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();

        // default disable the SSL* protocols
        assertStartsWith(engine.getEnabledProtocols(), "TLS");
        assertStartsWith(socket.getEnabledProtocols(), "TLS");
        assertStartsWith(serverSocket.getEnabledProtocols(), "TLS");

        // allow SSL* protocols by explicitly asking for them
        final SecureSocketProtocolsParameters protocols = new SecureSocketProtocolsParameters();
        protocols.setSecureSocketProtocol(Collections.singletonList("SSLv3"));
        scp.setSecureSocketProtocols(protocols);

        context = scp.createSSLContext(null);
        engine = context.createSSLEngine();
        socket = (SSLSocket) context.getSocketFactory().createSocket();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();

        assertEquals(1, engine.getEnabledProtocols().length);
        assertEquals("SSLv3", engine.getEnabledProtocols()[0]);
        assertEquals(1, socket.getEnabledProtocols().length);
        assertEquals("SSLv3", socket.getEnabledProtocols()[0]);
        assertEquals(1, serverSocket.getEnabledProtocols().length);
        assertEquals("SSLv3", serverSocket.getEnabledProtocols()[0]);
    }

    @Test
    public void testProvider() throws Exception {
        SSLContextParameters scp = new SSLContextParameters();
        scp.createSSLContext(null);

        SSLContext context = scp.createSSLContext(null);

        SSLContext defaultContext = SSLContext.getDefault();

        assertEquals(defaultContext.getProvider().getName(), context.getProvider().getName());
    }

    @Test
    @EnabledForJreRange(min = JRE.JAVA_21)
    public void testPqcNamedGroupsAutoConfigured() throws Exception {
        SSLContext controlContext = SSLContext.getInstance("TLSv1.3");
        controlContext.init(null, null, null);
        SSLEngine controlEngine = controlContext.createSSLEngine();
        String[] controlNamedGroups = getNamedGroups(controlEngine.getSSLParameters());
        boolean pqcAvailable = controlNamedGroups != null
                && Arrays.asList(controlNamedGroups).contains("X25519MLKEM768");

        SSLContextParameters scp = new SSLContextParameters();
        SSLContext context = scp.createSSLContext(null);
        SSLEngine engine = context.createSSLEngine();
        String[] resultGroups = getNamedGroups(engine.getSSLParameters());

        if (pqcAvailable) {
            // X25519MLKEM768 should be first
            assertEquals("X25519MLKEM768", resultGroups[0]);
            // All original groups should still be present
            List<String> resultList = Arrays.asList(resultGroups);
            for (String group : controlNamedGroups) {
                assertTrue(resultList.contains(group), "Expected group " + group + " to be present");
            }
        } else {
            // No PQC, defaults should be unchanged
            assertArrayEquals(controlNamedGroups, resultGroups);
        }
    }

    @Test
    @EnabledForJreRange(min = JRE.JAVA_21)
    public void testPqcNamedGroupsUserConfigOverrides() throws Exception {
        // User-configured named groups should NOT be overridden by PQC auto-config
        SSLContextParameters scp = new SSLContextParameters();
        NamedGroupsParameters ngp = new NamedGroupsParameters();
        ngp.setNamedGroup(Collections.singletonList("secp256r1"));
        scp.setNamedGroups(ngp);

        SSLContext context = scp.createSSLContext(null);
        SSLEngine engine = context.createSSLEngine();

        assertEquals(1, getNamedGroups(engine.getSSLParameters()).length);
        assertEquals("secp256r1", getNamedGroups(engine.getSSLParameters())[0]);
    }

    @Test
    @EnabledForJreRange(min = JRE.JAVA_21)
    public void testPqcNamedGroupsFilterOverrides() throws Exception {
        // User-configured named groups filter should NOT be overridden by PQC auto-config
        SSLContextParameters scp = new SSLContextParameters();
        FilterParameters filter = new FilterParameters();
        filter.getInclude().add("secp.*");
        scp.setNamedGroupsFilter(filter);

        SSLContext context = scp.createSSLContext(null);
        SSLEngine engine = context.createSSLEngine();

        for (String group : getNamedGroups(engine.getSSLParameters())) {
            assertTrue(group.startsWith("secp"), "Expected group starting with 'secp' but got: " + group);
        }
    }

    @Test
    public void testPqcAutoConfigDoesNotPersist() throws Exception {
        // PQC auto-config should not permanently mutate the SSLContextParameters instance
        SSLContextParameters scp = new SSLContextParameters();
        scp.createSSLContext(null);

        // After createSSLContext, namedGroups should be null (reset)
        assertNull(scp.getNamedGroups());
    }

    protected String[] getDefaultCipherSuiteIncludes(String[] availableCipherSuites) {
        List<String> enabled = new LinkedList<>();

        for (String string : availableCipherSuites) {
            if (!string.contains("_anon_") && !string.contains("_NULL_") && !string.contains("_EXPORT_")
                    && !string.contains("_DES_")) {
                enabled.add(string);
            }
        }

        return enabled.toArray(new String[0]);
    }

    protected void assertStartsWith(String[] values, String prefix) {
        assertNotNull(values, "The values should not be null");
        for (String value : values) {
            assertTrue(value.startsWith(prefix), value + " does not start with the prefix " + prefix);
        }
    }

    protected void assertStartsWith(Collection<String> values, String prefix) {
        assertNotNull(values, "The values should not be null");
        for (String value : values) {
            assertTrue(value.startsWith(prefix), value + " does not start with the prefix " + prefix);
        }
    }

    // Reflection helpers for JDK 20+ SSLParameters.getNamedGroups()/getSignatureSchemes()
    // These methods are not available with --release 17 compilation target
    private static String[] getNamedGroups(SSLParameters params) throws Exception {
        Method m = SSLParameters.class.getMethod("getNamedGroups");
        return (String[]) m.invoke(params);
    }

    private static String[] getSignatureSchemes(SSLParameters params) throws Exception {
        Method m = SSLParameters.class.getMethod("getSignatureSchemes");
        return (String[]) m.invoke(params);
    }
}
