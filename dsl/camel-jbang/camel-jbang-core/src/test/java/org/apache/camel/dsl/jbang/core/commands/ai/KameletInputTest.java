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
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The data type a Kamelet turns the body into before its route sees it, at the step that calls it (CAMEL-27475).
 */
class KameletInputTest {

    private static final CamelCatalog CATALOG = new DefaultCamelCatalog();

    /** As postgresql-sink: one JSON object, which a Map becomes. */
    private static final String DB_SINK = """
            apiVersion: camel.apache.org/v1
            kind: Kamelet
            metadata:
              name: db-sink
              labels:
                camel.apache.org/kamelet.type: sink
            spec:
              definition:
                title: DB Sink
              dataTypes:
                in:
                  default: json
                  types:
                    json:
                      format: application-json
                      description: One JSON object, as JSON text in bytes.
                      mediaType: application/json
              template:
                from:
                  uri: kamelet:source
                  steps:
                    - to: log:db
            """;

    /** As csv-deserialize-action: the CSV text. */
    private static final String CSV_ACTION = """
            apiVersion: camel.apache.org/v1
            kind: Kamelet
            metadata:
              name: csv-action
              labels:
                camel.apache.org/kamelet.type: action
            spec:
              definition:
                title: CSV Action
              dataTypes:
                in:
                  default: text
                  types:
                    text:
                      format: text-plain
                      mediaType: text/plain
              template:
                from:
                  uri: kamelet:source
                  steps:
                    - unmarshal:
                        csv: {}
            """;

