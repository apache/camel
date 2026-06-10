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
package org.apache.camel.http.base;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.OAuthTokenValidationConfig;
import org.apache.camel.spi.OAuthTokenValidationFactory;
import org.apache.camel.spi.OAuthTokenValidationResult;
import org.apache.camel.spi.OAuthTokenValidationResult.ErrorCode;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OAuthHttpSecuritySupportTest {

    @Test
    void extractsValidBearerTokens() {
        assertEquals("token", OAuthHttpSecuritySupport.extractBearerToken("Bearer token"));
        assertEquals("token", OAuthHttpSecuritySupport.extractBearerToken("bearer token"));
        assertEquals("token", OAuthHttpSecuritySupport.extractBearerToken("BEARER token"));
        assertEquals("abc.DEF-123_~/+=", OAuthHttpSecuritySupport.extractBearerToken("Bearer   abc.DEF-123_~/+="));
    }

    @Test
    void rejectsMalformedBearerTokens() {
        assertNull(OAuthHttpSecuritySupport.extractBearerToken(null));
        assertNull(OAuthHttpSecuritySupport.extractBearerToken("Basic dXNlcjpwYXNz"));
        assertNull(OAuthHttpSecuritySupport.extractBearerToken("Bearer"));
        assertNull(OAuthHttpSecuritySupport.extractBearerToken("Bearer   "));
        assertNull(OAuthHttpSecuritySupport.extractBearerToken("Bearertoken"));
        assertNull(OAuthHttpSecuritySupport.extractBearerToken("Bearer\t token"));
        assertNull(OAuthHttpSecuritySupport.extractBearerToken("Bearer token value"));
        assertNull(OAuthHttpSecuritySupport.extractBearerToken("Bearer token "));
        assertNull(OAuthHttpSecuritySupport.extractBearerToken("Bearer =token"));
        assertNull(OAuthHttpSecuritySupport.extractBearerToken("Bearer token=value"));
        assertNull(OAuthHttpSecuritySupport.extractBearerToken("Bearer " + "a".repeat(8193)));
        assertNull(OAuthHttpSecuritySupport.extractBearerToken("Bearer " + " ".repeat(17) + "token"));
    }

    @Test
    void validatesAndAuthenticatesExchange() {
        OAuthTokenValidationResult result = validResult("alice");
        OAuthHttpSecuritySupport security = new OAuthHttpSecuritySupport("myprofile", new StubFactory(result));
        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.getMessage().setHeader(OAuthHttpSecuritySupport.AUTHORIZATION, "Bearer valid-token");

        assertTrue(security.authenticate(exchange));
        assertNull(exchange.getMessage().getHeader(OAuthHttpSecuritySupport.AUTHORIZATION));
        assertSame(result, exchange.getProperty(OAuthHttpSecuritySupport.OAUTH_TOKEN_VALIDATION_RESULT));
        assertFalse(exchange.isRouteStop());
    }

    @Test
    void removesAuthorizationHeaderIgnoringCase() {
        OAuthTokenValidationResult result = validResult("alice");
        OAuthHttpSecuritySupport security = new OAuthHttpSecuritySupport("myprofile", new StubFactory(result));
        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.getMessage().setHeader("authorization", "Bearer valid-token");

        assertTrue(security.authenticate(exchange));
        assertNull(exchange.getMessage().getHeader("authorization"));
        assertSame(result, exchange.getProperty(OAuthHttpSecuritySupport.OAUTH_TOKEN_VALIDATION_RESULT));
    }

    @Test
    void rejectsInvalidTokenAndStopsRoute() {
        OAuthHttpSecuritySupport security = new OAuthHttpSecuritySupport(
                "myprofile", new StubFactory(OAuthTokenValidationResult.invalid(ErrorCode.INVALID_TOKEN, "invalid")));
        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.getMessage().setHeader(OAuthHttpSecuritySupport.AUTHORIZATION, "Bearer invalid-token");

        assertFalse(security.authenticate(exchange));
        assertNull(exchange.getMessage().getHeader(OAuthHttpSecuritySupport.AUTHORIZATION));
        assertEquals(OAuthHttpSecuritySupport.UNAUTHORIZED,
                exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("text/plain", exchange.getMessage().getHeader(Exchange.CONTENT_TYPE));
        assertEquals("Bearer error=\"invalid_token\"",
                exchange.getMessage().getHeader(OAuthHttpSecuritySupport.WWW_AUTHENTICATE));
        assertEquals(OAuthHttpSecuritySupport.UNAUTHORIZED_BODY, exchange.getMessage().getBody(String.class));
        assertTrue(exchange.isRouteStop());
    }

    @Test
    void rejectsMalformedHeaderWithBadRequest() {
        OAuthHttpSecuritySupport security = new OAuthHttpSecuritySupport("myprofile", new StubFactory(validResult("alice")));
        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.getMessage().setHeader(OAuthHttpSecuritySupport.AUTHORIZATION, "Bearer token value");

        assertFalse(security.authenticate(exchange));
        assertEquals(OAuthHttpSecuritySupport.BAD_REQUEST,
                exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("text/plain", exchange.getMessage().getHeader(Exchange.CONTENT_TYPE));
        assertEquals("Bearer error=\"invalid_request\"",
                exchange.getMessage().getHeader(OAuthHttpSecuritySupport.WWW_AUTHENTICATE));
        assertEquals(OAuthHttpSecuritySupport.BAD_REQUEST_BODY, exchange.getMessage().getBody(String.class));
        assertTrue(exchange.isRouteStop());
    }

    @Test
    void rejectsDuplicateAuthorizationHeadersWithBadRequest() {
        OAuthHttpSecuritySupport security = new OAuthHttpSecuritySupport("myprofile", new StubFactory(validResult("alice")));
        OAuthHttpSecuritySupport.Validation validation = security.validate(
                new DefaultCamelContext(), List.of("Bearer valid-token", "Bearer other-token"));

        assertFalse(validation.isAuthenticated());
        assertEquals(OAuthHttpSecuritySupport.BAD_REQUEST, validation.getRejectionStatusCode());
        assertEquals("Bearer error=\"invalid_request\"", validation.getWwwAuthenticate());
        assertEquals(OAuthHttpSecuritySupport.BAD_REQUEST_BODY, validation.getResponseBody());
    }

    @Test
    void rejectsOverlongAuthorizationHeaderBeforeWhitespaceScan() {
        OAuthHttpSecuritySupport security = new OAuthHttpSecuritySupport("myprofile", new StubFactory(validResult("alice")));
        OAuthHttpSecuritySupport.Validation validation = security.validate(
                new DefaultCamelContext(), "Bearer " + " ".repeat(17) + "token");

        assertFalse(validation.isAuthenticated());
        assertEquals(OAuthHttpSecuritySupport.BAD_REQUEST, validation.getRejectionStatusCode());
    }

    @Test
    void mapsInfrastructureFailureToServiceUnavailable() {
        OAuthHttpSecuritySupport security = new OAuthHttpSecuritySupport("myprofile", new FailingFactory());
        OAuthHttpSecuritySupport.Validation validation = security.validate(new DefaultCamelContext(), "Bearer token");

        assertFalse(validation.isAuthenticated());
        assertEquals(OAuthHttpSecuritySupport.SERVICE_UNAVAILABLE, validation.getRejectionStatusCode());
        assertNull(validation.getValidationResult());
    }

    @Test
    void mapsNullValidationResultToServiceUnavailable() {
        OAuthHttpSecuritySupport security = new OAuthHttpSecuritySupport("myprofile", new StubFactory(null));
        OAuthHttpSecuritySupport.Validation validation = security.validate(new DefaultCamelContext(), "Bearer token");

        assertFalse(validation.isAuthenticated());
        assertEquals(OAuthHttpSecuritySupport.SERVICE_UNAVAILABLE, validation.getRejectionStatusCode());
        assertNull(validation.getValidationResult());
    }

    @Test
    void authenticateRejectsMissingHeaderWithUnauthorized() {
        OAuthHttpSecuritySupport security = new OAuthHttpSecuritySupport("myprofile", new StubFactory(validResult("alice")));
        Exchange exchange = new DefaultExchange(new DefaultCamelContext());

        assertFalse(security.authenticate(exchange));
        assertEquals(OAuthHttpSecuritySupport.UNAUTHORIZED,
                exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("text/plain", exchange.getMessage().getHeader(Exchange.CONTENT_TYPE));
        assertEquals(OAuthHttpSecuritySupport.BEARER,
                exchange.getMessage().getHeader(OAuthHttpSecuritySupport.WWW_AUTHENTICATE));
        assertEquals(OAuthHttpSecuritySupport.UNAUTHORIZED_BODY, exchange.getMessage().getBody(String.class));
        assertTrue(exchange.isRouteStop());
    }

    @Test
    void createReturnsNullWhenProfileIsEmpty() {
        assertNull(OAuthHttpSecuritySupport.create(new DefaultCamelContext(), null));
        assertNull(OAuthHttpSecuritySupport.create(new DefaultCamelContext(), ""));
    }

    @Test
    void createValidatesConfigurationEagerly() {
        DefaultCamelContext context = new DefaultCamelContext();
        context.getRegistry().bind(OAuthTokenValidationFactory.FACTORY, new FailingConfigurationFactory());

        assertThrows(IllegalArgumentException.class, () -> OAuthHttpSecuritySupport.create(context, "myprofile"));
    }

    @Test
    void createSnapshotsProfileConfigurationAtStartup() {
        DefaultCamelContext context = new DefaultCamelContext();
        ConfigCapturingFactory factory = new ConfigCapturingFactory();
        context.getRegistry().bind(OAuthTokenValidationFactory.FACTORY, factory);
        context.getPropertiesComponent().addInitialProperty("camel.oauth.myprofile.expected-audience", "api-a");

        OAuthHttpSecuritySupport security = OAuthHttpSecuritySupport.create(context, "myprofile");
        context.getPropertiesComponent().addInitialProperty("camel.oauth.myprofile.expected-audience", "api-b");

        OAuthHttpSecuritySupport.Validation validation = security.validate(context, "Bearer token");

        assertTrue(validation.isAuthenticated());
        assertEquals(Set.of("api-a"), factory.expectedAudiences);
    }

    @Test
    void validationObjectRepresentsAuthenticatedResult() {
        OAuthTokenValidationResult result = validResult("bob");
        OAuthHttpSecuritySupport.Validation validation = OAuthHttpSecuritySupport.Validation.authenticated(result);

        assertTrue(validation.isAuthenticated());
        assertThrows(IllegalStateException.class, validation::getRejectionStatusCode);
        assertSame(result, validation.getValidationResult());
        assertEquals("bob", result.getSubject());
        assertEquals("https://issuer.example", result.getIssuer());
        assertEquals(List.of("camel-api"), result.getAudience());
        assertEquals(List.of("read"), result.getScopes());
        assertEquals("acme", result.getClaim("tenant"));
    }

    @Test
    void authenticatedValidationRequiresResult() {
        assertThrows(NullPointerException.class, () -> OAuthHttpSecuritySupport.Validation.authenticated(null));
    }

    @Test
    void constructorsRequireProfileName() {
        assertThrows(NullPointerException.class, () -> new OAuthHttpSecuritySupport(null));
        assertThrows(NullPointerException.class,
                () -> new OAuthHttpSecuritySupport(null, new StubFactory(validResult("alice"))));
    }

    @Test
    void rejectAppliesValidationResponse() {
        Exchange unauthorized = new DefaultExchange(new DefaultCamelContext());
        OAuthHttpSecuritySupport.reject(unauthorized, OAuthHttpSecuritySupport.Validation.unauthorized());
        assertEquals("text/plain", unauthorized.getMessage().getHeader(Exchange.CONTENT_TYPE));
        assertEquals(OAuthHttpSecuritySupport.BEARER,
                unauthorized.getMessage().getHeader(OAuthHttpSecuritySupport.WWW_AUTHENTICATE));

        Exchange badRequest = new DefaultExchange(new DefaultCamelContext());
        OAuthHttpSecuritySupport.reject(badRequest, OAuthHttpSecuritySupport.Validation.invalidRequest());
        assertEquals("text/plain", badRequest.getMessage().getHeader(Exchange.CONTENT_TYPE));
        assertEquals("Bearer error=\"invalid_request\"",
                badRequest.getMessage().getHeader(OAuthHttpSecuritySupport.WWW_AUTHENTICATE));

        Exchange unavailable = new DefaultExchange(new DefaultCamelContext());
        OAuthHttpSecuritySupport.reject(unavailable, OAuthHttpSecuritySupport.Validation.serviceUnavailable());
        assertEquals("text/plain", unavailable.getMessage().getHeader(Exchange.CONTENT_TYPE));
        assertNull(unavailable.getMessage().getHeader(OAuthHttpSecuritySupport.WWW_AUTHENTICATE));
    }

    private static OAuthTokenValidationResult validResult(String subject) {
        return OAuthTokenValidationResult.valid(
                subject, "https://issuer.example", List.of("camel-api"), List.of("read"), Map.of("tenant", "acme"), 0);
    }

    private static class StubFactory implements OAuthTokenValidationFactory {

        private final OAuthTokenValidationResult result;

        private StubFactory(OAuthTokenValidationResult result) {
            this.result = result;
        }

        @Override
        public OAuthTokenValidationResult validateToken(OAuthTokenValidationConfig config, String token) {
            return result;
        }

        @Override
        public OAuthTokenValidationResult validateToken(CamelContext context, String profileName, String token) {
            assertEquals("myprofile", profileName);
            assertNotNull(token);
            return result;
        }

        @Override
        public void validateConfiguration(OAuthTokenValidationConfig config) {
        }
    }

    private static final class FailingFactory implements OAuthTokenValidationFactory {

        @Override
        public OAuthTokenValidationResult validateToken(OAuthTokenValidationConfig config, String token) {
            throw new IllegalStateException("validator unavailable");
        }

        @Override
        public OAuthTokenValidationResult validateToken(CamelContext context, String profileName, String token) {
            throw new IllegalStateException("validator unavailable");
        }

        @Override
        public void validateConfiguration(OAuthTokenValidationConfig config) {
        }
    }

    private static final class ConfigCapturingFactory implements OAuthTokenValidationFactory {

        private Set<String> expectedAudiences;

        @Override
        public OAuthTokenValidationResult validateToken(OAuthTokenValidationConfig config, String token) {
            expectedAudiences = config.getExpectedAudiences();
            return validResult("alice");
        }

        @Override
        public void validateConfiguration(OAuthTokenValidationConfig config) {
        }
    }

    private static final class FailingConfigurationFactory extends StubFactory {

        private FailingConfigurationFactory() {
            super(validResult("alice"));
        }

        @Override
        public void validateConfiguration(CamelContext context, String profileName) {
            throw new IllegalArgumentException("invalid profile");
        }
    }
}
