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
package org.apache.camel.component.vertx.websocket;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.VertxException;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.WebSocket;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.impl.btc.BlockedThreadEvent;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit5.CamelTestSupport;

public class VertxWebSocketTestSupport extends CamelTestSupport {

    protected final int port = AvailablePortFinder.getNextAvailable();
    protected final int port2 = AvailablePortFinder.getNextAvailable();

    /**
     * Returns the randomized port used for the Vert.x server if no port was provided to the consumer.
     */
    public int getVertxServerRandomPort() {
        VertxWebsocketComponent component = context.getComponent("vertx-websocket", VertxWebsocketComponent.class);
        Map<VertxWebsocketHostKey, VertxWebsocketHost> registry = component.getVertxHostRegistry();
        return registry.values()
                .stream()
                .filter(wsHost -> wsHost.getPort() != port)
                .filter(wsHost -> wsHost.getPort() != port2)
                .findFirst()
                .get()
                .getPort();
    }

    public WebSocket openWebSocketConnection(String host, int port, String path, Consumer<String> handler) throws Exception {
        HttpClient client = Vertx.vertx().createHttpClient();
        CompletableFuture<WebSocket> future = client.webSocket(port, host, path)
                .toCompletionStage()
                .toCompletableFuture();
        WebSocket webSocket = future.get(5, TimeUnit.SECONDS);
        webSocket.textMessageHandler(handler::accept);
        return webSocket;
    }

    public Router createRouter(String path, CountDownLatch latch) {
        return createRouter(path, null, latch);
    }

    public Router createRouter(String path, Handler<RoutingContext> handler, CountDownLatch latch) {
        Router router = Router.router(Vertx.vertx());
        Route route = router.route(path);

        if (handler == null) {
            handler = (RoutingContext ctx) -> ctx.request().toWebSocket()
                    .onSuccess(webSocket -> webSocket.textMessageHandler(message -> {
                        webSocket.writeTextMessage("Hello world");
                        latch.countDown();
                    }));
        }

        route.handler(handler);
        return router;
    }

    public Vertx createVertxWithThreadBlockedHandler(Handler<BlockedThreadEvent> handler) {
        VertxOptions vertxOptions = new VertxOptions();
        vertxOptions.setMaxEventLoopExecuteTime(500);
        vertxOptions.setMaxEventLoopExecuteTimeUnit(TimeUnit.MILLISECONDS);
        vertxOptions.setBlockedThreadCheckInterval(10);
        vertxOptions.setBlockedThreadCheckIntervalUnit(TimeUnit.MILLISECONDS);
        Vertx vertx = Vertx.vertx(vertxOptions);
        ((VertxInternal) vertx).blockedThreadChecker().setThreadBlockedHandler(handler);
        return vertx;
    }

    static class BlockedThreadReporter implements Handler<BlockedThreadEvent> {
        private boolean eventLoopBlocked;

        @Override
        public void handle(BlockedThreadEvent event) {
            VertxException stackTrace = new VertxException("Thread blocked");
            stackTrace.setStackTrace(event.thread().getStackTrace());
            stackTrace.printStackTrace();
            eventLoopBlocked = true;
        }

        public boolean isEventLoopBlocked() {
            return eventLoopBlocked;
        }

        public void reset() {
            eventLoopBlocked = false;
        }
    }
}
