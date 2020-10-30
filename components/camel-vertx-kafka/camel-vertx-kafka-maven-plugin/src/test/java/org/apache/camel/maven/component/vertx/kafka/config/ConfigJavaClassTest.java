package org.apache.camel.maven.component.vertx.kafka.config;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.apache.kafka.common.config.ConfigDef;
import org.junit.jupiter.api.Test;

class ConfigJavaClassTest {

    @Test
    void generate() {
        final ConfigDef consumerConfigDef = new ConfigDef()
                .define("consumer.test.field.1", ConfigDef.Type.STRING, ConfigDef.Importance.MEDIUM, "docs1")
                .define("consumer.test.field.2", ConfigDef.Type.CLASS, ConfigDef.Importance.MEDIUM, "docs2")
                .define("consumer.test.field.3", ConfigDef.Type.PASSWORD, ConfigDef.Importance.MEDIUM, "doc3")
                .define("consumer.test.field.4", ConfigDef.Type.INT, 10, ConfigDef.Importance.MEDIUM, "doc4")
                .define("consumer.test.field.5", ConfigDef.Type.INT, ConfigDef.Importance.MEDIUM, "doc5")
                .define("consumer.test.field.6", ConfigDef.Type.STRING, "default field 6", ConfigDef.Importance.MEDIUM,
                        "docs6");

        final List<String> valuesProducer = new LinkedList<>();
        valuesProducer.add("test-1");
        valuesProducer.add("test-2");
        valuesProducer.add("test-3");

        final ConfigDef producerConfigDef = new ConfigDef()
                .define("producer.test.field.1", ConfigDef.Type.STRING, "default value", ConfigDef.Importance.MEDIUM, "docs1")
                .define("producer.test.field.2", ConfigDef.Type.CLASS, ConfigDef.Importance.MEDIUM, "docs2")
                .define("producer.test.field.3", ConfigDef.Type.LIST, valuesProducer, ConfigDef.Importance.MEDIUM, "docs2");

        final ConfigDef commonConfigDef = new ConfigDef()
                .define("common.test.field.1", ConfigDef.Type.STRING, "default",
                        ConfigDef.ValidString.in("default", "default 2"), ConfigDef.Importance.MEDIUM, "docs1")
                .define("common.test.field.2", ConfigDef.Type.CLASS, ConfigDef.Importance.MEDIUM, "docs2")
                .define("common.test.field.3", ConfigDef.Type.LIST, Collections.emptyList(), ConfigDef.Importance.MEDIUM,
                        "docs2");

        final String packageName = "org.apache.camel.test.config";
        final String className = "UnitTestConfiguration";

        final ConfigJavaClass javaClass = ConfigJavaClass.builder()
                .withClassName(className)
                .withPackageName(packageName)
                .withCommonConfigs(commonConfigDef.configKeys())
                .withProducerConfigs(producerConfigDef.configKeys())
                .build();

        System.out.println(javaClass.printClassAsString());
    }
}
