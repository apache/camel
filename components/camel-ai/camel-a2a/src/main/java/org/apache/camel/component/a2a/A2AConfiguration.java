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
package org.apache.camel.component.a2a;

import org.apache.camel.component.a2a.model.AgentCard;
import org.apache.camel.component.a2a.operation.A2AOperations;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

/**
 * Configuration for the A2A component.
 */
@UriParams
public class A2AConfiguration {

    private static final int MAX_PUSH_RETRY_ATTEMPTS = 30;
    private static final long MAX_PUSH_RETRY_BACKOFF_MS = 3600000L;

    @UriParam(label = "producer", defaultValue = "MESSAGE_SEND")
    @Metadata(description = "The A2A operation to perform", defaultValue = "MESSAGE_SEND")
    private A2AOperations operation = A2AOperations.MESSAGE_SEND;

    @UriParam(enums = "HTTP+JSON,JSONRPC")
    @Metadata(description = "The protocol binding to use for communication. Legacy aliases rest and jsonrpc are also accepted.",
              defaultValue = "HTTP+JSON")
    private String protocolBinding = A2AConstants.PROTOCOL_REST;

    @UriParam
    @Metadata(description = "The host to connect to for producers")
    private String host;

    @UriParam
    @Metadata(description = "The port to connect to for producers")
    private Integer port;

    @UriParam
    @Metadata(description = "The base path for HTTP requests", defaultValue = "")
    private String basePath = "";

    @UriParam
    @Metadata(description = "The agent name (overrides agent card)")
    private String name;

    @UriParam
    @Metadata(description = "The agent description (overrides agent card)")
    private String description;

    @UriParam
    @Metadata(description = "The agent version (overrides agent card)")
    private String version;

    @UriParam
    @Metadata(description = "Agent card bean reference or inline configuration")
    private AgentCard agentCard;

    @UriParam(label = "security")
    @Metadata(description = "OAuth profile name for obtaining an access token via the OAuth 2.0 Client Credentials grant. "
                            + "When set, the token is acquired from the configured identity provider and used for authentication. "
                            + "Requires camel-oauth on the classpath. The profile properties are resolved from "
                            + "camel.oauth.profile-name.client-id, camel.oauth.profile-name.client-secret, "
                            + "and camel.oauth.profile-name.token-endpoint.")
    private String oauthProfile;

    @UriParam(label = "security", security = "secret")
    @Metadata(description = "API key for authentication", security = "secret")
    private String apiKey;

    @UriParam(label = "security", defaultValue = "Authorization")
    @Metadata(description = "HTTP header name for API key authentication (e.g., X-API-Key, Authorization)",
              defaultValue = "Authorization")
    private String apiKeyHeader = "Authorization";

    @UriParam(label = "security", security = "secret")
    @Metadata(description = "Bearer token for authentication", security = "secret")
    private String bearerToken;

    @UriParam(defaultValue = "true", security = "insecure:dev", insecureValue = "false")
    @Metadata(description = "Whether to validate authentication on incoming consumer operation requests. "
                            + "Disable explicitly only for unauthenticated A2A operation serving.",
              defaultValue = "true")
    private boolean validateAuth = true;

    @UriParam(label = "advanced", defaultValue = "3600000")
    @Metadata(description = "Time-to-live in milliseconds for completed tasks before cleanup", defaultValue = "3600000")
    private long completedTaskTtl = 3600000L;

    @UriParam(defaultValue = "false")
    @Metadata(description = "Whether to return immediately without waiting for task completion", defaultValue = "false")
    private boolean returnImmediately = false;

    @UriParam
    @Metadata(description = "Maximum number of history messages to include in the context")
    private Integer historyLength;

    @UriParam(label = "advanced", defaultValue = "300000")
    @Metadata(description = "Timeout in milliseconds for asynchronous task operations", defaultValue = "300000")
    private long asyncTimeout = 300000L;

    @UriParam(label = "advanced", defaultValue = "6291456")
    @Metadata(description = "Maximum payload size in bytes (default 6MB)", defaultValue = "6291456")
    private long maxPayloadSize = 6291456L;

