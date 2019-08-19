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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;

import org.apache.camel.test.AvailablePortFinder;
import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.HttpResponseFactory;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.HttpServerConnection;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.impl.DefaultHttpServerConnection;
import org.apache.http.localserver.EchoHandler;
import org.apache.http.localserver.RandomHandler;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.params.SyncBasicHttpParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.BasicHttpProcessor;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpExpectationVerifier;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.protocol.HttpRequestHandlerRegistry;
import org.apache.http.protocol.HttpService;
import org.apache.http.protocol.ImmutableHttpProcessor;
import org.apache.http.protocol.ResponseConnControl;
import org.apache.http.protocol.ResponseContent;
import org.apache.http.protocol.ResponseDate;
import org.apache.http.protocol.ResponseServer;

/**
 * Copy of org.apache.http.localserver.LocalTestServer to use a specific port.
 */
public class HttpTestServer {
    
    public static final int PORT = AvailablePortFinder.getNextAvailable();
    
    /**
     * The local address to bind to.
     * The host is an IP number rather than "localhost" to avoid surprises
     * on hosts that map "localhost" to an IPv6 address or something else.
     * The port is 0 to let the system pick one.
     */
    public static final InetSocketAddress TEST_SERVER_ADDR =
        new InetSocketAddress("localhost", PORT);

    /** The request handler registry. */
    private final HttpRequestHandlerRegistry handlerRegistry;

    private final HttpService httpservice;

    /** Optional SSL context */
    private final SSLContext sslcontext;

    /** The server socket, while being served. */
    private volatile ServerSocket servicedSocket;

    /** The request listening thread, while listening. */
    private volatile ListenerThread listenerThread;

    /** Set of active worker threads */
    private final Set<Worker> workers;

    /** The number of connections this accepted. */
    private final AtomicInteger acceptedConnections = new AtomicInteger(0);
    
    static {
        //set them as system properties so Spring can use the property placeholder
        //things to set them into the URL's in the spring contexts 
        System.setProperty("HttpTestServer.Port", Integer.toString(PORT));
    }

    /**
     * Creates a new test server.
     *
     * @param proc      the HTTP processors to be used by the server, or
     *                  <code>null</code> to use a
     *                  {@link #newProcessor default} processor
     * @param reuseStrat the connection reuse strategy to be used by the
     *                  server, or <code>null</code> to use
     *                  {@link #newConnectionReuseStrategy() default}
     *                  strategy.
     * @param params    the parameters to be used by the server, or
     *                  <code>null</code> to use
     *                  {@link #newDefaultParams default} parameters
     * @param sslcontext optional SSL context if the server is to leverage
     *                   SSL/TLS transport security
     */
    public HttpTestServer(
            final BasicHttpProcessor proc,
            final ConnectionReuseStrategy reuseStrat,
            final HttpResponseFactory responseFactory,
            final HttpExpectationVerifier expectationVerifier,
            final HttpParams params,
            final SSLContext sslcontext) {
        this.handlerRegistry = new HttpRequestHandlerRegistry();
        this.workers = Collections.synchronizedSet(new HashSet<Worker>());
        this.httpservice = new HttpService(
            proc != null ? proc : newProcessor(),
            reuseStrat != null ? reuseStrat : newConnectionReuseStrategy(),
            responseFactory != null ? responseFactory : newHttpResponseFactory(),
            handlerRegistry,
            expectationVerifier,
            params != null ? params : newDefaultParams());
        this.sslcontext = sslcontext;
    }

    /**
     * Creates a new test server with SSL/TLS encryption.
     *
     * @param sslcontext SSL context
     */
    public HttpTestServer(final SSLContext sslcontext) {
        this(null, null, null, null, null, sslcontext);
    }

    /**
     * Creates a new test server.
     *
     * @param proc      the HTTP processors to be used by the server, or
     *                  <code>null</code> to use a
     *                  {@link #newProcessor default} processor
     * @param params    the parameters to be used by the server, or
     *                  <code>null</code> to use
     *                  {@link #newDefaultParams default} parameters
     */
    public HttpTestServer(
            BasicHttpProcessor proc,
            HttpParams params) {
        this(proc, null, null, null, params, null);
    }

    /**
     * Obtains an HTTP protocol processor with default interceptors.
     *
     * @return  a protocol processor for server-side use
     */
    protected HttpProcessor newProcessor() {
        return new ImmutableHttpProcessor(new HttpResponseInterceptor[] {new ResponseDate(),
                                                                         new ResponseServer(),
                                                                         new ResponseContent(),
                                                                         new ResponseConnControl()});
    }


