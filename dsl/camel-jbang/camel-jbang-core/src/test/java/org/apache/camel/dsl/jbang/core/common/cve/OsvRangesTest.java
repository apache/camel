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
package org.apache.camel.dsl.jbang.core.common.cve;

import java.util.List;

import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-27328: the version that fixes an installed version, from the affected ranges of an OSV.dev record, not the
 * fixed versions of every line and package mixed.
 */
class OsvRangesTest {

    // the shape of GHSA-jjjh-jjxp-wpff (jackson-databind): one range per line, and a second package
    private static final String RECORD = """
            {"id": "GHSA-test",
             "severity": [
               {"type": "CVSS_V4", "score": "CVSS:4.0/AV:N/AC:L/AT:N/PR:N/UI:N/VC:N/VI:N/VA:H/SC:N/SI:N/SA:N"},
               {"type": "CVSS_V3", "score": "CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:N/I:N/A:H"}],
             "affected": [
               {"package": {"ecosystem": "Maven", "name": "com.fasterxml.jackson.core:jackson-databind"},
                "ranges": [{"type": "ECOSYSTEM", "events": [{"introduced": "0"}, {"fixed": "2.12.7.1"}]},
                           {"type": "ECOSYSTEM", "events": [{"introduced": "2.13.0"}, {"fixed": "2.13.4"}]}]},
               {"package": {"ecosystem": "Maven", "name": "com.example:other"},
                "ranges": [{"type": "ECOSYSTEM", "events": [{"introduced": "0"}, {"fixed": "9.0"}]}]},
               {"package": {"ecosystem": "Maven", "name": "com.example:nofix"},
                "ranges": [{"type": "ECOSYSTEM", "events": [{"introduced": "1.0"}, {"last_affected": "1.5"}]}]}]}
            """;

    private static OsvClient.Vulnerability vulnerability() throws Exception {
        JsonObject json = (JsonObject) Jsoner.deserialize(RECORD);
        List<OsvClient.AffectedRange> ranges = OsvClient.extractRanges(json);
        return new OsvClient.Vulnerability(
                "GHSA-test", null, null, "HIGH", OsvClient.extractCvssVector(json), null,
                List.of(), List.of("2.12.7.1", "2.13.4", "9.0"), ranges);
    }

    @Test
    void theFixOfTheRangeTheVersionIsIn() throws Exception {
        OsvClient.Vulnerability v = vulnerability();
        String databind = "com.fasterxml.jackson.core:jackson-databind";
        assertThat(v.nearestFix(databind, "2.13.2")).isEqualTo("2.13.4");
        assertThat(v.nearestFix(databind, "2.12.3")).isEqualTo("2.12.7.1");
        // not the 9.0 of the other package of the record
        assertThat(v.nearestFix(databind, "2.13.3")).isEqualTo("2.13.4");
    }

    @Test
    void noFixAboveTheVersionOrForAPackageWithoutOne() throws Exception {
        OsvClient.Vulnerability v = vulnerability();
        assertThat(v.nearestFix("com.fasterxml.jackson.core:jackson-databind", "2.13.4")).isNull();
        assertThat(v.nearestFix("com.example:nofix", "1.2")).isNull();
        assertThat(v.nearestFix("com.example:unknown", "1.0")).isNull();
    }

    @Test
    void theCvss3VectorIsPreferredAsItCanBeScored() throws Exception {
        JsonObject json = (JsonObject) Jsoner.deserialize(RECORD);
        assertThat(OsvClient.extractCvssVector(json)).isEqualTo("CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:N/I:N/A:H");
        // without database_specific.severity, the severity is that of the score (7.5)
        assertThat(OsvClient.extractSeverity(json)).isEqualTo("HIGH");
    }
}
