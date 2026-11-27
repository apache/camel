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
package org.apache.camel.component.rest.postman.support;

import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PostmanRedactorTest {

    private static JsonObject redact(String json) throws Exception {
        return PostmanRedactor.redact((JsonObject) Jsoner.deserialize(json));
    }

    @Test
    void shouldRemoveTheCollectionAuthBlock() throws Exception {
        JsonObject redacted = redact("""
                {"info":{"name":"t"},
                 "auth":{"type":"bearer","bearer":[{"key":"token","value":"s3cr3t"}]},
                 "item":[]}""");

        assertThat(redacted).doesNotContainKey("auth");
        assertThat(redacted.toJson()).doesNotContain("s3cr3t");
    }

    @Test
    void shouldRemoveNestedAuthBlocks() throws Exception {
        JsonObject redacted = redact("""
                {"info":{"name":"t"},
                 "item":[{"name":"F","auth":{"type":"basic","basic":[{"key":"password","value":"hunter2"}]},
                          "item":[{"name":"R","request":{"method":"GET","url":"https://h/x",
                                   "auth":{"type":"bearer","bearer":[{"key":"token","value":"leaky"}]}}}]}]}""");

        assertThat(redacted.toJson()).doesNotContain("hunter2").doesNotContain("leaky").doesNotContain("\"auth\"");
    }

    @Test
    void shouldRedactSecretVariables() throws Exception {
        JsonObject redacted = redact("""
                {"info":{"name":"t"},
                 "variable":[{"key":"apiToken","value":"s3cr3t","type":"secret"},
                             {"key":"baseUrl","value":"https://api.example.com"}],
                 "item":[]}""");

        assertThat(redacted.toJson()).doesNotContain("s3cr3t");

        JsonArray variables = redacted.getJsonArray("variable");
        assertThat(variables.getJsonObject(0).getString("value")).isEqualTo("***");
        // non-secret variables are untouched, as they are needed to make sense of the document
        assertThat(variables.getJsonObject(1).getString("value")).isEqualTo("https://api.example.com");
    }

    @Test
    void shouldLeaveTheRestOfTheDocumentIntact() throws Exception {
        JsonObject redacted = redact("""
                {"info":{"name":"Petstore","schema":"v2.1"},
                 "item":[{"name":"Get Pet","request":{"method":"GET","url":"https://api.example.com/pet"}}]}""");

        assertThat(redacted.getJsonObject("info").getString("name")).isEqualTo("Petstore");

        JsonObject item = redacted.getJsonArray("item").getJsonObject(0);
        assertThat(item.getString("name")).isEqualTo("Get Pet");
        assertThat(item.getJsonObject("request").getString("url")).isEqualTo("https://api.example.com/pet");
    }

    @Test
    void shouldNotMutateTheOriginalDocument() throws Exception {
        JsonObject original = (JsonObject) Jsoner.deserialize("""
                {"info":{"name":"t"},"auth":{"type":"bearer","bearer":[{"key":"token","value":"s3cr3t"}]},
                 "item":[]}""");

        PostmanRedactor.redact(original);

        assertThat(original).containsKey("auth");
    }

    private static JsonObject request(JsonObject redacted) {
        return redacted.getJsonArray("item").getJsonObject(0).getJsonObject("request");
    }

    private static String header(JsonObject redacted, String name) {
        JsonArray headers = request(redacted).getJsonArray("header");
        for (int i = 0; i < headers.size(); i++) {
            JsonObject header = headers.getJsonObject(i);
            if (name.equals(header.getString("key"))) {
                return header.getString("value");
            }
        }
        throw new IllegalArgumentException("No header " + name);
    }

    private static JsonObject withHeader(String name, String value) throws Exception {
        return redact("""
                {"info":{"name":"t"},
                 "item":[{"name":"R","request":{"method":"GET","url":"https://h/x",
                          "header":[{"key":"%s","value":"%s"},{"key":"Accept","value":"application/json"}]}}]}"""
                .formatted(name, value));
    }

    @Test
    void shouldMaskAuthorizationHeader() throws Exception {
        JsonObject redacted = withHeader("Authorization", "Bearer s3cr3t");

        assertThat(redacted.toJson()).doesNotContain("s3cr3t");
        assertThat(header(redacted, "Authorization")).isEqualTo("***");
        assertThat(header(redacted, "Accept")).isEqualTo("application/json");
    }

    @Test
    void shouldMaskProxyAuthorizationHeader() throws Exception {
        JsonObject redacted = withHeader("Proxy-Authorization", "Basic dXNlcjpwYXNz");

        assertThat(header(redacted, "Proxy-Authorization")).isEqualTo("***");
    }

    @Test
    void shouldMaskApiKeyHeader() throws Exception {
        assertThat(header(withHeader("x-api-key", "k3y"), "x-api-key")).isEqualTo("***");
        assertThat(header(withHeader("api-key", "k3y"), "api-key")).isEqualTo("***");
    }

    @Test
    void shouldMaskCookieHeader() throws Exception {
        JsonObject redacted = withHeader("Cookie", "SESSION=abc123");

        assertThat(redacted.toJson()).doesNotContain("abc123");
        assertThat(header(redacted, "Cookie")).isEqualTo("***");
    }

    @Test
    void shouldMaskHeaderWhoseNameContainsToken() throws Exception {
        assertThat(header(withHeader("X-Token-Id", "t0k3n"), "X-Token-Id")).isEqualTo("***");
        assertThat(header(withHeader("X-Auth-Token", "t0k3n"), "X-Auth-Token")).isEqualTo("***");
    }

    @Test
    void shouldMaskHeadersOfExampleResponses() throws Exception {
        JsonObject redacted = redact("""
                {"info":{"name":"t"},
                 "item":[{"name":"R","request":{"method":"GET","url":"https://h/x"},
                          "response":[{"name":"ok","code":200,
                                       "originalRequest":{"method":"GET","url":"https://h/x",
                                                          "header":[{"key":"Authorization","value":"Bearer leaky"}]},
                                       "header":[{"key":"Set-Cookie","value":"SESSION=abc123"}],
                                       "cookie":[{"name":"SESSION","value":"abc123","domain":"h"}]}]}]}""");

        assertThat(redacted.toJson()).doesNotContain("leaky").doesNotContain("abc123");
    }

    @Test
    void shouldMaskHeadersGivenAsString() throws Exception {
        JsonObject redacted = redact("""
                {"info":{"name":"t"},
                 "item":[{"name":"R","request":{"method":"GET","url":"https://h/x",
                          "header":"Accept: application/json\nAuthorization: Bearer s3cr3t"}}]}""");

        assertThat(request(redacted).getString("header")).isEqualTo("Accept: application/json\nAuthorization: ***");
    }

    @Test
    void shouldLeavePlaceholderHeaderValue() throws Exception {
        assertThat(header(withHeader("Authorization", "Bearer {{token}}"), "Authorization")).isEqualTo("Bearer {{token}}");
        assertThat(header(withHeader("x-api-key", "{{apiKey}}"), "x-api-key")).isEqualTo("{{apiKey}}");
    }

    @Test
    void shouldMaskDefaultTypeVariablesNamedAsCredential() throws Exception {
        JsonObject redacted = redact("""
                {"info":{"name":"t"},
                 "variable":[{"key":"apiKey","value":"k3y"},
                             {"key":"password","value":"hunter2","type":"string"},
                             {"key":"access_token","value":"t0k3n"},
                             {"key":"petId","value":"42"}],
                 "item":[]}""");

        assertThat(redacted.toJson()).doesNotContain("k3y").doesNotContain("hunter2").doesNotContain("t0k3n");

        JsonArray variables = redacted.getJsonArray("variable");
        assertThat(variables.getJsonObject(0).getString("value")).isEqualTo("***");
        assertThat(variables.getJsonObject(1).getString("value")).isEqualTo("***");
        assertThat(variables.getJsonObject(2).getString("value")).isEqualTo("***");
        assertThat(variables.getJsonObject(3).getString("value")).isEqualTo("42");
    }

    @Test
    void shouldLeavePlaceholderVariableValue() throws Exception {
        JsonObject redacted = redact("""
                {"info":{"name":"t"},
                 "variable":[{"key":"apiKey","value":"{{vaultApiKey}}"}],
                 "item":[]}""");

        assertThat(redacted.getJsonArray("variable").getJsonObject(0).getString("value")).isEqualTo("{{vaultApiKey}}");
    }

    @Test
    void shouldMaskUserInfoPasswordInRawUrl() throws Exception {
        JsonObject redacted = redact("""
                {"info":{"name":"t"},
                 "item":[{"name":"R","request":{"method":"GET","url":"https://scott:tiger@api.example.com/pet"}}]}""");

        assertThat(request(redacted).getString("url")).isEqualTo("https://scott:***@api.example.com/pet");
    }

    @Test
    void shouldMaskUserInfoPasswordInStructuredUrl() throws Exception {
        JsonObject redacted = redact("""
                {"info":{"name":"t"},
                 "item":[{"name":"R","request":{"method":"GET",
                          "url":{"raw":"https://scott:tiger@api.example.com/pet","protocol":"https",
                                 "host":["scott:tiger@api","example","com"],"path":["pet"]}}}]}""");

        assertThat(redacted.toJson()).doesNotContain("tiger");
        JsonObject url = request(redacted).getJsonObject("url");
        assertThat(url.getString("raw")).isEqualTo("https://scott:***@api.example.com/pet");
        assertThat(url.getJsonArray("host").getString(0)).isEqualTo("scott:***@api");
    }

    @Test
    void shouldMaskVariableHoldingUrlWithUserInfo() throws Exception {
        JsonObject redacted = redact("""
                {"info":{"name":"t"},
                 "variable":[{"key":"baseUrl","value":"https://scott:tiger@api.example.com"}],
                 "item":[]}""");

        assertThat(redacted.getJsonArray("variable").getJsonObject(0).getString("value"))
                .isEqualTo("https://scott:***@api.example.com");
    }

    @Test
    void shouldMaskSensitiveQueryParametersInRawUrl() throws Exception {
        JsonObject redacted = redact(
                """
                        {"info":{"name":"t"},
                         "item":[{"name":"R","request":{"method":"GET",
                                  "url":"https://h/pet?api_key=k1&apikey=k2&token=t1&access_token=t2&password=p1&secret=s1&status=sold#top"}}]}""");

        assertThat(request(redacted).getString("url")).isEqualTo(
                "https://h/pet?api_key=***&apikey=***&token=***&access_token=***&password=***&secret=***&status=sold#top");
    }

    @Test
    void shouldMaskSensitiveQueryParametersInStructuredUrl() throws Exception {
        JsonObject redacted = redact("""
                {"info":{"name":"t"},
                 "item":[{"name":"R","request":{"method":"GET",
                          "url":{"raw":"https://h/pet?api_key=k1&token=t1&access_token=t2&status=sold",
                                 "host":["h"],"path":["pet"],
                                 "query":[{"key":"api_key","value":"k1"},{"key":"token","value":"t1"},
                                          {"key":"access_token","value":"t2"},{"key":"status","value":"sold"}]}}}]}""");

        assertThat(redacted.toJson()).doesNotContain("k1").doesNotContain("t1").doesNotContain("t2");
        JsonObject url = request(redacted).getJsonObject("url");
        assertThat(url.getString("raw")).isEqualTo("https://h/pet?api_key=***&token=***&access_token=***&status=sold");
        JsonArray query = url.getJsonArray("query");
        assertThat(query.getJsonObject(0).getString("value")).isEqualTo("***");
        assertThat(query.getJsonObject(1).getString("value")).isEqualTo("***");
        assertThat(query.getJsonObject(2).getString("value")).isEqualTo("***");
        assertThat(query.getJsonObject(3).getString("value")).isEqualTo("sold");
    }

    @Test
    void shouldMaskSensitiveFormFields() throws Exception {
        JsonObject redacted = redact("""
                {"info":{"name":"t"},
                 "item":[{"name":"R","request":{"method":"POST","url":"https://h/login",
                          "body":{"mode":"urlencoded","urlencoded":[{"key":"username","value":"scott"},
                                                                    {"key":"client_secret","value":"hunter2"},
                                                                    {"key":"grant_type","value":"password"}]}}}]}""");

        assertThat(redacted.toJson()).doesNotContain("hunter2");
        JsonArray fields = request(redacted).getJsonObject("body").getJsonArray("urlencoded");
        assertThat(fields.getJsonObject(1).getString("value")).isEqualTo("***");
        assertThat(fields.getJsonObject(2).getString("value")).isEqualTo("password");
    }

    @Test
    void shouldLeavePlaceholdersInUrls() throws Exception {
        JsonObject redacted = redact("""
                {"info":{"name":"t"},
                 "item":[{"name":"R","request":{"method":"GET",
                          "url":{"raw":"https://{{user}}:{{password}}@{{host}}/pet?api_key={{apiKey}}",
                                 "host":["{{user}}:{{password}}@{{host}}"],"path":["pet"],
                                 "query":[{"key":"api_key","value":"{{apiKey}}"}]}}}]}""");

        JsonObject url = request(redacted).getJsonObject("url");
        assertThat(url.getString("raw")).isEqualTo("https://{{user}}:{{password}}@{{host}}/pet?api_key={{apiKey}}");
        assertThat(url.getJsonArray("host").getString(0)).isEqualTo("{{user}}:{{password}}@{{host}}");
        assertThat(url.getJsonArray("query").getJsonObject(0).getString("value")).isEqualTo("{{apiKey}}");
    }
}
