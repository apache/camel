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
package org.apache.camel.maven.component.vertx.kafka.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.util.ObjectHelper;
import org.apache.commons.io.IOUtils;
import org.apache.kafka.common.config.ConfigDef;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigJavaClassTest {

    @Test
    void testIfCorrectlyGeneratedFile() throws IOException {
        final ConfigDef consumerConfigDef = new ConfigDef()
                .define("consumer.test.field.1", ConfigDef.Type.STRING, ConfigDef.Importance.MEDIUM, "docs1")
                .define("consumer.test.field.2", ConfigDef.Type.CLASS, ConfigDef.Importance.MEDIUM, "docs2")
                .define("consumer.test.field.3", ConfigDef.Type.PASSWORD, ConfigDef.Importance.MEDIUM, "doc3")
                .define("consumer.test.field.4", ConfigDef.Type.INT, 10, ConfigDef.Importance.MEDIUM, "doc4")
                .define("consumer.test.field.5", ConfigDef.Type.INT, ConfigDef.Importance.MEDIUM, "doc5")
                .define("consumer.test.field.6", ConfigDef.Type.STRING, "default field 6", ConfigDef.Importance.MEDIUM,
                        "docs6")
                .define("common.test.field.1", ConfigDef.Type.STRING, "default",
                        ConfigDef.ValidString.in("default", "default 2"), ConfigDef.Importance.MEDIUM, "docs1")
                .define("common.test.field.2", ConfigDef.Type.CLASS, ConfigDef.Importance.MEDIUM, "docs2")
                .define("common.test.field.3", ConfigDef.Type.LIST, Collections.emptyList(), ConfigDef.Importance.MEDIUM,
                        "docs2");

        final List<String> valuesProducer = new LinkedList<>();
        valuesProducer.add("test-1");
        valuesProducer.add("test-2");
        valuesProducer.add("test-3");

        final ConfigDef producerConfigDef = new ConfigDef()
                .define("producer.test.field.1", ConfigDef.Type.STRING, "default value", ConfigDef.Importance.MEDIUM, "docs1")
                .define("producer.test.field.2", ConfigDef.Type.CLASS, ConfigDef.Importance.MEDIUM, "docs2")
                .define("producer.test.field.3", ConfigDef.Type.LIST, valuesProducer, ConfigDef.Importance.MEDIUM, "docs2")
                .define("common.test.field.1", ConfigDef.Type.STRING, "default",
                        ConfigDef.ValidString.in("default", "default 2"), ConfigDef.Importance.MEDIUM, "docs1")
                .define("common.test.field.2", ConfigDef.Type.CLASS, ConfigDef.Importance.MEDIUM, "docs2")
                .define("common.test.field.3", ConfigDef.Type.LIST, Collections.emptyList(), ConfigDef.Importance.MEDIUM,
                        "docs2");

        final Set<String> requiredConfigsAndUriPathConfigs = new HashSet<>();
        requiredConfigsAndUriPathConfigs.add("topic.config.1");
        requiredConfigsAndUriPathConfigs.add("topic.config.2");

        final Set<String> skippedFields = new HashSet<>();
        skippedFields.add("consumer.test.field.6");

        final Map<String, ConfigField> consumerConfigs = new ConfigFieldsBuilder()
                .fromConfigKeys(consumerConfigDef.configKeys())
                .addConfig(ConfigField.withName("topic.config.1").withType(ConfigDef.Type.STRING)
                        .withDefaultValue("default value").withDocumentation("docs1").build())
                .addConfig(ConfigField.withName("topic.config.2").withType(ConfigDef.Type.STRING)
                        .withDefaultValue("default value").withDocumentation("docs1").build())
                .setSkippedFields(skippedFields)
                .setRequiredFields(requiredConfigsAndUriPathConfigs)
                .setUriPathFields(requiredConfigsAndUriPathConfigs)
                .build();

        final Map<String, ConfigField> producerConfigs = new ConfigFieldsBuilder()
                .fromConfigKeys(producerConfigDef.configKeys())
                .addConfig(ConfigField.withName("topic.config.1").withType(ConfigDef.Type.STRING)
                        .withDefaultValue("default value").withDocumentation("docs1").isUriPathOption().isRequired().build())
                .addConfig(ConfigField.withName("topic.config.2").withType(ConfigDef.Type.STRING)
                        .withDefaultValue("default value").withDocumentation("docs1").isUriPathOption()
                        .withLabels("test_label_1", "test_label_2").isRequired().build())
                .setSkippedFields(skippedFields)
                .setRequiredFields(requiredConfigsAndUriPathConfigs)
                .setUriPathFields(requiredConfigsAndUriPathConfigs)
                .build();

        final String packageName = "org.apache.camel.maven.component.vertx.kafka.config";
        final String className = "UnitTestConfiguration";

        final ConfigJavaClass javaClass = ConfigJavaClass.builder()
                .withClassName(className)
                .withPackageName(packageName)
                .withConsumerConfigs(consumerConfigs)
                .withProducerConfigs(producerConfigs)
                .build();

        final String javaClassAsText = javaClass.printClassAsString();

        final String expectedClass
                = IOUtils.toString(ObjectHelper.loadResourceAsStream("UnitTestConfiguration.java.txt"), StandardCharsets.UTF_8);

        assertEquals(expectedClass, javaClassAsText);
    }
}
