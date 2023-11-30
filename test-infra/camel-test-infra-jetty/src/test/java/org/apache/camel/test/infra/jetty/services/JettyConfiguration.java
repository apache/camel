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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import jakarta.servlet.Servlet;

import javax.net.ssl.SSLContext;

import org.apache.camel.util.KeyValueHolder;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.ee10.servlet.security.ConstraintMapping;
import org.eclipse.jetty.ee10.servlet.security.ConstraintSecurityHandler;
import org.eclipse.jetty.ee10.webapp.WebAppContext;
import org.eclipse.jetty.ee10.websocket.jakarta.server.config.JakartaWebSocketServletContainerInitializer;
import org.eclipse.jetty.security.Constraint;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.security.SecurityHandler;
import org.eclipse.jetty.security.UserStore;
import org.eclipse.jetty.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.eclipse.jetty.util.security.Credential;

/**
 * A configuration holder for embedded Jetty instances
 */
public class JettyConfiguration {
    public static final String ROOT_CONTEXT_PATH = "/";

    public abstract static class AbstractContextHandlerConfiguration<T> {

        protected final String contextPath;
        protected Consumer<T> customizer;

        public AbstractContextHandlerConfiguration(String contextPath) {
            this.contextPath = contextPath;
        }

        public String getContextPath() {
            return contextPath;
        }

        public void customize(Consumer<T> customizer) {
            this.customizer = customizer;
        }

        abstract T resolve();
    }

    public static class ContextHandlerConfiguration extends AbstractContextHandlerConfiguration<ContextHandler> {
        private final ContextHandler contextHandler;

        public ContextHandlerConfiguration(String contextPath) {
            super(contextPath);

            contextHandler = new ContextHandler(contextPath);
        }

        public void setErrorHandler(ErrorHandler errorHandler) {
            contextHandler.setErrorHandler(errorHandler);
        }

        public void setHandler(Handler handler) {
            contextHandler.setHandler(handler);
        }

        @Override
        ContextHandler resolve() {
            if (customizer != null) {
                customizer.accept(contextHandler);
            }

            return contextHandler;
        }
    }

    public static class WebContextConfiguration extends AbstractContextHandlerConfiguration<WebAppContext> {
        private String webApp;

        public WebContextConfiguration(String contextPath) {
            super(contextPath);
        }

        public void setWebApp(String webApp) {
            this.webApp = webApp;
        }

        public WebAppContext resolve() {
            final WebAppContext webAppContext = new WebAppContext(webApp, super.getContextPath());

            if (customizer != null) {
                customizer.accept(webAppContext);
            }

            return webAppContext;
        }
    }

    public static class ServletHandlerConfiguration extends AbstractContextHandlerConfiguration<ServletContextHandler> {
        /**
         * A configuration holder for Jetty servlet holders
         *
         * @param <T>
         */
        public static class ServletConfiguration<T> {
            public static final String ROOT_PATH_SPEC = "/*";

            private final T servlet;
            private final String pathSpec;
            private Map<String, String> initParameters = new HashMap<>();
            private String name;

            public ServletConfiguration(T servlet, String pathSpec) {
                this.servlet = servlet;
                this.pathSpec = pathSpec;
                this.name = null;
            }

            public ServletConfiguration(T servlet, String pathSpec, String name) {
                this.servlet = servlet;
                this.pathSpec = pathSpec;
                this.name = name;
            }

            public T getServlet() {
                return servlet;
            }

            public String getPathSpec() {
                return pathSpec;
            }

            public void addInitParameter(String param, String value) {
                initParameters.put(param, value);
            }

            public Map<String, String> getInitParameters() {
                return Collections.unmodifiableMap(initParameters);
            }

            public ServletHolder buildServletHolder() {
                ServletHolder servletHolder = resolveServletHolder();

                if (!initParameters.isEmpty()) {
                    servletHolder.setInitParameters(initParameters);
                }

                return servletHolder;
            }

            public String getName() {
                return name;
            }

            private ServletHolder resolveServletHolder() {
                if (servlet instanceof ServletHolder) {
                    return (ServletHolder) servlet;
                }

                ServletHolder servletHolder = new ServletHolder();

                if (name != null) {
                    servletHolder.setName(name);
                }

                if (servlet instanceof String) {
                    servletHolder.setClassName((String) servlet);
                } else {
                    if (servlet instanceof Servlet) {
                        servletHolder.setServlet((Servlet) servlet);
                    } else {
                        throw new IllegalArgumentException(
                                "Unknown servlet type: " + (servlet == null ? "null" : servlet.getClass()));
                    }
                }

                return servletHolder;
            }
        }

        private String realm;
        private List<KeyValueHolder<String, String>> userInfos = new ArrayList<>();
        private List<ServletConfiguration<?>> servletConfigurations = new ArrayList<>();

        public ServletHandlerConfiguration(String contextPath) {
            super(contextPath);
        }

        public void addBasicAuthUser(String username, String password, String realm) {
            this.realm = Objects.requireNonNull(realm);
            addBasicAuthUser(new KeyValueHolder<>(username, password));
        }

