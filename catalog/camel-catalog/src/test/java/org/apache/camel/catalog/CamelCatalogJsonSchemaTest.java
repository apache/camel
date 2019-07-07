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
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.*;

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

            assertTrue(name, tree.has("component"));
            assertTrue(name, tree.has("componentProperties"));
            assertTrue(name, tree.has("properties"));

            validateComponentSyntax(name, tree);
        }
    }

    private void validateComponentSyntax(String name, JsonNode tree) {
        String syntax = tree.get("component").get("syntax").textValue();
        assertFalse("Empty syntax for component " + name, syntax.isEmpty());
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
        List<String> syntaxParts = Arrays.asList(syntax.split("[/:#.]"));
        Assert.assertEquals("Syntax must start with component name", name, syntaxParts.get(0));

        for (String part : syntaxParts.subList(1, syntaxParts.size())) {
            if (!part.isEmpty()) {
                Assert.assertTrue(String.format("Component %s. Syntax %s. Part %s is not defined as UriPath", name, syntax, part), pathProperties.contains(part));
            }
        }

        for (String requiredPart : requiredProperties) {
            Assert.assertTrue(String.format("Component %s. Syntax %s. Required param %s is not defined in syntax", name, syntax, requiredPart), syntaxParts.contains(requiredPart));
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

            assertTrue(name, tree.has("dataformat"));
            assertTrue(name, tree.has("properties"));
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

            assertTrue(name, tree.has("language"));
            assertTrue(name, tree.has("properties"));
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

            assertTrue(name, tree.has("model"));
            assertTrue(name, tree.has("properties"));
        }
    }
}
