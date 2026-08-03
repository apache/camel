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
package org.apache.camel.component.mcp.server.vertx;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.spec.HttpHeaders;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpStreamableServerSession;
import io.modelcontextprotocol.spec.McpStreamableServerTransport;
import io.modelcontextprotocol.spec.McpStreamableServerTransportProvider;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.apache.camel.component.platform.http.vertx.VertxPlatformHttpRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * MCP streamable HTTP server transport serving through the Vert.x platform HTTP router: POST answering
 * {@code application/json} or {@code text/event-stream}, long-lived GET SSE channel with {@code Last-Event-ID} replay,
 * {@code Mcp-Session-Id} session management and DELETE for session termination.
 * <p>
 * This is the Vert.x equivalent of the MCP SDK's {@code HttpServletStreamableServerTransportProvider} (the SDK ships
 * only servlet and stdio server transports). Request handling is offloaded to the Vert.x worker pool (unordered);
 * response writes always run on the connection's event-loop context. The long-lived GET stream does not occupy a worker
 * thread.
 */
public class VertxMcpStreamableServerTransportProvider implements McpStreamableServerTransportProvider {

    public static final String MESSAGE_EVENT_TYPE = "message";

    private static final Logger LOG = LoggerFactory.getLogger(VertxMcpStreamableServerTransportProvider.class);

    private static final String ACCEPT = "Accept";
    private static final String APPLICATION_JSON = "application/json";
    private static final String TEXT_EVENT_STREAM = "text/event-stream";

    private final McpJsonMapper jsonMapper;
    private final String path;
    private final ConcurrentHashMap<String, McpStreamableServerSession> sessions = new ConcurrentHashMap<>();
    private final List<Route> routes = new ArrayList<>();

    private McpStreamableServerSession.Factory sessionFactory;
    private volatile boolean closing;

    public VertxMcpStreamableServerTransportProvider(McpJsonMapper jsonMapper, String path) {
        this.jsonMapper = jsonMapper;
        this.path = path;
    }

