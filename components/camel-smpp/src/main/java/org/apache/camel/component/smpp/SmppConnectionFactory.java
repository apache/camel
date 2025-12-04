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

package org.apache.camel.component.smpp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Base64;
import java.util.Map;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.util.IOHelper;
import org.jsmpp.session.connection.Connection;
import org.jsmpp.session.connection.ConnectionFactory;
import org.jsmpp.session.connection.socket.SocketConnection;

/**
 * A Jsmpp ConnectionFactory that creates SSL Sockets.
 */
public final class SmppConnectionFactory implements ConnectionFactory {
    private final SmppConfiguration config;

    private SmppConnectionFactory(SmppConfiguration config) {
        this.config = config;
    }

    public static SmppConnectionFactory getInstance(SmppConfiguration config) {
        return new SmppConnectionFactory(config);
    }

    @Override
    public Connection createConnection(String host, int port) throws IOException {
        try {
            Socket socket;
            SocketFactory socketFactory;
            socketFactory = config.isUsingSSL() && config.getHttpProxyHost() == null
                    ? SSLSocketFactory.getDefault()
                    : SocketFactory.getDefault();
            // NOTE: the socket must be closed by the factory method client.
            socket = socketFactory.createSocket(); // NOSONAR
            if (config.getHttpProxyHost() != null) {
                // setup the proxy tunnel
                // jsmpp uses enquire link timer as socket read timeout, so also use it to establish the initial
                // connection
                socket.connect(
                        new InetSocketAddress(config.getHttpProxyHost(), config.getHttpProxyPort()),
                        config.getEnquireLinkTimer());
                connectProxy(host, port, socket);
            } else {
                // jsmpp uses enquire link timer as socket read timeout, so also use it to establish the initial
                // connection
                socket.connect(new InetSocketAddress(host, port), config.getEnquireLinkTimer());
            }

            if (config.isUsingSSL() && config.getHttpProxyHost() != null) {
                // Init the SSL socket which is based on the proxy socket
                SSLSocketFactory sslSocketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
                // NOTE: the socket must be closed by the factory method client.
                SSLSocket sslSocket = (SSLSocket) sslSocketFactory.createSocket(socket, host, port, true); // NOSONAR
                sslSocket.startHandshake();
                socket = sslSocket;
            }

            return new SocketConnection(socket);
        } catch (Exception e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    private void connectProxy(String host, int port, Socket socket) throws IOException {
        try {
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            String connectString = "CONNECT " + host + ":" + port + " HTTP/1.0\r\n";
            out.write(connectString.getBytes());

            String username = config.getHttpProxyUsername();
            String password = config.getHttpProxyPassword();

            if (username != null && password != null) {
                String usernamePassword = username + ":" + password;
                byte[] code = Base64.getEncoder().encode(usernamePassword.getBytes());
                out.write("Proxy-Authorization: Basic ".getBytes());
                out.write(code);
                out.write("\r\n".getBytes());
            }

            Map<String, String> proxyHeaders = config.getProxyHeaders();
            if (proxyHeaders != null) {
                for (Map.Entry<String, String> entry : proxyHeaders.entrySet()) {
                    out.write((entry.getKey() + ": " + entry.getValue()).getBytes());
                    out.write("\r\n".getBytes());
                }
            }

            out.write("\r\n".getBytes());
            out.flush();

            int ch = 0;

            BufferedReader reader = IOHelper.buffered(new InputStreamReader(in));
            String response = reader.readLine();
            if (response == null) {
                throw new RuntimeCamelException("Empty response to CONNECT request to host " + host + ":" + port);
            }
            String reason;
            int code;
            try {
                ch = response.indexOf(' ');
                int bar = response.indexOf(' ', ch + 1);
                code = Integer.parseInt(response.substring(ch + 1, bar));
                reason = response.substring(bar + 1);
            } catch (NumberFormatException e) {
                throw new RuntimeCamelException("Invalid response to CONNECT request to host " + host + ":" + port
                        + " - cannot parse code from response string: " + response);
            }
            if (code != 200) {
                throw new RuntimeCamelException("Proxy error: " + reason);
            }

            // read until empty line
            while (!response.isEmpty()) {
                response = reader.readLine();
                if (response == null) {
                    throw new RuntimeCamelException("Proxy error: reached end of stream");
                }
            }
        } catch (RuntimeException re) {
            closeSocket(socket);
            throw re;
        } catch (Exception e) {
            closeSocket(socket);
            throw new RuntimeCamelException("SmppConnectionFactory: " + e.getMessage(), e);
        }
    }

    private static void closeSocket(Socket s) {
        if (s != null) {
            try {
                s.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }
}
