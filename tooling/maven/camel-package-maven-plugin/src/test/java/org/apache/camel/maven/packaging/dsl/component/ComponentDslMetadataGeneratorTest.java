package org.apache.camel.maven.packaging.dsl.component;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

import org.apache.camel.maven.packaging.model.ComponentModel;
import org.apache.camel.tooling.util.PackageHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ComponentDslMetadataGeneratorTest {

    @Test
    public void testJson() throws IOException {
        final String json = PackageHelper.loadText(new File(Objects.requireNonNull(getClass().getClassLoader().getResource("json/test_component.json")).getFile()));

        final ComponentModel componentModel = ComponentModel.generateComponentModelFromJsonString(json);

        /*final ComponentDslMetadataGenerator componentDslMetadataGenerator = new ComponentDslMetadataGenerator(new File(""), new File(Objects.requireNonNull(getClass().getClassLoader().getResource("json/component_metadata.json")).getFile()), ComponentDslGenerator);

        componentDslMetadataGenerator.addComponentToMetadata(componentModel, "testBuilder");
        System.out.println(componentDslMetadataGenerator.toJson());*/

    }

}