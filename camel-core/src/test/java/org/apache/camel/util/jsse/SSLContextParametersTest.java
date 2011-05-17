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
package org.apache.camel.util.jsse;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;

import junit.framework.TestCase;

public class SSLContextParametersTest extends TestCase {
    
    public void testServerParametersClientAuthentication() throws Exception {
        SSLContext controlContext = SSLContext.getInstance("TLS");
        controlContext.init(null, null, null);
        SSLEngine controlEngine = controlContext.createSSLEngine();
        SSLServerSocket controlServerSocket = (SSLServerSocket) controlContext.getServerSocketFactory().createServerSocket(); 
        
        
        SSLContextParameters scp = new SSLContextParameters();
        SSLContextServerParameters scsp = new SSLContextServerParameters();
        
        scp.setServerParameters(scsp);
        SSLContext context = scp.createSSLContext();
        
        
        SSLEngine engine = context.createSSLEngine();
        SSLServerSocket serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();
        
        assertEquals(controlServerSocket.getWantClientAuth(), serverSocket.getWantClientAuth());
        assertEquals(controlServerSocket.getNeedClientAuth(), serverSocket.getNeedClientAuth());
        assertEquals(controlEngine.getWantClientAuth(), engine.getWantClientAuth());
        assertEquals(controlEngine.getNeedClientAuth(), engine.getNeedClientAuth());
        
        // ClientAuthentication - NONE
        scsp.setClientAuthentication(ClientAuthentication.NONE);
        context = scp.createSSLContext();
        engine = context.createSSLEngine();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();
        
        assertEquals(false, serverSocket.getWantClientAuth());
        assertEquals(false, serverSocket.getNeedClientAuth());
        assertEquals(false, engine.getWantClientAuth());
        assertEquals(false, engine.getNeedClientAuth());
        
        // ClientAuthentication - WANT
        scsp.setClientAuthentication(ClientAuthentication.WANT);
        context = scp.createSSLContext();
        engine = context.createSSLEngine();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();
        
        assertEquals(true, serverSocket.getWantClientAuth());
        assertEquals(false, serverSocket.getNeedClientAuth());
        assertEquals(true, engine.getWantClientAuth());
        assertEquals(false, engine.getNeedClientAuth());
        
        // ClientAuthentication - REQUIRE
        scsp.setClientAuthentication(ClientAuthentication.REQUIRE);
        context = scp.createSSLContext();
        engine = context.createSSLEngine();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();
        
        assertEquals(false, serverSocket.getWantClientAuth());
        assertEquals(true, serverSocket.getNeedClientAuth());
        assertEquals(false, engine.getWantClientAuth());
        assertEquals(true, engine.getNeedClientAuth());
    }
    
