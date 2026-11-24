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
package org.apache.camel.component.ai.prompt;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * A prompt request that is in flight inside an {@code ai-prompt} route when the route is stopped completes: the
 * shutdown strategy suspends the consumer, which deregisters the prompt, waits for the request, and then stops the
 * route.
 */
class AiPromptRouteStopInFlightTest extends CamelTestSupport {

    private final CountDownLatch entered = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("ai-prompt:held?tags=test&description=Held inside the route").routeId("held")
                        .process(exchange -> {
                            entered.countDown();
                            if (!release.await(10, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("The request was not released");
                            }
                        })
                        .setBody(constant("ok"));
            }
        };
    }

    @Test
    void aRequestHeldInsideTheRouteCompletesWhenTheRouteStops() throws Exception {
        AiPromptSpec spec = findSpec("held");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<AiPromptResult> request = executor.submit(
                    () -> AiPromptExecutor.execute(spec, Map.of(), spec.getConsumer().getEndpoint().createExchange()));
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();

            Future<?> stop = executor.submit(() -> {
                context.getRouteController().stopRoute("held");
                return null;
            });
            // the shutdown strategy has suspended the consumer and now waits for the request
            await().atMost(10, TimeUnit.SECONDS).until(() -> spec.getConsumer().isSuspended());
            assertThat(AiPromptRegistry.getOrCreate(context).getPromptsByTag("test"))
                    .extracting(AiPromptSpec::getName)
                    .doesNotContain("held");
            release.countDown();

            AiPromptResult result = request.get(10, TimeUnit.SECONDS);
            assertThat(result).isInstanceOf(AiPromptResult.Success.class);
            assertThat(((AiPromptResult.Success) result).value()).isEqualTo("ok");
            stop.get(10, TimeUnit.SECONDS);
            assertThat(context.getRouteController().getRouteStatus("held")).isEqualTo(ServiceStatus.Stopped);
        } finally {
            executor.shutdownNow();
        }
    }

    private AiPromptSpec findSpec(String promptName) {
        return AiPromptRegistry.getOrCreate(context).getPromptsByTag("test").stream()
                .filter(s -> promptName.equals(s.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Prompt not found: " + promptName));
    }
}
