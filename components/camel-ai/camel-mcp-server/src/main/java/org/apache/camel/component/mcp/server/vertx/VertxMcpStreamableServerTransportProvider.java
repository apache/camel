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

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.server.transport.DefaultServerTransportSecurityValidator;
import io.modelcontextprotocol.server.transport.HeaderAccessor;
import io.modelcontextprotocol.server.transport.ServerHttpHeaderValidator;
import io.modelcontextprotocol.server.transport.ServerTransportSecurityException;
import io.modelcontextprotocol.spec.HttpHeaders;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpStreamableServerSession;
import io.modelcontextprotocol.spec.McpStreamableServerTransport;
import io.modelcontextprotocol.spec.McpStreamableServerTransportProvider;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.net.HostAndPort;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.SecurityPolicyHandler;
import org.apache.camel.CamelContext;
import org.apache.camel.component.mcp.server.McpServerHosts;
import org.apache.camel.component.mcp.server.McpServerOrigins;
import org.apache.camel.component.platform.http.vertx.VertxPlatformHttpRouter;
import org.apache.camel.http.base.OAuthHttpSecuritySupport;
import org.apache.camel.spi.OAuthTokenValidationResult;
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
 * <p>
 * Every request carrying an {@code Origin} header is checked against the allowed origins before any other handling, as
 * the MCP streamable HTTP transport requires; a request from an origin that is not allowed gets HTTP 403 with a
 * JSON-RPC error and never reaches the MCP session. Requests without an {@code Origin} header are served.
 * <p>
 * When allowed hosts are configured, the {@code Host} header is checked in the same handler: a request whose
 * {@code Host} is missing or not allowed gets HTTP 421 (Misdirected Request), which protects a server on a private
 * address against DNS rebinding. Without allowed hosts the {@code Host} header is not checked.
 */
public class VertxMcpStreamableServerTransportProvider implements McpStreamableServerTransportProvider {

    private static final String MESSAGE_EVENT_TYPE = "message";

    private static final Logger LOG = LoggerFactory.getLogger(VertxMcpStreamableServerTransportProvider.class);

    private static final Duration NOTIFICATION_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration INITIALIZATION_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration KEEP_ALIVE_PING_TIMEOUT = Duration.ofSeconds(5);
    private static final int MAX_CONSECUTIVE_PING_FAILURES = 3;

    private static final String ACCEPT = "Accept";
    private static final String APPLICATION_JSON = "application/json";
    private static final String TEXT_EVENT_STREAM = "text/event-stream";
    private static final String ORIGIN = "Origin";
    private static final String HOST = "Host";
    // the status the MCP SDK validator answers a Host that is not allowed with
    private static final int MISDIRECTED_REQUEST = 421;

    /**
     * The origins allowed when none are configured: loopback hosts on any port, so browser-based tools on the same
     * machine keep working.
     */
    static final List<String> DEFAULT_ALLOWED_ORIGINS = List.of(
            "http://localhost:*", "https://localhost:*",
            "http://127.0.0.1:*", "https://127.0.0.1:*",
            "http://[::1]:*", "https://[::1]:*");

    private final McpJsonMapper jsonMapper;
    private final String path;
    private final long sessionKeepAliveIntervalMs;
    private final long sessionIdleTtlMs;
    private final ServerHttpHeaderValidator originValidator;
    private final CamelContext camelContext;
    private final OAuthHttpSecuritySupport oauthSecurity;
    private final List<String> requiredScopes;
    private final McpOAuthProtectedResourceMetadata protectedResourceMetadata;
    // Both security handlers run before the request body handler.
    private final SecurityPolicyHandler originHandler = this::checkOrigin;
    private final SecurityPolicyHandler oauthHandler = this::checkOAuth;
    private final ConcurrentHashMap<String, ManagedSession> sessions = new ConcurrentHashMap<>();
    private final List<Route> routes = new ArrayList<>();

    private McpStreamableServerSession.Factory sessionFactory;
    private volatile boolean closing;
    private Vertx vertx;
    private Long keepAliveTimerId;
    private Long idleTimerId;
    private final AtomicBoolean keepAliveInProgress = new AtomicBoolean();
    private final AtomicBoolean idleEvictionInProgress = new AtomicBoolean();

