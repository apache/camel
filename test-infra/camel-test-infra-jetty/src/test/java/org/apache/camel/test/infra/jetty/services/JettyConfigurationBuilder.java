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

import java.util.function.Consumer;

import javax.net.ssl.SSLContext;

import org.apache.camel.util.KeyValueHolder;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.servlet.ServletContextHandler;

/**
 * This builder can be used to build and configure a configuration holder for embedded Jetty instances
 */
public final class JettyConfigurationBuilder {

    private interface ConfigurationBuilderDelegate {
        JettyConfigurationBuilder build();
    }

    public static class ServletConfigurationBuilder implements ConfigurationBuilderDelegate {
        private final JettyConfiguration jettyConfiguration;
        private final JettyConfigurationBuilder jettyConfigurationBuilder;

        private JettyConfiguration.ServletHandlerConfiguration servletHandlerConfiguration;

        public ServletConfigurationBuilder(JettyConfigurationBuilder builder, JettyConfiguration jettyConfiguration) {
            this.jettyConfigurationBuilder = builder;
            this.jettyConfiguration = jettyConfiguration;
            servletHandlerConfiguration
                    = new JettyConfiguration.ServletHandlerConfiguration(jettyConfiguration.getContextPath());
        }

        public ServletConfigurationBuilder customize(Consumer<ServletContextHandler> customizer) {
            servletHandlerConfiguration.customize(customizer);
            return this;
        }

        public ServletConfigurationBuilder addBasicAuthUser(String username, String password, String realm) {
            servletHandlerConfiguration.addBasicAuthUser(username, password, realm);
            return this;
        }

        public ServletConfigurationBuilder addBasicAuthUser(KeyValueHolder<String, String> userInfo) {
            servletHandlerConfiguration.addBasicAuthUser(userInfo);
            return this;
        }

        public ServletConfigurationBuilder addServletConfiguration(
                JettyConfiguration.ServletHandlerConfiguration.ServletConfiguration<?> servletConfiguration) {
            servletHandlerConfiguration.addServletConfiguration(servletConfiguration);
            return this;
        }

        @Override
        public JettyConfigurationBuilder build() {
            jettyConfiguration.setContextHandlerConfiguration(servletHandlerConfiguration);
            return jettyConfigurationBuilder;
        }
    }

    public static class WebSocketConfigurationBuilder implements ConfigurationBuilderDelegate {
        private final JettyConfiguration jettyConfiguration;
        private final JettyConfigurationBuilder jettyConfigurationBuilder;

        private JettyConfiguration.WebSocketContextHandlerConfiguration wsHandlerConfiguration;

        public WebSocketConfigurationBuilder(JettyConfigurationBuilder builder, JettyConfiguration jettyConfiguration) {
            this.jettyConfigurationBuilder = builder;
            this.jettyConfiguration = jettyConfiguration;
            wsHandlerConfiguration
                    = new JettyConfiguration.WebSocketContextHandlerConfiguration(jettyConfiguration.getContextPath());
        }

        @Override
        public JettyConfigurationBuilder build() {
            jettyConfiguration.setContextHandlerConfiguration(wsHandlerConfiguration);
            return jettyConfigurationBuilder;
        }

        public WebSocketConfigurationBuilder addServletConfiguration(
                JettyConfiguration.ServletHandlerConfiguration.ServletConfiguration<?> servletConfiguration) {
            wsHandlerConfiguration.addServletConfiguration(servletConfiguration);
            return this;
        }
    }

    public static class WebAppContextConfigurationBuilder implements ConfigurationBuilderDelegate {
        private final JettyConfiguration jettyConfiguration;
        private final JettyConfigurationBuilder jettyConfigurationBuilder;

        private final JettyConfiguration.WebContextConfiguration webContextConfiguration;

        public WebAppContextConfigurationBuilder(JettyConfigurationBuilder jettyConfigurationBuilder,
                                                 JettyConfiguration jettyConfiguration) {
            this.jettyConfigurationBuilder = jettyConfigurationBuilder;
            this.jettyConfiguration = jettyConfiguration;
            this.webContextConfiguration = new JettyConfiguration.WebContextConfiguration(jettyConfiguration.getContextPath());
        }

        public WebAppContextConfigurationBuilder withWebApp(String webApp) {
            webContextConfiguration.setWebApp(webApp);
            return this;
        }

        @Override
        public JettyConfigurationBuilder build() {
            jettyConfiguration.setContextHandlerConfiguration(webContextConfiguration);
            return jettyConfigurationBuilder;
        }
    }

    public static class ContextHandlerConfigurationBuilder implements ConfigurationBuilderDelegate {
        private final JettyConfiguration jettyConfiguration;
        private final JettyConfigurationBuilder jettyConfigurationBuilder;

