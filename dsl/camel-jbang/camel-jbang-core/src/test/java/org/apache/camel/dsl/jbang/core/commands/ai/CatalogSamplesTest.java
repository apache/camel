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
package org.apache.camel.dsl.jbang.core.commands.ai;

import java.util.List;

import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CatalogSamplesTest {

    @Test
    void onExceptionIsATopLevelEntryWithASample() {
        JsonObject o = CatalogSamples.sample("onException", 2);
        assertThat(o.getString("name")).isEqualTo("onException");
        assertThat(o.getString("placement")).contains("top-level");
        JsonArray samples = (JsonArray) o.get("samples");
        assertThat(samples).isNotEmpty().hasSizeLessThanOrEqualTo(2);
        assertThat(((JsonObject) samples.get(0)).getString("yaml")).startsWith("- onException:");
    }

    @Test
    void kebabCaseAndPartsResolveToTheEip() {
        JsonObject o = CatalogSamples.sample("circuit-breaker", 1);
        assertThat(o.getString("name")).isEqualTo("circuitBreaker");
        assertThat(o.getString("placement")).contains("step");
        assertThat(((JsonObject) ((JsonArray) o.get("samples")).get(0)).getString("yaml")).contains("onFallback");

        o = CatalogSamples.sample("onFallback", 1);
        assertThat(o.getString("name")).isEqualTo("circuitBreaker");
        assertThat(o.getString("partOf")).isEqualTo("circuitBreaker");
    }

    @Test
    void intentWordsResolveToTheEip() {
        JsonObject o = CatalogSamples.sample("read file", 1);
        assertThat(o.getString("name")).isEqualTo("poll");
        assertThat(o.getString("note")).contains("poll EIP");
        assertThat(((JsonObject) ((JsonArray) o.get("samples")).get(0)).getString("yaml")).contains("poll");
        assertThat(CatalogSamples.sample("batch", 1).getString("name")).isEqualTo("aggregate");
        assertThat(CatalogSamples.sample("retry", 1).getString("name")).isEqualTo("onException");
    }

    @Test
    void unknownNameGetsSuggestions() {
        JsonObject o = CatalogSamples.sample("aggregat", 2);
        assertThat(o.getString("error")).contains("aggregat");
        assertThat(String.valueOf(o.get("suggestions"))).contains("aggregate");
    }

    @Test
    void limitIsCapped() {
        JsonObject o = CatalogSamples.sample("aggregate", 50);
        assertThat((JsonArray) o.get("samples")).hasSizeLessThanOrEqualTo(CatalogSamples.MAX_LIMIT);
    }

    @Test
    void everySampleIsACompleteTopLevelList() {
        assertThat(CatalogSamples.names()).hasSizeGreaterThan(80);
        CatalogSamples.samples().forEach((name, list) -> list.forEach(s -> assertThat(s.get("yaml"))
                .as("%s from %s", name, s.get("source")).startsWith("- ")));
    }

    @Test
    void eipSamplesComeFromTheCatalogDocsAndTheRestFromTheShippedSet() {
        org.apache.camel.catalog.CamelCatalog catalog = new org.apache.camel.catalog.DefaultCamelCatalog();
        JsonObject o = CatalogSamples.sample(catalog, "poll", 2);
        JsonArray samples = (JsonArray) o.get("samples");
        assertThat(samples).isNotEmpty();
        assertThat(((JsonObject) samples.get(0)).getString("source")).startsWith("poll-eip.adoc (Camel ");
        assertThat(((JsonObject) samples.get(0)).getString("yaml")).startsWith("- ");
        assertThat(((JsonObject) ((JsonArray) CatalogSamples.sample(catalog, "circuitBreaker", 1).get("samples")).get(0))
                .getString("source"))
                .startsWith("circuitBreaker-eip.adoc (Camel ");
        assertThat(((JsonObject) ((JsonArray) CatalogSamples.sample(catalog, "rest", 1).get("samples")).get(0))
                .getString("source"))
                .isEqualTo("rest-dsl.adoc");
    }

    @Test
    void theToolIsOnTheSharedRegistry() {
        ToolDescriptor td = ToolRegistry.authoringTools().stream()
                .filter(t -> t.name().equals("camel_catalog_sample")).findFirst().orElseThrow();
        assertThat(td.params()).anyMatch(p -> p.name().equals("name") && p.required());
        assertThat(td.params()).anyMatch(p -> p.name().equals("limit") && !p.required());
    }

    @Test
    void beansIsATopLevelEntryWithASample() {
        // a model asked for "beans" after a "beans is a list" error and got "No sample"
        JsonObject o = CatalogSamples.sample("beans", 3);
        assertThat(o.getString("name")).isEqualTo("beans");
        assertThat(o.getString("placement")).contains("top-level");
        JsonArray samples = (JsonArray) o.get("samples");
        assertThat(samples).hasSize(3);
        // the second example of yaml-dsl.adoc declares a bean and calls it from a route, within the default limit
        String yaml = ((JsonObject) samples.get(1)).getString("yaml");
        assertThat(yaml).contains("- beans:").contains("- name: myBean").contains("ref: myBean");
    }

    @Test
    void theGeneratedSamplesCoverTheEipsAndTheFileEntries() {
        // eip-samples.json is generated from the documentation by the build (CAMEL-24713): the EIP pages, the user
        // manual pages of the file entries, and the beans of yaml-dsl.adoc
        assertThat(CatalogSamples.names())
                .contains("aggregate", "split", "choice", "circuitBreaker", "deadLetterChannel", "intercept",
                        "keyValueRepository", "route", "rest", "routeTemplate", "routeConfiguration", "onException",
                        "onCompletion", "doTry", "beans")
                .hasSizeGreaterThan(100);
        assertThat(CatalogSamples.samples().get("deadLetterChannel").get(0).get("source"))
                .isEqualTo("dead-letter-channel.adoc");
        assertThat(CatalogSamples.samples().get("doTry")).extracting(s -> s.get("source"))
                .contains("doTry-eip.adoc", "try-catch-finally.adoc");
        assertThat(CatalogSamples.samples().values().stream().mapToInt(List::size).sum()).isGreaterThan(300);
    }

    @Test
    void patternPagesWithoutTheEipSuffixAreReadFromTheCatalog() {
        org.apache.camel.catalog.CamelCatalog catalog = new org.apache.camel.catalog.DefaultCamelCatalog();
        JsonArray samples = (JsonArray) CatalogSamples.sample(catalog, "deadLetterChannel", 1).get("samples");
        assertThat(((JsonObject) samples.get(0)).getString("source")).startsWith("dead-letter-channel.adoc (Camel ");
        samples = (JsonArray) CatalogSamples.sample(catalog, "keyValueRepository", 1).get("samples");
        assertThat(((JsonObject) samples.get(0)).getString("source")).startsWith("keyValueRepository.adoc (Camel ");
    }
}
