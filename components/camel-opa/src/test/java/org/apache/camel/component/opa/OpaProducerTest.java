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

import java.util.List;
import java.util.Map;

import com.styra.opa.OPAClient;
import com.styra.opa.OPAException;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpaProducerTest extends CamelTestSupport {

    private static final String PATH = "authz/orders/allow";
    private static final String ENDPOINT = "opa:" + PATH + "?opaClient=#opaClient";

    @BindToRegistry("opaClient")
    private final OPAClient client = mock(OPAClient.class);

    private void givenDecision(Object decision) throws OPAException {
        when(client.evaluate(eq(PATH), anyMap(), eq(Object.class))).thenReturn(decision);
    }

    @Test
    void allowsWhenPolicyReturnsTrue() throws Exception {
        givenDecision(Boolean.TRUE);

        Exchange out = template.request(ENDPOINT, e -> {
        });

        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getHeader(OpaConstants.DECISION_ALLOW)).isEqualTo(true);
        assertThat(out.getMessage().getHeader(OpaConstants.DECISION)).isEqualTo(true);
        assertThat(out.getMessage().getHeader(OpaConstants.POLICY_PATH)).isEqualTo(PATH);
    }

    @Test
    void deniesWhenPolicyReturnsFalse() throws Exception {
        givenDecision(Boolean.FALSE);

        Exchange out = template.request(ENDPOINT, e -> {
        });

        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getHeader(OpaConstants.DECISION_ALLOW)).isEqualTo(false);
    }

    @Test
    void readsVerdictFromTheAllowKeyOfADecisionObject() throws Exception {
        Map<String, Object> decision = Map.of("allow", true, "reasons", List.of());
        givenDecision(decision);

        Exchange out = template.request(ENDPOINT, e -> {
        });

        assertThat(out.getMessage().getHeader(OpaConstants.DECISION_ALLOW)).isEqualTo(true);
        assertThat(out.getMessage().getHeader(OpaConstants.DECISION)).isEqualTo(decision);
    }

    @Test
    void readsVerdictFromACustomAllowKey() throws Exception {
        givenDecision(Map.of("permitted", true));

        Exchange out = template.request(ENDPOINT + "&allowKey=permitted", e -> {
        });

        assertThat(out.getMessage().getHeader(OpaConstants.DECISION_ALLOW)).isEqualTo(true);
    }

    @Test
    void deniesWhenTheDecisionObjectHasNoVerdictButKeepsTheRawDocument() throws Exception {
        Map<String, Object> decision = Map.of("deny", List.of("not an owner"));
        givenDecision(decision);

        Exchange out = template.request(ENDPOINT, e -> {
        });

        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getHeader(OpaConstants.DECISION_ALLOW)).isEqualTo(false);
        assertThat(out.getMessage().getHeader(OpaConstants.DECISION)).isEqualTo(decision);
    }

    @Test
    void deniesWhenTheVerdictIsNotABoolean() throws Exception {
        givenDecision(Map.of("allow", "true"));

        Exchange out = template.request(ENDPOINT, e -> {
        });

        assertThat(out.getMessage().getHeader(OpaConstants.DECISION_ALLOW)).isEqualTo(false);
    }

    @Test
    void failsClosedWhenThePolicyCannotBeEvaluated() throws Exception {
        when(client.evaluate(eq(PATH), anyMap(), eq(Object.class))).thenThrow(new OPAException("connection refused"));

        Exchange out = template.request(ENDPOINT, e -> {
        });

        assertThat(out.getException()).isInstanceOf(OpaPolicyEvaluationException.class)
                .hasMessageContaining(PATH);
        assertThat(out.getMessage().getHeader(OpaConstants.DECISION_ALLOW)).isNull();
    }

    @Test
    void failsClosedWhenTheSdkFailsWithSomethingOtherThanAnOpaException() throws Exception {
        when(client.evaluate(eq(PATH), anyMap(), eq(Object.class)))
                .thenThrow(new IllegalArgumentException("could not serialize the input document"));

        Exchange out = template.request(ENDPOINT, e -> {
        });

        assertThat(out.getException()).isInstanceOf(OpaPolicyEvaluationException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class);
        assertThat(out.getMessage().getHeader(OpaConstants.DECISION_ALLOW)).isNull();
    }

    @Test
    void failsOpenWhenExplicitlyConfiguredTo() throws Exception {
        when(client.evaluate(eq(PATH), anyMap(), eq(Object.class))).thenThrow(new OPAException("connection refused"));

        Exchange out = template.request(ENDPOINT + "&failOpen=true", e -> {
        });

        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getHeader(OpaConstants.DECISION_ALLOW)).isEqualTo(true);
        assertThat(out.getMessage().getHeader(OpaConstants.DECISION)).isNull();
    }

    @Test
    void acceptsAPolicyPathWrittenWithALeadingSlash() throws Exception {
        givenDecision(Boolean.TRUE);

        Exchange out = template.request("opa:/" + PATH + "?opaClient=#opaClient", e -> {
        });

        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getHeader(OpaConstants.POLICY_PATH)).isEqualTo(PATH);
    }

    @Test
    void overwritesAVerdictClaimedByTheInboundMessage() throws Exception {
        givenDecision(Boolean.FALSE);

        Exchange out = template.request(ENDPOINT, e -> e.getMessage().setHeader(OpaConstants.DECISION_ALLOW, true));

        assertThat(out.getMessage().getHeader(OpaConstants.DECISION_ALLOW)).isEqualTo(false);
    }

    @Test
    void alsoOverwritesAVerdictClaimedByTheInboundMessageWhenTheDecisionCannotBeRead() throws Exception {
        when(client.evaluate(eq(PATH), anyMap(), eq(Object.class))).thenThrow(new OPAException("connection refused"));

        Exchange out = template.request(ENDPOINT + "&failOpen=true",
                e -> e.getMessage().setHeader(OpaConstants.DECISION, "forged"));

        assertThat(out.getMessage().getHeader(OpaConstants.DECISION)).isNull();
    }
}
