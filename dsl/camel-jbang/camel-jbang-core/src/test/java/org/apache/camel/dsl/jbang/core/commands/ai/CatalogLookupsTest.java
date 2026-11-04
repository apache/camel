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

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-26572: in the local-model benchmark a model sent one catalog lookup again and again, until it had used all its
 * calls, when the lookup could not find what it looked for. The optionsFilter of a component now searches its message
 * headers when no option matches, camel_catalog_find finds a header by its name or constant, the optionsFilter of
 * simple searches the file: functions, and a filter that matches nothing says where an option or header of that name
 * is. An answer that matched before stays as it was.
 */
class CatalogLookupsTest {

    private static JsonObject catalogDoc(Map<String, String> args) throws Exception {
        return (JsonObject) Jsoner.deserialize(
                String.valueOf(ToolRegistry.execute("camel_catalog_doc", new ToolContext(), new HashMap<>(args))));
    }

    private static List<String> strings(JsonObject answer, String key) {
        return ((JsonArray) answer.get(key)).stream().map(String::valueOf).toList();
    }

    private static List<String> names(JsonObject answer, String key) {
        return ((JsonArray) answer.get(key)).stream().map(o -> ((JsonObject) o).getString("name")).toList();
    }

    private static JsonObject first(JsonObject answer, String key) {
        return (JsonObject) ((JsonArray) answer.get(key)).get(0);
    }

    @Test
    void aFilterThatMatchesNoOptionFindsTheMessageHeaders() throws Exception {
        // a model sent optionsFilter=CamelAwsS3Key on aws2-s3 17 times in one step, and no option matches it
        JsonObject answer = catalogDoc(Map.of("name", "aws2-s3", "optionsFilter", "CamelAwsS3Key"));
        assertThat(answer.getInteger("matchedOptions")).isZero();
        assertThat(answer.getInteger("matchedHeaders")).isEqualTo(2);
        // the header the filter names comes first, with what the catalog says of it
        JsonObject key = first(answer, "headers");
        assertThat(key.getString("name")).isEqualTo("CamelAwsS3Key");
        assertThat(key.getString("constantName")).isEqualTo("org.apache.camel.component.aws2.s3.AWS2S3Constants#KEY");
        assertThat(key.getString("javaType")).isEqualTo("String");
        assertThat(key.getString("group")).isEqualTo("common");
        assertThat(key.getString("description")).contains("key");
        assertThat(answer.getString("note")).contains("2 message headers do", "${header.CamelAwsS3Key}");
        assertThat(answer.get("closestOptions")).isNull();

        // with includeHeaders the headers the filter matches, not all of them (54, cut at the end of the answer)
        JsonObject included
                = catalogDoc(Map.of("name", "aws2-s3", "optionsFilter", "CamelAwsS3Key", "includeHeaders", "true"));
        assertThat(names(included, "headers")).containsExactly("CamelAwsS3Key", "CamelAwsS3KeysToDelete");

        // a filter that matches many headers lists their names, so the answer stays short
        JsonObject many = catalogDoc(Map.of("name", "aws2-s3", "optionsFilter", "CamelAwsS3"));
        assertThat(many.getInteger("matchedHeaders")).isGreaterThan(CatalogDocs.MATCHED_HEADERS_LIMIT);
        assertThat(many.get("headers")).isNull();
        assertThat(strings(many, "headerNames")).contains("CamelAwsS3Key", "CamelAwsS3ContentType");
        assertThat(many.getString("headersHint")).contains("optionsFilter=<header name>");

        // a filter that matches an option is answered as before, without headers
        JsonObject options = catalogDoc(Map.of("name", "kafka", "optionsFilter", "key"));
        assertThat(options.getInteger("matchedOptions")).isPositive();
        assertThat(options.get("matchedHeaders")).isNull();
        assertThat(options.get("headers")).isNull();
    }