    public VertxMcpStreamableServerTransportProvider(McpJsonMapper jsonMapper, String path) {
        this(jsonMapper, path, 0, 0);
    }

    public VertxMcpStreamableServerTransportProvider(
                                                     McpJsonMapper jsonMapper, String path, long sessionKeepAliveIntervalMs,
                                                     long sessionIdleTtlMs) {
        this(jsonMapper, path, sessionKeepAliveIntervalMs, sessionIdleTtlMs, null);
    }

    /**
     * @param allowedOrigins the origins allowed to call the endpoint, each exact (e.g. {@code https://app.example.com})
     *                       or with a wildcard port (e.g. {@code http://localhost:*}), normalized with
     *                       {@link McpServerOrigins#normalizeAllowedOrigin(String)}; {@code null} for
     *                       {@link #DEFAULT_ALLOWED_ORIGINS}
     */
    public VertxMcpStreamableServerTransportProvider(
                                                     McpJsonMapper jsonMapper, String path, long sessionKeepAliveIntervalMs,
                                                     long sessionIdleTtlMs, List<String> allowedOrigins) {
        this(jsonMapper, path, sessionKeepAliveIntervalMs, sessionIdleTtlMs, allowedOrigins,
             null, null, null, null, null, false);
    }

    /**
     * Creates the transport with an explicit opt-in for an HTTP authorization-server issuer. The opt-in comes from the
     * OAuth profile's allow-insecure-http setting; the MCP resource URI is always HTTPS.
     */
    public VertxMcpStreamableServerTransportProvider(
                                                     McpJsonMapper jsonMapper,
                                                     String path,
                                                     long sessionKeepAliveIntervalMs,
                                                     long sessionIdleTtlMs,
                                                     List<String> allowedOrigins,
                                                     CamelContext camelContext,
                                                     OAuthHttpSecuritySupport oauthSecurity,
                                                     String requiredScopes,
                                                     String resourceUri,
                                                     String authorizationServerIssuer,
                                                     boolean allowInsecureAuthorizationServer) {
        this(jsonMapper, path, sessionKeepAliveIntervalMs, sessionIdleTtlMs, allowedOrigins, camelContext,
             oauthSecurity, requiredScopes, resourceUri, authorizationServerIssuer, allowInsecureAuthorizationServer,
             null);
    }

    /**
     * Creates the transport with allowed hosts.
     *
     * @param allowedHosts the hosts allowed in the {@code Host} header, each exact (e.g. {@code mcp.example.com}) or
     *                     with a wildcard port (e.g. {@code localhost:*}, which also allows a {@code Host} without a
     *                     port), normalized with {@link McpServerHosts#normalizeAllowedHost(String)}; {@code null} to
     *                     not check the {@code Host} header
     */
    public VertxMcpStreamableServerTransportProvider(
                                                     McpJsonMapper jsonMapper,
                                                     String path,
                                                     long sessionKeepAliveIntervalMs,
                                                     long sessionIdleTtlMs,
                                                     List<String> allowedOrigins,
                                                     CamelContext camelContext,
                                                     OAuthHttpSecuritySupport oauthSecurity,
                                                     String requiredScopes,
                                                     String resourceUri,
                                                     String authorizationServerIssuer,
                                                     boolean allowInsecureAuthorizationServer,
                                                     List<String> allowedHosts) {
        this.jsonMapper = jsonMapper;
        this.path = path;
        this.sessionKeepAliveIntervalMs = sessionKeepAliveIntervalMs;
        this.sessionIdleTtlMs = sessionIdleTtlMs;
        this.camelContext = camelContext;
        this.oauthSecurity = oauthSecurity;
        List<String> configuredScopes = parseRequiredScopes(requiredScopes);
        this.protectedResourceMetadata = oauthSecurity != null
                ? McpOAuthProtectedResourceMetadata.create(
                        resourceUri, path, authorizationServerIssuer, configuredScopes,
                        allowInsecureAuthorizationServer)
                : null;
        this.requiredScopes = configuredScopes;
        this.originValidator = DefaultServerTransportSecurityValidator.builder()
                .allowedOrigins(allowedOrigins != null
                        ? allowedOrigins.stream().map(McpServerOrigins::normalizeAllowedOrigin).toList()
                        : DEFAULT_ALLOWED_ORIGINS)
                .allowedHosts(allowedHosts != null ? hostPatterns(allowedHosts) : List.of())
                .build();
    }