        private final JettyConfiguration.ContextHandlerConfiguration contextHandlerConfiguration;
        private Consumer<ContextHandler> contextHandlerCustomizer;

        public ContextHandlerConfigurationBuilder(JettyConfigurationBuilder jettyConfigurationBuilder,
                                                  JettyConfiguration jettyConfiguration) {
            this.jettyConfiguration = jettyConfiguration;
            this.jettyConfigurationBuilder = jettyConfigurationBuilder;
            contextHandlerConfiguration
                    = new JettyConfiguration.ContextHandlerConfiguration(jettyConfiguration.getContextPath());
        }

        public ContextHandlerConfigurationBuilder withErrorHandler(ErrorHandler errorHandler) {
            contextHandlerConfiguration.setErrorHandler(errorHandler);
            return this;
        }

        public ContextHandlerConfigurationBuilder withHandler(Handler handler) {
            contextHandlerConfiguration.setHandler(handler);
            return this;
        }

        public ContextHandlerConfigurationBuilder withCustomizer(Consumer<ContextHandler> contextHandlerCustomizer) {
            contextHandlerConfiguration.customize(contextHandlerCustomizer);
            return this;
        }

        @Override
        public JettyConfigurationBuilder build() {
            jettyConfiguration.setContextHandlerConfiguration(contextHandlerConfiguration);
            return jettyConfigurationBuilder;
        }
    }

    public static class HandlerContextConfigurationBuilder implements ConfigurationBuilderDelegate {
        private final JettyConfiguration jettyConfiguration;
        private final JettyConfigurationBuilder jettyConfigurationBuilder;
        private final JettyConfiguration.HandlerCollectionConfiguration handlerCollectionConfiguration;

        public HandlerContextConfigurationBuilder(JettyConfigurationBuilder jettyConfigurationBuilder,
                                                  JettyConfiguration jettyConfiguration) {
            this.jettyConfiguration = jettyConfiguration;
            this.jettyConfigurationBuilder = jettyConfigurationBuilder;
            handlerCollectionConfiguration
                    = new JettyConfiguration.HandlerCollectionConfiguration(jettyConfiguration.getContextPath());
        }

        public HandlerContextConfigurationBuilder addHandlers(Handler handler) {
            handlerCollectionConfiguration.addHandlers(handler);
            return this;
        }

        public HandlerContextConfigurationBuilder withCustomizer(Consumer<HandlerCollection> contextHandlerCustomizer) {
            handlerCollectionConfiguration.customize(contextHandlerCustomizer);
            return this;
        }

        @Override
        public JettyConfigurationBuilder build() {
            jettyConfiguration.setContextHandlerConfiguration(handlerCollectionConfiguration);
            return jettyConfigurationBuilder;
        }
    }

    private JettyConfiguration jettyConfiguration = new JettyConfiguration();

    private JettyConfigurationBuilder() {
    }

    public JettyConfigurationBuilder withPort(int port) {
        jettyConfiguration.setPort(port);
        return this;
    }

    public JettyConfigurationBuilder withSslContext(SSLContext sslContext) {
        jettyConfiguration.setSslContext(sslContext);
        return this;
    }

    public ServletConfigurationBuilder withServletConfiguration() {
        return new ServletConfigurationBuilder(this, jettyConfiguration);
    }

    public WebSocketConfigurationBuilder withWebSocketConfiguration() {
        return new WebSocketConfigurationBuilder(this, jettyConfiguration);
    }

    public WebAppContextConfigurationBuilder withWebAppContextConfiguration() {
        return new WebAppContextConfigurationBuilder(this, jettyConfiguration);
    }

    public ContextHandlerConfigurationBuilder withContextHandlerConfiguration() {
        return new ContextHandlerConfigurationBuilder(this, jettyConfiguration);
    }

    public HandlerContextConfigurationBuilder withHandlerCollectionConfiguration() {
        return new HandlerContextConfigurationBuilder(this, jettyConfiguration);
    }

    public JettyConfigurationBuilder withContextPath(String contextPath) {
        jettyConfiguration.setContextPath(contextPath);
        return this;
    }

    public JettyConfiguration build() {
        return jettyConfiguration;
    }

    public static JettyConfigurationBuilder emptyTemplate() {
        return new JettyConfigurationBuilder();
    }

    public static JettyConfigurationBuilder bareTemplate() {
        // Setups a very basic Jetty server with a randomly allocated port
        return emptyTemplate()
                .withPort(0)
                .withContextPath(JettyConfiguration.ROOT_CONTEXT_PATH);
    }

    public static JettyConfiguration bare() {
        // Setups a very basic Jetty server with a randomly allocated port
        return bareTemplate().build();
    }
}
