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
package org.apache.camel.dsl.jbang.core.commands.catalog;

import java.io.StringReader;
import java.util.List;
import java.util.function.BiFunction;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.tooling.model.ArtifactModel;
import org.apache.camel.tooling.model.ComponentModel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogModelHeaderTest {

    @Test
    void firstObjectOnly() throws Exception {
        String json = """
                {
                  "component": {
                    "name": "foo",
                    "nested": { "a": 1 }
                  },
                  "componentProperties": { "x": { "description": "never read" } }
                }""";

        assertThat(CatalogModelHeader.firstObject(new StringReader(json), "component"))
                .isEqualTo("{\"component\":{\n    \"name\": \"foo\",\n    \"nested\": { \"a\": 1 }\n  }}");
    }

    @Test
    void bracesAndQuotesInStrings() throws Exception {
        String json = "{\"component\":{\"description\":\"a } and a \\\" and a {\",\"x\":\"\\\\\"},\"properties\":{}}";

        assertThat(CatalogModelHeader.firstObject(new StringReader(json), "component"))
                .isEqualTo("{\"component\":{\"description\":\"a } and a \\\" and a {\",\"x\":\"\\\\\"}}");
    }

    @Test
    void notTheExpectedJson() throws Exception {
        // another first key, a key that only starts the same, not an object, or the JSON ends first
        assertThat(CatalogModelHeader.firstObject(new StringReader("{\"properties\":{},\"component\":{}}"), "component"))
                .isNull();
        assertThat(CatalogModelHeader.firstObject(new StringReader("{\"componentX\":{}}"), "component")).isNull();
        assertThat(CatalogModelHeader.firstObject(new StringReader("{\"component\":\"foo\"}"), "component")).isNull();
        assertThat(CatalogModelHeader.firstObject(new StringReader("[]"), "component")).isNull();
        assertThat(CatalogModelHeader.firstObject(new StringReader("{\"component\":{\"name\":\"foo\""), "component"))
                .isNull();
    }

    @Test
    void sameAsTheWholeModel() {
        CamelCatalog catalog = new DefaultCamelCatalog();

        assertSame(catalog, catalog.findComponentNames(), CatalogModelHeader::componentModel, CamelCatalog::componentModel);
    }

    // the fields that the lists of camel catalog show
    private static <T extends ArtifactModel<?>> void assertSame(
            CamelCatalog catalog, List<String> names, BiFunction<CamelCatalog, String, T> header,
            BiFunction<CamelCatalog, String, T> whole) {
        assertThat(names).isNotEmpty();
        for (String name : names) {
            T expected = whole.apply(catalog, name);
            T actual = header.apply(catalog, name);
            if (expected == null) {
                assertThat(actual).as(name).isNull();
                continue;
            }
            assertThat(actual).as(name).isNotNull();
            assertThat(actual.getName()).as(name).isEqualTo(expected.getName());
            if (actual instanceof ComponentModel component) {
                assertThat(component.getScheme()).as(name).isEqualTo(((ComponentModel) expected).getScheme());
            }
            assertThat(actual.getTitle()).as(name).isEqualTo(expected.getTitle());
            assertThat(actual.getSupportLevel()).as(name).isEqualTo(expected.getSupportLevel());
            assertThat(actual.getFirstVersionShort()).as(name).isEqualTo(expected.getFirstVersionShort());
            assertThat(actual.getDescription()).as(name).isEqualTo(expected.getDescription());
            assertThat(actual.getLabel()).as(name).isEqualTo(expected.getLabel());
            assertThat(actual.isDeprecated()).as(name).isEqualTo(expected.isDeprecated());
            assertThat(actual.isNativeSupported()).as(name).isEqualTo(expected.isNativeSupported());
            assertThat(actual.getGroupId()).as(name).isEqualTo(expected.getGroupId());
            assertThat(actual.getArtifactId()).as(name).isEqualTo(expected.getArtifactId());
            assertThat(actual.getVersion()).as(name).isEqualTo(expected.getVersion());
        }
    }
}
