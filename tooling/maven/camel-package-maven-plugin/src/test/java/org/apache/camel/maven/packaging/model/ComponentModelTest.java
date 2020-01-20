package org.apache.camel.maven.packaging.model;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

import org.apache.camel.tooling.util.PackageHelper;
import org.junit.jupiter.api.Test;

public class ComponentModelTest {

    @Test
    public void testMe() throws IOException {
        final String json = PackageHelper.loadText(new File(Objects.requireNonNull(getClass().getClassLoader().getResource("json/test_component.json")).getFile()));

        final ComponentModel componentModel = ComponentModel.generateComponentModelFromJsonString(json);

        System.out.println(componentModel);
    }

}