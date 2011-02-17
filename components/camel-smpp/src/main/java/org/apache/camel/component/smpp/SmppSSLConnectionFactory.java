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
package org.apache.camel.component.smpp;

import java.io.IOException;
import java.net.Socket;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;

import org.jsmpp.session.connection.Connection;
import org.jsmpp.session.connection.ConnectionFactory;
import org.jsmpp.session.connection.socket.SocketConnection;

/**
 * A Jsmpp ConnectionFactory that creates SSL Sockets.
 * 
 * @version 
 */
public final class SmppSSLConnectionFactory implements ConnectionFactory {

    private static final SmppSSLConnectionFactory CONN_FACTORY = new SmppSSLConnectionFactory();

    private SmppSSLConnectionFactory() {
    }
    
    public static SmppSSLConnectionFactory getInstance() {
        return CONN_FACTORY;
    }    

    public Connection createConnection(String host, int port) throws IOException {
        try {
            SocketFactory socketFactory = SSLSocketFactory.getDefault();
            Socket socket = socketFactory.createSocket(host, port);

            return new SocketConnection(socket);

        } catch (Exception e) {
            throw new IOException(e.getMessage());
        }
    }
}