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
package org.apache.camel.component.rest.postman;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestPostmanHelperTest {

    @ParameterizedTest
    @CsvSource({
            "'Get User By Id', getUserById",
            "'get user by id', getUserById",
            "'GET /v1/users', getV1Users",
            "'Add-Pet', addPet",
            "'  Trim  Me  ', trimMe",
            "'Créer Utilisateur', creerUtilisateur",
            "'user', user",
            "'2FA Verify', r2faVerify",
            "'!!!', fallback",
            "'', fallback"
    })
    void shouldSlugifyNames(String name, String expected) {
        assertThat(RestPostmanHelper.slugify(name, "fallback")).isEqualTo(expected);
    }

    @Test
    void shouldSlugifyNullToFallback() {
        assertThat(RestPostmanHelper.slugify(null, "fallback")).isEqualTo("fallback");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "3f2504e0-4f89-11d3-9a0c-0305e82c3301",
            "12345678-3f2504e0-4f89-11d3-9a0c-0305e82c3301"
    })
    void shouldRecogniseUuids(String candidate) {
        assertThat(RestPostmanHelper.isUuid(candidate)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "getUserById",
            "3f2504e0-4f89-11d3-9a0c-0305e82c3301.json",
            "users/getUserById",
            "not-a-uuid"
    })
    void shouldRejectNonUuids(String candidate) {
        assertThat(RestPostmanHelper.isUuid(candidate)).isFalse();
    }

    @Test
    void shouldRejectNullUuid() {
        assertThat(RestPostmanHelper.isUuid(null)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = { "http://api.example.com", "https://api.example.com", "https://api.example.com:8443" })
    void shouldAcceptAbsoluteHosts(String host) {
        assertThat(RestPostmanHelper.isHostParam(host)).isEqualTo(host);
    }

    @ParameterizedTest
    @ValueSource(strings = { "api.example.com", "ftp://api.example.com", "https://api.example.com/v3" })
    void shouldRejectMalformedHosts(String host) {
        assertThatThrownBy(() -> RestPostmanHelper.isHostParam(host))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("host must be an absolute URI");
    }

    @Test
    void shouldUpperCaseValidMethods() {
        assertThat(RestPostmanHelper.validateMethod("get", "'x'")).isEqualTo("GET");
    }

    @ParameterizedTest
    @ValueSource(strings = { "GET:/evil", "GET POST", "GET?x=1", "" })
    void shouldRejectMethodsThatCouldCorruptTheDelegateUri(String method) {
        assertThatThrownBy(() -> RestPostmanHelper.validateMethod(method, "'x'"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid HTTP method");
    }

    @ParameterizedTest
    @ValueSource(strings = { "a?b", "a#b", "a&b", "a:b" })
    void shouldRejectPathSegmentsThatCouldCorruptTheDelegateUri(String segment) {
        assertThatThrownBy(() -> RestPostmanHelper.validatePathSegment(segment, "'x'"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be expressed as a REST endpoint");
    }

    @Test
    void shouldAcceptOrdinaryPathSegments() {
        assertThat(RestPostmanHelper.validatePathSegment("users", "'x'")).isEqualTo("users");
    }

    @Test
    void shouldBuildQueryParameterExpressions() {
        assertThat(RestPostmanHelper.queryParameterExpression("status", false)).isEqualTo("status={status?}");
        assertThat(RestPostmanHelper.queryParameterExpression("status", true)).isEqualTo("status={status}");
    }
}
