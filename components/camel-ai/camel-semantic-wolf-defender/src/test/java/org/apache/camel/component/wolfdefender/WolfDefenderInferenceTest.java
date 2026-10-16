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
package org.apache.camel.component.wolfdefender;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.awaitility.Awaitility.await;

@EnabledIf("supportedRuntime")
class WolfDefenderInferenceTest {
    static boolean supportedRuntime() {
        String os = System.getProperty("os.name");
        String arch = System.getProperty("os.arch");
        boolean x64 = "amd64".equals(arch) || "x86_64".equals(arch);
        boolean arm64 = "aarch64".equals(arch);
        return os.startsWith("Linux") && (x64 || arm64)
                || os.startsWith("Mac") && arm64 || os.startsWith("Windows") && x64;
    }

    @BeforeAll
    static void offlineNativeLibraries() {
        System.setProperty("DJL_OFFLINE", "true");
        System.setProperty("RUST_FLAVOR", "cpu");
    }

    static Path resource(String name) throws Exception {
        return Path.of(WolfDefenderInferenceTest.class.getResource("/" + name).toURI());
    }

    @Test
    void realTokenizerAndTensorsMatchSyntheticModel() throws Exception {
        try (var inference = new WolfDefenderInference(resource("classifier.onnx.bin"), resource("tokenizer.json"), 1)) {
            // <bos>=2 hello=4 <eos>=3 -> logits [-9,9].
            assertThat(inference.evaluate("hello", 3, new WolfDefenderInference.Call()))
                    .isCloseTo(1 / (1 + Math.exp(-18)), within(1e-12));
            assertThatThrownBy(() -> inference.evaluate("hello", 2, new WolfDefenderInference.Call()))
                    .hasMessageContaining("maxTokens=2");
            // A tokenizer configured to truncate at four tokens must not discard this suffix.
            assertThatThrownBy(() -> inference.validateInput("hello hello hello attack", 4, new WolfDefenderInference.Call()))
                    .hasMessageContaining("maxTokens=4");
            assertThatThrownBy(() -> inference.evaluate("hello hello hello attack", 4, new WolfDefenderInference.Call()))
                    .hasMessageContaining("maxTokens=4");
            var cancelled = new WolfDefenderInference.Call();
            cancelled.cancel();
            assertThatThrownBy(() -> inference.evaluate("hello", 4, cancelled)).hasMessageContaining("cancelled");
        }
    }

    @Test
    void invalidGraphAndTokenizerFailAtStartup(@TempDir Path directory) throws Exception {
        assertThatThrownBy(() -> new WolfDefenderInference(resource("cancellation.onnx.bin"), resource("tokenizer.json"), 1))
                .hasMessageContaining("Expected input_ids");
        assertThatThrownBy(
                () -> new WolfDefenderInference(resource("classifier.onnx.bin"), directory.resolve("missing.json"), 1))
                .isInstanceOf(Exception.class);
        assertThatThrownBy(() -> WolfDefenderInference.verify(resource("classifier.onnx.bin"), "incorrect"))
                .hasMessageContaining("checksum mismatch");
    }

    @Test
    void nativeRunOptionsTerminateAnInFlightSession() throws Exception {
        var executor = Executors.newSingleThreadExecutor();
        var call = new WolfDefenderInference.Call();
        CountDownLatch entered = new CountDownLatch(1);
        AtomicReference<Thread> worker = new AtomicReference<>();
        try {
            var run = executor.submit(() -> {
                // The worker owns every native resource, including if a caller-side assertion fails.
                try (var options = new OrtSession.SessionOptions();
                     var session = OrtEnvironment.getEnvironment()
                             .createSession(resource("cancellation.onnx.bin").toString(), options);
                     call) {
                    var runOptions = call.open();
                    worker.set(Thread.currentThread());
                    entered.countDown();
                    try (var ignored = session.run(Map.of(), runOptions)) {
                        return false;
                    } catch (OrtException cancelled) {
                        return cancelled.getMessage().contains("terminate");
                    }
                }
            });
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            await().atMost(5, TimeUnit.SECONDS).until(() -> Arrays.stream(worker.get().getStackTrace())
                    .anyMatch(frame -> frame.isNativeMethod() && frame.getClassName().equals(OrtSession.class.getName())));
            call.cancel();
            assertThat(run.get(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            try {
                call.cancel();
            } finally {
                executor.shutdownNow();
            }
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }
}
