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
package org.apache.camel.dsl.jbang.launcher.selfupdate;

import java.time.Duration;
import java.util.Map;
import java.util.Properties;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UpdateCheckerTest {

    @Test
    void formatsNoticeWhenNewerVersionCached() {
        String notice = UpdateChecker.formatNotice("4.22.0", "4.23.0");

        assertThat(notice)
                .isEqualTo("camel: a new version is available (4.22.0 -> 4.23.0). Run 'camel self-update' to install it.");
    }

    @Test
    void shouldNotifyReturnsTrueWhenCachedVersionIsNewer() {
        Properties cache = new Properties();
        cache.setProperty("latest_version", "4.23.0");

        assertThat(UpdateChecker.shouldNotify(cache, "4.22.0")).isTrue();
    }

    @Test
    void shouldNotifyReturnsFalseWhenCachedVersionIsSameOrOlder() {
        Properties cache = new Properties();
        cache.setProperty("latest_version", "4.22.0");

        assertThat(UpdateChecker.shouldNotify(cache, "4.22.0")).isFalse();
    }

    @Test
    void shouldNotifyReturnsFalseWhenCacheEmpty() {
        assertThat(UpdateChecker.shouldNotify(new Properties(), "4.22.0")).isFalse();
    }

    @Test
    void isStaleReturnsTrueWhenNoTimestampCached() {
        assertThat(UpdateChecker.isStale(new Properties(), System.currentTimeMillis())).isTrue();
    }

    @Test
    void isStaleReturnsFalseWithinTwentyFourHours() {
        Properties cache = new Properties();
        long now = 1_800_000_000_000L;
        cache.setProperty("last_checked", Long.toString(now - Duration.ofHours(1).toMillis()));

        assertThat(UpdateChecker.isStale(cache, now)).isFalse();
    }

    @Test
    void isStaleReturnsTrueAfterTwentyFourHours() {
        Properties cache = new Properties();
        long now = 1_800_000_000_000L;
        cache.setProperty("last_checked", Long.toString(now - Duration.ofHours(25).toMillis()));

        assertThat(UpdateChecker.isStale(cache, now)).isTrue();
    }

    @Test
    void skipsSelfUpdateInvocation() {
        assertThat(UpdateChecker.isEligible(new String[] { "self-update" }, "false")).isFalse();
    }

    @Test
    void skipsWhenOptedOut() {
        assertThat(UpdateChecker.isEligible(new String[] { "version", "get" }, "false")).isFalse();
    }

    @Test
    void eligibleForOrdinaryInvocation() {
        assertThat(UpdateChecker.isEligible(new String[] { "version", "get" }, null)).isTrue();
    }

    @Test
    void eligibleForNoArgsInvocation() {
        assertThat(UpdateChecker.isEligible(new String[0], null)).isTrue();
    }

    @Test
    void targetsLatestWhenUnpinnedAndTheAliasForSelectorPins() {
        assertThat(UpdateChecker.targetOf(null)).isEqualTo("latest");
        assertThat(UpdateChecker.targetOf(Pin.parse("LTS"))).isEqualTo("lts");
        assertThat(UpdateChecker.targetOf(Pin.parse("4.22"))).isEqualTo("4.22");
    }

    @Test
    void cacheWithoutTargetCountsAsLatest() {
        Properties cache = new Properties();
        long now = 1_800_000_000_000L;
        cache.setProperty("last_checked", Long.toString(now - Duration.ofHours(1).toMillis()));
        cache.setProperty("latest_version", "4.23.0");

        assertThat(UpdateChecker.isStale(cache, now, "latest")).isFalse();
        assertThat(UpdateChecker.shouldNotify(cache, "4.22.0", "latest")).isTrue();
    }

    @Test
    void notifiesSelectorPinFromItsOwnAliasOnly() {
        Properties cache = new Properties();
        long now = 1_800_000_000_000L;
        cache.setProperty("last_checked", Long.toString(now - Duration.ofHours(1).toMillis()));
        cache.setProperty("target", "4.22");
        cache.setProperty("latest_version", "4.22.3");

        assertThat(UpdateChecker.isStale(cache, now, "4.22")).isFalse();
        assertThat(UpdateChecker.shouldNotify(cache, "4.22.2", "4.22")).isTrue();
        assertThat(UpdateChecker.shouldNotify(cache, "4.22.3", "4.22")).isFalse();
        // Resolved for another target (the pin changed since): stale, and never used for a notice.
        assertThat(UpdateChecker.isStale(cache, now, "lts")).isTrue();
        assertThat(UpdateChecker.shouldNotify(cache, "4.22.2", "lts")).isFalse();
        assertThat(UpdateChecker.isStale(cache, now)).isTrue();
        assertThat(UpdateChecker.shouldNotify(cache, "4.22.2")).isFalse();
    }

    @Test
    void resolvesTheAliasMatchingThePin() throws Exception {
        HttpServer server = ManifestFetcherTest.serve(Map.of(
                "/releases/latest.properties", ManifestFetcherTest.manifest("4.24.0"),
                "/releases/lts.properties", ManifestFetcherTest.manifest("4.22.2"),
                "/releases/4.23.properties", ManifestFetcherTest.manifest("4.23.1"),
                "/releases/4.21.properties", ManifestFetcherTest.manifest("4.22.0")));
        try {
            ManifestFetcher fetcher = ManifestFetcherTest.fetcherFor(server);

            assertThat(UpdateChecker.resolve(fetcher, null)).isEqualTo("4.24.0");
            assertThat(UpdateChecker.resolve(fetcher, Pin.parse("LTS"))).isEqualTo("4.22.2");
            assertThat(UpdateChecker.resolve(fetcher, Pin.parse("4.23"))).isEqualTo("4.23.1");
            assertThatThrownBy(() -> UpdateChecker.resolve(fetcher, Pin.parse("4.21")))
                    .isInstanceOf(SelfUpdateException.class)
                    .hasMessageContaining("manifest version (4.22.0) does not match this install, which is pinned to the 4.21"
                                          + " release line");
            assertThatThrownBy(() -> UpdateChecker.resolve(fetcher, Pin.parse("4.20")))
                    .isInstanceOf(SelfUpdateException.class)
                    .hasMessageContaining("no manifest for 4.20");
        } finally {
            server.stop(0);
        }
    }
}
