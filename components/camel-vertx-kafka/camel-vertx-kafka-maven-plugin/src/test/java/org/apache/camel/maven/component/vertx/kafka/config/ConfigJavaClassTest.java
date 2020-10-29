package org.apache.camel.maven.component.vertx.kafka.config;

import org.apache.kafka.common.config.ConfigDef;
import org.junit.jupiter.api.Test;

class ConfigJavaClassTest {

    @Test
    void generate() {
        final ConfigDef consumerConfigDef = new ConfigDef()
                .define("consumer.test.field.1", ConfigDef.Type.STRING, ConfigDef.Importance.MEDIUM, "docs1")
                .define("consumer.test.field.2", ConfigDef.Type.CLASS, ConfigDef.Importance.MEDIUM, "docs2")
                .define("consumer.test.field.3", ConfigDef.Type.PASSWORD, ConfigDef.Importance.MEDIUM, "doc3")
                .define("consumer.test.field.4", ConfigDef.Type.INT, ConfigDef.Importance.MEDIUM, "doc4")
                .define("consumer.test.field.5", ConfigDef.Type.INT, ConfigDef.Importance.MEDIUM, "doc5");

        final ConfigDef producerConfigDef = new ConfigDef()
                .define("producer.test.field.1", ConfigDef.Type.STRING, ConfigDef.Importance.MEDIUM, "docs1")
                .define("producer.test.field.2", ConfigDef.Type.CLASS, ConfigDef.Importance.MEDIUM, "docs2");

        final ConfigDef commonConfigDef = new ConfigDef()
                .define("common.test.field.1", ConfigDef.Type.STRING, ConfigDef.Importance.MEDIUM, "docs1")
                .define("common.test.field.2", ConfigDef.Type.CLASS, ConfigDef.Importance.MEDIUM, "docs2");

        final String packageName = "org.apache.camel.test.config";
        final String className = "UnitTestConfiguration";

        final ConfigJavaClass javaClass = ConfigJavaClass.builder()
                .withClassName(className)
                .withPackageName(packageName)
                .withCommonConfigs(commonConfigDef.configKeys())
                .withProducerConfigs(producerConfigDef.configKeys())
                .withConsumerConfigs(consumerConfigDef.configKeys())
                .build();

    }
}
