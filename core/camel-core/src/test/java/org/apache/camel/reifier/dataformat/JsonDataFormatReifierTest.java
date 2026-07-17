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
package org.apache.camel.reifier.dataformat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.dataformat.JsonDataFormat;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JsonDataFormatReifierTest {

    private static final Set<String> JACKSON_ONLY_OPTIONS = Set.of(
            "objectMapper", "useDefaultObjectMapper", "autoDiscoverObjectMapper",
            "include", "allowJmsType", "collectionType", "useList",
            "combineUnicodeSurrogates", "moduleClassNames", "moduleRefs",
            "enableFeatures", "disableFeatures", "allowUnmarshallType",
            "schemaResolver", "autoDiscoverSchemaResolver", "namingStrategy",
            "timezone", "maxStringLength");

    @Test
    public void testGsonDoesNotIncludeJacksonOnlyOptions() {
        assertNoJacksonOptions(JsonLibrary.Gson);
    }

    @Test
    public void testFastjsonDoesNotIncludeJacksonOnlyOptions() {
        assertNoJacksonOptions(JsonLibrary.Fastjson);
    }

    @Test
    public void testJsonbDoesNotIncludeJacksonOnlyOptions() {
        Map<String, Object> properties = getProperties(JsonLibrary.Jsonb);
        // Jsonb supports objectMapper, so only check the rest
        Set<String> jsonbExcluded = Set.of(
                "useDefaultObjectMapper", "autoDiscoverObjectMapper",
                "include", "allowJmsType", "collectionType", "useList",
                "combineUnicodeSurrogates", "moduleClassNames", "moduleRefs",
                "enableFeatures", "disableFeatures", "allowUnmarshallType",
                "schemaResolver", "autoDiscoverSchemaResolver", "namingStrategy",
                "timezone", "maxStringLength");
        for (String option : jsonbExcluded) {
            assertFalse(properties.containsKey(option),
                    "Jsonb should not include Jackson-only option: " + option);
        }
        assertTrue(properties.containsKey("objectMapper"),
                "Jsonb should include objectMapper");
    }

    @Test
    public void testJacksonIncludesAllOptions() {
        Map<String, Object> properties = getProperties(JsonLibrary.Jackson);
        for (String option : JACKSON_ONLY_OPTIONS) {
            assertTrue(properties.containsKey(option),
                    "Jackson should include option: " + option);
        }
    }

    private void assertNoJacksonOptions(JsonLibrary library) {
        Map<String, Object> properties = getProperties(library);
        for (String option : JACKSON_ONLY_OPTIONS) {
            assertFalse(properties.containsKey(option),
                    library.name() + " should not include Jackson-only option: " + option);
        }
    }

    private Map<String, Object> getProperties(JsonLibrary library) {
        DefaultCamelContext context = new DefaultCamelContext();
        JsonDataFormat definition = new JsonDataFormat();
        definition.setLibrary(library);
        JsonDataFormatReifier reifier = new JsonDataFormatReifier(context, definition);
        Map<String, Object> properties = new LinkedHashMap<>();
        reifier.prepareDataFormatConfig(properties);
        return properties;
    }
}
