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
package org.apache.camel.component.opa;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import org.apache.camel.Exchange;
import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.opa.common.OpaProperties;
import org.apache.camel.test.infra.opa.services.OpaLocalContainerInfraService;
import org.apache.camel.test.infra.opa.services.OpaService;
import org.apache.camel.test.infra.opa.services.OpaServiceFactory;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the {@code bearerToken} option actually authenticates against an OPA server that requires it.
 * <p/>
 * The server runs with {@code --authentication=token --authorization=basic} and the system authorization policy in
 * {@code system-authz.rego}, so a request without the configured token is rejected by OPA rather than answered.
 */
public class OpaBearerTokenIT extends CamelTestSupport {

    private static final String TOKEN = "s3cr3t-opa-token";
    private static final String PATH = "authz/allow";

    @RegisterExtension
    static OpaService service = OpaServiceFactory.builder()
            .addLocalMapping(() -> new AuthenticatingOpaService(authenticatingContainer()))
            .addRemoteMapping(OpaServiceFactory.OpaRemoteTestService::new)
            .build();

    public static class AuthenticatingOpaService extends OpaLocalContainerInfraService implements OpaService {
        public AuthenticatingOpaService(GenericContainer<?> container) {
            super(container);
        }
    }

    @SuppressWarnings("resource")
    private static GenericContainer<?> authenticatingContainer() {
        // the image comes from the test-infra container.properties, so it stays in one place
        String image = LocalPropertyResolver.getProperty(
                OpaLocalContainerInfraService.class, OpaProperties.OPA_CONTAINER);

        return new GenericContainer<>(image) // NOSONAR
                .withExposedPorts(OpaLocalContainerInfraService.OPA_PORT)
                .withCopyFileToContainer(
                        MountableFile.forClasspathResource("system-authz.rego"), "/policies/system-authz.rego")
                .withCommand("run", "--server", "--addr=0.0.0.0:" + OpaLocalContainerInfraService.OPA_PORT,
                        "--authentication=token", "--authorization=basic", "/policies")
                .waitingFor(Wait.forHttp("/health").forPort(OpaLocalContainerInfraService.OPA_PORT)
                        .forStatusCode(200));
    }

    @BeforeAll
    static void uploadPolicy() throws Exception {
        String rego;
        try (InputStream in = OpaBearerTokenIT.class.getResourceAsStream("/authz.rego")) {
            rego = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        // uploading already requires the token, since the system authorization policy protects every endpoint
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder()
                        .uri(URI.create(service.getOpaUrl() + "/v1/policies/authz"))
                        .header("Content-Type", "text/plain")
                        .header("Authorization", "Bearer " + TOKEN)
                        .PUT(HttpRequest.BodyPublishers.ofString(rego))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Could not load the test policy into OPA: " + response.body());
        }
    }

    @Test
    void evaluatesThePolicyWhenTheTokenIsConfigured() {
        Exchange out = template.request(
                "opa:" + PATH + "?serverUrl=" + service.getOpaUrl() + "&bearerToken=" + TOKEN,
                e -> e.getMessage().setHeader("user", "alice"));

        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getHeader(OpaConstants.DECISION_ALLOW)).isEqualTo(true);
    }

    @Test
    void failsClosedWhenNoTokenIsConfigured() {
        Exchange out = template.request(
                "opa:" + PATH + "?serverUrl=" + service.getOpaUrl(),
                e -> e.getMessage().setHeader("user", "alice"));

        assertThat(out.getException()).isInstanceOf(OpaPolicyEvaluationException.class);
        assertThat(out.getMessage().getHeader(OpaConstants.DECISION_ALLOW)).isNull();
    }

    @Test
    void failsClosedWhenTheTokenIsWrong() {
        Exchange out = template.request(
                "opa:" + PATH + "?serverUrl=" + service.getOpaUrl() + "&bearerToken=not-the-token",
                e -> e.getMessage().setHeader("user", "alice"));

        assertThat(out.getException()).isInstanceOf(OpaPolicyEvaluationException.class);
        assertThat(out.getMessage().getHeader(OpaConstants.DECISION_ALLOW)).isNull();
    }
}