    @UriParam(label = "advanced", defaultValue = "false")
    @Metadata(description = "Whether the HTTP client should follow redirects. Disabled by default to prevent "
                            + "credential leakage on cross-origin redirects. Enable only when the remote agent is "
                            + "known to issue redirects (e.g., behind a load balancer).",
              defaultValue = "false")
    private boolean followRedirects;

    @UriParam(label = "producer,advanced", defaultValue = "30000")
    @Metadata(description = "Connect timeout in milliseconds for the HTTP client used by the producer",
              defaultValue = "30000")
    private long connectTimeout = 30000L;

    @UriParam(label = "advanced", defaultValue = "3")
    @Metadata(description = "Maximum number of retry attempts for push notification webhook delivery", defaultValue = "3")
    private int pushRetryAttempts = 3;

    @UriParam(label = "advanced", defaultValue = "1000")
    @Metadata(description = "Initial backoff in milliseconds for push notification retry. "
                            + "Retries use exponential backoff with this delay multiplied by 2 to the attempt number.",
              defaultValue = "1000")
    private long pushRetryBackoffMs = 1000L;

    @UriParam(label = "consumer,advanced", defaultValue = "0")
    @Metadata(description = "Maximum number of tasks the agent can process concurrently. "
                            + "When the limit is reached, new requests are rejected with ServerBusyError (HTTP 429). "
                            + "Set to 0 (default) for unlimited concurrency.",
              defaultValue = "0")
    private int maxConcurrentTasks;

    @UriParam(label = "consumer,advanced", defaultValue = "0")
    @Metadata(description = "Maximum number of tasks that can wait in the pending queue when all concurrent slots are occupied. "
                            + "Only applies to async requests (returnImmediately=true). Queued tasks receive SUBMITTED status "
                            + "and are processed as capacity becomes available. Set to 0 (default) for no queueing — "
                            + "requests are rejected immediately when at capacity.",
              defaultValue = "0")
    private int taskQueueSize;

    @UriParam(label = "consumer,advanced")
    @Metadata(description = "The Camel HTTP component to use for serving incoming A2A requests (consumer side). "
                            + "Must implement RestConsumerFactory (e.g., platform-http, jetty, netty-http, undertow, servlet). "
                            + "When not set, the component is auto-discovered from the classpath or falls back to "
                            + "the global camel.rest.component setting.")
    private String httpServerComponent;

    @UriParam(label = "consumer,security", defaultValue = "false", security = "insecure:dev", insecureValue = "true")
    @Metadata(description = "Whether to allow webhook URLs pointing to localhost/loopback addresses. "
                            + "When false (default), push notification webhook URLs targeting 127.0.0.0/8, ::1, or "
                            + "localhost are rejected as SSRF protection. Enable for local development only.",
              defaultValue = "false")
    private boolean allowLocalWebhookUrls;

    @UriParam(label = "consumer,advanced", defaultValue = "15000")
    @Metadata(description = "Interval in milliseconds for SSE keep-alive heartbeat comments. "
                            + "Sent as ':' comment lines to prevent proxies from closing idle connections. "
                            + "Independent from asyncTimeout which controls task processing timeout.",
              defaultValue = "15000")
    private long sseHeartbeatInterval = 15000L;

    @UriParam(label = "consumer,advanced", defaultValue = "1000")
    @Metadata(description = "Maximum number of SSE events that can be buffered per streaming connection. "
                            + "When the queue is full, new events are dropped with a warning log. "
                            + "Prevents unbounded memory growth from slow clients.",
              defaultValue = "1000")
    private int sseQueueCapacity = 1000;

    @UriParam(label = "producer,advanced", defaultValue = "300000")
    @Metadata(description = "Read timeout in milliseconds for the producer's SSE streaming connection. "
                            + "If no SSE event arrives within this period, the stream is closed with an error. "
                            + "Prevents indefinite blocking when a remote agent stops sending events.",
              defaultValue = "300000")
    private long streamingReadTimeout = 300000L;