    @Test
    void findFindsAHeaderByItsNameOrConstant() {
        CamelCatalog catalog = new DefaultCamelCatalog();
        // a model sent such finds in one step, and each was answered "Nothing in the catalog matches"
        for (String term : List.of("CamelAwsS3Key", "camelawss3key", "AWS2S3Constants KEY", "AWS2S3Constants.KEY")) {
            JsonObject result = CatalogDocs.find(catalog, term, null, 10);
            assertThat(result.getInteger("count")).as(term).isEqualTo(1);
            JsonObject header = first(result, "matches");
            assertThat(header.getString("kind")).isEqualTo("header");
            assertThat(header.getString("name")).isEqualTo("CamelAwsS3Key");
            assertThat(strings(header, "components")).containsExactly("aws2-s3");
            assertThat(header.getString("constantName"))
                    .isEqualTo("org.apache.camel.component.aws2.s3.AWS2S3Constants#KEY");
            assertThat(result.getString("message")).contains("${header.CamelAwsS3Key}");
        }

        // a header of many components is one match with all of them: what it says is from the components that
        // share the most common constant (ftp, sftp), not from the first in the catalog (alibaba-oss)
        JsonObject fileName = first(CatalogDocs.find(catalog, "CamelFileName", null, 10), "matches");
        assertThat(strings(fileName, "components")).contains("file", "ftp", "sftp", "alibaba-oss");
        assertThat(fileName.getString("description")).contains("file");

        // headers are looked for only when nothing else matches: pulsar has a header named key
        JsonArray key = (JsonArray) CatalogDocs.find(catalog, "key", "component", 10).get("matches");
        assertThat(key).isNotEmpty().allSatisfy(m -> assertThat(((JsonObject) m).getString("kind")).isEqualTo("component"));
        // and among the components only
        assertThat(CatalogDocs.find(catalog, "CamelAwsS3Key", "eip", 10).getInteger("count")).isZero();
    }

    @Test
    void theSimpleFilterFindsTheFileFunctions() throws Exception {
        // a model sent optionsFilter=file:name.noext on simple 14 times in one step: the file: functions are in the
        // catalog entry of the file language
        for (String filter : List.of("file:name.noext", "noext", "name.noext")) {
            JsonObject answer = catalogDoc(Map.of("name", "simple", "kind", "language", "optionsFilter", filter));
            assertThat(names(answer, "functions")).as(filter).contains("file:name.noext");
            assertThat(answer.getString("functionsNote")).as(filter).contains("${file:name.noext}");
        }
        JsonObject exact = catalogDoc(Map.of("name", "simple", "kind", "language", "optionsFilter", "file:name.noext"));
        assertThat(names(exact, "functions")).containsExactly("file:name.noext", "file:name.noext.single");
        assertThat(exact.getInteger("matchedFunctions")).isEqualTo(2);

        // a filter that matches a function of simple is answered as before
        JsonObject random = catalogDoc(Map.of("name", "simple", "kind", "language", "optionsFilter", "random"));
        assertThat(random.get("functionsNote")).isNull();
        assertThat(names(random, "functions")).noneMatch(name -> name.startsWith("file:"));
    }

