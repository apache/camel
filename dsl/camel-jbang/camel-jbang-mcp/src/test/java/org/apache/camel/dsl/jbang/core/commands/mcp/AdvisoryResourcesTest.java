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
package org.apache.camel.dsl.jbang.core.commands.mcp;

import io.quarkiverse.mcp.server.TextResourceContents;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the {@code camel://security/advisories} MCP resources, backed by the advisory data shipped with the Camel
 * catalog. Fully offline.
 */
class AdvisoryResourcesTest {

    private AdvisoryResources resources() {
        AdvisoryResources resources = new AdvisoryResources();
        resources.advisoryService = new AdvisoryService();
        return resources;
    }

    @Test
    void advisoriesListReturnsValidJson() throws Exception {
        TextResourceContents contents = resources().securityAdvisories();

        assertThat(contents.uri()).isEqualTo("camel://security/advisories");
        assertThat(contents.mimeType()).isEqualTo("application/json");

        JsonObject result = (JsonObject) Jsoner.deserialize(contents.text());
        assertThat(result.getInteger("totalCount")).isGreaterThan(70);
        assertThat(result.getString("source")).isEqualTo("https://camel.apache.org/security/");
        assertThat(result.containsKey("advisoryDataUnavailable")).isFalse();

        JsonArray advisories = result.getCollection("advisories");
        JsonObject known = null;
        for (Object o : advisories) {
            JsonObject json = (JsonObject) o;
            if ("CVE-2025-27636".equals(json.getString("cve"))) {
                known = json;
            }
        }
        assertThat(known).isNotNull();
        assertThat(known.getString("severity")).isEqualTo("MEDIUM");
        assertThat(known.getString("fixed")).contains("4.10.2");
        assertThat(known.getString("url")).isEqualTo("https://camel.apache.org/security/CVE-2025-27636.html");
    }

    @Test
    void advisoryDetailReturnsAllFields() throws Exception {
        TextResourceContents contents = resources().securityAdvisoryDetail("CVE-2013-4330");

        JsonObject result = (JsonObject) Jsoner.deserialize(contents.text());
        assertThat(result.getBoolean("found")).isTrue();
        assertThat(result.getString("cve")).isEqualTo("CVE-2013-4330");
        assertThat(result.getString("severity")).isEqualTo("CRITICAL");
        assertThat(result.getString("affected")).contains("2.9.0 up to 2.9.7");
        assertThat(result.getString("fixed")).contains("2.12.1");
        assertThat(result.getString("mitigation")).isNotBlank();
        assertThat(result.getString("date")).isNotBlank();
    }

    @Test
    void advisoryDetailIsCaseInsensitive() throws Exception {
        TextResourceContents contents = resources().securityAdvisoryDetail("cve-2025-27636");

        JsonObject result = (JsonObject) Jsoner.deserialize(contents.text());
        assertThat(result.getBoolean("found")).isTrue();
        JsonArray components = result.getCollection("components");
        assertThat(components).contains("camel-bean", "camel-undertow");
    }

    @Test
    void advisoryDetailReportsUnknownCve() throws Exception {
        TextResourceContents contents = resources().securityAdvisoryDetail("CVE-1999-0001");

        JsonObject result = (JsonObject) Jsoner.deserialize(contents.text());
        assertThat(result.getBoolean("found")).isFalse();
        assertThat(result.getString("message")).contains("No published Apache Camel security advisory");
    }

    @Test
    void unavailableAdvisoryDataIsExplicitNotEmpty() throws Exception {
        AdvisoryResources resources = new AdvisoryResources();
        resources.advisoryService = AdvisoryServiceTest.unavailableService();

        JsonObject list = (JsonObject) Jsoner.deserialize(resources.securityAdvisories().text());
        assertThat(list.getBoolean("advisoryDataUnavailable")).isTrue();
        assertThat(list.containsKey("advisories")).isFalse();

        JsonObject detail = (JsonObject) Jsoner.deserialize(resources.securityAdvisoryDetail("CVE-2025-27636").text());
        assertThat(detail.getBoolean("advisoryDataUnavailable")).isTrue();
    }
}