    /**
     * Obtains a set of reasonable default parameters for a server.
     *
     * @return  default parameters
     */
    protected HttpParams newDefaultParams() {
        HttpParams params = new SyncBasicHttpParams();
        params
            .setIntParameter(CoreConnectionPNames.SO_TIMEOUT, 60000)
            .setIntParameter(CoreConnectionPNames.SOCKET_BUFFER_SIZE, 8 * 1024)
            .setBooleanParameter(CoreConnectionPNames.STALE_CONNECTION_CHECK, false)
            .setBooleanParameter(CoreConnectionPNames.TCP_NODELAY, true)
            .setParameter(CoreProtocolPNames.ORIGIN_SERVER,
                          "LocalTestServer/1.1");
        return params;
    }

    protected ConnectionReuseStrategy newConnectionReuseStrategy() {
        return new DefaultConnectionReuseStrategy();
    }

    protected HttpResponseFactory newHttpResponseFactory() {
        return new DefaultHttpResponseFactory();
    }

    /**
     * Returns the number of connections this test server has accepted.
     */
    public int getAcceptedConnectionCount() {
        return acceptedConnections.get();
    }

    /**
     * {@link #register Registers} a set of default request handlers.
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
     * @param pattern   the URL pattern to match
     * @param handler   the handler to apply
     */
    public void register(String pattern, HttpRequestHandler handler) {
        handlerRegistry.register(pattern, handler);
    }


    /**
     * Unregisters a handler from the local registry.
     *
     * @param pattern   the URL pattern
     */
    public void unregister(String pattern) {
        handlerRegistry.unregister(pattern);
    }


    /**
     * Starts this test server.
     */
    public void start() throws Exception {
        if (servicedSocket != null) {
            throw new IllegalStateException(this.toString() + " already running");
        }
        ServerSocket ssock;
        if (sslcontext != null) {
            SSLServerSocketFactory sf = sslcontext.getServerSocketFactory();
            ssock = sf.createServerSocket();
        } else {
            ssock = new ServerSocket();
        }

        ssock.setReuseAddress(true); // probably pointless for port '0'
        ssock.bind(TEST_SERVER_ADDR);
        servicedSocket = ssock;

        listenerThread = new ListenerThread();
        listenerThread.setDaemon(false);
        listenerThread.start();
    }

    /**
     * Stops this test server.
     */
    public void stop() throws Exception {
        if (servicedSocket == null) {
            return; // not running
        }
        ListenerThread t = listenerThread;
        if (t != null) {
            t.shutdown();
        }
        synchronized (workers) {
            for (Worker worker : workers) {
                worker.shutdown();
            }
        }
    }

    public void awaitTermination(long timeMs) throws InterruptedException {
        if (listenerThread != null) {
            listenerThread.join(timeMs);
        }
    }

    @Override
    public String toString() {
        ServerSocket ssock = servicedSocket; // avoid synchronization
        StringBuilder sb = new StringBuilder(80);
        sb.append("LocalTestServer/");
        if (ssock == null) {
            sb.append("stopped");
        } else {
            sb.append(ssock.getLocalSocketAddress());
        }
        return sb.toString();
    }

    /**
     * Obtains the local address the server is listening on
     *
     * @return the service address
     */
    public InetSocketAddress getServiceAddress() {
        ServerSocket ssock = servicedSocket; // avoid synchronization
        if (ssock == null) {
            throw new IllegalStateException("not running");
        }
        return (InetSocketAddress) ssock.getLocalSocketAddress();
    }

    /**
     * The request listener.
     * Accepts incoming connections and launches a service thread.
     */
    class ListenerThread extends Thread {

        private volatile Exception exception;

        @Override
        public void run() {
            try {
                while (!interrupted()) {
                    Socket socket = servicedSocket.accept();
                    acceptedConnections.incrementAndGet();
                    DefaultHttpServerConnection conn = new DefaultHttpServerConnection();
                    conn.bind(socket, httpservice.getParams());
                    // Start worker thread
                    Worker worker = new Worker(conn);
                    workers.add(worker);
                    worker.setDaemon(true);
                    worker.start();
                }
            } catch (Exception ex) {
                this.exception = ex;
            } finally {
                try {
                    servicedSocket.close();
                } catch (IOException ignore) {
                }
            }
        }

        public void shutdown() {
            interrupt();
            try {
                servicedSocket.close();
            } catch (IOException ignore) {
            }
        }

        public Exception getException() {
            return this.exception;
        }

    }

    class Worker extends Thread {

        private final HttpServerConnection conn;

        private volatile Exception exception;

        Worker(final HttpServerConnection conn) {
            this.conn = conn;
        }

        @Override
        public void run() {
            HttpContext context = new BasicHttpContext();
            try {
                while (this.conn.isOpen() && !Thread.interrupted()) {
                    httpservice.handleRequest(this.conn, context);
                }
            } catch (Exception ex) {
                this.exception = ex;
            } finally {
                workers.remove(this);
                try {
                    this.conn.shutdown();
                } catch (IOException ignore) {
                }
            }
        }

        public void shutdown() {
            interrupt();
            try {
                this.conn.shutdown();
            } catch (IOException ignore) {
            }
        }

        public Exception getException() {
            return this.exception;
        }

    }

}