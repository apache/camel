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
package org.apache.camel.maven.packaging.dsl.component;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.JsonMapper;
import org.apache.camel.tooling.util.PackageHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComponentsDslMetadataRegistryTest {

    @Test
    public void testIfSyncCorrectly() throws IOException {
        final String json = PackageHelper.loadText(new File(Objects.requireNonNull(getClass().getClassLoader().getResource("json/test_component2.json")).getFile()));
        final File metadata = new File(Objects.requireNonNull(getClass().getClassLoader().getResource("json/component_metadata.json")).getFile());
        final File metadataWork = metadata.toPath().resolveSibling("component_metadata_work.json").toFile();

        Files.copy(metadata.toPath(), metadataWork.toPath(), StandardCopyOption.REPLACE_EXISTING);

        final String metadataJson = PackageHelper.loadText(metadataWork);

        final File classesDir = FileSystems.getDefault().getPath(".").resolve("src/test/java/org/apache/camel/maven/packaging/dsl/component").toFile();

        final ComponentModel componentModel = JsonMapper.generateComponentModel(json);
        final ComponentsDslMetadataRegistry componentsDslMetadataRegistry = new ComponentsDslMetadataRegistry(classesDir, metadataWork);

        // check for size
        assertEquals(2, componentsDslMetadataRegistry.getComponentCacheFromMemory().size());

        // check if metadata cache is loaded correctly
        componentsDslMetadataRegistry.getComponentCacheFromMemory().forEach((key, value) -> {
            assertTrue(metadataJson.contains(key));
            assertNotNull(value);
        });

        // check behavior when we add component to memory
        // first it adds to he memory cache and then it sync the metadata file by checking existing classes and delete whatever not presented there
        componentsDslMetadataRegistry.addComponentToMetadataAndSyncMetadataFile(componentModel, "ComponentsDslMetadataRegistryTest");

        final String updatedMetadataJson = PackageHelper.loadText(metadataWork);

        // check for the size
        assertEquals(1, componentsDslMetadataRegistry.getComponentCacheFromMemory().size());

        // check if exists
        assertTrue(updatedMetadataJson.contains("ComponentsDslMetadataRegistryTest"));

        // check for the model
        final ComponentModel componentModelUpdated = componentsDslMetadataRegistry.getComponentCacheFromMemory().get("ComponentsDslMetadataRegistryTest");

        assertNotNull(componentModelUpdated);
    }
}
