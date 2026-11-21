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
package org.apache.camel.catalog.cve;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-27328: the CVSS 3.x base score of the CVE Audit tab, checked against the scores NVD publishes.
 */
class CvssScoreTest {

    @Test
    void scoresTheBaseVector() {
        // a remote code execution: 9.8
        assertThat(CvssScore.baseScore("CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H")).isEqualTo(9.8);
        // CVE-2021-45046 (9.0) and CVE-2021-44228 (10.0) of Log4j, with a changed scope
        assertThat(CvssScore.baseScore("CVSS:3.1/AV:N/AC:H/PR:N/UI:N/S:C/C:H/I:H/A:H")).isEqualTo(9.0);
        assertThat(CvssScore.baseScore("CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:C/C:H/I:H/A:H")).isEqualTo(10.0);
        // a reflected XSS: 6.1
        assertThat(CvssScore.baseScore("CVSS:3.1/AV:N/AC:L/PR:N/UI:R/S:C/C:L/I:L/A:N")).isEqualTo(6.1);
        // a denial of service: 7.5
        assertThat(CvssScore.baseScore("CVSS:3.0/AV:N/AC:L/PR:N/UI:N/S:U/C:N/I:N/A:H")).isEqualTo(7.5);
        assertThat(CvssScore.baseScore("CVSS:3.1/AV:L/AC:H/PR:H/UI:R/S:U/C:L/I:N/A:N")).isEqualTo(1.8);
        assertThat(CvssScore.baseScore("CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:N/I:N/A:N")).isEqualTo(0.0);
    }

    @Test
    void noScoreForCvss4OrAnIncompleteVector() {
        assertThat(CvssScore.baseScore("CVSS:4.0/AV:N/AC:L/AT:N/PR:N/UI:N/VC:H/VI:H/VA:H/SC:N/SI:N/SA:N")).isNull();
        assertThat(CvssScore.baseScore("CVSS:3.1/AV:N/AC:L")).isNull();
        assertThat(CvssScore.baseScore(null)).isNull();
    }

    @Test
    void severityAndFormat() {
        assertThat(CvssScore.severity(9.8)).isEqualTo("CRITICAL");
        assertThat(CvssScore.severity(7.0)).isEqualTo("HIGH");
        assertThat(CvssScore.severity(6.9)).isEqualTo("MEDIUM");
        assertThat(CvssScore.severity(3.9)).isEqualTo("LOW");
        assertThat(CvssScore.severity(0.0)).isEqualTo("NONE");
        assertThat(CvssScore.format(7.5)).isEqualTo("7.5");
        assertThat(CvssScore.format(10.0)).isEqualTo("10.0");
        assertThat(CvssScore.format(null)).isEmpty();
    }
}
