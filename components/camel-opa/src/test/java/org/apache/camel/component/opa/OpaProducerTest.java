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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.apache.camel.component.opa.OpaSdkFailures.badRequest;
import static org.apache.camel.component.opa.OpaSdkFailures.evaluationError;
import static org.apache.camel.component.opa.OpaSdkFailures.interrupted;
import static org.apache.camel.component.opa.OpaSdkFailures.rejectedBeforeSending;
import static org.apache.camel.component.opa.OpaSdkFailures.status;
import static org.apache.camel.component.opa.OpaSdkFailures.timedOut;
import static org.apache.camel.component.opa.OpaSdkFailures.undefinedDecision;
import static org.apache.camel.component.opa.OpaSdkFailures.unreachable;
import static org.apache.camel.component.opa.OpaSdkFailures.unserializableInput;
import static org.apache.camel.component.opa.OpaSdkFailures.unserializableInputUnchecked;
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
    void readsAVerdictNestedInsideTheDecisionDocument() throws Exception {
        givenDecision(Map.of("result", Map.of("allow", true)));

        Exchange out = template.request(ENDPOINT + "&allowKey=result.allow", e -> {
        });

        assertThat(out.getMessage().getHeader(OpaConstants.DECISION_ALLOW)).isEqualTo(true);
    }

    @Test
    void prefersATopLevelKeyThatItselfContainsADot() throws Exception {
        // walking "com.acme.allow" as a path would miss a document that has it as one key. Rego rule names cannot
        // contain a dot, but a decision document is arbitrary JSON and may well come from elsewhere.
        givenDecision(Map.of("com.acme.allow", true, "com", Map.of("acme", Map.of("allow", false))));

        Exchange out = template.request(ENDPOINT + "&allowKey=com.acme.allow", e -> {
        });

        assertThat(out.getMessage().getHeader(OpaConstants.DECISION_ALLOW)).isEqualTo(true);
    }

    @Test
    void deniesWhenADottedPathDoesNotResolve() throws Exception {
        givenDecision(Map.of("result", Map.of("permitted", true)));

        Exchange out = template.request(ENDPOINT + "&allowKey=result.allow", e -> {
        });

        // fail closed, and the raw document stays available so the misconfiguration can be seen
        assertThat(out.getMessage().getHeader(OpaConstants.DECISION_ALLOW)).isEqualTo(false);
        assertThat(out.getMessage().getHeader(OpaConstants.DECISION)).isNotNull();
    }

    @Test
    void deniesWhenADottedPathRunsPastANonMap() throws Exception {
        givenDecision(Map.of("result", "not-a-map"));

        Exchange out = template.request(ENDPOINT + "&allowKey=result.allow", e -> {
        });

        assertThat(out.getMessage().getHeader(OpaConstants.DECISION_ALLOW)).isEqualTo(false);
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
    void marksAnExchangeThatOnlyProceededBecauseOfFailOpen() throws Exception {
        when(client.evaluate(eq(PATH), anyMap(), eq(Object.class))).thenThrow(unreachable(PATH));

        Exchange out = template.request(ENDPOINT + "&failOpen=true", e -> {
        });

        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getHeader(OpaConstants.DECISION_ALLOW)).isEqualTo(true);
        assertThat(out.getMessage().getHeader(OpaConstants.DECISION_FAILED_OPEN)).isEqualTo(true);
    }

    @Test
    void doesNotMarkADecisionAPolicyActuallyMade() throws Exception {
        // the point of the marker is that it separates the two, so an allow from a real policy must not carry it
        givenDecision(Boolean.TRUE);

        Exchange out = template.request(ENDPOINT + "&failOpen=true", e -> {
        });

        assertThat(out.getMessage().getHeader(OpaConstants.DECISION_ALLOW)).isEqualTo(true);
        assertThat(out.getMessage().getHeader(OpaConstants.DECISION_FAILED_OPEN)).isNull();
    }

    @Test
    void doesNotLetAnInboundMessageClaimItDidNotFailOpen() throws Exception {
        // as settable by a sender as the verdict was: left in place, "FailedOpen=false" would disguise an
        // unauthorized exchange as one a policy allowed - which is the audit trail this header exists to give
        when(client.evaluate(eq(PATH), anyMap(), eq(Object.class))).thenThrow(unreachable(PATH));

        Exchange out = template.request(ENDPOINT + "&failOpen=true",
                e -> e.getMessage().setHeader(OpaConstants.DECISION_FAILED_OPEN, false));

        assertThat(out.getMessage().getHeader(OpaConstants.DECISION_FAILED_OPEN)).isEqualTo(true);
    }

    @Test
    void clearsAClaimedFailOpenMarkerOnAnOrdinaryDecision() throws Exception {
        givenDecision(Boolean.TRUE);

        Exchange out = template.request(ENDPOINT,
                e -> e.getMessage().setHeader(OpaConstants.DECISION_FAILED_OPEN, true));

        assertThat(out.getMessage().getHeader(OpaConstants.DECISION_FAILED_OPEN)).isNull();
    }

    @Test
    void failsClosedWhenThePolicyCannotBeEvaluated() throws Exception {
        when(client.evaluate(eq(PATH), anyMap(), eq(Object.class))).thenThrow(unreachable(PATH));

        Exchange out = template.request(ENDPOINT, e -> {
        });

        assertThat(out.getException()).isInstanceOf(OpaPolicyEvaluationException.class)
                .hasMessageContaining(PATH);
        assertThat(out.getMessage().getHeader(OpaConstants.DECISION_ALLOW)).isNull();
        // the marker is for the deliberate failOpen path only, not for any exception the evaluator happens to hit
        assertThat(out.getMessage().getHeader(OpaConstants.DECISION_FAILED_OPEN)).isNull();
    }

    @Test
    void doesNotLeaveAVerdictClaimedByTheMessageBehindWhenEvaluationFails() throws Exception {
        // the decision headers used to be written only on a path that reached a verdict, so a claim carried by
        // the message survived a failure. A route that handles the exception - doTry/doCatch, or
        // onException().handled(true) - then read the sender's own "allowed" as though a policy had said it
        when(client.evaluate(eq(PATH), anyMap(), eq(Object.class))).thenThrow(unreachable(PATH));

        Exchange out = template.request(ENDPOINT, e -> {
            e.getMessage().setHeader(OpaConstants.DECISION_ALLOW, true);
            e.getMessage().setHeader(OpaConstants.DECISION, Map.of("allow", true));
            e.getMessage().setHeader(OpaConstants.POLICY_PATH, "authz/some-other-policy");
        });

        assertThat(out.getException()).isInstanceOf(OpaPolicyEvaluationException.class);
        assertThat(out.getMessage().getHeader(OpaConstants.DECISION_ALLOW)).isNull();
        assertThat(out.getMessage().getHeader(OpaConstants.DECISION)).isNull();
        assertThat(out.getMessage().getHeader(OpaConstants.POLICY_PATH)).isNull();
    }

    @Test
    void replacesAVerdictClaimedByTheMessageWithTheOneThePolicyGave() throws Exception {
        givenDecision(Boolean.FALSE);

        Exchange out = template.request(ENDPOINT, e -> {
            e.getMessage().setHeader(OpaConstants.DECISION_ALLOW, true);
            e.getMessage().setHeader(OpaConstants.POLICY_PATH, "authz/some-other-policy");
        });

        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getHeader(OpaConstants.DECISION_ALLOW)).isEqualTo(false);
        assertThat(out.getMessage().getHeader(OpaConstants.POLICY_PATH)).isEqualTo(PATH);
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
        when(client.evaluate(eq(PATH), anyMap(), eq(Object.class))).thenThrow(unreachable(PATH));

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
        when(client.evaluate(eq(PATH), anyMap(), eq(Object.class))).thenThrow(unreachable(PATH));

        Exchange out = template.request(ENDPOINT + "&failOpen=true",
                e -> e.getMessage().setHeader(OpaConstants.DECISION, "forged"));

        assertThat(out.getMessage().getHeader(OpaConstants.DECISION)).isNull();
    }

    private void givenFailure(OPAException failure) throws OPAException {
        when(client.evaluate(eq(PATH), anyMap(), eq(Object.class))).thenThrow(failure);
    }

    private void assertFailedClosed(Exchange out) {
        assertThat(out.getException()).isInstanceOf(OpaPolicyEvaluationException.class);
        assertThat(out.getMessage().getHeader(OpaConstants.DECISION_ALLOW)).isNull();
        assertThat(out.getMessage().getHeader(OpaConstants.DECISION_FAILED_OPEN)).isNull();
    }

    private void assertFailedOpen(Exchange out) {
        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getHeader(OpaConstants.DECISION_ALLOW)).isEqualTo(true);
        assertThat(out.getMessage().getHeader(OpaConstants.DECISION_FAILED_OPEN)).isEqualTo(true);
    }

    @Test
    void failsOpenOnATimeout() throws Exception {
        givenFailure(timedOut(PATH));

        assertFailedOpen(template.request(ENDPOINT + "&failOpen=true", e -> {
        }));
    }

    @ParameterizedTest
    @ValueSource(ints = { 429, 502, 503, 504 })
    void failsOpenWhenAGatewayReportsTheServerUnavailable(int statusCode) throws Exception {
        givenFailure(status(PATH, statusCode));

        assertFailedOpen(template.request(ENDPOINT + "&failOpen=true", e -> {
        }));
    }

    @Test
    void failsClosedOnAnUndefinedDecisionEvenUnderFailOpen() throws Exception {
        // a rule with no default is undefined for an input it does not match. That is the policy not saying yes,
        // not the server being unavailable, and failOpen must not turn it into an allow
        givenFailure(undefinedDecision(PATH));

        assertFailedClosed(template.request(ENDPOINT + "&failOpen=true", e -> {
        }));
    }

    @Test
    void failsClosedOnABadRequestEvenUnderFailOpen() throws Exception {
        givenFailure(badRequest(PATH));

        assertFailedClosed(template.request(ENDPOINT + "&failOpen=true", e -> {
        }));
    }

    @ParameterizedTest
    @ValueSource(ints = { 401, 403, 404, 413 })
    void failsClosedOnARejectedRequestEvenUnderFailOpen(int statusCode) throws Exception {
        // a wrong or expired bearer token, a wrong policy path or an oversized input: the server answered
        givenFailure(status(PATH, statusCode));

        assertFailedClosed(template.request(ENDPOINT + "&failOpen=true", e -> {
        }));
    }

    @Test
    void failsClosedOnAPolicyEvaluationErrorEvenUnderFailOpen() throws Exception {
        // OPA answers 500 when the policy cannot be evaluated against this input, which the input can provoke
        givenFailure(evaluationError(PATH));

        assertFailedClosed(template.request(ENDPOINT + "&failOpen=true", e -> {
        }));
    }

    @Test
    void failsClosedOnAnInterruptedCallEvenUnderFailOpen() throws Exception {
        // the SDK wraps the interrupt in its OPAException, so the dedicated InterruptedException catch never sees
        // it: the classification is what keeps a shutdown from turning into an allow
        givenFailure(interrupted(PATH));

        assertFailedClosed(template.request(ENDPOINT + "&failOpen=true", e -> {
        }));
    }

    @Test
    void failsClosedWhenTheSdkRefusesToSendTheRequestEvenUnderFailOpen() throws Exception {
        // nothing reached the server, so it was not the server that was unavailable
        givenFailure(rejectedBeforeSending(PATH));

        assertFailedClosed(template.request(ENDPOINT + "&failOpen=true", e -> {
        }));
    }

    @Test
    void failsClosedOnAnInputTheSdkCannotSerializeEvenUnderFailOpen() throws Exception {
        // Jackson reports this as an IOException, which must not pass for a transport failure
        givenFailure(unserializableInput(PATH));

        assertFailedClosed(template.request(ENDPOINT + "&failOpen=true", e -> {
        }));
    }

    @Test
    void failsClosedOnASerializationFailureWrappedInAnUncheckedIOExceptionEvenUnderFailOpen() throws Exception {
        // UncheckedIOException is not an IOException, so the classification has to walk past it to the Jackson
        // cause rather than stop at the first type it does not recognise
        givenFailure(unserializableInputUnchecked(PATH));

        assertFailedClosed(template.request(ENDPOINT + "&failOpen=true", e -> {
        }));
    }
}
