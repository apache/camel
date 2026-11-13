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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import com.styra.opa.OPAClient;
import com.styra.opa.OPAResult;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.opa.security.OpaSecurityPolicy;
import org.apache.camel.support.ExpressionAdapter;
import org.apache.camel.support.builder.ExpressionBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpaInputExpressionTest extends CamelTestSupport {

    private static final String PATH = "authz/allow";

    @BindToRegistry("opaClient")
    private final OPAClient client = mock(OPAClient.class);

    private OpaEndpoint endpoint(Expression expression, boolean batch, boolean failOpen) throws Exception {
        OpaConfiguration configuration = new OpaConfiguration();
        configuration.setOpaClient(client);
        configuration.setInputExpression(expression);
        configuration.setBatch(batch);
        configuration.setFailOpen(failOpen);
        OpaEndpoint endpoint = new OpaEndpoint("opa:" + PATH, new OpaComponent(context), configuration);
        endpoint.setPolicyPath(PATH);
        context.addService(endpoint);
        endpoint.start();
        return endpoint;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> capturedInput() throws Exception {
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(client).evaluate(eq(PATH), captor.capture(), eq(Object.class));
        return captor.getValue();
    }

    @Test
    void sendsTheCompleteDomainInputWithoutTheDefaultEnvelope() throws Exception {
        when(client.evaluate(eq(PATH), anyMap(), eq(Object.class))).thenReturn(Boolean.TRUE);
        Map<String, Object> input = Map.of("subject", "alice", "order", Map.of("id", 42, "lines", List.of("a", "b")));

        Exchange out = template.request(endpoint(ExpressionBuilder.bodyExpression(), false, false), e -> {
            e.getMessage().setBody(input);
            e.getMessage().setHeader("ignored-by-custom-mapping", "value");
        });

        assertThat(out.getException()).isNull();
        assertThat(capturedInput()).isEqualTo(input).doesNotContainKeys("headers", "body", "exchangeId");
        assertThat(out.getMessage().getBody()).isSameAs(input);
    }

    @Test
    void convertsPojoAndNestedValuesToJsonObjects() throws Exception {
        when(client.evaluate(eq(PATH), anyMap(), eq(Object.class))).thenReturn(Boolean.TRUE);
        Order order = new Order("alice", new OrderDetails(42, List.of("a", "b")));

        Exchange out = template.request(endpoint(ExpressionBuilder.bodyExpression(), false, false),
                e -> e.getMessage().setBody(order));

        assertThat(out.getException()).isNull();
        assertThat(capturedInput()).containsEntry("subject", "alice")
                .containsEntry("order", Map.of("id", 42, "lines", List.of("a", "b")));
        assertThat(out.getMessage().getBody()).isSameAs(order);
    }

    @Test
    void rejectsNullScalarAndStreamingResultsEvenUnderFailOpen() throws Exception {
        ByteArrayInputStream stream = new ByteArrayInputStream(new byte[] { 1, 2, 3 });
        for (Object input : Arrays.asList(null, "not a JSON object", List.of("not", "an", "object"), stream)) {
            Exchange out = template.request(endpoint(ExpressionBuilder.bodyExpression(), false, true),
                    e -> e.getMessage().setBody(input));

            assertThat(out.getException()).isInstanceOf(OpaPolicyEvaluationException.class)
                    .hasMessageContaining("Failed to build input");
            assertThat(out.getMessage().getHeader(OpaConstants.DECISION_ALLOW)).isNull();
            assertThat(out.getMessage().getHeader(OpaConstants.DECISION_FAILED_OPEN)).isNull();
        }
        assertThat(stream.available()).as("mapping did not consume the rejected stream").isEqualTo(3);
        verify(client, never()).evaluate(anyString(), anyMap(), eq(Object.class));
    }

    @Test
    void treatsMappingFailureSeparatelyFromDecisionPointUnavailability() throws Exception {
        Expression broken = new ExpressionAdapter() {
            @Override
            public Object evaluate(Exchange exchange) {
                throw new IllegalStateException("Could not map input", new IOException("local data unavailable"));
            }
        };
        for (boolean batch : List.of(false, true)) {
            Exchange out = template.request(endpoint(broken, batch, true),
                    e -> e.getMessage().setBody(List.of("a")));

            assertThat(out.getException()).isInstanceOf(OpaPolicyEvaluationException.class)
                    .hasMessageContaining("build");
            assertThat(out.getMessage().getHeader(OpaConstants.DECISION_FAILED_OPEN)).isNull();
            assertThat(out.getMessage().getHeader(OpaConstants.BATCH_DECISION)).isNull();
        }
        verify(client, never()).evaluate(anyString(), anyMap(), eq(Object.class));
        verify(client, never()).evaluateBatch(anyString(), anyMap());
    }

    @Test
    void mapsEachBatchBodyOnAnExchangeCopy() throws Exception {
        when(client.evaluateBatch(eq(PATH), anyMap())).thenReturn(
                Map.of("0", new OPAResult(true), "1", new OPAResult(false)));
        Expression input = new ExpressionAdapter() {
            @Override
            public Object evaluate(Exchange exchange) {
                exchange.getMessage().setHeader("mapper-state", "local to this element");
                return Map.of("subject", exchange.getProperty("verifiedSubject"), "order", exchange.getMessage().getBody());
            }
        };
        List<Map<String, Integer>> orders = List.of(Map.of("id", 1), Map.of("id", 2));

        Exchange out = template.request(endpoint(input, true, false), e -> {
            e.setProperty("verifiedSubject", "alice");
            e.getMessage().setBody(orders);
        });

        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getBody()).isSameAs(orders);
        assertThat(out.getMessage().getHeader("mapper-state")).isNull();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(client).evaluateBatch(eq(PATH), captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "0", Map.of("subject", "alice", "order", Map.of("id", 1)),
                "1", Map.of("subject", "alice", "order", Map.of("id", 2))));
        assertThat(out.getMessage().getHeader(OpaConstants.BATCH_DECISION, List.class)).containsExactly(true, false);
    }

    @Test
    void rejectsAnInvalidBatchElementBeforeCallingOpa() throws Exception {
        List<Object> elements = Arrays.asList(Map.of("subject", "alice"), null);

        Exchange out = template.request(endpoint(ExpressionBuilder.bodyExpression(), true, true),
                e -> e.getMessage().setBody(elements));

        assertThat(out.getException()).isInstanceOf(OpaPolicyEvaluationException.class)
                .hasMessageContaining("Failed to build batch input");
        assertThat(out.getMessage().getBody()).isSameAs(elements);
        assertThat(out.getMessage().getHeader(OpaConstants.BATCH_DECISION)).isNull();
        assertThat(out.getMessage().getHeader(OpaConstants.DECISION_FAILED_OPEN)).isNull();
        verify(client, never()).evaluateBatch(anyString(), anyMap());
    }

    @Test
    void normalizesAnObjectJsonNode() throws Exception {
        when(client.evaluate(eq(PATH), anyMap(), eq(Object.class))).thenReturn(Boolean.TRUE);
        var input = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("subject", "alice");

        Exchange out = template.request(endpoint(ExpressionBuilder.bodyExpression(), false, false),
                e -> e.getMessage().setBody(input));

        assertThat(out.getException()).isNull();
        assertThat(capturedInput()).containsOnlyKeys("subject").containsEntry("subject", "alice");
        assertThat(out.getMessage().getBody()).isSameAs(input);
    }

    @Test
    void initializesTheExpressionBeforeTheFirstEvaluation() throws Exception {
        AtomicBoolean initialized = new AtomicBoolean();
        Expression expression = new ExpressionAdapter() {
            @Override
            public void init(org.apache.camel.CamelContext camelContext) {
                initialized.set(true);
            }

            @Override
            public Object evaluate(Exchange exchange) {
                assertThat(initialized).isTrue();
                return Map.of("subject", "alice");
            }
        };
        when(client.evaluate(eq(PATH), anyMap(), eq(Object.class))).thenReturn(Boolean.TRUE);

        Exchange out = template.request(endpoint(expression, false, false), e -> {
        });

        assertThat(initialized).isTrue();
        assertThat(out.getException()).isNull();
    }

    @Test
    void usesTheSameMappingThroughASecurityPolicy() throws Exception {
        when(client.evaluate(eq(PATH), anyMap(), eq(Object.class))).thenReturn(Boolean.TRUE);
        OpaSecurityPolicy policy = new OpaSecurityPolicy();
        policy.setPolicyPath(PATH);
        policy.setOpaClient(client);
        policy.setInputExpression(ExpressionBuilder.bodyExpression());
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:mapped-policy").policy(policy).to("mock:mapped-result");
            }
        });

        Exchange out = template.request("direct:mapped-policy", e -> e.getMessage().setBody(Map.of("subject", "alice")));

        assertThat(out.getException()).isNull();
        assertThat(capturedInput()).containsOnlyKeys("subject").containsEntry("subject", "alice");
        assertThat(out.getMessage().getBody()).isEqualTo(Map.of("subject", "alice"));
    }

    public record Order(String subject, OrderDetails order) {
    }

    public record OrderDetails(int id, List<String> lines) {
    }
}
