package org.apache.camel.maven.packaging.dsl.component;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

import org.apache.camel.maven.packaging.model.ComponentModel;
import org.apache.camel.tooling.util.PackageHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ComponentDslBuilderFactoryGeneratorTest {

    @Test
    public void testIfCreateJavaClassCorrectly() throws IOException {
        final String json = PackageHelper.loadText(new File(Objects.requireNonNull(getClass().getClassLoader().getResource("json/test_component.json")).getFile()));
        final ComponentModel componentModel = ComponentModel.generateComponentModelFromJsonString(json);

        final ComponentDslBuilderFactoryGenerator componentDslBuilderFactoryGenerator = ComponentDslBuilderFactoryGenerator.generateClass(componentModel, getClass().getClassLoader(), "org.apache.camel.builder.component.dsl");

        assertEquals("KafkaComponentBuilderFactory", componentDslBuilderFactoryGenerator.getGeneratedClassName());

        // assert the content of the generated class
        final String classCode = componentDslBuilderFactoryGenerator.printClassAsString();
        assertTrue(classCode.contains("public interface KafkaComponentBuilderFactory"));

        assertTrue(classCode.contains(" static KafkaComponentBuilder kafka()"));
    }

}