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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A new or stopped integration is noticed on every tab: the cheap look at the status files in ~/.camel asks for a full
 * scan when they change, at most once a second. Lookups that wait for the diagram retry until found.
 */
@Isolated
class DataRefreshMembershipTest {

    private String originalHome;

    @AfterEach
    void tearDown() {
        if (originalHome != null) {
            CommandLineHelper.useHomeDir(originalHome);
        }
    }

    @Test
    void aNewOrStoppedIntegrationAsksForAFullScan(@TempDir Path home) throws Exception {
        originalHome = CommandLineHelper.getHomeDir().toString();
        CommandLineHelper.useHomeDir(home.toString());
        Path camelDir = CommandLineHelper.getCamelDir();
        Files.createDirectories(camelDir);

        DataRefreshService service = newService();
        long now = 10_000;
        // the first look sees nothing new
        assertThat(service.membershipChanged(now)).isFalse();

        Files.writeString(camelDir.resolve("12345-status.json"), "{}");
        // within a second the files are not looked at again
        assertThat(service.membershipChanged(now + 500)).isFalse();
        assertThat(service.membershipChanged(now + 1000)).isTrue();
        assertThat(service.membershipChanged(now + 2000)).isFalse();

        Files.delete(camelDir.resolve("12345-status.json"));
        assertThat(service.membershipChanged(now + 3000)).isTrue();
        assertThat(service.membershipChanged(now + 4000)).isFalse();
    }

    @Test
    void aKnownIntegrationThatIsGoneAsksForAFullScan(@TempDir Path home) throws Exception {
        originalHome = CommandLineHelper.getHomeDir().toString();
        CommandLineHelper.useHomeDir(home.toString());
        Files.createDirectories(CommandLineHelper.getCamelDir());

        DataRefreshService service = newService();
        assertThat(service.membershipChanged(10_000)).isFalse();
        // a pid that no longer runs, while its status file is already gone
        service.setCachedPidsForTesting(List.of(Long.MAX_VALUE));
        assertThat(service.membershipChanged(11_000)).isTrue();
        service.setCachedPidsForTesting(List.of(ProcessHandle.current().pid()));
        assertThat(service.membershipChanged(12_000)).isFalse();
    }

    @Test
    void retryUntilFoundWaitsForTheLookup() {
        AtomicInteger calls = new AtomicInteger();
        assertThat(TuiToolRegistry.retryUntilFound(() -> calls.incrementAndGet() >= 3 ? "route1" : null, () -> false, 5000))
                .isEqualTo("route1");
        assertThat(calls.get()).isEqualTo(3);

        assertThat(TuiToolRegistry.retryUntilFound(() -> null, () -> false, 300)).isNull();
        // the diagram has loaded and the route is not in it: no need to wait
        calls.set(0);
        long start = System.currentTimeMillis();
        assertThat(TuiToolRegistry.retryUntilFound(() -> {
            calls.incrementAndGet();
            return null;
        }, () -> calls.get() >= 2, 5000)).isNull();
        assertThat(calls.get()).isEqualTo(2);
        assertThat(System.currentTimeMillis() - start).isLessThan(2000);
        // no wait: a single look
        calls.set(0);
        assertThat(TuiToolRegistry.retryUntilFound(() -> {
            calls.incrementAndGet();
            return null;
        }, () -> false, 0)).isNull();
        assertThat(calls.get()).isEqualTo(1);
    }

    private static DataRefreshService newService() {
        return new DataRefreshService(
                "test",
                new DataRefreshService.RefreshContext() {
                    @Override
                    public int selectedTab() {
                        return 0;
                    }

                    @Override
                    public boolean isSwitchPopupVisible() {
                        return false;
                    }

                    @Override
                    public String getPendingAutoSelect() {
                        return null;
                    }

                    @Override
                    public void clearPendingAutoSelect() {
                    }

                    @Override
                    public void onInfraAutoSelected(int tableIndex, String pid) {
                    }

                    @Override
                    public boolean isInfraSelected() {
                        return false;
                    }
                },
                Path::of, Path::of);
    }
}