    public void testServerParameters() throws Exception {
        SSLContext controlContext = SSLContext.getInstance("TLS");
        controlContext.init(null, null, null);
        SSLEngine controlEngine = controlContext.createSSLEngine();
        SSLSocket controlSocket = (SSLSocket) controlContext.getSocketFactory().createSocket();
        SSLServerSocket controlServerSocket = (SSLServerSocket) controlContext.getServerSocketFactory().createServerSocket(); 
        
        
        SSLContextParameters scp = new SSLContextParameters();
        SSLContextServerParameters scsp = new SSLContextServerParameters();
        
        scp.setServerParameters(scsp);
        SSLContext context = scp.createSSLContext();
        
        SSLEngine engine = context.createSSLEngine();
        SSLSocket socket = (SSLSocket) context.getSocketFactory().createSocket();
        SSLServerSocket serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();
        
        assertTrue(Arrays.equals(controlEngine.getEnabledCipherSuites(), engine.getEnabledCipherSuites()));
        assertTrue(Arrays.equals(controlSocket.getEnabledCipherSuites(), socket.getEnabledCipherSuites()));
        assertTrue(Arrays.equals(this.getDefaultCipherSuiteIncludes(controlServerSocket.getSupportedCipherSuites()), serverSocket.getEnabledCipherSuites()));
        assertEquals(controlServerSocket.getWantClientAuth(), serverSocket.getWantClientAuth());
        assertEquals(controlServerSocket.getNeedClientAuth(), serverSocket.getNeedClientAuth());
        
        // No csp or filter on server params passes through shared config
        scp.setCipherSuites(new CipherSuitesParameters());
        context = scp.createSSLContext();
        engine = context.createSSLEngine();
        socket = (SSLSocket) context.getSocketFactory().createSocket();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();
        
        assertEquals(0, serverSocket.getEnabledCipherSuites().length);
        
        // Csp on server params
        scp.setCipherSuites(null);
        CipherSuitesParameters csp = new CipherSuitesParameters();
        scsp.setCipherSuites(csp);
        context = scp.createSSLContext();
        engine = context.createSSLEngine();
        socket = (SSLSocket) context.getSocketFactory().createSocket();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();
        
        assertTrue(Arrays.equals(controlEngine.getEnabledCipherSuites(), engine.getEnabledCipherSuites()));
        assertTrue(Arrays.equals(controlSocket.getEnabledCipherSuites(), socket.getEnabledCipherSuites()));
        assertEquals(0, serverSocket.getEnabledCipherSuites().length);
        
        // Cipher suites filter on server params
        FilterParameters filter = new FilterParameters();
        filter.getExclude().add(".*");
        scsp.setCipherSuites(null);
        scsp.setCipherSuitesFilter(filter);
        context = scp.createSSLContext();
        engine = context.createSSLEngine();
        socket = (SSLSocket) context.getSocketFactory().createSocket();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();
        
        assertTrue(Arrays.equals(controlEngine.getEnabledCipherSuites(), engine.getEnabledCipherSuites()));
        assertTrue(Arrays.equals(controlSocket.getEnabledCipherSuites(), socket.getEnabledCipherSuites()));
        assertEquals(0, serverSocket.getEnabledCipherSuites().length);
        
        // Csp on server overrides cipher suites filter on server
        filter.getInclude().add(".*");
        filter.getExclude().clear();
        scsp.setCipherSuites(csp);
        context = scp.createSSLContext();
        engine = context.createSSLEngine();
        socket = (SSLSocket) context.getSocketFactory().createSocket();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();
        
        assertTrue(Arrays.equals(controlEngine.getEnabledCipherSuites(), engine.getEnabledCipherSuites()));
        assertTrue(Arrays.equals(controlSocket.getEnabledCipherSuites(), socket.getEnabledCipherSuites()));
        assertEquals(0, serverSocket.getEnabledCipherSuites().length);
        
        // Sspp on server params
        SecureSocketProtocolsParameters sspp = new SecureSocketProtocolsParameters();
        scsp.setSecureSocketProtocols(sspp);
        context = scp.createSSLContext();
        engine = context.createSSLEngine();
        socket = (SSLSocket) context.getSocketFactory().createSocket();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();
        
        assertTrue(Arrays.equals(controlEngine.getEnabledProtocols(), engine.getEnabledProtocols()));
        assertTrue(Arrays.equals(controlSocket.getEnabledProtocols(), socket.getEnabledProtocols()));
        assertEquals(0, serverSocket.getEnabledProtocols().length);
        
        // Secure socket protocols filter on client params
        filter = new FilterParameters();
        filter.getExclude().add(".*");
        scsp.setSecureSocketProtocols(null);
        scsp.setSecureSocketProtocolsFilter(filter);
        engine = context.createSSLEngine();
        socket = (SSLSocket) context.getSocketFactory().createSocket();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();
        
        assertTrue(Arrays.equals(controlEngine.getEnabledProtocols(), engine.getEnabledProtocols()));
        assertTrue(Arrays.equals(controlSocket.getEnabledProtocols(), socket.getEnabledProtocols()));
        assertEquals(0, serverSocket.getEnabledProtocols().length);
        
        // Sspp on client params overrides  secure socket protocols filter on client
        filter.getInclude().add(".*");
        filter.getExclude().clear();
        scsp.setSecureSocketProtocols(sspp);
        context = scp.createSSLContext();
        engine = context.createSSLEngine();
        socket = (SSLSocket) context.getSocketFactory().createSocket();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();
        
        assertTrue(Arrays.equals(controlEngine.getEnabledProtocols(), engine.getEnabledProtocols()));
        assertTrue(Arrays.equals(controlSocket.getEnabledProtocols(), socket.getEnabledProtocols()));
        assertEquals(0, serverSocket.getEnabledProtocols().length);
        
        // Server session timeout only affects server session configuration
        scsp.setSessionTimeout(12345);
        context = scp.createSSLContext();
        engine = context.createSSLEngine();
        socket = (SSLSocket) context.getSocketFactory().createSocket();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();
        
        assertEquals(controlContext.getClientSessionContext().getSessionTimeout(), context.getClientSessionContext().getSessionTimeout());
        assertEquals(12345, context.getServerSessionContext().getSessionTimeout());
    }
    
