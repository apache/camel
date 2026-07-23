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

import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.platform.http.spi.OAuthPlatformHttpSecurityHandler;
import org.apache.camel.component.platform.http.spi.PlatformHttpConsumer;
import org.apache.camel.component.platform.http.spi.PlatformHttpEngine;
import org.apache.camel.component.platform.http.spi.PlatformHttpSecurityHandler;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.OAuthTokenValidationConfig;
import org.apache.camel.spi.OAuthTokenValidationFactory;
import org.apache.camel.spi.OAuthTokenValidationResult;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformHttpOAuthProfileTest {

    @BeforeEach
    void resetStubFactory() {
        StubOAuthTokenValidationFactory.reset();
        ProfileBeanOAuthTokenValidationFactory.reset();
    }

    @Test
    void oauthProfileIsDelegatedToPlatformHttpEngine() throws Exception {
        CapturingEngine engine = new CapturingEngine();

        try (DefaultCamelContext context = new DefaultCamelContext()) {
            PlatformHttpComponent component = new PlatformHttpComponent();
            component.setEngine(engine);
            context.addComponent("platform-http", component);
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("platform-http:/secure?oauthProfile=myprofile")
                            .setBody().constant("secured");
                }
            });

            context.start();

            assertNotNull(engine.securityHandler);
            OAuthPlatformHttpSecurityHandler handler
                    = assertInstanceOf(OAuthPlatformHttpSecurityHandler.class, engine.securityHandler);
            assertEquals("myprofile", handler.getOauthProfile());
            assertEquals("myprofile", StubOAuthTokenValidationFactory.lastConfigurationProfileName);
        }
    }

    @Test
    void oauthProfileConfigurationErrorFailsStartup() {
        CapturingEngine engine = new CapturingEngine();

        assertThrows(Exception.class, () -> {
            try (DefaultCamelContext context = new DefaultCamelContext()) {
                PlatformHttpComponent component = new PlatformHttpComponent();
                component.setEngine(engine);
                context.addComponent("platform-http", component);
                context.addRoutes(new RouteBuilder() {
                    @Override
                    public void configure() {
                        from("platform-http:/secure?oauthProfile=invalid-profile")
                                .setBody().constant("secured");
                    }
                });

                context.start();
            }
        });
    }

    @Test
    void oauthProfileUsesProfileSpecificValidationFactoryBean() throws Exception {
        CapturingEngine engine = new CapturingEngine();

        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.getRegistry().bind("profileFactory", new ProfileBeanOAuthTokenValidationFactory());
            context.getPropertiesComponent().addInitialProperty(
                    "camel.oauth.myprofile.validation-factory", "#bean:profileFactory");

            PlatformHttpComponent component = new PlatformHttpComponent();
            component.setEngine(engine);
            context.addComponent("platform-http", component);
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("platform-http:/secure?oauthProfile=myprofile")
                            .setBody().constant("secured");
                }
            });

            context.start();

            assertEquals("myprofile", ProfileBeanOAuthTokenValidationFactory.lastConfigurationProfileName);
            assertNull(StubOAuthTokenValidationFactory.lastConfigurationProfileName);
            assertNotNull(engine.securityHandler);
        }
    }

    @Test
    void oauthProfileUsesSingleRegistryValidationFactoryBean() throws Exception {
        CapturingEngine engine = new CapturingEngine();

        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.getRegistry().bind("registryFactory", new ProfileBeanOAuthTokenValidationFactory());

            PlatformHttpComponent component = new PlatformHttpComponent();
            component.setEngine(engine);
            context.addComponent("platform-http", component);
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("platform-http:/secure?oauthProfile=myprofile")
                            .setBody().constant("secured");
                }
            });

            context.start();

            assertEquals("myprofile", ProfileBeanOAuthTokenValidationFactory.lastConfigurationProfileName);
            assertNull(StubOAuthTokenValidationFactory.lastConfigurationProfileName);
            assertNotNull(engine.securityHandler);
        }
    }

    @Test
    void oauthProfileSpecificMissingValidationFactoryBeanFailsStartup() {
        CapturingEngine engine = new CapturingEngine();

        assertThrows(Exception.class, () -> {
            try (DefaultCamelContext context = new DefaultCamelContext()) {
                context.getPropertiesComponent().addInitialProperty(
                        "camel.oauth.myprofile.validation-factory", "#bean:missingFactory");

                PlatformHttpComponent component = new PlatformHttpComponent();
                component.setEngine(engine);
                context.addComponent("platform-http", component);
                context.addRoutes(new RouteBuilder() {
                    @Override
                    public void configure() {
                        from("platform-http:/secure?oauthProfile=myprofile")
                                .setBody().constant("secured");
                    }
                });

                context.start();
            }
        });
    }

    @Test
    void oauthProfileSpecificWrongTypeValidationFactoryBeanFailsStartup() throws Exception {
        CapturingEngine engine = new CapturingEngine();

        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.getRegistry().bind("profileFactory", "not a token validation factory");
            context.getPropertiesComponent().addInitialProperty(
                    "camel.oauth.myprofile.validation-factory", "#bean:profileFactory");

            PlatformHttpComponent component = new PlatformHttpComponent();
            component.setEngine(engine);
            context.addComponent("platform-http", component);
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("platform-http:/secure?oauthProfile=myprofile")
                            .setBody().constant("secured");
                }
            });

            assertThrows(Exception.class, () -> context.start());
        }
    }

    @Test
    void fallbackSecurityHandlerRejectsMissingBearerToken() throws Exception {
        OAuthPlatformHttpSecurityHandler handler = new OAuthPlatformHttpSecurityHandler("myprofile");
        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        boolean[] routeInvoked = new boolean[1];

        Processor secured = handler.wrapProcessor(null, e -> routeInvoked[0] = true);
        secured.process(exchange);

        assertFalse(routeInvoked[0]);
        assertEquals(401, exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("Bearer", exchange.getMessage().getHeader("WWW-Authenticate"));
        assertEquals("Unauthorized", exchange.getMessage().getBody(String.class));
        assertNull(exchange.getMessage().getHeader("Authorization"));
    }

    @Test
    void fallbackSecurityHandlerAcceptsValidBearerToken() throws Exception {
        OAuthPlatformHttpSecurityHandler handler
                = new OAuthPlatformHttpSecurityHandler("myprofile", new StubOAuthTokenValidationFactory());
        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.getMessage().setHeader("Authorization", "bearer valid-token");
        boolean[] routeInvoked = new boolean[1];

        Processor secured = handler.wrapProcessor(null, e -> routeInvoked[0] = true);
        secured.process(exchange);

        assertEquals("myprofile", StubOAuthTokenValidationFactory.lastProfileName);
        assertEquals("valid-token", StubOAuthTokenValidationFactory.lastToken);
        assertTrue(routeInvoked[0]);
        OAuthTokenValidationResult result
                = exchange.getProperty(PlatformHttpConstants.OAUTH_TOKEN_VALIDATION_RESULT, OAuthTokenValidationResult.class);
        assertNotNull(result);
        assertNull(exchange.getProperty(Exchange.AUTHENTICATION));
        assertNull(exchange.getMessage().getHeader("Authorization"));
        assertEquals("camel-user", result.getSubject());
    }

    @Test
    void fallbackSecurityHandlerRejectsInvalidBearerToken() throws Exception {
        OAuthPlatformHttpSecurityHandler handler
                = new OAuthPlatformHttpSecurityHandler("myprofile", new StubOAuthTokenValidationFactory());
        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.getMessage().setHeader("Authorization", "Bearer invalid-token");
        boolean[] routeInvoked = new boolean[1];

        Processor secured = handler.wrapProcessor(null, e -> routeInvoked[0] = true);
        secured.process(exchange);

        assertFalse(routeInvoked[0]);
        assertEquals(401, exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("Bearer error=\"invalid_token\"", exchange.getMessage().getHeader("WWW-Authenticate"));
        assertEquals("Unauthorized", exchange.getMessage().getBody(String.class));
        assertNull(exchange.getMessage().getHeader("Authorization"));
    }

    @Test
    void fallbackSecurityHandlerRejectsMalformedBearerHeaders() throws Exception {
        OAuthPlatformHttpSecurityHandler handler
                = new OAuthPlatformHttpSecurityHandler("myprofile", new StubOAuthTokenValidationFactory());

        for (String authorization : List.of(
                "Basic valid-token",
                "Bearer",
                "Bearer   ",
                "Bearervalid-token",
                "Bearer valid token",
                "Bearer valid-token ",
                "Bearer\tvalid-token",
                "Bearer =")) {
            StubOAuthTokenValidationFactory.reset();
            Exchange exchange = new DefaultExchange(new DefaultCamelContext());
            exchange.getMessage().setHeader("Authorization", authorization);
            boolean[] routeInvoked = new boolean[1];

            Processor secured = handler.wrapProcessor(null, e -> routeInvoked[0] = true);
            secured.process(exchange);

            assertFalse(routeInvoked[0]);
            assertEquals(400, exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
            assertEquals("Bearer error=\"invalid_request\"", exchange.getMessage().getHeader("WWW-Authenticate"));
            assertEquals("Bad Request", exchange.getMessage().getBody(String.class));
            assertNull(exchange.getMessage().getHeader("Authorization"));
            assertNull(StubOAuthTokenValidationFactory.lastToken);
        }
    }

    @Test
    void fallbackSecurityHandlerRejectsOversizedBearerToken() throws Exception {
        OAuthPlatformHttpSecurityHandler handler
                = new OAuthPlatformHttpSecurityHandler("myprofile", new StubOAuthTokenValidationFactory());
        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.getMessage().setHeader("Authorization", "Bearer " + "a".repeat(8193));
        boolean[] routeInvoked = new boolean[1];

        Processor secured = handler.wrapProcessor(null, e -> routeInvoked[0] = true);
        secured.process(exchange);

        assertFalse(routeInvoked[0]);
        assertEquals(400, exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("Bearer error=\"invalid_request\"", exchange.getMessage().getHeader("WWW-Authenticate"));
        assertEquals("Bad Request", exchange.getMessage().getBody(String.class));
        assertNull(exchange.getMessage().getHeader("Authorization"));
        assertNull(StubOAuthTokenValidationFactory.lastToken);
    }

    @Test
    void fallbackSecurityHandlerMapsInfrastructureErrorToServiceUnavailable() throws Exception {
        OAuthPlatformHttpSecurityHandler handler
                = new OAuthPlatformHttpSecurityHandler("myprofile", new StubOAuthTokenValidationFactory());
        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.getMessage().setHeader("Authorization", "Bearer error-token");
        boolean[] routeInvoked = new boolean[1];

        Processor secured = handler.wrapProcessor(null, e -> routeInvoked[0] = true);
        secured.process(exchange);

        assertFalse(routeInvoked[0]);
        assertEquals(503, exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertNull(exchange.getMessage().getHeader("WWW-Authenticate"));
        assertEquals("Service Unavailable", exchange.getMessage().getBody(String.class));
        assertNull(exchange.getMessage().getHeader("Authorization"));
    }

    @Test
    void oauthProfileWrapsProcessorForEnginesUsingDefaultCreateConsumer() throws Exception {
        OldStyleEngine engine = new OldStyleEngine();

        try (DefaultCamelContext context = new DefaultCamelContext()) {
            PlatformHttpComponent component = new PlatformHttpComponent();
            component.setEngine(engine);
            context.addComponent("platform-http", component);
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("platform-http:/secure?oauthProfile=myprofile")
                            .setBody().constant("secured");
                }
            });

            context.start();

            Exchange missingToken = new DefaultExchange(context);
            engine.processor.process(missingToken);
            assertEquals(401, missingToken.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
            assertEquals("Unauthorized", missingToken.getMessage().getBody(String.class));

            Exchange validToken = new DefaultExchange(context);
            validToken.getMessage().setHeader("Authorization", "Bearer valid-token");
            engine.processor.process(validToken);
            assertEquals("secured", validToken.getMessage().getBody(String.class));
            assertNotNull(validToken.getProperty(PlatformHttpConstants.OAUTH_TOKEN_VALIDATION_RESULT));
        }
    }

    private static final class CapturingEngine implements PlatformHttpEngine {
        private PlatformHttpSecurityHandler securityHandler;

        @Override
        public PlatformHttpConsumer createConsumer(PlatformHttpEndpoint platformHttpEndpoint, Processor processor) {
            return new NoopPlatformHttpConsumer(platformHttpEndpoint, processor);
        }

        @Override
        public PlatformHttpConsumer createConsumer(
                PlatformHttpEndpoint platformHttpEndpoint,
                Processor processor,
                PlatformHttpSecurityHandler securityHandler) {
            this.securityHandler = securityHandler;
            return createConsumer(platformHttpEndpoint, processor);
        }
    }

    private static final class OldStyleEngine implements PlatformHttpEngine {
        private Processor processor;

        @Override
        public PlatformHttpConsumer createConsumer(PlatformHttpEndpoint platformHttpEndpoint, Processor processor) {
            this.processor = processor;
            return new NoopPlatformHttpConsumer(platformHttpEndpoint, processor);
        }
    }

    private static final class NoopPlatformHttpConsumer extends DefaultConsumer implements PlatformHttpConsumer {

        private NoopPlatformHttpConsumer(Endpoint endpoint, Processor processor) {
            super(endpoint, processor);
        }

        @Override
        public PlatformHttpEndpoint getEndpoint() {
            return (PlatformHttpEndpoint) super.getEndpoint();
        }
    }

    private static final class ProfileBeanOAuthTokenValidationFactory implements OAuthTokenValidationFactory {

        private static String lastConfigurationProfileName;

        private static void reset() {
            lastConfigurationProfileName = null;
        }

        @Override
        public OAuthTokenValidationResult validateToken(OAuthTokenValidationConfig config, String token) {
            return OAuthTokenValidationResult.valid(
                    "profile-bean-user",
                    "https://issuer.example",
                    List.of("camel-api"),
                    List.of("read"),
                    Map.of(),
                    0);
        }

        @Override
        public void validateConfiguration(OAuthTokenValidationConfig config) {
        }

        @Override
        public void validateConfiguration(CamelContext context, String profileName) {
            lastConfigurationProfileName = profileName;
        }
    }
}
