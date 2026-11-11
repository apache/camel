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
package org.apache.camel.component.opa;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Timeout(10)
class OpaReloadableWasmEvaluatorTest {

    private static OpaWasmEvaluator.Bundle bundle(int version) {
        return new OpaWasmEvaluator.Bundle(new byte[] { (byte) version }, "{\"version\":" + version + "}");
    }

    private OpaReloadableWasmEvaluator evaluator(
            DefaultCamelContext context,
            OpaReloadableWasmEvaluator.BundleLoader loader,
            OpaReloadableWasmEvaluator.EvaluatorFactory factory)
            throws Exception {
        return new OpaReloadableWasmEvaluator(context, 0, loader, factory, "authz/allow", "allow", "*", "", false, false);
    }

    @Test
    void publishesChangedContentAndSkipsUnchangedContent() throws Exception {
        var content = new AtomicReference<>(bundle(1));
        var old = mock(OpaWasmEvaluator.class);
        var replacement = mock(OpaWasmEvaluator.class);
        when(old.evaluateDecision(anyMap())).thenReturn(false);
        when(replacement.evaluateDecision(anyMap())).thenReturn(true);
        try (var context = new DefaultCamelContext();
             var evaluator = evaluator(context, content::get, b -> b.wasm()[0] == 1 ? old : replacement)) {
            String original = evaluator.getReloadStatus().getActiveRevision();
            assertThat(evaluator.reload()).isFalse();
            verify(old, never()).close();
            content.set(bundle(2));
            assertThat(evaluator.reload()).isTrue();
            assertThat(evaluator.getReloadStatus().getActiveRevision()).hasSize(64).isNotEqualTo(original);
            assertThat(evaluator.evaluateDecision(Map.of())).isEqualTo(true);
            verify(old).close();
            assertThat(evaluator.reload()).isFalse();
        }
        verify(replacement).close();
    }

    @Test
    void failedReplacementRetainsLastValidatedRevisionAndSafeDiagnostics() throws Exception {
        var content = new AtomicReference<>(bundle(1));
        var old = mock(OpaWasmEvaluator.class);
        when(old.evaluateDecision(anyMap())).thenReturn(true);
        try (var context = new DefaultCamelContext();
             var evaluator = evaluator(context, content::get, b -> {
                 if (b.wasm()[0] == 2) {
                     throw new IOException("secret resource credentials");
                 }
                 return old;
             })) {
            String revision = evaluator.getReloadStatus().getActiveRevision();
            content.set(bundle(2));
            assertThatThrownBy(evaluator::reload).isInstanceOf(IOException.class);
            assertThat(evaluator.getReloadStatus().getActiveRevision()).isEqualTo(revision);
            assertThat(evaluator.getReloadStatus().getLastReloadFailureType()).isEqualTo(IOException.class.getName());
            assertThat(evaluator.getReloadStatus().getLastFailedReload()).isNotNull();
            assertThat(evaluator.evaluateDecision(Map.of())).isEqualTo(true);
            verify(old, never()).close();
            content.set(bundle(1));
            assertThat(evaluator.reload()).isFalse();
            assertThat(evaluator.getReloadStatus().getLastReloadFailureType()).isNull();
        }
    }

