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
package org.apache.camel.component.mcp.server.main;

import org.apache.camel.component.mcp.server.McpServerEngine;
import org.apache.camel.component.mcp.server.stdio.StdioMcpServerEngine;
import org.apache.camel.main.HttpServerConfigurationProperties;
import org.apache.camel.main.MainConfigurationProperties;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultMcpServerFactoryTest extends CamelTestSupport {

    @Test
    void registersStdioEngineWhenTransportIsStdio() throws Exception {
        DefaultMcpServerFactory factory = new DefaultMcpServerFactory();
        HttpServerConfigurationProperties server = new MainConfigurationProperties().httpServer();
        server.setMcpEnabled(true);
        server.setMcpTransport("stdio");
        server.setMcpTags("agent");

        factory.newMcpServer(context, server);

        McpServerEngine engine = context.getRegistry().findSingleByType(McpServerEngine.class);
        assertThat(engine).isInstanceOf(StdioMcpServerEngine.class);
    }

    @Test
    void doesNotRegisterStdioEngineForHttpTransport() {
        DefaultMcpServerFactory factory = new DefaultMcpServerFactory();
        HttpServerConfigurationProperties server = new MainConfigurationProperties().httpServer();
        server.setMcpEnabled(true);
        server.setMcpTransport("http");

        factory.newMcpServer(context, server);

        assertThat(context.getRegistry().findSingleByType(McpServerEngine.class)).isNull();
    }

    @Test
    void isStdioTransportRecognizesCaseInsensitiveValues() {
        assertThat(DefaultMcpServerFactory.isStdioTransport("stdio")).isTrue();
        assertThat(DefaultMcpServerFactory.isStdioTransport(" STDIO ")).isTrue();
        assertThat(DefaultMcpServerFactory.isStdioTransport("http")).isFalse();
        assertThat(DefaultMcpServerFactory.isStdioTransport(null)).isFalse();
    }
}
