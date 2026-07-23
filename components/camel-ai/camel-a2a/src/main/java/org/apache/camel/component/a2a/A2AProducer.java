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

import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.a2a.auth.A2AAuthHandler;
import org.apache.camel.component.a2a.model.AgentCard;
import org.apache.camel.component.a2a.model.ListPushNotificationConfigsResponse;
import org.apache.camel.component.a2a.model.ListTasksResponse;
import org.apache.camel.component.a2a.model.Message;
import org.apache.camel.component.a2a.model.SendMessageResponse;
import org.apache.camel.component.a2a.model.SupportedInterface;
import org.apache.camel.component.a2a.model.Task;
import org.apache.camel.component.a2a.model.TaskListRequest;
import org.apache.camel.component.a2a.model.TaskPushNotificationConfig;
import org.apache.camel.component.a2a.operation.A2AOperation;
import org.apache.camel.component.a2a.operation.A2AOperations;
import org.apache.camel.component.a2a.operation.MessageSendOperation;
import org.apache.camel.component.a2a.operation.MessageStreamOperation;
import org.apache.camel.component.a2a.operation.PushConfigCreateOperation;
import org.apache.camel.component.a2a.operation.PushConfigDeleteOperation;
import org.apache.camel.component.a2a.operation.PushConfigGetOperation;
import org.apache.camel.component.a2a.operation.PushConfigListOperation;
import org.apache.camel.component.a2a.operation.TaskCancelOperation;
import org.apache.camel.component.a2a.operation.TaskGetOperation;
import org.apache.camel.component.a2a.operation.TaskListOperation;
import org.apache.camel.component.a2a.operation.TaskSubscribeOperation;
import org.apache.camel.component.a2a.protocol.A2AProtocol;
import org.apache.camel.component.a2a.streaming.SseEventIterator;
import org.apache.camel.component.a2a.util.BoundedInputStreamReader;
import org.apache.camel.support.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A2A producer. Sends A2A operations to remote agents.
 */
