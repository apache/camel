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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
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
    void eipAliasesResolveThroughTheCatalog() {
        // the aliases of the EIP models, so the intent map need not repeat them
        CamelCatalog catalog = new DefaultCamelCatalog();
        JsonObject o = CatalogSamples.sample(catalog, "fan-out", 1);
        assertThat(o.getString("name")).isEqualTo("multicast");
        assertThat(o.getString("note")).contains("multicast EIP");
        assertThat(CatalogSamples.sample(catalog, "broadcast", 1).getString("name")).isEqualTo("multicast");
        // an exact component name (chunk is the Chunk templating component) beats an alias of an EIP, unless asked
        assertThat(CatalogSamples.sample(catalog, "chunk", 1).getString("kind")).isEqualTo("component");
        assertThat(CatalogSamples.sample(catalog, "eip", "chunk", 1).getString("name")).isEqualTo("split");
        // an alias of an EIP whose page has no YAML example still names the EIP
        JsonObject dedup = CatalogSamples.sample(catalog, "dedup", 1);
        assertThat(dedup.getString("error")).contains("dedup");
        assertThat(dedup.getString("eip")).isEqualTo("idempotentConsumer");
        assertThat(dedup.getString("hint")).contains("idempotentConsumer EIP");
        assertThat(CatalogSamples.sample(catalog, "rate-limit", 1).getString("name")).isEqualTo("throttle");
        assertThat(CatalogSamples.sample(catalog, "router", 1).getString("name")).isEqualTo("choice");
        // an aliased part of an EIP shows the whole
        assertThat(CatalogSamples.sample(catalog, "fallback", 1).getString("name")).isEqualTo("circuitBreaker");
        // an exact name says nothing about a match
        assertThat(CatalogSamples.sample(catalog, "multicast", 1).get("note")).isNull();
        // without a catalog only the shipped names and intents resolve
        assertThat(CatalogSamples.sample("fan-out", 1).getString("error")).contains("fan-out");
        assertThat(CatalogSamples.INTENTS).doesNotContainKeys("fan out", "broadcast", "router", "fallback", "split");
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
        CamelCatalog catalog = new DefaultCamelCatalog();
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
        CamelCatalog catalog = new DefaultCamelCatalog();
        JsonArray samples = (JsonArray) CatalogSamples.sample(catalog, "deadLetterChannel", 1).get("samples");
        assertThat(((JsonObject) samples.get(0)).getString("source")).startsWith("dead-letter-channel.adoc (Camel ");
        samples = (JsonArray) CatalogSamples.sample(catalog, "keyValueRepository", 1).get("samples");
        assertThat(((JsonObject) samples.get(0)).getString("source")).startsWith("keyValueRepository.adoc (Camel ");
    }

    // CAMEL-24720: components, data formats and languages from their documentation

    private static String yaml(JsonObject answer, int i) {
        return ((JsonObject) ((JsonArray) answer.get("samples")).get(i)).getString("yaml");
    }

    private static String source(JsonObject answer, int i) {
        return ((JsonObject) ((JsonArray) answer.get("samples")).get(i)).getString("source");
    }

    @Test
    void componentSamplesComeFromTheComponentPageWithItsEndpointFirst() {
        CamelCatalog catalog = new DefaultCamelCatalog();
        JsonObject o = CatalogSamples.sample(catalog, "kafka", 2);
        assertThat(o.getString("name")).isEqualTo("kafka");
        assertThat(o.getString("kind")).isEqualTo("component");
        assertThat(o.getString("placement")).contains("from:").contains("to:");
        assertThat((JsonArray) o.get("samples")).hasSize(2);
        // the first example of the kafka page is an idempotent consumer with a SQL repository: a kafka endpoint first
        assertThat(yaml(o, 0)).contains("kafka:");
        assertThat(source(o, 0)).startsWith("kafka-component.adoc (Camel ");
        assertThat(o.get("note")).isNull();
        assertThat((Integer) o.get("count")).isGreaterThan(5);
        // any case, and the kind when given
        assertThat(CatalogSamples.sample(catalog, "component", "Kafka", 1).getString("name")).isEqualTo("kafka");
        // without a catalog there is no component page to read
        assertThat(CatalogSamples.sample("kafka", 1).getString("error")).contains("kafka");
    }

    @Test
    void theSubPagesOfAComponentBelongToIt() {
        CamelCatalog catalog = new DefaultCamelCatalog();
        assertThat(CatalogSamples.subPages(catalog, "aws2-s3"))
                .contains("aws2-s3-consumer-examples", "aws2-s3-producer-operations", "aws2-s3-streaming");
        // mina-sftp is a component of its own: its pages are not mina's
        assertThat(CatalogSamples.subPages(catalog, "mina")).isEmpty();
        assertThat(CatalogSamples.subPages(catalog, "mina-sftp")).contains("mina-sftp-authentication");
        // the examples of the sub-pages count, after the ones of the main page
        Matcher m = Pattern.compile("\\[source,yaml\\]").matcher(catalog.asciiDoc("aws2-s3-component"));
        int onMainPage = 0;
        while (m.find()) {
            onMainPage++;
        }
        JsonObject o = CatalogSamples.sample(catalog, "aws2-s3", 5);
        assertThat((Integer) o.get("count")).isGreaterThan(onMainPage);
        assertThat(source(o, 0)).startsWith("aws2-s3-component.adoc");
    }

    @Test
    void aConsumerOrProducerOnlyComponentSaysItsSide() {
        CamelCatalog catalog = new DefaultCamelCatalog();
        JsonObject timer = CatalogSamples.sample(catalog, "timer", 1);
        assertThat(timer.getString("placement")).contains("from: only");
        assertThat(yaml(timer, 0)).contains("timer:");
        JsonObject log = CatalogSamples.sample(catalog, "component", "log", 1);
        assertThat(log.getString("kind")).isEqualTo("component");
        assertThat(log.getString("placement")).contains("to: only");
        // without a kind the log EIP answers first, and says the component is there too
        JsonObject eip = CatalogSamples.sample(catalog, "log", 1);
        assertThat(eip.getString("kind")).isEqualTo("eip");
        assertThat((JsonArray) eip.get("also")).containsExactly("component");
        assertThat(eip.getString("hint")).contains("kind");
        // an EIP that is no component says nothing
        assertThat(CatalogSamples.sample(catalog, "split", 1).get("also")).isNull();
    }

    @Test
    void dataFormatAndLanguageSamplesShowTheStepAndTheExpression() {
        CamelCatalog catalog = new DefaultCamelCatalog();
        JsonObject csv = CatalogSamples.sample(catalog, "csv", 1);
        assertThat(csv.getString("kind")).isEqualTo("dataformat");
        assertThat(csv.getString("placement")).contains("marshal");
        assertThat(yaml(csv, 0)).contains("csv:").containsAnyOf("marshal:", "unmarshal:");
        assertThat(source(csv, 0)).startsWith("csv-dataformat.adoc (Camel ");
        JsonObject jq = CatalogSamples.sample(catalog, "jq", 1);
        assertThat(jq.getString("kind")).isEqualTo("language");
        assertThat(jq.getString("placement")).contains("expression");
        assertThat(yaml(jq, 0)).contains("jq:");
        assertThat(source(jq, 0)).startsWith("jq-language.adoc (Camel ");
    }

    @Test
    void aNameInSeveralKindsReturnsTheChoiceUnlessOnlyOneHasSamples() {
        CamelCatalog catalog = new DefaultCamelCatalog();
        JsonObject avro = CatalogSamples.sample(catalog, "avro", 1);
        assertThat(avro.get("samples")).isNull();
        assertThat((JsonArray) avro.get("kinds")).containsExactly("component", "dataformat");
        assertThat(avro.getString("hint")).contains("kind");
        assertThat(CatalogSamples.sample(catalog, "dataformat", "avro", 1).getString("kind")).isEqualTo("dataformat");
        assertThat(CatalogSamples.sample(catalog, "Data-Formats", "avro", 1).getString("kind")).isEqualTo("dataformat");
        assertThat(yaml(CatalogSamples.sample(catalog, "component", "avro", 1), 0)).contains("avro:");
        // file is a component and a language, and only the component page has examples
        JsonObject file = CatalogSamples.sample(catalog, "file", 1);
        assertThat(file.getString("kind")).isEqualTo("component");
        assertThat(yaml(file, 0)).contains("file:");
        assertThat(CatalogSamples.sample(catalog, "steps", "file", 1).getString("error")).contains("kind must be one of");
    }

    @Test
    void componentsSharingAPageAndPagesThatOnceLackedAnEndpointExample() {
        CamelCatalog catalog = new DefaultCamelCatalog();
        // smtp, imap and pop3 are documented on the mail page
        JsonObject smtp = CatalogSamples.sample(catalog, "smtp", 1);
        assertThat(smtp.getString("kind")).isEqualTo("component");
        assertThat(source(smtp, 0)).startsWith("mail-component.adoc (Camel ");
        assertThat(yaml(smtp, 0)).containsAnyOf("smtp:", "imap:", "pop3:");
        assertThat(smtp.get("note")).isNull();
        JsonObject mail = CatalogSamples.sample(catalog, "mail", 1);
        assertThat(mail.getString("name")).isEqualTo("mail");
        assertThat(mail.get("note")).isNull();
        // mapstruct used to be shown only through convertBodyTo; the page now has a mapstruct: endpoint example too
        JsonObject mapstruct = CatalogSamples.sample(catalog, "mapstruct", 1);
        assertThat((Integer) mapstruct.get("count")).isPositive();
        assertThat(mapstruct.get("note")).isNull();
        assertThat(yaml(mapstruct, 0)).contains("mapstruct:");
        // knative used to show only Kubernetes manifests; the page now has route examples
        JsonObject knative = CatalogSamples.sample(catalog, "knative", 1);
        assertThat((Integer) knative.get("count")).isPositive();
        assertThat(knative.get("hint")).isNull();
        assertThat(yaml(knative, 0)).contains("knative:");
    }

    @Test
    void anUnknownNameGetsTheCatalogSuggestionsToo() {
        CamelCatalog catalog = new DefaultCamelCatalog();
        JsonObject o = CatalogSamples.sample(catalog, "mqtt", 1);
        assertThat(o.getString("error")).contains("mqtt");
        assertThat(String.valueOf(o.get("suggestions"))).contains("(component)");
        // a suggestion is a name with a page to come back for, and no EIP is guessed for another kind
        JsonObject typo = CatalogSamples.sample(catalog, "dataformat", "univocity", 1);
        assertThat(typo.getString("error")).contains("as a dataformat");
        assertThat(String.valueOf(typo.get("suggestions"))).contains("univocityCsv (dataformat)");
        assertThat(typo.get("eip")).isNull();
    }

    @Test
    void aDataFormatDocumentedUnderAnotherPageNameStillAnswers() {
        CamelCatalog catalog = new DefaultCamelCatalog();
        // jackson is on the jackson2 and jackson3 pages, one example each
        assertThat(CatalogSamples.dataFormatPages(catalog, "jackson"))
                .containsExactly("jackson2-dataformat", "jackson3-dataformat");
        JsonObject jackson = CatalogSamples.sample(catalog, "jackson", 5);
        assertThat(jackson.getString("kind")).isEqualTo("dataformat");
        assertThat(source(jackson, 0)).startsWith("jackson2-dataformat.adoc");
        assertThat((Integer) jackson.get("count")).isEqualTo(2);
        // bindyCsv is on the bindy page, which has no YAML example
        assertThat(CatalogSamples.dataFormatPages(catalog, "bindyCsv")).containsExactly("bindy-dataformat");
        JsonObject bindy = CatalogSamples.sample(catalog, "bindyCsv", 1);
        assertThat(bindy.getString("kind")).isEqualTo("dataformat");
        assertThat((Integer) bindy.get("count")).isZero();
        assertThat(bindy.getString("hint")).contains("no YAML route example");
    }
}
