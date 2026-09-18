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
package org.apache.camel.model;

import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.camel.spi.Metadata;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the generated model JSON stays consistent with the {@link Metadata} annotations it reflects.
 * <p/>
 * {@link RouteDefinition} uses {@code XmlAccessType.PROPERTY}, so its options are not discovered by the generic field
 * scanning in {@code SchemaGeneratorMojo}. They are instead hard-coded in {@code SchemaGeneratorMojo.processRoute}, and
 * can therefore silently drift from the annotations on the setter methods.
 */
public class RouteDefinitionMetadataTest {

    private static final String MODEL_DIR = "/META-INF/org/apache/camel/model";

    /**
     * Options where the generator deliberately assigns a label that the setter does not declare.
     */
    private static final Set<String> LABEL_EXCLUSIONS = Set.of("errorHandlerRef", "errorHandler");

    @Test
    public void routeOptionLabelsMatchMetadataAnnotations() throws Exception {
        Map<String, JsonObject> options = options(loadModel(modelDir().resolve("route.json")));

        List<String> mismatches = new ArrayList<>();
        for (Map.Entry<String, JsonObject> entry : options.entrySet()) {
            String name = entry.getKey();
            if (LABEL_EXCLUSIONS.contains(name)) {
                continue;
            }
            Metadata metadata = findSetterMetadata(name);
            if (metadata == null) {
                // no annotation to compare against
                continue;
            }
            String expected = metadata.label();
            String actual = entry.getValue().getStringOrDefault("label", "");
            if (!expected.equals(actual)) {
                mismatches.add(name + ": route.json has label '" + actual + "' but the setter declares '" + expected + "'");
            }
        }

        Assertions.assertTrue(mismatches.isEmpty(),
                "Labels in route.json drifted from the @Metadata annotations on RouteDefinition."
                                                    + " Fix SchemaGeneratorMojo.processRoute and regenerate:\n"
                                                    + String.join("\n", mismatches));
    }

    /**
     * {@code processRoute} builds its options with a long positional {@code createOption} call in which
     * {@code defaultValue} and {@code label} are adjacent String arguments. Swapping the two is not a compile error -
     * it shipped for two years as {@code "delayer": { "defaultValue": "advanced" }} - so guard against it across the
     * whole model.
     */
    @Test
    public void noLabelIsLeakedIntoDefaultValue() throws Exception {
        Map<Path, Map<String, JsonObject>> models = new LinkedHashMap<>();
        try (Stream<Path> files = Files.walk(modelDir())) {
            for (Path file : files.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList()) {
                models.put(file, options(loadModel(file)));
            }
        }
        Assertions.assertFalse(models.isEmpty(), "No model JSON files found on the classpath");

        Set<String> labels = new LinkedHashSet<>();
        for (Map<String, JsonObject> options : models.values()) {
            for (JsonObject option : options.values()) {
                for (String label : option.getStringOrDefault("label", "").split(",")) {
                    if (!label.isBlank()) {
                        labels.add(label.trim());
                    }
                }
            }
        }

        List<String> violations = new ArrayList<>();
        for (Map.Entry<Path, Map<String, JsonObject>> model : models.entrySet()) {
            for (Map.Entry<String, JsonObject> entry : model.getValue().entrySet()) {
                JsonObject option = entry.getValue();
                Object defaultValue = option.get("defaultValue");
                String type = option.getStringOrDefault("type", "");
                // a string or enum option may legitimately default to a word that is also used as a label
                boolean freeText = "string".equals(type) || "enum".equals(type)
                        || "object".equals(type) || "array".equals(type);
                if (!freeText && defaultValue instanceof String && labels.contains(defaultValue)) {
                    violations.add(model.getKey().getFileName() + " option '" + entry.getKey() + "' (type " + type
                                   + ") has defaultValue '" + defaultValue + "' which is a label name");
                }
            }
        }

        Assertions.assertTrue(violations.isEmpty(),
                "A label was passed as the defaultValue argument of createOption:\n" + String.join("\n", violations));
    }

    private static Metadata findSetterMetadata(String option) {
        String setter = "set" + Character.toUpperCase(option.charAt(0)) + option.substring(1);
        for (Method method : RouteDefinition.class.getMethods()) {
            if (method.getName().equals(setter) && method.getParameterCount() == 1) {
                Metadata metadata = method.getAnnotation(Metadata.class);
                if (metadata != null) {
                    return metadata;
                }
            }
        }
        return null;
    }

    private static Map<String, JsonObject> options(JsonObject model) {
        Map<String, JsonObject> answer = new LinkedHashMap<>();
        Map<String, Object> properties = model.getMap("properties");
        if (properties != null) {
            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                answer.put(entry.getKey(), new JsonObject((Map<String, ?>) entry.getValue()));
            }
        }
        return answer;
    }

    private static Path modelDir() throws Exception {
        URL url = RouteDefinitionMetadataTest.class.getResource(MODEL_DIR + "/route.json");
        Assertions.assertNotNull(url, "Cannot find route.json on the classpath");
        return Paths.get(url.toURI()).getParent();
    }

    private static JsonObject loadModel(Path path) throws Exception {
        return (JsonObject) Jsoner.deserialize(Files.readString(path, StandardCharsets.UTF_8));
    }
}
