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

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NightlyTest {

    @Test
    void classifiesNightlySelectors() {
        assertThat(Nightly.isLatest("SNAPSHOT")).isTrue();
        assertThat(Nightly.isLatest("snapshot")).isTrue();
        assertThat(Nightly.isSnapshotVersion("4.24.0-SNAPSHOT")).isTrue();
        assertThat(Nightly.isBuild("4.24.0-20261108.143653-24")).isTrue();
        assertThat(Nightly.isNightly("4.24.0")).isFalse();
        assertThat(Nightly.isNightly("LTS")).isFalse();
        assertThat(Nightly.normalize("snapshot")).isEqualTo("SNAPSHOT");
        assertThat(Nightly.normalize("4.24.0-SNAPSHOT")).isEqualTo("4.24.0-SNAPSHOT");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "4.24.0-20261108.143653", "4.24.0-20261108-24", "4.24.0-2026110.143653-24", "4.24.0-20261108.143653-",
            "4.24.0-20261108.143653-24-1", "4.24-20261108.143653-24", "4.24.0-20261108.143653-24/..",
            "../4.24.0-20261108.143653-24", "4.24.0-20261108.143653-1234567890" })
    void rejectsMalformedBuilds(String value) {
        assertThat(Nightly.isBuild(value)).isFalse();
        assertThat(Nightly.isNightly(value)).isFalse();
    }

    @Test
    void releaseOfSnapshotVersion() {
        assertThat(Nightly.releaseOf("4.22.3-SNAPSHOT")).isEqualTo("4.22.3");
        assertThatThrownBy(() -> Nightly.releaseOf("SNAPSHOT")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void snapshotVersionOfBuild() {
        assertThat(Nightly.snapshotVersionOf("4.22.3-20261107.143456-15")).isEqualTo("4.22.3-SNAPSHOT");
        assertThatThrownBy(() -> Nightly.snapshotVersionOf("4.22.3")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void comparesBuildsByTimestampThenBuildNumber() {
        assertThat(Nightly.compare("4.24.0-20261109.010000-25", "4.24.0-20261108.143653-24")).isPositive();
        assertThat(Nightly.compare("4.24.0-20261108.143653-24", "4.24.0-20261108.143653-24")).isZero();
        // the build number is compared as a number, not as text
        assertThat(Nightly.compare("4.24.0-20261108.143653-10", "4.24.0-20261108.143653-9")).isPositive();
    }

    @Test
    void runningBuildIsTheVersionDirectoryOfTheLauncher() {
        Path versions = Path.of("/home/user/.local/share/camel-cli/versions");

        assertThat(Nightly.runningBuild(
                versions.resolve("4.24.0-20261108.143653-24/lib/camel-launcher-4.24.0-SNAPSHOT.jar"), versions))
                .contains("4.24.0-20261108.143653-24");
        assertThat(Nightly.runningBuild(versions.resolve("4.23.0/lib/camel-launcher-4.23.0.jar"), versions)).isEmpty();
        assertThat(Nightly.runningBuild(Path.of("/opt/camel/lib/camel-launcher-4.24.0-SNAPSHOT.jar"), versions)).isEmpty();
        assertThat(Nightly.runningBuild(null, versions)).isEmpty();
    }
}
