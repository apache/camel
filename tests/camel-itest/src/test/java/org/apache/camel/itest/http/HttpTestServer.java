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
package org.apache.camel.itest.http;

import java.util.concurrent.TimeUnit;

import org.apache.camel.test.AvailablePortFinder;
import org.apache.hc.core5.http.ConnectionReuseStrategy;
import org.apache.hc.core5.http.impl.DefaultConnectionReuseStrategy;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.util.Timeout;

/**
 * Copy of org.apache.http.localserver.LocalTestServer to use a specific port.
 */
public class HttpTestServer {

    public static final int PORT = AvailablePortFinder.getNextAvailable();

    /** The request handler registry. */
    private final ServerBootstrap bootstrap;

    private HttpServer httpServer;

    static {
        //set them as system properties so Spring can use the property placeholder
        //things to set them into the URL's in the spring contexts
        System.setProperty("HttpTestServer.Port", Integer.toString(PORT));
    }

    /**
     * Creates a new test server.
     *
     */
    public HttpTestServer() {
        SocketConfig socketConfig = SocketConfig.custom()
                .setSoTimeout(Timeout.of(60, TimeUnit.SECONDS))
                .setRcvBufSize(8 * 1024)
                .setSndBufSize(8 * 1024)
                .setTcpNoDelay(true)
                .build();
        this.bootstrap = ServerBootstrap.bootstrap()
                .setListenerPort(PORT)
                .setConnectionReuseStrategy(newConnectionReuseStrategy())
                .setSocketConfig(socketConfig);
    }

    protected ConnectionReuseStrategy newConnectionReuseStrategy() {
        return new DefaultConnectionReuseStrategy();
    }

    /**
     * Registers a handler with the local registry.
     *
     * @param pattern the URL pattern to match
     * @param handler the handler to apply
     */
    public void register(String pattern, HttpRequestHandler handler) {
        bootstrap.register(pattern, handler);
    }

    /**
     * Starts this test server.
     */
    public void start() throws Exception {
        httpServer = bootstrap.create();
        httpServer.start();
    }

    /**
     * Stops this test server.
     */
    public void stop() {
        if (httpServer != null) {
            httpServer.stop();
        }
    }
}
