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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Service;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.Language;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QuickjsLanguageLifecycleTest {

    CamelContext context;

    @BeforeEach
    void startContext() {
        context = new DefaultCamelContext();
        context.start();
    }

    @AfterEach
    void stopContext() {
        context.stop();
    }

    @Test
    void stopThenEvaluateFromSameWorkerThreadCreatesNewEngine() throws Exception {
        Language language = context.resolveLanguage("quickjs");
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            assertThat(pool.submit(() -> evaluate(language, "1 + 1")).get(20, TimeUnit.SECONDS)).isEqualTo(2);
            ((Service) language).stop();
            ((Service) language).start();
            assertThat(pool.submit(() -> evaluate(language, "2 + 3")).get(20, TimeUnit.SECONDS)).isEqualTo(5);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void repeatedStopStartCycles() {
        Language language = context.resolveLanguage("quickjs");
        Exchange exchange = new DefaultExchange(context);
        for (int i = 0; i < 3; i++) {
            ((Service) language).stop();
            ((Service) language).start();
            assertThat(language.createExpression("40 + 2").evaluate(exchange, Integer.class)).isEqualTo(42);
        }
    }

    @Test
    void stopClosesEnginesRegisteredByWorkerThreads() throws Exception {
        QuickjsLanguage language = (QuickjsLanguage) context.resolveLanguage("quickjs");
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            for (int i = 0; i < 4; i++) {
                assertThat(pool.submit(() -> evaluate(language, "1 + 1")).get(20, TimeUnit.SECONDS)).isEqualTo(2);
            }
            assertThat(language.trackedEngineCount()).isPositive();
            language.stop();
            assertThat(language.trackedEngineCount()).isZero();
            language.start();
            assertThat(pool.submit(() -> evaluate(language, "2 + 2")).get(20, TimeUnit.SECONDS)).isEqualTo(4);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentEvaluationDuringStopStartDoesNotLeaveTrackedEngines() throws Exception {
        QuickjsLanguage language = (QuickjsLanguage) context.resolveLanguage("quickjs");
        ExecutorService pool = Executors.newFixedThreadPool(4);
        AtomicBoolean run = new AtomicBoolean(true);
        try {
            for (int i = 0; i < 4; i++) {
                pool.submit(() -> {
                    while (run.get()) {
                        try {
                            evaluate(language, "1 + 1");
                        } catch (RuntimeException e) {
                            // stop() may close an engine that is mid-evaluation
                        }
                    }
                });
            }
            for (int i = 0; i < 20; i++) {
                language.stop();
                language.start();
            }
            run.set(false);
            pool.shutdown();
            assertThat(pool.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
            language.stop();
            assertThat(language.trackedEngineCount()).isZero();
            language.start();
        } finally {
            run.set(false);
            pool.shutdownNow();
        }
    }

    Integer evaluate(Language language, String script) {
        Exchange exchange = new DefaultExchange(context);
        return language.createExpression(script).evaluate(exchange, Integer.class);
    }
}
