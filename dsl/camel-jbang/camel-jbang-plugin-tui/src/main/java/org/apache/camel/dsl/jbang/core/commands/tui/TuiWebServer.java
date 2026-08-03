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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import dev.tamboui.backend.aesh.AeshBackend;
import org.aesh.terminal.Connection;
import org.aesh.terminal.http.netty.NettyWebsocketTtyBootstrap;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;

/**
 * Serves the Camel TUI dashboard to a web browser over WebSocket, using Aesh's HTTP/WebSocket terminal bridge.
 * <p>
 * Each incoming connection gets its own {@link CamelMonitor} instance (running the same live-monitoring logic as a
 * local terminal session) driven by an {@link AeshBackend} wrapping that connection.
 * <p>
 * Binds to 127.0.0.1 only for security.
 */
class TuiWebServer {

    private static final Logger LOG = System.getLogger(TuiWebServer.class.getName());
    private static final long START_TIMEOUT_SECONDS = 10;

    private final int port;
    private final CamelJBangMain main;
    private final ClassLoader classLoader;
    private final String name;
    private final long refreshInterval;
    private final String theme;
    private final NettyWebsocketTtyBootstrap bootstrap = new NettyWebsocketTtyBootstrap();
    private final ExecutorService sessionExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "tui-web-session");
        t.setDaemon(true);
        return t;
    });

    TuiWebServer(int port, CamelJBangMain main, ClassLoader classLoader, String name, long refreshInterval,
                 String theme) {
        this.port = port;
        this.main = main;
        this.classLoader = classLoader;
        this.name = name;
        this.refreshInterval = refreshInterval;
        this.theme = theme;
    }

    void start() throws IOException {
        bootstrap.setHost("127.0.0.1");
        bootstrap.setPort(port);
        try {
            bootstrap.start(this::accept).get(START_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("Failed to start web terminal server", e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while starting web terminal server", e);
        } catch (TimeoutException e) {
            throw new IOException("Timed out starting web terminal server", e);
        }
    }

    void stop() {
        bootstrap.stop().join();
        sessionExecutor.shutdownNow();
    }

    private void accept(Connection connection) {
        sessionExecutor.submit(() -> {
            try {
                AeshBackend backend = new AeshBackend(connection);
                CamelMonitor monitor = new CamelMonitor(main, classLoader);
                monitor.name = name;
                monitor.refreshInterval = refreshInterval;
                monitor.theme = theme;
                monitor.webBackend = backend;
                monitor.call();
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Web TUI session ended with an error", e);
            } finally {
                try {
                    connection.close();
                } catch (Exception ignored) {
                    // connection already closing
                }
            }
        });
    }
}
