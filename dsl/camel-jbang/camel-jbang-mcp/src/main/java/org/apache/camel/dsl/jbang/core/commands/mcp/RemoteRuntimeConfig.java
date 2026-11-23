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
package org.apache.camel.dsl.jbang.core.commands.mcp;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Configuration for the opt-in remote integration endpoint. */
@ApplicationScoped
public class RemoteRuntimeConfig {

    @ConfigProperty(name = "camel.mcp.remote.enabled", defaultValue = "false")
    boolean enabled;

    @ConfigProperty(name = "camel.mcp.remote.token", defaultValue = "")
    String token;

    @ConfigProperty(name = "camel.mcp.remote.max-connections", defaultValue = "16")
    int maxConnections;

    @ConfigProperty(name = "camel.mcp.remote.max-retained-runtimes", defaultValue = "32")
    int maxRetainedRuntimes;

    @ConfigProperty(name = "camel.mcp.remote.retention-ms", defaultValue = "3600000")
    long retentionMillis;

    @ConfigProperty(name = "camel.mcp.remote.heartbeat-timeout-ms", defaultValue = "90000")
    long heartbeatTimeoutMillis;

    @ConfigProperty(name = "camel.mcp.remote.max-frame-chars", defaultValue = "16777216")
    int maxFrameChars;

    @ConfigProperty(name = "camel.mcp.remote.max-snapshot-chars", defaultValue = "1048576")
    int maxSnapshotChars;

    @ConfigProperty(name = "camel.mcp.remote.max-retained-messages", defaultValue = "500")
    int maxRetainedMessages;

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isTokenConfigured() {
        return token != null && !token.isBlank();
    }

    /** Constant-time comparison of the configured shared bearer token. */
    public boolean isAuthorized(String authorization) {
        if (!isTokenConfigured() || authorization == null) {
            return false;
        }
        byte[] expected = ("Bearer " + token).getBytes(StandardCharsets.UTF_8);
        byte[] supplied = authorization.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, supplied);
    }

    /** A non-secret namespace for the one configured authentication principal. */
    public String ownerId() {
        if (!isTokenConfigured()) {
            return "unconfigured";
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return "token:" + java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    int maxConnections() {
        return maxConnections > 0 ? maxConnections : 16;
    }

    int maxRetainedRuntimes() {
        return maxRetainedRuntimes > 0 ? maxRetainedRuntimes : 32;
    }

    long retentionMillis() {
        return retentionMillis > 0 ? retentionMillis : 3_600_000L;
    }

    long heartbeatTimeoutMillis() {
        return heartbeatTimeoutMillis > 0 ? heartbeatTimeoutMillis : 90_000L;
    }

    int maxFrameChars() {
        return maxFrameChars > 0 ? maxFrameChars : 16 * 1024 * 1024;
    }

    int maxSnapshotChars() {
        return maxSnapshotChars > 0 ? maxSnapshotChars : 1024 * 1024;
    }

    int maxRetainedMessages() {
        return maxRetainedMessages > 0 ? maxRetainedMessages : 500;
    }
}
