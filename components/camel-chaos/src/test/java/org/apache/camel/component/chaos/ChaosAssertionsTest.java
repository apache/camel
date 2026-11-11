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
package org.apache.camel.component.chaos;

import java.time.Duration;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The assertions for chaos in a test: the verdict, an expectation and the faults of a rule, with the report when they
 * fail.
 */
class ChaosAssertionsTest extends ChaosTestSupport {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // the stock call is retried, so a fault there is recovered and the order still ships
                errorHandler(defaultErrorHandler().maximumRedeliveries(3).redeliveryDelay(0));
                from("direct:orders").routeId("orders")
                        .to("mock:stock").id("callStock")
                        .to("direct:ship");
                from("direct:ship").routeId("ship").to("mock:ship");
            }
        };
    }

    private void orders(int count) {
        for (int i = 0; i < count; i++) {
            template.sendBody("direct:orders", "order " + i);
        }
    }

    @Test
    void aRetriedFaultPassesAndTheExpectationIsMet() {
        String id = chaos().addRule("step=callStock error rate=every:2 expect=ship:4 within=10s");
        orders(4);

        ChaosAssertions.assertFired(context, id, 2);
        ChaosAssertions.assertExpectationMet(context, id, TIMEOUT);
        ChaosAssertions.assertVerdict(context, ChaosReport.PASS, TIMEOUT);
        ChaosAssertions.assertVerdict(context, ChaosReport.PASS);
    }

    @Test
    void aLostMessageFailsWithTheReasonAndTheReport() {
        String id = chaos().addRule("endpoint=direct:ship drop expect=ship:2 within=300ms");
        orders(2);

        assertThatThrownBy(() -> ChaosAssertions.assertExpectationMet(context, id, TIMEOUT))
                .isInstanceOf(AssertionError.class)
                .hasMessageStartingWith("The expectation of chaos rule " + id + " is unmet, not met")
                .hasMessageContaining("Chaos report: FAIL");
        ChaosAssertions.assertVerdict(context, ChaosReport.FAIL, TIMEOUT);
        assertThatThrownBy(() -> ChaosAssertions.assertVerdict(context, ChaosReport.PASS))
                .hasMessageStartingWith("The chaos verdict is FAIL, expected PASS: route ship completed 0 of 2 exchanges");
    }

    @Test
    void aPendingReportIsNotFinalInTime() {
        String id = chaos().addRule("endpoint=direct:ship drop expect=ship:2 within=1m");

        assertThatThrownBy(() -> ChaosAssertions.awaitFinalReport(context, Duration.ofMillis(100)))
                .hasMessageStartingWith("The chaos report is still pending after PT0.1S");
        assertThatThrownBy(() -> ChaosAssertions.assertVerdict(context, ChaosReport.PASS))
                .hasMessageStartingWith("The chaos report is not final");
        assertThatThrownBy(() -> ChaosAssertions.assertExpectationMet(context, id, Duration.ofMillis(100)))
                .hasMessageStartingWith("The expectation of chaos rule " + id + " is pending after PT0.1S, not met");
    }

    @Test
    void whatTheAssertionsCannotFind() throws Exception {
        String id = chaos().addRule("step=callStock delay duration=1ms");
        orders(1);

        assertThatThrownBy(() -> ChaosAssertions.assertFired(context, id, 2))
                .hasMessageStartingWith("Chaos rule " + id + " fired 1 fault, expected at least 2");
        assertThatThrownBy(() -> ChaosAssertions.assertExpectationMet(context, id, TIMEOUT))
                .hasMessageStartingWith("Chaos rule " + id + " has no expectation (expect=)");
        assertThatThrownBy(() -> ChaosAssertions.assertFired(context, "r99", 1))
                .hasMessageStartingWith("There is no chaos rule r99");
        try (DefaultCamelContext off = new DefaultCamelContext()) {
            assertThatThrownBy(() -> ChaosAssertions.assertVerdict(off, ChaosReport.PASS))
                    .hasMessageStartingWith("Chaos is off in this CamelContext");
        }
        assertThat(ChaosAssertions.awaitFinalReport(context, TIMEOUT).isFinal()).isTrue();
    }
}
