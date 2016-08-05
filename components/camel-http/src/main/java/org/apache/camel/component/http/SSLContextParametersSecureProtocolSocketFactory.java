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
package org.apache.camel.component.http;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.util.jsse.SSLContextParameters;
import org.apache.commons.httpclient.params.HttpConnectionParams;
import org.apache.commons.httpclient.protocol.ControllerThreadSocketFactory;
import org.apache.commons.httpclient.protocol.SecureProtocolSocketFactory;

/**
 * A {@code SecureProtocolSocketFactory} implementation to allow configuration
 * of Commons HTTP SSL/TLS options based on a JSSEClientParameters
 * instance or a provided {@code SSLSocketFactory} instance.
 */
public class SSLContextParametersSecureProtocolSocketFactory implements SecureProtocolSocketFactory {
    
    protected SSLSocketFactory factory;
    
    protected SSLContext context;
    
    /**
     * Creates a new instance using the provided factory.
     *
     * @param factory the factory to use
     */
    public SSLContextParametersSecureProtocolSocketFactory(SSLSocketFactory factory) {
        this.factory = factory;
    } 
    
    /**
     * Creates a new instance using a factory created by the provided client configuration
     * parameters.
     *
     * @param params the configuration parameters to use when creating the socket factory
     * @deprecated use {@link #SSLContextParametersSecureProtocolSocketFactory(SSLContextParameters, CamelContext)}
     */
    @Deprecated
    public SSLContextParametersSecureProtocolSocketFactory(SSLContextParameters params) {
        this(params, null);
    }

    /**
     * Creates a new instance using a factory created by the provided client configuration
     * parameters.
     *
     * @param params the configuration parameters to use when creating the socket factory
     * @param camelContext the Camel context
     */
    public SSLContextParametersSecureProtocolSocketFactory(SSLContextParameters params, CamelContext camelContext) {
        try {
            this.context = params.createSSLContext(camelContext);
            this.factory = this.context.getSocketFactory();
        } catch (Exception e) {
            throw new RuntimeCamelException("Error creating the SSLContext.", e);
        }
    }    

    @Override
    public Socket createSocket(String host, int port, InetAddress localAddress, int localPort) throws IOException {
        return this.factory.createSocket(host, port, localAddress, localPort);
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localAddress, int localPort, HttpConnectionParams params) throws IOException {
        
        if (params == null) {
            throw new IllegalArgumentException("Parameters may not be null");
        }
        int timeout = params.getConnectionTimeout();
        if (timeout == 0) {
            return createSocket(host, port, localAddress, localPort);
        } else {
            return ControllerThreadSocketFactory.createSocket(
                    this, host, port, localAddress, localPort, timeout);
        }
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException, UnknownHostException {
        return this.factory.createSocket(host, port);
    }

    @Override
    public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException, UnknownHostException {
        return this.factory.createSocket(socket, host, port, autoClose);
    }
}
