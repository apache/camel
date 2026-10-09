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
package org.apache.camel.component.rest.postman.collection;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.rest.postman.model.PostmanCollection;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PostmanCollectionLoaderTest {

    private DefaultCamelContext context;

    @BeforeEach
    void setUp() {
        context = new DefaultCamelContext();
    }

    @AfterEach
    void tearDown() {
        context.stop();
    }

    @Test
    void shouldLoadFromTheClasspath() {
        PostmanCollection collection
                = PostmanCollectionLoader.loadFromResource(context, "classpath:petstore-collection.json");

        assertThat(collection.getName()).isEqualTo("Petstore");
        assertThat(collection.getItems()).hasSize(2);
    }

    @Test
    void shouldUnwrapTheCloudEnvelope() {
        PostmanCollection collection = PostmanCollectionLoader.parse(
                "{\"collection\":{\"info\":{\"name\":\"Wrapped\",\"schema\":\"v2.1\"},\"item\":[]}}", "test");

        assertThat(collection.getName()).isEqualTo("Wrapped");
    }

    @Test
    void shouldReportAMissingResourceClearly() {
        assertThatThrownBy(() -> PostmanCollectionLoader.loadFromResource(context, "classpath:nope.json"))
                .isInstanceOf(RuntimeCamelException.class)
                .hasMessageContaining("Postman collection not found: classpath:nope.json");
    }

    @Test
    void shouldRejectContentThatIsNotJson() {
        assertThatThrownBy(() -> PostmanCollectionLoader.parse("not json at all", "test"))
                .isInstanceOf(RuntimeCamelException.class)
                .hasMessageContaining("not valid JSON");
    }

    @Test
    void shouldRejectJsonThatIsNotACollection() {
        assertThatThrownBy(() -> PostmanCollectionLoader.parse("{\"hello\":\"world\"}", "test"))
                .isInstanceOf(RuntimeCamelException.class)
                .hasMessageContaining("the info object is missing");
    }

    @Test
    void shouldAcceptACollectionWithAnUnexpectedSchemaVersion() {
        // it only warns: refusing outright would block collections that are in practice still readable
        PostmanCollection collection = PostmanCollectionLoader.parse(
                "{\"info\":{\"name\":\"Old\",\"schema\":\"https://schema.getpostman.com/json/collection/v2.0.0/collection.json\"},"
                                                                     + "\"item\":[]}",
                "test");

        assertThat(collection.getName()).isEqualTo("Old");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "12ece9e1-2abf-4edc-8e34-de66e74114d2",
            "12345678-12ece9e1-2abf-4edc-8e34-de66e74114d2"
    })
    void shouldDetectCloudSources(String source) {
        assertThat(PostmanCollectionLoader.isCloudSource(source, PostmanCollectionLoader.SOURCE_TYPE_AUTO)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "petstore.json",
            "classpath:petstore.json",
            "https://example.com/petstore.json",
            "12ece9e1-2abf-4edc-8e34-de66e74114d2.json"
    })
    void shouldDetectResourceSources(String source) {
        assertThat(PostmanCollectionLoader.isCloudSource(source, PostmanCollectionLoader.SOURCE_TYPE_AUTO)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "petstore.json",
            "classpath:petstore.json",
            "file:target/petstore.json",
            "12ece9e1-2abf-4edc-8e34-de66e74114d2.json"
    })
    void shouldDetectLocalSources(String source) {
        assertThat(PostmanCollectionLoader.isLocalSource(source, PostmanCollectionLoader.SOURCE_TYPE_AUTO)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "12ece9e1-2abf-4edc-8e34-de66e74114d2",
            "http://example.com/petstore.json",
            "https://example.com/petstore.json",
            "ref:petstore",
            "mem:petstore.json"
    })
    void shouldDetectSourcesThatAreNotLocal(String source) {
        assertThat(PostmanCollectionLoader.isLocalSource(source, PostmanCollectionLoader.SOURCE_TYPE_AUTO)).isFalse();
    }

    /**
     * The resource loader takes a drive letter for a scheme, so a Windows path is not loadable and needs a file: URI.
     */
    @ParameterizedTest
    @ValueSource(strings = { "C:\\petstore.json", "C:/petstore.json" })
    void shouldNotLoadAWindowsPathWithoutAFileScheme(String source) {
        assertThat(PostmanCollectionLoader.isLocalSource(source, PostmanCollectionLoader.SOURCE_TYPE_AUTO)).isFalse();
        assertThatThrownBy(() -> PostmanCollectionLoader.loadFromResource(context, source))
                .isInstanceOf(RuntimeCamelException.class)
                .rootCause()
                .hasMessageContaining("supporting the scheme: C");
    }

    @Test
    void shouldNotTreatACloudSourceAsLocal() {
        assertThat(PostmanCollectionLoader.isLocalSource("petstore.json", PostmanCollectionLoader.SOURCE_TYPE_CLOUD))
                .isFalse();
        assertThat(PostmanCollectionLoader.isLocalSource(
                "12ece9e1-2abf-4edc-8e34-de66e74114d2", PostmanCollectionLoader.SOURCE_TYPE_RESOURCE)).isTrue();
    }

    @Test
    void shouldHonourAnExplicitSourceType() {
        assertThat(PostmanCollectionLoader.isCloudSource("petstore.json", PostmanCollectionLoader.SOURCE_TYPE_CLOUD))
                .isTrue();
        assertThat(PostmanCollectionLoader.isCloudSource(
                "12ece9e1-2abf-4edc-8e34-de66e74114d2", PostmanCollectionLoader.SOURCE_TYPE_RESOURCE)).isFalse();
    }
}
