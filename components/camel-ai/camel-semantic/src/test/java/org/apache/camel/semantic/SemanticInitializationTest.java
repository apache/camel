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
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.language.semantic.SemanticLanguage;
import org.apache.camel.support.service.ServiceSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticInitializationTest {
    @Test
    void initializationDoesNotAcquirePublicContextOrRegistryMonitors() throws Exception {
        ExecutorService callers = Executors.newSingleThreadExecutor();
        try (var context = new DefaultCamelContext()) {
            context.start();
            synchronized (context) {
                var questions = callers.submit(() -> SemanticQuestions.get(context));
                assertThat(questions.get(10, TimeUnit.SECONDS)).isSameAs(SemanticQuestions.get(context));
            }
            var language = language(context, Adapter.class);
            synchronized (context.getRegistry()) {
                var expression = callers.submit(() -> language.createExpression("ref:q"));
                assertThat(expression.get(10, TimeUnit.SECONDS)).isNotNull();
            }
        } finally {
            callers.shutdownNow();
        }
    }

    @Test
    void concurrentQuestionRegistryCreationReturnsOneInstance() throws Exception {
        ExecutorService callers = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        try (var context = new DefaultCamelContext()) {
            List<Future<SemanticQuestions>> results = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                results.add(callers.submit(() -> {
                    assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                    return SemanticQuestions.get(context);
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
    void concurrentLanguagesCannotOverwriteTheAdapterOwner() throws Exception {
        ExecutorService callers = Executors.newFixedThreadPool(2);
        CyclicBarrier start = new CyclicBarrier(2);
        CountingAdapter.constructed.set(0);
        CountingAdapter.started.set(0);
        CountingAdapter.stopped.set(0);
        try (var context = new DefaultCamelContext()) {
            context.start();
            var first = language(context, CountingAdapter.class);
            var second = language(context, CountingAdapter.class);
            List<Future<String>> results = new ArrayList<>();
            for (var language : List.of(first, second)) {
                results.add(callers.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    try {
                        language.createExpression("ref:q");
                        return "created";
                    } catch (RuntimeCamelException e) {
                        assertThat(e).hasRootCauseInstanceOf(IllegalArgumentException.class)
                                .hasRootCauseMessage(
                                        "Semantic adapter registry name is already bound: " + SemanticLanguage.ADAPTER_NAME);
                        return "collision";
                    }
                }));
            }
            assertThat(List.of(results.get(0).get(10, TimeUnit.SECONDS), results.get(1).get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("created", "collision");
            assertThat(CountingAdapter.constructed).hasValue(1);
            assertThat(CountingAdapter.started).hasValue(1);
            assertThat(context.getRegistry().lookupByName(SemanticLanguage.ADAPTER_NAME)).isInstanceOf(CountingAdapter.class);
        } finally {
            callers.shutdownNow();
        }
        assertThat(CountingAdapter.stopped).hasValue(1);
    }

    @Test
    void blockedConstructorDoesNotBlockAnotherContext() throws Exception {
        ExecutorService callers = Executors.newFixedThreadPool(2);
        BlockingAdapter.entered = new CountDownLatch(1);
        BlockingAdapter.release = new CountDownLatch(1);
        try (var first = new DefaultCamelContext(); var second = new DefaultCamelContext()) {
            first.start();
            second.start();
            var blocked = language(first, BlockingAdapter.class);
            var independent = language(second, Adapter.class);
            Future<?> creation = callers.submit(() -> blocked.createExpression("ref:q"));
            try {
                assertThat(BlockingAdapter.entered.await(10, TimeUnit.SECONDS)).isTrue();
                var other = callers.submit(() -> independent.createExpression("ref:q"));
                assertThat(other.get(10, TimeUnit.SECONDS)).isNotNull();
            } finally {
                BlockingAdapter.release.countDown();
                creation.get(10, TimeUnit.SECONDS);
            }
        } finally {
            BlockingAdapter.release.countDown();
            callers.shutdownNow();
        }
    }

    private static SemanticLanguage language(DefaultCamelContext context, Class<? extends SemanticAdapter> type) {
        SemanticQuestions.get(context).replace("test", Map.of("q",
                new SemanticQuestion(
                        SemanticQuestion.Type.BOOLEAN, "Is this valid?", null, null, null, 0.5, 0,
                        SemanticQuestion.UncertaintyPolicy.FAIL)));
        SemanticLanguage language = new SemanticLanguage();
        language.setCamelContext(context);
        language.setAdapter(type.getName());
        return language;
    }

    public static class Adapter extends ServiceSupport implements SemanticAdapter {
        @Override
        public void validate(SemanticQuestion question) {
        }

        @Override
        public SemanticResult evaluate(SemanticQuestion question, Object state) {
            return new SemanticResult(true, null, null, null, null);
        }
    }

    public static class CountingAdapter extends Adapter {
        static final AtomicInteger constructed = new AtomicInteger();
        static final AtomicInteger started = new AtomicInteger();
        static final AtomicInteger stopped = new AtomicInteger();

        public CountingAdapter() {
            constructed.incrementAndGet();
        }

        @Override
        protected void doStart() {
            started.incrementAndGet();
        }

        @Override
        protected void doStop() {
            stopped.incrementAndGet();
        }
    }

    public static class BlockingAdapter extends Adapter {
        static CountDownLatch entered;
        static CountDownLatch release;

        public BlockingAdapter() throws InterruptedException {
            entered.countDown();
            assertThat(release.await(30, TimeUnit.SECONDS)).isTrue();
        }
    }
}