    @Test
    void aFilterThatMatchesNothingSaysWhereAnOptionOfThatNameIs() throws Exception {
        // a model sent optionsFilter=apiContextPath on the rest component 23 times in one step
        JsonObject rest = catalogDoc(Map.of("name", "rest", "kind", "component", "optionsFilter", "apiContextPath"));
        assertThat(rest.getString("elsewhere")).startsWith("apiContextPath is an option of ")
                .contains("restConfiguration (eip)", "optionsFilter=apiContextPath shows it");

        // one place comes with the call that shows it
        JsonObject sqs = catalogDoc(Map.of("name", "aws2-sqs", "optionsFilter", "forcePathStyle"));
        assertThat(sqs.getString("elsewhere")).isEqualTo("forcePathStyle is an option of aws2-s3 (component):"
                                                         + " camel_catalog_doc name=aws2-s3 kind=component"
                                                         + " optionsFilter=forcePathStyle shows it");
        // a message header of another component
        JsonObject header = catalogDoc(Map.of("name", "aws2-sqs", "optionsFilter", "CamelAwsS3Key"));
        assertThat(header.getString("elsewhere")).startsWith("CamelAwsS3Key is a message header of aws2-s3:");

        // an EIP and a language say it too
        JsonObject eip = catalogDoc(Map.of("name", "restConfiguration", "kind", "eip", "optionsFilter", "componentName"));
        assertThat(eip.getString("elsewhere")).startsWith("componentName is an option of ").contains("rest-openapi");
        JsonObject simple = catalogDoc(Map.of("name", "simple", "kind", "language", "optionsFilter", "forcePathStyle"));
        assertThat(simple.getString("elsewhere")).startsWith("forcePathStyle is an option of aws2-s3 (component)");

        // a name of many places: a few of them, and how many more
        JsonObject timeout = catalogDoc(Map.of("name", "constant", "kind", "language", "optionsFilter", "timeout"));
        assertThat(timeout.getString("elsewhere"))
                .matches("timeout is an option of [^,]+, [^,]+, [^,]+ and \\d+ more: camel_catalog_doc"
                         + " name=<one of them> kind=<its kind> optionsFilter=timeout shows it");

        // nothing when the filter matches, also in the elements of an EIP, or when no option has that name
        assertThat(catalogDoc(Map.of("name", "rest", "kind", "component", "optionsFilter", "host")).get("elsewhere"))
                .isNull();
        assertThat(catalogDoc(Map.of("name", "onException", "kind", "eip", "optionsFilter", "logStackTrace"))
                .get("elsewhere")).isNull();
        assertThat(catalogDoc(Map.of("name", "restConfiguration", "kind", "eip", "optionsFilter", "apiDocsPath"))
                .get("elsewhere")).isNull();
    }

    @Test
    void aCutAnswerNamesTheHeadersItLeavesOut() throws Exception {
        // CAMEL-26591: aws2-s3 has 54 headers, the answer had room for 3 of them, and CamelAwsS3Key was not among them;
        // a model asked for the same answer 4 times in one step
        JsonObject answer = catalogDoc(Map.of("name", "aws2-s3", "kind", "component", "includeHeaders", "true"));
        assertThat(answer.toJson().length()).isLessThanOrEqualTo(CatalogDocs.MAX_ANSWER_LENGTH);
        List<String> listed = names(answer, "headers");
        List<String> more = strings(answer, "moreHeaders");
        assertThat(more).contains("CamelAwsS3Key").doesNotContainAnyElementsOf(listed);
        // every header is in the answer, in full or by name
        int headers = new DefaultCamelCatalog().componentModel("aws2-s3").getEndpointHeaders().size();
        assertThat(listed.size() + more.size()).isEqualTo(headers);
        assertThat(answer.getString("truncatedNote")).isEqualTo("the answer is cut at 18000 characters: the last "
                                                                + more.size() + " entries of headers are left out, by"
                                                                + " name in moreHeaders; optionsFilter=<header name>"
                                                                + " returns one in full");
        // and the filter the note gives returns the header
        JsonObject key = catalogDoc(Map.of("name", "aws2-s3", "kind", "component", "optionsFilter", "CamelAwsS3Key"));
        assertThat(first(key, "headers").getString("name")).isEqualTo("CamelAwsS3Key");
    }

