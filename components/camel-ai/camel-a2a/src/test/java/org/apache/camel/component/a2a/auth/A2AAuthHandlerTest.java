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
package org.apache.camel.component.a2a.auth;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.component.a2a.A2AConfiguration;
import org.apache.camel.component.a2a.model.AgentCard;
import org.apache.camel.component.a2a.model.SecurityRequirement;
import org.apache.camel.component.a2a.model.SecurityScheme;
import org.apache.camel.spi.OAuthTokenValidationConfig;
import org.apache.camel.spi.OAuthTokenValidationFactory;
import org.apache.camel.spi.OAuthTokenValidationResult;
import org.apache.camel.spi.OAuthTokenValidationResult.ErrorCode;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class A2AAuthHandlerTest extends CamelTestSupport {

    private Map<String, A2ASecuritySchemeHandler> defaultHandlers() {
        Map<String, A2ASecuritySchemeHandler> handlers = new LinkedHashMap<>();
        handlers.put("http", new HttpBearerSchemeHandler());
        handlers.put("apiKey", new ApiKeySchemeHandler());
        handlers.put("oauth2", new OAuth2SchemeHandler());
        handlers.put("openIdConnect", new OpenIdConnectSchemeHandler());
        return handlers;
    }

    private AgentCard cardWithScheme(String schemeName, String type) {
        SecurityScheme scheme;
        switch (type) {
            case "http":
                scheme = SecurityScheme.httpBearer();
                break;
            case "apiKey":
                scheme = SecurityScheme.apiKey(null, null);
                break;
            case "oauth2":
                scheme = SecurityScheme.oauth2(null);
                break;
            case "openIdConnect":
                scheme = SecurityScheme.openIdConnect(null);
                break;
            default:
                throw new IllegalArgumentException("Unknown scheme type: " + type);
        }
        return new AgentCard.Builder()
                .setName("test-agent")
                .setSecuritySchemes(Map.of(schemeName, scheme))
                .build();
    }

    // ---- Producer auth (backward compatibility) ----

    @Test
    void appliesBearerToken() {
        A2AConfiguration config = new A2AConfiguration();
        config.setBearerToken("test-token-123");
        A2AAuthHandler handler = new A2AAuthHandler(config, defaultHandlers());

        Exchange exchange = new DefaultExchange(context);
        handler.applyProducerAuth(exchange, context, cardWithScheme("bearer", "http"));

        assertThat(exchange.getIn().getHeader("Authorization")).isEqualTo("Bearer test-token-123");
    }

    @Test
    void appliesApiKeyWhenNoBearerToken() {
        A2AConfiguration config = new A2AConfiguration();
        config.setApiKey("api-key-456");
        A2AAuthHandler handler = new A2AAuthHandler(config, defaultHandlers());

        Exchange exchange = new DefaultExchange(context);
        handler.applyProducerAuth(exchange, context, cardWithScheme("apikey", "apiKey"));

        assertThat(exchange.getIn().getHeader("Authorization")).isEqualTo("Bearer api-key-456");
    }

    @Test
    void configHintPrioritizesMatchingScheme() {
        A2AConfiguration config = new A2AConfiguration();
        config.setBearerToken("bearer-wins");

        Map<String, SecurityScheme> schemes = new LinkedHashMap<>();
        schemes.put("apikey", SecurityScheme.apiKey("header", "Authorization"));
        schemes.put("bearer", SecurityScheme.httpBearer());

        AgentCard card = new AgentCard.Builder()
                .setName("test")
                .setSecuritySchemes(schemes)
                .build();

        A2AAuthHandler handler = new A2AAuthHandler(config, defaultHandlers());
        Exchange exchange = new DefaultExchange(context);
        handler.applyProducerAuth(exchange, context, card);

        assertThat(exchange.getIn().getHeader("Authorization")).isEqualTo("Bearer bearer-wins");
    }

    @Test
    void resolvesProducerAuthWithoutLeakingCardNamedApiKeyHeader() {
        A2AConfiguration config = new A2AConfiguration();
        config.setApiKey("agent-secret");

        AgentCard card = new AgentCard.Builder()
                .setName("test")
                .setSecuritySchemes(Map.of("agentKey", SecurityScheme.apiKey("header", "X-Agent-Key")))
                .setSecurityRequirements(List.of(SecurityRequirement.of("agentKey", List.of())))
                .build();

        A2AAuthHandler handler = new A2AAuthHandler(config, defaultHandlers());
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader("Authorization", "caller-token");

        A2AAuthHandler.ProducerAuth auth = handler.resolveProducerAuth(exchange, context, card);

        assertThat(auth.getHeaders()).containsEntry("X-Agent-Key", "agent-secret");
        assertThat(exchange.getMessage().getHeader("Authorization")).isEqualTo("caller-token");
        assertThat(exchange.getMessage().getHeader("X-Agent-Key")).isNull();
    }

    @Test
    void noAuthWhenNoCardSchemes() {
        A2AConfiguration config = new A2AConfiguration();
        A2AAuthHandler handler = new A2AAuthHandler(config, defaultHandlers());

        Exchange exchange = new DefaultExchange(context);
        handler.applyProducerAuth(exchange, context, null);

        assertThat(exchange.getIn().getHeader("Authorization")).isNull();
    }

    @Test
    void apiKeyWithCustomHeader() {
        A2AConfiguration config = new A2AConfiguration();
        config.setApiKey("my-api-key");
        config.setApiKeyHeader("X-API-Key");
        A2AAuthHandler handler = new A2AAuthHandler(config, defaultHandlers());

        Exchange exchange = new DefaultExchange(context);
        handler.applyProducerAuth(exchange, context, cardWithScheme("apikey", "apiKey"));

        assertThat(exchange.getIn().getHeader("X-API-Key")).isEqualTo("my-api-key");
        assertThat(exchange.getIn().getHeader("Authorization")).isNull();
    }

    @Test
    void oauthProfileFallsBackToBearerWhenSpiUnavailable() {
        A2AConfiguration config = new A2AConfiguration();
        config.setOauthProfile("test-profile");
        config.setBearerToken("fallback-token");
        A2AAuthHandler handler = new A2AAuthHandler(config, defaultHandlers());

        Exchange exchange = new DefaultExchange(context);
        handler.applyProducerAuth(exchange, context, cardWithScheme("bearer", "http"));

        assertThat(exchange.getIn().getHeader("Authorization")).isEqualTo("Bearer fallback-token");
    }

    // ---- Consumer auth validation ----

    @Test
    void validateConsumerAuthSkipsWhenDisabled() {
        A2AConfiguration config = new A2AConfiguration();
        config.setValidateAuth(false);
        A2AAuthHandler handler = new A2AAuthHandler(config, defaultHandlers());

        Exchange exchange = new DefaultExchange(context);
        A2AUserProfile profile = handler.validateConsumerAuth(exchange, cardWithScheme("bearer", "http"));

        assertThat(profile).isNull();
    }

    @Test
    void validateConsumerAuthAcceptsValidBearerToken() {
        A2AConfiguration config = new A2AConfiguration();
        config.setValidateAuth(true);
        config.setBearerToken("valid-token-xyz");
        A2AAuthHandler handler = new A2AAuthHandler(config, defaultHandlers());

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader("Authorization", "Bearer valid-token-xyz");

        A2AUserProfile profile = handler.validateConsumerAuth(exchange, cardWithScheme("bearer", "http"));

        assertThat(profile).isNotNull();
        assertThat(profile.getScheme()).isEqualTo("bearer");
        assertThat(profile.asMap()).containsEntry("scheme", "bearer");
    }

    @Test
    void validateConsumerAuthRejectsMissingToken() {
        A2AConfiguration config = new A2AConfiguration();
        config.setValidateAuth(true);
        A2AAuthHandler handler = new A2AAuthHandler(config, defaultHandlers());

        Exchange exchange = new DefaultExchange(context);

        assertThatThrownBy(() -> handler.validateConsumerAuth(exchange, cardWithScheme("bearer", "http")))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void validateConsumerAuthAcceptsValidApiKey() {
        A2AConfiguration config = new A2AConfiguration();
        config.setValidateAuth(true);
        config.setApiKey("secret-key-123");
        A2AAuthHandler handler = new A2AAuthHandler(config, defaultHandlers());

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader("Authorization", "Bearer secret-key-123");

        A2AUserProfile profile = handler.validateConsumerAuth(exchange, cardWithScheme("apikey", "apiKey"));

        assertThat(profile).isNotNull();
        assertThat(profile.getScheme()).isEqualTo("apiKey");
    }

    @Test
    void validateConsumerAuthAcceptsValidOAuth2Token() {
        bindOAuthValidationFactory();

        A2AConfiguration config = new A2AConfiguration();
        config.setValidateAuth(true);
        config.setOauthProfile("myprofile");
        A2AAuthHandler handler = new A2AAuthHandler(config, defaultHandlers());

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader("Authorization", "Bearer valid-token");

        A2AUserProfile profile = handler.validateConsumerAuth(exchange, cardWithScheme("oauth", "oauth2"));

        assertOAuthProfile(profile, "oauth2");
        assertThat(exchange.getProperty(BearerTokenExtractor.OAUTH_TOKEN_VALIDATION_RESULT))
                .isInstanceOf(OAuthTokenValidationResult.class);
    }

    @Test
    void validateConsumerAuthAcceptsValidOpenIdConnectToken() {
        bindOAuthValidationFactory();

        A2AConfiguration config = new A2AConfiguration();
        config.setValidateAuth(true);
        config.setOauthProfile("myprofile");
        A2AAuthHandler handler = new A2AAuthHandler(config, defaultHandlers());

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader("Authorization", "Bearer valid-token");

        A2AUserProfile profile = handler.validateConsumerAuth(exchange, cardWithScheme("oidc", "openIdConnect"));

        assertOAuthProfile(profile, "openIdConnect");
        assertThat(exchange.getProperty(BearerTokenExtractor.OAUTH_TOKEN_VALIDATION_RESULT))
                .isInstanceOf(OAuthTokenValidationResult.class);
    }

    @Test
    void validateConsumerAuthUsesRequirementAlternativesAsOr() {
        A2AConfiguration config = new A2AConfiguration();
        config.setValidateAuth(true);
        config.setApiKey("secret-key");
        A2AAuthHandler handler = new A2AAuthHandler(config, defaultHandlers());

        Map<String, SecurityScheme> schemes = new LinkedHashMap<>();
        schemes.put("bearer", SecurityScheme.httpBearer());
        schemes.put("agentKey", SecurityScheme.apiKey("header", "X-Agent-Key"));
        AgentCard card = new AgentCard.Builder()
                .setName("test")
                .setSecuritySchemes(schemes)
                .setSecurityRequirements(List.of(
                        SecurityRequirement.of("bearer", List.of()),
                        SecurityRequirement.of("agentKey", List.of())))
                .build();

        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader("X-Agent-Key", "secret-key");

        A2AUserProfile profile = handler.validateConsumerAuth(exchange, card);

        assertThat(profile).isNotNull();
        assertThat(profile.getScheme()).isEqualTo("apiKey");
    }

    @Test
    void validateConsumerAuthRequiresAllSchemesInOneRequirement() {
        A2AConfiguration config = new A2AConfiguration();
        config.setValidateAuth(true);
        config.setApiKey("secret-key");
        config.setBearerToken("bearer-token");
        A2AAuthHandler handler = new A2AAuthHandler(config, defaultHandlers());

        Map<String, SecurityScheme> schemes = new LinkedHashMap<>();
        schemes.put("agentKey", SecurityScheme.apiKey("header", "X-Agent-Key"));
        schemes.put("bearer", SecurityScheme.httpBearer());
        AgentCard card = new AgentCard.Builder()
                .setName("test")
                .setSecuritySchemes(schemes)
                .setSecurityRequirements(List.of(SecurityRequirement.fromScopeMap(
                        Map.of("agentKey", List.of(), "bearer", List.of()))))
                .build();

        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader("X-Agent-Key", "secret-key");
        exchange.getMessage().setHeader("Authorization", "Bearer bearer-token");

        assertThat(handler.validateConsumerAuth(exchange, card)).isNotNull();

        Exchange missingBearer = new DefaultExchange(context);
        missingBearer.getMessage().setHeader("X-Agent-Key", "secret-key");
        assertThatThrownBy(() -> handler.validateConsumerAuth(missingBearer, card))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Bearer");
    }

    @Test
    void validateConsumerAuthChecksOAuthScopes() {
        bindOAuthValidationFactory();

        A2AConfiguration config = new A2AConfiguration();
        config.setValidateAuth(true);
        config.setOauthProfile("myprofile");
        A2AAuthHandler handler = new A2AAuthHandler(config, defaultHandlers());

        AgentCard card = new AgentCard.Builder()
                .setName("test")
                .setSecuritySchemes(Map.of("oauth", SecurityScheme.oauth2(null)))
                .setSecurityRequirements(List.of(SecurityRequirement.of("oauth", List.of("message:send"))))
                .build();
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader("Authorization", "Bearer valid-token");

        assertOAuthProfile(handler.validateConsumerAuth(exchange, card), "oauth2");

        AgentCard adminCard = new AgentCard.Builder()
                .setName("test")
                .setSecuritySchemes(Map.of("oauth", SecurityScheme.oauth2(null)))
                .setSecurityRequirements(List.of(SecurityRequirement.of("oauth", List.of("admin"))))
                .build();
        Exchange missingScope = new DefaultExchange(context);
        missingScope.getMessage().setHeader("Authorization", "Bearer valid-token");

        assertThatThrownBy(() -> handler.validateConsumerAuth(missingScope, adminCard))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("missing required scopes");
    }

    @Test
    void userProfilePreservesOAuthFieldsAndMapView() {
        OAuthTokenValidationResult result = OAuthTokenValidationResult.valid(
                "user-1", "issuer-1", List.of("api"), List.of("read", "write"), Map.of("tenant", "t1"), 0);

        A2AUserProfile profile = A2AUserProfile.fromOAuthResult("oauth2", result);

        assertThat(profile.getScheme()).isEqualTo("oauth2");
        assertThat(profile.getSubject()).isEqualTo("user-1");
        assertThat(profile.getIssuer()).isEqualTo("issuer-1");
        assertThat(profile.getAudience()).containsExactly("api");
        assertThat(profile.getScopes()).containsExactly("read", "write");
        assertThat(profile.getClaims()).containsEntry("tenant", "t1");
        assertThat(profile.ownerKey()).isEqualTo("issuer-1|user-1");
        assertThat(profile.asMap())
                .containsEntry("scheme", "oauth2")
                .containsEntry("subject", "user-1")
                .containsEntry("issuer", "issuer-1")
                .containsEntry("audience", List.of("api"))
                .containsEntry("scopes", List.of("read", "write"))
                .containsEntry("claims", Map.of("tenant", "t1"));
    }

    @Test
    void validateConsumerAuthRejectsWrongApiKey() {
        A2AConfiguration config = new A2AConfiguration();
        config.setValidateAuth(true);
        config.setApiKey("correct-key");
        A2AAuthHandler handler = new A2AAuthHandler(config, defaultHandlers());

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader("Authorization", "Bearer wrong-key");

        assertThatThrownBy(() -> handler.validateConsumerAuth(exchange, cardWithScheme("apikey", "apiKey")))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Invalid API key");
    }

    @Test
    void validateConsumerAuthRejectsWhenNoSchemesInCard() {
        A2AConfiguration config = new A2AConfiguration();
        config.setValidateAuth(true);
        A2AAuthHandler handler = new A2AAuthHandler(config, defaultHandlers());

        Exchange exchange = new DefaultExchange(context);
        AgentCard cardNoSchemes = new AgentCard.Builder().setName("open-agent").build();

        assertThatThrownBy(() -> handler.validateConsumerAuth(exchange, cardNoSchemes))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("no security requirements");
    }

    private void bindOAuthValidationFactory() {
        context.getRegistry().bind(OAuthTokenValidationFactory.FACTORY, new StubOAuthTokenValidationFactory());
    }

    private static void assertOAuthProfile(A2AUserProfile profile, String scheme) {
        assertThat(profile).isNotNull();
        assertThat(profile.getScheme()).isEqualTo(scheme);
        assertThat(profile.getSubject()).isEqualTo("a2a-user");
        assertThat(profile.getIssuer()).isEqualTo("https://issuer.example");
        assertThat(profile.getAudience()).containsExactly("camel-a2a");
        assertThat(profile.getScopes()).containsExactly("message:send", "tasks:read");
        assertThat(profile.getClaims()).containsEntry("tenant", "acme");
        assertThat(profile.ownerKey()).isEqualTo("https://issuer.example|a2a-user");
    }

    private static final class StubOAuthTokenValidationFactory implements OAuthTokenValidationFactory {

        @Override
        public OAuthTokenValidationResult validateToken(OAuthTokenValidationConfig config, String token) {
            return validate(token);
        }

        @Override
        public OAuthTokenValidationResult validateToken(CamelContext context, String profileName, String token) {
            assertThat(profileName).isEqualTo("myprofile");
            return validate(token);
        }

        @Override
        public void validateConfiguration(OAuthTokenValidationConfig config) {
        }

        private static OAuthTokenValidationResult validate(String token) {
            if ("valid-token".equals(token)) {
                return OAuthTokenValidationResult.valid(
                        "a2a-user",
                        "https://issuer.example",
                        List.of("camel-a2a"),
                        List.of("message:send", "tasks:read"),
                        Map.of("tenant", "acme"),
                        1234567890L);
            }
            return OAuthTokenValidationResult.invalid(ErrorCode.INVALID_TOKEN, "invalid token");
        }
    }
}
