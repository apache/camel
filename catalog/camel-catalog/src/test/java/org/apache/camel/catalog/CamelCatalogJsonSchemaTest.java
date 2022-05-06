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
package org.apache.camel.catalog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CamelCatalogJsonSchemaTest {

    private static final Logger LOG = LoggerFactory.getLogger(CamelCatalogJsonSchemaTest.class);

    private CamelCatalog catalog = new DefaultCamelCatalog();

    @Test
    public void testValidateJsonComponent() throws Exception {
        for (String name : catalog.findComponentNames()) {
            String json = catalog.componentJSonSchema(name);
            LOG.info("Validating {} component", name);
            LOG.debug("with JSon: {}", json);

            // validate we can parse the json
            ObjectMapper mapper = new ObjectMapper();
            JsonNode tree = mapper.readTree(json);
            assertNotNull(tree);

            assertTrue(tree.has("component"), name);
            assertTrue(tree.has("componentProperties"), name);
            assertTrue(tree.has("properties"), name);

            validateComponentSyntax(name, tree);
        }
    }

    private void validateComponentSyntax(String name, JsonNode tree) {
        String syntax = tree.get("component").get("syntax").textValue();
        assertFalse(syntax.isEmpty(), "Empty syntax for component " + name);
        List<String> pathProperties = new ArrayList<>();
        List<String> requiredProperties = new ArrayList<>();

        Iterator<Map.Entry<String, JsonNode>> it = tree.get("properties").fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> property = it.next();
            if ("path".equals(property.getValue().get("kind").textValue())) {
                pathProperties.add(property.getKey());
                if (property.getValue().get("required").booleanValue()) {
                    requiredProperties.add(property.getKey());
                }
            }
        }
        // special for jt400
        List<String> syntaxParts;
        if ("jt400".equals(name)) {
            syntaxParts = new ArrayList<>();
            syntaxParts.add("jt400");
            syntaxParts.add("userID");
            syntaxParts.add("password");
            syntaxParts.add("systemName");
            syntaxParts.add("objectPath");
            syntaxParts.add("type");
        } else {
            syntaxParts = Arrays.asList(syntax.split("[/:#.]"));
        }
        assertEquals(name, syntaxParts.get(0), "Syntax must start with component name");

        for (String part : syntaxParts.subList(1, syntaxParts.size())) {
            if (!part.isEmpty()) {
                assertTrue(pathProperties.contains(part),
                        String.format("Component %s. Syntax %s. Part %s is not defined as UriPath", name, syntax, part));
            }
        }

        for (String requiredPart : requiredProperties) {
            assertTrue(syntaxParts.contains(requiredPart), String
                    .format("Component %s. Syntax %s. Required param %s is not defined in syntax", name, syntax, requiredPart));
        }
    }

    @Test
    public void testValidateJsonDataFormats() throws Exception {
        for (String name : catalog.findDataFormatNames()) {
            String json = catalog.dataFormatJSonSchema(name);
            LOG.info("Validating {} dataformat", name);
            LOG.debug("with JSon: {}", json);

            // validate we can parse the json
            ObjectMapper mapper = new ObjectMapper();
            JsonNode tree = mapper.readTree(json);
            assertNotNull(tree);

            assertTrue(tree.has("dataformat"), name);
            assertTrue(tree.has("properties"), name);
        }
    }

    @Test
    public void testValidateJsonLanguages() throws Exception {
        for (String name : catalog.findLanguageNames()) {
            String json = catalog.languageJSonSchema(name);
            LOG.info("Validating {} language", name);
            LOG.debug("with JSon: {}", json);

            // validate we can parse the json
            ObjectMapper mapper = new ObjectMapper();
            JsonNode tree = mapper.readTree(json);
            assertNotNull(tree);

            assertTrue(tree.has("language"), name);
            assertTrue(tree.has("properties"), name);
        }
    }

    @Test
    public void testValidateJsonModels() throws Exception {
        for (String name : catalog.findModelNames()) {
            String json = catalog.modelJSonSchema(name);
            LOG.info("Validating {} model", name);
            LOG.debug("with JSon: {}", json);

            // validate we can parse the json
            ObjectMapper mapper = new ObjectMapper();
            JsonNode tree = mapper.readTree(json);
            assertNotNull(tree);

            assertTrue(tree.has("model"), name);
            assertTrue(tree.has("properties"), name);
        }
    }
}
