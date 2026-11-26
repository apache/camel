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

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpStatelessServerHandler;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpStatelessServerTransport;
import reactor.core.publisher.Mono;

/**
 * Serves clients of the protocol versions that open with an {@code initialize} handshake without a session, as the MCP
 * streamable HTTP transport allows: each POST is handed to the MCP SDK stateless server on its own, and the answer is a
 * single JSON object. No {@code Mcp-Session-Id} is ever returned.
 * <p>
 * This is the Vert.x counterpart of the MCP SDK's {@code HttpServletStatelessServerTransport}. The HTTP concerns
 * (routes, origin, host and OAuth checks, response writing) stay in {@link VertxMcpStreamableServerTransportProvider},
 * which calls {@link #handle(McpTransportContext, McpSchema.JSONRPCMessage)} on a worker thread.
 */
final class VertxMcpStatelessServerTransport implements McpStatelessServerTransport {

    private final McpJsonMapper jsonMapper;
    private volatile McpStatelessServerHandler mcpHandler;

    VertxMcpStatelessServerTransport(McpJsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void setMcpHandler(McpStatelessServerHandler mcpHandler) {
        this.mcpHandler = mcpHandler;
    }

    @Override
    public Mono<Void> closeGracefully() {
        return Mono.empty();
    }

    /**
     * The answer to one message.
     *
     * @param status the HTTP status
     * @param body   the JSON body, or {@code null} for none
     */
    record Answer(int status, String body) {
    }

    /**
     * Handles one message on the calling (worker) thread: a request is answered with HTTP 200 and its JSON-RPC
     * response, a notification with HTTP 202.
     *
     * @return the answer, or {@code null} when the message is neither a request nor a notification
     */
    Answer handle(McpTransportContext transportContext, McpSchema.JSONRPCMessage message) throws Exception {
        McpStatelessServerHandler handler = mcpHandler;
        if (handler == null) {
            return new Answer(503, null);
        }
        if (message instanceof McpSchema.JSONRPCRequest request) {
            McpSchema.JSONRPCResponse response = handler.handleRequest(transportContext, request)
                    .contextWrite(ctx -> ctx.put(McpTransportContext.KEY, transportContext))
                    .block();
            return new Answer(200, jsonMapper.writeValueAsString(response));
        }
        if (message instanceof McpSchema.JSONRPCNotification notification) {
            handler.handleNotification(transportContext, notification)
                    .contextWrite(ctx -> ctx.put(McpTransportContext.KEY, transportContext))
                    .block();
            return new Answer(202, null);
        }
        return null;
    }
}