    public void testClientParameters() throws Exception {
        SSLContext controlContext = SSLContext.getInstance("TLS");
        controlContext.init(null, null, null);
        SSLEngine controlEngine = controlContext.createSSLEngine();
        SSLSocket controlSocket = (SSLSocket) controlContext.getSocketFactory().createSocket();
        SSLServerSocket controlServerSocket = (SSLServerSocket) controlContext.getServerSocketFactory().createServerSocket(); 
        
        
        SSLContextParameters scp = new SSLContextParameters();
        SSLContextClientParameters sccp = new SSLContextClientParameters();
        
        scp.setClientParameters(sccp);
        SSLContext context = scp.createSSLContext();
        
        SSLEngine engine = context.createSSLEngine();
        SSLSocket socket = (SSLSocket) context.getSocketFactory().createSocket();
        SSLServerSocket serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();
        
        assertTrue(Arrays.equals(controlEngine.getEnabledCipherSuites(), engine.getEnabledCipherSuites()));
        assertTrue(Arrays.equals(controlSocket.getEnabledCipherSuites(), socket.getEnabledCipherSuites()));
        assertTrue(Arrays.equals(this.getDefaultCipherSuiteIncludes(controlServerSocket.getSupportedCipherSuites()), serverSocket.getEnabledCipherSuites()));
        
        // No csp or filter on client params passes through shared config
        scp.setCipherSuites(new CipherSuitesParameters());
        context = scp.createSSLContext();
        engine = context.createSSLEngine();
        socket = (SSLSocket) context.getSocketFactory().createSocket();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();
        
        assertEquals(0, socket.getEnabledCipherSuites().length);
        
        // Csp on client params
        scp.setCipherSuites(null);
        CipherSuitesParameters csp = new CipherSuitesParameters();
        sccp.setCipherSuites(csp);
        context = scp.createSSLContext();
        engine = context.createSSLEngine();
        socket = (SSLSocket) context.getSocketFactory().createSocket();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();
        
        assertTrue(Arrays.equals(controlEngine.getEnabledCipherSuites(), engine.getEnabledCipherSuites()));
        assertEquals(0, socket.getEnabledCipherSuites().length);
        assertTrue(Arrays.equals(this.getDefaultCipherSuiteIncludes(controlServerSocket.getSupportedCipherSuites()), serverSocket.getEnabledCipherSuites()));
        
        // Cipher suites filter on client params
        FilterParameters filter = new FilterParameters();
        filter.getExclude().add(".*");
        sccp.setCipherSuites(null);
        sccp.setCipherSuitesFilter(filter);
        context = scp.createSSLContext();
        engine = context.createSSLEngine();
        socket = (SSLSocket) context.getSocketFactory().createSocket();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();
        
        assertTrue(Arrays.equals(controlEngine.getEnabledCipherSuites(), engine.getEnabledCipherSuites()));
        assertEquals(0, socket.getEnabledCipherSuites().length);
        assertTrue(Arrays.equals(this.getDefaultCipherSuiteIncludes(controlServerSocket.getSupportedCipherSuites()), serverSocket.getEnabledCipherSuites()));
        
        // Csp on client overrides cipher suites filter on client
        filter.getInclude().add(".*");
        filter.getExclude().clear();
        sccp.setCipherSuites(csp);
        context = scp.createSSLContext();
        engine = context.createSSLEngine();
        socket = (SSLSocket) context.getSocketFactory().createSocket();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();
        
        assertTrue(Arrays.equals(controlEngine.getEnabledCipherSuites(), engine.getEnabledCipherSuites()));
        assertEquals(0, socket.getEnabledCipherSuites().length);
        assertTrue(Arrays.equals(this.getDefaultCipherSuiteIncludes(controlServerSocket.getSupportedCipherSuites()), serverSocket.getEnabledCipherSuites()));
        
        // Sspp on client params
        SecureSocketProtocolsParameters sspp = new SecureSocketProtocolsParameters();
        sccp.setSecureSocketProtocols(sspp);
        context = scp.createSSLContext();
        engine = context.createSSLEngine();
        socket = (SSLSocket) context.getSocketFactory().createSocket();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();
        
        assertTrue(Arrays.equals(controlEngine.getEnabledProtocols(), engine.getEnabledProtocols()));
        assertEquals(0, socket.getEnabledProtocols().length);
        assertTrue(Arrays.equals(controlServerSocket.getEnabledProtocols(), serverSocket.getEnabledProtocols()));
        
        // Secure socket protocols filter on client params
        filter = new FilterParameters();
        filter.getExclude().add(".*");
        sccp.setSecureSocketProtocols(null);
        sccp.setSecureSocketProtocolsFilter(filter);
        engine = context.createSSLEngine();
        socket = (SSLSocket) context.getSocketFactory().createSocket();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();
        
        assertTrue(Arrays.equals(controlEngine.getEnabledProtocols(), engine.getEnabledProtocols()));
        assertEquals(0, socket.getEnabledProtocols().length);
        assertTrue(Arrays.equals(controlServerSocket.getEnabledProtocols(), serverSocket.getEnabledProtocols()));
        
        // Sspp on client params overrides  secure socket protocols filter on client
        filter.getInclude().add(".*");
        filter.getExclude().clear();
        sccp.setSecureSocketProtocols(sspp);
        context = scp.createSSLContext();
        engine = context.createSSLEngine();
        socket = (SSLSocket) context.getSocketFactory().createSocket();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();
        
        assertTrue(Arrays.equals(controlEngine.getEnabledProtocols(), engine.getEnabledProtocols()));
        assertEquals(0, socket.getEnabledProtocols().length);
        assertTrue(Arrays.equals(controlServerSocket.getEnabledProtocols(), serverSocket.getEnabledProtocols()));      
        
        // Client session timeout only affects client session configuration
        sccp.setSessionTimeout(12345);
        context = scp.createSSLContext();
        engine = context.createSSLEngine();
        socket = (SSLSocket) context.getSocketFactory().createSocket();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();
        
        assertEquals(controlContext.getServerSessionContext().getSessionTimeout(), context.getServerSessionContext().getSessionTimeout());
        assertEquals(12345, context.getClientSessionContext().getSessionTimeout());
    }
    
