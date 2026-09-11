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
package org.apache.camel.language.quickjs;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExpressionEvaluationException;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * QuickJS keeps every evaluated module, so an engine grows with each evaluation; the language recycles it.
 */
class QuickjsEngineRecyclingTest {

    private static CamelContext context;
    private static QuickjsLanguage language;

    @BeforeAll
    static void startContext() {
        context = new DefaultCamelContext();
        context.start();
        language = (QuickjsLanguage) context.resolveLanguage("quickjs");
    }

    @AfterAll
    static void stopContext() {
        context.stop();
    }

    private static Exchange exchange(Object body) {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(body);
        return exchange;
    }

    @Test
    void engineMemoryGrowsWithEvaluations() {
        Exchange exchange = exchange(1);
        language.createExpression("body + 1").evaluate(exchange, Integer.class);
        long before = language.engineMemory();
        assertThat(before).isPositive();
        for (int i = 0; i < 2000; i++) {
            language.createExpression("body + " + i).evaluate(exchange, Integer.class);
        }
        assertThat(language.engineMemory()).isGreaterThan(before);
    }

    @Test
    void engineIsRecycledAfterMaxEvaluations() {
        Exchange exchange = exchange(1);
        int max = language.getEngineMaxEvaluations();
        long maxMemory = language.getEngineMaxMemory();
        try {
            // start from no engine on this thread: whatever an earlier test left behind is discarded here
            language.setEngineMaxMemory(1);
            language.createExpression("body").evaluate(exchange, Integer.class);
            language.setEngineMaxMemory(maxMemory);
            assertThat(language.trackedEngineCount()).isZero();
            language.setEngineMaxEvaluations(10);
            for (int i = 0; i < 9; i++) {
                assertThat(language.createExpression("body + " + i).evaluate(exchange, Integer.class)).isEqualTo(1 + i);
                assertThat(language.trackedEngineCount()).isEqualTo(1);
            }
            // the 10th evaluation exhausts the engine: it is closed and the thread has none until the next call
            assertThat(language.createExpression("body + 9").evaluate(exchange, Integer.class)).isEqualTo(10);
            assertThat(language.trackedEngineCount()).isZero();
            assertThat(language.createExpression("body + 100").evaluate(exchange, Integer.class)).isEqualTo(101);
            assertThat(language.trackedEngineCount()).isEqualTo(1);
        } finally {
            language.setEngineMaxEvaluations(max);
            language.setEngineMaxMemory(maxMemory);
        }
    }

    @Test
    void throwingScriptsCountTowardsRecycling() {
        Exchange exchange = exchange(1);
        int max = language.getEngineMaxEvaluations();
        long maxMemory = language.getEngineMaxMemory();
        try {
            language.setEngineMaxMemory(1);
            language.createExpression("body").evaluate(exchange, Integer.class);
            language.setEngineMaxMemory(maxMemory);
            assertThat(language.trackedEngineCount()).isZero();
            language.setEngineMaxEvaluations(10);
            for (int i = 0; i < 9; i++) {
                assertThatThrownBy(() -> language.createExpression("notDefined + 1").evaluate(exchange, Object.class))
                        .isInstanceOf(ExpressionEvaluationException.class);
                assertThat(language.trackedEngineCount()).isEqualTo(1);
            }
            // the 10th failed evaluation exhausts the engine like a successful one would
            assertThatThrownBy(() -> language.createExpression("notDefined + 1").evaluate(exchange, Object.class))
                    .isInstanceOf(ExpressionEvaluationException.class);
            assertThat(language.trackedEngineCount()).isZero();
            assertThat(language.createExpression("body + 1").evaluate(exchange, Integer.class)).isEqualTo(2);
            assertThat(language.trackedEngineCount()).isEqualTo(1);
        } finally {
            language.setEngineMaxEvaluations(max);
            language.setEngineMaxMemory(maxMemory);
        }
    }

    @Test
    void engineIsRecycledAfterMaxMemory() {
        Exchange exchange = exchange(1);
        long max = language.getEngineMaxMemory();
        try {
            language.setEngineMaxMemory(1);
            language.createExpression("body").evaluate(exchange, Integer.class);
            assertThat(language.trackedEngineCount()).isZero();
        } finally {
            language.setEngineMaxMemory(max);
        }
        assertThat(language.createExpression("body + 1").evaluate(exchange, Integer.class)).isEqualTo(2);
        assertThat(language.trackedEngineCount()).isEqualTo(1);
    }
}
