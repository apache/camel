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
package org.apache.camel.component.rest.postman.model;

import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PostmanUrlTest {

    private static PostmanUrl parse(String json) throws Exception {
        return PostmanUrl.parse(Jsoner.deserialize(json));
    }

    @Test
    void shouldParseStructuredUrl() throws Exception {
        PostmanUrl url = parse("""
                {"protocol":"https","host":["api","example","com"],"port":"8443",
                 "path":["v3","pet",":petId"],
                 "query":[{"key":"verbose","value":"true"}],
                 "variable":[{"key":"petId","value":"42"}]}""");

        assertThat(url.getProtocol()).isEqualTo("https");
        assertThat(url.getHost()).isEqualTo("api.example.com");
        assertThat(url.getPort()).isEqualTo("8443");
        assertThat(url.getPathSegments()).containsExactly("v3", "pet", ":petId");
        assertThat(url.getQueryParams()).singleElement()
                .satisfies(param -> assertThat(param.key()).isEqualTo("verbose"));
        assertThat(url.getPathVariables()).singleElement()
                .satisfies(variable -> assertThat(variable.value()).isEqualTo("42"));
    }

    @Test
    void shouldParseBareStringUrl() {
        PostmanUrl url = PostmanUrl.parse("https://api.example.com/v3/pet/:petId?verbose=true#frag");

        assertThat(url.getProtocol()).isEqualTo("https");
        assertThat(url.getHost()).isEqualTo("api.example.com");
        assertThat(url.getPathSegments()).containsExactly("v3", "pet", ":petId");
        assertThat(url.getQueryParams()).singleElement()
                .satisfies(param -> {
                    assertThat(param.key()).isEqualTo("verbose");
                    assertThat(param.value()).isEqualTo("true");
                });
    }

    @Test
    void shouldRecoverStructureFromRawWhenOnlyRawIsGiven() throws Exception {
        PostmanUrl url = parse("""
                {"raw":"https://api.example.com:9090/v3/pet?status=available"}""");

        assertThat(url.getHost()).isEqualTo("api.example.com");
        assertThat(url.getPort()).isEqualTo("9090");
        assertThat(url.getPathSegments()).containsExactly("v3", "pet");
        assertThat(url.getQueryParams()).singleElement()
                .satisfies(param -> assertThat(param.key()).isEqualTo("status"));
    }

    @Test
    void shouldPreferStructuredFieldsOverRaw() throws Exception {
        PostmanUrl url = parse("""
                {"raw":"https://ignored.example.com/nope","host":["api","example","com"],"path":["pet"]}""");

        assertThat(url.getHost()).isEqualTo("api.example.com");
        assertThat(url.getPathSegments()).containsExactly("pet");
    }

    /**
     * The single most common Postman idiom: the host is a placeholder that expands to a complete URL carrying a path
     * prefix. Parsing that expansion is what recovers the base path.
     */
    @Test
    void shouldParseAnExpandedBaseUrlAsAFullUrl() {
        PostmanUrl expanded = PostmanUrl.parse("https://api.example.com/v3");

        assertThat(expanded.getProtocol()).isEqualTo("https");
        assertThat(expanded.getHost()).isEqualTo("api.example.com");
        assertThat(expanded.getPathSegments()).containsExactly("v3");
    }

    @Test
    void shouldLeaveUnexpandedPlaceholderHostAlone() throws Exception {
        PostmanUrl url = parse("""
                {"host":["{{baseUrl}}"],"path":["pet"]}""");

        assertThat(url.getHost()).isEqualTo("{{baseUrl}}");
        assertThat(url.getProtocol()).isNull();
    }

    @Test
    void shouldParseHostRelativeUrl() {
        PostmanUrl url = PostmanUrl.parse("/v3/pet");

        assertThat(url.getHost()).isNull();
        assertThat(url.getPathSegments()).containsExactly("v3", "pet");
    }

    @Test
    void shouldParsePathGivenAsAString() throws Exception {
        PostmanUrl url = parse("""
                {"host":["api","example","com"],"path":"v3/pet"}""");

        assertThat(url.getPathSegments()).containsExactly("v3", "pet");
    }

    @Test
    void shouldParsePathSegmentsGivenAsObjects() throws Exception {
        PostmanUrl url = parse("""
                {"host":["api","example","com"],"path":[{"value":"v3"},{"value":"pet"}]}""");

        assertThat(url.getPathSegments()).containsExactly("v3", "pet");
    }

    @Test
    void shouldNotMistakeAColonInThePathForAPort() {
        PostmanUrl url = PostmanUrl.parse("{{baseUrl}}/pet/:petId");

        assertThat(url.getHost()).isEqualTo("{{baseUrl}}");
        assertThat(url.getPort()).isNull();
        assertThat(url.getPathSegments()).containsExactly("pet", ":petId");
    }

    @Test
    void shouldReturnEmptyUrlForMissingNode() {
        PostmanUrl url = PostmanUrl.parse(null);

        assertThat(url.getHost()).isNull();
        assertThat(url.getPathSegments()).isEmpty();
        assertThat(url.getQueryParams()).isEmpty();
    }

    @Test
    void shouldSkipQueryEntriesWithoutAKey() throws Exception {
        JsonObject node = (JsonObject) Jsoner.deserialize("""
                {"host":["api","example","com"],"query":[{"value":"orphan"},{"key":"ok","value":"1"}]}""");

        assertThat(PostmanUrl.parse(node).getQueryParams()).singleElement()
                .satisfies(param -> assertThat(param.key()).isEqualTo("ok"));
    }
}
