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
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelAuthorizationException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.opa.security.OpaSecurityPolicy;
import org.apache.camel.test.infra.opa.services.OpaService;
import org.apache.camel.test.infra.opa.services.OpaServiceFactory;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test against a real OPA server evaluating the Rego policy in {@code src/test/resources/authz.rego}.
 */
public class OpaIT extends CamelTestSupport {

    @RegisterExtension
    static OpaService service = OpaServiceFactory.createSingletonService();

    private static final String POLICY_ID = "authz";

    @BeforeAll
    static void uploadPolicy() throws Exception {
        String rego;
        try (InputStream in = OpaIT.class.getResourceAsStream("/authz.rego")) {
            rego = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder()
                        .uri(URI.create(service.getOpaUrl() + "/v1/policies/" + POLICY_ID))
                        .header("Content-Type", "text/plain")
                        .PUT(HttpRequest.BodyPublishers.ofString(rego))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Could not load the test policy into OPA: " + response.body());
        }
    }

    private String opa(String path) {
        return "opa:" + path + "?serverUrl=" + service.getOpaUrl();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        OpaSecurityPolicy policy = new OpaSecurityPolicy(service.getOpaUrl(), "authz/allow");
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:guarded")
                        .policy(policy)
                        .to("mock:guarded");
            }
        };
    }

    @Test
    void allowsWhenThePolicyMatches() {
        Exchange out = template.request(opa("authz/allow"), e -> e.getMessage().setHeader("user", "alice"));

        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getHeader(OpaConstants.DECISION_ALLOW)).isEqualTo(true);
        assertThat(out.getMessage().getHeader(OpaConstants.POLICY_PATH)).isEqualTo("authz/allow");
    }

    @Test
    void deniesWhenThePolicyDoesNotMatch() {
        Exchange out = template.request(opa("authz/allow"), e -> e.getMessage().setHeader("user", "mallory"));

        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getHeader(OpaConstants.DECISION_ALLOW)).isEqualTo(false);
    }

    @Test
    void readsTheVerdictOutOfADecisionObject() {
        Exchange out = template.request(opa("authz/decision"), e -> e.getMessage().setHeader("user", "alice"));

        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getHeader(OpaConstants.DECISION_ALLOW)).isEqualTo(true);
        assertThat(out.getMessage().getHeader(OpaConstants.DECISION, Map.class))
                .containsEntry("allow", true);
    }

    @Test
    void keepsTheDenyReasonsFromADecisionObject() {
        Exchange out = template.request(opa("authz/decision"), e -> e.getMessage().setHeader("user", "mallory"));

        assertThat(out.getMessage().getHeader(OpaConstants.DECISION_ALLOW)).isEqualTo(false);
        assertThat(out.getMessage().getHeader(OpaConstants.DECISION, Map.class))
                .containsEntry("reasons", List.of("not the owner"));
    }

    @Test
    void failsClosedWhenTheServerIsNotReachable() {
        Exchange out = template.request("opa:authz/allow?serverUrl=http://localhost:1", e -> {
        });

        assertThat(out.getException()).isInstanceOf(OpaPolicyEvaluationException.class);
        assertThat(out.getMessage().getHeader(OpaConstants.DECISION_ALLOW)).isNull();
    }

    @Test
    void securityPolicyLetsAnAllowedExchangeThrough() throws Exception {
        MockEndpoint guarded = getMockEndpoint("mock:guarded");
        guarded.expectedMessageCount(1);

        Exchange out = template.request("direct:guarded", e -> e.getMessage().setHeader("role", "admin"));

        assertThat(out.getException()).isNull();
        guarded.assertIsSatisfied();
    }

    @Test
    void securityPolicyStopsADeniedExchange() throws Exception {
        MockEndpoint guarded = getMockEndpoint("mock:guarded");
        guarded.expectedMessageCount(0);

        Exchange out = template.request("direct:guarded", e -> e.getMessage().setHeader("user", "mallory"));

        assertThat(out.getException()).isInstanceOf(CamelAuthorizationException.class);
        guarded.assertIsSatisfied();
    }
}
