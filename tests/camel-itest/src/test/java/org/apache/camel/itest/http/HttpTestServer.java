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

import org.apache.camel.test.AvailablePortFinder;
import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.config.SocketConfig;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;
import org.apache.http.localserver.EchoHandler;
import org.apache.http.localserver.RandomHandler;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.protocol.UriHttpRequestHandlerMapper;

/**
 * Copy of org.apache.http.localserver.LocalTestServer to use a specific port.
 */
public class HttpTestServer {

    public static final int PORT = AvailablePortFinder.getNextAvailable();

    /** The request handler registry. */
    private final UriHttpRequestHandlerMapper handlerRegistry;

    private final HttpServer httpServer;

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
        this.handlerRegistry = new UriHttpRequestHandlerMapper();

        SocketConfig socketConfig = SocketConfig.custom()
                .setSoTimeout(60000)
                .setRcvBufSize(8 * 1024)
                .setSndBufSize(8 * 1024)
                .setTcpNoDelay(true)
                .build();

        this.httpServer = ServerBootstrap.bootstrap()
                .setListenerPort(PORT)
                .setConnectionReuseStrategy(newConnectionReuseStrategy())
                .setHandlerMapper(handlerRegistry)
                .setSocketConfig(socketConfig)
                .setServerInfo("LocalTestServer/1.1")
                .create();
    }

    protected ConnectionReuseStrategy newConnectionReuseStrategy() {
        return new DefaultConnectionReuseStrategy();
    }

    /**
     * {@link #register Registers} a set of default request handlers.
     * 
     * <pre>
     * URI pattern      Handler
     * -----------      -------
     * /echo/*          {@link EchoHandler EchoHandler}
     * /random/*        {@link RandomHandler RandomHandler}
     * </pre>
     */
    public void registerDefaultHandlers() {
        handlerRegistry.register("/echo/*", new EchoHandler());
        handlerRegistry.register("/random/*", new RandomHandler());
    }

    /**
     * Registers a handler with the local registry.
     *
     * @param pattern the URL pattern to match
     * @param handler the handler to apply
     */
    public void register(String pattern, HttpRequestHandler handler) {
        handlerRegistry.register(pattern, handler);
    }

    /**
     * Starts this test server.
     */
    public void start() throws Exception {
        httpServer.start();
    }

    /**
     * Stops this test server.
     */
    public void stop() {
        httpServer.stop();
    }
}
