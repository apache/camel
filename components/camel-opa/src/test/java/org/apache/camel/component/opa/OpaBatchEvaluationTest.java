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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.styra.opa.OPAClient;
import com.styra.opa.OPAException;
import com.styra.opa.OPAResult;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Batch evaluation authorizes a List body in one call and reports a per-element verdict list. Fail-closed is per
 * element: an element that could not be evaluated is denied (or allowed under failOpen) while its neighbours decide
 * normally (CAMEL-24740).
 */
public class OpaBatchEvaluationTest extends CamelTestSupport {

    private static final String PATH = "authz/allow";

    @BindToRegistry("opaClient")
    private final OPAClient client = mock(OPAClient.class);

    private OPAResult failed() {
        // a batch element whose evaluation could not be reached: the server returned a result carrying an error
        OPAResult result = mock(OPAResult.class);
        when(result.success()).thenReturn(false);
        return result;
    }

    private void stubBatch(Map<String, OPAResult> results) throws Exception {
        when(client.evaluateBatch(eq(PATH), anyMap())).thenReturn(results);
    }

    @Test
    void reportsAVerdictParallelToEachElement() throws Exception {
        Map<String, OPAResult> results = new LinkedHashMap<>();
        results.put("0", new OPAResult(Boolean.TRUE));
        results.put("1", new OPAResult(Boolean.FALSE));
        results.put("2", new OPAResult(Boolean.TRUE));
        stubBatch(results);

        Exchange out = template.request("opa:" + PATH + "?opaClient=#opaClient&batch=true",
                e -> e.getMessage().setBody(List.of("alice", "mallory", "carol")));

        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getHeader(OpaConstants.BATCH_DECISION, List.class))
                .containsExactly(true, false, true);
        // batch mode must set the policy-path header too, so observability tooling reads the same
        // CamelOpaPolicyPath as it does after a single evaluation
        assertThat(out.getMessage().getHeader(OpaConstants.POLICY_PATH, String.class)).isEqualTo(PATH);
    }

    @Test
    void deniesAFailedElementButLetsItsNeighboursDecide() throws Exception {
        Map<String, OPAResult> results = new LinkedHashMap<>();
        results.put("0", new OPAResult(Boolean.TRUE));
        results.put("1", failed());
        results.put("2", new OPAResult(Boolean.TRUE));
        stubBatch(results);

        Exchange out = template.request("opa:" + PATH + "?opaClient=#opaClient&batch=true",
                e -> e.getMessage().setBody(List.of("a", "b", "c")));

        // the middle element is denied because it could not be evaluated, not because the whole batch failed
        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getHeader(OpaConstants.BATCH_DECISION, List.class))
                .containsExactly(true, false, true);
    }

    @Test
    void allowsAFailedElementUnderFailOpen() throws Exception {
        Map<String, OPAResult> results = new LinkedHashMap<>();
        results.put("0", new OPAResult(Boolean.TRUE));
        results.put("1", failed());
        results.put("2", new OPAResult(Boolean.FALSE));
        stubBatch(results);

        Exchange out = template.request("opa:" + PATH + "?opaClient=#opaClient&batch=true&failOpen=true",
                e -> e.getMessage().setBody(List.of("a", "b", "c")));

        // failOpen turns the unreachable element into an allow; the genuine deny at index 2 is unaffected
        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getHeader(OpaConstants.BATCH_DECISION, List.class))
                .containsExactly(true, true, false);
    }

    @Test
    void requiresAListBody() {
        Exchange out = template.request("opa:" + PATH + "?opaClient=#opaClient&batch=true",
                e -> e.getMessage().setBody("not a list"));

        assertThat(out.getException()).isInstanceOf(IllegalArgumentException.class);
        assertThat(out.getException().getMessage()).contains("List");
    }

    @Test
    void rejectsBatchInWasmModeAtStartup() {
        assertThatThrownBy(() -> context.getEndpoint(
                "opa:" + PATH + "?evaluationMode=wasm&policyBundle=classpath:authz.wasm&batch=true").start())
                .isInstanceOf(Exception.class)
                .hasMessageContaining("batch");
    }

    @Test
    void reportsAnEmptyVerdictListForAnEmptyBatch() throws Exception {
        // an empty list is answered without calling the SDK: an empty batch input is undefined there, so the
        // short-circuit makes it a deterministic empty verdict list. The policy-path header is still set.
        Exchange out = template.request("opa:" + PATH + "?opaClient=#opaClient&batch=true",
                e -> e.getMessage().setBody(List.of()));

        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getHeader(OpaConstants.BATCH_DECISION, List.class)).isEmpty();
        assertThat(out.getMessage().getHeader(OpaConstants.POLICY_PATH, String.class)).isEqualTo(PATH);
        verify(client, never()).evaluateBatch(anyString(), anyMap());
    }

    @Test
    void failsClosedWhenTheWholeBatchCannotBeEvaluated() throws Exception {
        // the batch call itself fails - the server could not be reached at all - so nothing was decided. With
        // failOpen off, every element is denied by failing the exchange, not by returning a verdict list.
        when(client.evaluateBatch(eq(PATH), anyMap())).thenThrow(new OPAException("connection refused"));

        Exchange out = template.request("opa:" + PATH + "?opaClient=#opaClient&batch=true",
                e -> e.getMessage().setBody(List.of("a", "b")));

        assertThat(out.getException())
                .isInstanceOf(OpaPolicyEvaluationException.class)
                .hasMessageContaining("in batch");
        // fail-closed leaves no verdict on the exchange, mirroring the single-evaluation failure path
        assertThat(out.getMessage().getHeader(OpaConstants.BATCH_DECISION)).isNull();
    }

    @Test
    void allowsEveryElementUnderFailOpenWhenTheWholeBatchFails() throws Exception {
        // failOpen turns a whole-batch failure into an allow for every element, parallel to the single-evaluation
        // failOpen path
        when(client.evaluateBatch(eq(PATH), anyMap())).thenThrow(new OPAException("connection refused"));

        Exchange out = template.request("opa:" + PATH + "?opaClient=#opaClient&batch=true&failOpen=true",
                e -> e.getMessage().setBody(List.of("a", "b", "c")));

        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getHeader(OpaConstants.BATCH_DECISION, List.class))
                .containsExactly(true, true, true);
    }
}
