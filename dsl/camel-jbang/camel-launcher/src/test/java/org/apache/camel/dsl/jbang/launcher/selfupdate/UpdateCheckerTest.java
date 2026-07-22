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
import java.util.Properties;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
}
