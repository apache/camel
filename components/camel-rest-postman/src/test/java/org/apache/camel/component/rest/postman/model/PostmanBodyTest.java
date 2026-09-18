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

import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class PostmanBodyTest {

    private static PostmanBody parse(String json) throws Exception {
        return PostmanBody.parse(Jsoner.deserialize(json));
    }

    @ParameterizedTest
    @CsvSource({
            "json, application/json",
            "xml, application/xml",
            "html, text/html",
            "javascript, application/javascript",
            "text, text/plain",
            "JSON, application/json"
    })
    void shouldInferContentTypeFromTheRawLanguage(String language, String expected) throws Exception {
        PostmanBody body = parse("{\"mode\":\"raw\",\"raw\":\"x\",\"options\":{\"raw\":{\"language\":\""
                                 + language + "\"}}}");

        assertThat(body.inferContentType()).isEqualTo(expected);
    }

    @Test
    void shouldDefaultARawBodyWithoutLanguageToPlainText() throws Exception {
        assertThat(parse("{\"mode\":\"raw\",\"raw\":\"x\"}").inferContentType()).isEqualTo("text/plain");
    }

    @ParameterizedTest
    @CsvSource({
            "graphql, application/json",
            "urlencoded, application/x-www-form-urlencoded",
            "formdata, multipart/form-data",
            "file, application/octet-stream"
    })
    void shouldInferContentTypeFromTheMode(String mode, String expected) throws Exception {
        assertThat(parse("{\"mode\":\"" + mode + "\"}").inferContentType()).isEqualTo(expected);
    }

    @Test
    void shouldReturnNullForAnUnknownMode() throws Exception {
        assertThat(parse("{\"mode\":\"martian\"}").inferContentType()).isNull();
    }

    @Test
    void shouldTreatAnAbsentBodyAsNoBody() {
        assertThat(PostmanBody.parse(null)).isNull();
    }

    @Test
    void shouldTreatAnEmptyBodyAsNoBody() throws Exception {
        assertThat(parse("{}")).isNull();
    }

    @Test
    void shouldTreatADisabledBodyAsNoBody() throws Exception {
        assertThat(parse("{\"mode\":\"raw\",\"raw\":\"x\",\"disabled\":true}")).isNull();
    }

    @Test
    void shouldReadUrlencodedFields() throws Exception {
        PostmanBody body = parse("""
                {"mode":"urlencoded","urlencoded":[{"key":"a","value":"1"},
                                                   {"key":"b","value":"2","disabled":true}]}""");

        assertThat(body.getFormFields()).hasSize(2);
        assertThat(body.getFormFields().get(0).key()).isEqualTo("a");
        assertThat(body.getFormFields().get(1).disabled()).isTrue();
    }

    @Test
    void shouldNotReadFormFieldsForARawBody() throws Exception {
        assertThat(parse("{\"mode\":\"raw\",\"raw\":\"x\"}").getFormFields()).isEmpty();
    }
}
