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
package org.apache.camel.model.rest;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.util.StringHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rest binding options that are not set on a verb, a rest or the rest configuration take the value of the
 * {@link RestConfiguration} of the CamelContext, so the default value the model documents (and the catalog, the XML and
 * YAML schemas and the route dumps derive from it) must be the default of {@link RestConfiguration}.
 */
public class RestBindingOptionsDefaultValueTest {

    private static final List<Class<?>> MODEL_CLASSES = List.of(
            RestConfigurationDefinition.class, RestDefinition.class, VerbDefinition.class, RestBindingDefinition.class);

    private static final List<String> OPTIONS = List.of(
            "bindingMode", "skipBindingOnErrorCode", "clientRequestValidation", "clientResponseValidation", "enableCORS",
            "enableNoContentResponse");

    @Test
    public void documentedDefaultIsTheRuntimeDefault() throws Exception {
        RestConfiguration runtime = new RestConfiguration();
        List<String> mismatches = new ArrayList<>();
        for (String option : OPTIONS) {
            String expected = runtimeDefault(runtime, option);
            for (Class<?> model : MODEL_CLASSES) {
                Field field = model.getDeclaredField(option);
                Metadata metadata = field.getAnnotation(Metadata.class);
                String actual = metadata != null ? metadata.defaultValue() : "";
                if (!expected.equals(actual)) {
                    mismatches.add(model.getSimpleName() + "." + option + " documents the default '" + actual
                                   + "' but RestConfiguration defaults to '" + expected + "'");
                }
            }
        }
        assertTrue(mismatches.isEmpty(), String.join("\n", mismatches));
    }

    private static String runtimeDefault(RestConfiguration runtime, String option) throws Exception {
        String suffix = StringHelper.capitalize(option);
        Method getter;
        try {
            getter = RestConfiguration.class.getMethod("is" + suffix);
        } catch (NoSuchMethodException e) {
            getter = RestConfiguration.class.getMethod("get" + suffix);
        }
        return String.valueOf(getter.invoke(runtime));
    }
}