    /**
     * The hosts as the MCP SDK validator matches them: a {@code host:*} entry matches only a {@code Host} with a port
     * there, so the host without a port (the default port of the scheme) is added next to it.
     */
    private static List<String> hostPatterns(List<String> allowedHosts) {
        List<String> answer = new ArrayList<>();
        for (String entry : allowedHosts) {
            String host = McpServerHosts.normalizeAllowedHost(entry);
            answer.add(host);
            if (host.endsWith(":*")) {
                answer.add(host.substring(0, host.length() - 2));
            }
        }
        return answer;
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
        return Mono.fromRunnable(() -> sessions.values().forEach(managed -> {
            try {
                // bounded so a single stalled session cannot starve notifications to healthy sessions
                managed.session.sendNotification(method, params).block(NOTIFICATION_TIMEOUT);
            } catch (Exception e) {
                LOG.debug("Failed to send notification to MCP session {}: {}", managed.session.getId(), e.getMessage());
            }
        }));
    }

    @Override
    public Mono<Void> closeGracefully() {
        return Mono.fromRunnable(() -> {
            closing = true;
            stopSessionMaintenance();
            sessions.values().forEach(managed -> {
                try {
                    managed.session.closeGracefully().block(NOTIFICATION_TIMEOUT);
                } catch (Exception e) {
                    LOG.debug("Failed to close MCP session {}: {}", managed.session.getId(), e.getMessage());
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
        vertx = router.vertx();
        if (protectedResourceMetadata != null) {
            Route metadata = router.route(protectedResourceMetadata.metadataPath()).method(HttpMethod.GET);
            metadata.handler(this::handleProtectedResourceMetadata);
            routes.add(metadata);
        }
        Route post = router.route(path).method(HttpMethod.POST);
        // the origin check comes first, so a request that is not allowed is not even read
        post.handler(originHandler);
        if (oauthSecurity != null) {
            post.handler(oauthHandler);
        }
        post.handler(BodyHandler.create(false));
        post.handler(ctx -> dispatch(vertx, ctx, this::handlePost));
        routes.add(post);
        Route get = router.route(path).method(HttpMethod.GET);
        get.handler(originHandler);
        if (oauthSecurity != null) {
            get.handler(oauthHandler);
        }
        get.handler(ctx -> dispatch(vertx, ctx, this::handleGet));
        routes.add(get);
        Route delete = router.route(path).method(HttpMethod.DELETE);
        delete.handler(originHandler);
        if (oauthSecurity != null) {
            delete.handler(oauthHandler);
        }
        delete.handler(ctx -> dispatch(vertx, ctx, this::handleDelete));
        routes.add(delete);
        startSessionMaintenance();
    }

    public void unregisterRoutes() {
        stopSessionMaintenance();
        routes.forEach(Route::remove);
        routes.clear();
    }

    int sessionCount() {
        return sessions.size();
    }

    /**
     * Serves a request without an {@code Origin} header or from an allowed origin, and, when allowed hosts are
     * configured, with an allowed {@code Host}; answers any other with HTTP 403 (origin) or 421 (host) and a JSON-RPC
     * error that has no {@code id}. Runs on the event loop before any other handler of the route.
     */
    private void checkOrigin(RoutingContext ctx) {
        try {
            originValidator.validate(new VertxHeaderAccessor(ctx));
        } catch (ServerTransportSecurityException e) {
            String message;
            if (e.getStatusCode() == MISDIRECTED_REQUEST) {
                LOG.debug("Rejected MCP {} request on {}: host {} is not allowed", ctx.request().method(), path,
                        ctx.request().authority());
                message = "Host not allowed";
            } else {
                LOG.debug("Rejected MCP {} request on {}: origin {} is not allowed", ctx.request().method(), path,
                        ctx.request().getHeader(ORIGIN));
                message = "Origin not allowed";
            }
            ctx.response()
                    .setStatusCode(e.getStatusCode())
                    .putHeader("Content-Type", APPLICATION_JSON)
                    .end(errorBody(McpSchema.ErrorCodes.INVALID_REQUEST, message));
            return;
        }
        ctx.next();
    }

    /**
     * Validates a Bearer token before the MCP body handler or MCP dispatch runs. Validation executes on a worker thread
     * because JWKS and introspection may require outbound HTTP calls.
     */
    private void checkOAuth(RoutingContext ctx) {
        List<String> authorizations = List.copyOf(ctx.request().headers().getAll("Authorization"));
        ctx.request().pause();

        vertx.executeBlocking(
                () -> oauthSecurity.validate(camelContext, authorizations),
                false)
                .onComplete(ar -> vertx.runOnContext(ignored -> {
                    if (ar.failed()) {
                        LOG.warn("Unable to execute MCP OAuth validation", ar.cause());
                        ctx.request().resume();
                        ctx.response().setStatusCode(503).end("Service Unavailable");
                        return;
                    }

                    OAuthHttpSecuritySupport.Validation validation = ar.result();
                    if (!validation.isAuthenticated()) {
                        ctx.request().resume();
                        ctx.response()
                                .setStatusCode(validation.getRejectionStatusCode())
                                .putHeader("Content-Type", "text/plain");
                        String challenge = validation.getWwwAuthenticate();
                        if (validation.getRejectionStatusCode() == 401 && protectedResourceMetadata != null
                                && challenge != null) {
                            challenge = protectedResourceMetadata.addToWwwAuthenticate(challenge);
                        }
                        if (challenge != null) {
                            ctx.response().putHeader("WWW-Authenticate", challenge);
                        }
                        ctx.response().end(validation.getResponseBody());
                        return;
                    }

                    OAuthTokenValidationResult result = validation.getValidationResult();
                    List<String> missingScopes = requiredScopes.stream()
                            .filter(scope -> !result.hasScope(scope))
                            .toList();

                    if (!missingScopes.isEmpty()) {
                        ctx.request().resume();
                        String challenge = "Bearer error=\"insufficient_scope\"";
                        if (protectedResourceMetadata != null) {
                            challenge = protectedResourceMetadata.addToWwwAuthenticate(challenge);
                        } else {
                            challenge += ", scope=\"" + String.join(" ", requiredScopes) + "\"";
                        }
                        ctx.response()
                                .setStatusCode(403)
                                .putHeader("Content-Type", "text/plain")
                                .putHeader("WWW-Authenticate", challenge)
                                .end("Forbidden");
                        return;
                    }

                    ctx.put(VertxMcpServerEngine.TRANSPORT_OAUTH_RESULT_KEY, result);

                    // ctx.next() installs the next handler (BodyHandler for POST); resume only
                    // after that handler has had a chance to register its body listeners.
                    ctx.next();
                    ctx.request().resume();
                }));
    }

    /**
     * Serves the public RFC 9728 metadata document. This route is intentionally not protected by the MCP bearer
     * authentication handler; clients need the document before they can obtain an access token.
     */
    private void handleProtectedResourceMetadata(RoutingContext ctx) {
        try {
            String body = jsonMapper.writeValueAsString(protectedResourceMetadata.asMap());
            ctx.response()
                    .setStatusCode(200)
                    .putHeader("Content-Type", APPLICATION_JSON)
                    .end(body);
        } catch (Exception e) {
            LOG.warn("Unable to serialize MCP protected-resource metadata", e);
            ctx.response().setStatusCode(500).end();
        }
    }

    /**
     * Parses the comma-separated OAuth scope configuration and validates each scope-token before it is ever written
     * into a WWW-Authenticate response header.
     */
    private static List<String> parseRequiredScopes(String configured) {
        if (configured == null || configured.isBlank()) {
            return List.of();
        }

        Set<String> scopes = new LinkedHashSet<>();
        for (String item : configured.split(",")) {
            String scope = item.trim();
            if (scope.isEmpty()) {
                continue;
            }
            if (!isValidScopeToken(scope)) {
                throw new IllegalArgumentException("Invalid character in MCP required OAuth scope configuration");
            }
            scopes.add(scope);
        }
        return List.copyOf(scopes);
    }

    private static boolean isValidScopeToken(String scope) {
        for (int i = 0; i < scope.length(); i++) {
            char ch = scope.charAt(i);
            boolean valid = ch == 0x21 || (ch >= 0x23 && ch <= 0x5B) || (ch >= 0x5D && ch <= 0x7E);
            if (!valid) {
                return false;
            }
        }
        return true;
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
        String contentType = ctx.request().getHeader("Content-Type");
        if (contentType == null || !contentType.contains(APPLICATION_JSON)) {
            respondError(connection, ctx, 415, McpSchema.ErrorCodes.INVALID_REQUEST,
                    "Content-Type application/json required");
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
            LOG.debug("Invalid MCP message: {}", e.getMessage());
            respondError(connection, ctx, 400, McpSchema.ErrorCodes.PARSE_ERROR, "Invalid message format");
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
        ManagedSession managed = sessions.get(sessionId);
        if (managed == null || !isOwnedByAuthenticatedSubject(ctx, managed)) {
            // Do not disclose whether a session exists to a different authenticated subject.
            respondError(connection, ctx, 404, McpSchema.ErrorCodes.INVALID_REQUEST, "Session not found");
            return;
        }
        managed.touch();

        if (message instanceof McpSchema.JSONRPCResponse response) {
            managed.session.accept(response).block();
            endWithStatus(connection, ctx, 202);
        } else if (message instanceof McpSchema.JSONRPCNotification notification) {
            managed.session.accept(notification).block();
            endWithStatus(connection, ctx, 202);
        } else if (message instanceof McpSchema.JSONRPCRequest request) {
            VertxMcpSessionTransport transport = startSseResponse(ctx, connection, sessionId);
            McpTransportContext transportContext = toTransportContext(ctx);
            try {
                managed.session.responseStream(request, transport)
                        .contextWrite(reactorCtx -> reactorCtx.put(McpTransportContext.KEY, transportContext))
                        .block();
            } catch (Exception e) {
                LOG.warn("Failed to handle MCP request stream: {}", e.getMessage());
                transport.close();
            }
        } else {
            respondError(connection, ctx, 500, McpSchema.ErrorCodes.INVALID_REQUEST, "Unknown message type");
        }
    }

    /**
     * Builds the MCP transport context with both the platform-http principal (when present) and the OAuth validation
     * result produced by the MCP OAuth handler.
     */
    private static McpTransportContext toTransportContext(RoutingContext ctx) {
        Map<String, Object> values = new LinkedHashMap<>();

        User user = ctx.user();
        if (user != null) {
            values.put(VertxMcpServerEngine.TRANSPORT_PRINCIPAL_KEY, user);
        }

        OAuthTokenValidationResult oauthResult
                = ctx.get(VertxMcpServerEngine.TRANSPORT_OAUTH_RESULT_KEY);
        if (oauthResult != null) {
            values.put(VertxMcpServerEngine.TRANSPORT_OAUTH_RESULT_KEY, oauthResult);
        }

        return values.isEmpty() ? McpTransportContext.EMPTY : McpTransportContext.create(values);
    }

    private void handleInitialize(RoutingContext ctx, Context connection, McpSchema.JSONRPCRequest request)
            throws Exception {
        OAuthTokenValidationResult oauthResult = ctx.get(VertxMcpServerEngine.TRANSPORT_OAUTH_RESULT_KEY);
        String subject = oauthResult != null ? oauthResult.getSubject() : null;
        if (oauthSecurity != null && (subject == null || subject.isBlank())) {
            // A valid access token without a stable subject cannot safely own a stateful MCP session.
            respondError(connection, ctx, 403, McpSchema.ErrorCodes.INVALID_REQUEST,
                    "OAuth token subject is required for MCP sessions");
            return;
        }

        McpSchema.InitializeRequest initializeRequest
                = jsonMapper.convertValue(request.params(), new TypeRef<McpSchema.InitializeRequest>() {
                });
        McpStreamableServerSession.McpStreamableServerSessionInit init = sessionFactory.startSession(initializeRequest);
        sessions.put(init.session().getId(), new ManagedSession(init.session(), subject));
        McpSchema.InitializeResult initResult = init.initResult().block(INITIALIZATION_TIMEOUT);
        String json = jsonMapper.writeValueAsString(McpSchema.JSONRPCResponse.result(request.id(), initResult));
        connection.runOnContext(v -> ctx.response()
                .setStatusCode(200)
                .putHeader("Content-Type", APPLICATION_JSON)
                .putHeader(HttpHeaders.MCP_SESSION_ID, init.session().getId())
                .end(json));
    }

    private boolean isOwnedByAuthenticatedSubject(RoutingContext ctx, ManagedSession managed) {
        if (oauthSecurity == null) {
            return true;
        }
        OAuthTokenValidationResult oauthResult = ctx.get(VertxMcpServerEngine.TRANSPORT_OAUTH_RESULT_KEY);
        String subject = oauthResult != null ? oauthResult.getSubject() : null;
        return subject != null && subject.equals(managed.subject);
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
        ManagedSession managed = sessions.get(sessionId);
        if (managed == null || !isOwnedByAuthenticatedSubject(ctx, managed)) {
            endWithStatus(connection, ctx, 404);
            return;
        }
        managed.touch();

        VertxMcpSessionTransport transport = startSseResponse(ctx, connection, sessionId);
        String lastEventId = ctx.request().getHeader(HttpHeaders.LAST_EVENT_ID);
        if (lastEventId != null) {
            try {
                managed.session.replay(lastEventId).toIterable().forEach(message -> transport.sendMessage(message).block());
            } catch (Exception e) {
                LOG.warn("Failed to replay MCP messages: {}", e.getMessage());
                transport.close();
            }
        } else {
            McpStreamableServerSession.McpStreamableServerSessionStream listeningStream
                    = managed.session.listeningStream(transport);
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
            respondError(connection, ctx, 400, McpSchema.ErrorCodes.INVALID_REQUEST,
                    "Session ID required in " + HttpHeaders.MCP_SESSION_ID + " header");
            return;
        }
        ManagedSession managed = sessions.get(sessionId);
        if (managed == null || !isOwnedByAuthenticatedSubject(ctx, managed)) {
            endWithStatus(connection, ctx, 404);
            return;
        }
        managed.session.delete().block();
        sessions.remove(sessionId);
        endWithStatus(connection, ctx, 200);
    }

    private void startSessionMaintenance() {
        if (vertx == null) {
            return;
        }
        if (sessionKeepAliveIntervalMs > 0) {
            keepAliveTimerId = vertx.setPeriodic(sessionKeepAliveIntervalMs, id -> {
                if (!keepAliveInProgress.compareAndSet(false, true)) {
                    return;
                }
                vertx.executeBlocking(() -> {
                    try {
                        pingSessions();
                    } finally {
                        keepAliveInProgress.set(false);
                    }
                    return null;
                }, false);
            });
        }
        if (sessionIdleTtlMs > 0) {
            long scanInterval = Math.max(100L, Math.min(sessionIdleTtlMs / 2, 1_000L));
            idleTimerId = vertx.setPeriodic(scanInterval, id -> {
                if (!idleEvictionInProgress.compareAndSet(false, true)) {
                    return;
                }
                vertx.executeBlocking(() -> {
                    try {
                        evictIdleSessions();
                    } finally {
                        idleEvictionInProgress.set(false);
                    }
                    return null;
                }, false);
            });
        }
    }

    private void stopSessionMaintenance() {
        if (vertx == null) {
            return;
        }
        if (keepAliveTimerId != null) {
            vertx.cancelTimer(keepAliveTimerId);
            keepAliveTimerId = null;
        }
        if (idleTimerId != null) {
            vertx.cancelTimer(idleTimerId);
            idleTimerId = null;
        }
    }

    private void pingSessions() {
        if (closing || sessions.isEmpty()) {
            return;
        }
        for (ManagedSession managed : sessions.values()) {
            try {
                managed.session.sendRequest(McpSchema.METHOD_PING, null, new TypeRef<Object>() {
                }).block(KEEP_ALIVE_PING_TIMEOUT);
                managed.resetPingFailures();
                managed.touch();
            } catch (Exception e) {
                if (isMissingTransport(e)) {
                    // POST-only clients without an open GET stream cannot receive pings; idle TTL handles orphans
                    continue;
                }
                if (managed.recordPingFailure() >= MAX_CONSECUTIVE_PING_FAILURES) {
                    evictSession(managed.session.getId(), "keep-alive ping failed");
                }
            }
        }
    }

    private static boolean isMissingTransport(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof IllegalStateException && current.getMessage() != null
                    && current.getMessage().contains("Stream unavailable")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void evictIdleSessions() {
        if (closing || sessions.isEmpty()) {
            return;
        }
        long cutoff = System.nanoTime() - Duration.ofMillis(sessionIdleTtlMs).toNanos();
        sessions.forEach((sessionId, managed) -> {
            if (managed.lastActivityNanos() < cutoff) {
                evictSession(sessionId, "idle TTL exceeded");
            }
        });
    }

    private void evictSession(String sessionId, String reason) {
        ManagedSession managed = sessions.remove(sessionId);
        if (managed == null) {
            return;
        }
        LOG.debug("Evicting MCP session {}: {}", sessionId, reason);
        closeSessionQuietly(managed.session, sessionId);
    }

    private void scheduleAsyncSessionClose(String sessionId, McpStreamableServerSession session) {
        if (vertx == null) {
            closeSessionQuietly(session, sessionId);
            return;
        }
        vertx.executeBlocking(() -> {
            closeSessionQuietly(session, sessionId);
            return null;
        }, false);
    }

    private void closeSessionQuietly(McpStreamableServerSession session, String sessionId) {
        try {
            session.closeGracefully().block(NOTIFICATION_TIMEOUT);
        } catch (Exception e) {
            LOG.debug("Failed to close MCP session {}: {}", sessionId, e.getMessage());
        }
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
        respondError(connection, ctx, 400, McpSchema.ErrorCodes.INVALID_REQUEST, String.join("; ", errors));
        return true;
    }

    /**
     * Answers with the given HTTP status and a JSON-RPC error response without an id, see
     * {@link #errorBody(int, String)}.
     */
    private void respondError(Context connection, RoutingContext ctx, int status, int code, String message) {
        String body = errorBody(code, message);
        connection.runOnContext(v -> ctx.response()
                .setStatusCode(status)
                .putHeader("Content-Type", APPLICATION_JSON)
                .end(body));
    }

    /**
     * A JSON-RPC error response without an id (the request id is not known, and {@link McpSchema.JSONRPCResponse}
     * requires one).
     */
    private String errorBody(int code, String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", McpSchema.JSONRPC_VERSION);
        response.put("error", error);
        try {
            return jsonMapper.writeValueAsString(response);
        } catch (Exception e) {
            return "{\"jsonrpc\":\"" + McpSchema.JSONRPC_VERSION + "\",\"error\":{\"code\":" + code
                   + ",\"message\":\"" + message.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}}";
        }
    }

    private void endWithStatus(Context connection, RoutingContext ctx, int status) {
        connection.runOnContext(v -> ctx.response().setStatusCode(status).end());
    }

    private record VertxHeaderAccessor(RoutingContext ctx) implements HeaderAccessor {

        @Override
        public List<String> getHeader(String name) {
            if (HOST.equalsIgnoreCase(name)) {
                // the authority of the request: the Host header on HTTP/1.1, :authority on HTTP/2
                HostAndPort authority = ctx.request().authority();
                if (authority == null) {
                    return List.of();
                }
                String host = authority.host();
                if (host.indexOf(':') >= 0 && !host.startsWith("[")) {
                    host = "[" + host + "]";
                }
                String value = authority.port() >= 0 ? host + ":" + authority.port() : host;
                return List.of(McpServerHosts.normalizeHost(value));
            }
            List<String> values = ctx.request().headers().getAll(name);
            if (ORIGIN.equalsIgnoreCase(name)) {
                // compare in the form the allowed origins are kept in: lower-case scheme and host, no default port
                return values.stream().map(McpServerOrigins::normalizeOrigin).toList();
            }
            return values;
        }

        @Override
        public List<String> getHeaderNames() {
            return List.copyOf(ctx.request().headers().names());
        }
    }

    private static final class ManagedSession {

        private final McpStreamableServerSession session;
        private final String subject;
        private volatile long lastActivityNanos;
        private final AtomicInteger consecutivePingFailures = new AtomicInteger();

        private ManagedSession(McpStreamableServerSession session, String subject) {
            this.session = session;
            this.subject = subject;
            touch();
        }

        private void touch() {
            lastActivityNanos = System.nanoTime();
            consecutivePingFailures.set(0);
        }

        private long lastActivityNanos() {
            return lastActivityNanos;
        }

        private void resetPingFailures() {
            consecutivePingFailures.set(0);
        }

        private int recordPingFailure() {
            return consecutivePingFailures.incrementAndGet();
        }
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
                            ManagedSession managed = sessions.remove(sessionId);
                            if (managed != null) {
                                scheduleAsyncSessionClose(sessionId, managed.session);
                            }
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
