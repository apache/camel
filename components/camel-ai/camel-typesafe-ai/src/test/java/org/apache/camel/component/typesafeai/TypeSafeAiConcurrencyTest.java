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
package org.apache.camel.component.typesafeai;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.Predicate;
import org.apache.camel.component.typesafeai.TypeSafeAiCancellationTest.Cancellation;
import org.apache.camel.language.typesafeai.TypeSafeAiLanguage;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.apache.camel.builder.Builder.body;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

class TypeSafeAiConcurrencyTest extends TypeSafeAiTestSupport {
    enum Failure {
        InvalidRequest,
        InvalidResponse,
        Http,
        Transport
    }

    @Test
    void sharesLimitBetweenProducersAndPredicatesAndKeepsEndpointsIndependent() throws Exception {
        String uri = "typesafe-ai:limited?maxConcurrentRequests=2";
        Predicate predicate = predicate(uri, body(), "Refund?", 0.8);
        predicate.init(context);
        holdHeaders = true;
        var first = template.asyncSend(uri, e -> e.getMessage().setBody(request("first")));
        var second = template.asyncSend(uri, e -> e.getMessage().setBody(request("second")));
        try {
            await().atMost(5, TimeUnit.SECONDS).until(() -> requests.size() == 2);
            // Admission precedes JSON validation: even an invalid request is rejected at capacity.
            var rejected = template.asyncSend(uri, e -> e.getMessage().setBody(Map.of())).get(5, TimeUnit.SECONDS);
            assertThat(rejected.getException()).isInstanceOf(RejectedExecutionException.class)
                    .hasMessageContaining("maxConcurrentRequests");
            var exchange = new DefaultExchange(context);
            exchange.getMessage().setBody("refund");
            exchange.setProperty(TypeSafeAiLanguage.RESULT, "previous result");
            assertThatThrownBy(() -> predicate.matches(exchange)).hasCauseInstanceOf(RejectedExecutionException.class);
            assertThat(exchange.getProperty(TypeSafeAiLanguage.RESULT)).isNull();
            assertThat(requests).hasSize(2);

            // Leave the first two responses blocked while a different endpoint submits its request.
            var independent = template.asyncSend("typesafe-ai:independent?maxConcurrentRequests=1",
                    e -> e.getMessage().setBody(request("independent")));
            await().atMost(5, TimeUnit.SECONDS).until(() -> requests.size() == 3);
            assertThat(first).isNotDone();
            assertThat(second).isNotDone();

            release.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS).getException()).isNull();
            assertThat(second.get(5, TimeUnit.SECONDS).getException()).isNull();
            assertThat(independent.get(5, TimeUnit.SECONDS).getException()).isNull();
            assertThat(predicate.matches(exchange)).isTrue();
            assertThat(requests).hasSize(4);
        } finally {
            release.countDown();
        }
    }

    @ParameterizedTest
    @EnumSource(Failure.class)
    void releasesCapacityAfterFailure(Failure failure) throws Exception {
        TypeSafeAiEndpoint endpoint
                = context.getEndpoint("typesafe-ai:failure?maxConcurrentRequests=1", TypeSafeAiEndpoint.class);
        Map<String, Object> input = request("refund");
        Class<? extends Exception> expected = IOException.class;
        switch (failure) {
            case InvalidRequest -> {
                input = Map.of();
                expected = IllegalArgumentException.class;
            }
            case InvalidResponse -> respond = request -> "{}";
            case Http -> {
                status = 503;
                expected = TypeSafeAiHttpException.class;
            }
            case Transport -> respond = request -> {
                throw new IllegalStateException("Close the connection without a response");
            };
        }
        Map<String, Object> invalid = input;
        assertThatThrownBy(() -> endpoint.evaluate(invalid)).isInstanceOf(expected);
        status = 200;
        respond = request -> noulResponse(0.9);
        assertThat(endpoint.evaluate(request("retry")).getString("model")).isEqualTo("jev-1.13.0");
        assertThat(endpoint.evaluate(request("next")).getString("model")).isEqualTo("jev-1.13.0");
    }

    @ParameterizedTest
    @EnumSource(Cancellation.class)
    void releasesCapacityAfterCancellation(Cancellation cancellation) throws Exception {
        TypeSafeAiEndpoint endpoint = context.getEndpoint("typesafe-ai:cancel?maxConcurrentRequests=1&requestTimeout="
                                                          + (cancellation == Cancellation.Timeout ? 1500 : 30000),
                TypeSafeAiEndpoint.class);
        var caller = new AtomicReference<Thread>();
        var tasks = Executors.newSingleThreadExecutor();
        holdHeaders = true;
        try {
            var outcome = tasks.submit(() -> {
                caller.set(Thread.currentThread());
                try {
                    endpoint.evaluate(request("held"));
                    return null;
                } catch (Exception e) {
                    return e;
                }
            });
            await().atMost(5, TimeUnit.SECONDS).until(() -> requests.size() == 1);
            switch (cancellation) {
                case Timeout -> assertThat(outcome.get(5, TimeUnit.SECONDS)).isInstanceOf(TimeoutException.class);
                case Interrupt -> {
                    caller.get().interrupt();
                    assertThat(outcome.get(5, TimeUnit.SECONDS)).isInstanceOf(InterruptedException.class);
                }
                case Stop -> {
                    endpoint.stop();
                    assertThat(outcome.get(5, TimeUnit.SECONDS)).isInstanceOf(CancellationException.class);
                    assertThatThrownBy(() -> endpoint.evaluate(request("stopped")))
                            .isInstanceOf(IllegalStateException.class).hasMessage("TypeSafe AI endpoint is not started");
                    endpoint.start();
                }
            }
            holdHeaders = false;
            release.countDown();
            assertThat(endpoint.evaluate(request("retry")).getString("model")).isEqualTo("jev-1.13.0");
        } finally {
            release.countDown();
            tasks.shutdownNow();
        }
    }
}
