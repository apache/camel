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

    // the options of the Jackson data format; another library gets only the ones it supports
    private static final Set<String> JACKSON_OPTIONS = Set.of(
            "objectMapper", "useDefaultObjectMapper", "autoDiscoverObjectMapper",
            "include", "allowJmsType", "collectionType", "useList",
            "combineUnicodeSurrogates", "moduleClassNames", "moduleRefs",
            "enableFeatures", "disableFeatures", "allowUnmarshallType",
            "schemaResolver", "autoDiscoverSchemaResolver", "namingStrategy",
            "timezone", "maxStringLength");

    @Test
    public void testGsonDoesNotIncludeJacksonOnlyOptions() {
        // Gson supports allowUnmarshallType
        assertJacksonOptions(JsonLibrary.Gson, Set.of("allowUnmarshallType"));
    }

    @Test
    public void testFastjsonDoesNotIncludeJacksonOnlyOptions() {
        assertJacksonOptions(JsonLibrary.Fastjson, Set.of());
    }

    @Test
    public void testJsonbDoesNotIncludeJacksonOnlyOptions() {
        // Jsonb supports objectMapper and allowUnmarshallType
        assertJacksonOptions(JsonLibrary.Jsonb, Set.of("objectMapper", "allowUnmarshallType"));
    }

    @Test
    public void testJacksonIncludesAllOptions() {
        assertJacksonOptions(JsonLibrary.Jackson, JACKSON_OPTIONS);
    }

    /**
     * Asserts that the library gets the Jackson options it supports, and none of the others.
     */
    private void assertJacksonOptions(JsonLibrary library, Set<String> supported) {
        Map<String, Object> properties = getProperties(library);
        for (String option : JACKSON_OPTIONS) {
            if (supported.contains(option)) {
                assertTrue(properties.containsKey(option),
                        library.name() + " should include option: " + option);
            } else {
                assertFalse(properties.containsKey(option),
                        library.name() + " should not include option: " + option);
            }
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
