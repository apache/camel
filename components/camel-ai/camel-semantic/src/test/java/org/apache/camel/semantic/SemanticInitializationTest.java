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
package org.apache.camel.semantic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.language.semantic.SemanticLanguage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticInitializationTest {
    @Test
    void initializationDoesNotAcquirePublicContextOrRegistryMonitors() throws Exception {
        ExecutorService callers = Executors.newSingleThreadExecutor();
        try (var context = new DefaultCamelContext()) {
            context.init();
            synchronized (context) {
                var evaluations = callers.submit(() -> SemanticEvaluations.get(context));
                assertThat(evaluations.get(10, TimeUnit.SECONDS)).isSameAs(SemanticEvaluations.get(context));
            }
            var language = language(context);
            synchronized (context.getRegistry()) {
                var expression = callers.submit(() -> language.createExpression("ref:q"));
                assertThat(expression.get(10, TimeUnit.SECONDS)).isNotNull();
            }
        } finally {
            callers.shutdownNow();
        }
    }

    @Test
    void concurrentEvaluationRegistryCreationReturnsOneInstance() throws Exception {
        ExecutorService callers = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        try (var context = new DefaultCamelContext()) {
            List<Future<SemanticEvaluations>> results = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                results.add(callers.submit(() -> {
                    assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                    return SemanticEvaluations.get(context);
                }));
            }
            start.countDown();
            var expected = results.get(0).get(10, TimeUnit.SECONDS);
            for (var result : results) {
                assertThat(result.get(10, TimeUnit.SECONDS)).isSameAs(expected);
            }
        } finally {
            start.countDown();
            callers.shutdownNow();
        }
    }

    @Test
    void cachedRegistryLookupDoesNotWaitForAnotherContext() throws Exception {
        ExecutorService callers = Executors.newFixedThreadPool(2);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var creating = new DefaultCamelContext(); var cached = new DefaultCamelContext()) {
            var expected = SemanticEvaluations.get(cached);
            creating.getCamelContextExtension().lazyAddContextPlugin(SemanticEvaluations.class, () -> {
                entered.countDown();
                try {
                    assertThat(release.await(30, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                // Leave the plugin absent so get() creates and registers it after this lookup is released.
                return null;
            });
            var creation = callers.submit(() -> SemanticEvaluations.get(creating));
            try {
                assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
                var lookup = callers.submit(() -> SemanticEvaluations.get(cached));
                assertThat(lookup.get(10, TimeUnit.SECONDS)).isSameAs(expected);
            } finally {
                release.countDown();
                assertThat(creation.get(10, TimeUnit.SECONDS))
                        .as("registry created after the lazy lookup returns no plugin").isNotNull();
            }
        } finally {
            release.countDown();
            callers.shutdownNow();
        }
    }

    private static SemanticLanguage language(DefaultCamelContext context) {
        SemanticEvaluations.get(context).replace("test", Map.of("q",
                new SemanticEvaluation(
                        "boolean", "expert", null,
                        Map.of("instructions", "Is this valid?", "threshold", 0.5, "uncertainty", 0.0,
                                "uncertaintyPolicy", "fail"))));
        SemanticLanguage language = new SemanticLanguage();
        language.setCamelContext(context);
        context.getRegistry().bind("expert", new Adapter());
        return language;
    }

    public static class Adapter extends TestSemanticAdapter {
        @Override
        public void validate(SemanticEvaluation evaluation) {
        }

        @Override
        public SemanticResult evaluate(SemanticEvaluation evaluation, Object state) {
            return new SemanticResult(true, null, null, null, null);
        }
    }

}
