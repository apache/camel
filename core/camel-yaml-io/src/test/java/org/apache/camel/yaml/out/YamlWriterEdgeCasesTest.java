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
package org.apache.camel.yaml.out;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.app.SemanticDefinition;
import org.apache.camel.model.dataformat.JsonDataFormat;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.yaml.LwModelToYAMLDumper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class YamlWriterEdgeCasesTest {

    @Test
    public void semanticExportRejectsDuplicatesInsteadOfDiscardingThem() {
        SemanticDefinition questions = new SemanticDefinition();
        questions.question("q").type("boolean").instructions("First?");
        questions.question("q").type("boolean").instructions("Second?");
        YamlModelWriter writer = new YamlModelWriter();
        assertThatThrownBy(() -> writer.writeSemanticDefinition(questions)).hasMessageContaining("Duplicate semantic question");

        SemanticDefinition criteria = new SemanticDefinition();
        criteria.question("q").type("choice").instructions("Which?").criterion("a", "First").criterion("a", "Second");
        assertThatThrownBy(() -> writer.writeSemanticDefinition(criteria)).hasMessageContaining("Duplicate semantic criterion");
    }

    @Test
    public void semanticDeclarationsRetainYamlShapeAndTextTypes() throws Exception {
        SemanticDefinition semantic = new SemanticDefinition();
        semantic.question("007").type("boolean").instructions("true").state("42").threshold(0.8)
                .uncertainty(0.1).uncertaintyPolicy("non-match").criterion("true", "yes");
        semantic.question("priority").type("score").instructions("Priority?").level("Low").level("High");
        YamlModelWriter writer = new YamlModelWriter();
        String yaml = writer.printAsYaml(List.of(writer.writeSemanticDefinition(semantic)));
        JsonNode node = new YAMLMapper().readTree(yaml);
        JsonNode questions = node.get(0).get("semantic").get("question");
        assertThat(questions.isObject()).isTrue();
        assertThat(questions.get("007").get("instructions").isTextual()).isTrue();
        assertThat(questions.get("007").get("state").isTextual()).isTrue();
        assertThat(questions.get("007").get("criteria").get("true").asText()).isEqualTo("yes");
        assertThat(questions.get("007").get("threshold").doubleValue()).isEqualTo(0.8);
        assertThat(questions.get("priority").get("criteria").get(1).asText()).isEqualTo("High");
    }

    @Test
    public void testTextThatLooksLikeNumber() throws Exception {
        RouteDefinition route = new RouteDefinition();
        route.from("direct:a").setHeader("007").constant("x").log("1e3");

        YamlModelWriter writer = new YamlModelWriter();
        JsonObject jo = writer.writeRouteDefinition(route);
        String yaml = writer.printAsYaml(List.of(jo));

        JsonNode node = new YAMLMapper().readTree(yaml);
        JsonNode steps = node.get(0).get("route").get("from").get("steps");
        assertThat(steps.get(0).get("setHeader").get("name").asText()).as(yaml).isEqualTo("007");
        assertThat(steps.get(1).get("log").get("message").asText()).as(yaml).isEqualTo("1e3");
    }

    @Test
    public void testDumpDataFormats() throws Exception {
        JsonDataFormat json1 = new JsonDataFormat();
        json1.setId("one");
        JsonDataFormat json2 = new JsonDataFormat();
        json2.setId("two");
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("one", json1);
        map.put("two", json2);

        String yaml = new LwModelToYAMLDumper().dumpDataFormatsAsYaml(null, map);
        JsonNode node = new YAMLMapper().readTree(yaml);
        // a list of data formats, each as its own single-key map
        JsonNode list = node.get(0).get("dataFormats");
        assertThat(list.isArray()).as(yaml).isTrue();
        assertThat(list.size()).as(yaml).isEqualTo(2);
        assertThat(list.get(0).get("json").get("id").asText()).isEqualTo("one");
        assertThat(list.get(1).get("json").get("id").asText()).isEqualTo("two");
    }
}
