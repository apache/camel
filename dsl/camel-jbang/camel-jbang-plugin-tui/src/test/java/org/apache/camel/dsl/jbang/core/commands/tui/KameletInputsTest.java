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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import org.apache.camel.diagram.RouteDiagramHelper;
import org.apache.camel.diagram.RouteDiagramLayoutEngine;
import org.apache.camel.dsl.jbang.core.commands.ai.KameletInput;
import org.apache.camel.dsl.jbang.core.commands.tui.SourceLiveColumn.LiveLine;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The data type a Kamelet turns the body into, at the step that calls it, in the views (CAMEL-27475): the sample of the
 * Kamelets 4.24 blog post, where the Map of a row goes into postgresql-sink, which takes json.
 */
class KameletInputsTest {

    /** As postgresql-sink of the Kamelet catalog 4.24. */
    static final KameletInput JSON = new KameletInput(
            "postgresql-sink", "json", "application-json", "application/json",
            "One JSON object, or with batch a JSON array of objects, as JSON text in bytes.",
            List.of("text", "bytes", "stream", "map", "list", "pojo", "null"), "A Json object...");

    /** As csv-deserialize-action: text-plain takes no List. */
    static final KameletInput TEXT = new KameletInput(
            "csv-deserialize-action", "text", "text-plain", "text/plain", null,
            List.of("text", "bytes", "stream", "file", "value", "null"), null);

    @TempDir
    Path dir;

    @BeforeEach
    void setUp() {
        Theme.resetForTesting();
        KameletInputs.clearForTesting();
    }

    @AfterEach
    void tearDown() {
        KameletInputs.clearForTesting();
    }

    private static ProcessorInfo processor(String uri, KameletInput in, List<String> types, List<String> kinds) {
        ProcessorInfo p = new ProcessorInfo();
        p.id = "to1";
        p.processor = "to";
        p.uri = uri;
        p.kameletInput = in;
        p.bodyTypes = types;
        p.bodyKinds = kinds;
        return p;
    }

    @Test
    void theLabelOfAStepThatCallsAKamelet() {
        // the Map of a row becomes the JSON that the sink takes: no warning
        ProcessorInfo row = processor("kamelet:postgresql-sink", JSON, List.of("java.util.LinkedHashMap"), List.of("data"));
        assertThat(KameletInputs.shortLabel(row)).isEqualTo("LinkedHashMap → json");
        assertThat(KameletInputs.style(row)).isEqualTo(Theme.muted());
        // before a message has reached it: what it takes
        ProcessorInfo idle = processor("kamelet:postgresql-sink", JSON, List.of(), List.of());
        assertThat(KameletInputs.shortLabel(idle)).isEqualTo("→ json");
        // a body the transformer does not take needs attention
        ProcessorInfo list = processor("kamelet:csv-deserialize-action", TEXT, List.of("java.util.ArrayList"),
                List.of("collection"));
        assertThat(KameletInputs.shortLabel(list))
                .isEqualTo(BodyTypes.MARK + " ArrayList → text (text-plain does not take it)");
        assertThat(KameletInputs.style(list)).isEqualTo(Theme.warning());
        // no Kamelet: the body types alone
        ProcessorInfo log = processor("log:out", null, List.of("byte[]"), List.of("payload"));
        assertThat(KameletInputs.shortLabel(log)).isEqualTo("byte[]");
    }

    @Test
    void theQuickDocumentationSaysWhyAMapIsFine() {
        String doc = KameletInputs.doc(JSON, List.of("java.util.LinkedHashMap"), List.of("data"));
        assertThat(doc).startsWith("The Kamelet postgresql-sink takes json: One JSON object")
                .contains("with the data type transformer application-json, which takes text, bytes, a stream, a Map,"
                          + " a List, a POJO or no body.")
                .doesNotContain(BodyTypes.MARK);

        String notTaken = KameletInputs.doc(TEXT, List.of("java.util.ArrayList"), List.of("collection"));
        assertThat(notTaken).startsWith("The Kamelet csv-deserialize-action takes text.")
                .endsWith(BodyTypes.MARK + " It does not take ArrayList: convert the body first.");
    }

    @Test
    void theSourceLineOfAStepThatCallsAKamelet() {
        RouteInfo route = new RouteInfo();
        route.source = "csv-to-db.camel.yaml:2";
        ProcessorInfo csv = processor("kamelet:csv-deserialize-action", TEXT, List.of("java.io.File"), List.of("payload"));
        csv.source = "csv-to-db.camel.yaml:8";
        ProcessorInfo db = processor("kamelet:postgresql-sink", JSON, List.of("java.util.LinkedHashMap"), List.of("data"));
        db.source = "csv-to-db.camel.yaml:13";
        ProcessorInfo wrong = processor("kamelet:csv-deserialize-action", TEXT, List.of("java.util.ArrayList"),
                List.of("collection"));
        wrong.source = "csv-to-db.camel.yaml:20";
        route.processors.addAll(List.of(csv, db, wrong));

        Map<Integer, LiveLine> lines = LiveRunLines.of(List.of(route), "/work/csv-to-db.camel.yaml");

        assertThat(lines.get(7).kameletInput()).isEqualTo(TEXT);
        assertThat(lines.get(7).mixed()).isFalse();
        assertThat(lines.get(12).kameletInput()).isEqualTo(JSON);
        assertThat(lines.get(12).takes()).isTrue();
        assertThat(lines.get(12).mixed()).isFalse();
        // a List into text-plain: the line needs attention
        assertThat(lines.get(19).mixed()).isTrue();
    }

