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
    @Metadata(description = "The operation that the producer performs. The CamelA2AOperation header replaces it for a"
                            + " message.",
              defaultValue = "MESSAGE_SEND")
    private A2AOperations operation = A2AOperations.MESSAGE_SEND;

    @UriParam(enums = "HTTP+JSON,JSONRPC")
    @Metadata(description = "The A2A protocol binding. HTTP+JSON is the REST binding, with a path per operation such as"
                            + " /message:send, and JSONRPC posts JSON-RPC 2.0 requests to the base path. The consumer"
                            + " serves only this binding, and the producer calls the URL that the agent card gives for"
                            + " this binding. The aliases rest and jsonrpc are also accepted, in any case.",
              defaultValue = "HTTP+JSON")
    private String protocolBinding = A2AConstants.PROTOCOL_REST;

    @UriParam
    @Metadata(description = "The host of the remote agent that the producer calls, in place of the URL of the agent card"
                            + " and of agentCardSource, for example for tests. Without an http:// or https:// prefix,"
                            + " https is used. The port and basePath options are added to it. The consumer does not use"
                            + " it.")
    private String host;

    @UriParam
    @Metadata(description = "The port of the remote agent that the producer calls. Only used with the host option. The"
                            + " consumer does not use it.")
    private Integer port;

    @UriParam
    @Metadata(description = "The path prefix of the HTTP routes of the consumer, also of the path of its agent card, for"
                            + " example /agents/weather. The producer adds it to the URL of the remote agent only when"
                            + " the host option is set. A missing leading slash is added and a trailing slash removed.",
              defaultValue = "")
    private String basePath = "";

    @UriParam
    @Metadata(description = "The name of the agent, which replaces the name of the agent card from agentCardSource and"
                            + " agentCard. If no card gives a name, a plain name in agentCardSource is used, and the"
                            + " endpoint fails to start without a name.")
    private String name;

    @UriParam
    @Metadata(description = "The description of the agent, which replaces the description of the agent card from"
                            + " agentCardSource and agentCard. If the card declares no skills, it is also the description"
                            + " of the default skill of the card.")
    private String description;

    @UriParam
    @Metadata(description = "The version of the agent, which replaces the version of the agent card from agentCardSource"
                            + " and agentCard. If no card gives a version, 1.0 is used.")
    private String version;

    @UriParam
    @Metadata(description = "An agent card, usually a reference to an org.apache.camel.component.a2a.model.AgentCard bean"
                            + " in the registry. The fields that it sets replace those of the card from agentCardSource,"
                            + " and the name, description and version options replace its fields in turn.")
    private AgentCard agentCard;

    @UriParam(label = "security")
    @Metadata(description = "The name of a camel-oauth profile, for the security schemes of type oauth2, openIdConnect"
                            + " and http in the agent card. The producer gets an access token with the OAuth 2.0 client"
                            + " credentials grant, from the client-id, client-secret and token-endpoint properties of"
                            + " the profile under camel.oauth, and sends it as a bearer token. The consumer validates the"
                            + " bearer token of each request with this profile when validateAuth is true. Requires"
                            + " camel-oauth, or for the consumer another OAuthTokenValidationFactory.")
    private String oauthProfile;

    @UriParam(label = "security", security = "secret")
    @Metadata(description = "The API key, for the security schemes of type apiKey in the agent card. The producer sends"
                            + " it in the header, query parameter or cookie that the scheme names, with the Bearer"
                            + " prefix in the Authorization header. The consumer compares the key of each request with"
                            + " it when validateAuth is true.",
              security = "secret")
    private String apiKey;

    @UriParam(label = "security", defaultValue = "Authorization")
    @Metadata(description = "The name of the header, query parameter or cookie that holds the API key, for a security"
                            + " scheme of type apiKey in the agent card that gives no name.",
              defaultValue = "Authorization")
    private String apiKeyHeader = "Authorization";

    @UriParam(label = "security", security = "secret")
    @Metadata(description = "The bearer token, for the security schemes of type http in the agent card. The producer"
                            + " sends it in the Authorization header when oauthProfile is not set or gives no token. The"
                            + " consumer compares the bearer token of each request with it, also for the oauth2 and"
                            + " openIdConnect schemes, when validateAuth is true and oauthProfile is not set.",
              security = "secret")
    private String bearerToken;

    @UriParam(defaultValue = "true", security = "insecure:dev", insecureValue = "false")
    @Metadata(description = "Whether the consumer checks the credentials of each request, except the request for the"
                            + " agent card, against the security requirements of its agent card, with the apiKey,"
                            + " bearerToken or oauthProfile option. If true, the card must declare securitySchemes, or"
                            + " every request is rejected. If false, the consumer accepts requests without credentials."
                            + " The producer does not use it.",
              defaultValue = "true")
    private boolean validateAuth = true;

    @UriParam(label = "advanced", defaultValue = "3600000")
    @Metadata(description = "The time in milliseconds that the in-memory task store of the consumer keeps a task after"
                            + " it reached a final state: completed, failed, canceled or rejected. Not used when the"
                            + " registry holds an A2ATaskStore, which the endpoint then uses as its task store.",
              defaultValue = "3600000")
    private long completedTaskTtl = 3600000L;

    @UriParam(defaultValue = "false")
    @Metadata(description = "Whether the consumer answers SendMessage at once with a task in state"
                            + " TASK_STATE_SUBMITTED and runs the route in the background, so that the client polls the"
                            + " task with GetTask. The returnImmediately and blocking fields of the configuration of the"
                            + " request take precedence. With protocolBinding HTTP+JSON, a SendStreamingMessage request"
                            + " for which it is true is rejected. The producer does not use it.",
              defaultValue = "false")
    private boolean returnImmediately = false;

    @UriParam
    @Metadata(description = "Not used: the endpoint only checks when it starts that the value is not negative. The"
                            + " producer takes the history length of TASK_GET and TASK_SUBSCRIBE from the"
                            + " CamelA2AHistoryLength header, and that of TASK_LIST from the CamelA2AListHistoryLength"
                            + " header.")
    private Integer historyLength;

    @UriParam(label = "advanced", defaultValue = "300000")
    @Metadata(description = "The timeout in milliseconds of the route that the consumer runs in the background, for"
                            + " SendMessage with returnImmediately and for SendStreamingMessage: the consumer then cancels"
                            + " it and marks the task as failed. Use 0 for no timeout on the consumer. The producer uses it"
                            + " as the timeout of the response to its MESSAGE_STREAM and TASK_SUBSCRIBE requests, where it"
                            + " must be greater than 0, and a fixed timeout of 60 seconds for the other operations.",
              defaultValue = "300000")
    private long asyncTimeout = 300000L;

    @UriParam(label = "advanced", defaultValue = "6291456")
    @Metadata(description = "The maximum size in bytes of a request body that the consumer reads, and of a response body"
                            + " or a single server-sent event that the producer reads. The consumer rejects a larger"
                            + " request, and the producer fails the exchange. The agent card has its own limit of 1 MB.",
              defaultValue = "6291456")
    private long maxPayloadSize = 6291456L;

    @UriParam(label = "advanced", defaultValue = "false")
    @Metadata(description = "Whether the producer follows HTTP redirects of the remote agent. Disabled by default, as a"
                            + " redirect to another host would also receive the credentials of the producer. The download"
                            + " of the agent card and the push notifications of the consumer never follow redirects.",
              defaultValue = "false")
    private boolean followRedirects;

    @UriParam(label = "producer,advanced", defaultValue = "30000")
    @Metadata(description = "The connect timeout in milliseconds of the HTTP connections of the endpoint: the requests of"
                            + " the producer, the push notifications that the consumer sends, and the download of the"
                            + " agent card from an HTTP URL when the endpoint starts. It is also the timeout of the whole"
                            + " download of the agent card.",
              defaultValue = "30000")
    private long connectTimeout = 30000L;

    @UriParam(label = "advanced", defaultValue = "3")
    @Metadata(description = "The number of times that the consumer retries a push notification after a network error or"
                            + " a webhook response with a status other than 2xx and 4xx. A 4xx status is not retried,"
                            + " and 410 Gone also removes the push notification config. Between 0 and 30, where 0 sends"
                            + " each notification once.",
              defaultValue = "3")
    private int pushRetryAttempts = 3;

    @UriParam(label = "advanced", defaultValue = "1000")
    @Metadata(description = "The delay in milliseconds before the first retry of a push notification, doubled for each"
                            + " further retry and capped at one hour. At most 3600000, and greater than 0 when"
                            + " pushRetryAttempts is greater than 0.",
              defaultValue = "1000")
    private long pushRetryBackoffMs = 1000L;

    @UriParam(label = "consumer,advanced", defaultValue = "0")
    @Metadata(description = "The maximum number of SendMessage and SendStreamingMessage requests that the consumer"
                            + " processes at the same time. A request beyond the limit is rejected with a ServerBusyError,"
                            + " which is HTTP status 429 with protocolBinding HTTP+JSON, unless it can wait in the queue"
                            + " of taskQueueSize. Use 0 for no limit.",
              defaultValue = "0")
    private int maxConcurrentTasks;

    @UriParam(label = "consumer,advanced", defaultValue = "0")
    @Metadata(description = "The number of SendMessage requests with returnImmediately that can wait in a queue, as tasks"
                            + " in state TASK_STATE_SUBMITTED, while maxConcurrentTasks requests are in progress. Only"
                            + " used when maxConcurrentTasks is greater than 0. Use 0 for no queue, so that these"
                            + " requests are rejected at once.",
              defaultValue = "0")
    private int taskQueueSize;

    @UriParam(label = "consumer,advanced")
    @Metadata(description = "The name of the Camel component that serves the HTTP routes of the consumer, which must"
                            + " implement RestConsumerFactory, for example platform-http, undertow or jetty. If not set,"
                            + " the consumer uses the component of the REST configuration (camel.rest.component), else"
                            + " platform-http, else the only RestConsumerFactory among the components or in the"
                            + " registry.")
    private String httpServerComponent;

    @UriParam(label = "consumer,security", defaultValue = "false", security = "insecure:dev", insecureValue = "true")
    @Metadata(description = "Whether the consumer accepts push notification webhook URLs whose host resolves to a"
                            + " loopback address, such as localhost or 127.0.0.1, also with http. By default these URLs"
                            + " are rejected, like the URLs to private, link-local and other non-public addresses, which"
                            + " this option does not allow. For local development only.",
              defaultValue = "false")
    private boolean allowLocalWebhookUrls;

    @UriParam(label = "consumer,advanced", defaultValue = "15000")
    @Metadata(description = "The interval in milliseconds at which the consumer writes a heartbeat comment line (:) to a"
                            + " server-sent event stream that has no event to send, so that proxies do not close the idle"
                            + " connection. Used for SendStreamingMessage and SubscribeToTask.",
              defaultValue = "15000")
    private long sseHeartbeatInterval = 15000L;

    @UriParam(label = "consumer,advanced", defaultValue = "1000")
    @Metadata(description = "The number of server-sent events that the consumer buffers per stream for a slow client, at"
                            + " least 2. When the buffer is full, the consumer drops a new event and logs a warning, but"
                            + " for the final events of the stream it drops older events instead.",
              defaultValue = "1000")
    private int sseQueueCapacity = 1000;

    @UriParam(label = "producer,advanced", defaultValue = "300000")
    @Metadata(description = "The time in milliseconds that the producer waits for the next line of a server-sent event"
                            + " stream, for MESSAGE_STREAM and TASK_SUBSCRIBE, before it closes the stream with an error."
                            + " The heartbeat comments of the remote agent count as lines. Use 0 for no timeout.",
              defaultValue = "300000")
    private long streamingReadTimeout = 300000L;

    @UriParam(defaultValue = "PAYLOAD")
    @Metadata(description = "The form of the message body. PAYLOAD: the text of the message parts, one per line, where a"
                            + " Task of the producer gives the parts of the last message of its history. POJO:"
                            + " the model object, which is the Message of the request on the consumer and the Task or the"
                            + " Message of the response on the producer. RAW: JSON text, which is the Message of the"
                            + " request on the consumer and the response as received on the producer (an InputStream of"
                            + " the events for MESSAGE_STREAM and TASK_SUBSCRIBE), where the producer sets no A2A header"
                            + " but CamelA2AMethod. The reply of the consumer does not depend on it.")
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
