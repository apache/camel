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
package org.apache.camel.dsl.jbang.core.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Versions compare part by part as numbers: the export gates of the Quarkus platform (whose versions have four parts),
 * the order of camel version list, the self update of the launcher and the security advisories use it.
 */
class VersionHelperTest {

    @ParameterizedTest
    @CsvSource({
            "4.24.0, 4.24.0, 0",
            "4.24, 4.24.0, 0",
            "4.9.0, 4.10.0, -1",
            "4.10.0, 4.9.0, 1",
            "3.27.3.1, 3.27.4, -1",
            "3.33.1.1, 3.33.1, 1",
            "3.31.0.1, 3.31.0, 1",
            "4.99.0, 4.100.0, -1",
            "4.24.0, 4.24.0-SNAPSHOT, 1",
            "4.24.10-SNAPSHOT, 4.24.10, -1",
            "4.24.0-SNAPSHOT, 4.23.0, 1",
            "4.24.0-SNAPSHOT, 4.24.0-SNAPSHOT, 0",
            "4.24.0-RC1, 4.24.0-RC2, -1",
            "'4,10', '4,9', 1" })
    void compare(String source, String target, int expected) {
        assertThat(Integer.signum(VersionHelper.compare(source, target))).isEqualTo(expected);
    }

    @Test
    void missingVersionIsTheSame() {
        assertThat(VersionHelper.compare(null, "4.24.0")).isZero();
        assertThat(VersionHelper.compare("4.24.0", " ")).isZero();
    }

    @Test
    void ranges() {
        assertThat(VersionHelper.isGE("3.31.0.1", "3.31.0")).isTrue();
        assertThat(VersionHelper.isLE("3.27.3.1", "3.27.4")).isTrue();
        // a snapshot comes before its release, as in Maven
        assertThat(VersionHelper.isGE("4.24.0-SNAPSHOT", "4.24.0")).isFalse();
        assertThat(VersionHelper.isGE("4.24.0-SNAPSHOT", "4.23.0")).isTrue();
        assertThat(VersionHelper.isBetween("4.18.2", "4.18.0", "4.19.0")).isTrue();
        assertThat(VersionHelper.isBetween("4.19.0", "4.18.0", "4.19.0")).isFalse();
    }

    @Test
    void majorMinor() {
        assertThat(VersionHelper.getMajorMinorVersion("4.24.1")).isEqualTo("4.24");
    }
}
