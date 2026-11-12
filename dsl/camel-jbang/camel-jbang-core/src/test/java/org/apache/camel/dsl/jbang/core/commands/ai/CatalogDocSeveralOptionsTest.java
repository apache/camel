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
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-25632: in the local-model benchmark a model checked each option it meant to write with a camel_catalog_doc call
 * of its own, and ran out of tool calls before it wrote the route. The optionsFilter takes several terms, separated by
 * comma, and one call answers for all of them; a term that matches nothing is said in unmatchedTerms. A filter of one
 * term answers as before.
 */
class CatalogDocSeveralOptionsTest {

    private static JsonObject catalogDoc(Map<String, String> args) throws Exception {
        return (JsonObject) Jsoner.deserialize(
                String.valueOf(ToolRegistry.execute("camel_catalog_doc", new ToolContext(), new HashMap<>(args))));
    }

    private static List<String> names(JsonObject answer, String key) {
        JsonArray arr = (JsonArray) answer.get(key);
        return arr == null ? List.of() : arr.stream().map(o -> ((JsonObject) o).getString("name")).toList();
    }

    private static JsonObject unmatched(JsonObject answer, String term) {
        return ((JsonArray) answer.get("unmatchedTerms")).stream().map(JsonObject.class::cast)
                .filter(o -> term.equals(o.getString("term"))).findFirst().orElseThrow();
    }

    @Test
    void severalOptionsOfAnEipAreCheckedInOneCall() throws Exception {
        // 17 calls on onException in one step, one option each (connect-file-processing step 3)
        JsonObject answer = catalogDoc(Map.of("name", "onException", "kind", "eip",
                "optionsFilter", "logStackTrace,logExhausted,maximumRedeliveries,handled"));
        assertThat(names(answer, "options")).contains("handled");
        assertThat(names(answer, "nestedOptions")).contains("logStackTrace", "logExhausted", "maximumRedeliveries");
        assertThat(((JsonArray) answer.get("nestedOptions")).stream().map(JsonObject.class::cast)
                .filter(o -> "maximumRedeliveries".equals(o.getString("name")))
                .map(o -> o.getString("under")).toList()).containsExactly("redeliveryPolicy");
        assertThat(answer.get("unmatchedTerms")).isNull();
        assertThat(answer.get("closestOptions")).isNull();
    }

    @Test
    void severalOptionsOfAComponentAreCheckedInOneCall() throws Exception {
        // 13 calls on sql in one step (connect-service-sql step 1)
        JsonObject answer = catalogDoc(Map.of("name", "sql",
                "optionsFilter", "batch, onConsume ,onConsumeFailed,useMessageBodyForSql"));
        assertThat(names(answer, "options"))
                .contains("batch", "onConsume", "onConsumeFailed", "useMessageBodyForSql");
        assertThat(answer.get("unmatchedTerms")).isNull();
        assertThat(answer.getString("sectionsHint")).doesNotContain("contain '");
    }

    @Test
    void aTermThatMatchesNothingIsSaidWithTheClosestOptions() throws Exception {
        JsonObject answer = catalogDoc(Map.of("name", "sql", "optionsFilter", "onConsume,datasorce"));
        assertThat(names(answer, "options")).contains("onConsume");
        JsonObject term = unmatched(answer, "datasorce");
        assertThat(names(term, "closestOptions")).contains("dataSource");
        assertThat(term.getString("note")).contains("no option matches 'datasorce'");
        // the answer itself does not say that nothing matched
        assertThat(answer.get("note")).isNull();
        assertThat(answer.get("closestOptions")).isNull();
        assertThat(answer.getString("unmatchedHint")).contains("match no option");

        // nothing matches: each term is said
        JsonObject none = catalogDoc(Map.of("name", "onException", "kind", "eip", "optionsFilter", "foox,barx"));
        assertThat(((JsonArray) none.get("unmatchedTerms"))).hasSize(2);
        assertThat(none.get("nestedOptions")).isNull();
    }

    @Test
    void aTermThatIsAMessageHeaderSaysSo() throws Exception {
        JsonObject answer = catalogDoc(Map.of("name", "aws2-s3", "optionsFilter", "bucketNameOrArn,CamelAwsS3Key"));
        assertThat(names(answer, "options")).contains("bucketNameOrArn");
        JsonObject term = unmatched(answer, "camelawss3key");
        assertThat(names(term, "headers")).contains("CamelAwsS3Key");
        assertThat(term.getString("note")).contains("message headers do");
        assertThat(answer.get("matchedHeaders")).isNull();
    }

    @Test
    void aTermThatIsAnOptionElsewhereSaysWhere() throws Exception {
        // apiContextPath is an option of restConfiguration, not of the rest component (CAMEL-26572)
        JsonObject answer = catalogDoc(Map.of("name", "rest", "kind", "component",
                "optionsFilter", "apiContextPath,host"));
        assertThat(unmatched(answer, "apicontextpath").getString("elsewhere")).contains("restConfiguration (eip)");
        assertThat(answer.get("elsewhere")).isNull();
    }

