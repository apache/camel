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

package org.apache.camel.test.infra.jetty.services;

import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;

import org.apache.camel.test.infra.jetty.common.JettyProperties;
import org.awaitility.Awaitility;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * An embedded Jetty service that can be used to run servlets for testing purposes
 */
public class JettyEmbeddedService implements JettyService, BeforeEachCallback, AfterEachCallback {

    private final JettyConfiguration jettyConfiguration;
    private ServerConnector connector;
    private Server server;

    /**
     * Builds an instance of the service using the provided configuration
     * 
     * @param jettyConfiguration the configuration to use when building the service
     */
    public JettyEmbeddedService(JettyConfiguration jettyConfiguration) {
        this.jettyConfiguration = jettyConfiguration;
    }

    private ServerConnector createConnector(JettyConfiguration jettyConfiguration) {
        ServerConnector connector;
        SSLContext sslContext = jettyConfiguration.getSslContext();
        if (sslContext != null) {
            connector = new ServerConnector(server, createSslConnectionFactory(sslContext));
        } else {
            connector = new ServerConnector(server);
        }

        return connector;
    }

    @NotNull
    private static SslConnectionFactory createSslConnectionFactory(SSLContext sslContext) {
        try {
            SslContextFactory sslContextFactory = createSslContextFactory(sslContext);
            sslContextFactory.setSslContext(sslContext);
            return SslConnectionFactory.class.getConstructor(sslContextFactory.getClass(), String.class)
                    .newInstance(sslContextFactory, null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @NotNull
    private static SslContextFactory createSslContextFactory(SSLContext sslContext) throws Exception {
        Class<?> factoryClass;
        if (Server.getVersion().startsWith("9")) {
            // Jetty 9 detected
            factoryClass = Class.forName("org.eclipse.jetty.util.ssl.SslContextFactory");
        } else {
            factoryClass = Class.forName("org.eclipse.jetty.util.ssl.SslContextFactory$Server");
        }
        return (SslContextFactory) factoryClass.getConstructor().newInstance();
    }

    @Override
    public void registerProperties() {
        System.setProperty(JettyProperties.JETTY_ADDRESS, "localhost:" + getPort());
    }

    private void doInitialize() {
        try {
            server = new Server(jettyConfiguration.getPort());

            connector = createConnector(jettyConfiguration);

            server.addConnector(connector);

            Handler contextHandler = jettyConfiguration.getContextHandlerConfiguration().resolve();

            server.setHandler(contextHandler);

            server.start();
            Awaitility.await().atMost(10, TimeUnit.SECONDS).until(server::isStarted);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void initialize() {
        if (server == null || server.isStopped()) {
            doInitialize();
        }
    }

    @Override
    public void shutdown() {
        if (server != null && server.isStarted()) {
            doShutdown();
        }
    }

    private synchronized void doShutdown() {
        try {
            stop();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (server.isStopped()) {
                server.destroy();
            }

            server = null;
        }
    }

    public void stop() throws Exception {
        server.stop();

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(server::isStopped);
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) {
        shutdown();
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) {
        initialize();
    }

    @Override
    public int getPort() {
        return jettyConfiguration.getPort();
    }
}
