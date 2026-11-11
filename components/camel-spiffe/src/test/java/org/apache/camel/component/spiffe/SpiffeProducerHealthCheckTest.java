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
package org.apache.camel.component.spiffe;

import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Map;

import io.spiffe.bundle.jwtbundle.JwtBundle;
import io.spiffe.bundle.jwtbundle.JwtBundleSet;
import io.spiffe.spiffeid.TrustDomain;
import io.spiffe.svid.x509svid.X509Svid;
import io.spiffe.workloadapi.Watcher;
import io.spiffe.workloadapi.WorkloadApiClient;
import io.spiffe.workloadapi.X509Context;
import org.apache.camel.health.HealthCheck;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpiffeProducerHealthCheckTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void reportsUnavailableBeforeMaterialThenTracksValidityWithoutFurtherApiCalls() throws Exception {
        MutableClock clock = new MutableClock();
        SpiffeProducerHealthCheck check = check(SpiffeOperation.fetchX509Svid, clock);
        WorkloadApiClient client = mock(WorkloadApiClient.class);
        check.observe(client);
        Watcher<X509Context> watcher = x509Watcher(client);
        assertThat(check.call().getState()).isEqualTo(HealthCheck.State.DOWN);

        watcher.onUpdate(x509(NOW.plusSeconds(120)));
        assertThat(check.call().getState()).isEqualTo(HealthCheck.State.UP);
        assertThat(check.call().getDetails()).containsEntry("spiffe.remainingValidityMillis", 120000L)
                .containsEntry("spiffe.lastUpdate", NOW.toString());
        clock.now = NOW.plusSeconds(70);
        assertThat(check.call().getState()).isEqualTo(HealthCheck.State.DOWN);
        assertThat(check.call().getDetails()).containsEntry("spiffe.expiring", true)
                .containsEntry("spiffe.materialValid", true);
        clock.now = NOW.plusSeconds(121);
        assertThat(check.call().getDetails()).containsEntry("spiffe.materialValid", false);
        verify(client, never()).fetchX509Context();
    }

    @Test
    void disconnectRetainsMaterialMetadataAndRecoveryUpdatesItWithoutPublishingFailureDetails() throws Exception {
        MutableClock clock = new MutableClock();
        SpiffeProducerHealthCheck check = check(SpiffeOperation.fetchX509Svid, clock);
        WorkloadApiClient client = mock(WorkloadApiClient.class);
        check.observe(client);
        Watcher<X509Context> watcher = x509Watcher(client);
        watcher.onUpdate(x509(NOW.plusSeconds(120)));
        watcher.onError(new IllegalStateException("credential-must-not-appear"));

        HealthCheck.Result disconnected = check.call();
        assertThat(disconnected.getState()).isEqualTo(HealthCheck.State.DOWN);
        assertThat(disconnected.getDetails()).containsEntry("spiffe.connection", "disconnected")
                .containsEntry("spiffe.materialValid", true)
                .containsEntry("spiffe.lastUpdate", NOW.toString());
        assertThat(disconnected.getError()).isEmpty();
        assertThat(disconnected.getMessage().orElse("")).doesNotContain("credential-must-not-appear");
        assertThat(disconnected.getDetails().toString()).doesNotContain("credential-must-not-appear");

        clock.now = NOW.plusSeconds(30);
        watcher.onUpdate(x509(NOW.plusSeconds(240)));
        assertThat(check.call().getState()).isEqualTo(HealthCheck.State.UP);
        assertThat(check.call().getDetails()).containsEntry("spiffe.lastUpdate", clock.now.toString())
                .containsEntry("spiffe.expiry", NOW.plusSeconds(240).toString());
    }

    @Test
    void missingIdentityAndLateCallbacksAfterCloseNeverReportReady() throws Exception {
        SpiffeProducerHealthCheck check = check(SpiffeOperation.fetchX509Svid, new MutableClock());
        WorkloadApiClient client = mock(WorkloadApiClient.class);
        check.observe(client);
        Watcher<X509Context> watcher = x509Watcher(client);
        watcher.onUpdate(null);
        assertThat(check.call().getState()).isEqualTo(HealthCheck.State.DOWN);
        check.close();
        watcher.onUpdate(x509(NOW.plusSeconds(120)));
        watcher.onError(new IllegalStateException("late error"));
        assertThat(check.call().getState()).isEqualTo(HealthCheck.State.DOWN);
        assertThat(check.call().getDetails()).containsEntry("spiffe.connection", "stopped")
                .doesNotContainKeys("spiffe.expiry", "spiffe.lastUpdate");
    }

    @Test
    void jwtOperationsObserveBundlesWithoutClaimingIdentityExpiry() {
        for (SpiffeOperation operation : List.of(SpiffeOperation.fetchJwtSvid, SpiffeOperation.validateJwtSvid)) {
            SpiffeProducerHealthCheck check = check(operation, new MutableClock());
            WorkloadApiClient client = mock(WorkloadApiClient.class);
            check.observe(client);
            ArgumentCaptor<Watcher<JwtBundleSet>> captor = ArgumentCaptor.captor();
            verify(client).watchJwtBundles(captor.capture());
            captor.getValue().onUpdate(JwtBundleSet.emptySet());
            assertThat(check.call().getState()).isEqualTo(HealthCheck.State.DOWN);
            JwtBundleSet bundles = mock(JwtBundleSet.class);
            JwtBundle bundle = mock(JwtBundle.class);
            when(bundles.getBundles()).thenReturn(Map.of(TrustDomain.parse("example.org"), bundle));
            captor.getValue().onUpdate(bundles);
            assertThat(check.call().getState()).isEqualTo(HealthCheck.State.UP);
            assertThat(check.call().getDetails()).containsEntry("spiffe.observation", "jwt-bundles")
                    .doesNotContainKeys("spiffe.expiry", "spiffe.remainingValidityMillis", "spiffe.materialValid");
        }
    }

    @Test
    void futureDatedIdentityIsUnavailableUntilItsValidityStarts() throws Exception {
        MutableClock clock = new MutableClock();
        SpiffeProducerHealthCheck check = check(SpiffeOperation.fetchX509Svid, clock);
        WorkloadApiClient client = mock(WorkloadApiClient.class);
        check.observe(client);
        X509Context context = x509(NOW.plusSeconds(300));
        when(context.getDefaultSvid().getLeaf().getNotBefore()).thenReturn(Date.from(NOW.plusSeconds(30)));
        x509Watcher(client).onUpdate(context);
        assertThat(check.call().getDetails()).containsEntry("spiffe.materialAvailable", true)
                .containsEntry("spiffe.materialValid", false);
        assertThat(check.call().getState()).isEqualTo(HealthCheck.State.DOWN);
        clock.now = NOW.plusSeconds(30);
        assertThat(check.call().getState()).isEqualTo(HealthCheck.State.UP);
    }

    @Test
    void rejectsANegativeExpiryThreshold() {
        assertThatThrownBy(() -> new SpiffeProducerHealthCheck("test", SpiffeOperation.fetchX509Svid, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("healthCheckExpiryThreshold");
    }

    private static SpiffeProducerHealthCheck check(SpiffeOperation operation, Clock clock) {
        SpiffeProducerHealthCheck check = new SpiffeProducerHealthCheck("test", operation, 60000, clock);
        check.setEnabled(true);
        return check;
    }

    private static Watcher<X509Context> x509Watcher(WorkloadApiClient client) {
        ArgumentCaptor<Watcher<X509Context>> captor = ArgumentCaptor.captor();
        verify(client).watchX509Context(captor.capture());
        return captor.getValue();
    }

    private static X509Context x509(Instant expiry) throws Exception {
        X509Svid svid = mock(X509Svid.class);
        X509Certificate leaf = mock(X509Certificate.class);
        when(leaf.getNotAfter()).thenReturn(Date.from(expiry));
        when(svid.getLeaf()).thenReturn(leaf);
        X509Context context = mock(X509Context.class);
        when(context.getDefaultSvid()).thenReturn(svid);
        return context;
    }

    private static final class MutableClock extends Clock {
        private Instant now = NOW;

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
