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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.a2a.auth.A2AUserProfile;
import org.apache.camel.component.a2a.extension.A2AExtensionHandler;
import org.apache.camel.component.a2a.model.AgentCard;
import org.apache.camel.component.a2a.model.AgentExtension;
import org.apache.camel.component.a2a.model.ListPushNotificationConfigsResponse;
import org.apache.camel.component.a2a.model.ListTasksResponse;
import org.apache.camel.component.a2a.model.Message;
import org.apache.camel.component.a2a.model.SecurityScheme;
import org.apache.camel.component.a2a.model.SendMessageConfiguration;
import org.apache.camel.component.a2a.model.SendMessageRequest;
import org.apache.camel.component.a2a.model.SendMessageResponse;
import org.apache.camel.component.a2a.model.StreamResponse;
import org.apache.camel.component.a2a.model.Task;
import org.apache.camel.component.a2a.model.TaskListRequest;
import org.apache.camel.component.a2a.model.TaskPushNotificationConfig;
import org.apache.camel.component.a2a.model.TaskState;
import org.apache.camel.component.a2a.model.TaskStatus;
import org.apache.camel.component.a2a.model.TaskStatusUpdateEvent;
import org.apache.camel.component.a2a.operation.A2AOperations;
import org.apache.camel.component.a2a.operation.MessageSendOperation;
import org.apache.camel.component.a2a.protocol.JsonRpcProtocol;
import org.apache.camel.component.a2a.state.A2ATaskStore;
import org.apache.camel.component.a2a.state.A2ATaskSubscriber;
import org.apache.camel.component.a2a.streaming.QueueStreamEmitter;
import org.apache.camel.component.a2a.streaming.SseQueueInputStream;
import org.apache.camel.component.a2a.streaming.StreamSubscriber;
import org.apache.camel.component.a2a.util.A2AJsonMapper;
import org.apache.camel.component.a2a.util.BoundedInputStreamReader;
import org.apache.camel.component.a2a.util.WebhookUrlValidator;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.RestConsumerFactory;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.service.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A2A consumer that automatically registers HTTP endpoints via {@link RestConsumerFactory} SPI.
 * <p>
 * On startup, discovers a {@link RestConsumerFactory} (typically provided by camel-platform-http) and registers routes
 * for all A2A operations. For REST binding, creates separate routes per operation path. For JSON-RPC binding, creates a
 * single POST route that dispatches via the JSON-RPC method field.
 * <p>
 * The agent card is always served at {@code /.well-known/agent-card.json} regardless of protocol binding.
 */