        public void addBasicAuthUser(KeyValueHolder<String, String> userInfo) {
            userInfos.add(userInfo);
        }

        public List<KeyValueHolder<String, String>> getBasicUsers() {
            return Collections.unmodifiableList(userInfos);
        }

        public String getRealm() {
            return realm;
        }

        void addServletConfiguration(ServletConfiguration<?> servletConfiguration) {
            servletConfigurations.add(servletConfiguration);
        }

        public List<ServletConfiguration<?>> getServletConfigurations() {
            return Collections.unmodifiableList(servletConfigurations);
        }

        private SecurityHandler basicAuth(List<KeyValueHolder<String, String>> userInfoList, String realm) {

            HashLoginService l = new HashLoginService();
            UserStore us = new UserStore();

            for (var userInfo : userInfoList) {
                // In order: data1 == username, data2 == password
                us.addUser(userInfo.getKey(), Credential.getCredential(userInfo.getValue()), new String[] { "user" });

            }

            l.setName(realm);
            l.setUserStore(us);

            Constraint.Builder constraintBuilder = new Constraint.Builder();
            constraintBuilder.name("BASIC");
            constraintBuilder.roles("user");
            constraintBuilder.authorization(Constraint.Authorization.SPECIFIC_ROLE);

            ConstraintMapping cm = new ConstraintMapping();
            cm.setConstraint(constraintBuilder.build());
            cm.setPathSpec(ServletConfiguration.ROOT_PATH_SPEC);

            ConstraintSecurityHandler csh = new ConstraintSecurityHandler();
            csh.setAuthenticator(new BasicAuthenticator());
            csh.setRealmName("myrealm");
            csh.addConstraintMapping(cm);
            csh.setLoginService(l);

            return csh;
        }

        @Override
        ServletContextHandler resolve() {
            ServletContextHandler contextHandler = new ServletContextHandler(ServletContextHandler.SESSIONS);

            if (!userInfos.isEmpty()) {
                contextHandler.setSecurityHandler(basicAuth(userInfos, realm));
            }

            contextHandler.setContextPath(super.getContextPath());

            for (ServletConfiguration servletConfiguration : servletConfigurations) {
                contextHandler.addServlet(servletConfiguration.buildServletHolder(), servletConfiguration.getPathSpec());
            }

            if (customizer != null) {
                customizer.accept(contextHandler);
            }

            return contextHandler;
        }
    }

    public static class HandlerCollectionConfiguration extends AbstractContextHandlerConfiguration<ContextHandlerCollection> {
        private final ContextHandlerCollection handlers = new ContextHandlerCollection();

        public HandlerCollectionConfiguration(String contextPath) {
            super(contextPath);
        }

        public void addHandlers(Handler handler) {
            handlers.addHandler(handler);
        }

        public ContextHandlerCollection resolve() {
            return handlers;
        }
    }

    public static class WebSocketContextHandlerConfiguration extends ServletHandlerConfiguration {

        public WebSocketContextHandlerConfiguration(String contextPath) {
            super(contextPath);
        }

        @Override
        public List<ServletConfiguration<?>> getServletConfigurations() {
            return super.getServletConfigurations();
        }

        @Override
        ServletContextHandler resolve() {
            ServletContextHandler contextHandler = new ServletContextHandler(ServletContextHandler.SESSIONS);

            if (!super.userInfos.isEmpty()) {
                contextHandler.setSecurityHandler(super.basicAuth(super.userInfos, super.realm));
            }

            contextHandler.setContextPath(super.getContextPath());
            contextHandler.setAttribute(contextHandler.getClass().getName(), contextHandler);

            for (ServletConfiguration servletConfiguration : super.servletConfigurations) {
                contextHandler.addServlet(servletConfiguration.buildServletHolder(), servletConfiguration.getPathSpec());
            }

            if (customizer != null) {
                customizer.accept(contextHandler);
            }

            JakartaWebSocketServletContainerInitializer.configure(contextHandler, null);
            return contextHandler;
        }
    }

    private int port;
    private SSLContext sslContext;

    private String contextPath;
    private AbstractContextHandlerConfiguration<? extends Handler> contextHandlerConfiguration;

    private WebContextConfiguration webContextConfiguration;

    public int getPort() {
        return port;
    }

    void setPort(int port) {
        this.port = port;
    }

    public SSLContext getSslContext() {
        return sslContext;
    }

    void setSslContext(SSLContext sslContext) {
        this.sslContext = sslContext;
    }

    public String getContextPath() {
        return contextPath;
    }

    public void setContextPath(String contextPath) {
        this.contextPath = contextPath;
    }

    public void setContextHandlerConfiguration(
            AbstractContextHandlerConfiguration<? extends Handler> contextHandlerConfiguration) {
        this.contextHandlerConfiguration = contextHandlerConfiguration;
    }

    public WebContextConfiguration getWebContextConfiguration() {
        return webContextConfiguration;
    }

    void setWebContextConfiguration(WebContextConfiguration webContextConfiguration) {
        this.webContextConfiguration = webContextConfiguration;
    }

    public AbstractContextHandlerConfiguration<? extends Handler> getContextHandlerConfiguration() {
        return contextHandlerConfiguration;
    }
}
