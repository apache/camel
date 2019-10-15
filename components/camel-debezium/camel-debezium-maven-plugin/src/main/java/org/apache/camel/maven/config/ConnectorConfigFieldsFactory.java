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
package org.apache.camel.maven.config;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.debezium.config.Field;
import org.apache.camel.util.ObjectHelper;
import org.apache.kafka.common.config.ConfigDef;

public final class ConnectorConfigFieldsFactory {

    private ConnectorConfigFieldsFactory() {
    }

    public static Map<String, ConnectorConfigField> createConnectorFieldsAsMap(final ConfigDef configDef, final Class<?> configClass, final Set<String> requiredFields, final Map<String, Object> overridenDefaultValues) {
        // first we extract deprecated fields
        final Set<String> deprecatedFields = getDeprecatedFieldsFromConfigClass(configClass);

        return createConnectorFieldsAsMap(configDef, deprecatedFields, requiredFields, overridenDefaultValues);
    }

    public static Map<String, ConnectorConfigField> createConnectorFieldsAsMap(final ConfigDef configDef, final Set<String> deprecatedFields, final Set<String> requiredFields, final Map<String, Object> overridenDefaultValues) {
        ObjectHelper.notNull(configDef, "configDef");
        ObjectHelper.notNull(deprecatedFields, "deprecatedFields");
        ObjectHelper.notNull(requiredFields, "requiredFields");
        ObjectHelper.notNull(overridenDefaultValues, "overridenDefaultValues");

        final Map<String, ConnectorConfigField> results = new HashMap<>();

        // create our map for fields
        configDef.configKeys().forEach((name, configKey) -> {
            results.put(name, new ConnectorConfigField(configKey, deprecatedFields.contains(name), requiredFields.contains(name), overridenDefaultValues.getOrDefault(name, null)));
        });

        return results;
    }

    private static Set<String> getDeprecatedFieldsFromConfigClass(final Class<?> configClass) {
        return Stream.of(configClass.getDeclaredFields())
                .filter(field -> field.getAnnotation(Deprecated.class) != null)
                .map(ConnectorConfigFieldsFactory::retrieveDbzFieldWithReflection)
                .collect(Collectors.toSet());
    }

    private static String retrieveDbzFieldWithReflection(final java.lang.reflect.Field reflectionField) {
        try {
            return ((Field) reflectionField.get(null)).name();
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException("Error occurred in field : " + reflectionField.getName());
        }
    }
}