public class A2AConsumer extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(A2AConsumer.class);
    private static final ObjectMapper OBJECT_MAPPER = A2AJsonMapper.instance();
    private static final String REST_AND_SSE_CONTENT_TYPES
            = A2AConstants.CONTENT_TYPE + "," + A2AConstants.SSE_CONTENT_TYPE;
    private static final String JSONRPC_AND_SSE_CONTENT_TYPES
            = A2AConstants.JSONRPC_CONTENT_TYPE + "," + A2AConstants.SSE_CONTENT_TYPE;
    private static final String CACHE_CONTROL_HEADER = "Cache-Control";
    private static final String CONNECTION_HEADER = "Connection";
    private static final String NO_CACHE = "no-cache";
    private static final String KEEP_ALIVE = "keep-alive";
    private static final String STREAMING_CAPABILITY = "streaming";
    private static final String PUSH_NOTIFICATIONS_CAPABILITY = "pushNotifications";
    private static final String STREAMING_DISABLED_MESSAGE = "Streaming is not enabled for this agent";
    private static final String PUSH_NOTIFICATIONS_DISABLED_MESSAGE
            = "Push notifications are not enabled for this agent";

    private final MessageSendOperation messageSendOperation;
    private final List<Consumer> httpConsumers = new ArrayList<>();
    private final ConcurrentHashMap<String, Future<?>> inFlightTasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ScheduledFuture<?>> timeoutFutures = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> taskOwners = new ConcurrentHashMap<>();
    private ExecutorService asyncExecutor;
    private ScheduledExecutorService asyncTimeoutScheduler;
    private Semaphore taskPermits;
    private LinkedBlockingQueue<PendingTask> pendingTaskQueue;
    private boolean corsEnabled;
    private Map<String, String> corsHeaders;

    public A2AConsumer(A2AEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.messageSendOperation = new MessageSendOperation();
    }

    @Override
    public A2AEndpoint getEndpoint() {
        return (A2AEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        AgentCard card = getEndpoint().getResolvedCard();
        String agentName = card != null ? card.getName() : "unknown";
        LOG.info("A2A Consumer starting for agent: {}", agentName);

        RestConsumerFactory factory = A2AHttpTransportSupport.resolveRestConsumerFactory(getEndpoint(), LOG);
        if (factory == null) {
            throw new IllegalStateException(
                    "No RestConsumerFactory found. The A2A consumer requires camel-platform-http or another "
                                            + "HTTP server implementation selected with httpServerComponent or "
                                            + "camel.rest.component.");
        }

        try {
            asyncExecutor = getEndpoint().getCamelContext()
                    .getExecutorServiceManager()
                    .newThreadPool(this, "A2AAsyncProcessor", 1, 10);
            asyncTimeoutScheduler = getEndpoint().getCamelContext()
                    .getExecutorServiceManager()
                    .newScheduledThreadPool(this, "A2AAsyncTimeout", 1);

            int maxTasks = getEndpoint().getConfiguration().getMaxConcurrentTasks();
            if (maxTasks > 0) {
                taskPermits = new Semaphore(maxTasks);
                int queueSize = getEndpoint().getConfiguration().getTaskQueueSize();
                if (queueSize > 0) {
                    pendingTaskQueue = new LinkedBlockingQueue<>(queueSize);
                }
                LOG.info("A2A capacity limits: maxConcurrentTasks={}, taskQueueSize={}", maxTasks, queueSize);
            }

            RestConfiguration restConfig = getEndpoint().getCamelContext().getRestConfiguration();
            String basePath = getEndpoint().getConfiguration().getBasePath();

            corsEnabled = restConfig.isEnableCORS();
            corsHeaders = restConfig.getCorsHeaders();

            // Agent card is always public — no auth required
            registerRoute(factory, restConfig, "GET", basePath + A2AConstants.WELL_KNOWN_PATH,
                    this::handleAgentCardRequest, true, null, A2AConstants.CONTENT_TYPE);

            boolean isJsonRpc = isJsonRpcBinding();

            if (isJsonRpc) {
                registerSseRoute(factory, restConfig, "POST", basePath + "/",
                        this::handleJsonRpcDispatch, A2AConstants.JSONRPC_CONTENT_TYPE, JSONRPC_AND_SSE_CONTENT_TYPES);
            } else {
                registerRoute(factory, restConfig, "POST", basePath + "/message:send",
                        this::handleSendMessage, A2AConstants.CONTENT_TYPE, A2AConstants.CONTENT_TYPE);
                registerSseRoute(factory, restConfig, "POST", basePath + "/message:stream",
                        this::handleMessageStream, A2AConstants.CONTENT_TYPE, REST_AND_SSE_CONTENT_TYPES);
                registerRoute(factory, restConfig, "GET", basePath + "/tasks",
                        this::handleListTasks, null, A2AConstants.CONTENT_TYPE);
                registerRoute(factory, restConfig, "GET", basePath + "/tasks/{taskId}",
                        this::handleGetTask, null, A2AConstants.CONTENT_TYPE);
                registerRoute(factory, restConfig, "POST", basePath + "/tasks/{taskId}:cancel",
                        this::handleCancelTask, A2AConstants.CONTENT_TYPE, A2AConstants.CONTENT_TYPE);
                registerSseRoute(factory, restConfig, "POST", basePath + "/tasks/{taskId}:subscribe",
                        this::handleTaskSubscribe, A2AConstants.CONTENT_TYPE, REST_AND_SSE_CONTENT_TYPES);
                registerRoute(factory, restConfig, "POST", basePath + "/tasks/{taskId}/pushNotificationConfigs",
                        this::handlePushConfigCreate, A2AConstants.CONTENT_TYPE, A2AConstants.CONTENT_TYPE);
                registerRoute(factory, restConfig, "GET", basePath + "/tasks/{taskId}/pushNotificationConfigs",
                        this::handlePushConfigList, null, A2AConstants.CONTENT_TYPE);
                registerRoute(factory, restConfig, "GET",
                        basePath + "/tasks/{taskId}/pushNotificationConfigs/{configId}",
                        this::handlePushConfigGet, null, A2AConstants.CONTENT_TYPE);
                registerRoute(factory, restConfig, "DELETE",
                        basePath + "/tasks/{taskId}/pushNotificationConfigs/{configId}",
                        this::handlePushConfigDelete, null, A2AConstants.CONTENT_TYPE);
            }

            if (corsEnabled) {
                registerRoute(factory, restConfig, "OPTIONS", basePath + A2AConstants.WELL_KNOWN_PATH,
                        A2AHttpTransportSupport::handleCorsPreFlight, true, null, null);
                if (isJsonRpc) {
                    registerRoute(factory, restConfig, "OPTIONS", basePath + "/",
                            A2AHttpTransportSupport::handleCorsPreFlight, true, null, null);
                } else {
                    for (String path : List.of(
                            "/message:send", "/message:stream", "/tasks", "/tasks/{taskId}",
                            "/tasks/{taskId}:cancel", "/tasks/{taskId}:subscribe",
                            "/tasks/{taskId}/pushNotificationConfigs",
                            "/tasks/{taskId}/pushNotificationConfigs/{configId}")) {
                        registerRoute(factory, restConfig, "OPTIONS", basePath + path,
                                A2AHttpTransportSupport::handleCorsPreFlight, true, null, null);
                    }
                }
            }

            for (Consumer consumer : httpConsumers) {
                ServiceHelper.startService(consumer);
            }

            LOG.info("A2A Consumer registered {} HTTP endpoint(s) for agent '{}'",
                    httpConsumers.size(), agentName);
        } catch (Exception e) {
            cleanupConsumerResources(true);
            throw e;
        }
    }

    @Override
    protected void doStop() throws Exception {
        failPendingQueuedTasks();
        cancelAllInFlight();
        for (Consumer consumer : httpConsumers) {
            ServiceHelper.stopService(consumer);
        }
        httpConsumers.clear();
        shutdownConsumerExecutors();
        super.doStop();
    }

    @Override
    protected void doShutdown() throws Exception {
        failPendingQueuedTasks();
        cancelAllInFlight();
        for (Consumer consumer : httpConsumers) {
            ServiceHelper.stopAndShutdownService(consumer);
        }
        httpConsumers.clear();
        shutdownConsumerExecutors();
        super.doShutdown();
    }

    private void cleanupConsumerResources(boolean shutdownHttpConsumers) {
        failPendingQueuedTasks();
        cancelAllInFlight();
        for (Consumer consumer : httpConsumers) {
            if (shutdownHttpConsumers) {
                ServiceHelper.stopAndShutdownService(consumer);
            } else {
                ServiceHelper.stopService(consumer);
            }
        }
        httpConsumers.clear();
        shutdownConsumerExecutors();
    }

    private void shutdownConsumerExecutors() {
        if (asyncExecutor != null) {
            getEndpoint().getCamelContext().getExecutorServiceManager().shutdownGraceful(asyncExecutor);
            asyncExecutor = null;
        }
        if (asyncTimeoutScheduler != null) {
            getEndpoint().getCamelContext().getExecutorServiceManager().shutdownGraceful(asyncTimeoutScheduler);
            asyncTimeoutScheduler = null;
        }
        taskPermits = null;
        pendingTaskQueue = null;
        taskOwners.clear();
    }

    private void cancelAllInFlight() {
        A2ATaskStore store = getEndpoint().getTaskStore();
        inFlightTasks.forEach((taskId, future) -> {
            if (inFlightTasks.remove(taskId, future)) {
                future.cancel(true);
                store.updateStatusAndNotify(taskId, new TaskStatus(TaskState.FAILED));
                releasePermit(false);
            }
        });
        for (ScheduledFuture<?> timeoutFuture : timeoutFutures.values()) {
            timeoutFuture.cancel(false);
        }
        timeoutFutures.clear();
    }

    // ---- Capacity limiting ----

    private boolean tryAcquirePermit() {
        return taskPermits == null || taskPermits.tryAcquire();
    }

    private void releasePermit() {
        releasePermit(true);
    }

    private void releasePermit(boolean drain) {
        if (taskPermits != null) {
            taskPermits.release();
            if (drain) {
                drainPendingQueue();
            }
        }
    }

    private void drainPendingQueue() {
        if (pendingTaskQueue == null || pendingTaskQueue.isEmpty()) {
            return;
        }
        if (!taskPermits.tryAcquire()) {
            return;
        }
        PendingTask pending = pendingTaskQueue.poll();
        if (pending == null) {
            taskPermits.release();
            return;
        }
        try {
            submitAsyncTask(pending.taskId, pending.contextId, pending.processorExchange);
        } catch (Exception e) {
            cleanupTaskSubmissionFailure(pending.taskId, null, null, null);
            releasePermit();
            LOG.error("Failed to submit pending task {}", pending.taskId, e);
        }
    }

    private void failPendingQueuedTasks() {
        if (pendingTaskQueue == null) {
            return;
        }
        A2ATaskStore store = getEndpoint().getTaskStore();
        PendingTask pending;
        while ((pending = pendingTaskQueue.poll()) != null) {
            Task failedTask = Task.builder()
                    .id(pending.taskId)
                    .contextId(pending.contextId)
                    .status(new TaskStatus(TaskState.FAILED))
                    .build();
            store.put(pending.taskId, failedTask);
            store.notifySubscribers(pending.taskId,
                    StreamResponse.ofStatusUpdate(buildStatusEvent(failedTask)));
            forgetTaskOwner(pending.taskId);
        }
    }

    private void writeServerBusyError(Exchange exchange) throws Exception {
        int maxTasks = getEndpoint().getConfiguration().getMaxConcurrentTasks();
        writeRestError(exchange, 429, "ServerBusyError",
                "Agent at capacity: " + maxTasks + " concurrent tasks");
    }

    private static void writeRestError(Exchange exchange, int statusCode, String code, String message) throws Exception {
        A2AErrorSupport.writeRestError(exchange, statusCode, code, message);
    }

    private static void writeJsonResponse(Exchange exchange, Object response) throws Exception {
        exchange.getMessage().setBody(OBJECT_MAPPER.writeValueAsBytes(response));
        exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, A2AConstants.CONTENT_TYPE);
    }

    private void writeProtocolError(Exchange exchange, String code, String message) throws Exception {
        if (isJsonRpcBinding()) {
            writeJsonRpcError(exchange, A2AErrorSupport.jsonRpcErrorCode(code), message, extractJsonRpcRequestId(exchange),
                    List.of(A2AErrorSupport.errorInfo(code)));
        } else {
            writeRestError(exchange, A2AErrorSupport.httpStatusCode(code), code, message);
        }
    }

    static class ServerBusyException extends RuntimeException {
        ServerBusyException(String message) {
            super(message);
        }
    }

    private record PendingTask(String taskId, String contextId, Exchange processorExchange) {
    }

    private void registerRoute(
            RestConsumerFactory factory, RestConfiguration restConfig,
            String verb, String path, A2ARequestHandler handler)
            throws Exception {
        registerRoute(factory, restConfig, verb, path, handler, false, null, A2AConstants.CONTENT_TYPE);
    }

    private void registerRoute(
            RestConsumerFactory factory, RestConfiguration restConfig,
            String verb, String path, A2ARequestHandler handler, String consumes, String produces)
            throws Exception {
        registerRoute(factory, restConfig, verb, path, handler, false, consumes, produces);
    }

    private void registerRoute(
            RestConsumerFactory factory, RestConfiguration restConfig,
            String verb, String path, A2ARequestHandler handler, boolean isPublic,
            String consumes, String produces)
            throws Exception {
        Consumer consumer = factory.createConsumer(
                getEndpoint().getCamelContext(),
                createDispatchProcessor(handler, isPublic),
                verb, path, null,
                consumes, produces,
                restConfig, Collections.emptyMap());
        getEndpoint().configureNestedConsumer(consumer);
        httpConsumers.add(consumer);
        LOG.debug("Registered A2A route: {} {}", verb, path);
    }

    private void registerSseRoute(
            RestConsumerFactory factory, RestConfiguration restConfig,
            String verb, String path, A2ARequestHandler handler, String consumes, String produces)
            throws Exception {
        Consumer consumer = factory.createConsumer(
                getEndpoint().getCamelContext(),
                createDispatchProcessor(handler, false),
                verb, path, null,
                consumes, produces,
                restConfig, Map.of("useStreaming", "true"));
        getEndpoint().configureNestedConsumer(consumer);
        httpConsumers.add(consumer);
        LOG.debug("Registered A2A SSE route: {} {}", verb, path);
    }

    Processor createDispatchProcessor(A2ARequestHandler handler, boolean isPublic) {
        return exchange -> {
            try {
                if (!isPublic) {
                    String clientVersion = resolveRequestedVersion(exchange);
                    if (clientVersion != null && !A2AConstants.A2A_VERSION.equals(clientVersion)) {
                        writeProtocolError(exchange, "VersionNotSupportedError",
                                "Unsupported A2A version: " + clientVersion
                                                                                 + ". Supported: " + A2AConstants.A2A_VERSION);
                        return;
                    }
                    List<AgentExtension> extensions = validateRequestedExtensions(exchange);
                    if (!extensions.isEmpty()) {
                        List<String> extensionUris = extensionUris(extensions);
                        exchange.setProperty(A2AConstants.EXTENSIONS, extensionUris);
                        exchange.setProperty(A2AConstants.EXTENSION_DECLARATIONS, extensions);
                        exchange.getMessage().setHeader(A2AConstants.EXTENSIONS, extensionUris);
                    }
                    A2AUserProfile profile = getEndpoint().getAuthHandler()
                            .validateConsumerAuth(exchange, getEndpoint().getResolvedCard());
                    if (profile != null) {
                        exchange.setProperty(A2AConstants.USER_PROFILE, profile.asMap());
                    }
                }
                handler.handle(exchange);
                if (!isPublic) {
                    setProtocolResponseHeaders(exchange);
                }
            } catch (SecurityException e) {
                LOG.debug("Authentication failed: {}", e.getMessage());
                writeProtocolError(exchange, "AuthenticationError", e.getMessage());
                exchange.getMessage().setHeader("WWW-Authenticate", resolveWwwAuthenticate());
            } catch (AuthorizationException e) {
                LOG.debug("A2A authorization failed: {}", e.getMessage());
                writeProtocolError(exchange, "AuthorizationError", e.getMessage());
            } catch (UnsupportedExtensionException e) {
                LOG.debug("A2A extension negotiation failed: {}", e.getMessage());
                writeProtocolError(exchange, "ExtensionSupportRequiredError", e.getMessage());
            } catch (Exception e) {
                LOG.error("A2A request handling failed", e);
                writeProtocolError(exchange, "InternalError", "Internal server error");
            } finally {
                if (corsEnabled) {
                    A2AHttpTransportSupport.setCorsHeaders(exchange, corsHeaders, LOG);
                }
            }
        };
    }

    private boolean isJsonRpcBinding() {
        return A2AConstants.PROTOCOL_JSONRPC.equalsIgnoreCase(getEndpoint().getConfiguration().getProtocolBinding());
    }

    private String resolveRequestedVersion(Exchange exchange) {
        String header = exchange.getMessage().getHeader(A2AConstants.HEADER_A2A_VERSION, String.class);
        if (header != null) {
            return header.isBlank() ? "0.3" : header.trim();
        }
        String queryValue = A2AHttpPathSupport.parseQueryParameter(exchange, A2AConstants.HEADER_A2A_VERSION);
        if (queryValue != null) {
            return queryValue.isBlank() ? "0.3" : queryValue.trim();
        }
        return null;
    }

    private Object extractJsonRpcRequestId(Exchange exchange) {
        if (!isJsonRpcBinding()) {
            return null;
        }
        try {
            byte[] body = readRequestBodyBytes(
                    exchange, getEndpoint().getConfiguration().getMaxPayloadSize(), "A2A JSON-RPC request");
            if (body == null || body.length == 0) {
                return null;
            }
            exchange.getMessage().setBody(body);
            JsonRpcProtocol jsonRpc = (JsonRpcProtocol) getEndpoint().getProtocol();
            return jsonRpc.extractId(jsonRpc.parseEnvelope(body));
        } catch (Exception e) {
            return null;
        }
    }

    private List<AgentExtension> validateRequestedExtensions(Exchange exchange) {
        List<String> requested = parseExtensionHeader(
                exchange.getMessage().getHeader(A2AConstants.HEADER_A2A_EXTENSIONS, String.class));

        Map<String, AgentExtension> supported = supportedExtensionDeclarations();
        List<String> required = supported.values().stream()
                .filter(extension -> Boolean.TRUE.equals(extension.getRequired()))
                .map(AgentExtension::getUri)
                .filter(extension -> !requested.contains(extension))
                .toList();
        if (!required.isEmpty()) {
            throw new UnsupportedExtensionException(
                    "Required A2A extension(s) not requested: " + String.join(", ", required));
        }
        if (requested.isEmpty()) {
            return List.of();
        }

        List<String> unsupported = requested.stream()
                .filter(extension -> !supported.containsKey(extension))
                .toList();
        if (!unsupported.isEmpty()) {
            throw new UnsupportedExtensionException(
                    "Unsupported A2A extension(s): " + String.join(", ", unsupported));
        }
        return requested.stream()
                .map(supported::get)
                .toList();
    }

    private Map<String, AgentExtension> supportedExtensionDeclarations() {
        AgentCard card = getEndpoint().getResolvedCard();
        if (card == null || card.getCapabilities() == null || card.getCapabilities().getExtensions() == null) {
            return Map.of();
        }

        Map<String, AgentExtension> answer = new LinkedHashMap<>();
        for (AgentExtension extension : card.getCapabilities().getExtensions()) {
            if (extension.getUri() != null && !extension.getUri().isBlank()) {
                answer.put(extension.getUri(), extension);
            }
        }
        return answer;
    }

    private static List<String> parseExtensionHeader(String header) {
        if (header == null || header.isBlank()) {
            return List.of();
        }

        List<String> answer = new ArrayList<>();
        for (String value : header.split(",")) {
            String trimmed = value.trim();
            if (!trimmed.isEmpty()) {
                answer.add(trimmed);
            }
        }
        return List.copyOf(answer);
    }

    @SuppressWarnings("unchecked")
    private static List<String> negotiatedExtensions(Exchange exchange) {
        Object value = exchange.getProperty(A2AConstants.EXTENSIONS);
        if (value instanceof List<?> list) {
            return (List<String>) list;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private static List<AgentExtension> negotiatedExtensionDeclarations(Exchange exchange) {
        Object value = exchange.getProperty(A2AConstants.EXTENSION_DECLARATIONS);
        if (value instanceof List<?> list) {
            return (List<AgentExtension>) list;
        }
        return List.of();
    }

    private static List<String> extensionUris(List<AgentExtension> extensions) {
        return extensions.stream()
                .map(AgentExtension::getUri)
                .toList();
    }

    private static void setProtocolResponseHeaders(Exchange exchange) {
        exchange.getMessage().setHeader(A2AConstants.HEADER_A2A_VERSION, A2AConstants.A2A_VERSION);
        List<String> extensions = negotiatedExtensions(exchange);
        if (!extensions.isEmpty()) {
            exchange.getMessage().setHeader(A2AConstants.HEADER_A2A_EXTENSIONS, String.join(", ", extensions));
        }
    }

    // ---- JSON-RPC dispatch ----

    void handleJsonRpcDispatch(Exchange exchange) throws Exception {
        filterInboundHeaders(exchange);
        long maxSize = getEndpoint().getConfiguration().getMaxPayloadSize();
        byte[] body;
        try {
            body = readRequestBodyBytes(exchange, maxSize, "A2A JSON-RPC request");
        } catch (IOException e) {
            writeJsonRpcError(exchange, JsonRpcProtocol.INVALID_REQUEST, e.getMessage(), null);
            return;
        }
        if (body == null || body.length == 0) {
            writeJsonRpcError(exchange, JsonRpcProtocol.INVALID_REQUEST, "Invalid Request: empty body", null);
            return;
        }

        JsonRpcProtocol jsonRpc = (JsonRpcProtocol) getEndpoint().getProtocol();
        String method;
        Object requestId;
        Map<String, Object> params;
        try {
            Map<String, Object> envelope = jsonRpc.parseEnvelope(body);
            requestId = jsonRpc.extractId(envelope);
            if (!JsonRpcProtocol.VERSION.equals(envelope.get("jsonrpc"))) {
                writeJsonRpcError(exchange, JsonRpcProtocol.INVALID_REQUEST,
                        "Invalid Request: jsonrpc must be 2.0", requestId);
                return;
            }
            method = jsonRpc.detectMethod(envelope);
            Object rawParams = envelope.get("params");
            if (rawParams != null && !(rawParams instanceof Map)) {
                writeJsonRpcError(exchange, JsonRpcProtocol.INVALID_PARAMS,
                        "Invalid params: params must be an object", requestId);
                return;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> typedParams = (Map<String, Object>) rawParams;
            params = typedParams;
        } catch (Exception e) {
            writeJsonRpcError(exchange, JsonRpcProtocol.PARSE_ERROR, "Parse error", null);
            return;
        }

        if (method == null) {
            writeJsonRpcError(exchange, JsonRpcProtocol.INVALID_REQUEST, "Invalid Request: missing method", requestId);
            return;
        }

        // Streaming methods produce text/event-stream, not a single JSON-RPC envelope
        A2AOperations operation = A2AOperations.fromMethodName(method);
        LOG.debug("JSON-RPC dispatch: method={}, operation={}", method, operation);
        if (operation == null) {
            writeJsonRpcError(exchange, JsonRpcProtocol.METHOD_NOT_FOUND, "Method not found", requestId);
            return;
        }
        if (operation == A2AOperations.MESSAGE_STREAM) {
            LOG.debug("Entering handleJsonRpcStream for requestId={}", requestId);
            try {
                handleJsonRpcStream(exchange, params, requestId);
                LOG.debug("handleJsonRpcStream completed, contentType={}",
                        exchange.getMessage().getHeader(Exchange.CONTENT_TYPE));
            } catch (Exception e) {
                LOG.error("handleJsonRpcStream failed: {}", e.getMessage(), e);
                writeJsonRpcError(exchange, JsonRpcProtocol.INTERNAL_ERROR, "Internal error", requestId);
            }
            return;
        }
        if (operation == A2AOperations.TASK_SUBSCRIBE) {
            LOG.debug("Entering handleJsonRpcTaskSubscribe for requestId={}", requestId);
            try {
                handleJsonRpcTaskSubscribe(exchange, params, requestId);
            } catch (Exception e) {
                LOG.error("handleJsonRpcTaskSubscribe failed: {}", e.getMessage(), e);
                writeJsonRpcError(exchange, JsonRpcProtocol.INTERNAL_ERROR, "Internal error", requestId);
            }
            return;
        }

        Object result;
        try {
            result = dispatchJsonRpcMethod(method, params, exchange);
        } catch (ServerBusyException e) {
            writeJsonRpcA2AError(exchange, "ServerBusyError", e.getMessage(), requestId);
            return;
        } catch (TaskNotFoundException e) {
            writeJsonRpcA2AError(exchange, "TaskNotFoundError", e.getMessage(), requestId);
            return;
        } catch (AuthorizationException e) {
            writeJsonRpcA2AError(exchange, "AuthorizationError", e.getMessage(), requestId);
            return;
        } catch (UnsupportedOperationException e) {
            writeJsonRpcA2AError(exchange, "UnsupportedOperationError", e.getMessage(), requestId);
            return;
        } catch (IllegalStateException e) {
            writeJsonRpcA2AError(exchange, "TaskNotCancelableError", e.getMessage(), requestId);
            return;
        } catch (IllegalArgumentException e) {
            writeJsonRpcError(exchange, JsonRpcProtocol.INVALID_PARAMS, "Invalid params: " + e.getMessage(), requestId);
            return;
        } catch (Exception e) {
            LOG.error("JSON-RPC dispatch failed for method {}", method, e);
            writeJsonRpcError(exchange, JsonRpcProtocol.INTERNAL_ERROR, "Internal error", requestId);
            return;
        }

        byte[] response = jsonRpc.wrapJsonRpcResponse(result, requestId);
        exchange.getMessage().setBody(response);
        exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, A2AConstants.JSONRPC_CONTENT_TYPE);
    }

    private Object dispatchJsonRpcMethod(String method, Map<String, Object> params, Exchange exchange) throws Exception {
        A2AOperations operation = A2AOperations.fromMethodName(method);
        if (operation == null) {
            throw new IllegalArgumentException("Unknown method: " + method);
        }
        return switch (operation) {
            case MESSAGE_SEND -> {
                requireParams(params, method);
                SendMessageRequest request = OBJECT_MAPPER.convertValue(params, SendMessageRequest.class);
                validateSendMessageRequest(request);
                yield processSendMessage(request, exchange);
            }
            case TASK_GET -> {
                String taskId = requiredStringParam(params, "id");
                yield processGetTask(taskId, exchange);
            }
            case TASK_CANCEL -> {
                String taskId = requiredStringParam(params, "id");
                yield processCancelTask(taskId, exchange);
            }
            case TASK_LIST -> {
                TaskListRequest request = params != null
                        ? OBJECT_MAPPER.convertValue(params, TaskListRequest.class)
                        : new TaskListRequest();
                yield processListTasks(request, exchange);
            }
            case PUSH_CONFIG_CREATE -> {
                String taskId = requiredStringParam(params, "taskId");
                TaskPushNotificationConfig config = OBJECT_MAPPER.convertValue(params, TaskPushNotificationConfig.class);
                validatePushConfig(config);
                yield processPushConfigCreate(taskId, config, exchange);
            }
            case PUSH_CONFIG_GET -> {
                String taskId = requiredStringParam(params, "taskId");
                String configId = requiredStringParam(params, "id");
                yield processPushConfigGet(taskId, configId, exchange);
            }
            case PUSH_CONFIG_LIST -> {
                String taskId = requiredStringParam(params, "taskId");
                yield processPushConfigList(taskId, exchange);
            }
            case PUSH_CONFIG_DELETE -> {
                String taskId = requiredStringParam(params, "taskId");
                String configId = requiredStringParam(params, "id");
                yield processPushConfigDelete(taskId, configId, exchange);
            }
            default -> throw new IllegalArgumentException("Unsupported method: " + method);
        };
    }

    private void writeJsonRpcError(Exchange exchange, int code, String message, Object requestId) throws Exception {
        writeJsonRpcError(exchange, code, message, requestId, null);
    }

    private void writeJsonRpcA2AError(Exchange exchange, String code, String message, Object requestId) throws Exception {
        writeJsonRpcError(exchange, A2AErrorSupport.jsonRpcErrorCode(code), message, requestId,
                List.of(A2AErrorSupport.errorInfo(code)));
    }

    private void writeJsonRpcError(
            Exchange exchange, int code, String message, Object requestId, Object data)
            throws Exception {
        JsonRpcProtocol jsonRpc = (JsonRpcProtocol) getEndpoint().getProtocol();
        byte[] error = jsonRpc.wrapJsonRpcError(code, message, requestId, data);
        exchange.getMessage().setBody(error);
        exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, A2AConstants.JSONRPC_CONTENT_TYPE);
    }

    // ---- JSON-RPC streaming ----

    private void handleJsonRpcStream(Exchange exchange, Map<String, Object> params, Object requestId) throws Exception {
        if (!isCapabilityEnabled(STREAMING_CAPABILITY)) {
            writeJsonRpcA2AError(exchange, "UnsupportedOperationError", STREAMING_DISABLED_MESSAGE, requestId);
            return;
        }

        // Parse and validate untrusted input BEFORE acquiring the permit to avoid leaking
        // a permit if convertValue or contextId resolution throws.
        SendMessageRequest request;
        try {
            request = OBJECT_MAPPER.convertValue(params, SendMessageRequest.class);
            validateSendMessageRequest(request);
            validateSendConfiguration(request.getConfiguration());
        } catch (IllegalArgumentException e) {
            writeJsonRpcError(exchange, JsonRpcProtocol.INVALID_PARAMS,
                    "Invalid params: " + e.getMessage(), requestId);
            return;
        }
        String contextId = contextIdOrNew(request.getMessage().contextId());

        if (!tryAcquirePermit()) {
            writeJsonRpcA2AError(exchange, "ServerBusyError",
                    "Agent at capacity: " + getEndpoint().getConfiguration().getMaxConcurrentTasks()
                                                              + " concurrent tasks",
                    requestId);
            return;
        }

        SseQueueInputStream inputStream = startMessageStream(
                request, contextId, jsonRpcStreamEncoder(requestId), exchange);
        setSseResponse(exchange, inputStream);
    }

    private void handleJsonRpcTaskSubscribe(Exchange exchange, Map<String, Object> params, Object requestId)
            throws Exception {
        if (!isCapabilityEnabled(STREAMING_CAPABILITY)) {
            writeJsonRpcA2AError(exchange, "UnsupportedOperationError", STREAMING_DISABLED_MESSAGE, requestId);
            return;
        }

        String taskId;
        try {
            taskId = requiredStringParam(params, "id");
        } catch (IllegalArgumentException e) {
            writeJsonRpcError(exchange, JsonRpcProtocol.INVALID_PARAMS, "Invalid params: " + e.getMessage(), requestId);
            return;
        }

        try {
            SseQueueInputStream inputStream = createTaskSubscription(taskId, jsonRpcStreamEncoder(requestId), exchange);
            setSseResponse(exchange, inputStream);
        } catch (TaskNotFoundException e) {
            writeJsonRpcA2AError(exchange, "TaskNotFoundError", e.getMessage(), requestId);
        } catch (AuthorizationException e) {
            writeJsonRpcA2AError(exchange, "AuthorizationError", e.getMessage(), requestId);
        } catch (IllegalStateException e) {
            writeJsonRpcA2AError(exchange, "UnsupportedOperationError", e.getMessage(), requestId);
        }
    }

    // ---- REST handler methods ----

    private <T> T readRestRequest(Exchange exchange, Class<T> type) throws RestRequestException {
        byte[] requestBody = readRestRequestBody(exchange);
        try {
            return OBJECT_MAPPER.readValue(requestBody, type);
        } catch (JsonProcessingException e) {
            throw new RestRequestException(
                    400, "InvalidParamsError",
                    "Malformed JSON request: " + e.getOriginalMessage());
        } catch (IOException e) {
            throw new RestRequestException(
                    400, "InvalidParamsError",
                    "Malformed JSON request: " + e.getMessage());
        }
    }

    private byte[] readRestRequestBody(Exchange exchange) throws RestRequestException {
        long maxSize = getEndpoint().getConfiguration().getMaxPayloadSize();
        byte[] requestBody;
        try {
            requestBody = readRequestBodyBytes(exchange, maxSize, "A2A REST request");
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("exceeds maximum size")) {
                throw new RestRequestException(413, "ContentTypeNotSupportedError", e.getMessage());
            }
            throw new RestRequestException(400, "InvalidParamsError", "Malformed request body: " + e.getMessage());
        }
        if (requestBody == null || requestBody.length == 0) {
            throw new RestRequestException(400, "ContentTypeNotSupportedError", "Request body is empty");
        }
        return requestBody;
    }

    private static byte[] readRequestBodyBytes(Exchange exchange, long maxSize, String description) throws IOException {
        Object body = exchange.getMessage().getBody();
        byte[] requestBody;
        if (body instanceof byte[] bytes) {
            requestBody = bytes;
        } else if (body instanceof InputStream inputStream) {
            requestBody = BoundedInputStreamReader.readAtMostAndClose(inputStream, maxSize, description);
        } else {
            String text = exchange.getMessage().getBody(String.class);
            requestBody = text != null ? text.getBytes(StandardCharsets.UTF_8) : null;
        }
        if (requestBody != null && requestBody.length > maxSize) {
            throw new IOException(description + " exceeds maximum size: " + maxSize + " bytes");
        }
        return requestBody;
    }

    void handleAgentCardRequest(Exchange exchange) throws Exception {
        filterInboundHeaders(exchange);
        AgentCard card = getEndpoint().getResolvedCard();
        writeJsonResponse(exchange, card);
    }

    void handleSendMessage(Exchange exchange) throws Exception {
        filterInboundHeaders(exchange);
        SendMessageRequest request;
        try {
            request = readRestRequest(exchange, SendMessageRequest.class);
            validateSendMessageRequest(request);
        } catch (RestRequestException e) {
            e.write(exchange);
            return;
        } catch (IllegalArgumentException e) {
            writeRestError(exchange, 400, "InvalidParamsError", e.getMessage());
            return;
        }

        SendMessageResponse response;
        try {
            response = processSendMessage(request, exchange);
        } catch (ServerBusyException e) {
            writeServerBusyError(exchange);
            return;
        } catch (TaskNotFoundException e) {
            writeRestError(exchange, 404, "TaskNotFoundError", e.getMessage());
            return;
        } catch (IllegalArgumentException e) {
            writeRestError(exchange, 400, "InvalidParamsError", e.getMessage());
            return;
        }

        writeJsonResponse(exchange, response);
    }

    void handleGetTask(Exchange exchange) throws Exception {
        String httpPath = exchange.getMessage().getHeader(Exchange.HTTP_PATH, String.class);
        filterInboundHeaders(exchange);
        String taskId = extractTaskIdFromPath(httpPath);

        try {
            Task task = processGetTask(taskId, exchange);
            writeJsonResponse(exchange, task);
        } catch (TaskNotFoundException e) {
            writeRestError(exchange, 404, "TaskNotFoundError", e.getMessage());
        }
    }

    void handleCancelTask(Exchange exchange) throws Exception {
        String httpPath = exchange.getMessage().getHeader(Exchange.HTTP_PATH, String.class);
        filterInboundHeaders(exchange);
        String taskId = extractTaskIdFromPath(httpPath);

        try {
            Task task = processCancelTask(taskId, exchange);
            writeJsonResponse(exchange, task);
        } catch (TaskNotFoundException e) {
            writeRestError(exchange, 404, "TaskNotFoundError", e.getMessage());
        } catch (IllegalStateException e) {
            writeRestError(exchange, 409, "TaskNotCancelableError", e.getMessage());
        }
    }

    void handleListTasks(Exchange exchange) throws Exception {
        TaskListRequest request = A2AListTasksSupport.fromQuery(exchange);
        filterInboundHeaders(exchange);

        try {
            ListTasksResponse response = processListTasks(request, exchange);
            writeJsonResponse(exchange, response);
        } catch (IllegalArgumentException e) {
            writeRestError(exchange, 400, "InvalidParamsError", e.getMessage());
        }
    }

    // ---- SSE streaming handlers ----

    void handleMessageStream(Exchange exchange) throws Exception {
        if (!isCapabilityEnabled(STREAMING_CAPABILITY)) {
            writeRestError(exchange, 405, "UnsupportedOperationError", STREAMING_DISABLED_MESSAGE);
            return;
        }
        filterInboundHeaders(exchange);
        SendMessageRequest request;
        try {
            request = readRestRequest(exchange, SendMessageRequest.class);
            validateSendMessageRequest(request);
        } catch (RestRequestException e) {
            e.write(exchange);
            return;
        } catch (IllegalArgumentException e) {
            writeRestError(exchange, 400, "InvalidParamsError", e.getMessage());
            return;
        }

        if (resolveReturnImmediately(request, exchange)) {
            writeRestError(exchange, 400, "UnsupportedOperationError",
                    "returnImmediately is not supported for streaming operations per A2A spec");
            return;
        }

        if (!tryAcquirePermit()) {
            writeServerBusyError(exchange);
            return;
        }

        SseQueueInputStream inputStream = startMessageStream(
                request, contextIdOrNew(request.getMessage().contextId()), null, exchange);
        setSseResponse(exchange, inputStream);
    }

    void handleTaskSubscribe(Exchange exchange) throws Exception {
        if (!isCapabilityEnabled(STREAMING_CAPABILITY)) {
            writeRestError(exchange, 405, "UnsupportedOperationError", STREAMING_DISABLED_MESSAGE);
            return;
        }

        String httpPath = exchange.getMessage().getHeader(Exchange.HTTP_PATH, String.class);
        filterInboundHeaders(exchange);
        String taskId = extractTaskIdFromPath(httpPath);
        A2ATaskStore store = getEndpoint().getTaskStore();

        Task task = store.get(taskId);
        if (task == null) {
            writeRestError(exchange, 404, "TaskNotFoundError", "Task not found: " + taskId);
            return;
        }

        try {
            SseQueueInputStream inputStream = createTaskSubscription(taskId, null, exchange);
            setSseResponse(exchange, inputStream);
        } catch (AuthorizationException e) {
            writeRestError(exchange, 403, "AuthorizationError", e.getMessage());
        } catch (IllegalStateException e) {
            writeRestError(exchange, 400, "UnsupportedOperationError", e.getMessage());
        }
    }

    private SseQueueInputStream createTaskSubscription(
            String taskId, Function<StreamResponse, String> encoder,
            Exchange exchange)
            throws TaskNotFoundException {
        A2ATaskStore store = getEndpoint().getTaskStore();

        Task task = store.get(taskId);
        if (task == null) {
            throw new TaskNotFoundException("Task not found: " + taskId);
        }
        assertTaskAccess(taskId, exchange);
        int queueCapacity = sseQueueCapacity();
        LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>(queueCapacity);
        QueueStreamEmitter emitter = createQueueStreamEmitter(taskId, task.contextId(), queue, encoder);
        SseQueueInputStream inputStream = createSseQueueInputStream(queue);

        A2ATaskSubscriber subscriber = new StreamSubscriber(emitter);
        store.addSubscriber(taskId, subscriber);

        closeSubscriptionOnStreamClose(inputStream, store, taskId, subscriber, emitter);

        Task latest = store.get(taskId);
        if (latest == null) {
            store.removeSubscriber(taskId, subscriber);
            emitter.close();
            throw new TaskNotFoundException("Task not found: " + taskId);
        }
        if (latest.status() != null && latest.status().state() != null && latest.status().state().isTerminal()) {
            store.removeSubscriber(taskId, subscriber);
            try {
                inputStream.close();
            } catch (IOException e) {
                LOG.debug("Failed to close terminal task subscription stream for task {}: {}", taskId, e.getMessage());
            }
            emitter.close();
            throw new IllegalStateException("Task is already terminal: " + latest.status().state());
        }
        emitter.emitTask(latest);
        return inputStream;
    }

    private SseQueueInputStream startMessageStream(
            SendMessageRequest request, String contextId, Function<StreamResponse, String> encoder,
            Exchange exchange)
            throws Exception {
        boolean submitted = false;
        SseQueueInputStream inputStream = null;
        String taskId = null;
        QueueStreamEmitter emitter = null;
        A2ATaskSubscriber subscriber = null;
        try {
            taskId = UUID.randomUUID().toString();

            A2ATaskStore store = getEndpoint().getTaskStore();
            Task submittedTask = newSubmittedTask(taskId, contextId);
            store.put(taskId, submittedTask);

            rememberTaskOwner(taskId, exchange);
            int queueCapacity = sseQueueCapacity();
            LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>(queueCapacity);
            emitter = createQueueStreamEmitter(taskId, contextId, queue, encoder);
            inputStream = createSseQueueInputStream(queue);

            subscriber = new StreamSubscriber(emitter);
            store.addSubscriber(taskId, subscriber);
            closeSubscriptionOnStreamClose(inputStream, store, taskId, subscriber, emitter);

            emitter.emitTask(submittedTask);
            submitStreamProcessing(taskId, contextId, request, emitter, exchange);
            submitted = true;
            return inputStream;
        } catch (Exception e) {
            cleanupTaskSubmissionFailure(taskId, subscriber, emitter, inputStream);
            throw e;
        } finally {
            if (!submitted) {
                releasePermit();
            }
        }
    }

    private Function<StreamResponse, String> jsonRpcStreamEncoder(Object requestId) {
        JsonRpcProtocol jsonRpc = (JsonRpcProtocol) getEndpoint().getProtocol();
        return response -> {
            byte[] envelope = jsonRpc.wrapJsonRpcResponse(response, requestId);
            return "data: " + new String(envelope, StandardCharsets.UTF_8) + "\n\n";
        };
    }

    private SseQueueInputStream createSseQueueInputStream(LinkedBlockingQueue<String> queue) {
        return new SseQueueInputStream(
                queue,
                getEndpoint().getConfiguration().getSseHeartbeatInterval());
    }

    private static QueueStreamEmitter createQueueStreamEmitter(
            String taskId, String contextId, LinkedBlockingQueue<String> queue,
            Function<StreamResponse, String> encoder) {
        return encoder != null
                ? new QueueStreamEmitter(taskId, contextId, queue, encoder)
                : new QueueStreamEmitter(taskId, contextId, queue);
    }

    private static void closeSubscriptionOnStreamClose(
            SseQueueInputStream inputStream, A2ATaskStore store, String taskId,
            A2ATaskSubscriber subscriber, QueueStreamEmitter emitter) {
        inputStream.setOnClose(() -> {
            store.removeSubscriber(taskId, subscriber);
            emitter.close();
        });
    }

    private static void setSseResponse(Exchange exchange, SseQueueInputStream inputStream) {
        exchange.getExchangeExtension().setStreamCacheDisabled(true);
        exchange.getMessage().setBody(inputStream);
        exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, A2AConstants.SSE_CONTENT_TYPE);
        exchange.getMessage().setHeader(CACHE_CONTROL_HEADER, NO_CACHE);
        exchange.getMessage().setHeader(CONNECTION_HEADER, KEEP_ALIVE);
    }

    // ---- Shared processing logic (used by both REST and JSON-RPC paths) ----

    private SendMessageResponse processSendMessage(SendMessageRequest request, Exchange exchange) throws Exception {
        validateSendMessageRequest(request);
        validateSendConfiguration(request.getConfiguration());

        Exchange processorExchange = getEndpoint().createExchange();
        processorExchange.setProperty(A2AConstants.USER_PROFILE, exchange.getProperty(A2AConstants.USER_PROFILE));
        copyNegotiatedExtensions(exchange, processorExchange);
        messageSendOperation.parseRequest(processorExchange, request,
                getEndpoint().getConfiguration().getDataFormat());

        String taskId = request.getMessage().taskId();
        String contextId = request.getMessage().contextId();

        if (taskId != null && !taskId.isEmpty()) {
            A2ATaskStore store = getEndpoint().getTaskStore();
            Task existing = store.get(taskId);
            if (existing == null) {
                throw new TaskNotFoundException("Task not found: " + taskId);
            }
            assertTaskAccess(taskId, exchange);
            if (contextId == null || contextId.isEmpty()) {
                contextId = existing.contextId();
            }
        } else {
            taskId = UUID.randomUUID().toString();
        }
        rememberTaskOwner(taskId, exchange);

        contextId = contextIdOrNew(contextId);

        processorExchange.getMessage().setHeader(A2AConstants.TASK_ID, taskId);
        processorExchange.getMessage().setHeader(A2AConstants.CONTEXT_ID, contextId);

        if (resolveReturnImmediately(request, exchange)) {
            return processSendMessageAsync(processorExchange, taskId, contextId);
        }

        if (!tryAcquirePermit()) {
            throw new ServerBusyException(
                    "Agent at capacity: "
                                          + getEndpoint().getConfiguration().getMaxConcurrentTasks() + " concurrent tasks");
        }
        try {
            invokeExtensionHandlersBeforeRoute(processorExchange);
            getProcessor().process(processorExchange);
            if (processorExchange.getException() != null) {
                throw processorExchange.getException();
            }
            invokeExtensionHandlersAfterRoute(processorExchange);
        } finally {
            releasePermit();
        }

        SendMessageResponse response = applySendResponseConfiguration(
                (SendMessageResponse) messageSendOperation.buildResponse(processorExchange),
                request.getConfiguration());

        if (response.isTaskResponse()) {
            Task task = response.getTask();
            A2ATaskStore store = getEndpoint().getTaskStore();
            store.put(task.id(), task);
            if (task.status() != null) {
                store.notifySubscribers(task.id(),
                        StreamResponse.ofStatusUpdate(buildStatusEvent(task)));
            }
        }

        return response;
    }

    private SendMessageResponse processSendMessageAsync(
            Exchange processorExchange, String taskId, String contextId) {
        A2ATaskStore store = getEndpoint().getTaskStore();

        Task submittedTask = newSubmittedTask(taskId, contextId);

        if (tryAcquirePermit()) {
            try {
                store.put(taskId, submittedTask);
                store.notifySubscribers(taskId, StreamResponse.ofStatusUpdate(buildStatusEvent(submittedTask)));
                submitAsyncTask(taskId, contextId, processorExchange);
            } catch (Exception e) {
                cleanupTaskSubmissionFailure(taskId, null, null, null);
                releasePermit();
                throw e;
            }
        } else if (pendingTaskQueue != null
                && pendingTaskQueue.offer(new PendingTask(taskId, contextId, processorExchange))) {
            store.put(taskId, submittedTask);
            store.notifySubscribers(taskId, StreamResponse.ofStatusUpdate(buildStatusEvent(submittedTask)));
            LOG.debug("Task {} queued (queue size: {})", taskId, pendingTaskQueue.size());
        } else {
            throw new ServerBusyException(
                    "Agent at capacity: "
                                          + getEndpoint().getConfiguration().getMaxConcurrentTasks() + " concurrent tasks");
        }

        Task responseTask = newSubmittedTask(taskId, contextId);

        SendMessageResponse response = new SendMessageResponse();
        response.setTask(responseTask);
        return response;
    }

    private void submitAsyncTask(String taskId, String contextId, Exchange processorExchange) {
        A2ATaskStore store = getEndpoint().getTaskStore();

        FutureTask<Void> future = new FutureTask<>(() -> {
            try {
                Task workingTask = store.get(taskId);
                if (workingTask != null) {
                    store.updateStatusAndNotify(taskId, new TaskStatus(TaskState.WORKING));
                }
                invokeExtensionHandlersBeforeRoute(processorExchange);
                getProcessor().process(processorExchange);
                if (processorExchange.getException() != null) {
                    throw processorExchange.getException();
                }
                invokeExtensionHandlersAfterRoute(processorExchange);
                SendMessageResponse asyncResponse
                        = (SendMessageResponse) messageSendOperation.buildResponse(processorExchange);
                if (asyncResponse.isTaskResponse()) {
                    Task completed = Task.builder(asyncResponse.getTask())
                            .id(taskId)
                            .contextId(contextId)
                            .build();
                    store.put(taskId, completed);
                    store.notifySubscribers(taskId, StreamResponse.ofStatusUpdate(buildStatusEvent(completed)));
                }
            } catch (Exception e) {
                LOG.error("Async task processing failed for taskId={}", taskId, e);
                store.updateStatusAndNotify(taskId, new TaskStatus(TaskState.FAILED));
            } finally {
                boolean weOwnPermit = inFlightTasks.remove(taskId) != null;
                ScheduledFuture<?> timeoutFuture = timeoutFutures.remove(taskId);
                if (timeoutFuture != null) {
                    timeoutFuture.cancel(false);
                }
                if (weOwnPermit) {
                    releasePermit();
                }
            }
            return null;
        });
        inFlightTasks.put(taskId, future);
        ScheduledFuture<?> timeoutFuture = null;
        try {
            long asyncTimeout = getEndpoint().getConfiguration().getAsyncTimeout();
            if (asyncTimeout > 0) {
                timeoutFuture = asyncTimeoutScheduler.schedule(() -> {
                    timeoutFutures.remove(taskId);
                    Future<?> f = inFlightTasks.remove(taskId);
                    if (f != null && !f.isDone()) {
                        f.cancel(true);
                        store.updateStatusAndNotify(taskId, new TaskStatus(TaskState.FAILED));
                        releasePermit();
                        LOG.warn("Async task timed out after {}ms for taskId={}", asyncTimeout, taskId);
                    }
                }, asyncTimeout, TimeUnit.MILLISECONDS);
                timeoutFutures.put(taskId, timeoutFuture);
            }
            asyncExecutor.execute(future);
        } catch (RuntimeException e) {
            inFlightTasks.remove(taskId, future);
            future.cancel(true);
            if (timeoutFuture != null) {
                timeoutFutures.remove(taskId, timeoutFuture);
                timeoutFuture.cancel(false);
            }
            throw e;
        }
    }

    private boolean resolveReturnImmediately(SendMessageRequest request, Exchange exchange) {
        SendMessageConfiguration configuration = request.getConfiguration();
        if (configuration != null && configuration.getReturnImmediately() != null) {
            return configuration.getReturnImmediately();
        }
        if (configuration != null && configuration.getBlocking() != null) {
            return !configuration.getBlocking();
        }
        Boolean header = exchange.getMessage().getHeader(A2AConstants.RETURN_IMMEDIATELY, Boolean.class);
        if (header != null) {
            return header;
        }
        return getEndpoint().getConfiguration().isReturnImmediately();
    }

    private static String contextIdOrNew(String contextId) {
        return contextId == null || contextId.isEmpty() ? UUID.randomUUID().toString() : contextId;
    }

    private static Task newSubmittedTask(String taskId, String contextId) {
        return Task.builder()
                .id(taskId)
                .contextId(contextId)
                .status(new TaskStatus(TaskState.SUBMITTED))
                .build();
    }

    private static void validateSendConfiguration(SendMessageConfiguration configuration) {
        if (configuration != null
                && configuration.getHistoryLength() != null
                && configuration.getHistoryLength() < 0) {
            throw new IllegalArgumentException("configuration.historyLength must be greater than or equal to zero");
        }
    }

    private static void validateSendMessageRequest(SendMessageRequest request) {
        if (request == null || request.getMessage() == null) {
            throw new IllegalArgumentException("message is required");
        }
        if (request.getMessage().role() == null) {
            throw new IllegalArgumentException("message.role is required");
        }
        if (request.getMessage().parts() == null || request.getMessage().parts().isEmpty()) {
            throw new IllegalArgumentException("message.parts must contain at least one part");
        }
    }

    private static void validatePushConfig(TaskPushNotificationConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("push notification config is required");
        }
        if (config.getUrl() == null || config.getUrl().isBlank()) {
            throw new IllegalArgumentException("url is required");
        }
    }

    private static void requireParams(Map<String, Object> params, String method) {
        if (params == null) {
            throw new IllegalArgumentException(method + " params are required");
        }
    }

    private static String requiredStringParam(Map<String, Object> params, String name) {
        if (params == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        Object value = params.get(name);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return text;
    }

    private static SendMessageResponse applySendResponseConfiguration(
            SendMessageResponse response, SendMessageConfiguration configuration) {
        if (response == null || configuration == null || configuration.getHistoryLength() == null
                || !response.isTaskResponse()) {
            return response;
        }

        Task task = response.getTask();
        if (task.history() != null) {
            int fromIndex = Math.max(0, task.history().size() - configuration.getHistoryLength());
            response.setTask(Task.builder(task)
                    .history(task.history().subList(fromIndex, task.history().size()))
                    .build());
        }
        return response;
    }

    private Task processGetTask(String taskId, Exchange exchange) throws TaskNotFoundException {
        A2ATaskStore store = getEndpoint().getTaskStore();
        Task task = store.get(taskId);
        if (task == null) {
            forgetTaskOwner(taskId);
            throw new TaskNotFoundException("Task not found: " + taskId);
        }
        assertTaskAccess(taskId, exchange);
        return task;
    }

    private Task processCancelTask(String taskId, Exchange exchange) throws TaskNotFoundException {
        A2ATaskStore store = getEndpoint().getTaskStore();
        Task existing = store.get(taskId);
        if (existing == null) {
            throw new TaskNotFoundException("Task not found: " + taskId);
        }
        assertTaskAccess(taskId, exchange);
        Task task = store.cancelIfNotTerminal(taskId);
        if (task == null) {
            throw new TaskNotFoundException("Task not found: " + taskId);
        }
        Future<?> inFlight = inFlightTasks.remove(taskId);
        if (inFlight != null) {
            inFlight.cancel(true);
            ScheduledFuture<?> timeoutFuture = timeoutFutures.remove(taskId);
            if (timeoutFuture != null) {
                timeoutFuture.cancel(false);
            }
            releasePermit();
        }
        if (pendingTaskQueue != null) {
            pendingTaskQueue.removeIf(pending -> taskId.equals(pending.taskId()));
        }
        store.notifySubscribers(taskId,
                StreamResponse.ofStatusUpdate(buildStatusEvent(task)));
        return task;
    }

    private void submitStreamProcessing(
            String taskId, String contextId, SendMessageRequest request,
            QueueStreamEmitter emitter, Exchange requestExchange) {
        Exchange processorExchange = getEndpoint().createExchange();
        processorExchange.setProperty(A2AConstants.USER_PROFILE, requestExchange.getProperty(A2AConstants.USER_PROFILE));
        copyNegotiatedExtensions(requestExchange, processorExchange);
        A2ADataFormat dataFormat = getEndpoint().getConfiguration().getDataFormat();
        if (dataFormat == A2ADataFormat.POJO) {
            processorExchange.getMessage().setBody(request.getMessage());
        } else if (dataFormat == A2ADataFormat.RAW) {
            try {
                processorExchange.getMessage().setBody(
                        A2AJsonMapper.instance().writeValueAsString(request.getMessage()));
            } catch (Exception e) {
                throw new RuntimeCamelException("Failed to serialize Message to JSON", e);
            }
        } else {
            // PAYLOAD (default)
            processorExchange.getMessage().setBody(A2ATypeConverters.messageToString(request.getMessage()));
        }
        processorExchange.getMessage().setHeader(A2AConstants.TASK_ID, taskId);
        processorExchange.getMessage().setHeader(A2AConstants.CONTEXT_ID, contextId);
        processorExchange.getMessage().setHeader(A2AConstants.OPERATION, A2AOperations.MESSAGE_STREAM.getMethodName());
        processorExchange.getMessage().setHeader(A2AConstants.STREAM_EMITTER, emitter);

        A2ATaskStore store = getEndpoint().getTaskStore();
        FutureTask<Void> future = new FutureTask<>(() -> {
            try {
                invokeExtensionHandlersBeforeRoute(processorExchange);
                getProcessor().process(processorExchange);
                if (processorExchange.getException() != null) {
                    throw processorExchange.getException();
                }
                invokeExtensionHandlersAfterRoute(processorExchange);
                SendMessageResponse streamResponse
                        = (SendMessageResponse) messageSendOperation.buildResponse(processorExchange);
                if (streamResponse.isTaskResponse()) {
                    Task task = streamResponse.getTask();
                    if (task.history() != null && !task.history().isEmpty()) {
                        Message agentMessage = task.history().get(task.history().size() - 1);
                        A2AProgress.emitMessage(processorExchange, agentMessage);
                    }
                }
                store.updateStatusAndNotify(taskId, new TaskStatus(TaskState.COMPLETED));
            } catch (Exception e) {
                LOG.error("Stream processing failed for task {}: {}", taskId, e.getMessage(), e);
                store.updateStatusAndNotify(taskId, new TaskStatus(TaskState.FAILED));
            } finally {
                emitter.close();
                boolean weOwnPermit = inFlightTasks.remove(taskId) != null;
                ScheduledFuture<?> timeoutFuture = timeoutFutures.remove(taskId);
                if (timeoutFuture != null) {
                    timeoutFuture.cancel(false);
                }
                if (weOwnPermit) {
                    releasePermit();
                }
            }
            return null;
        });
        inFlightTasks.put(taskId, future);
        ScheduledFuture<?> timeoutFuture = null;
        try {
            long asyncTimeout = getEndpoint().getConfiguration().getAsyncTimeout();
            if (asyncTimeout > 0) {
                timeoutFuture = asyncTimeoutScheduler.schedule(() -> {
                    timeoutFutures.remove(taskId);
                    Future<?> f = inFlightTasks.remove(taskId);
                    if (f != null && !f.isDone()) {
                        f.cancel(true);
                        store.updateStatusAndNotify(taskId, new TaskStatus(TaskState.FAILED));
                        emitter.close();
                        releasePermit();
                        LOG.warn("Streaming task timed out after {}ms for taskId={}", asyncTimeout, taskId);
                    }
                }, asyncTimeout, TimeUnit.MILLISECONDS);
                timeoutFutures.put(taskId, timeoutFuture);
            }
            asyncExecutor.execute(future);
        } catch (RuntimeException e) {
            inFlightTasks.remove(taskId, future);
            future.cancel(true);
            if (timeoutFuture != null) {
                timeoutFutures.remove(taskId, timeoutFuture);
                timeoutFuture.cancel(false);
            }
            throw e;
        }
    }

    private ListTasksResponse processListTasks(TaskListRequest request, Exchange exchange) {
        cleanupTaskOwners();
        String owner = resolveTaskOwner(exchange);
        return A2AListTasksSupport.process(
                request, getEndpoint().getTaskStore(), owner, taskId -> canAccessTask(taskId, owner));
    }

    // ---- Push notification config handlers ----

    void handlePushConfigCreate(Exchange exchange) throws Exception {
        if (!isCapabilityEnabled(PUSH_NOTIFICATIONS_CAPABILITY)) {
            writeRestError(exchange, 405, "UnsupportedOperationError", PUSH_NOTIFICATIONS_DISABLED_MESSAGE);
            return;
        }

        String httpPath = exchange.getMessage().getHeader(Exchange.HTTP_PATH, String.class);
        filterInboundHeaders(exchange);
        String taskId = extractTaskIdFromPath(httpPath);

        TaskPushNotificationConfig config;
        try {
            config = readRestRequest(exchange, TaskPushNotificationConfig.class);
            validatePushConfig(config);
        } catch (RestRequestException e) {
            e.write(exchange);
            return;
        } catch (IllegalArgumentException e) {
            writeRestError(exchange, 400, "InvalidParamsError", e.getMessage());
            return;
        }

        try {
            config = processPushConfigCreate(taskId, config, exchange);
            writeJsonResponse(exchange, config);
        } catch (TaskNotFoundException e) {
            writeRestError(exchange, 404, "TaskNotFoundError", e.getMessage());
        } catch (IllegalArgumentException e) {
            writeRestError(exchange, 400, "InvalidParamsError", e.getMessage());
        }
    }

    void handlePushConfigGet(Exchange exchange) throws Exception {
        if (!isCapabilityEnabled(PUSH_NOTIFICATIONS_CAPABILITY)) {
            writeRestError(exchange, 405, "UnsupportedOperationError", PUSH_NOTIFICATIONS_DISABLED_MESSAGE);
            return;
        }

        String httpPath = exchange.getMessage().getHeader(Exchange.HTTP_PATH, String.class);
        filterInboundHeaders(exchange);
        String[] ids = extractPushConfigIds(httpPath);
        A2ATaskStore store = getEndpoint().getTaskStore();

        try {
            assertTaskAccess(ids[0], exchange);
        } catch (AuthorizationException e) {
            writeRestError(exchange, 403, "AuthorizationError", e.getMessage());
            return;
        }

        TaskPushNotificationConfig config = store.getPushConfig(ids[0], ids[1]);
        if (config == null) {
            writeRestError(exchange, 404, "TaskNotFoundError", "Push config not found: " + ids[1]);
            return;
        }

        writeJsonResponse(exchange, config);
    }

    void handlePushConfigList(Exchange exchange) throws Exception {
        if (!isCapabilityEnabled(PUSH_NOTIFICATIONS_CAPABILITY)) {
            writeRestError(exchange, 405, "UnsupportedOperationError", PUSH_NOTIFICATIONS_DISABLED_MESSAGE);
            return;
        }

        String httpPath = exchange.getMessage().getHeader(Exchange.HTTP_PATH, String.class);
        filterInboundHeaders(exchange);
        String taskId = extractTaskIdFromPath(httpPath);
        A2ATaskStore store = getEndpoint().getTaskStore();

        try {
            assertTaskAccess(taskId, exchange);
        } catch (AuthorizationException e) {
            writeRestError(exchange, 403, "AuthorizationError", e.getMessage());
            return;
        }

        List<TaskPushNotificationConfig> configs = store.listPushConfigs(taskId);
        ListPushNotificationConfigsResponse response = new ListPushNotificationConfigsResponse(configs);
        writeJsonResponse(exchange, response);
    }

    void handlePushConfigDelete(Exchange exchange) throws Exception {
        if (!isCapabilityEnabled(PUSH_NOTIFICATIONS_CAPABILITY)) {
            writeRestError(exchange, 405, "UnsupportedOperationError", PUSH_NOTIFICATIONS_DISABLED_MESSAGE);
            return;
        }

        String httpPath = exchange.getMessage().getHeader(Exchange.HTTP_PATH, String.class);
        filterInboundHeaders(exchange);
        String[] ids = extractPushConfigIds(httpPath);
        A2ATaskStore store = getEndpoint().getTaskStore();

        try {
            assertTaskAccess(ids[0], exchange);
        } catch (AuthorizationException e) {
            writeRestError(exchange, 403, "AuthorizationError", e.getMessage());
            return;
        }

        boolean deleted = store.deletePushConfig(ids[0], ids[1]);
        if (!deleted) {
            writeRestError(exchange, 404, "TaskNotFoundError", "Push config not found: " + ids[1]);
            return;
        }

        writeJsonResponse(exchange, Collections.emptyMap());
    }

    private TaskPushNotificationConfig processPushConfigCreate(
            String taskId, TaskPushNotificationConfig config,
            Exchange exchange)
            throws TaskNotFoundException {
        ensureCapabilityEnabled(PUSH_NOTIFICATIONS_CAPABILITY, PUSH_NOTIFICATIONS_DISABLED_MESSAGE);
        A2ATaskStore store = getEndpoint().getTaskStore();
        if (!store.contains(taskId)) {
            forgetTaskOwner(taskId);
            throw new TaskNotFoundException("Task not found: " + taskId);
        }
        assertTaskAccess(taskId, exchange);
        WebhookUrlValidator.validate(config.getUrl(), getEndpoint().getConfiguration().isAllowLocalWebhookUrls());
        store.putPushConfig(taskId, config);
        return config;
    }

    private TaskPushNotificationConfig processPushConfigGet(String taskId, String configId, Exchange exchange)
            throws TaskNotFoundException {
        ensureCapabilityEnabled(PUSH_NOTIFICATIONS_CAPABILITY, PUSH_NOTIFICATIONS_DISABLED_MESSAGE);
        assertTaskAccess(taskId, exchange);
        A2ATaskStore store = getEndpoint().getTaskStore();
        TaskPushNotificationConfig config = store.getPushConfig(taskId, configId);
        if (config == null) {
            throw new TaskNotFoundException("Push config not found: " + configId);
        }
        return config;
    }

    private List<TaskPushNotificationConfig> processPushConfigList(String taskId, Exchange exchange) {
        ensureCapabilityEnabled(PUSH_NOTIFICATIONS_CAPABILITY, PUSH_NOTIFICATIONS_DISABLED_MESSAGE);
        assertTaskAccess(taskId, exchange);
        A2ATaskStore store = getEndpoint().getTaskStore();
        return store.listPushConfigs(taskId);
    }

    private Object processPushConfigDelete(String taskId, String configId, Exchange exchange) throws TaskNotFoundException {
        ensureCapabilityEnabled(PUSH_NOTIFICATIONS_CAPABILITY, PUSH_NOTIFICATIONS_DISABLED_MESSAGE);
        assertTaskAccess(taskId, exchange);
        A2ATaskStore store = getEndpoint().getTaskStore();
        if (!store.deletePushConfig(taskId, configId)) {
            throw new TaskNotFoundException("Push config not found: " + configId);
        }
        return Collections.emptyMap();
    }

    // ---- Utilities ----

    private static TaskStatusUpdateEvent buildStatusEvent(Task task) {
        return TaskStatusUpdateEvent.builder()
                .taskId(task.id())
                .contextId(task.contextId())
                .status(task.status())
                .build();
    }

    private int sseQueueCapacity() {
        return Math.max(2, getEndpoint().getConfiguration().getSseQueueCapacity());
    }

    private void filterInboundHeaders(Exchange exchange) {
        exchange.getMessage().getHeaders().entrySet().removeIf(entry -> {
            String lower = entry.getKey().toLowerCase(Locale.ENGLISH);
            return lower.startsWith("camel") || lower.startsWith("org.apache.camel.");
        });
    }

    private static void copyNegotiatedExtensions(Exchange source, Exchange target) {
        List<String> extensions = negotiatedExtensions(source);
        if (!extensions.isEmpty()) {
            target.setProperty(A2AConstants.EXTENSIONS, extensions);
            target.getMessage().setHeader(A2AConstants.EXTENSIONS, extensions);
        }
        List<AgentExtension> declarations = negotiatedExtensionDeclarations(source);
        if (!declarations.isEmpty()) {
            target.setProperty(A2AConstants.EXTENSION_DECLARATIONS, declarations);
        }
    }

    private void invokeExtensionHandlersBeforeRoute(Exchange exchange) throws Exception {
        invokeExtensionHandlers(exchange, true);
    }

    private void invokeExtensionHandlersAfterRoute(Exchange exchange) throws Exception {
        invokeExtensionHandlers(exchange, false);
    }

    private void invokeExtensionHandlers(Exchange exchange, boolean beforeRoute) throws Exception {
        List<AgentExtension> extensions = negotiatedExtensionDeclarations(exchange);
        if (extensions.isEmpty()) {
            return;
        }
        Map<String, A2AExtensionHandler> handlers = getEndpoint().getExtensionHandlers();
        for (AgentExtension extension : extensions) {
            A2AExtensionHandler handler = handlers.get(extension.getUri());
            if (handler != null) {
                if (beforeRoute) {
                    handler.beforeRoute(exchange, extension);
                } else {
                    handler.afterRoute(exchange, extension);
                }
            }
        }
    }

    private void rememberTaskOwner(String taskId, Exchange exchange) {
        cleanupTaskOwners();
        String owner = resolveTaskOwner(exchange);
        if (owner != null) {
            taskOwners.putIfAbsent(taskId, owner);
        }
    }

    private void forgetTaskOwner(String taskId) {
        if (taskId != null) {
            taskOwners.remove(taskId);
        }
    }

    private void cleanupTaskOwners() {
        if (!taskOwners.isEmpty()) {
            A2ATaskStore store = getEndpoint().getTaskStore();
            taskOwners.keySet().removeIf(taskId -> !store.contains(taskId));
        }
    }

    private void cleanupTaskSubmissionFailure(
            String taskId, A2ATaskSubscriber subscriber, QueueStreamEmitter emitter, SseQueueInputStream inputStream) {
        if (taskId == null) {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    LOG.debug("Failed to close rejected stream without task id: {}", e.getMessage());
                }
            } else if (emitter != null) {
                emitter.close();
            }
            return;
        }
        A2ATaskStore store = getEndpoint().getTaskStore();
        Future<?> future = inFlightTasks.remove(taskId);
        if (future != null) {
            future.cancel(true);
        }
        ScheduledFuture<?> timeoutFuture = timeoutFutures.remove(taskId);
        if (timeoutFuture != null) {
            timeoutFuture.cancel(false);
        }
        if (subscriber != null) {
            store.removeSubscriber(taskId, subscriber);
        }
        if (inputStream != null) {
            try {
                inputStream.close();
            } catch (IOException e) {
                LOG.debug("Failed to close rejected stream for task {}: {}", taskId, e.getMessage());
            }
        } else if (emitter != null) {
            emitter.close();
        }
        store.delete(taskId);
        forgetTaskOwner(taskId);
    }

    private void assertTaskAccess(String taskId, Exchange exchange) {
        String owner = resolveTaskOwner(exchange);
        if (!canAccessTask(taskId, owner)) {
            throw new AuthorizationException("Not authorized for task: " + taskId);
        }
    }

    private boolean canAccessTask(String taskId, String owner) {
        if (owner == null) {
            return true;
        }
        return owner.equals(taskOwners.get(taskId));
    }

    private static String resolveTaskOwner(Exchange exchange) {
        Object profile = exchange.getProperty(A2AConstants.USER_PROFILE);
        if (profile instanceof A2AUserProfile typedProfile) {
            return typedProfile.ownerKey();
        }
        if (profile instanceof Map<?, ?> profileMap && !profileMap.isEmpty()) {
            return A2AUserProfile.fromMap(toProfileMap(profileMap)).ownerKey();
        }
        return null;
    }

    private static Map<String, Object> toProfileMap(Map<?, ?> profile) {
        Map<String, Object> answer = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : profile.entrySet()) {
            if (entry.getKey() != null) {
                answer.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return answer;
    }

    private String extractTaskIdFromPath(String path) {
        return A2AHttpPathSupport.extractTaskId(path, getEndpoint().getConfiguration().getBasePath());
    }

    private String[] extractPushConfigIds(String path) {
        return A2AHttpPathSupport.extractPushConfigIds(path, getEndpoint().getConfiguration().getBasePath());
    }

    private String resolveWwwAuthenticate() {
        AgentCard card = getEndpoint().getResolvedCard();
        if (card != null && card.getSecuritySchemes() != null) {
            for (SecurityScheme scheme : card.getSecuritySchemes().values()) {
                String type = scheme.getType();
                if ("http".equals(type) || "oauth2".equals(type) || "openIdConnect".equals(type)) {
                    return "Bearer realm=\"a2a\"";
                }
                if ("apiKey".equals(type)) {
                    return "ApiKey";
                }
            }
        }
        return "Bearer realm=\"a2a\"";
    }

    private boolean isCapabilityEnabled(String capability) {
        AgentCard card = getEndpoint().getResolvedCard();
        if (card == null || card.getCapabilities() == null) {
            return false;
        }
        return switch (capability) {
            case STREAMING_CAPABILITY -> card.getCapabilities().isStreaming();
            case PUSH_NOTIFICATIONS_CAPABILITY -> card.getCapabilities().isPushNotifications();
            default -> true;
        };
    }

    private void ensureCapabilityEnabled(String capability, String message) {
        if (!isCapabilityEnabled(capability)) {
            throw new UnsupportedOperationException(message);
        }
    }

    @FunctionalInterface
    interface A2ARequestHandler {
        void handle(Exchange exchange) throws Exception;
    }

    private static class TaskNotFoundException extends Exception {
        TaskNotFoundException(String message) {
            super(message);
        }
    }

    private static class AuthorizationException extends RuntimeException {
        AuthorizationException(String message) {
            super(message);
        }
    }

    private static class UnsupportedExtensionException extends RuntimeException {
        UnsupportedExtensionException(String message) {
            super(message);
        }
    }

    private static class RestRequestException extends Exception {
        private final int statusCode;
        private final String code;

        RestRequestException(int statusCode, String code, String message) {
            super(message);
            this.statusCode = statusCode;
            this.code = code;
        }

        void write(Exchange exchange) throws Exception {
            writeRestError(exchange, statusCode, code, getMessage());
        }
    }
}
