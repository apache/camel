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
package org.apache.camel.component.opa.security;

import java.util.Map;

import com.styra.opa.OPAClient;
import com.styra.opa.OPAException;
import org.apache.camel.CamelAuthorizationException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.opa.OpaConstants;
import org.apache.camel.component.opa.OpaPolicyEvaluationException;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpaSecurityPolicyTest extends CamelTestSupport {

    private static final String PATH = "authz/orders/allow";

    private final OPAClient client = mock(OPAClient.class);
    private final OpaSecurityPolicy policy = new OpaSecurityPolicy();

    @Override
    protected RouteBuilder createRouteBuilder() {
        policy.setPolicyPath(PATH);
        policy.setOpaClient(client);
        policy.setIncludeProperties("subject");
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .policy(policy)
                        .to("mock:result");
            }
        };
    }

    private void givenDecision(Object decision) throws OPAException {
        when(client.evaluate(eq(PATH), anyMap(), eq(Object.class))).thenReturn(decision);
    }

    @Test
    void letsTheRouteRunWhenThePolicyAllows() throws Exception {
        givenDecision(Boolean.TRUE);
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(1);

        Exchange out = template.request("direct:start", e -> e.getMessage().setBody("an order"));

        assertThat(out.getException()).isNull();
        result.assertIsSatisfied();
        assertThat(out.getMessage().getHeader(OpaConstants.DECISION_ALLOW)).isEqualTo(true);
    }

    @Test
    void stopsTheRouteWhenThePolicyDenies() throws Exception {
        givenDecision(Boolean.FALSE);
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(0);

        Exchange out = template.request("direct:start", e -> e.getMessage().setBody("an order"));

        assertThat(out.getException()).isInstanceOf(CamelAuthorizationException.class)
                .hasMessageContaining(PATH);
        assertThat(out.getMessage().getHeader(Exchange.AUTHENTICATION_FAILURE_POLICY_ID))
                .isEqualTo("OpaSecurityPolicy");
        result.assertIsSatisfied();
    }

    @Test
    void deniesWhenTheDecisionCarriesNoBooleanVerdict() throws Exception {
        givenDecision(Map.of("deny", "not an owner"));

        Exchange out = template.request("direct:start", e -> e.getMessage().setBody("an order"));

        assertThat(out.getException()).isInstanceOf(CamelAuthorizationException.class);
        assertThat(out.getMessage().getHeader(OpaConstants.DECISION)).isEqualTo(Map.of("deny", "not an owner"));
    }

    @Test
    void deniesWhenThePolicyCannotBeEvaluated() throws Exception {
        when(client.evaluate(eq(PATH), anyMap(), eq(Object.class))).thenThrow(new OPAException("connection refused"));
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(0);

        Exchange out = template.request("direct:start", e -> e.getMessage().setBody("an order"));

        assertThat(out.getException()).isInstanceOf(CamelAuthorizationException.class)
                .hasCauseInstanceOf(OpaPolicyEvaluationException.class);
        result.assertIsSatisfied();
    }

    @SuppressWarnings("unchecked")
    @Test
    void handsThePolicyAnIdentityCarriedAsAnExchangeProperty() throws Exception {
        givenDecision(Boolean.TRUE);

        template.request("direct:start", e -> e.setProperty("subject", "alice"));

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(client).evaluate(eq(PATH), captor.capture(), eq(Object.class));
        assertThat((Map<String, Object>) captor.getValue().get("properties"))
                .containsEntry("subject", "alice");
    }

    @Test
    void overwritesAVerdictClaimedByTheInboundMessage() throws Exception {
        givenDecision(Boolean.FALSE);

        Exchange out = template.request("direct:start",
                e -> e.getMessage().setHeader(OpaConstants.DECISION_ALLOW, true));

        assertThat(out.getException()).isInstanceOf(CamelAuthorizationException.class);
        assertThat(out.getMessage().getHeader(OpaConstants.DECISION_ALLOW)).isEqualTo(false);
    }
}
