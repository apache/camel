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

class DiagnoseResourcesTest {

    private final DiagnoseResources resources;

    DiagnoseResourcesTest() {
        resources = new DiagnoseResources();
        resources.diagnoseData = new DiagnoseData();
    }

    // ---- Exception catalog ----

    @Test
    void catalogReturnsValidJson() throws Exception {
        TextResourceContents contents = resources.exceptionCatalog();

        assertThat(contents.uri()).isEqualTo("camel://error/exception-catalog");
        assertThat(contents.mimeType()).isEqualTo("application/json");

        JsonObject result = (JsonObject) Jsoner.deserialize(contents.text());
        assertThat(result.getInteger("totalCount")).isGreaterThan(0);

        JsonArray exceptions = result.getCollection("exceptions");
        assertThat(exceptions).isNotEmpty();
    }

    @Test
    void catalogContainsAllKnownExceptions() throws Exception {
        TextResourceContents contents = resources.exceptionCatalog();
        JsonObject result = (JsonObject) Jsoner.deserialize(contents.text());

        DiagnoseData data = new DiagnoseData();
        int expectedCount = data.getKnownExceptions().size();

        assertThat(result.getInteger("totalCount")).isEqualTo(expectedCount);

        JsonArray exceptions = result.getCollection("exceptions");
        assertThat(exceptions).hasSize(expectedCount);
    }

    @Test
    void catalogEntriesHaveRequiredFields() throws Exception {
        TextResourceContents contents = resources.exceptionCatalog();
        JsonObject result = (JsonObject) Jsoner.deserialize(contents.text());
        JsonArray exceptions = result.getCollection("exceptions");

        for (Object obj : exceptions) {
            JsonObject entry = (JsonObject) obj;
            assertThat(entry.getString("name")).isNotBlank();
            assertThat(entry.getString("description")).isNotBlank();
            JsonArray entryDocs = entry.getCollection("documentationLinks");
            assertThat(entryDocs).isNotEmpty();
        }
    }

    // ---- Exception detail for known exception ----

    @Test
    void detailReturnsKnownException() throws Exception {
        TextResourceContents contents = resources.exceptionDetail("NoSuchEndpointException");

        assertThat(contents.uri()).isEqualTo("camel://error/exception/NoSuchEndpointException");
        assertThat(contents.mimeType()).isEqualTo("application/json");

        JsonObject result = (JsonObject) Jsoner.deserialize(contents.text());
        assertThat(result.getString("name")).isEqualTo("NoSuchEndpointException");
        assertThat(result.getBoolean("found")).isTrue();
        assertThat(result.getString("description")).isNotBlank();

        JsonArray causes = result.getCollection("commonCauses");
        assertThat(causes).isNotEmpty();

        JsonArray fixes = result.getCollection("suggestedFixes");
        assertThat(fixes).isNotEmpty();

        JsonArray docs = result.getCollection("documentationLinks");
        assertThat(docs).isNotEmpty();
    }

    @Test
    void detailReturnsAllFieldsForCamelExecutionException() throws Exception {
        TextResourceContents contents = resources.exceptionDetail("CamelExecutionException");
        JsonObject result = (JsonObject) Jsoner.deserialize(contents.text());

        assertThat(result.getBoolean("found")).isTrue();

        JsonArray causes = result.getCollection("commonCauses");
        assertThat(causes.size()).isGreaterThanOrEqualTo(3);

        JsonArray fixes = result.getCollection("suggestedFixes");
        assertThat(fixes.size()).isGreaterThanOrEqualTo(3);

        JsonArray docs = result.getCollection("documentationLinks");
        assertThat(docs.size()).isGreaterThanOrEqualTo(2);
    }

    // ---- Exception detail not found ----

    @Test
    void detailReturnsNotFoundForUnknownException() throws Exception {
        TextResourceContents contents = resources.exceptionDetail("NonExistentException");

        assertThat(contents.uri()).isEqualTo("camel://error/exception/NonExistentException");

        JsonObject result = (JsonObject) Jsoner.deserialize(contents.text());
        assertThat(result.getString("name")).isEqualTo("NonExistentException");
        assertThat(result.getBoolean("found")).isFalse();
        assertThat(result.getString("message")).contains("not in the known exceptions catalog");
    }
}
