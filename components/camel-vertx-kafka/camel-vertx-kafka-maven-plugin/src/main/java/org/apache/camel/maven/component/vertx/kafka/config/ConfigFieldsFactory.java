package org.apache.camel.maven.component.vertx.kafka.config;

import java.util.Map;
import java.util.Set;

import org.apache.camel.util.ObjectHelper;
import org.apache.kafka.common.config.ConfigDef;

public class ConfigFieldsFactory {

    private static final String[] ILLEGAL_CHARS = { "%", "+", "[", "]", "*", "(", ")", "ˆ", "@", "%", "~" };

    private ConfigFieldsFactory() {
    }

    /*public static Map<String, ConfigField> createFieldsAsMap(final ConfigDef consumerConfigs, final ConfigDef producerConfigs,
                                                             final Set<String> requiredFields, final Set<String> deprecatedFields,
                                                             final Set<String> skippedFields, final Map<String, Object> overridenDefaultValues) {
        ObjectHelper.notNull(producerConfigs, "producerConfigs");
        ObjectHelper.notNull(consumerConfigs, "consumerConfigs");
        ObjectHelper.notNull(deprecatedFields, "deprecatedFields");
        ObjectHelper.notNull(requiredFields, "requiredFields");
        ObjectHelper.notNull(skippedFields, "skippedFields");
        ObjectHelper.notNull(overridenDefaultValues, "overridenDefaultValues");

    }*/
}
