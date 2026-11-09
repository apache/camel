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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The calls per second of the Circuit Breaker chart come from running totals, not from the sliding window of the
 * breaker, which stays the same while the calls go on.
 */
class MetricsCollectorCircuitBreakerTest {

    @Test
    void theCallsAreRunningTotalsNotTheSlidingWindow() {
        CircuitBreakerInfo cb = new CircuitBreakerInfo();
        // a window of 4, full of successful calls, after 5947 calls of which the fallback answered 13
        cb.bufferedCalls = 4;
        cb.successfulCalls = 4;
        cb.total = 5947;
        cb.fallbackCalls = 13;

        assertThat(MetricsCollector.cbCalls(cb)).containsExactly(5934, 13);

        // a second later the window is the same, the totals have moved on
        cb.total = 5948;
        assertThat(MetricsCollector.cbCalls(cb)).containsExactly(5935, 13);
    }

    @Test
    void anOutageIsAnsweredByTheFallback() {
        CircuitBreakerInfo cb = new CircuitBreakerInfo();
        cb.total = 100;
        cb.fallbackCalls = 10;
        cb.total += 5;
        cb.fallbackCalls += 5;

        assertThat(MetricsCollector.cbCalls(cb)).containsExactly(90, 15);
    }
}