    @Override
    public void setSessionFactory(McpStreamableServerSession.Factory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    @Override
    public Mono<Void> notifyClients(String method, Object params) {
        if (sessions.isEmpty()) {
            return Mono.empty();
        }
        return Mono.fromRunnable(() -> sessions.values().forEach(session -> {
            try {
                session.sendNotification(method, params).block();
            } catch (Exception e) {
                LOG.debug("Failed to send notification to MCP session {}: {}", session.getId(), e.getMessage());
            }
        }));
    }

    @Override
    public Mono<Void> closeGracefully() {
        return Mono.fromRunnable(() -> {
            closing = true;
            sessions.values().forEach(session -> {
                try {
                    session.closeGracefully().block();
                } catch (Exception e) {
                    LOG.debug("Failed to close MCP session {}: {}", session.getId(), e.getMessage());
                }
            });
            sessions.clear();
        });
    }

    /**
     * Registers the POST/GET/DELETE routes for the MCP endpoint. Must be called after the MCP server has been built
     * (the server sets the session factory on construction).
     */
    public void registerRoutes(VertxPlatformHttpRouter router) {
        Vertx vertx = router.vertx();
        Route post = router.route(path).method(HttpMethod.POST);
        post.handler(BodyHandler.create(false));
        post.handler(ctx -> dispatch(vertx, ctx, this::handlePost));
        routes.add(post);
        Route get = router.route(path).method(HttpMethod.GET);
        get.handler(ctx -> dispatch(vertx, ctx, this::handleGet));
        routes.add(get);
        Route delete = router.route(path).method(HttpMethod.DELETE);
        delete.handler(ctx -> dispatch(vertx, ctx, this::handleDelete));
        routes.add(delete);
    }

    public void unregisterRoutes() {
        routes.forEach(Route::remove);
        routes.clear();
    }

    @FunctionalInterface
    private interface BlockingRequestHandler {
        void handle(RoutingContext ctx, Context connection) throws Exception;
    }

    private void dispatch(Vertx vertx, RoutingContext ctx, BlockingRequestHandler handler) {
        // capture the connection's event-loop context before offloading; all response writes go through it
        Context connection = vertx.getOrCreateContext();
        vertx.executeBlocking(() -> {
            handler.handle(ctx, connection);
            return null;
        }, false).onFailure(t -> {
            LOG.warn("Error handling MCP request", t);
            if (!ctx.response().ended()) {
                ctx.response().setStatusCode(500).end();
            }
        });
    }

    private void handlePost(RoutingContext ctx, Context connection) throws Exception {
        if (closing) {
            endWithStatus(connection, ctx, 503);
            return;
        }
        List<String> badRequestErrors = new ArrayList<>();
        String accept = ctx.request().getHeader(ACCEPT);
        if (accept == null || !accept.contains(TEXT_EVENT_STREAM)) {
            badRequestErrors.add("text/event-stream required in Accept header");
        }
        if (accept == null || !accept.contains(APPLICATION_JSON)) {
            badRequestErrors.add("application/json required in Accept header");
        }

        McpSchema.JSONRPCMessage message;
        try {
            message = McpSchema.deserializeJsonRpcMessage(jsonMapper, ctx.body().asString());
        } catch (Exception e) {
            respondError(connection, ctx, 400, McpError.builder(McpSchema.ErrorCodes.INVALID_REQUEST)
                    .message("Invalid message format: " + e.getMessage()).build());
            return;
        }

        if (message instanceof McpSchema.JSONRPCRequest request
                && McpSchema.METHOD_INITIALIZE.equals(request.method())) {
            if (respondBadRequest(connection, ctx, badRequestErrors)) {
                return;
            }
            handleInitialize(ctx, connection, request);
            return;
        }

        String sessionId = ctx.request().getHeader(HttpHeaders.MCP_SESSION_ID);
        if (sessionId == null || sessionId.isBlank()) {
            badRequestErrors.add("Session ID required in " + HttpHeaders.MCP_SESSION_ID + " header");
        }
        if (respondBadRequest(connection, ctx, badRequestErrors)) {
            return;
        }
        McpStreamableServerSession session = sessions.get(sessionId);
        if (session == null) {
            respondError(connection, ctx, 404, McpError.builder(McpSchema.ErrorCodes.INTERNAL_ERROR)
                    .message("Session not found: " + sessionId).build());
            return;
        }

        if (message instanceof McpSchema.JSONRPCResponse response) {
            session.accept(response).block();
            endWithStatus(connection, ctx, 202);
        } else if (message instanceof McpSchema.JSONRPCNotification notification) {
            session.accept(notification).block();
            endWithStatus(connection, ctx, 202);
        } else if (message instanceof McpSchema.JSONRPCRequest request) {
            VertxMcpSessionTransport transport = startSseResponse(ctx, connection, sessionId);
            try {
                session.responseStream(request, transport).block();
            } catch (Exception e) {
                LOG.warn("Failed to handle MCP request stream: {}", e.getMessage());
                transport.close();
            }
        } else {
            respondError(connection, ctx, 500,
                    McpError.builder(McpSchema.ErrorCodes.INVALID_REQUEST).message("Unknown message type").build());
        }
    }

    private void handleInitialize(RoutingContext ctx, Context connection, McpSchema.JSONRPCRequest request)
            throws Exception {
        McpSchema.InitializeRequest initializeRequest
                = jsonMapper.convertValue(request.params(), new TypeRef<McpSchema.InitializeRequest>() {
                });
        McpStreamableServerSession.McpStreamableServerSessionInit init = sessionFactory.startSession(initializeRequest);
        sessions.put(init.session().getId(), init.session());
        McpSchema.InitializeResult initResult = init.initResult().block();
        String json = jsonMapper.writeValueAsString(McpSchema.JSONRPCResponse.result(request.id(), initResult));
        connection.runOnContext(v -> ctx.response()
                .setStatusCode(200)
                .putHeader("Content-Type", APPLICATION_JSON)
                .putHeader(HttpHeaders.MCP_SESSION_ID, init.session().getId())
                .end(json));
    }

    private void handleGet(RoutingContext ctx, Context connection) {
        if (closing) {
            endWithStatus(connection, ctx, 503);
            return;
        }
        List<String> badRequestErrors = new ArrayList<>();
        String accept = ctx.request().getHeader(ACCEPT);
        if (accept == null || !accept.contains(TEXT_EVENT_STREAM)) {
            badRequestErrors.add("text/event-stream required in Accept header");
        }
        String sessionId = ctx.request().getHeader(HttpHeaders.MCP_SESSION_ID);
        if (sessionId == null || sessionId.isBlank()) {
            badRequestErrors.add("Session ID required in " + HttpHeaders.MCP_SESSION_ID + " header");
        }
        if (respondBadRequest(connection, ctx, badRequestErrors)) {
            return;
        }
        McpStreamableServerSession session = sessions.get(sessionId);
        if (session == null) {
            endWithStatus(connection, ctx, 404);
            return;
        }

        VertxMcpSessionTransport transport = startSseResponse(ctx, connection, sessionId);
        String lastEventId = ctx.request().getHeader(HttpHeaders.LAST_EVENT_ID);
        if (lastEventId != null) {
            try {
                session.replay(lastEventId).toIterable().forEach(message -> transport.sendMessage(message).block());
            } catch (Exception e) {
                LOG.warn("Failed to replay MCP messages: {}", e.getMessage());
                transport.close();
            }
        } else {
            McpStreamableServerSession.McpStreamableServerSessionStream listeningStream
                    = session.listeningStream(transport);
            connection.runOnContext(v -> ctx.response().closeHandler(x -> listeningStream.close()));
        }
    }

    private void handleDelete(RoutingContext ctx, Context connection) {
        if (closing) {
            endWithStatus(connection, ctx, 503);
            return;
        }
        String sessionId = ctx.request().getHeader(HttpHeaders.MCP_SESSION_ID);
        if (sessionId == null || sessionId.isBlank()) {
            respondError(connection, ctx, 400, McpError.builder(McpSchema.ErrorCodes.METHOD_NOT_FOUND)
                    .message("Session ID required in " + HttpHeaders.MCP_SESSION_ID + " header").build());
            return;
        }
        McpStreamableServerSession session = sessions.get(sessionId);
        if (session == null) {
            endWithStatus(connection, ctx, 404);
            return;
        }
        session.delete().block();
        sessions.remove(sessionId);
        endWithStatus(connection, ctx, 200);
    }

    private VertxMcpSessionTransport startSseResponse(RoutingContext ctx, Context connection, String sessionId) {
        connection.runOnContext(v -> ctx.response()
                .setChunked(true)
                .putHeader("Content-Type", TEXT_EVENT_STREAM)
                .putHeader("Cache-Control", "no-cache"));
        return new VertxMcpSessionTransport(sessionId, ctx.response(), connection);
    }

    private boolean respondBadRequest(Context connection, RoutingContext ctx, List<String> errors) {
        if (errors.isEmpty()) {
            return false;
        }
        respondError(connection, ctx, 400,
                McpError.builder(McpSchema.ErrorCodes.METHOD_NOT_FOUND).message(String.join("; ", errors)).build());
        return true;
    }

    private void respondError(Context connection, RoutingContext ctx, int status, McpError error) {
        String json;
        try {
            json = jsonMapper.writeValueAsString(error);
        } catch (Exception e) {
            json = "{}";
        }
        String body = json;
        connection.runOnContext(v -> ctx.response()
                .setStatusCode(status)
                .putHeader("Content-Type", APPLICATION_JSON)
                .end(body));
    }

    private void endWithStatus(Context connection, RoutingContext ctx, int status) {
        connection.runOnContext(v -> ctx.response().setStatusCode(status).end());
    }

    /**
     * Per-connection transport writing SSE frames on the connection's event-loop context. The SDK session awaits each
     * write, providing natural backpressure.
     */
    private final class VertxMcpSessionTransport implements McpStreamableServerTransport {

        private final String sessionId;
        private final HttpServerResponse response;
        private final Context connection;
        private volatile boolean closed;

        private VertxMcpSessionTransport(String sessionId, HttpServerResponse response, Context connection) {
            this.sessionId = sessionId;
            this.response = response;
            this.connection = connection;
        }

        @Override
        public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message) {
            return sendMessage(message, null);
        }

        @Override
        public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message, String messageId) {
            return Mono.create(sink -> connection.runOnContext(v -> {
                if (closed || response.ended() || response.closed()) {
                    sink.success();
                    return;
                }
                try {
                    String json = jsonMapper.writeValueAsString(message);
                    String frame = "id: " + (messageId != null ? messageId : sessionId) + "\n"
                                   + "event: " + MESSAGE_EVENT_TYPE + "\n"
                                   + "data: " + json + "\n\n";
                    response.write(frame).onComplete(result -> {
                        if (!result.succeeded()) {
                            LOG.debug("Failed to write to MCP session {}: {}", sessionId,
                                    result.cause() != null ? result.cause().getMessage() : "unknown");
                            closed = true;
                        }
                        sink.success();
                    });
                } catch (Exception e) {
                    LOG.warn("Failed to send message to MCP session {}: {}", sessionId, e.getMessage());
                    closed = true;
                    sink.success();
                }
            }));
        }

        @Override
        public <T> T unmarshalFrom(Object data, TypeRef<T> typeRef) {
            return jsonMapper.convertValue(data, typeRef);
        }

        @Override
        public Mono<Void> closeGracefully() {
            return Mono.fromRunnable(this::close);
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            connection.runOnContext(v -> {
                if (!response.ended() && !response.closed()) {
                    response.end();
                }
            });
        }
    }
}
