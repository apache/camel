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

package org.apache.camel.component.as2.api;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;

import org.apache.camel.component.as2.api.io.AS2BHttpServerConnection;
import org.apache.camel.util.ObjectHelper;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.impl.io.HttpService;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.HttpServerConnection;
import org.apache.hc.core5.http.io.HttpServerRequestHandler;
import org.apache.hc.core5.http.io.support.BasicHttpServerRequestHandler;
import org.apache.hc.core5.http.protocol.BasicHttpContext;
import org.apache.hc.core5.http.protocol.DefaultHttpProcessor;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http.protocol.RequestHandlerRegistry;
import org.apache.hc.core5.http.protocol.RequestValidateHost;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Receives the asynchronous AS2-MDN that is requested by the sender of an AS2 message.
 */
public class AS2AsyncMDNServerConnection {

    private static final Logger LOG = LoggerFactory.getLogger(AS2AsyncMDNServerConnection.class);

    private static final String REQUEST_LISTENER_THREAD_NAME_PREFIX = "AS2AsyncMdnSvr-";
    private static final String REQUEST_HANDLER_THREAD_NAME_PREFIX = "AS2AsyncMdnHdlr-";
    private static final int DEFAULT_BUFFER_SIZE = 8 * 1024;
    private RequestListenerThread listenerThread;
    private final Lock lock = new ReentrantLock();

    public AS2AsyncMDNServerConnection(Integer portNumber, SSLContext sslContext) throws IOException {
        final Integer parserPortNumber = ObjectHelper.notNull(portNumber, "portNumber");
        listenerThread = new RequestListenerThread(parserPortNumber, sslContext);
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    public void close() {
        if (listenerThread != null) {
            lock.lock();
            try {
                try {
                    listenerThread.serverSocket.close();
                } catch (IOException e) {
                    LOG.info(e.getMessage(), e);
                } finally {
                    listenerThread = null;
                }
            } finally {
                lock.unlock();
            }
        }
    }

    public void receive(String requestUriPattern, HttpRequestHandler handler) {
        if (listenerThread != null) {
            lock.lock();
            try {
                listenerThread.registerHandler(requestUriPattern, handler);
            } finally {
                lock.unlock();
            }
        }
    }

    class RequestListenerThread extends Thread {

        private final ServerSocket serverSocket;
        private final HttpService httpService;
        private final RequestHandlerRegistry registry;
        private final HttpServerRequestHandler handler;

        public RequestListenerThread(int port, SSLContext sslContext) throws IOException {
            setName(REQUEST_LISTENER_THREAD_NAME_PREFIX + port);
            if (sslContext == null) {
                serverSocket = new ServerSocket(port);
            } else {
                SSLServerSocketFactory factory = sslContext.getServerSocketFactory();
                serverSocket = factory.createServerSocket(port);
            }
            HttpProcessor httpProcessor = new DefaultHttpProcessor(new RequestValidateHost());
            registry = new RequestHandlerRegistry<>();
            handler = new BasicHttpServerRequestHandler(registry);
            httpService = new HttpService(httpProcessor, handler);
        }

        @Override
        public void run() {
            LOG.info("Listening on port {}", this.serverSocket.getLocalPort());
            while (!Thread.interrupted()) {
                try {
                    Socket inSocket = this.serverSocket.accept();
                    // Start worker thread
                    Thread t = new RequestHandlerThread(this.httpService, inSocket);
                    t.setDaemon(true);
                    t.start();
                } catch (final InterruptedIOException | SocketException ex) {
                    // Interrupted or server socket closed
                    break;
                } catch (final IOException e) {
                    LOG.error("I/O error initialising connection thread: {}", e.getMessage());
                    break;
                }
            }
        }

        void registerHandler(String requestUriPattern, HttpRequestHandler httpRequestHandler) {
            registry.register(null, requestUriPattern, httpRequestHandler);
        }
    }

    class RequestHandlerThread extends Thread {
        private final HttpService httpService;
        private final HttpServerConnection serverConnection;

        public RequestHandlerThread(HttpService httpService, Socket inSocket) throws IOException {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Incoming connection from {}", inSocket.getInetAddress());
            }
            Http1Config cfg =
                    Http1Config.custom().setBufferSize(DEFAULT_BUFFER_SIZE).build();
            // parses the received AS2MessageEntity
            // NOTE: the connection will be closed after the execution of the process.
            AS2BHttpServerConnection inConn = new AS2BHttpServerConnection(cfg); // NOSONAR
            inConn.bind(inSocket);
            // TODO Update once baseline is Java 21
            // setName(REQUEST_HANDLER_THREAD_NAME_PREFIX + threadId());
            setName(REQUEST_HANDLER_THREAD_NAME_PREFIX + getId());
            this.httpService = httpService;
            this.serverConnection = inConn;
        }

        @Override
        public void run() {
            final HttpContext context = new BasicHttpContext(null);
            try {
                while (!Thread.interrupted()) {
                    this.httpService.handleRequest(this.serverConnection, context);
                }
            } catch (final IOException ex) {
                LOG.error("I/O exception: {}", ex.getMessage(), ex);
            } catch (final HttpException ex) {
                LOG.error("Unrecoverable HTTP protocol violation: {}", ex.getMessage(), ex);
            } finally {
                try {
                    this.serverConnection.close();
                } catch (final IOException e) {
                    // ignore
                    LOG.warn("An exception happened while closing server connection: ignoring", e);
                }
            }
        }
    }
}
