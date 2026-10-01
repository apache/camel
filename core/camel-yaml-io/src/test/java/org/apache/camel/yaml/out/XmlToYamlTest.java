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

import java.io.FileInputStream;
import java.io.IOError;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.xml.in.ModelParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XmlToYamlTest {

    public static final String NAMESPACE = "http://camel.apache.org/schema/spring";

    private static final Logger LOG = LoggerFactory.getLogger(XmlToYamlTest.class);

    @ParameterizedTest
    @MethodSource("routes")
    @DisplayName("Test xml to yaml for <routes>")
    void testRoutes(String xml) throws Exception {
        try (InputStream is = new FileInputStream("../camel-xml-io/src/test/resources/" + xml)) {
            RoutesDefinition expected = new ModelParser(is, NAMESPACE).parseRoutesDefinition().get();
            assertNotNull(expected, "Parsed routes definition should not be null for " + xml);
            assertFalse(expected.getRoutes().isEmpty(), "Routes should not be empty for " + xml);
            YamlModelWriter writer = new YamlModelWriter();
            List<JsonObject> roots = new ArrayList<>();
            for (RouteDefinition route : expected.getRoutes()) {
                roots.add(writer.writeRouteDefinition(route));
            }
            String out = writer.printAsYaml(roots);
            assertNotNull(out, "YAML output should not be null for " + xml);
            assertFalse(out.isEmpty(), "YAML output should not be empty for " + xml);
            LOG.info("xml={}\n{}\n", xml, out);
        }
    }

    @Test
    void switchPreservesSelectorNamespacesAndCaseDetails() throws Exception {
        try (InputStream is = new FileInputStream("../camel-xml-io/src/test/resources/switch.xml")) {
            RoutesDefinition routes = new ModelParser(is, NAMESPACE).parseRoutesDefinition().get();
            YamlModelWriter writer = new YamlModelWriter();
            List<JsonObject> roots = new ArrayList<>();
            for (RouteDefinition route : routes.getRoutes()) {
                roots.add(writer.writeRouteDefinition(route));
            }
            JsonNode yaml = new YAMLMapper().readTree(writer.printAsYaml(roots));
            JsonNode sw = yaml.get(0).path("route").path("from").path("steps").get(0).path("switch");
            assertEquals("department", sw.path("selector").path("header").path("expression").asText());
            assertEquals("billingCase", sw.path("case").get(0).path("id").asText());
            assertEquals("direct:billing", sw.path("case").get(0).path("uri").asText());
            assertTrue(sw.path("case").get(2).path("value").isTextual());
            assertEquals("001", sw.path("case").get(2).path("value").asText());
            assertEquals("direct:review", sw.path("otherwise").path("uri").asText());
            JsonNode xpath = yaml.get(1).path("route").path("from").path("steps").get(0)
                    .path("switch").path("selector").path("xpath");
            assertEquals("string(/t:ticket/t:department)", xpath.path("expression").asText());
            assertEquals("java.lang.String", xpath.path("resultType").asText());
            assertEquals("urn:tickets", xpath.path("namespace").get(0).path("value").asText());
            assertEquals("t", xpath.path("namespace").get(0).path("key").asText());
        }
    }

    private static Stream<Arguments> routes() {
        return definitions("routes");
    }

    private static Stream<Arguments> definitions(String xml) {
        try {
            return Files.list(Paths.get("../camel-xml-io/src/test/resources"))
                    .filter(p -> {
                        try {
                            return Files.isRegularFile(p)
                                    && p.getFileName().toString().endsWith(".xml")
                                    && Files.readString(p).contains("<" + xml);
                        } catch (IOException e) {
                            throw new IOError(e);
                        }
                    })
                    .map(p -> p.getFileName().toString())
                    .flatMap(p -> Stream.of(Arguments.of(p, NAMESPACE)));
        } catch (IOException e) {
            throw new IOError(e);
        }
    }

}
