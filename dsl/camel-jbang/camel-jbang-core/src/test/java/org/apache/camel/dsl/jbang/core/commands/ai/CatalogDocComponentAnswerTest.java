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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-25541: the component answer of camel_catalog_doc is bounded, says when nothing matches and where else to look
 * (the closest options, the prefix options, the sections of the page), resolves the names the tools hand out, and
 * answers a full application.properties key.
 */
class CatalogDocComponentAnswerTest {

    private static String call(Map<String, String> args) {
        return String.valueOf(ToolRegistry.execute("camel_catalog_doc", new ToolContext(), new HashMap<>(args)));
    }

    private static JsonObject catalogDoc(Map<String, String> args) throws Exception {
        return (JsonObject) Jsoner.deserialize(call(args));
    }

    private static List<String> strings(JsonObject answer, String key) {
        return ((JsonArray) answer.get(key)).stream().map(String::valueOf).toList();
    }

    private static List<String> names(JsonObject answer, String key) {
        return ((JsonArray) answer.get(key)).stream().map(o -> ((JsonObject) o).getString("name")).toList();
    }

    @Test
    void theDefaultAnswerOfALargeComponentIsBounded() throws Exception {
        // activemq answered 88 options in about 38 000 characters
        String json = call(Map.of("name", "activemq"));
        assertThat(json.length()).isLessThan(CatalogDocs.MAX_ANSWER_LENGTH);
        JsonObject answer = (JsonObject) Jsoner.deserialize(json);
        assertThat(answer.getBoolean("truncated")).isTrue();
        assertThat(answer.getInteger("listedOptions")).isEqualTo(CatalogDocs.DEFAULT_OPTIONS_LIMIT);
        assertThat(answer.getInteger("matchedOptions")).isGreaterThan(80);
        // the required option first, the rest by name, and how to get one of them in full
        assertThat(names(answer, "options").get(0)).isEqualTo("destinationName");
        assertThat(strings(answer, "moreOptions")).contains("brokerURL");
        assertThat(answer.getString("optionsHint")).contains("optionsFilter=", "limit=");

        // all options, also by name, still fit, and a limit lists more in full
        assertThat(call(Map.of("name", "activemq", "includeOptions", "all")).length())
                .isLessThanOrEqualTo(CatalogDocs.MAX_ANSWER_LENGTH);
        assertThat(catalogDoc(Map.of("name", "activemq", "limit", "25")).getInteger("listedOptions"))
                .isGreaterThan(CatalogDocs.DEFAULT_OPTIONS_LIMIT);
    }

    @Test
    void aFilterThatMatchesNothingSaysSoAndWhereElseToLook() throws Exception {
        JsonObject answer = catalogDoc(Map.of("name", "smtp", "optionsFilter", "subjct"));
        assertThat(answer.getInteger("matchedOptions")).isZero();
        assertThat(answer.getString("note")).contains("no option matches 'subjct'");
        JsonObject closest = (JsonObject) ((JsonArray) answer.get("closestOptions")).get(0);
        assertThat(closest.getString("name")).isEqualTo("subject");
        assertThat(closest.getString("group")).isEqualTo("producer");

        // TLS is configured through the mail.* parameters, which no option is named after: the prefix option is
        // said also when the filter matches something, and the sections about TLS come first
        answer = catalogDoc(Map.of("name", "smtp", "optionsFilter", "tls"));
        assertThat(answer.get("note")).isNull();
        assertThat(names(answer, "prefixOptions")).contains("additionalJavaMailProperties");
        assertThat(strings(answer, "sections").get(0)).isEqualTo("SSL support");
        assertThat(answer.getString("sectionsHint")).contains("contain 'tls'");
    }

