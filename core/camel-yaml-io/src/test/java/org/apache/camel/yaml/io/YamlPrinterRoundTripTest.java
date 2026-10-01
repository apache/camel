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
package org.apache.camel.yaml.io;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.SwitchDefinition;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.yaml.out.YamlModelWriter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

public class YamlPrinterRoundTripTest {

    @ParameterizedTest
    @ValueSource(strings = { "001", "true", "null", "", "*", "otherwise", "1e3" })
    public void testSwitchLiteralValue(String value) throws Exception {
        RouteDefinition route = new RouteDefinition("direct:start");
        SwitchDefinition sw = route.doSwitch().header("department");
        sw.doCase(value, "direct:matched").otherwise("direct:review");
        sw.preCreateProcessor();
        YamlModelWriter writer = new YamlModelWriter();

        String yaml = writer.printAsYaml(List.of(writer.writeRouteDefinition(route)));
        JsonNode node = new YAMLMapper().readTree(yaml).get(0).path("route").path("from").path("steps").get(0).path("switch");
        JsonNode literal = node.path("case").get(0).path("value");
        assertThat(literal.isTextual()).as(yaml).isTrue();
        assertThat(literal.asText()).isEqualTo(value);
        assertThat(node.path("selector").path("header").path("expression").asText()).isEqualTo("department");
        assertThat(node.path("otherwise").path("uri").asText()).isEqualTo("direct:review");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Processing:", ",leading comma", "]x", "}x", "a\rb", " x\ny", "  lead\nnext", "x\n\n", "007", "+5", "1.",
            ".inf", ".nan", "1e3", "0x1f" })
    public void testStringValue(String value) throws Exception {
        JsonObject log = new JsonObject();
        log.put("message", value);
        JsonObject root = new JsonObject(Map.of("log", log));

        String yaml = YamlPrinter.print(List.of(root));
        JsonNode node = new YAMLMapper().readTree(yaml);
        JsonNode message = node.get(0).get("log").get("message");
        assertThat(message.isTextual()).as(yaml).isTrue();
        assertThat(message.asText()).as(yaml).isEqualTo(value);
    }
}
