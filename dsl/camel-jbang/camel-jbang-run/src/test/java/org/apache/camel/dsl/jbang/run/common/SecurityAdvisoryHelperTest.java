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
package org.apache.camel.dsl.jbang.run.common;

import java.util.List;

import org.apache.camel.tooling.model.SecurityAdvisoryModel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityAdvisoryHelperTest {

    // the style of the recent advisories, e.g. CVE-2026-49098
    private static final String AFFECTED
            = "From 4.0.0 before 4.14.8, from 4.15.0 before 4.18.3, from 4.19.0 before 4.21.0.";
    private static final String FIXED = "4.14.8, 4.18.3 and 4.21.0";

    @Test
    void affectsVersionReadsTheRecentStyle() {
        assertThat(SecurityAdvisoryHelper.affectsVersion(AFFECTED, "4.18.1")).isTrue();
        assertThat(SecurityAdvisoryHelper.affectsVersion(AFFECTED, "4.18.3")).isFalse();
        assertThat(SecurityAdvisoryHelper.affectsVersion(AFFECTED, "4.21.0")).isFalse();
        assertThat(SecurityAdvisoryHelper.affectsVersion(AFFECTED, "3.22.0")).isFalse();
    }

    @Test
    void upgradeToTheFixOfTheSameLine() {
        assertThat(SecurityAdvisoryHelper.upgradeTo(AFFECTED, FIXED, "4.18.1")).isEqualTo("4.18.3");
        assertThat(SecurityAdvisoryHelper.upgradeTo(AFFECTED, FIXED, "4.14.2")).isEqualTo("4.14.8");
        assertThat(SecurityAdvisoryHelper.upgradeTo(AFFECTED, FIXED, "4.18.1-SNAPSHOT")).isEqualTo("4.18.3");
    }

    @Test
    void upgradeToTheLowestFixAboveWhenTheLineHasNone() {
        // 4.16 and 4.20 got no patch release of their own
        assertThat(SecurityAdvisoryHelper.upgradeTo(AFFECTED, FIXED, "4.16.0")).isEqualTo("4.18.3");
        assertThat(SecurityAdvisoryHelper.upgradeTo(AFFECTED, FIXED, "4.20.0")).isEqualTo("4.21.0");
    }

    @Test
    void noUpgradeWhenNotAffectedOrNoFixIsNamed() {
        assertThat(SecurityAdvisoryHelper.upgradeTo(AFFECTED, FIXED, "4.18.3")).isNull();
        assertThat(SecurityAdvisoryHelper.upgradeTo(AFFECTED, FIXED, "4.22.0")).isNull();
        assertThat(SecurityAdvisoryHelper.upgradeTo(AFFECTED, null, "4.18.1")).isNull();
        assertThat(SecurityAdvisoryHelper.upgradeTo(AFFECTED, FIXED, null)).isNull();
    }

    @Test
    void upgradeToSkipsAFixedVersionThatIsStillAffected() {
        // the old style, CVE-2013-4330: "2.12.1 and newer"
        String affected = "2.9.0 up to 2.9.7, 2.10.0 up to 2.10.6, 2.11.0 up to 2.11.1, 2.12.0";
        String fixed = "2.9.8, 2.10.7, 2.11.2, 2.12.1 and newer";
        assertThat(SecurityAdvisoryHelper.upgradeTo(affected, fixed, "2.10.3")).isEqualTo("2.10.7");
        assertThat(SecurityAdvisoryHelper.upgradeTo(affected, fixed, "2.12.0")).isEqualTo("2.12.1");
    }

    @Test
    void namesComponentWithOrWithoutThePrefix() {
        SecurityAdvisoryModel advisory = new SecurityAdvisoryModel();
        advisory.setComponents(List.of("camel-kafka"));
        assertThat(SecurityAdvisoryHelper.namesComponent(advisory, "camel-kafka")).isTrue();
        assertThat(SecurityAdvisoryHelper.namesComponent(advisory, "kafka")).isTrue();
        assertThat(SecurityAdvisoryHelper.namesComponent(advisory, "camel-http")).isFalse();
    }
}
