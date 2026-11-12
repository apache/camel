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
package org.apache.camel.component.spiffe.integration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import io.spiffe.exception.JwtSvidException;
import org.apache.camel.CamelAuthorizationException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.spiffe.SpiffeComponent;
import org.apache.camel.component.spiffe.SpiffeConstants;
import org.apache.camel.component.spiffe.SpiffeJwtIdentity;
import org.apache.camel.component.spiffe.SpiffeJwtSecurityPolicy;
import org.apache.camel.test.infra.spiffe.services.SpiffeService;
import org.apache.camel.test.infra.spiffe.services.SpiffeServiceFactory;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the camel-spiffe producer against a real SPIRE Workload API: fetching an X509-SVID and a JWT-SVID, and
 * validating a JWT-SVID (accepting the configured audience, rejecting another).
 * <p>
 * Linux only: the SPIRE agent is run in the host PID namespace with the Workload API socket bind-mounted, which works
 * against a Linux Docker or Podman engine (rootful or rootless) but not Docker Desktop's VM, so the test is skipped off
 * Linux rather than left to fail on a developer machine.
 */
@EnabledOnOs(OS.LINUX)
class SpiffeWorkloadApiIT extends CamelTestSupport {

    @RegisterExtension
    static SpiffeService service = SpiffeServiceFactory.createSingletonService();

