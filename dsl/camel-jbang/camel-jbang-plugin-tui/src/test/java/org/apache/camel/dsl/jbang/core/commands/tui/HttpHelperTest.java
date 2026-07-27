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
package org.apache.camel.dsl.jbang.core.commands.tui;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HttpHelperTest {

    @Test
    void extractPlatformHttpPathSimple() {
        assertThat(HttpHelper.extractPlatformHttpPath("platform-http:/hello"))
                .isEqualTo("/hello");
    }

    @Test
    void extractPlatformHttpPathTripleSlash() {
        assertThat(HttpHelper.extractPlatformHttpPath("platform-http:///api/users"))
                .isEqualTo("/api/users");
    }

    @Test
    void extractPlatformHttpPathStripsQueryParams() {
        assertThat(HttpHelper.extractPlatformHttpPath("platform-http:/greet?httpMethodRestrict=GET"))
                .isEqualTo("/greet");
    }

    @Test
    void extractPlatformHttpPathNoLeadingSlash() {
        assertThat(HttpHelper.extractPlatformHttpPath("platform-http:hello"))
                .isEqualTo("/hello");
    }

    @Test
    void extractPlatformHttpPathDoubleSlash() {
        assertThat(HttpHelper.extractPlatformHttpPath("platform-http://orders"))
                .isEqualTo("/orders");
    }

    @Test
    void extractHttpMethodFromRestrict() {
        assertThat(HttpHelper.extractHttpMethod("platform-http:/api?httpMethodRestrict=PUT", "body"))
                .isEqualTo("PUT");
    }

    @Test
    void extractHttpMethodFirstFromCommaSeparated() {
        assertThat(HttpHelper.extractHttpMethod("platform-http:/api?httpMethodRestrict=GET,POST", null))
                .isEqualTo("GET");
    }

    @Test
    void extractHttpMethodPostWhenBodyPresent() {
        assertThat(HttpHelper.extractHttpMethod("platform-http:/api", "some body"))
                .isEqualTo("POST");
    }

    @Test
    void extractHttpMethodGetWhenNoBody() {
        assertThat(HttpHelper.extractHttpMethod("platform-http:/api", null))
                .isEqualTo("GET");
    }

    @Test
    void extractHttpMethodGetWhenEmptyBody() {
        assertThat(HttpHelper.extractHttpMethod("platform-http:/api", ""))
                .isEqualTo("GET");
    }

    @Test
    void extractHttpMethodWithOtherQueryParams() {
        assertThat(HttpHelper.extractHttpMethod("platform-http:/api?consumes=application/json&httpMethodRestrict=DELETE", null))
                .isEqualTo("DELETE");
    }
}
