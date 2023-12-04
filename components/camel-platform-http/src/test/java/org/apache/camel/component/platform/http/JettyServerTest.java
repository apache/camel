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
package org.apache.camel.component.platform.http;

import org.apache.camel.test.infra.jetty.services.JettyConfiguration;
import org.apache.camel.test.infra.jetty.services.JettyConfigurationBuilder;
import org.apache.camel.test.infra.jetty.services.JettyEmbeddedService;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;

public class JettyServerTest {
    public static final String JETTY_SERVER_NAME = "JettyServerTest";

    private final int port;
    private final ContextHandlerCollection contextHandlerCollection;
    private final JettyEmbeddedService service;

    public JettyServerTest(int port) {
        contextHandlerCollection = new ContextHandlerCollection(true);

        final JettyConfiguration configuration = JettyConfigurationBuilder.bareTemplate()
                .withPort(port)
                .withHandlerCollectionConfiguration().addHandlers(contextHandlerCollection).build().build();
        this.service = new JettyEmbeddedService(configuration);

        this.port = port;

    }

    public void start() {
        service.initialize();

    }

    public void stop() throws Exception {
        service.stop();
    }

    public void addHandler(ContextHandler contextHandler) throws Exception {
        contextHandlerCollection.addHandler(contextHandler);
        contextHandler.start();
    }

    public int getServerPort() {
        return port;
    }
}