    private static String socket() {
        return "RAW(" + service.getWorkloadApiSocketPath() + ")";
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // The JWT policy inherits the component's Workload API socket; its audience remains policy-owned.
                getContext().getComponent("spiffe", SpiffeComponent.class).getConfiguration()
                        .setSpiffeSocketPath(service.getWorkloadApiSocketPath());
                from("direct:x509")
                        .toF("spiffe:x509?operation=fetchX509Svid&spiffeSocketPath=%s", socket());
                from("direct:jwt")
                        .toF("spiffe:jwt?operation=fetchJwtSvid&audience=my-audience&spiffeSocketPath=%s", socket());
                from("direct:validateCorrectAudience")
                        .toF("spiffe:valOk?operation=validateJwtSvid&audience=my-audience&spiffeSocketPath=%s", socket());
                from("direct:validateWrongAudience")
                        .toF("spiffe:valWrong?operation=validateJwtSvid&audience=other-audience&spiffeSocketPath=%s",
                                socket());
                from("direct:jwtPolicy").routeId("jwtPolicy")
                        .process(SpiffeWorkloadApiIT::capturePolicyBody)
                        .policy(jwtPolicy("my-audience"))
                        .setProperty("guardedProcessorRan", constant(true));
                from("direct:wrongAudiencePolicy")
                        .process(SpiffeWorkloadApiIT::capturePolicyBody)
                        .policy(jwtPolicy("other-audience"))
                        .setProperty("guardedProcessorRan", constant(true));
            }
        };
    }

    private static SpiffeJwtSecurityPolicy jwtPolicy(String audience) {
        SpiffeJwtSecurityPolicy policy = new SpiffeJwtSecurityPolicy();
        policy.setAudience(audience);
        policy.setAcceptedSpiffeIds(service.getWorkloadSpiffeId());
        return policy;
    }

    @Test
    void fetchesAnX509SvidForThisWorkload() {
        Exchange out = template.request("direct:x509", e -> {
        });

        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getHeader(SpiffeConstants.SPIFFE_ID))
                .isEqualTo(service.getWorkloadSpiffeId());
        // the default x509Response is the certificate chain, which must carry at least the leaf certificate
        assertThat(out.getMessage().getBody(List.class)).isNotEmpty();
    }

    @Test
    void fetchesAJwtSvidForThisWorkload() {
        Exchange out = template.request("direct:jwt", e -> {
        });

        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getHeader(SpiffeConstants.SPIFFE_ID))
                .isEqualTo(service.getWorkloadSpiffeId());
        // a JWT-SVID is a compact JWT: three base64url parts separated by dots
        assertThat(out.getMessage().getBody(String.class).split("\\.")).hasSize(3);
    }

    @Test
    void validatesAJwtSvidAgainstItsAudience() {
        String token = template.requestBody("direct:jwt", null, String.class);

        Exchange out = template.request("direct:validateCorrectAudience",
                e -> e.getMessage().setHeader(SpiffeConstants.TOKEN, token));

        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getHeader(SpiffeConstants.SPIFFE_ID))
                .isEqualTo(service.getWorkloadSpiffeId());
    }

    @Test
    void rejectsAJwtSvidMintedForAnotherAudience() {
        String token = template.requestBody("direct:jwt", null, String.class);

        Exchange out = template.request("direct:validateWrongAudience",
                e -> e.getMessage().setHeader(SpiffeConstants.TOKEN, token));

        // the token is valid but minted for my-audience, so validation against other-audience must fail
        assertThat(out.getException()).isInstanceOf(JwtSvidException.class);
    }

    @Test
    void jwtPolicyPreservesThePostBodyRejectsTheWrongAudienceAndValidatesAgainAfterRouteRestart() throws Exception {
        String token = template.requestBody("direct:jwt", null, String.class);
        byte[] payload = "{\"order\":123}".getBytes(StandardCharsets.UTF_8);

        ByteArrayInputStream body = new ByteArrayInputStream(payload);
        Exchange allowed = policyRequest("direct:jwtPolicy", token, body);
        assertThat(allowed.getException()).isNull();
        assertPolicyBodyPreserved(allowed, payload);
        assertThat(allowed.getProperty("guardedProcessorRan")).isEqualTo(true);
        assertThat(allowed.getProperty(SpiffeConstants.JWT_IDENTITY, SpiffeJwtIdentity.class).getSpiffeId())
                .isEqualTo(service.getWorkloadSpiffeId());
        assertThat(allowed.getMessage().getHeader(SpiffeConstants.SPIFFE_ID)).isNull();
        assertThat(allowed.getMessage().getHeader(SpiffeConstants.EXPIRY)).isNull();

        ByteArrayInputStream rejectedBody = new ByteArrayInputStream(payload);
        Exchange rejected = policyRequest("direct:wrongAudiencePolicy", token, rejectedBody);
        assertThat(rejected.getException()).isInstanceOf(CamelAuthorizationException.class);
        assertThat(rejected.getProperty("guardedProcessorRan")).isNull();
        assertThat(rejected.getProperty(SpiffeConstants.JWT_IDENTITY)).isNull();
        assertPolicyBodyPreserved(rejected, payload);

        context.getRouteController().stopRoute("jwtPolicy");
        context.getRouteController().startRoute("jwtPolicy");
        ByteArrayInputStream restartedBody = new ByteArrayInputStream(payload);
        Exchange restarted = policyRequest("direct:jwtPolicy", token, restartedBody);
        assertThat(restarted.getException()).isNull();
        assertThat(restarted.getProperty("guardedProcessorRan")).isEqualTo(true);
        assertPolicyBodyPreserved(restarted, payload);
    }

    private static void capturePolicyBody(Exchange exchange) throws IOException {
        // Camel may cache the caller's stream before entering this route. Compare at the policy boundary so the
        // assertion detects policy replacement or consumption, rather than the framework's stream-cache wrapper.
        Object body = exchange.getMessage().getBody();
        exchange.setProperty("bodyBeforeJwtPolicy", body);
        exchange.setProperty("availableBeforeJwtPolicy", ((InputStream) body).available());
    }

    private static void assertPolicyBodyPreserved(Exchange exchange, byte[] payload) throws IOException {
        Object actual = exchange.getMessage().getBody();
        assertThat(actual).isSameAs(exchange.getProperty("bodyBeforeJwtPolicy")).isInstanceOf(InputStream.class);
        InputStream body = (InputStream) actual;
        assertThat(body.available()).isEqualTo(exchange.getProperty("availableBeforeJwtPolicy", Integer.class))
                .isEqualTo(payload.length);
        assertThat(body.readAllBytes()).containsExactly(payload);
    }

    private Exchange policyRequest(String route, String token, ByteArrayInputStream body) {
        return template.request(route, exchange -> {
            exchange.getMessage().setHeader(Exchange.HTTP_METHOD, "POST");
            exchange.getMessage().setHeader("Authorization", "Bearer " + token);
            exchange.getMessage().setHeader(SpiffeConstants.SPIFFE_ID, "unverified-sender-identity");
            exchange.setProperty(SpiffeConstants.JWT_IDENTITY, "stale-identity");
            exchange.getMessage().setBody(body);
        });
    }
}