    @Test
    void theSectionsOfThePageAreListedAndOneIsReturnedAsText() throws Exception {
        // xslt takes the headers as stylesheet parameters, which only a section further down the page says
        JsonObject answer = catalogDoc(Map.of("name", "xslt", "optionsFilter", "param"));
        assertThat(answer.getString("note")).contains("no option matches 'param'");
        assertThat(strings(answer, "sections").get(0))
                .isEqualTo("Getting Usable Parameters into the XSLT");

        JsonObject section = catalogDoc(Map.of("name", "xslt", "docPage", "Getting Usable Parameters into the XSLT"));
        assertThat(section.getString("section")).isEqualTo("Getting Usable Parameters into the XSLT");
        assertThat(section.getString("doc")).contains("all headers and variables are added as parameters")
                .doesNotContain("== Spring XML versions");
        assertThat(section.get("options")).isNull();

        // a sub-page keeps its name: platform-http links to the page of camel run's HTTP server
        JsonObject main = catalogDoc(Map.of("name", "platform-http", "docPage", "main"));
        assertThat(main.getString("doc")).contains("camel.server.enabled=true");
        JsonObject unknown = catalogDoc(Map.of("name", "xslt", "docPage", "no such section"));
        assertThat(unknown.getString("error")).contains("no such section");
        assertThat(strings(unknown, "sections")).contains("Dynamic stylesheets");
    }

    @Test
    void thePageNamesOfTheCatalogAndAnyCaseResolve() throws Exception {
        assertThat(catalogDoc(Map.of("name", "wireTap-eip")).getString("name")).isEqualTo("wireTap");
        assertThat(catalogDoc(Map.of("name", "jackson-dataformat")).getString("kind")).isEqualTo("dataformat");
        assertThat(catalogDoc(Map.of("name", "file-component")).getString("name")).isEqualTo("file");
        assertThat(catalogDoc(Map.of("name", "WireTap", "kind", "eip")).getString("name")).isEqualTo("wireTap");

        // a bare word of an EIP title is not that EIP: retry was optimisticLockRetryPolicy
        JsonObject retry = catalogDoc(Map.of("name", "retry"));
        assertThat(retry.getString("error")).contains("retry");
        assertThat(strings(retry, "suggestions")).startsWith("errorHandler", "onException", "redeliveryPolicy");
        assertThat(retry.getString("errorHandling")).contains("redeliveryPolicy: {maximumRedeliveries");
        // an alias still names its EIP
        assertThat(catalogDoc(Map.of("name", "fan-out")).getString("name")).isEqualTo("multicast");
    }

    @Test
    void aFullKeyAnswersItsGroupWithTheKeyFirst() throws Exception {
        JsonObject server = catalogDoc(Map.of("name", "camel.server.port"));
        assertThat(server.getString("group")).isEqualTo("camel.server");
        assertThat(server.getString("matchedKey")).isEqualTo("camel.server.port");
        assertThat(names(server, "options").get(0)).isEqualTo("camel.server.port");
        assertThat(names(server, "options")).contains("camel.server.enabled");

        JsonObject jbang = catalogDoc(Map.of("name", "camel.jbang.dependencies"));
        assertThat(jbang.getString("kind")).isEqualTo("jbang-options");
        assertThat(names(jbang, "options").get(0)).isEqualTo("camel.jbang.dependencies");

        JsonObject typo = catalogDoc(Map.of("name", "camel.server.prot"));
        assertThat(typo.getString("error")).contains("camel.server.prot");
        assertThat(strings(typo, "suggestions")).contains("camel.server.port");

        // the port of platform-http is not an option of the component
        JsonObject http = catalogDoc(Map.of("name", "platform-http", "optionsFilter", "port"));
        assertThat(http.getString("mainConfig")).contains("camel.server.enabled=true", "camel.server.port");
        assertThat(names(http, "mainOptions")).contains("camel.server.port");
        // and the start of its page says so, where the excerpt carries it
        assertThat(http.getString("documentation")).contains("camel.server.enabled=true");
    }

    @Test
    void aComponentOptionCarriesItsApplicationPropertiesKey() throws Exception {
        JsonObject answer = catalogDoc(Map.of("name", "timer", "optionsFilter", "bridgeErrorHandler"));
        List<JsonObject> options = ((JsonArray) answer.get("options")).stream().map(JsonObject.class::cast)
                .filter(o -> "bridgeErrorHandler".equals(o.getString("name"))).toList();
        assertThat(options).extracting(o -> o.getString("scope")).containsExactlyInAnyOrder("endpoint", "component");
        for (JsonObject option : options) {
            // only a component option is a camel.component.* key
            assertThat(option.getString("configKey")).isEqualTo(
                    "component".equals(option.getString("scope")) ? "camel.component.timer.bridgeErrorHandler" : null);
        }
    }
}
