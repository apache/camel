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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The data types of a Kamelet say the body it takes and gives, as the body types of their data type transformers in the
 * catalog say (CAMEL-25928).
 */
class KameletDataTypesTest {

    private static final CamelCatalog CATALOG = new DefaultCamelCatalog();

    private static final String MY_SOURCE = """
            apiVersion: camel.apache.org/v1
            kind: Kamelet
            metadata:
              name: my-source
              labels:
                camel.apache.org/kamelet.type: source
            spec:
              definition:
                title: My Source
              dataTypes:
                out:
                  default: binary
                  types:
                    binary:
                      format: application-octet-stream
                      mediaType: application/octet-stream
                    cloudevents:
                      scheme: aws2-s3
                      format: application-cloudevents
                      description: The CloudEvent headers
              template:
                from:
                  uri: timer:tick
                  steps:
                    - setBody:
                        constant: hello
                    - to: kamelet:sink
            """;

    @BeforeAll
    static void setUp() {
        // no Kamelet catalog: the project's own Kamelets only
        KameletDefinitions.setTestCatalog(Map.of());
    }

    @AfterAll
    static void tearDown() {
        KameletDefinitions.setTestCatalog(null);
    }

    @Test
    void theDataTypesOfAProjectKamelet(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("my-source.kamelet.yaml"), MY_SOURCE);
        KameletDefinitions.Definition def = KameletDefinitions.projectKamelets(dir).get("my-source");
        assertThat(def.dataTypes()).hasSize(2);
        assertThat(def.defaultFormat("out")).isEqualTo("application-octet-stream");
        assertThat(def.defaultFormat("in")).isNull();
        // the scheme and the format name the transformer, as Camel joins them
        assertThat(def.dataTypes().get(1).format).isEqualTo("aws2-s3:application-cloudevents");
        assertThat(def.dataTypes().get(1).defaultType).isFalse();
    }

    @Test
    void theDocSaysTheBodyOfEachDataType(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("my-source.kamelet.yaml"), MY_SOURCE);
        JsonObject doc = CatalogDocs.kameletDoc(KameletDefinitions.projectKamelets(dir).get("my-source"), CATALOG);
        JsonArray dataTypes = (JsonArray) doc.get("dataTypes");
        assertThat(dataTypes).hasSize(2);
        JsonObject binary = (JsonObject) dataTypes.get(0);
        assertThat(binary.getString("direction")).isEqualTo("out");
        assertThat(binary.getString("format")).isEqualTo("application-octet-stream");
        assertThat(binary.getBoolean("default")).isTrue();
        assertThat(binary.getString("body")).startsWith("bytes");
        assertThat(((JsonObject) dataTypes.get(1)).getString("body")).startsWith("unchanged");
        // without the catalog the doc has the data types, but not their body
        JsonObject plain = CatalogDocs.kameletDoc(KameletDefinitions.projectKamelets(dir).get("my-source"));
        assertThat(((JsonObject) ((JsonArray) plain.get("dataTypes")).get(0)).get("body")).isNull();
    }

    @Test
    void theValidatorWalksTheBodyOfTheDataType(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("my-source.kamelet.yaml"), MY_SOURCE);
        String route = """
                - route:
                    from:
                      uri: kamelet:my-source
                      steps:
                        - setHeader:
                            name: status
                            simple: "${body[status]}"
                """;
        List<String> messages = SourceValidator.validate("orders.camel.yaml", route, CATALOG, null, dir);
        assertThat(messages).anyMatch(m -> m.contains("simple reads a field of the body (${body[status]})")
                && m.contains("kamelet:my-source gives it as its out data type application-octet-stream"));
        assertThat(ValidatorFacts.kameletDataTypes(route, dir))
                .containsKey("my-source")
                .doesNotContainKey("other");
        // a file that uses no Kamelet does not read them
        assertThat(ValidatorFacts.kameletDataTypes("- route: {from: {uri: timer:tick}}", dir)).isEmpty();
    }
}
