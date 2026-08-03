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
package org.apache.camel.component.platform.http.main;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiUiSupportTest {

    @Test
    void normalizeSpecPathDefaultsToOpenApiJson() {
        assertThat(OpenApiUiSupport.normalizeSpecPath(null)).isEqualTo(OpenApiUiSupport.DEFAULT_SPEC_PATH);
        assertThat(OpenApiUiSupport.normalizeSpecPath("")).isEqualTo(OpenApiUiSupport.DEFAULT_SPEC_PATH);
    }

    @Test
    void normalizeSpecPathAddsLeadingSlashForRelativePaths() {
        assertThat(OpenApiUiSupport.normalizeSpecPath("api-docs.json")).isEqualTo("/api-docs.json");
    }

    @Test
    void normalizeSpecPathPreservesAbsoluteHttpUrls() {
        String url = "http://localhost:9090/q/openapi.json";
        assertThat(OpenApiUiSupport.normalizeSpecPath(url)).isEqualTo(url);
    }

    @Test
    void escapeForJavaScriptStringEscapesQuotesAndScriptDelimiters() {
        assertThat(OpenApiUiSupport.escapeForJavaScriptString("/q/openapi.json")).isEqualTo("/q/openapi.json");
        assertThat(OpenApiUiSupport.escapeForJavaScriptString("http://host/path\"</script>"))
                .isEqualTo("http://host/path\\\"\\u003c/script\\u003e");
    }

    @Test
    void resolveSwaggerUiWebjarVersionMatchesClasspathArtifact() {
        assertThat(OpenApiUiSupport.resolveSwaggerUiWebjarVersion()).matches("\\d+\\.\\d+\\.\\d+");
    }
}
