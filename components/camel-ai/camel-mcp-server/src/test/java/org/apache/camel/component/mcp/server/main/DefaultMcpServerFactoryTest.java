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

import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.component.mcp.server.McpServerBridge;
import org.apache.camel.component.mcp.server.McpServerEngine;
import org.apache.camel.component.mcp.server.stdio.StdioMcpServerEngine;
import org.apache.camel.main.HttpServerConfigurationProperties;
import org.apache.camel.main.MainConfigurationProperties;
import org.apache.camel.spi.OAuthTokenValidationConfig;
import org.apache.camel.spi.OAuthTokenValidationFactory;
import org.apache.camel.spi.OAuthTokenValidationResult;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void validatesOAuthProfileBeforeCreatingBridge() {
        DefaultMcpServerFactory factory = new DefaultMcpServerFactory();
        HttpServerConfigurationProperties server = new MainConfigurationProperties().httpServer();
        server.setMcpEnabled(true);
        server.setMcpOauthProfile("keycloak");
        server.setMcpRequiredScopes("mcp:read,mcp:write");
        server.setMcpResourceUri("https://mcp.example.test/mcp");

        context.getPropertiesComponent().addInitialProperty(
                "camel.oauth.keycloak.base-uri", "https://id.example.test/realms/camel");
        context.getPropertiesComponent().addInitialProperty(
                "camel.oauth.keycloak.expected-audience", "camel-mcp-server");

        AtomicReference<OAuthTokenValidationConfig> validatedConfig = new AtomicReference<>();
        OAuthTokenValidationFactory validationFactory = new OAuthTokenValidationFactory() {
            @Override
            public OAuthTokenValidationResult validateToken(OAuthTokenValidationConfig config, String token) {
                return OAuthTokenValidationResult.invalid("not used by this test");
            }

            @Override
            public void validateConfiguration(OAuthTokenValidationConfig config) {
                validatedConfig.set(config);
            }
        };
        context.getRegistry().bind(OAuthTokenValidationFactory.FACTORY, validationFactory);

        McpServerBridge bridge = (McpServerBridge) factory.newMcpServer(context, server);

        assertThat(bridge.getConfiguration().getOauthProfile()).isEqualTo("keycloak");
        assertThat(bridge.getConfiguration().getRequiredScopes()).isEqualTo("mcp:read,mcp:write");
        assertThat(bridge.getConfiguration().getResourceUri()).isEqualTo("https://mcp.example.test/mcp");
        assertThat(validatedConfig.get()).isNotNull();
        assertThat(validatedConfig.get().getExpectedIssuer()).isEqualTo("https://id.example.test/realms/camel");
        assertThat(validatedConfig.get().getExpectedAudiences()).contains("camel-mcp-server");
    }

    @Test
    void requiresExplicitResourceUriWhenOAuthIsConfigured() {
        DefaultMcpServerFactory factory = new DefaultMcpServerFactory();
        HttpServerConfigurationProperties server = new MainConfigurationProperties().httpServer();
        server.setMcpEnabled(true);
        server.setMcpOauthProfile("keycloak");

        assertThatThrownBy(() -> factory.newMcpServer(context, server))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("camel.server.mcp-resource-uri");
    }

    @Test
    void rejectsRequiredScopesWithoutOAuthProfile() {
        DefaultMcpServerFactory factory = new DefaultMcpServerFactory();
        HttpServerConfigurationProperties server = new MainConfigurationProperties().httpServer();
        server.setMcpEnabled(true);
        server.setMcpRequiredScopes("mcp:read");

        assertThatThrownBy(() -> factory.newMcpServer(context, server))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("camel.server.mcp-oauth-profile");
    }

    @Test
    void ignoresOAuthConfigurationForStdioTransport() {
        DefaultMcpServerFactory factory = new DefaultMcpServerFactory();
        HttpServerConfigurationProperties server = new MainConfigurationProperties().httpServer();
        server.setMcpEnabled(true);
        server.setMcpTransport("stdio");
        server.setMcpOauthProfile("keycloak");
        server.setMcpRequiredScopes("mcp:read");

        AtomicReference<OAuthTokenValidationConfig> validatedConfig = new AtomicReference<>();
        OAuthTokenValidationFactory validationFactory = new OAuthTokenValidationFactory() {
            @Override
            public OAuthTokenValidationResult validateToken(OAuthTokenValidationConfig config, String token) {
                return OAuthTokenValidationResult.invalid("not used by this test");
            }

            @Override
            public void validateConfiguration(OAuthTokenValidationConfig config) {
                validatedConfig.set(config);
            }
        };
        context.getRegistry().bind(OAuthTokenValidationFactory.FACTORY, validationFactory);

        factory.newMcpServer(context, server);

        assertThat(validatedConfig.get()).isNull();
        assertThat(context.getRegistry().findSingleByType(McpServerEngine.class))
                .isInstanceOf(StdioMcpServerEngine.class);
    }

    @Test
    void isStdioTransportRecognizesCaseInsensitiveValues() {
        assertThat(DefaultMcpServerFactory.isStdioTransport("stdio")).isTrue();
        assertThat(DefaultMcpServerFactory.isStdioTransport(" STDIO ")).isTrue();
        assertThat(DefaultMcpServerFactory.isStdioTransport("http")).isFalse();
        assertThat(DefaultMcpServerFactory.isStdioTransport(null)).isFalse();
    }
}
