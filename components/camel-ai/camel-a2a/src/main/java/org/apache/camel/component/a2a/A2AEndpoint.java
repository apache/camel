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

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.a2a.auth.A2AAuthHandler;
import org.apache.camel.component.a2a.auth.A2ASecuritySchemeHandler;
import org.apache.camel.component.a2a.auth.ApiKeySchemeHandler;
import org.apache.camel.component.a2a.auth.HttpBearerSchemeHandler;
import org.apache.camel.component.a2a.auth.OAuth2SchemeHandler;
import org.apache.camel.component.a2a.auth.OpenIdConnectSchemeHandler;
import org.apache.camel.component.a2a.card.AgentCardLoader;
import org.apache.camel.component.a2a.card.AgentCardResolver;
import org.apache.camel.component.a2a.extension.A2AExtensionHandler;
import org.apache.camel.component.a2a.model.AgentCard;
import org.apache.camel.component.a2a.protocol.A2AProtocol;
import org.apache.camel.component.a2a.protocol.JsonRpcProtocol;
import org.apache.camel.component.a2a.protocol.RestProtocol;
import org.apache.camel.component.a2a.push.PushNotificationDispatcher;
import org.apache.camel.component.a2a.push.PushNotificationSubscriber;
import org.apache.camel.component.a2a.state.A2ATaskStore;
import org.apache.camel.component.a2a.state.GuardedTaskStore;
import org.apache.camel.component.a2a.state.InMemoryTaskStore;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.service.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A2A endpoint for agent-to-agent communication.
 */
@UriEndpoint(
             firstVersion = "4.21.0",
             scheme = A2AConstants.SCHEME,
             title = "A2A",
             syntax = "a2a:agentCardSource",
             category = { Category.AI },
             headersClass = A2AConstants.class)
