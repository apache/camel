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
package org.apache.camel.component.nats;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.SecureRandom;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLContextSpi;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

/**
 * An SSLContext whose client sockets and engines check that the certificate of the server was issued for the host they
 * connect to, as jnats does not ask for it.
 */
final class HostnameVerifyingSSLContext extends SSLContext {

    private HostnameVerifyingSSLContext(SSLContext delegate) {
        super(new Spi(delegate), delegate.getProvider(), delegate.getProtocol());
    }

    static SSLContext wrap(SSLContext context) {
        return context instanceof HostnameVerifyingSSLContext ? context : new HostnameVerifyingSSLContext(context);
    }

    private static <T> T verifyHostname(T socket) {
        if (socket instanceof SSLSocket sslSocket) {
            SSLParameters parameters = sslSocket.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            sslSocket.setSSLParameters(parameters);
        } else if (socket instanceof SSLEngine engine) {
            SSLParameters parameters = engine.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            engine.setSSLParameters(parameters);
        }
        return socket;
    }

    private static final class Spi extends SSLContextSpi {

        private final SSLContext delegate;

        Spi(SSLContext delegate) {
            this.delegate = delegate;
        }

        @Override
        protected void engineInit(KeyManager[] km, TrustManager[] tm, SecureRandom sr) throws KeyManagementException {
            delegate.init(km, tm, sr);
        }

        @Override
        protected SSLSocketFactory engineGetSocketFactory() {
            return new SocketFactory(delegate.getSocketFactory());
        }

        @Override
        protected SSLServerSocketFactory engineGetServerSocketFactory() {
            return delegate.getServerSocketFactory();
        }

        @Override
        protected SSLEngine engineCreateSSLEngine() {
            return verifyHostname(delegate.createSSLEngine());
        }

        @Override
        protected SSLEngine engineCreateSSLEngine(String host, int port) {
            return verifyHostname(delegate.createSSLEngine(host, port));
        }

        @Override
        protected SSLSessionContext engineGetServerSessionContext() {
            return delegate.getServerSessionContext();
        }

        @Override
        protected SSLSessionContext engineGetClientSessionContext() {
            return delegate.getClientSessionContext();
        }

        @Override
        protected SSLParameters engineGetDefaultSSLParameters() {
            return delegate.getDefaultSSLParameters();
        }

        @Override
        protected SSLParameters engineGetSupportedSSLParameters() {
            return delegate.getSupportedSSLParameters();
        }
    }

    private static final class SocketFactory extends SSLSocketFactory {

        private final SSLSocketFactory delegate;

        SocketFactory(SSLSocketFactory delegate) {
            this.delegate = delegate;
        }

        @Override
        public String[] getDefaultCipherSuites() {
            return delegate.getDefaultCipherSuites();
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return delegate.getSupportedCipherSuites();
        }

        @Override
        public Socket createSocket() throws IOException {
            return verifyHostname(delegate.createSocket());
        }

        @Override
        public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
            return verifyHostname(delegate.createSocket(s, host, port, autoClose));
        }

        @Override
        public Socket createSocket(Socket s, InputStream consumed, boolean autoClose) throws IOException {
            return verifyHostname(delegate.createSocket(s, consumed, autoClose));
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            return verifyHostname(delegate.createSocket(host, port));
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
            return verifyHostname(delegate.createSocket(host, port, localHost, localPort));
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            return verifyHostname(delegate.createSocket(host, port));
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort)
                throws IOException {
            return verifyHostname(delegate.createSocket(address, port, localAddress, localPort));
        }
    }
}