    @Test
    void oldEvaluationsDrainWithoutBlockingNewRevision() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var content = new AtomicReference<>(bundle(1));
        var old = mock(OpaWasmEvaluator.class);
        var replacement = mock(OpaWasmEvaluator.class);
        when(old.evaluateDecision(anyMap())).thenAnswer(call -> {
            entered.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            return false;
        });
        when(replacement.evaluateDecision(anyMap())).thenReturn(true);
        var executor = Executors.newSingleThreadExecutor();
        try (var context = new DefaultCamelContext();
             var evaluator = evaluator(context, content::get, b -> b.wasm()[0] == 1 ? old : replacement)) {
            var decision = executor.submit(() -> evaluator.evaluateDecision(Map.of()));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            content.set(bundle(2));
            assertThat(evaluator.reload()).isTrue();
            verify(old, never()).close();
            assertThat(evaluator.evaluateDecision(Map.of())).isEqualTo(true);
            release.countDown();
            assertThat(decision.get(5, TimeUnit.SECONDS)).isEqualTo(false);
            verify(old).close();
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void closeDuringWarmupRejectsAndClosesUnpublishedReplacement() throws Exception {
        var warming = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var content = new AtomicReference<>(bundle(1));
        var old = mock(OpaWasmEvaluator.class);
        var replacement = mock(OpaWasmEvaluator.class);
        var executor = Executors.newSingleThreadExecutor();
        try (var context = new DefaultCamelContext()) {
            var evaluator = evaluator(context, content::get, b -> {
                if (b.wasm()[0] == 1) {
                    return old;
                }
                warming.countDown();
                assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
                return replacement;
            });
            String revision = evaluator.getReloadStatus().getActiveRevision();
            content.set(bundle(2));
            var reload = executor.submit(() -> {
                assertThatThrownBy(evaluator::reload).isInstanceOf(IllegalStateException.class);
            });
            assertThat(warming.await(5, TimeUnit.SECONDS)).isTrue();
            evaluator.close();
            release.countDown();
            reload.get(5, TimeUnit.SECONDS);
            assertThat(evaluator.getReloadStatus().getActiveRevision()).isEqualTo(revision);
            verify(old).close();
            verify(replacement).close();
            evaluator.close();
            verify(old, times(1)).close();
            assertThatThrownBy(() -> evaluator.evaluateDecision(Map.of())).isInstanceOf(IllegalStateException.class);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void stoppedWatcherCannotPublishAnInFlightReload() throws Exception {
        var warming = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var content = new AtomicReference<>(bundle(1));
        var old = mock(OpaWasmEvaluator.class);
        var replacement = mock(OpaWasmEvaluator.class);
        var executor = Executors.newSingleThreadExecutor();
        try (var context = new DefaultCamelContext();
             var evaluator = evaluator(context, content::get, b -> {
                 if (b.wasm()[0] == 2) {
                     warming.countDown();
                     assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
                     return replacement;
                 }
                 return old;
             })) {
            String revision = evaluator.getReloadStatus().getActiveRevision();
            content.set(bundle(2));
            var reload = executor.submit(() -> {
                assertThatThrownBy(evaluator::reload).isInstanceOf(IllegalStateException.class);
            });
            assertThat(warming.await(5, TimeUnit.SECONDS)).isTrue();
            evaluator.stopWatching();
            release.countDown();
            reload.get(5, TimeUnit.SECONDS);
            assertThat(evaluator.getReloadStatus().getActiveRevision()).isEqualTo(revision);
            verify(replacement).close();
            verify(old, never()).close();
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void managedWatcherLoadsChangedResourceAndCanRestart() throws Exception {
        var content = new AtomicReference<>(bundle(1));
        var old = mock(OpaWasmEvaluator.class);
        var replacement = mock(OpaWasmEvaluator.class);
        try (var context = new DefaultCamelContext();
             var evaluator = new OpaReloadableWasmEvaluator(
                     context, 10, content::get,
                     b -> b.wasm()[0] == 1 ? old : replacement,
                     "authz/allow", "allow", "*", "", false, false)) {
            String original = evaluator.getReloadStatus().getActiveRevision();
            evaluator.startWatching();
            content.set(bundle(2));
            await().atMost(5, TimeUnit.SECONDS)
                    .until(() -> !evaluator.getReloadStatus().getActiveRevision().equals(original));
            evaluator.stopWatching();
            verify(old).close();
            evaluator.startWatching();
            evaluator.stopWatching();
        }
        verify(replacement).close();
    }

    @Test
    void revisionIncludesDataAndDistinguishesMissingDataFromEmptyObject() throws Exception {
        var content = new AtomicReference<>(new OpaWasmEvaluator.Bundle(new byte[] { 1 }, null));
        var first = mock(OpaWasmEvaluator.class);
        var second = mock(OpaWasmEvaluator.class);
        try (var context = new DefaultCamelContext();
             var evaluator = evaluator(context, content::get, b -> b.data() == null ? first : second)) {
            String original = evaluator.getReloadStatus().getActiveRevision();
            content.set(new OpaWasmEvaluator.Bundle(new byte[] { 1 }, "{}"));
            assertThat(evaluator.reload()).isTrue();
            assertThat(evaluator.getReloadStatus().getActiveRevision()).isNotEqualTo(original);
        }
    }

    @Test
    void closeDrainsAdmittedDecisionWithoutClosingItsPoolEarly() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var delegate = mock(OpaWasmEvaluator.class);
        when(delegate.evaluateDecision(anyMap())).thenAnswer(call -> {
            entered.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            return true;
        });
        var executor = Executors.newSingleThreadExecutor();
        try (var context = new DefaultCamelContext()) {
            var evaluator = evaluator(context, () -> bundle(1), b -> delegate);
            var decision = executor.submit(() -> evaluator.evaluateDecision(Map.of()));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            evaluator.close();
            verify(delegate, never()).close();
            assertThatThrownBy(evaluator::reload).isInstanceOf(IllegalStateException.class);
            release.countDown();
            assertThat(decision.get(5, TimeUnit.SECONDS)).isEqualTo(true);
            verify(delegate).close();
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void defaultDoesNotReloadResourceOnEachDecision() throws Exception {
        var loads = new java.util.concurrent.atomic.AtomicInteger();
        var delegate = mock(OpaWasmEvaluator.class);
        when(delegate.evaluateDecision(anyMap())).thenReturn(true);
        try (var context = new DefaultCamelContext();
             var evaluator = evaluator(context, () -> {
                 loads.incrementAndGet();
                 return bundle(1);
             }, b -> delegate)) {
            evaluator.startWatching();
            evaluator.evaluateDecision(Map.of());
            evaluator.evaluateDecision(Map.of());
            assertThat(loads.get()).isEqualTo(1);
        }
    }

    @Test
    void malformedDataNeverReachesReplacementFactory() throws Exception {
        var content = new AtomicReference<>(bundle(1));
        var creations = new java.util.concurrent.atomic.AtomicInteger();
        var delegate = mock(OpaWasmEvaluator.class);
        try (var context = new DefaultCamelContext();
             var evaluator = evaluator(context, content::get, b -> {
                 creations.incrementAndGet();
                 return delegate;
             })) {
            String revision = evaluator.getReloadStatus().getActiveRevision();
            content.set(new OpaWasmEvaluator.Bundle(new byte[] { 1 }, "{invalid"));
            assertThatThrownBy(evaluator::reload).isInstanceOf(Exception.class);
            content.set(new OpaWasmEvaluator.Bundle(new byte[] { 1 }, "{} {}"));
            assertThatThrownBy(evaluator::reload).isInstanceOf(Exception.class);
            content.set(new OpaWasmEvaluator.Bundle(new byte[] { 1 }, " "));
            assertThatThrownBy(evaluator::reload).isInstanceOf(IllegalArgumentException.class);
            assertThat(creations.get()).isEqualTo(1);
            assertThat(evaluator.getReloadStatus().getActiveRevision()).isEqualTo(revision);
        }
    }

    @Test
    void rapidReloadsCloseEachRetiredSnapshotOnce() throws Exception {
        var content = new AtomicReference<>(bundle(1));
        var delegates = new java.util.ArrayList<OpaWasmEvaluator>();
        try (var context = new DefaultCamelContext();
             var evaluator = evaluator(context, content::get, b -> {
                 var delegate = mock(OpaWasmEvaluator.class);
                 when(delegate.evaluateDecision(anyMap())).thenReturn((int) b.wasm()[0]);
                 delegates.add(delegate);
                 return delegate;
             })) {
            for (int version = 2; version <= 10; version++) {
                content.set(bundle(version));
                assertThat(evaluator.reload()).isTrue();
                assertThat(evaluator.evaluateDecision(Map.of())).isEqualTo(version);
                verify(delegates.get(version - 2)).close();
                verify(delegates.get(version - 1), never()).close();
            }
        }
        for (var delegate : delegates) {
            verify(delegate, times(1)).close();
        }
    }

}
