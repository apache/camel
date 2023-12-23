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

package org.apache.camel.dsl.jbang.core.commands.k;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.v1.integrationspec.Traits;

/**
 * Utility class manages trait expressions and its conversion to proper trait model.
 */
public final class TraitHelper {

    private TraitHelper() {
        //prevent instantiation of utility class.
    }

    /**
     * Parses given list of trait expressions to proper trait model object.
     *
     * @param  traits
     * @return
     */
    public static Traits parseTraits(String[] traits) {
        try {
            Map<String, Map<String, Object>> traitJson = new HashMap<>();

            for (String traitExpression : traits) {
                //traitName.key=value
                final String[] trait = traitExpression.split("\\.", 2);
                final String[] traitConfig = trait[1].split("=", 2);

                final String traitKey = traitConfig[0];
                final Object traitValue = resolveTraitValue(traitKey, traitConfig[1].trim());
                if (traitJson.containsKey(trait[0])) {
                    Map<String, Object> config = traitJson.get(trait[0]);

                    if (config.containsKey(traitKey)) {
                        Object existingValue = config.get(traitKey);

                        if (existingValue instanceof List) {
                            List<String> values = (List<String>) existingValue;
                            values.add(traitValue.toString());
                        } else {
                            config.put(traitKey, Arrays.asList(existingValue.toString(), traitValue));
                        }
                    } else {
                        config.put(traitKey, initializeTraitValue(traitValue));
                    }
                } else {
                    Map<String, Object> props = new HashMap<>();
                    props.put(traitKey, initializeTraitValue(traitValue));
                    traitJson.put(trait[0], props);
                }
            }

            return KubernetesHelper.json().readerFor(Traits.class).readValue(
                    KubernetesHelper.json().writeValueAsString(traitJson));
        } catch (IOException e) {
            throw new RuntimeCamelException("Failed to parse trait options", e);
        }
    }

    /**
     * Resolve trait value with automatic type conversion. Some trait keys (like enabled, verbose) need to be converted
     * to boolean type.
     *
     * @param  traitKey
     * @param  value
     * @return
     */
    private static Object resolveTraitValue(String traitKey, String value) {
        if (value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }

        if (value.startsWith("'") && value.endsWith("'")) {
            return value.substring(1, value.length() - 1);
        }

        if (traitKey.equalsIgnoreCase("enabled") ||
                traitKey.equalsIgnoreCase("verbose")) {
            return Boolean.valueOf(value);
        }

        return value;
    }

    /**
     * Initialize trait value with support for array type values.
     *
     * @param  value
     * @return
     */
    private static Object initializeTraitValue(Object value) {
        if (value instanceof String && value.toString().startsWith("[") && value.toString().endsWith("]")) {
            List<String> values = new ArrayList<>();
            values.add(resolveTraitValue("", value.toString().substring(1, value.toString().length() - 1)).toString());
            return values;
        }

        return value;
    }
}
