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

import java.util.function.Supplier;

import javax.net.ssl.SSLContext;

/**
 * This builder can be used to build and configure a configuration holder for embedded Jetty instances
 */
public final class JettyConfigurationBuilder {
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

    public JettyConfigurationBuilder withSslContext(Supplier<SSLContext> contextSupplier) {
        return withSslContext(contextSupplier::get);
    }

    public JettyConfigurationBuilder addServletConfiguration(JettyConfiguration.ServletConfiguration servletConfiguration) {
        jettyConfiguration.addServletConfiguration(servletConfiguration);

        return this;
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

    public static JettyConfiguration bare() {
        // Setups a very basic Jetty server with a randomly allocated port
        return emptyTemplate()
                .withPort(0)
                .build();
    }
}