    @UriParam(defaultValue = "PAYLOAD")
    @Metadata(description = "The data format for the exchange body, following the CXF DataFormat convention. "
                            + "PAYLOAD (default) extracts text content from message parts as a String — backward compatible, "
                            + "simple for chatbot routes. "
                            + "POJO sets the body to the full Java model object (Message on consumer, Task or Message on producer) "
                            + "preserving all parts, metadata, and file content. "
                            + "RAW passes the raw JSON string without deserialization — useful for forwarding, logging, or compliance.")
    private A2ADataFormat dataFormat = A2ADataFormat.PAYLOAD;

    public A2AOperations getOperation() {
        return operation;
    }

    public void setOperation(A2AOperations operation) {
        this.operation = operation;
    }

    public String getProtocolBinding() {
        return protocolBinding;
    }

    public void setProtocolBinding(String protocolBinding) {
        this.protocolBinding = normalizeProtocolBinding(protocolBinding);
    }

    private static String normalizeProtocolBinding(String protocolBinding) {
        if (protocolBinding == null || protocolBinding.isBlank()) {
            return A2AConstants.PROTOCOL_REST;
        }
        if (A2AConstants.PROTOCOL_REST_ALIAS.equalsIgnoreCase(protocolBinding)
                || A2AConstants.PROTOCOL_REST.equalsIgnoreCase(protocolBinding)) {
            return A2AConstants.PROTOCOL_REST;
        }
        if (A2AConstants.PROTOCOL_JSONRPC_ALIAS.equalsIgnoreCase(protocolBinding)
                || A2AConstants.PROTOCOL_JSONRPC.equalsIgnoreCase(protocolBinding)) {
            return A2AConstants.PROTOCOL_JSONRPC;
        }
        return protocolBinding;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getBasePath() {
        return basePath;
    }

    public void setBasePath(String basePath) {
        this.basePath = normalizeBasePath(basePath);
    }

    private static String normalizeBasePath(String basePath) {
        if (basePath == null || basePath.isBlank() || "/".equals(basePath.trim())) {
            return "";
        }
        String normalized = basePath.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        while (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public AgentCard getAgentCard() {
        return agentCard;
    }

    public void setAgentCard(AgentCard agentCard) {
        this.agentCard = agentCard;
    }

    public String getOauthProfile() {
        return oauthProfile;
    }

    public void setOauthProfile(String oauthProfile) {
        this.oauthProfile = oauthProfile;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getApiKeyHeader() {
        return apiKeyHeader;
    }

    public void setApiKeyHeader(String apiKeyHeader) {
        this.apiKeyHeader = apiKeyHeader;
    }

    public String getBearerToken() {
        return bearerToken;
    }

    public void setBearerToken(String bearerToken) {
        this.bearerToken = bearerToken;
    }

    public boolean isValidateAuth() {
        return validateAuth;
    }

    public void setValidateAuth(boolean validateAuth) {
        this.validateAuth = validateAuth;
    }

    public long getCompletedTaskTtl() {
        return completedTaskTtl;
    }

    public void setCompletedTaskTtl(long completedTaskTtl) {
        this.completedTaskTtl = completedTaskTtl;
    }

    public boolean isReturnImmediately() {
        return returnImmediately;
    }

    public void setReturnImmediately(boolean returnImmediately) {
        this.returnImmediately = returnImmediately;
    }

    public Integer getHistoryLength() {
        return historyLength;
    }

    public void setHistoryLength(Integer historyLength) {
        this.historyLength = historyLength;
    }

    public long getAsyncTimeout() {
        return asyncTimeout;
    }

    public void setAsyncTimeout(long asyncTimeout) {
        this.asyncTimeout = asyncTimeout;
    }

    public long getMaxPayloadSize() {
        return maxPayloadSize;
    }

    public void setMaxPayloadSize(long maxPayloadSize) {
        this.maxPayloadSize = maxPayloadSize;
    }

    public boolean isFollowRedirects() {
        return followRedirects;
    }

    public void setFollowRedirects(boolean followRedirects) {
        this.followRedirects = followRedirects;
    }

    public long getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(long connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public int getPushRetryAttempts() {
        return pushRetryAttempts;
    }

    public void setPushRetryAttempts(int pushRetryAttempts) {
        this.pushRetryAttempts = pushRetryAttempts;
    }

    public long getPushRetryBackoffMs() {
        return pushRetryBackoffMs;
    }

    public void setPushRetryBackoffMs(long pushRetryBackoffMs) {
        this.pushRetryBackoffMs = pushRetryBackoffMs;
    }

    public int getMaxConcurrentTasks() {
        return maxConcurrentTasks;
    }

    public void setMaxConcurrentTasks(int maxConcurrentTasks) {
        this.maxConcurrentTasks = maxConcurrentTasks;
    }

    public int getTaskQueueSize() {
        return taskQueueSize;
    }

    public void setTaskQueueSize(int taskQueueSize) {
        this.taskQueueSize = taskQueueSize;
    }

    public String getHttpServerComponent() {
        return httpServerComponent;
    }

    public void setHttpServerComponent(String httpServerComponent) {
        this.httpServerComponent = httpServerComponent;
    }

    public boolean isAllowLocalWebhookUrls() {
        return allowLocalWebhookUrls;
    }

    public void setAllowLocalWebhookUrls(boolean allowLocalWebhookUrls) {
        this.allowLocalWebhookUrls = allowLocalWebhookUrls;
    }

    public long getSseHeartbeatInterval() {
        return sseHeartbeatInterval;
    }

    public void setSseHeartbeatInterval(long sseHeartbeatInterval) {
        this.sseHeartbeatInterval = sseHeartbeatInterval;
    }

    public int getSseQueueCapacity() {
        return sseQueueCapacity;
    }

    public void setSseQueueCapacity(int sseQueueCapacity) {
        this.sseQueueCapacity = sseQueueCapacity;
    }

    public long getStreamingReadTimeout() {
        return streamingReadTimeout;
    }

    public void setStreamingReadTimeout(long streamingReadTimeout) {
        this.streamingReadTimeout = streamingReadTimeout;
    }

    public A2ADataFormat getDataFormat() {
        return dataFormat;
    }

    public void setDataFormat(A2ADataFormat dataFormat) {
        this.dataFormat = dataFormat;
    }

    void validate() {
        if (port != null && (port < 1 || port > 65535)) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }
        if (historyLength != null && historyLength < 0) {
            throw new IllegalArgumentException("historyLength must be greater than or equal to zero");
        }
        requireNonNegative("completedTaskTtl", completedTaskTtl);
        requireNonNegative("asyncTimeout", asyncTimeout);
        requirePositive("maxPayloadSize", maxPayloadSize);
        requirePositive("connectTimeout", connectTimeout);
        requireNonNegative("maxConcurrentTasks", maxConcurrentTasks);
        requireNonNegative("taskQueueSize", taskQueueSize);
        requirePositive("sseHeartbeatInterval", sseHeartbeatInterval);
        requirePositive("sseQueueCapacity", sseQueueCapacity);
        requireNonNegative("streamingReadTimeout", streamingReadTimeout);
        if (pushRetryAttempts < 0 || pushRetryAttempts > MAX_PUSH_RETRY_ATTEMPTS) {
            throw new IllegalArgumentException(
                    "pushRetryAttempts must be between 0 and " + MAX_PUSH_RETRY_ATTEMPTS);
        }
        requireNonNegative("pushRetryBackoffMs", pushRetryBackoffMs);
        if (pushRetryAttempts > 0 && pushRetryBackoffMs == 0) {
            throw new IllegalArgumentException("pushRetryBackoffMs must be greater than zero when retries are enabled");
        }
        if (pushRetryBackoffMs > MAX_PUSH_RETRY_BACKOFF_MS) {
            throw new IllegalArgumentException(
                    "pushRetryBackoffMs must be less than or equal to " + MAX_PUSH_RETRY_BACKOFF_MS);
        }
    }

    private static void requirePositive(String name, long value) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be greater than zero");
        }
    }

    private static void requireNonNegative(String name, long value) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be greater than or equal to zero");
        }
    }
}
