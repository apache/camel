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
package org.apache.camel.itest.http;

import java.net.InetSocketAddress;
import java.net.ServerSocket;

import org.apache.http.localserver.LocalTestServer;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.BasicHttpProcessor;

/**
 * Subclass the org.apache.http.localserver.LocalTestServer to choose a specific
 * port.
 * 
 * @author muellerc
 */
public class HttpTestServer extends LocalTestServer {

    /**
     * The local address to bind to. The host is an IP number rather than
     * "localhost" to avoid surprises on hosts that map "localhost" to an IPv6
     * address or something else. The port is 18080 by default.
     */
    public static final InetSocketAddress TEST_SERVER_ADDR = new InetSocketAddress(
            "127.0.0.1", 18080);

    public HttpTestServer(BasicHttpProcessor proc, HttpParams params) {
        super(proc, params);
    }

    /**
     * Starts this test server. Use {@link #getServicePort getServicePort} to
     * obtain the port number afterwards.
     */
    @Override
    public void start() throws Exception {
        if (servicedSocket != null) {
            throw new IllegalStateException(this.toString() + " already running");
        }

        ServerSocket ssock = new ServerSocket();
        ssock.setReuseAddress(true); // probably pointless for port '0'
        ssock.bind(TEST_SERVER_ADDR);
        servicedSocket = ssock;

        listenerThread = new Thread(new RequestListener());
        listenerThread.setDaemon(false);
        listenerThread.start();
    }
}