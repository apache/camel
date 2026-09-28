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
package org.apache.camel.component.openfga.security;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import dev.openfga.sdk.api.client.OpenFgaClient;
import dev.openfga.sdk.api.client.model.ClientCheckRequest;
import dev.openfga.sdk.api.client.model.ClientCheckResponse;
import org.apache.camel.CamelAuthorizationException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.openfga.OpenFgaConstants;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenFgaSecurityPolicyTest extends CamelTestSupport {

    private static final String STORE = "01HQMVAJXYZ0000000000000";

    private final OpenFgaClient client = mock(OpenFgaClient.class);

    private void givenVerdict(Boolean allowed) throws Exception {
        ClientCheckResponse response = mock(ClientCheckResponse.class);
        when(response.getAllowed()).thenReturn(allowed);
        when(client.check(any(ClientCheckRequest.class), any())).thenReturn(CompletableFuture.completedFuture(response));
    }

    private void givenServerIsUnreachable() throws Exception {
        when(client.check(any(ClientCheckRequest.class), any()))
                .thenReturn(CompletableFuture.failedFuture(new IOException("connection refused")));
    }

    private OpenFgaSecurityPolicy policy(boolean failOpen) {
        OpenFgaSecurityPolicy policy = new OpenFgaSecurityPolicy();
        policy.setStoreId(STORE);
        policy.setRelation("reader");
        policy.setUser("user:${exchangeProperty.subject}");
        policy.setObject("document:${header.documentId}");
        policy.setOpenFgaClient(client);
        policy.setFailOpen(failOpen);
        return policy;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:guarded")
                        .policy(policy(false))
                        .to("mock:allowed");

                from("direct:guardedFailOpen")
                        .policy(policy(true))
                        .to("mock:failOpen");
            }
        };
    }

    private Exchange send(String uri, String subject) {
        return template.request(uri, e -> {
            // the identity an authentication step in the route established, as an exchange property rather than a
            // header, so nothing outside the route could have set it
            e.setProperty("subject", subject);
            e.getMessage().setHeader("documentId", "budget");
        });
    }

    @Test
    void letsTheRouteRunWhenTheRelationshipExists() throws Exception {
        givenVerdict(Boolean.TRUE);
        MockEndpoint allowed = getMockEndpoint("mock:allowed");
        allowed.expectedMessageCount(1);

        Exchange out = send("direct:guarded", "anne");

        assertThat(out.getException()).isNull();
        allowed.assertIsSatisfied();
        assertThat(out.getMessage().getHeader(OpenFgaConstants.ALLOWED)).isEqualTo(true);
    }

    @Test
    void stopsTheRouteWithAnAuthorizationExceptionOnADeny() throws Exception {
        givenVerdict(Boolean.FALSE);
        MockEndpoint allowed = getMockEndpoint("mock:allowed");
        allowed.expectedMessageCount(0);

        Exchange out = send("direct:guarded", "bob");

        assertThat(out.getException()).isInstanceOf(CamelAuthorizationException.class);
        assertThat(out.getException()).hasMessageContaining("reader").hasMessageContaining("denied");
        assertThat(out.getMessage().getHeader(Exchange.AUTHENTICATION_FAILURE_POLICY_ID))
                .isEqualTo("OpenFgaSecurityPolicy");
        allowed.assertIsSatisfied();
    }

    @Test
    void deniesWhenOpenFgaCannotBeReached() throws Exception {
        givenServerIsUnreachable();
        MockEndpoint allowed = getMockEndpoint("mock:allowed");
        allowed.expectedMessageCount(0);

        Exchange out = send("direct:guarded", "anne");

        assertThat(out.getException()).isInstanceOf(CamelAuthorizationException.class);
        allowed.assertIsSatisfied();
    }

    @Test
    void letsTheRouteRunWhenOpenFgaCannotBeReachedAndFailOpenIsEnabled() throws Exception {
        givenServerIsUnreachable();
        MockEndpoint failOpen = getMockEndpoint("mock:failOpen");
        failOpen.expectedMessageCount(1);

        Exchange out = send("direct:guardedFailOpen", "anne");

        assertThat(out.getException()).isNull();
        failOpen.assertIsSatisfied();
    }

    @Test
    void deniesAnExchangeWithNoIdentityEvenWhenFailOpenIsEnabled() throws Exception {
        givenVerdict(Boolean.TRUE);
        MockEndpoint failOpen = getMockEndpoint("mock:failOpen");
        failOpen.expectedMessageCount(0);

        // no subject property: the exchange carried no identity, which is a decision and not a failure to reach
        // OpenFGA, so failOpen must not turn it into an allow
        Exchange out = template.request("direct:guardedFailOpen",
                e -> e.getMessage().setHeader("documentId", "budget"));

        assertThat(out.getException()).isInstanceOf(CamelAuthorizationException.class);
        assertThat(out.getException()).hasMessageContaining("missing-user");
        failOpen.assertIsSatisfied();
    }

    @Test
    void registersAReadinessCheckOnlyForAClientItBuiltItself() {
        // an injected client can point anywhere and the policy has no way to ask it where, so probing the configured
        // URL would report on a server it may never talk to
        assertThat(context.getCamelContextExtension().getContextPlugin(
                org.apache.camel.health.HealthCheckRegistry.class).stream()
                .filter(check -> check.getId().contains("openfga"))
                .toList()).isEmpty();
    }
}
