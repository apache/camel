package org.apache.camel.component.http;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.util.jsse.SSLContextParameters;
import org.apache.commons.httpclient.ConnectTimeoutException;
import org.apache.commons.httpclient.params.HttpConnectionParams;
import org.apache.commons.httpclient.protocol.ControllerThreadSocketFactory;
import org.apache.commons.httpclient.protocol.SecureProtocolSocketFactory;

/**
 * A {@code SecureProtocolSocketFactory} implementation to allow configuration
 * of Commons HTTP SSL/TLS options based on a {@link #JSSEClientParameters}
 * instance or a provided {@code SSLSocketFactory} instance.
 */
public class SSLContextParametersSecureProtocolSocketFactory implements SecureProtocolSocketFactory {
    
    protected SSLSocketFactory factory = null;
    
    protected SSLContext context = null;
    
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
     */
    public SSLContextParametersSecureProtocolSocketFactory(SSLContextParameters params) {

        try {
            this.context = params.createSSLContext();
            this.factory = this.context.getSocketFactory();
        } catch (Exception e) {
            throw new RuntimeCamelException("Error creating the SSLContext.", e);
        }
    }    

    @Override
    public Socket createSocket(String host, int port, 
                               InetAddress localAddress, int localPort) throws IOException, UnknownHostException {
        return this.factory.createSocket(host, port, localAddress, localPort);
    }

    @Override
    public Socket createSocket(String host, int port, 
                               InetAddress localAddress, int localPort, HttpConnectionParams params) throws IOException, UnknownHostException,
        ConnectTimeoutException {
        
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
