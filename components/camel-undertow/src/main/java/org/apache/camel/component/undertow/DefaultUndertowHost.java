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
package org.apache.camel.component.undertow;

import java.net.URI;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import jakarta.servlet.ServletException;

import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import io.undertow.server.HttpHandler;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import org.apache.camel.component.undertow.handlers.CamelRootHandler;
import org.apache.camel.component.undertow.handlers.NotFoundHandler;
import org.apache.camel.component.undertow.handlers.RestRootHandler;
import org.apache.camel.component.undertow.spi.UndertowSecurityProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The default UndertowHost which manages standalone Undertow server.
 */
public class DefaultUndertowHost implements UndertowHost {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultUndertowHost.class);

    private final Lock lock = new ReentrantLock();
    private final UndertowHostKey key;
    private final UndertowHostOptions options;
    private final CamelRootHandler rootHandler;
    private final RestRootHandler restHandler;
    private Undertow undertow;
    private String hostString;
    private DeploymentManager deploymentManager;
    // the rest or the root handler, depending on the first endpoint registered on the server
    private HttpHandler serverHandler;
    // the server handler, or the servlet deployment around it once an endpoint needs a servlet context
    private volatile HttpHandler entryHandler;

    public DefaultUndertowHost(UndertowHostKey key) {
        this(key, null);
    }

    public DefaultUndertowHost(UndertowHostKey key, UndertowHostOptions options) {
        this.key = key;
        this.options = options;
        this.rootHandler = new CamelRootHandler(new NotFoundHandler());
        this.restHandler = new RestRootHandler();
        this.restHandler.init(key.getPort());
    }

    @Override
    public void validateEndpointURI(URI httpURI) {
        // all URIs are good
    }

    @Override
    public HttpHandler registerHandler(
            UndertowConsumer consumer, HttpHandlerRegistrationInfo registrationInfo, HttpHandler handler) {
        return registerHandler(consumer != null ? consumer.getEndpoint() : null, consumer, registrationInfo, handler);
    }

    @Override
    public HttpHandler registerHandler(
            UndertowEndpoint endpoint, UndertowConsumer consumer, HttpHandlerRegistrationInfo registrationInfo,
            HttpHandler handler) {
        lock.lock();
        try {
            if (undertow == null) {
                Undertow.Builder builder = Undertow.builder();
                if (key.getSslContext() != null) {
                    builder.addHttpsListener(key.getPort(), key.getHost(), key.getSslContext());
                } else {
                    builder.addHttpListener(key.getPort(), key.getHost());
                }

                if (options != null) {
                    if (options.getIoThreads() != null) {
                        builder.setIoThreads(options.getIoThreads());
                    }
                    if (options.getWorkerThreads() != null) {
                        builder.setWorkerThreads(options.getWorkerThreads());
                    }
                    if (options.getBufferSize() != null) {
                        builder.setBufferSize(options.getBufferSize());
                    }
                    if (options.getDirectBuffers() != null) {
                        builder.setDirectBuffers(options.getDirectBuffers());
                    }
                    if (options.getHttp2Enabled() != null) {
                        builder.setServerOption(UndertowOptions.ENABLE_HTTP2, options.getHttp2Enabled());
                    }
                    if (options.getMaxEntitySize() != null) {
                        builder.setServerOption(UndertowOptions.MAX_ENTITY_SIZE, options.getMaxEntitySize());
                    }
                    if (options.getMultipartMaxEntitySize() != null) {
                        builder.setServerOption(UndertowOptions.MULTIPART_MAX_ENTITY_SIZE, options.getMultipartMaxEntitySize());
                    }
                    if (options.getMaxHeaderSize() != null) {
                        builder.setServerOption(UndertowOptions.MAX_HEADER_SIZE, options.getMaxHeaderSize());
                    }
                    if (options.getNoRequestTimeout() != null) {
                        builder.setServerOption(UndertowOptions.NO_REQUEST_TIMEOUT, options.getNoRequestTimeout());
                    }
                    if (options.getIdleTimeout() != null) {
                        builder.setServerOption(UndertowOptions.IDLE_TIMEOUT, options.getIdleTimeout());
                    }
                    if (options.getRequestParseTimeout() != null) {
                        builder.setServerOption(UndertowOptions.REQUEST_PARSE_TIMEOUT, options.getRequestParseTimeout());
                    }
                    if (options.getMaxParameters() != null) {
                        builder.setServerOption(UndertowOptions.MAX_PARAMETERS, options.getMaxParameters());
                    }
                    if (options.getMaxHeaders() != null) {
                        builder.setServerOption(UndertowOptions.MAX_HEADERS, options.getMaxHeaders());
                    }
                }

                // use the rest handler as its a rest consumer
                serverHandler = consumer != null && consumer.isRest() ? restHandler : rootHandler;
                entryHandler = serverHandler;
                if (requiresServletContext(endpoint)) {
                    // deploy before the server starts, so that a failure leaves no server running
                    deployServletContext();
                }
                undertow = builder.setHandler(exchange -> entryHandler.handleRequest(exchange)).build();
                LOG.info("Starting Undertow server on {}://{}:{}", key.getSslContext() != null ? "https" : "http",
                        key.getHost(),
                        key.getPort());

                try {
                    // If there is an exception while starting up, Undertow wraps it
                    // as RuntimeException which leaves the consumer in an inconsistent
                    // state as a subsequent start if the route (i.e. manually) won't
                    // start the Undertow instance as undertow is not null.
                    undertow.start();
                } catch (RuntimeException e) {
                    LOG.warn("Failed to start Undertow server on {}://{}:{}, reason: {}",
                            key.getSslContext() != null ? "https" : "http", key.getHost(), key.getPort(), e.getMessage());

                    // Cleanup any resource that may have been created during start
                    // and reset the instance so a subsequent start will trigger the
                    // initialization again.
                    undertow.stop();
                    undertow = null;
                    if (deploymentManager != null) {
                        deploymentManager.undeploy();
                        deploymentManager = null;
                    }

                    throw e;
                }
            }
            if (deploymentManager == null && requiresServletContext(endpoint)) {
                // a later endpoint needs a servlet context: wrap the handler of the running server
                deployServletContext();
            }
            if (consumer != null && consumer.isRest()) {
                restHandler.addConsumer(consumer, handler);
                return restHandler;
            } else {
                return rootHandler.add(registrationInfo.getUri().getPath(), registrationInfo.getMethodRestrict(),
                        registrationInfo.isMatchOnUriPrefix(), handler);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Whether the security provider of the endpoint, or of its component, needs a servlet context, for example to run
     * servlet filters. A producer endpoint can register first on a server, such as a WebSocket producer.
     */
    private static boolean requiresServletContext(UndertowEndpoint endpoint) {
        if (endpoint == null) {
            return false;
        }
        UndertowSecurityProvider endpointProvider = endpoint.getSecurityProvider();
        UndertowSecurityProvider componentProvider = endpoint.getComponent().getSecurityProvider();
        return endpointProvider != null && endpointProvider.requireServletContext()
                || componentProvider != null && componentProvider.requireServletContext();
    }

    /**
     * Starts an empty servlet deployment around the handler of the server, so that every request has a servlet context.
     */
    private void deployServletContext() {
        HttpHandler handler = serverHandler;
        DeploymentInfo deployment = Servlets.deployment()
                .setContextPath("")
                .setDisplayName("application")
                .setDeploymentName("camel-undertow")
                .setClassLoader(getClass().getClassLoader())
                //httpHandler for servlet is ignored, camel handler is used instead of it
                .addOuterHandlerChainWrapper(h -> handler);

        DeploymentManager manager = Servlets.newContainer().addDeployment(deployment);
        manager.deploy();
        try {
            entryHandler = manager.start();
        } catch (ServletException e) {
            LOG.warn("Failed to start the servlet context of the Undertow server on {}://{}:{}, reason: {}",
                    key.getSslContext() != null ? "https" : "http", key.getHost(), key.getPort(), e.getMessage());
            manager.undeploy();
            throw new RuntimeException(e);
        }
        deploymentManager = manager;
    }

    @Override
    public void unregisterHandler(UndertowConsumer consumer, HttpHandlerRegistrationInfo registrationInfo) {
        lock.lock();
        try {
            if (undertow == null) {
                return;
            }

            boolean stop;
            if (consumer != null && consumer.isRest()) {
                restHandler.removeConsumer(consumer);
                stop = restHandler.consumers() <= 0;
            } else {
                rootHandler.remove(registrationInfo.getUri().getPath(), registrationInfo.getMethodRestrict(),
                        registrationInfo.isMatchOnUriPrefix());
                stop = rootHandler.isEmpty();
            }
            if (stop) {
                // the servlet deployment serves every endpoint of the server, so it can only go with the server
                if (deploymentManager != null) {
                    deploymentManager.undeploy();
                    deploymentManager = null;
                }
                LOG.info("Stopping Undertow server on {}://{}:{}", key.getSslContext() != null ? "https" : "http",
                        key.getHost(),
                        key.getPort());
                undertow.stop();
                undertow = null;
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public String toString() {
        if (hostString == null) {
            hostString = String.format("DefaultUndertowHost[%s://%s:%s]", key.getSslContext() != null ? "https" : "http",
                    key.getHost(), key.getPort());
        }
        return hostString;
    }
}
