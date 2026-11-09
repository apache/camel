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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The steps of an exchange in the order they ran, with the exchanges it led to inlined where they started.
 */
class StepOrderTest {

    @Test
    void aRouteCalledOverDirectIsUnderTheStepThatCallsIt() {
        // direct passes the exchange on, but the trace of the called route can come as an exchange of its own
        List<TraceEntry> steps = List.of(
                step("A", "orders", "from1", "from", "timer:orders", true),
                step("A", "orders", "to1", "to", "to[direct://audit?block=true]", false),
                step("A", "orders", "log1", "log", "log[done]", false),
                step("B", "audit", "from2", "from", "direct://audit", true),
                step("B", "audit", "log2", "log", "log[audited]", false));

        List<TraceEntry> ordered = StepOrder.depthFirst("A", steps);

        assertThat(ordered).extracting(e -> e.nodeId).containsExactly("from1", "to1", "from2", "log2", "log1");
        assertThat(ordered).extracting(e -> e.inlineDepth).containsExactly(0, 0, 1, 1, 0);
    }

    @Test
    void thePartsOfASplitAreUnderIt() {
        List<TraceEntry> steps = List.of(
                step("A", "orders", "from1", "from", "file:in", true),
                step("A", "orders", "split1", "split", "split[tokenize]", false),
                step("B", "orders", "to1", "to", "to[mock:line]", false),
                step("C", "orders", "to1", "to", "to[mock:line]", false),
                step("A", "orders", "log1", "log", "log[done]", false));

        assertThat(StepOrder.depthFirst("A", steps)).extracting(e -> e.exchangeId + ":" + e.nodeId)
                .containsExactly("A:from1", "A:split1", "B:to1", "C:to1", "A:log1");
    }

    @Test
    void theStepsOfACircuitBreakerAreUnderIt() {
        // the breaker runs its steps on a copy, which calls the supplier route
        List<TraceEntry> steps = List.of(
                step("A", "stock-check", "from1", "from", "timer:stock", true),
                step("A", "stock-check", "circuitBreaker1", "circuitBreaker", "circuitBreaker", false),
                step("B", "stock-check", "to1", "to", "to[direct:supplier]", false),
                step("B", "supplier", "from2", "from", "direct:supplier", true),
                step("B", "supplier", "setBody3", "setBody", "setBody[in stock]", false),
                step("A", "stock-check", "log1", "log", "log[Stock check]", false));

        List<TraceEntry> ordered = StepOrder.depthFirst("A", steps);

        assertThat(ordered).extracting(e -> e.nodeId)
                .containsExactly("from1", "circuitBreaker1", "to1", "from2", "setBody3", "log1");
        assertThat(ordered).extracting(e -> e.inlineDepth).containsExactly(0, 0, 1, 1, 1, 0);
    }

    @Test
    void theTableOfTheStepsFitsThem() {
        // 9 steps on a 50 row screen: all of them, with the borders and the header
        assertThat(HistoryTab.fitSteps(9, 44)).isEqualTo(12);
        // a few steps keep the 10 rows the table had
        assertThat(HistoryTab.fitSteps(4, 44)).isEqualTo(10);
        // many steps take half the screen, the detail of the step the rest
        assertThat(HistoryTab.fitSteps(80, 44)).isEqualTo(22);
    }

    private static TraceEntry step(
            String exchangeId, String routeId, String nodeId, String shortName, String label, boolean first) {
        TraceEntry e = new TraceEntry();
        e.exchangeId = exchangeId;
        e.routeId = routeId;
        e.nodeId = nodeId;
        e.nodeShortName = shortName;
        e.nodeLabel = label;
        e.first = first;
        return e;
    }
}