public class A2AProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(A2AProducer.class);

    private final A2AEndpoint endpoint;
    private final MessageSendOperation messageSendOperation = new MessageSendOperation();
    private final TaskGetOperation taskGetOperation = new TaskGetOperation();
    private final TaskCancelOperation taskCancelOperation = new TaskCancelOperation();
    private final TaskListOperation taskListOperation = new TaskListOperation();
    private final PushConfigCreateOperation pushConfigCreateOperation = new PushConfigCreateOperation();
    private final PushConfigGetOperation pushConfigGetOperation = new PushConfigGetOperation();
    private final PushConfigListOperation pushConfigListOperation = new PushConfigListOperation();
    private final PushConfigDeleteOperation pushConfigDeleteOperation = new PushConfigDeleteOperation();
    private final MessageStreamOperation messageStreamOperation = new MessageStreamOperation();
    private final TaskSubscribeOperation taskSubscribeOperation = new TaskSubscribeOperation();

    public A2AProducer(A2AEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        A2AOperations operationType = resolveOperation(exchange);
        LOG.debug("Resolved operationType={}, header={}", operationType,
                exchange.getMessage().getHeader(A2AConstants.OPERATION));

        A2AOperation operation = resolveOperationHandler(operationType);
        A2AProtocol protocol = endpoint.getProtocol();

        Object requestPayload = operation.buildRequest(exchange);
        byte[] requestBody = protocol.wrapRequest(operationType.getMethodName(), requestPayload);

        String baseUrl = resolveBaseUrl();
        A2AAuthHandler.ProducerAuth producerAuth
                = endpoint.getAuthHandler().resolveProducerAuth(exchange, endpoint.getCamelContext(),
                        endpoint.getResolvedCard());
        String taskId = exchange.getMessage().getHeader(A2AConstants.TASK_ID, String.class);
        String path = protocol.resolvePath(operationType, taskId);
        String httpMethod = protocol.resolveHttpMethod(operationType);

        if (("GET".equals(httpMethod) || "DELETE".equals(httpMethod)) && requestPayload != null) {
            String queryString = buildQueryString(requestPayload);
            if (queryString != null && !queryString.isEmpty()) {
                path = path + "?" + queryString;
            }
        }
        path = appendQueryParameters(path, producerAuth.getQueryParameters());

        URI uri = URI.create(baseUrl + path);

        HttpRequest httpRequest = buildHttpRequest(
                uri, httpMethod, requestBody, producerAuth.getHeaders(), operation.isStreaming(), protocol.contentType());

        if (operation.isStreaming()) {
            processStreaming(exchange, operation, operationType, httpRequest, uri, httpMethod, producerAuth);
        } else {
            processNonStreaming(exchange, operation, operationType, protocol, httpRequest, uri, httpMethod, producerAuth);
        }
    }

    private HttpRequest buildHttpRequest(
            URI uri, String httpMethod, byte[] requestBody, Map<String, String> authHeaders,
            boolean streaming, String contentType) {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(uri)
                .header(Exchange.CONTENT_TYPE, contentType)
                .header("Accept", streaming ? A2AConstants.SSE_CONTENT_TYPE : contentType)
                .header(A2AConstants.HEADER_A2A_VERSION, A2AConstants.A2A_VERSION);

        long timeoutMs = streaming
                ? endpoint.getConfiguration().getAsyncTimeout()
                : 60000L;
        requestBuilder.timeout(Duration.ofMillis(timeoutMs));

        for (Map.Entry<String, String> header : authHeaders.entrySet()) {
            requestBuilder.header(header.getKey(), header.getValue());
        }

        if ("POST".equals(httpMethod)) {
            return requestBuilder.POST(HttpRequest.BodyPublishers.ofByteArray(requestBody)).build();
        } else if ("GET".equals(httpMethod)) {
            return requestBuilder.GET().build();
        } else if ("DELETE".equals(httpMethod)) {
            return requestBuilder.DELETE().build();
        } else {
            throw new RuntimeCamelException("HTTP method not supported: " + httpMethod);
        }
    }

    private void processNonStreaming(
            Exchange exchange, A2AOperation operation, A2AOperations operationType,
            A2AProtocol protocol, HttpRequest httpRequest, URI uri, String httpMethod,
            A2AAuthHandler.ProducerAuth producerAuth)
            throws Exception {
        LOG.debug("Sending {} request to {}", httpMethod, redactUri(uri, producerAuth.getQueryParameters()));
        HttpResponse<InputStream> response
                = endpoint.getHttpClient().send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
        byte[] responseBody = BoundedInputStreamReader.readAtMostAndClose(
                response.body(), endpoint.getConfiguration().getMaxPayloadSize(), "A2A response");

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String responseText = new String(responseBody, StandardCharsets.UTF_8);
            String truncated = responseText.length() > 500 ? responseText.substring(0, 500) + "..." : responseText;
            throw new RuntimeCamelException(
                    "A2A request failed with status " + response.statusCode() + ": " + truncated);
        }

        // RAW mode: pass raw JSON through without deserialization
        if (endpoint.getConfiguration().getDataFormat() == A2ADataFormat.RAW) {
            String rawJson = new String(responseBody, StandardCharsets.UTF_8);
            exchange.getMessage().setBody(rawJson);
            exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, protocol.contentType());
            exchange.getMessage().setHeader(A2AConstants.METHOD, operationType.getMethodName());
            return;
        }

        Class<?> responseType = resolveResponseType(operationType);
        Object responsePayload = protocol.unwrapResponse(responseBody, responseType);
        operation.parseResponse(exchange, responsePayload);

        if (endpoint.getConfiguration().getDataFormat() == A2ADataFormat.PAYLOAD) {
            Object body = exchange.getMessage().getBody();
            if (body instanceof Task task) {
                exchange.getMessage().setBody(A2ATypeConverters.taskToString(task));
            } else if (body instanceof Message msg) {
                exchange.getMessage().setBody(A2ATypeConverters.messageToString(msg));
            }
        }

        exchange.getMessage().setHeader(A2AConstants.METHOD, operationType.getMethodName());
    }

    private void processStreaming(
            Exchange exchange, A2AOperation operation, A2AOperations operationType,
            HttpRequest httpRequest, URI uri, String httpMethod, A2AAuthHandler.ProducerAuth producerAuth)
            throws Exception {
        LOG.debug("Sending streaming {} request to {}", httpMethod, redactUri(uri, producerAuth.getQueryParameters()));
        HttpResponse<InputStream> response
                = endpoint.getHttpClient().send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
        LOG.debug("Streaming response status={}, contentType={}", response.statusCode(),
                response.headers().firstValue("Content-Type").orElse("none"));

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            byte[] errorBytes = BoundedInputStreamReader.readAtMostAndClose(
                    response.body(), endpoint.getConfiguration().getMaxPayloadSize(), "A2A streaming error response");
            String errorBody = new String(errorBytes, StandardCharsets.UTF_8);
            String truncated = errorBody.length() > 500 ? errorBody.substring(0, 500) + "..." : errorBody;
            throw new RuntimeCamelException(
                    "A2A streaming request failed with status " + response.statusCode() + ": " + truncated);
        }

        if (endpoint.getConfiguration().getDataFormat() == A2ADataFormat.RAW) {
            exchange.getMessage().setBody(response.body());
            exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, A2AConstants.SSE_CONTENT_TYPE);
            exchange.getMessage().setHeader(A2AConstants.METHOD, operationType.getMethodName());
            return;
        }

        SseEventIterator iterator = new SseEventIterator(
                response.body(), endpoint.getProtocol(),
                endpoint.getConfiguration().getStreamingReadTimeout(),
                endpoint.getConfiguration().getMaxPayloadSize());
        operation.parseResponse(exchange, iterator);
        exchange.getMessage().setHeader(A2AConstants.METHOD, operationType.getMethodName());
    }

    @Override
    public A2AEndpoint getEndpoint() {
        return endpoint;
    }

    /**
     * Resolves the A2A operation from the exchange header or endpoint configuration.
     */
    private A2AOperations resolveOperation(Exchange exchange) {
        String headerOp = exchange.getMessage().getHeader(A2AConstants.OPERATION, String.class);
        if (headerOp != null) {
            // Try method name first
            A2AOperations operation = A2AOperations.fromMethodName(headerOp);
            if (operation != null) {
                return operation;
            }
            // Try enum name
            try {
                return A2AOperations.valueOf(headerOp);
            } catch (IllegalArgumentException e) {
                LOG.warn("Unknown operation in header: {}, falling back to endpoint default", headerOp);
            }
        }
        return endpoint.getConfiguration().getOperation();
    }

    /**
     * Resolves the operation handler for the given operation type.
     */
    private A2AOperation resolveOperationHandler(A2AOperations operationType) {
        return switch (operationType) {
            case MESSAGE_SEND -> messageSendOperation;
            case TASK_GET -> taskGetOperation;
            case TASK_CANCEL -> taskCancelOperation;
            case TASK_LIST -> taskListOperation;
            case PUSH_CONFIG_CREATE -> pushConfigCreateOperation;
            case PUSH_CONFIG_GET -> pushConfigGetOperation;
            case PUSH_CONFIG_LIST -> pushConfigListOperation;
            case PUSH_CONFIG_DELETE -> pushConfigDeleteOperation;
            case MESSAGE_STREAM -> messageStreamOperation;
            case TASK_SUBSCRIBE -> taskSubscribeOperation;
        };
    }

    /**
     * Resolves the response type for the given operation.
     */
    private Class<?> resolveResponseType(A2AOperations operationType) {
        return switch (operationType) {
            case MESSAGE_SEND -> SendMessageResponse.class;
            case TASK_GET, TASK_CANCEL -> Task.class;
            case TASK_LIST -> ListTasksResponse.class;
            case PUSH_CONFIG_CREATE, PUSH_CONFIG_GET -> TaskPushNotificationConfig.class;
            case PUSH_CONFIG_LIST -> ListPushNotificationConfigsResponse.class;
            default -> Object.class;
        };
    }

    /**
     * Resolves the base URL for the remote agent. Priority: config host/port/basePath overrides card's
     * supportedInterfaces, which overrides the agentCardSource URL.
     */
    private String resolveBaseUrl() {
        A2AConfiguration config = endpoint.getConfiguration();

        // 1. Explicit host config overrides everything (dev/test override)
        String host = config.getHost();
        if (host != null && !host.isBlank()) {
            return buildUrlFromConfig(host, config.getPort(), config.getBasePath());
        }

        String cardSourceUrl = resolveBaseUrlFromAgentCardSource();
        if (hasConfiguredProducerCredentials()) {
            if (cardSourceUrl != null) {
                return cardSourceUrl;
            }
            throw new IllegalStateException(
                    "Cannot send configured producer credentials without an explicit host or HTTP(S) agentCardSource");
        }

        // 2. Try supportedInterfaces from resolved card
        AgentCard card = endpoint.getResolvedCard();
        if (card != null && card.getSupportedInterfaces() != null && !card.getSupportedInterfaces().isEmpty()) {
            String binding = endpoint.getConfiguration().getProtocolBinding();
            for (SupportedInterface iface : card.getSupportedInterfaces()) {
                if (binding.equals(iface.getProtocolBinding())) {
                    String url = iface.getUrl();
                    if (url != null && !url.isEmpty()) {
                        return stripTrailingSlash(url);
                    }
                }
            }
            throw new IllegalStateException(
                    "Cannot resolve base URL: agent card has no supportedInterfaces URL for protocol binding "
                                            + binding);
        }

        // 3. Try agentCardSource if it's an HTTP URL
        if (cardSourceUrl != null) {
            return cardSourceUrl;
        }

        throw new IllegalStateException(
                "Cannot resolve base URL: no host config, no supportedInterfaces in agent card, "
                                        + "and agentCardSource is not an HTTP URL");
    }

    private String resolveBaseUrlFromAgentCardSource() {
        String cardSource = endpoint.getAgentCardSource();
        if (cardSource != null && (cardSource.startsWith("http://") || cardSource.startsWith("https://"))) {
            if (cardSource.endsWith(A2AConstants.WELL_KNOWN_PATH)) {
                return stripTrailingSlash(
                        cardSource.substring(0, cardSource.length() - A2AConstants.WELL_KNOWN_PATH.length()));
            }
            return cardSource;
        }
        return null;
    }

    private boolean hasConfiguredProducerCredentials() {
        A2AConfiguration config = endpoint.getConfiguration();
        return config.getApiKey() != null || config.getBearerToken() != null || config.getOauthProfile() != null;
    }

    private static String buildUrlFromConfig(String host, Integer port, String basePath) {
        StringBuilder url = new StringBuilder();
        if (host.startsWith("http://") || host.startsWith("https://")) {
            url.append(host);
        } else {
            url.append("https://").append(host);
        }
        if (port != null) {
            url.append(":").append(port);
        }
        if (basePath != null && !basePath.isEmpty()) {
            if (!basePath.startsWith("/")) {
                url.append("/");
            }
            url.append(basePath);
        }
        return stripTrailingSlash(url.toString());
    }

    private static String appendQueryParameters(String path, Map<String, String> queryParameters) {
        if (queryParameters.isEmpty()) {
            return path;
        }
        StringBuilder answer = new StringBuilder(path);
        String separator = path.contains("?") ? "&" : "?";
        for (Map.Entry<String, String> parameter : queryParameters.entrySet()) {
            answer.append(separator)
                    .append(URLEncoder.encode(parameter.getKey(), StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(parameter.getValue(), StandardCharsets.UTF_8));
            separator = "&";
        }
        return answer.toString();
    }

    private static String redactUri(URI uri, Map<String, String> sensitiveQueryParameters) {
        if (sensitiveQueryParameters.isEmpty() || uri.getRawQuery() == null) {
            return uri.toString();
        }
        StringBuilder query = new StringBuilder();
        for (String pair : uri.getRawQuery().split("&")) {
            if (query.length() > 0) {
                query.append('&');
            }
            int eq = pair.indexOf('=');
            String name = eq >= 0 ? pair.substring(0, eq) : pair;
            String decodedName = URLDecoder.decode(name, StandardCharsets.UTF_8);
            query.append(name);
            if (eq >= 0) {
                query.append('=');
                query.append(sensitiveQueryParameters.containsKey(decodedName) ? "xxxxxx" : pair.substring(eq + 1));
            }
        }
        StringBuilder answer = new StringBuilder();
        answer.append(uri.getScheme()).append("://").append(uri.getRawAuthority()).append(uri.getRawPath());
        answer.append('?').append(query);
        if (uri.getRawFragment() != null) {
            answer.append('#').append(uri.getRawFragment());
        }
        return answer.toString();
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private String buildQueryString(Object payload) {
        if (payload instanceof TaskListRequest request) {
            StringBuilder query = new StringBuilder();
            appendParam(query, "contextId", request.getContextId());
            appendParam(query, "pageSize", request.getPageSize());
            appendParam(query, "pageToken", request.getPageToken());
            appendParam(query, "includeArtifacts", request.getIncludeArtifacts());
            appendParam(query, "historyLength", request.getHistoryLength());
            appendParam(query, "statusTimestampAfter", request.getStatusTimestampAfter());
            if (request.getStatus() != null && !request.getStatus().isEmpty()) {
                appendParam(query, "status", String.join(",", request.getStatus()));
            }
            return query.length() > 0 ? query.toString() : null;
        }
        return null;
    }

    private static void appendParam(StringBuilder query, String key, Object value) {
        if (value == null) {
            return;
        }
        if (query.length() > 0) {
            query.append('&');
        }
        query.append(URLEncoder.encode(key, StandardCharsets.UTF_8))
                .append('=')
                .append(URLEncoder.encode(String.valueOf(value), StandardCharsets.UTF_8));
    }
}