    public void testCipherSuites() throws Exception {
        SSLContext controlContext = SSLContext.getInstance("TLS");
        controlContext.init(null, null, null);
        SSLEngine controlEngine = controlContext.createSSLEngine();
        SSLSocket controlSocket = (SSLSocket) controlContext.getSocketFactory().createSocket();
        SSLServerSocket controlServerSocket = (SSLServerSocket) controlContext.getServerSocketFactory().createServerSocket(); 
        
        // default
        SSLContextParameters scp = new SSLContextParameters();
        
        SSLContext context = scp.createSSLContext();
        
        SSLEngine engine = context.createSSLEngine();
        SSLSocket socket = (SSLSocket) context.getSocketFactory().createSocket();
        SSLServerSocket serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();
        
        assertTrue(Arrays.equals(controlEngine.getEnabledCipherSuites(), engine.getEnabledCipherSuites()));
        assertTrue(Arrays.equals(controlSocket.getEnabledCipherSuites(), socket.getEnabledCipherSuites()));
        assertTrue(Arrays.equals(this.getDefaultCipherSuiteIncludes(controlServerSocket.getSupportedCipherSuites()), serverSocket.getEnabledCipherSuites()));
        
        
        // empty csp
        
        CipherSuitesParameters csp = new CipherSuitesParameters();
        scp.setCipherSuites(csp);
        context = scp.createSSLContext();
        engine = context.createSSLEngine();
        socket = (SSLSocket) context.getSocketFactory().createSocket();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();
        
        assertEquals(0, engine.getEnabledCipherSuites().length);
        assertEquals(0, socket.getEnabledCipherSuites().length);
        assertEquals(0, serverSocket.getEnabledCipherSuites().length);
        
        // explicit csp
        
        csp.getCipherSuite().add(controlEngine.getEnabledCipherSuites()[0]);
        context = scp.createSSLContext();
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
        context = scp.createSSLContext();
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
    
    public void testCipherSuitesFilter() throws Exception {
        SSLContext controlContext = SSLContext.getInstance("TLS");
        controlContext.init(null, null, null);
        SSLEngine controlEngine = controlContext.createSSLEngine();
        SSLSocket controlSocket = (SSLSocket) controlContext.getSocketFactory().createSocket();
        SSLServerSocket controlServerSocket = (SSLServerSocket) controlContext.getServerSocketFactory().createServerSocket(); 
        
        // default
        SSLContextParameters scp = new SSLContextParameters();
        
        SSLContext context = scp.createSSLContext();
        
        SSLEngine engine = context.createSSLEngine();
        SSLSocket socket = (SSLSocket) context.getSocketFactory().createSocket();
        SSLServerSocket serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();
        
        assertTrue(Arrays.equals(controlEngine.getEnabledProtocols(), engine.getEnabledProtocols()));
        assertTrue(Arrays.equals(controlSocket.getEnabledProtocols(), socket.getEnabledProtocols()));
        assertTrue(Arrays.equals(controlServerSocket.getEnabledProtocols(), serverSocket.getEnabledProtocols()));
        
        // empty filter
        
        FilterParameters filter = new FilterParameters();
        scp.setSecureSocketProtocolsFilter(filter);
        context = scp.createSSLContext();
        engine = context.createSSLEngine();
        socket = (SSLSocket) context.getSocketFactory().createSocket();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();
        
        assertEquals(0, engine.getEnabledProtocols().length);
        assertEquals(0, socket.getEnabledProtocols().length);
        assertEquals(0, serverSocket.getEnabledProtocols().length);
        
        // explicit filter
        
        filter.getInclude().add(".*");
        context = scp.createSSLContext();
        engine = context.createSSLEngine();
        socket = (SSLSocket) context.getSocketFactory().createSocket();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();
        
        assertTrue(Arrays.equals(controlEngine.getEnabledProtocols(), engine.getEnabledProtocols()));
        assertTrue(Arrays.equals(controlSocket.getEnabledProtocols(), socket.getEnabledProtocols()));
        assertTrue(Arrays.equals(controlServerSocket.getEnabledProtocols(), serverSocket.getEnabledProtocols()));
        
        // explicit filter with excludes (excludes overrides)
        filter.getExclude().add(".*");
        context = scp.createSSLContext();
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
        context = scp.createSSLContext();
        engine = context.createSSLEngine();
        socket = (SSLSocket) context.getSocketFactory().createSocket();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();
        
        assertTrue(engine.getEnabledProtocols().length >= 1);
        assertStartsWith(engine.getEnabledProtocols(), "TLS");
        assertTrue(socket.getEnabledProtocols().length >= 1);
        assertStartsWith(socket.getEnabledProtocols(), "TLS");
        assertTrue(socket.getEnabledProtocols().length >= 1);
        assertStartsWith(serverSocket.getEnabledProtocols(), "TLS");        
    }

    public void testSecureSocketProtocols() throws Exception {
        SSLContext controlContext = SSLContext.getInstance("TLS");
        controlContext.init(null, null, null);
        SSLEngine controlEngine = controlContext.createSSLEngine();
        SSLSocket controlSocket = (SSLSocket) controlContext.getSocketFactory().createSocket();
        SSLServerSocket controlServerSocket = (SSLServerSocket) controlContext.getServerSocketFactory().createServerSocket(); 
        
        // default
        SSLContextParameters scp = new SSLContextParameters();
        
        SSLContext context = scp.createSSLContext();
        
        SSLEngine engine = context.createSSLEngine();
        SSLSocket socket = (SSLSocket) context.getSocketFactory().createSocket();
        SSLServerSocket serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();
        
        assertTrue(Arrays.equals(controlEngine.getEnabledProtocols(), engine.getEnabledProtocols()));
        assertTrue(Arrays.equals(controlSocket.getEnabledProtocols(), socket.getEnabledProtocols()));
        assertTrue(Arrays.equals(controlServerSocket.getEnabledProtocols(), serverSocket.getEnabledProtocols()));
        
        // empty sspp
        
        SecureSocketProtocolsParameters sspp = new SecureSocketProtocolsParameters();
        scp.setSecureSocketProtocols(sspp);
        context = scp.createSSLContext();
        engine = context.createSSLEngine();
        socket = (SSLSocket) context.getSocketFactory().createSocket();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();
        
        assertEquals(0, engine.getEnabledProtocols().length);
        assertEquals(0, socket.getEnabledProtocols().length);
        assertEquals(0, serverSocket.getEnabledProtocols().length);
        
        // explicit sspp
        
        sspp.getSecureSocketProtocol().add("TLSv1");
        context = scp.createSSLContext();
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
        context = scp.createSSLContext();
        engine = context.createSSLEngine();
        socket = (SSLSocket) context.getSocketFactory().createSocket();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();
        
        assertEquals(1, engine.getEnabledProtocols().length);
        assertEquals("TLSv1", engine.getEnabledProtocols()[0]);
        assertEquals(1, socket.getEnabledProtocols().length);
        assertEquals("TLSv1", socket.getEnabledProtocols()[0]);
        assertEquals(1, socket.getEnabledProtocols().length);
        assertEquals("TLSv1", serverSocket.getEnabledProtocols()[0]);        
    }
    
    public void testSecureSocketProtocolsFilter() throws Exception {
        SSLContext controlContext = SSLContext.getInstance("TLS");
        controlContext.init(null, null, null);
        SSLEngine controlEngine = controlContext.createSSLEngine();
        SSLSocket controlSocket = (SSLSocket) controlContext.getSocketFactory().createSocket();
        SSLServerSocket controlServerSocket = (SSLServerSocket) controlContext.getServerSocketFactory().createServerSocket(); 
        
        // default
        SSLContextParameters scp = new SSLContextParameters();
        
        SSLContext context = scp.createSSLContext();
        
        SSLEngine engine = context.createSSLEngine();
        SSLSocket socket = (SSLSocket) context.getSocketFactory().createSocket();
        SSLServerSocket serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();
        
        assertTrue(Arrays.equals(controlEngine.getEnabledProtocols(), engine.getEnabledProtocols()));
        assertTrue(Arrays.equals(controlSocket.getEnabledProtocols(), socket.getEnabledProtocols()));
        assertTrue(Arrays.equals(controlServerSocket.getEnabledProtocols(), serverSocket.getEnabledProtocols()));
        
        // empty filter
        
        FilterParameters filter = new FilterParameters();
        scp.setSecureSocketProtocolsFilter(filter);
        context = scp.createSSLContext();
        engine = context.createSSLEngine();
        socket = (SSLSocket) context.getSocketFactory().createSocket();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();
        
        assertEquals(0, engine.getEnabledProtocols().length);
        assertEquals(0, socket.getEnabledProtocols().length);
        assertEquals(0, serverSocket.getEnabledProtocols().length);
        
        // explicit filter
        
        filter.getInclude().add(".*");
        context = scp.createSSLContext();
        engine = context.createSSLEngine();
        socket = (SSLSocket) context.getSocketFactory().createSocket();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();
        
        assertTrue(Arrays.equals(controlEngine.getEnabledProtocols(), engine.getEnabledProtocols()));
        assertTrue(Arrays.equals(controlSocket.getEnabledProtocols(), socket.getEnabledProtocols()));
        assertTrue(Arrays.equals(controlServerSocket.getEnabledProtocols(), serverSocket.getEnabledProtocols()));
        
        // explicit filter with excludes (excludes overrides)
        filter.getExclude().add(".*");
        context = scp.createSSLContext();
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
        context = scp.createSSLContext();
        engine = context.createSSLEngine();
        socket = (SSLSocket) context.getSocketFactory().createSocket();
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();
        
        assertTrue(engine.getEnabledProtocols().length >= 1);
        assertStartsWith(engine.getEnabledProtocols(), "TLS");
        assertTrue(socket.getEnabledProtocols().length >= 1);
        assertStartsWith(socket.getEnabledProtocols(), "TLS");
        assertTrue(socket.getEnabledProtocols().length >= 1);
        assertStartsWith(serverSocket.getEnabledProtocols(), "TLS");        
    }
    
    public void testSessionTimeout() throws Exception {
        SSLContextParameters scp = new SSLContextParameters();
        scp.setSessionTimeout(60);
        
        SSLContext context = scp.createSSLContext();
        
        assertEquals(60, context.getClientSessionContext().getSessionTimeout());
        assertEquals(60, context.getServerSessionContext().getSessionTimeout());
        
        scp.setSessionTimeout(0);
        
        context = scp.createSSLContext();
        
        assertEquals(0, context.getClientSessionContext().getSessionTimeout());
        assertEquals(0, context.getServerSessionContext().getSessionTimeout());
        
    }
    
    public void testDefaultSecureSocketProtocol() throws Exception {
        SSLContextParameters scp = new SSLContextParameters();
        
        SSLContext context = scp.createSSLContext();
        
        assertEquals("TLS", context.getProtocol());
    }
    
    public void testSecureSocketProtocol() throws Exception {
        SSLContextParameters scp = new SSLContextParameters();
        scp.setSecureSocketProtocol("SSLv3");
        
        SSLContext context = scp.createSSLContext();
        
        assertEquals("SSLv3", context.getProtocol());
    }
    
    public void testProvider() throws Exception {
        SSLContextParameters scp = new SSLContextParameters();
        scp.createSSLContext();
        
        SSLContext context = scp.createSSLContext();
        
        SSLContext defaultContext = SSLContext.getDefault();
        
        assertEquals(defaultContext.getProvider().getName(), context.getProvider().getName());
    }
    
    protected String[] getDefaultCipherSuiteIncludes(String[] availableCipherSuites) {
        List<String> enabled = new LinkedList<String>();
        
        for (String string : availableCipherSuites) {
            if (!string.contains("_anon_") && !string.contains("_NULL_")) {
                enabled.add(string);
            }
        }
        
        return enabled.toArray(new String[enabled.size()]);
    }
    
    protected void assertStartsWith(String[] values, String prefix) {
        for (String value : values) {
            assertTrue(value.startsWith(prefix));
        }
    }
}