    @Test
    void findFindsWhatHasAllTheWordsOfATerm() {
        CamelCatalog catalog = new DefaultCamelCatalog();
        // CAMEL-26591: a model sent aws2-s3 header KEY twice, and was told that nothing matches
        JsonObject key = CatalogDocs.find(catalog, "aws2-s3 header KEY", null, 10);
        JsonObject header = first(key, "matches");
        assertThat(header.getString("kind")).isEqualTo("header");
        assertThat(header.getString("name")).isEqualTo("CamelAwsS3Key");
        assertThat(header.getString("constantName")).isEqualTo("org.apache.camel.component.aws2.s3.AWS2S3Constants#KEY");
        assertThat(strings(header, "components")).containsExactly("aws2-s3");
        // the header whose constant is KEY first, then those with the word key in their name
        assertThat(names(key, "matches")).containsExactly("CamelAwsS3Key", "CamelAwsS3ProducedKey",
                "CamelAwsS3DestinationKey");
        assertThat(key.getString("message")).contains("${header.CamelAwsS3Key}");

        // all the headers of a component: as many in full as the limit, and the others by name
        JsonObject all = CatalogDocs.find(catalog, "aws2-s3 header", null, 10);
        assertThat(all.getInteger("count")).isEqualTo(10);
        assertThat(all.getInteger("matchedHeaders")).isGreaterThan(10);
        assertThat(strings(all, "headerNames")).contains("CamelAwsS3Key").hasSizeLessThanOrEqualTo(
                CatalogDocs.MAX_HEADER_NAMES);
        assertThat(all.getString("headersHint")).contains("camel_catalog_find term=<header name>");

        // an entry of the catalog that has every word: the title of jackson is JSON Jackson
        JsonObject jackson = CatalogDocs.find(catalog, "jackson json", "dataformat", 10);
        assertThat(names(jackson, "matches")).containsExactly("jackson");
        assertThat(jackson.getString("message")).startsWith("No name matches 'jackson json' as a whole");
        // a word that names the kind: the split EIP, the log Kamelets
        assertThat(names(CatalogDocs.find(catalog, "split eip", null, 10), "matches")).containsExactly("split");
        assertThat(names(CatalogDocs.find(catalog, "kamelet log", null, 10), "matches")).contains("log-sink");
        // jms for artemis, with the note that a broker is reached with the jms component
        JsonObject jms = first(CatalogDocs.find(catalog, "jms artemis", null, 10), "matches");
        assertThat(jms.getString("name")).isEqualTo("jms");
        assertThat(jms.getString("note")).contains("'artemis' is done with");

        // a term that matches as a whole is answered as before, and one whose words nothing has all of finds nothing
        assertThat(CatalogDocs.find(catalog, "json jackson", "dataformat", 10).getString("message"))
                .isEqualTo("Best match first; camel_catalog_doc gives the options of one");
        assertThat(CatalogDocs.find(catalog, "citrus log verify", "eip", 10).getInteger("count")).isZero();
        // a header is found by a word that names it or its component, not by a word of many header names alone
        // (CamelAwsAthenaRetry), and not when a word names another kind (pulsar has a header named properties)
        assertThat(CatalogDocs.find(catalog, "retry header", null, 10).getInteger("count")).isZero();
        assertThat(CatalogDocs.find(catalog, "properties component", null, 10).getInteger("count")).isZero();
    }

    @Test
    void aFilterThatMatchesNoOptionOfAnEipGetsTheClosestOptionsOfTheEip() throws Exception {
        // CAMEL-26591: restConfiguration's own component is the option meant, and the elsewhere line points to the
        // componentName of rest-openapi and rest-postman
        JsonObject answer
                = catalogDoc(Map.of("name", "restConfiguration", "kind", "eip", "optionsFilter", "componentName"));
        assertThat(names(answer, "closestOptions")).startsWith("component", "apiComponent");
        assertThat(first(answer, "closestOptions").getString("group")).isEqualTo("common");
        assertThat(first(answer, "closestOptions").get("scope")).isNull();
        // before the elsewhere line
        List<String> keys = List.copyOf(answer.keySet());
        assertThat(keys.indexOf("closestOptions")).isLessThan(keys.indexOf("elsewhere"));
        assertThat(answer.getString("elsewhere")).contains("rest-openapi");

        assertThat(names(catalogDoc(Map.of("name", "rest", "kind", "eip", "optionsFilter", "openApiComponentName")),
                "closestOptions")).containsExactly("openApi");
        // none when the filter matches an option, also one in an element of the EIP
        assertThat(catalogDoc(Map.of("name", "restConfiguration", "kind", "eip", "optionsFilter", "component"))
                .get("closestOptions")).isNull();
        assertThat(catalogDoc(Map.of("name", "onException", "kind", "eip", "optionsFilter", "logStackTrace"))
                .get("closestOptions")).isNull();
    }
}
