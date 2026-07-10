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
package org.apache.camel.maven.packaging;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.camel.tooling.model.JsonMapper;
import org.apache.camel.tooling.model.SecurityAdvisoryModel;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the advisory front-matter parsing of {@link UpdateSecurityAdvisoriesMojo} against canned copies of two real
 * published advisories: CVE-2025-27636 (recent front matter style) and CVE-2013-4330 (old style).
 */
class UpdateSecurityAdvisoriesMojoTest {

    private static String fixture(String name) throws IOException {
        try (InputStream in = UpdateSecurityAdvisoriesMojoTest.class
                .getResourceAsStream("/security-advisories/" + name)) {
            assertThat(in).as("fixture /security-advisories/" + name).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void parsesRecentAdvisoryStyle() throws Exception {
        SecurityAdvisoryModel model = UpdateSecurityAdvisoriesMojo.parseAdvisory(fixture("CVE-2025-27636.md"));

        assertThat(model).isNotNull();
        assertThat(model.getCve()).isEqualTo("CVE-2025-27636");
        assertThat(model.getSeverity()).isEqualTo("MEDIUM");
        assertThat(model.getSummary()).contains("Header Injection");
        assertThat(model.getAffected()).contains("4.10.0 before 4.10.2");
        assertThat(model.getFixed()).contains("4.10.2");
        assertThat(model.getMitigation()).isNotBlank();
        assertThat(model.getUrl()).isEqualTo("https://camel.apache.org/security/CVE-2025-27636.html");
        assertThat(model.getComponents())
                .contains("camel-bean", "camel-undertow", "camel-kafka", "camel-platform-http");
    }

    @Test
    void parsesOldAdvisoryStyle() throws Exception {
        SecurityAdvisoryModel model = UpdateSecurityAdvisoriesMojo.parseAdvisory(fixture("CVE-2013-4330.md"));

        assertThat(model).isNotNull();
        assertThat(model.getCve()).isEqualTo("CVE-2013-4330");
        assertThat(model.getSeverity()).isEqualTo("CRITICAL");
        assertThat(model.getAffected()).startsWith("2.9.0 up to 2.9.7");
        assertThat(model.getFixed()).contains("2.12.1");
        assertThat(model.getUrl()).isEqualTo("https://camel.apache.org/security/CVE-2013-4330.html");
        // the old advisory does not name camel-* components
        assertThat(model.getComponents()).isEmpty();
    }

    @Test
    void skipsDraftsAndNonAdvisories() {
        String draft = """
                ---
                type: security-advisory
                draft: true
                cve: CVE-2099-99999
                severity: LOW
                ---
                body
                """;
        assertThat(UpdateSecurityAdvisoriesMojo.parseAdvisory(draft)).isNull();

        String wrongType = """
                ---
                type: blog-post
                draft: false
                cve: CVE-2099-99999
                ---
                body
                """;
        assertThat(UpdateSecurityAdvisoriesMojo.parseAdvisory(wrongType)).isNull();

        assertThat(UpdateSecurityAdvisoriesMojo.parseAdvisory("no front matter")).isNull();
        assertThat(UpdateSecurityAdvisoriesMojo.parseAdvisory(null)).isNull();
    }

    @Test
    void modelRoundTripsThroughJsonMapper() throws Exception {
        SecurityAdvisoryModel model = UpdateSecurityAdvisoriesMojo.parseAdvisory(fixture("CVE-2025-27636.md"));

        JsonObject json = JsonMapper.asJsonObject(model);
        SecurityAdvisoryModel copy = JsonMapper.generateSecurityAdvisoryModel(json);

        assertThat(copy.getCve()).isEqualTo(model.getCve());
        assertThat(copy.getSeverity()).isEqualTo(model.getSeverity());
        assertThat(copy.getSummary()).isEqualTo(model.getSummary());
        assertThat(copy.getAffected()).isEqualTo(model.getAffected());
        assertThat(copy.getFixed()).isEqualTo(model.getFixed());
        assertThat(copy.getMitigation()).isEqualTo(model.getMitigation());
        assertThat(copy.getUrl()).isEqualTo(model.getUrl());
        assertThat(copy.getComponents()).isEqualTo(model.getComponents());
    }
}