    /** No default in data type: the Kamelet gets the body as it is. */
    private static final String LOG_SINK = """
            apiVersion: camel.apache.org/v1
            kind: Kamelet
            metadata:
              name: my-log-sink
              labels:
                camel.apache.org/kamelet.type: sink
            spec:
              definition:
                title: Log Sink
              template:
                from:
                  uri: kamelet:source
                  steps:
                    - to: log:out
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

    private static Path project(Path dir) throws Exception {
        Files.writeString(dir.resolve("db-sink.kamelet.yaml"), DB_SINK);
        Files.writeString(dir.resolve("csv-action.kamelet.yaml"), CSV_ACTION);
        Files.writeString(dir.resolve("my-log-sink.kamelet.yaml"), LOG_SINK);
        return dir;
    }

    @Test
    void theNameOfTheKameletAUriCalls() {
        assertThat(KameletInput.kameletName("kamelet:postgresql-sink?serverName=db")).isEqualTo("postgresql-sink");
        assertThat(KameletInput.kameletName("kamelet://postgresql-sink")).isEqualTo("postgresql-sink");
        assertThat(KameletInput.kameletName("kamelet:aws-s3-sink/route1")).isEqualTo("aws-s3-sink");
        // the ends of the route of a Kamelet, a dynamic name, and another component
        assertThat(KameletInput.kameletName("kamelet:sink")).isNull();
        assertThat(KameletInput.kameletName("kamelet:source")).isNull();
        assertThat(KameletInput.kameletName("kamelet:${header.target}")).isNull();
        assertThat(KameletInput.kameletName("log:foo")).isNull();
        assertThat(KameletInput.kameletName(null)).isNull();
    }

    @Test
    void theDataTypeAndWhatItsTransformerTakes(@TempDir Path dir) throws Exception {
        KameletInput in = KameletInput.find("kamelet:db-sink?query=x", project(dir), CATALOG);

        assertThat(in.dataType()).isEqualTo("json");
        assertThat(in.transformer()).isEqualTo("application-json");
        assertThat(in.mediaType()).isEqualTo("application/json");
        assertThat(in.description()).isEqualTo("One JSON object, as JSON text in bytes.");
        // the catalog says what application-json takes: also a Map, a List and a POJO, which it writes as JSON
        assertThat(in.carriers()).contains("text", "bytes", "stream", "map", "list", "pojo");
        assertThat(in.takes()).isNotBlank();
        assertThat(in.carriersInWords()).contains("a Map", "a List", "a POJO").endsWith(" or no body");

        // a Kamelet with no default in data type gets the body as it is, and one that is not known says nothing
        assertThat(KameletInput.find("kamelet:my-log-sink", dir, CATALOG)).isNull();
        assertThat(KameletInput.find("kamelet:unknown-sink", dir, CATALOG)).isNull();
    }

    @Test
    void whetherTheTransformerTakesTheBodies(@TempDir Path dir) throws Exception {
        KameletInput json = KameletInput.find("kamelet:db-sink", project(dir), CATALOG);
        KameletInput text = KameletInput.find("kamelet:csv-action", dir, CATALOG);

        // the rows of the blog sample: a Map into a sink that takes JSON is fine
        assertThat(json.takes(List.of("java.util.LinkedHashMap"), List.of("data"))).isTrue();
        // a File into csv-deserialize-action is fine too: text-plain reads the file
        assertThat(text.takes(List.of("java.io.File"), List.of("payload"))).isTrue();
        // the List of rows is not text
        assertThat(text.takes(List.of("java.util.ArrayList"), List.of("collection"))).isFalse();
        assertThat(json.takes(List.of("org.apache.camel.component.file.GenericFile"), List.of("payload"))).isFalse();
        // a stream cache of Camel is a stream
        assertThat(json.takes(List.of("org.apache.camel.converter.stream.ByteArrayInputStreamCache"), List.of("payload")))
                .isTrue();
        // a class the catalog has no name for, as a payload: not known, so no warning
        assertThat(json.takes(List.of("com.acme.Payload"), List.of("payload"))).isNull();
        // not reached yet
        assertThat(json.takes(List.of(), List.of())).isNull();
    }

    @Test
    void theRouteStructureHasTheDataTypeOfEachStepThatCallsAKamelet(@TempDir Path dir) throws Exception {
        JsonObject structure = (JsonObject) Jsoner.deserialize("""
                {"routes": [{"routeId": "csv-to-db", "code": [
                  {"type": "route", "id": "csv-to-db", "level": 0, "code": "route[csv-to-db]"},
                  {"type": "from", "id": "from1", "level": 1, "code": "from[kamelet:file-watch-source]",
                   "uri": "kamelet:file-watch-source"},
                  {"type": "to", "id": "to1", "level": 2, "code": "to[kamelet:csv-action]", "uri": "kamelet:csv-action",
                   "bodyTypes": ["java.io.File"], "bodyKinds": ["payload"]},
                  {"type": "split", "id": "split1", "level": 2, "code": "split[${body}]"},
                  {"type": "to", "id": "to2", "level": 3, "code": "to[kamelet:db-sink]", "uri": "kamelet:db-sink",
                   "bodyTypes": ["java.util.LinkedHashMap"], "bodyKinds": ["data"]},
                  {"type": "kamelet", "id": "kamelet1", "level": 3, "code": "kamelet[csv-action]"},
                  {"type": "to", "id": "to3", "level": 3, "code": "to[kamelet:my-log-sink]",
                   "uri": "kamelet:my-log-sink"}]}]}
                """, (JsonObject) null);

        int added = KameletInput.addTo(structure, project(dir), CATALOG);

        assertThat(added).isEqualTo(3);
        List<JsonObject> code = ((JsonObject) ((JsonArray) structure.getCollection("routes")).get(0)).getCollection("code");
        JsonObject csv = (JsonObject) code.get(2).get("kameletInput");
        assertThat(csv.getString("dataType")).isEqualTo("text");
        assertThat(csv.getBoolean("takesBody")).isTrue();
        JsonObject db = (JsonObject) code.get(4).get("kameletInput");
        assertThat(db.getString("dataType")).isEqualTo("json");
        assertThat(db.getString("transformer")).isEqualTo("application-json");
        assertThat(db.getBoolean("takesBody")).isTrue();
        // the kamelet EIP, not reached yet: the data type, and nothing about the body
        JsonObject eip = (JsonObject) code.get(5).get("kameletInput");
        assertThat(eip.getString("dataType")).isEqualTo("text");
        assertThat(eip.containsKey("takesBody")).isFalse();
        // a Kamelet without a default in data type, and the from
        assertThat(code.get(6).containsKey("kameletInput")).isFalse();
        assertThat(code.get(1).containsKey("kameletInput")).isFalse();
    }

    @Test
    void theArrowInWords() {
        assertThat(KameletInput.arrowText("json", true, "application-json")).isEqualTo("→ json");
        assertThat(KameletInput.arrowText("json", null, "application-json")).isEqualTo("→ json");
        assertThat(KameletInput.arrowText("text", false, "text-plain")).isEqualTo("→ text (text-plain does not take it)");
    }
}
