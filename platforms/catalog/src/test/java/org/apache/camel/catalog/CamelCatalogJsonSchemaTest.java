/**
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class CamelCatalogJsonSchemaTest {

    private CamelCatalog catalog = new DefaultCamelCatalog();

    @Test
    public void testValidateJsonComponent() throws Exception {
        for (String name : catalog.findComponentNames()) {
            String json = catalog.componentJSonSchema(name);

            // validate we can parse the json
            ObjectMapper mapper = new ObjectMapper();
            JsonNode tree = mapper.readTree(json);
            assertNotNull(tree);

            assertTrue(name, tree.has("component"));
            assertTrue(name, tree.has("componentProperties"));
            assertTrue(name, tree.has("properties"));
        }
    }

    @Test
    public void testValidateJsonDataFormats() throws Exception {
        for (String name : catalog.findDataFormatNames()) {
            String json = catalog.dataFormatJSonSchema(name);

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

            // validate we can parse the json
            ObjectMapper mapper = new ObjectMapper();
            JsonNode tree = mapper.readTree(json);
            assertNotNull(tree);

            assertTrue(name, tree.has("model"));
            assertTrue(name, tree.has("properties"));
        }
    }
}
