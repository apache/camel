package org.apache.camel.maven.packaging;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

import org.apache.camel.maven.packaging.dsl.component.ComponentDslGenerator;
import org.apache.camel.maven.packaging.model.ComponentModel;
import org.apache.camel.tooling.util.PackageHelper;
import org.junit.jupiter.api.Test;

class ComponentDslGeneratorTest {

    @Test
    public void testIfCreateJavaClassCorrectly() throws IOException {
        final String json = PackageHelper.loadText(new File(Objects.requireNonNull(getClass().getClassLoader().getResource("json/test_component.json")).getFile()));
        final ComponentModel componentModel = ComponentModel.generateComponentModelFromJsonString(json);

        final ComponentDslGenerator componentDslGenerator = ComponentDslGenerator.createDslJavaClassFromComponentModel(componentModel, getClass().getClassLoader());

        System.out.println(componentDslGenerator.toString());
    }
}