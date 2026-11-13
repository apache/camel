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
package org.apache.camel.component.knative.http;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.http.HttpHeaders;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.cloudevents.CloudEvent;
import org.apache.camel.cloudevents.CloudEvents;
import org.apache.camel.component.knative.KnativeComponent;
import org.apache.camel.component.knative.spi.Knative;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.PropertyBindingSupport;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import static io.restassured.RestAssured.given;
import static org.apache.camel.component.knative.KnativeEnvironmentSupport.sourceEndpoint;
import static org.apache.camel.component.knative.http.KnativeHttpTestSupport.configurePlatformHttpComponent;
import static org.apache.camel.component.knative.http.KnativeHttpTestSupport.httpAttribute;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The consumer with OIDC enabled accepts a request only when its Authorization header is the token mounted at the token
 * path, and follows a renewal of that token: the renewed token is accepted and the one it replaces is refused.
 */
class KnativeHttpOidcConsumerTest {

    private static final CloudEvent CE = CloudEvents.v1_0;

    @RegisterExtension
    AvailablePortFinder.Port platformHttpPort = AvailablePortFinder.find();

    @TempDir
    Path tempDir;

    private CamelContext context;
    private Path tokenFile;

    @BeforeEach
    void before() throws Exception {
        context = new DefaultCamelContext();
        configurePlatformHttpComponent(context, platformHttpPort.getPort());
        RestAssured.port = platformHttpPort.getPort();

        tokenFile = tempDir.resolve("token");
        writeToken("the_mounted_token");
    }

    @AfterEach
    void after() {
        context.stop();
    }

    @Test
    void acceptsTheMountedToken() throws Exception {
        startConsumer(serviceOptions());

        assertThat(post("Bearer the_mounted_token")).isEqualTo(200);
    }

    @Test
    void refusesAnotherToken() throws Exception {
        startConsumer(serviceOptions());
        MockEndpoint mock = context.getEndpoint("mock:result", MockEndpoint.class);
        mock.expectedMessageCount(0);

        assertThat(post("Bearer another_token")).isEqualTo(401);
        // a prefix or a longer value of the token is not the token
        assertThat(post("Bearer the_mounted_toke")).isEqualTo(401);
        assertThat(post("Bearer the_mounted_token2")).isEqualTo(401);
        assertThat(post("the_mounted_token")).isEqualTo(401);

        mock.assertIsSatisfied();
    }

    @Test
    void refusesARequestWithoutAuthorization() throws Exception {
        startConsumer(serviceOptions());

        assertThat(post(null)).isEqualTo(401);
    }

    @Test
    void acceptsARenewedTokenAndRefusesTheOldOne() throws Exception {
        startConsumer(serviceOptions());

        // the token is read and cached
        assertThat(post("Bearer the_mounted_token")).isEqualTo(200);

        // Knative renews the token in the volume mount
        writeToken("the_renewed_token");

        assertThat(post("Bearer the_renewed_token")).isEqualTo(200);
        assertThat(post("Bearer the_mounted_token")).isEqualTo(401);
        assertThat(post("Bearer the_renewed_token")).isEqualTo(200);
    }

    @Test
    void acceptsTheOldTokenUntilTheRenewedOneIsUsed() throws Exception {
        startConsumer(serviceOptions());

        assertThat(post("Bearer the_mounted_token")).isEqualTo(200);

        writeToken("the_renewed_token");

        // the cached token is read again when a comparison fails, so the old token is accepted until the sender
        // uses the renewed one
        assertThat(post("Bearer the_mounted_token")).isEqualTo(200);
        assertThat(post("Bearer the_renewed_token")).isEqualTo(200);
        assertThat(post("Bearer the_mounted_token")).isEqualTo(401);
    }

    @Test
    void acceptsARenewedTokenWithoutTheCache() throws Exception {
        KnativeOidcServiceOptions options = serviceOptions();
        options.setCacheTokens(false);
        startConsumer(options);

        assertThat(post("Bearer the_mounted_token")).isEqualTo(200);

        writeToken("the_renewed_token");

        assertThat(post("Bearer the_mounted_token")).isEqualTo(401);
        assertThat(post("Bearer the_renewed_token")).isEqualTo(200);
    }

    @Test
    void configuredWithProperties() throws Exception {
        context.getPropertiesComponent().addInitialProperty("camel.knative.service.oidc.enabled", "true");
        context.getPropertiesComponent().addInitialProperty("camel.knative.service.oidc.token.path", "file:" + tokenFile);

        KnativeComponent component = configureKnativeComponent();
        PropertyBindingSupport.build().bind(context, component,
                "consumerFactory.serviceOptions", "#class:" + KnativeOidcServiceOptions.class.getName());
        startRoute();

        assertThat(post("Bearer the_mounted_token")).isEqualTo(200);
        assertThat(post("Bearer another_token")).isEqualTo(401);
    }

    private KnativeOidcServiceOptions serviceOptions() {
        KnativeOidcServiceOptions options = new KnativeOidcServiceOptions(context);
        options.setOidcEnabled(true);
        options.setOidcTokenPath("file:" + tokenFile);
        return options;
    }

    private void writeToken(String token) throws Exception {
        // the mounted token file ends with a new line, which is not part of the token
        Files.writeString(tokenFile, token + "\n", StandardCharsets.UTF_8);
    }

    private void startConsumer(KnativeOidcServiceOptions options) throws Exception {
        KnativeComponent component = configureKnativeComponent();
        if (component.getConsumerFactory() instanceof KnativeHttpConsumerFactory consumerFactory) {
            consumerFactory.setServiceOptions(options);
        }
        startRoute();
    }

    private KnativeComponent configureKnativeComponent() throws Exception {
        return KnativeHttpTestSupport.configureKnativeComponent(context, CE,
                sourceEndpoint("from", Map.of(
                        Knative.KNATIVE_CLOUD_EVENT_TYPE, "org.apache.camel.event",
                        Knative.CONTENT_TYPE, "text/plain")));
    }

    private void startRoute() throws Exception {
        RouteBuilder.addRoutes(context, b -> b.from("knative:endpoint/from")
                .to("mock:result")
                .setBody().constant("reply"));
        context.start();
    }

    private int post(String authorization) {
        RequestSpecification request = given()
                .body("test")
                .header(Exchange.CONTENT_TYPE, "text/plain")
                .header(httpAttribute(CE, CloudEvent.CAMEL_CLOUD_EVENT_VERSION), CE.version())
                .header(httpAttribute(CE, CloudEvent.CAMEL_CLOUD_EVENT_TYPE), "org.apache.camel.event")
                .header(httpAttribute(CE, CloudEvent.CAMEL_CLOUD_EVENT_ID), "myEventID")
                .header(httpAttribute(CE, CloudEvent.CAMEL_CLOUD_EVENT_SOURCE), "/somewhere");
        if (authorization != null) {
            request.header(HttpHeaders.AUTHORIZATION.toString(), authorization);
        }
        return request.when().post().statusCode();
    }
}
