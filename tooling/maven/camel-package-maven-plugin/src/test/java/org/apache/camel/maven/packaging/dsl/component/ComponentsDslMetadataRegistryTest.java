package org.apache.camel.maven.packaging.dsl.component;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.JsonMapper;
import org.apache.camel.tooling.util.PackageHelper;
import org.junit.jupiter.api.Test;

class ComponentsDslMetadataRegistryTest {

    @Test
    public void testJson() throws IOException {
        final String json = PackageHelper.loadText(new File(Objects.requireNonNull(getClass().getClassLoader().getResource("json/test_component.json")).getFile()));
        final File metadata = new File(Objects.requireNonNull(getClass().getClassLoader().getResource("json/component_metadata.json")).getFile());
        final File componentsDir = new File("/Users/oalsafi/Work/Apache/camel/core/camel-componentdsl/src/main/java/org/apache/camel/builder/component/dsl");

        final ComponentModel componentModel = JsonMapper.generateComponentModel(json);
        final ComponentsDslMetadataRegistry componentsDslMetadataRegistry = new ComponentsDslMetadataRegistry(componentsDir, metadata);

        componentsDslMetadataRegistry.addComponentToMetadataAndSyncMetadataFile(componentModel, "KafkaComponentBuilderFsactory");

        System.out.println(componentsDslMetadataRegistry.getComponentCacheFromMemory());

    }

}