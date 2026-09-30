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
import org.apache.camel.model.dataformat.JsonDataFormat;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.yaml.LwModelToYAMLDumper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class YamlWriterEdgeCasesTest {

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
