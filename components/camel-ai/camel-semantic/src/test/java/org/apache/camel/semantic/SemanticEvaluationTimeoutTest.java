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

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import org.apache.camel.console.DevConsole;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticEvaluationTimeoutTest {
    @Test
    void timeoutInterruptsTheProviderAndReturnsAnError() throws Exception {
        try (var context = new DefaultCamelContext()) {
            var expert = new WaitingExpert();
            context.getRegistry().bind("waiting", expert);
            context.start();
            var console = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("semantic-evaluate");
            JsonObject result = (JsonObject) console.call(DevConsole.MediaType.JSON, options(500));
            assertThat(result).containsEntry("status", "failed");
            assertThat(result.getString("error")).contains("timed out", "cancellation requested");
            assertThat(expert.entered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(expert.interrupted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat((JsonObject) console.call(DevConsole.MediaType.JSON, options(50001)))
                    .containsEntry("status", "failed");
        }
    }

    @Test
    void interruptingTheCallerCancelsItsEvaluation() throws Exception {
        try (var context = new DefaultCamelContext()) {
            var expert = new WaitingExpert();
            context.getRegistry().bind("waiting", expert);
            context.start();
            var console = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("semantic-evaluate");
            FutureTask<Object> call = new FutureTask<>(() -> console.call(DevConsole.MediaType.JSON, options(50000)));
            Thread caller = new Thread(call);
            caller.start();
            try {
                assertThat(expert.entered.await(5, TimeUnit.SECONDS)).isTrue();
                call.cancel(true);
                assertThat(expert.interrupted.await(5, TimeUnit.SECONDS)).isTrue();
            } finally {
                call.cancel(true);
                caller.join(5000);
            }
        }
    }

    @Test
    void concurrentCallsHaveNoUnboundedQueue() throws Exception {
        try (var context = new DefaultCamelContext()) {
            var expert = new WaitingExpert(2);
            context.getRegistry().bind("waiting", expert);
            context.start();
            var console = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("semantic-evaluate");
            FutureTask<Object> first = new FutureTask<>(() -> console.call(DevConsole.MediaType.JSON, options(50000)));
            FutureTask<Object> second = new FutureTask<>(() -> console.call(DevConsole.MediaType.JSON, options(50000)));
            Thread a = new Thread(first);
            Thread b = new Thread(second);
            a.start();
            b.start();
            try {
                assertThat(expert.entered.await(5, TimeUnit.SECONDS)).isTrue();
                JsonObject response = (JsonObject) console.call(DevConsole.MediaType.JSON, options(500));
                assertThat(response).containsEntry("status", "failed");
                assertThat(response.getString("error")).contains("Busy");
            } finally {
                first.cancel(true);
                second.cancel(true);
                a.join(5000);
                b.join(5000);
            }
        }
    }

    private static Map<String, Object> options(long timeout) {
        return Map.of("expert", "waiting", "operation", "injection", "input", "test", "timeout", timeout);
    }

    static class WaitingExpert extends SemanticMetadataConsoleTest.Detector {
        final CountDownLatch entered;

        WaitingExpert() {
            this(1);
        }

        WaitingExpert(int callers) {
            entered = new CountDownLatch(callers);
        }

        final CountDownLatch interrupted = new CountDownLatch(1);

        @Override
        public void validate(SemanticEvaluation evaluation) {
        }

        @Override
        public SemanticResult evaluate(SemanticEvaluation evaluation, Object state) {
            entered.countDown();
            try {
                new CountDownLatch(1).await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                interrupted.countDown();
                Thread.currentThread().interrupt();
            }
            return new SemanticResult(false, null, null, null, null);
        }
    }
}
