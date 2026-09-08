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

import java.util.Date;
import java.util.Map;

import com.styra.opa.OPAClient;
import com.styra.opa.OPAException;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the shape of the {@code input} document handed to OPA.
 */
class OpaInputDocumentTest extends CamelTestSupport {

    private static final String PATH = "authz/allow";
    private static final String ENDPOINT = "opa:" + PATH + "?opaClient=#opaClient";

    @BindToRegistry("opaClient")
    private final OPAClient client = mock(OPAClient.class);

    @SuppressWarnings("unchecked")
    private Map<String, Object> inputSentFor(String endpoint, Processor exchangeSetup) throws OPAException {
        when(client.evaluate(eq(PATH), anyMap(), eq(Object.class))).thenReturn(Boolean.TRUE);
        template.request(endpoint, exchangeSetup);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(client).evaluate(eq(PATH), captor.capture(), eq(Object.class));
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> headersOf(Map<String, Object> input) {
        return (Map<String, Object>) input.get("headers");
    }

    @Test
    void sendsHeadersAndRoutingContextButNotTheBodyByDefault() throws Exception {
        Map<String, Object> input = inputSentFor(ENDPOINT, e -> {
            e.getMessage().setHeader("user", "alice");
            e.getMessage().setBody("the payload");
        });

        assertThat(headersOf(input)).containsEntry("user", "alice");
        assertThat(input).doesNotContainKey("body");
        assertThat(input).containsKey("exchangeId");
    }

    @Test
    void sendsTheBodyWhenIncludeBodyIsEnabled() throws Exception {
        Map<String, Object> input = inputSentFor(ENDPOINT + "&includeBody=true",
                e -> e.getMessage().setBody(Map.of("amount", 42)));

        assertThat(input).containsEntry("body", Map.of("amount", 42));
    }

    @Test
    void convertsANonJsonBodyToItsStringForm() throws Exception {
        Map<String, Object> input = inputSentFor(ENDPOINT + "&includeBody=true",
                e -> e.getMessage().setBody("the payload".getBytes()));

        assertThat(input).containsEntry("body", "the payload");
    }

    @Test
    void sendsOnlyTheListedHeadersWhenNarrowed() throws Exception {
        Map<String, Object> input = inputSentFor(ENDPOINT + "&includeHeaders=user,tenant", e -> {
            e.getMessage().setHeader("user", "alice");
            e.getMessage().setHeader("tenant", "acme");
            e.getMessage().setHeader("Authorization", "Bearer secret");
        });

        assertThat(headersOf(input)).containsOnlyKeys("user", "tenant");
    }

    @Test
    void matchesTheListedHeaderNamesCaseInsensitively() throws Exception {
        Map<String, Object> input = inputSentFor(ENDPOINT + "&includeHeaders=user",
                e -> e.getMessage().setHeader("USER", "alice"));

        assertThat(headersOf(input)).containsEntry("USER", "alice");
    }

    @Test
    void neverSendsBackItsOwnDecisionHeaders() throws Exception {
        Map<String, Object> input = inputSentFor(ENDPOINT, e -> {
            e.getMessage().setHeader(OpaConstants.DECISION_ALLOW, true);
            e.getMessage().setHeader(OpaConstants.DECISION, "forged");
            e.getMessage().setHeader(OpaConstants.POLICY_PATH, "authz/always_allow");
            e.getMessage().setHeader("user", "alice");
        });

        assertThat(headersOf(input)).containsOnlyKeys("user");
    }

    @Test
    void convertsANonJsonHeaderValueToItsStringForm() throws Exception {
        Map<String, Object> input = inputSentFor(ENDPOINT,
                e -> e.getMessage().setHeader("when", new Date(0)));

        assertThat(headersOf(input).get("when")).isInstanceOf(String.class);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> propertiesOf(Map<String, Object> input) {
        return (Map<String, Object>) input.get("properties");
    }

    @Test
    void sendsNoExchangePropertiesByDefault() throws Exception {
        Map<String, Object> input = inputSentFor(ENDPOINT, e -> e.setProperty("subject", "alice"));

        assertThat(input).doesNotContainKey("properties");
    }

    @Test
    void sendsOnlyTheListedExchangeProperties() throws Exception {
        Map<String, Object> input = inputSentFor(ENDPOINT + "&includeProperties=subject", e -> {
            e.setProperty("subject", "alice");
            e.setProperty("internalScratch", "not for the policy");
        });

        assertThat(propertiesOf(input)).containsOnlyKeys("subject").containsEntry("subject", "alice");
    }

    @Test
    void omitsPropertiesKeyWhenIncludeMatchesNothing() throws Exception {
        // includeProperties is configured but the exchange carries none of the listed properties, so the
        // "properties" key must be absent rather than present-and-empty (otherwise has(input, "properties") lies)
        Map<String, Object> input = inputSentFor(ENDPOINT + "&includeProperties=subject",
                e -> e.setProperty("internalScratch", "not for the policy"));

        assertThat(input).doesNotContainKey("properties");
    }

    @Test
    void sendsEveryExchangePropertyWhenAskedForAll() throws Exception {
        Map<String, Object> input = inputSentFor(ENDPOINT + "&includeProperties=*", e -> {
            e.setProperty("subject", "alice");
            e.setProperty("tenant", "acme");
        });

        assertThat(propertiesOf(input)).containsEntry("subject", "alice").containsEntry("tenant", "acme");
    }

    @Test
    void matchesTheListedPropertyNamesCaseInsensitively() throws Exception {
        Map<String, Object> input = inputSentFor(ENDPOINT + "&includeProperties=subject",
                e -> e.setProperty("SUBJECT", "alice"));

        assertThat(propertiesOf(input)).containsEntry("SUBJECT", "alice");
    }

    @Test
    void convertsANonJsonPropertyValueToItsStringForm() throws Exception {
        Map<String, Object> input = inputSentFor(ENDPOINT + "&includeProperties=when",
                e -> e.setProperty("when", new Date(0)));

        assertThat(propertiesOf(input).get("when")).isInstanceOf(String.class);
    }

    @Test
    void keepsHeadersAndPropertiesInSeparateObjects() throws Exception {
        Map<String, Object> input = inputSentFor(ENDPOINT + "&includeProperties=subject", e -> {
            e.getMessage().setHeader("subject", "mallory");
            e.setProperty("subject", "alice");
        });

        assertThat(headersOf(input)).containsEntry("subject", "mallory");
        assertThat(propertiesOf(input)).containsEntry("subject", "alice");
    }

    @SuppressWarnings("unchecked")
    @Test
    void reportsTheRouteTheExchangeCameFrom() throws Exception {
        when(client.evaluate(eq(PATH), anyMap(), eq(Object.class))).thenReturn(Boolean.TRUE);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("orders").to(ENDPOINT);
            }
        });

        Exchange out = template.request("direct:start", e -> {
        });

        assertThat(out.getException()).isNull();
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(client).evaluate(eq(PATH), captor.capture(), eq(Object.class));
        assertThat(captor.getValue()).containsEntry("routeId", "orders");
    }
}
