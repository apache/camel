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
package org.apache.camel.component.platform.http.vertx;

import io.restassured.RestAssured;
import io.vertx.core.AsyncResult;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.authentication.AuthenticationProvider;
import io.vertx.ext.auth.impl.UserImpl;
import io.vertx.ext.web.handler.BasicAuthHandler;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.platform.http.vertx.auth.AuthenticationConfig;
import org.apache.camel.component.platform.http.vertx.auth.AuthenticationConfig.AuthenticationConfigEntry;
import org.apache.camel.component.platform.http.vertx.auth.AuthenticationConfig.AuthenticationHandlerFactory;
import org.apache.camel.component.platform.http.vertx.auth.AuthenticationConfig.AuthenticationProviderFactory;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

public class VertxPlatformHttpAuthenticationTest {

    @Test
    public void testAuthenticationDisabled() throws Exception {
        CamelContext context = createCamelContext(authenticationConfig -> {
            //authentication disabled by default
        });

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("platform-http:/disabledAuth")
                        .setBody().constant("disabledAuth");
            }
        });

        try {
            context.start();

            given()
                    .when()
                    .get("/disabledAuth")
                    .then()
                    .statusCode(200)
                    .body(equalTo("disabledAuth"));
        } finally {
            context.stop();
        }
    }

    @Test
    public void testDefaultAuthenticationConfig() throws Exception {
        CamelContext context = createCamelContext(authenticationConfig -> {
            authenticationConfig.setEnabled(true);
        });

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("platform-http:/defaultAuth")
                        .setBody().constant("defaultAuth");
            }
        });

        try {
            context.start();

            given()
                    .when()
                    .get("/defaultAuth")
                    .then()
                    .statusCode(401)
                    .body(equalTo("Unauthorized"));

            given()
                    .auth().basic("camel", "propertiesPass")
                    .when()
                    .get("/defaultAuth")
                    .then()
                    .statusCode(200)
                    .body(equalTo("defaultAuth"));

        } finally {
            context.stop();
        }
    }

    @Test
    public void testAuthenticateSpecificPathOnly() throws Exception {
        CamelContext context = createCamelContext(authenticationConfig -> {
            authenticationConfig.setEnabled(true);
            authenticationConfig.getEntries().get(0).setPath("/specific/path");
        });

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("platform-http:/specific/path")
                        .setBody().constant("specificPath");

                from("platform-http:/unprotected")
                        .setBody().constant("unprotected");
            }
        });

        try {
            context.start();

            given()
                    .when()
                    .get("/specific/path")
                    .then()
                    .statusCode(401)
                    .body(equalTo("Unauthorized"));

            given()
                    .auth().basic("camel", "propertiesPass")
                    .when()
                    .get("/specific/path")
                    .then()
                    .statusCode(200)
                    .body(equalTo("specificPath"));

            given()
                    .when()
                    .get("/unprotected")
                    .then()
                    .statusCode(200)
                    .body(equalTo("unprotected"));

        } finally {
            context.stop();
        }
    }

    @Test
    public void testAddingCustomAuthenticationProvider() throws Exception {
        CamelContext context = createCamelContext(authenticationConfig -> {
            authenticationConfig.setEnabled(true);
            AuthenticationProviderFactory provider = createCustomProvider("CustomUser", "CustomPass");
            AuthenticationHandlerFactory handler = BasicAuthHandler::create;
            AuthenticationConfigEntry customEntry = new AuthenticationConfigEntry();
            customEntry.setPath("/custom/provider");
            customEntry.setAuthenticationProviderFactory(provider);
            customEntry.setAuthenticationHandlerFactory(handler);
            authenticationConfig.getEntries().add(customEntry);
        });

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("platform-http:/custom/provider")
                        .setBody().constant("customProvider");

                from("platform-http:/defaultAuth")
                        .setBody().constant("defaultAuth");
            }
        });

        try {
            context.start();

            given()
                    .when()
                    .get("/defaultAuth")
                    .then()
                    .statusCode(401)
                    .body(equalTo("Unauthorized"));

            given()
                    .auth().basic("camel", "propertiesPass")
                    .when()
                    .get("/defaultAuth")
                    .then()
                    .statusCode(200)
                    .body(equalTo("defaultAuth"));

            given()
                    .when()
                    .get("/custom/provider")
                    .then()
                    .statusCode(401)
                    .body(equalTo("Unauthorized"));

            given()
                    .auth().basic("CustomUser", "CustomPass")
                    .when()
                    .get("/custom/provider")
                    .then()
                    .statusCode(200)
                    .body(equalTo("customProvider"));

        } finally {
            context.stop();
        }
    }

    @Test
    public void testAuthenticateSpecificPathWithCustomAuthenticationProvider() throws Exception {
        CamelContext context = createCamelContext(authenticationConfig -> {
            authenticationConfig.setEnabled(true);
            AuthenticationProviderFactory provider = createCustomProvider("CustomUser", "CustomPass");
            AuthenticationHandlerFactory handler = BasicAuthHandler::create;
            AuthenticationConfigEntry customEntry = new AuthenticationConfigEntry();
            customEntry.setPath("/customProvider");
            customEntry.setAuthenticationProviderFactory(provider);
            customEntry.setAuthenticationHandlerFactory(handler);
            authenticationConfig.getEntries().add(customEntry);
        });

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("platform-http:/customProvider")
                        .setBody().constant("customProvider");

                from("platform-http:/defaultAuth")
                        .setBody().constant("defaultAuth");
            }
        });

        try {
            context.start();

            given()
                    .when()
                    .get("/defaultAuth")
                    .then()
                    .statusCode(401)
                    .body(equalTo("Unauthorized"));

            given()
                    .auth().basic("camel", "propertiesPass")
                    .when()
                    .get("/defaultAuth")
                    .then()
                    .statusCode(200)
                    .body(equalTo("defaultAuth"));

            given()
                    .when()
                    .get("/customProvider")
                    .then()
                    .statusCode(401)
                    .body(equalTo("Unauthorized"));

            given()
                    .auth().basic("CustomUser", "CustomPass")
                    .when()
                    .get("/customProvider")
                    .then()
                    .statusCode(200)
                    .body(equalTo("customProvider"));

        } finally {
            context.stop();
        }
    }

    private AuthenticationProviderFactory createCustomProvider(String username, String pass) {
        return vertx -> (AuthenticationProvider) (credentials, resultHandler) -> {
            boolean usernameMatched = credentials.getString("username").equals(username);
            boolean passwordMatched = credentials.getString("password").equals(pass);
            if (usernameMatched && passwordMatched) {
                AsyncResult<User> result = getUser();
                resultHandler.handle(result);
            }
        };
    }

    private AsyncResult<User> getUser() {
        return new AsyncResult<>() {
            @Override
            public User result() {
                return new UserImpl();
            }

            @Override
            public Throwable cause() {
                return null;
            }

            @Override
            public boolean succeeded() {
                return true;
            }

            @Override
            public boolean failed() {
                return false;
            }
        };
    }

    private CamelContext createCamelContext(AuthenticationConfigCustomizer customizer)
            throws Exception {
        int bindPort = AvailablePortFinder.getNextAvailable();
        RestAssured.port = bindPort;
        return createCamelContext(bindPort, customizer);
    }

    private CamelContext createCamelContext(int bindPort, AuthenticationConfigCustomizer customizer)
            throws Exception {
        VertxPlatformHttpServerConfiguration conf = new VertxPlatformHttpServerConfiguration();
        conf.setBindPort(bindPort);

        AuthenticationConfig authenticationConfig = new AuthenticationConfig();
        customizer.customize(authenticationConfig);
        conf.setAuthenticationConfig(authenticationConfig);

        CamelContext camelContext = new DefaultCamelContext();
        camelContext.addService(new VertxPlatformHttpServer(conf));
        return camelContext;
    }

    interface AuthenticationConfigCustomizer {
        void customize(AuthenticationConfig authenticationConfig);
    }
}