    @Test
    void severalFunctionsOfSimpleAreCheckedInOneCall() throws Exception {
        JsonObject answer = catalogDoc(Map.of("name", "simple", "kind", "language", "optionsFilter", "random,uuid"));
        assertThat(names(answer, "functions")).contains("random(min,max)", "uuid(type)");
        assertThat(answer.getInteger("matchedFunctions")).isEqualTo(names(answer, "functions").size());
        // a function name comes first for its term, and is listed once
        assertThat(names(answer, "functions").stream().filter("random(min,max)"::equals)).hasSize(1);
    }

    @Test
    void aListWrittenAsTextIsSeveralTerms() throws Exception {
        JsonObject answer = catalogDoc(Map.of("name", "onException", "kind", "eip",
                "optionsFilter", "[\"logStackTrace\", \"logExhausted\"]"));
        assertThat(names(answer, "nestedOptions")).contains("logStackTrace", "logExhausted");
        assertThat(answer.get("unmatchedTerms")).isNull();
    }

    @Test
    void oneTermAnswersAsBefore() throws Exception {
        // a trailing comma leaves one term
        JsonObject one = catalogDoc(Map.of("name", "sql", "optionsFilter", "datasorce"));
        JsonObject trailing = catalogDoc(Map.of("name", "sql", "optionsFilter", "datasorce,"));
        assertThat(trailing.toJson()).isEqualTo(one.toJson());
        assertThat(one.getString("note")).contains("no option matches 'datasorce'");
        assertThat(names(one, "closestOptions")).contains("dataSource");
        assertThat(one.get("unmatchedTerms")).isNull();

        JsonObject header = catalogDoc(Map.of("name", "aws2-s3", "optionsFilter", "CamelAwsS3Key"));
        assertThat(header.getInteger("matchedHeaders")).isPositive();
        assertThat(header.get("unmatchedTerms")).isNull();
    }

    @Test
    void theElementsOfAnEipAreSearchedAlsoWhenItsOwnOptionsMatch() throws Exception {
        // redelivery matches redeliveryPolicy of onException itself, and the options a model wants are in that element
        JsonObject answer = catalogDoc(Map.of("name", "onException", "kind", "eip", "optionsFilter", "redelivery"));
        assertThat(names(answer, "options")).contains("redeliveryPolicy");
        assertThat(names(answer, "nestedOptions")).contains("redeliveryDelay");
        assertThat(answer.getString("nestedHint")).contains("redeliveryPolicy: {");
    }

    @Test
    void aCutAnswerNamesTheEntriesItLeavesOut() throws Exception {
        // a matches nearly every option of kafka, more than the answer has room for
        JsonObject answer = catalogDoc(Map.of("name", "kafka", "optionsFilter", "a,e"));
        assertThat(answer.toJson().length()).isLessThanOrEqualTo(CatalogDocs.MAX_ANSWER_LENGTH);
        assertThat(answer.getBoolean("truncated")).isTrue();
        assertThat(answer.getString("truncatedNote")).containsPattern("entries of options are left out \\(\\w+, ");
    }

    @Test
    void theTermsAreTrimmedLowerCasedAndUnique() {
        assertThat(CatalogDocs.filterTerms(" LogStackTrace , logstacktrace,,'logExhausted' "))
                .isEqualTo("logstacktrace,logexhausted");
        assertThat(CatalogDocs.filterTerms("logStackTrace")).isEqualTo("logStackTrace");
        assertThat(CatalogDocs.filterTerms(" , ")).isNull();
        assertThat(CatalogDocs.filterTerms(null)).isNull();
    }

    @Test
    void theNumberOfTermsIsBounded() throws Exception {
        String filter = IntStream.rangeClosed(1, CatalogDocs.MAX_FILTER_TERMS + 5).mapToObj(i -> "term" + i)
                .collect(Collectors.joining(","));
        assertThat(CatalogDocs.filterTerms(filter).split(",")).hasSize(CatalogDocs.MAX_FILTER_TERMS);
        JsonObject answer = catalogDoc(Map.of("name", "sql", "optionsFilter", filter));
        assertThat(answer.getString("filterNote")).contains("only the first " + CatalogDocs.MAX_FILTER_TERMS);
        assertThat(answer.toJson().length()).isLessThanOrEqualTo(CatalogDocs.MAX_ANSWER_LENGTH);
    }

    @Test
    void theToolDescriptionSaysSeveralCanBeChecked() {
        // the shared description, which the MCP server uses too
        String description = ToolRegistry.findTool("camel_catalog_doc").params().stream()
                .filter(p -> "optionsFilter".equals(p.name())).findFirst().orElseThrow().description();
        assertThat(description).isEqualTo(CatalogDocs.OPTIONS_FILTER_DESC).contains("comma-separated");
    }
}
