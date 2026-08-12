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
}
