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
package org.apache.camel.component.mail;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

import org.apache.camel.RuntimeCamelException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DummySSLSocketFactory for testing with SSL - <b>NOT SECURE</b>.
 * <p/>
 * This factory is only to be used for testing purposes.
 */
public class DummySSLSocketFactory extends SSLSocketFactory {

    private static final Logger LOG = LoggerFactory.getLogger(DummySSLSocketFactory.class);
    private SSLSocketFactory factory;

    public DummySSLSocketFactory() {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            TrustManager[] trustManagers = new TrustManager[] {new DummyTrustManager()};
            sslContext.init(null, trustManagers, new java.security.SecureRandom());
            factory = sslContext.getSocketFactory();
        } catch (Exception e) {
            throw new RuntimeCamelException("Error creating DummySSLSocketFactory: " + e.getMessage(), e);
        }
    }

    /**
     * Must provide this getDefault operation for JavaMail to be able to use this factory.
     */
    public static SocketFactory getDefault() {
        LOG.warn("Camel is using DummySSLSocketFactory as SSLSocketFactory (only to be used for testing purpose)");
        return new DummySSLSocketFactory();
    }

    public String[] getDefaultCipherSuites() {
        return factory.getDefaultCipherSuites();
    }

    public String[] getSupportedCipherSuites() {
        return factory.getSupportedCipherSuites();
    }

    public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException {
        return factory.createSocket(socket, host, port, autoClose);
    }

    public Socket createSocket(String host, int port) throws IOException {
        return factory.createSocket(host, port);
    }

    public Socket createSocket(String host, int port, InetAddress localAddress, int localPort)
        throws IOException {
        return factory.createSocket(host, port, localAddress, localPort);
    }

    public Socket createSocket(InetAddress host, int port) throws IOException {
        return factory.createSocket(host, port);
    }

    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort)
        throws IOException {
        return factory.createSocket(address, port, localAddress, localPort);
    }

    public Socket createSocket() throws IOException {
        // must have this createSocket method
        return factory.createSocket();
    }

}