public class A2AEndpoint extends DefaultEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(A2AEndpoint.class);

    @UriPath
    @Metadata(required = true, description = "The agent card source (classpath:, file:, http://, https://, or plain name)")
    private String agentCardSource;

    @UriParam
    private A2AConfiguration configuration;

    private final AtomicReference<AgentCard> resolvedCard = new AtomicReference<>();
    private A2AProtocol protocol;
    private A2ATaskStore taskStore;
    private boolean taskStoreOwned;
    private A2AAuthHandler authHandler;
    private PushNotificationDispatcher pushDispatcher;
    private ScheduledExecutorService pushDispatcherExecutor;
    private ExecutorService httpClientExecutor;
    private HttpClient httpClient;
    private Map<String, A2AExtensionHandler> extensionHandlers = Map.of();
    private boolean producerCreated;
    private boolean consumerCreated;

    public A2AEndpoint(String uri, A2AComponent component, A2AConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        producerCreated = true;
        return new A2AProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        consumerCreated = true;
        A2AConsumer consumer = new A2AConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    void configureNestedConsumer(Consumer consumer) throws Exception {
        configureConsumer(consumer);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        configuration.validate();

        try {
            httpClientExecutor = getCamelContext().getExecutorServiceManager()
                    .newThreadPool(this, "A2AHttpClient", 1, 4);

            // 1. Load card from file/classpath/URL
            AgentCardLoader loader = new AgentCardLoader(
                    createHttpClient(HttpClient.Redirect.NEVER),
                    Duration.ofMillis(configuration.getConnectTimeout()));
            AgentCard fileCard = loader.load(agentCardSource);

            // 2. Resolve card with layered precedence: file < bean < URI params
            AgentCard resolved = AgentCardResolver.resolve(
                    fileCard,
                    configuration.getAgentCard(),
                    agentCardSource,
                    configuration.getName(),
                    configuration.getDescription(),
                    configuration.getVersion());

            resolvedCard.set(resolved);

            // 3. Create protocol
            // TODO: Consider a registry-based ProtocolFactory when a third protocol binding is added.
            String binding = configuration.getProtocolBinding();
            if (A2AConstants.PROTOCOL_REST.equalsIgnoreCase(binding)) {
                protocol = new RestProtocol();
            } else if (A2AConstants.PROTOCOL_JSONRPC.equalsIgnoreCase(binding)) {
                protocol = new JsonRpcProtocol();
            } else {
                throw new IllegalArgumentException("Unsupported protocol binding: " + binding);
            }

            boolean roleKnown = producerCreated || consumerCreated;
            boolean initializeConsumerServices = consumerCreated || !roleKnown;

            // 4. Discover or create task store for consumer-side operation serving
            if (initializeConsumerServices) {
                taskStore = getCamelContext().getRegistry().findSingleByType(A2ATaskStore.class);
                if (taskStore != null) {
                    taskStore = new GuardedTaskStore(taskStore, configuration.isAllowLocalWebhookUrls());
                    taskStoreOwned = false;
                } else {
                    LOG.debug("No A2ATaskStore found in registry, creating InMemoryTaskStore");
                    InMemoryTaskStore memStore = new InMemoryTaskStore();
                    memStore.setCompletedTaskTtlMs(configuration.getCompletedTaskTtl());
                    memStore.setAllowLocalWebhookUrls(configuration.isAllowLocalWebhookUrls());
                    taskStore = memStore;
                    taskStoreOwned = true;
                }
            }

            // 5. Create shared HTTP client for producers
            HttpClient.Redirect redirectPolicy = configuration.isFollowRedirects()
                    ? HttpClient.Redirect.NORMAL
                    : HttpClient.Redirect.NEVER;
            httpClient = createHttpClient(redirectPolicy);

            // 6. Create push notification dispatcher and wire to store for consumers
            if (initializeConsumerServices) {
                pushDispatcherExecutor = getCamelContext().getExecutorServiceManager()
                        .newScheduledThreadPool(this, "A2APushDispatcher", 4);
                pushDispatcher = new PushNotificationDispatcher(
                        createHttpClient(HttpClient.Redirect.NEVER), taskStore,
                        configuration.getPushRetryAttempts(),
                        configuration.getPushRetryBackoffMs(),
                        pushDispatcherExecutor,
                        configuration.isAllowLocalWebhookUrls());
                taskStore.addGlobalSubscriber(new PushNotificationSubscriber(pushDispatcher));
            }

            // 7. Discover security scheme handlers and create auth handler
            Map<String, A2ASecuritySchemeHandler> schemeHandlers = discoverSchemeHandlers();
            authHandler = new A2AAuthHandler(configuration, schemeHandlers);

            // 8. Discover protocol extension handlers
            extensionHandlers = discoverExtensionHandlers();
        } catch (Exception e) {
            cleanupEndpointResources();
            throw e;
        }
    }

    @Override
    protected void doStop() throws Exception {
        cleanupEndpointResources();
        super.doStop();
    }

    HttpClient getHttpClient() {
        return httpClient;
    }

    public String getAgentCardSource() {
        return agentCardSource;
    }

    public void setAgentCardSource(String agentCardSource) {
        this.agentCardSource = agentCardSource;
    }

    A2AConfiguration getConfiguration() {
        return configuration;
    }

    public AgentCard getResolvedCard() {
        return resolvedCard.get();
    }

    A2AProtocol getProtocol() {
        return protocol;
    }

    A2ATaskStore getTaskStore() {
        return taskStore;
    }

    A2AAuthHandler getAuthHandler() {
        return authHandler;
    }

    Map<String, A2AExtensionHandler> getExtensionHandlers() {
        return extensionHandlers;
    }

    private HttpClient createHttpClient(HttpClient.Redirect redirectPolicy) {
        return HttpClient.newBuilder()
                .executor(httpClientExecutor)
                .followRedirects(redirectPolicy)
                .connectTimeout(Duration.ofMillis(configuration.getConnectTimeout()))
                .build();
    }

    private void cleanupEndpointResources() {
        if (taskStoreOwned && taskStore != null) {
            try {
                ServiceHelper.stopService(taskStore);
            } catch (Exception e) {
                LOG.debug("Error stopping A2A task store: {}", e.getMessage());
            }
        }
        if (pushDispatcher != null) {
            pushDispatcher.shutdown();
        }
        pushDispatcher = null;
        if (pushDispatcherExecutor != null) {
            getCamelContext().getExecutorServiceManager().shutdownNow(pushDispatcherExecutor);
            pushDispatcherExecutor = null;
        }
        if (httpClientExecutor != null) {
            getCamelContext().getExecutorServiceManager().shutdownGraceful(httpClientExecutor);
            httpClientExecutor = null;
        }
        httpClient = null;
        protocol = null;
        authHandler = null;
        extensionHandlers = Map.of();
    }

    private Map<String, A2ASecuritySchemeHandler> discoverSchemeHandlers() {
        Map<String, A2ASecuritySchemeHandler> handlerMap = new LinkedHashMap<>();

        // Default handlers
        handlerMap.put("http", new HttpBearerSchemeHandler());
        handlerMap.put("apiKey", new ApiKeySchemeHandler());
        handlerMap.put("oauth2", new OAuth2SchemeHandler());
        handlerMap.put("openIdConnect", new OpenIdConnectSchemeHandler());

        // User-registered handlers override defaults
        Set<A2ASecuritySchemeHandler> discovered = getCamelContext().getRegistry()
                .findByType(A2ASecuritySchemeHandler.class);
        for (A2ASecuritySchemeHandler handler : discovered) {
            handlerMap.put(handler.schemeType(), handler);
            LOG.info("Registered custom A2ASecuritySchemeHandler for scheme type: {}", handler.schemeType());
        }

        return handlerMap;
    }

    private Map<String, A2AExtensionHandler> discoverExtensionHandlers() {
        Map<String, A2AExtensionHandler> handlerMap = new LinkedHashMap<>();
        Set<A2AExtensionHandler> discovered = getCamelContext().getRegistry()
                .findByType(A2AExtensionHandler.class);
        for (A2AExtensionHandler handler : discovered) {
            String uri = handler.extensionUri();
            if (uri != null && !uri.isBlank()) {
                handlerMap.put(uri, handler);
                LOG.debug("Registered custom A2AExtensionHandler for extension URI: {}", uri);
            }
        }
        return handlerMap;
    }
}
