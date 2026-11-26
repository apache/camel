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

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.modern.JsonRpc;
import io.modelcontextprotocol.modern.JsonRpc.JSONRPCMessage;
import io.modelcontextprotocol.modern.JsonRpc.JSONRPCNotification;
import io.modelcontextprotocol.modern.JsonRpc.JSONRPCRequest;
import io.modelcontextprotocol.modern.JsonRpc.JSONRPCResponse;
import io.modelcontextprotocol.modern.JsonRpc.JSONRPCResponse.JSONRPCError;
import io.modelcontextprotocol.modern.McpHttp;
import io.modelcontextprotocol.modern.McpSchema;
import io.modelcontextprotocol.modern.McpSchema.ErrorCodes;
import io.modelcontextprotocol.modern.server.McpHeaders;
import io.modelcontextprotocol.modern.server.McpRequestContext;
import io.modelcontextprotocol.modern.server.McpRequestManager;
import io.modelcontextprotocol.modern.server.McpServer;
import io.modelcontextprotocol.modern.server.McpTransportResponse;
import io.modelcontextprotocol.modern.server.RequestStateCodec;
import io.modelcontextprotocol.modern.server.feature.McpChangeFeed;
import io.modelcontextprotocol.modern.server.feature.PromptsFeature;
import io.modelcontextprotocol.modern.server.feature.ResourcesFeature;
import io.modelcontextprotocol.modern.server.feature.ToolsFeature;
import io.modelcontextprotocol.modern.server.transport.McpHeaderDecoder;
import io.modelcontextprotocol.server.transport.HeaderAccessor;
import io.vertx.core.Context;
import io.vertx.core.http.HttpServerResponse;
import org.apache.camel.component.mcp.server.McpServerIcon;
import org.apache.camel.component.mcp.server.McpServerInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Serves requests of MCP protocol version 2026-07-28 and later: stateless, one self-contained request per POST,
 * answered with a single JSON object or a Server-Sent Events stream scoped to that request.
 * <p>
 * This is the Vert.x counterpart of the POST handling of the MCP SDK's {@code HttpServletMcpTransport}; the HTTP
 * concerns shared with the other protocol versions (routes, origin, host and OAuth checks) stay in
 * {@link VertxMcpStreamableServerTransportProvider}, which calls {@link #handle} on a worker thread. A request's
 * {@code Mcp-Method}, {@code MCP-Protocol-Version} and {@code Mcp-Name} headers are checked against its body before it
 * reaches the {@link McpRequestManager}; a mismatch is answered with HTTP 400 and error {@code -32020}. A
 * {@code subscriptions/listen} stream does not hold a worker thread, and a stream that the client closes is cancelled.
 */
final class VertxMcpModernTransport {

    private static final Logger LOG = LoggerFactory.getLogger(VertxMcpModernTransport.class);

    static final Duration DEFAULT_KEEP_ALIVE_INTERVAL = Duration.ofSeconds(30);

    private static final String APPLICATION_JSON = "application/json";
    private static final String TEXT_EVENT_STREAM = "text/event-stream";
    private static final String KEEP_ALIVE_FRAME = ":\n\n";
    // the handshake of the protocol versions before 2026-07-28 (the SDK constant is deprecated)
    private static final String METHOD_INITIALIZE = "initialize";

    /**
     * The HTTP status of each JSON-RPC error code, as the MCP SDK's servlet transport answers them; any other code is
     * answered with HTTP 200.
     */
    private static final Map<Integer, Integer> ERROR_STATUSES = Map.of(
            ErrorCodes.PARSE_ERROR, 400,
            ErrorCodes.INVALID_REQUEST, 400,
            ErrorCodes.INVALID_PARAMS, 400,
            ErrorCodes.HEADER_MISMATCH, 400,
            ErrorCodes.MISSING_REQUIRED_CLIENT_CAPABILITY, 400,
            ErrorCodes.UNSUPPORTED_PROTOCOL_VERSION, 400,
            ErrorCodes.METHOD_NOT_FOUND, 404);

    /**
     * Camel tools, resources and prompts never answer with an {@code InputRequiredResult}, so no request state is ever
     * sealed, and a request state sent by a client cannot have come from this server. This also spares the
     * multi-instance warning of the SDK's default codec, whose per-process key Camel would never use.
     */
    private static final RequestStateCodec NO_REQUEST_STATE = new RequestStateCodec() {
        @Override
        public String seal(McpRequestContext ctx, String state) {
            throw new IllegalStateException("Camel MCP server never asks the client for input");
        }

        @Override
        public String open(McpRequestContext ctx, String sealed) {
            return null;
        }
    };

    private final McpRequestManager requestManager;
    private final McpJsonMapper jsonMapper;
    private final Duration keepAliveInterval;

    VertxMcpModernTransport(McpRequestManager requestManager, McpJsonMapper jsonMapper, Duration keepAliveInterval) {
        this.requestManager = requestManager;
        this.jsonMapper = jsonMapper;
        this.keepAliveInterval = keepAliveInterval;
    }

    /**
     * Builds the transport over an MCP SDK modern server that serves the tools, resources and prompts of the catalog
     * and tells listening clients when a list changes.
     */
    static VertxMcpModernTransport create(
            McpServerInfo info, McpModernCatalog catalog, McpChangeFeed changes, McpJsonMapper jsonMapper,
            Duration keepAliveInterval) {
        McpServer.Builder builder = McpServer.builder()
                .serverInfo(buildServerInfo(info))
                .jsonMapper(jsonMapper)
                .feature(ToolsFeature.ofSync(catalog.toolRepository()))
                .feature(ResourcesFeature.ofSync(catalog.resourceRepository()))
                .feature(PromptsFeature.ofSync(catalog.promptRepository()))
                .subscriptions(changes)
                .requestStateCodec(NO_REQUEST_STATE);
        if (info.instructions() != null && !info.instructions().isBlank()) {
            builder.instructions(info.instructions());
        }
        return new VertxMcpModernTransport(builder.build(), jsonMapper, keepAliveInterval);
    }

    private static McpSchema.Implementation buildServerInfo(McpServerInfo info) {
        McpSchema.Implementation.Builder builder = McpSchema.Implementation.builder(info.serverName(), info.version());
        if (info.title() != null && !info.title().isBlank()) {
            builder.title(info.title());
        }
        if (info.description() != null && !info.description().isBlank()) {
            builder.description(info.description());
        }
        if (info.websiteUrl() != null && !info.websiteUrl().isBlank()) {
            builder.websiteUrl(info.websiteUrl());
        }
        if (info.icons() != null && !info.icons().isEmpty()) {
            builder.icons(info.icons().stream().map(VertxMcpModernTransport::buildIcon).toList());
        }
        return builder.build();
    }

    private static McpSchema.Icon buildIcon(McpServerIcon icon) {
        McpSchema.Icon.Builder builder = McpSchema.Icon.builder(icon.src());
        if (icon.mimeType() != null && !icon.mimeType().isBlank()) {
            builder.mimeType(icon.mimeType());
        }
        if (icon.sizes() != null && !icon.sizes().isEmpty()) {
            builder.sizes(icon.sizes());
        }
        if (icon.theme() != null && !icon.theme().isBlank()) {
            builder.theme(icon.theme());
        }
        return builder.build();
    }

    /**
     * Ends the {@code subscriptions/listen} streams.
     */
    void closeGracefully() {
        if (requestManager instanceof McpServer server) {
            server.closeGracefully();
        }
    }

    /**
     * Handles one POST on the calling (worker) thread; every write goes through the connection's event-loop context.
     *
     * @param body             the request body
     * @param headers          the request headers
     * @param transportContext the caller identity of the request
     * @param response         the HTTP response
     * @param connection       the event-loop context of the connection
     */
    void handle(
            String body, HeaderAccessor headers, McpTransportContext transportContext,
            HttpServerResponse response, Context connection) {
        JSONRPCMessage message;
        try {
            message = JsonRpc.deserializeMessage(jsonMapper, body);
        } catch (IOException e) {
            writeError(response, connection,
                    JSONRPCResponse.error(null, new JSONRPCError(ErrorCodes.PARSE_ERROR, "Parse error")));
            return;
        } catch (JsonRpc.InvalidMessageException e) {
            writeError(response, connection, JSONRPCResponse.error(e.id(),
                    new JSONRPCError(ErrorCodes.INVALID_REQUEST, "Invalid JSON-RPC message")));
            return;
        }

        if (message instanceof JSONRPCNotification notification) {
            requestManager.handleNotification(transportContext, notification).block();
            connection.runOnContext(v -> response.setStatusCode(202).end());
            return;
        }
        if (!(message instanceof JSONRPCRequest request)) {
            writeError(response, connection, JSONRPCResponse.error(null, new JSONRPCError(
                    ErrorCodes.INVALID_REQUEST, "The server accepts either requests or notifications")));
            return;
        }

        String headerMismatch = validateHeaders(headers, request);
        if (headerMismatch != null) {
            writeError(response, connection, JSONRPCResponse.error(request.id(),
                    new JSONRPCError(ErrorCodes.HEADER_MISMATCH, headerMismatch)));
            return;
        }

        McpTransportContext context = transportContext.putAll(
                Map.of(McpHeaders.TRANSPORT_CONTEXT_KEY, McpHeaderDecoder.headers(headers)));
        boolean listen = McpSchema.METHOD_SUBSCRIPTIONS_LISTEN.equals(request.method());
        // a listen stream is served without blocking; any other request runs its sync code on this worker thread
        McpTransportResponse answer = (listen
                ? requestManager.handle(context, request)
                : requestManager.handleBlocking(context, request)).block();

        if (answer instanceof McpTransportResponse.Result result) {
            writeJson(response, connection, 200, result.response());
        } else if (answer instanceof McpTransportResponse.Error error) {
            writeError(response, connection, error.response());
        } else if (answer instanceof McpTransportResponse.Streaming streaming) {
            stream(response, connection, streaming.messages(), listen);
        } else {
            // the request manager always answers; never leave the client waiting if it does not
            LOG.warn("MCP request {} completed without an answer", request.method());
            connection.runOnContext(v -> response.setStatusCode(500).end());
        }
    }

    /**
     * Writes the messages as SSE events. A listen stream is written from a worker of its own, so the threads that
     * publish changes only hand them off; any other stream is written from the calling thread, where sync handler code
     * runs. Closing the connection cancels the stream.
     */
    private void stream(HttpServerResponse response, Context connection, Flux<JSONRPCMessage> messages, boolean listen) {
        connection.runOnContext(v -> response
                .setStatusCode(200)
                .setChunked(true)
                .putHeader("Content-Type", TEXT_EVENT_STREAM)
                .putHeader("Cache-Control", "no-cache, no-transform")
                .putHeader("X-Accel-Buffering", "no"));
        Flux<String> frames = frames(listen ? messages.publishOn(Schedulers.boundedElastic()) : messages);
        Disposable.Swap subscription = Disposables.swap();
        connection.runOnContext(v -> response.closeHandler(closed -> subscription.dispose()));
        subscription.update(frames
                .concatMap(frame -> write(response, connection, frame))
                .doOnError(error -> LOG.debug("MCP response stream ended early: {}", error.getMessage()))
                .onErrorComplete()
                .subscribe(null, null, () -> connection.runOnContext(v -> {
                    if (!response.ended() && !response.closed()) {
                        response.end();
                    }
                })));
    }

    private Flux<String> frames(Flux<JSONRPCMessage> messages) {
        Flux<String> events = messages.map(this::eventFrame);
        if (keepAliveInterval == null || keepAliveInterval.isZero()) {
            return events;
        }
        // keep-alives stop with the stream; a keep-alive that fails to write is how a quiet stream notices the client
        // is gone. A tick nobody has requested is dropped rather than failing the stream.
        return events.publish(shared -> shared.mergeWith(Flux.interval(keepAliveInterval)
                .onBackpressureDrop()
                .map(tick -> KEEP_ALIVE_FRAME)
                .takeUntilOther(shared.then())));
    }

    private String eventFrame(JSONRPCMessage message) {
        try {
            return "event: " + McpHttp.MESSAGE_EVENT + "\ndata: " + jsonMapper.writeValueAsString(message) + "\n\n";
        } catch (IOException e) {
            throw new IllegalStateException("Unable to serialize MCP message", e);
        }
    }

    private static Mono<Void> write(HttpServerResponse response, Context connection, String frame) {
        return Mono.create(sink -> connection.runOnContext(v -> {
            if (response.ended() || response.closed()) {
                sink.error(new IOException("Client disconnected"));
                return;
            }
            response.write(frame).onComplete(result -> {
                if (result.succeeded()) {
                    sink.success();
                } else {
                    sink.error(result.cause());
                }
            });
        }));
    }

    private void writeError(HttpServerResponse response, Context connection, JSONRPCResponse error) {
        writeJson(response, connection, ERROR_STATUSES.getOrDefault(error.error().code(), 200), error);
    }

    private void writeJson(HttpServerResponse response, Context connection, int status, JSONRPCResponse answer) {
        String json;
        try {
            json = jsonMapper.writeValueAsString(answer);
        } catch (IOException e) {
            LOG.warn("Unable to serialize MCP response", e);
            connection.runOnContext(v -> response.setStatusCode(500).end());
            return;
        }
        connection.runOnContext(v -> response
                .setStatusCode(status)
                .putHeader("Content-Type", APPLICATION_JSON)
                .end(json));
    }

    /**
     * Checks {@code Mcp-Method}, {@code MCP-Protocol-Version} and, for the three named methods, {@code Mcp-Name}
     * against the request body, as the MCP SDK's servlet transport does. Each header must appear once, so a request
     * cannot be routed or authorized on one copy of a header while another is validated.
     *
     * @return a description of the mismatch, or {@code null} when the headers agree with the body
     */
    static String validateHeaders(HeaderAccessor headers, JSONRPCRequest request) {
        String metaVersion = protocolVersionFromMeta(request.params());
        if (METHOD_INITIALIZE.equals(request.method()) && metaVersion == null) {
            // a client of a protocol version that opens with initialize sends none of these headers: the request
            // manager answers it with the versions it supports, which that client can show to its user
            return null;
        }
        HeaderValue method = HeaderValue.of(headers, McpHttp.Headers.MCP_METHOD);
        if (method.missing()) {
            return "Missing required header: Mcp-Method";
        }
        if (method.repeated()) {
            return "Mcp-Method header has more than one value";
        }
        if (!method.value().equals(request.method())) {
            return "Mcp-Method header does not match request method";
        }
        if (metaVersion != null) {
            // a body without a readable version is answered by the request manager with -32602
            HeaderValue version = HeaderValue.of(headers, McpHttp.Headers.MCP_PROTOCOL_VERSION);
            if (version.missing()) {
                return "Missing required header: MCP-Protocol-Version";
            }
            if (version.repeated()) {
                return "MCP-Protocol-Version header has more than one value";
            }
            if (!version.value().equals(metaVersion)) {
                return "MCP-Protocol-Version header does not match _meta protocol version";
            }
        }
        String nameKey = switch (request.method()) {
            case McpSchema.METHOD_TOOLS_CALL, McpSchema.METHOD_PROMPTS_GET -> "name";
            case McpSchema.METHOD_RESOURCES_READ -> "uri";
            default -> null;
        };
        if (nameKey == null || !(request.params() instanceof Map<?, ?> params)
                || !(params.get(nameKey) instanceof String expected)) {
            return null;
        }
        HeaderValue name = HeaderValue.of(headers, McpHttp.Headers.MCP_NAME);
        if (name.missing()) {
            return "Missing required header: Mcp-Name";
        }
        if (name.repeated()) {
            return "Mcp-Name header has more than one value";
        }
        String decoded = McpHeaderDecoder.decodeValue(name.value());
        if (decoded == null) {
            return "Mcp-Name header has a malformed value";
        }
        if (!expected.equals(decoded)) {
            return "Mcp-Name header does not match request name/uri";
        }
        return null;
    }

    /**
     * The values a header appears with: missing, once, or repeated.
     */
    private record HeaderValue(List<String> values) {

        static HeaderValue of(HeaderAccessor headers, String name) {
            List<String> values = headers.getHeader(name);
            return new HeaderValue(values != null ? values : List.of());
        }

        boolean missing() {
            return values.isEmpty();
        }

        boolean repeated() {
            return values.size() > 1;
        }

        String value() {
            return values.get(0);
        }
    }

    private static String protocolVersionFromMeta(Object params) {
        if (params instanceof Map<?, ?> paramsMap && paramsMap.get("_meta") instanceof Map<?, ?> meta
                && meta.get(McpSchema.MetaKeys.PROTOCOL_VERSION) instanceof String version && !version.isBlank()) {
            return version;
        }
        return null;
    }
}