    @Test
    void theSourceViewShowsTheArrowAndTheQuickDocumentation() throws Exception {
        Path file = Files.writeString(dir.resolve("csv-to-db.camel.yaml"), """
                - route:
                    from:
                      uri: kamelet:file-watch-source
                      steps:
                        - to: kamelet:postgresql-sink
                        - to: kamelet:csv-deserialize-action
                """);
        SourceViewer viewer = new SourceViewer();
        viewer.loadFile(file);
        Map<Integer, LiveLine> data = new HashMap<>();
        data.put(4, new LiveLine(2, 0, 0, List.of("java.util.LinkedHashMap"), List.of("data"), List.of(), JSON, false));
        // reached by no message yet: what the Kamelet takes
        data.put(5, new LiveLine(0, 0, 0, List.of(), List.of(), List.of(), TEXT, false));
        viewer.liveColumn().setLiveRunData(path -> data);
        viewer.liveColumn().refreshLiveLinesForTesting();

        Rect area = new Rect(0, 0, 120, 12);
        Buffer buffer = Buffer.empty(area);
        viewer.render(Frame.forTesting(buffer), area);
        String rendered = TuiTestHelper.bufferToString(buffer);

        assertThat(rendered).contains("LinkedHashMap → json").contains("→ text");
        List<SourceViewer.DocEntry> docs = viewer.liveColumn().docs(4);
        assertThat(docs).extracting(SourceViewer.DocEntry::title).containsExactly("Body types (data)", "Kamelet data type");
        assertThat(viewer.liveColumn().docs(5)).extracting(SourceViewer.DocEntry::title)
                .containsExactly("Kamelet data type");
    }

    @Test
    void theDiagramHasItFromTheStatus() {
        IntegrationInfo info = new IntegrationInfo();
        RouteInfo route = new RouteInfo();
        route.routeId = "csv-to-db";
        route.processors.add(processor("kamelet:postgresql-sink", JSON, List.of("java.util.LinkedHashMap"), List.of("data")));
        info.routes.add(route);
        JsonObject structure = (JsonObject) Jsoner.deserialize("""
                {"routes": [{"routeId": "csv-to-db", "code": [
                  {"type": "route", "id": "csv-to-db", "level": 0, "code": "route[csv-to-db]"},
                  {"type": "from", "id": "from1", "level": 1, "code": "from[kamelet:file-watch-source]"},
                  {"type": "to", "id": "to1", "level": 2, "code": "to[kamelet:postgresql-sink]"}]}]}
                """, (JsonObject) null);

        JsonObject topology = DiagramTopology.withNumbers(structure, info);

        RouteDiagramLayoutEngine.NodeInfo to = RouteDiagramHelper.parseRoutes(topology).get(0).nodes.get(2);
        assertThat(to.kameletInput.dataType()).isEqualTo("json");
        assertThat(to.kameletInput.takesBody()).isTrue();

        // the Info panel says what the Kamelet takes, and why the Map is fine
        List<Line> lines = new ArrayList<>();
        DiagramDetailSupport.addBodyTypes(lines, to, 40);
        String text = String.join("\n", lines.stream()
                .map(l -> l.spans().stream().map(Span::content).reduce("", String::concat)).toList());
        assertThat(text).contains(" Kamelet takes: json").contains("Camel turns the body into it")
                .contains("LinkedHashMap").doesNotContain(BodyTypes.MARK);
    }

    @Test
    void theKameletsVersionOfTheIntegration() {
        JsonObject root = (JsonObject) Jsoner.deserialize("""
                {"context": {"name": "csv-to-db", "version": "4.24.0"},
                 "properties": {"properties": [
                   {"key": "camel.jbang.kameletsVersion", "value": "4.24.0-SNAPSHOT"},
                   {"key": "camel.main.name", "value": "csv-to-db"}]}}
                """, (JsonObject) null);

        IntegrationInfo info = StatusParser.parseIntegration(ProcessHandle.current(), root);

        // the Kamelets it runs, which camel run --kamelets-version may set to another than the Camel CLI's
        assertThat(info.kameletsVersion).isEqualTo("4.24.0-SNAPSHOT");
        // the camel.jbang properties stay out of the configuration
        assertThat(info.configProperties).extracting(cp -> cp.key).containsExactly("camel.main.name");
    }

    @Test
    void theKameletIsLookedUpInTheBackground() throws Exception {
        Files.writeString(dir.resolve("my-sink.kamelet.yaml"), """
                apiVersion: camel.apache.org/v1
                kind: Kamelet
                metadata:
                  name: my-sink
                  labels:
                    camel.apache.org/kamelet.type: sink
                spec:
                  definition:
                    title: My Sink
                  dataTypes:
                    in:
                      default: json
                      types:
                        json:
                          format: application-json
                  template:
                    from:
                      uri: kamelet:source
                      steps:
                        - to: log:out
                """);

        // the first look is not known yet: the view does not wait
        assertThat(KameletInputs.get("my-sink", dir, null)).isNull();
        await().atMost(10, TimeUnit.SECONDS).until(() -> KameletInputs.get("my-sink", dir, null) != null);
        KameletInput in = KameletInputs.get("my-sink", dir, null);
        assertThat(in.dataType()).isEqualTo("json");
        assertThat(in.carriers()).contains("map");
    }
}
