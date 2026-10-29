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
package org.apache.camel.dsl.jbang.core.commands.kaoto;

import java.io.IOException;
import java.net.BindException;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.web.Router;

class KaotoWebServer {

    private final int port;
    private final Path sourceDir;
    private final CountDownLatch shutdownLatch;
    private Vertx vertx;
    private HttpServer server;
    private boolean stopped;

    KaotoWebServer(int port, Path sourceDir, CountDownLatch shutdownLatch) {
        this.port = port;
        this.sourceDir = sourceDir;
        this.shutdownLatch = shutdownLatch;
    }

    void start() throws IOException {
        vertx = Vertx.vertx();

        Router router = Router.router(vertx);

        router.route().handler(ctx -> {
            ctx.response().putHeader("X-Frame-Options", "DENY");
            ctx.response().putHeader("Content-Security-Policy", "frame-ancestors 'none'");
            ctx.next();
        });

        KaotoRequestHandler.registerRoutes(router, sourceDir, shutdownLatch);

        HttpServerOptions options = new HttpServerOptions()
                .setHost("127.0.0.1")
                .setPort(port);

        server = vertx.createHttpServer(options);
        try {
            server.requestHandler(router).listen().toCompletionStage().toCompletableFuture().get();
        } catch (Exception e) {
            stop();
            Throwable cause = unwrap(e);
            if (cause instanceof BindException bindException) {
                throw bindException;
            }
            throw new IOException("Failed to start Kaoto server", cause);
        }
    }

    synchronized void stop() {
        if (stopped) {
            return;
        }
        stopped = true;
        if (server != null) {
            try {
                server.close().toCompletionStage().toCompletableFuture().get();
            } catch (Exception e) {
                // best effort
            }
        }
        if (vertx != null) {
            try {
                vertx.close().toCompletionStage().toCompletableFuture().get();
            } catch (Exception e) {
                // best effort
            }
        }
    }

    private static Throwable unwrap(Throwable t) {
        while (t.getCause() != null && t != t.getCause()) {
            t = t.getCause();
        }
        return t;
    }
}
