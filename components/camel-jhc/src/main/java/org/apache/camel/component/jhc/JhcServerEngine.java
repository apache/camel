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
package org.apache.camel.component.jhc;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.ThreadFactory;

import javax.net.ssl.SSLContext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.impl.nio.DefaultServerIOEventDispatch;
import org.apache.http.impl.nio.reactor.DefaultListeningIOReactor;
import org.apache.http.impl.nio.reactor.SSLServerIOEventDispatch;
import org.apache.http.nio.reactor.IOEventDispatch;
import org.apache.http.nio.reactor.IOReactorException;
import org.apache.http.nio.reactor.ListeningIOReactor;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpRequestHandlerRegistry;

public class JhcServerEngine {
    private static final Log LOG = LogFactory.getLog(JhcServerEngine.class);
    private final HttpParams params;
    private int port;
    private String protocol;
    private int nbThreads = 2;
    private ListeningIOReactor ioReactor;
    private ThreadFactory threadFactory;
    private Thread runner;
    private SSLContext sslContext;
    private AsyncBufferingHttpServiceHandler serviceHandler;
    private HttpRequestHandlerRegistry handlerRegistry;
    private boolean isStarted;
    private int referenceCounter;

    JhcServerEngine(HttpParams params, int port, String protocol) {
        this.params = params;
        serviceHandler = new AsyncBufferingHttpServiceHandler(params);
        handlerRegistry = new HttpRequestHandlerRegistry();
        serviceHandler.setHandlerResolver(handlerRegistry);
        this.port = port;
        this.protocol = protocol;
    }


    public int getPort() {
        return port;
    }

    public String getProtocol() {
        return this.protocol;
    }

    public void setSslContext(SSLContext sslContext) {
        this.sslContext = sslContext;
    }

    public SSLContext getSslContext() {
        return this.sslContext;
    }

    public synchronized void register(String pattern, AsyncHttpRequestHandler handler) {
        handlerRegistry.register(pattern, handler);
        referenceCounter++;
    }

    public synchronized void unregister(String pattern) {
        handlerRegistry.unregister(pattern);
        referenceCounter--;
    }

    public int getReferenceCounter() {
        return referenceCounter;
    }

    public boolean isStarted() {
        return isStarted;
    }

    public void start() throws IOReactorException {
        final SocketAddress addr = new InetSocketAddress(port);
        ioReactor = new DefaultListeningIOReactor(nbThreads, threadFactory, params);

        final IOEventDispatch ioEventDispatch;
        if ("https".equals(protocol)) {
            ioEventDispatch = new SSLServerIOEventDispatch(serviceHandler, sslContext, params);
        } else {
            ioEventDispatch = new DefaultServerIOEventDispatch(serviceHandler, params);
        }
        runner = new Thread() {
            public void run() {
                try {
                    ioReactor.listen(addr);
                    isStarted = true;
                    ioReactor.execute(ioEventDispatch);
                } catch (InterruptedIOException ex) {
                    LOG.info("Interrupted");
                } catch (IOException e) {
                    LOG.warn("I/O error: " + e.getMessage());
                }
                LOG.debug("Shutdown");
            }
        };
        runner.start();
    }

    public void stop() throws IOException {
        LOG.debug("Stopping the jhc ioReactor ");
        ioReactor.shutdown();
        LOG.debug("Waiting the runner");
        try {
            runner.join();
        } catch (InterruptedException e) {
            //do nothing here
        }
        isStarted = false;
        LOG.debug("Runner stopped");
    }
}
