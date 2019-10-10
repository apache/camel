package org.apache.camel.maven;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.debezium.config.CommonConnectorConfig;
import io.debezium.config.Field;
import org.apache.camel.util.ObjectHelper;
import org.apache.kafka.common.config.ConfigDef;

public class ConnectorConfigFieldFactory {

    private ConnectorConfigFieldFactory() {
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
        if (isConfigClassValid(configClass)) {
            return Stream.of(configClass.getDeclaredFields())
                    .filter(field -> field.getAnnotation(Deprecated.class) != null)
                    .map(ConnectorConfigFieldFactory::retrieveDbzFieldWithReflection)
                    .collect(Collectors.toSet());
        }
        throw new IllegalArgumentException(String.format("Class '%s' is not valid Debezium configuration class", configClass.getName()));
    }

    private static boolean isConfigClassValid(final Class<?> configClass) {
        // config class should be a subtype of CommonConnectorConfig
        Class<?> clazz = configClass;
        while (clazz != null) {
            if (clazz == CommonConnectorConfig.class) {
                return true;
            }
            clazz = clazz.getSuperclass();
        }
        return false;
    }

    private static String retrieveDbzFieldWithReflection(final java.lang.reflect.Field reflectionField) {
        try {
            return ((Field) reflectionField.get(null)).name();
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException("Error occurred in field : " + reflectionField.getName());
        }
    }
}
