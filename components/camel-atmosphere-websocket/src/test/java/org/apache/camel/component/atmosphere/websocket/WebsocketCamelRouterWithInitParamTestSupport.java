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
package org.apache.camel.component.atmosphere.websocket;

import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.infra.jetty.services.JettyConfiguration;
import org.apache.camel.test.infra.jetty.services.JettyConfigurationBuilder;
import org.apache.camel.test.infra.jetty.services.JettyEmbeddedService;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

public abstract class WebsocketCamelRouterWithInitParamTestSupport extends CamelTestSupport {
    protected static final int PORT = AvailablePortFinder.getNextAvailable();

    // This test needs to run with its own lifecycle management, so we cannot use extensions
    protected JettyEmbeddedService service;

    @BeforeEach
    void setupJetty() {
        final JettyConfiguration.ServletHandlerConfiguration.ServletConfiguration<CamelWebSocketServlet> servletConfiguration
                = new JettyConfiguration.ServletHandlerConfiguration.ServletConfiguration<>(
                        new CamelWebSocketServlet(),
                        JettyConfiguration.ServletHandlerConfiguration.ServletConfiguration.ROOT_PATH_SPEC, "CamelWsServlet");

        servletConfiguration.addInitParameter("events", "true");

        final JettyConfiguration jettyConfiguration = JettyConfigurationBuilder
                .emptyTemplate()
                .withPort(PORT)
                .withContextPath(JettyConfiguration.ROOT_CONTEXT_PATH)
                .withServletConfiguration().addServletConfiguration(servletConfiguration).build()
                .build();

        service = new JettyEmbeddedService(jettyConfiguration);
        service.initialize();
    }

    @AfterEach
    void tearDownJetty() {
        service.shutdown();
    }
}
