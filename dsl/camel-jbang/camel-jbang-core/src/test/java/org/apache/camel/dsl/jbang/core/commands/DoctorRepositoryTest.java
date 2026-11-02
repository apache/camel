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
package org.apache.camel.dsl.jbang.core.commands;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.common.CamelJBangConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junitpioneer.jupiter.ClearSystemProperty;
import org.junitpioneer.jupiter.SetSystemProperty;

import static org.assertj.core.api.Assertions.assertThat;

@SetSystemProperty(key = CamelJBangConstants.MAVEN_CENTRAL_ENABLED, value = "false")
@SetSystemProperty(key = CamelJBangConstants.MAVEN_APACHE_SNAPSHOTS, value = "false")
@SetSystemProperty(key = CamelJBangConstants.DOWNLOAD, value = "true")
@SetSystemProperty(key = CamelJBangConstants.FRESH, value = "false")
@ClearSystemProperty(key = CamelJBangConstants.REPOS)
@ClearSystemProperty(key = CamelJBangConstants.MAVEN_SETTINGS)
@ClearSystemProperty(key = CamelJBangConstants.MAVEN_SETTINGS_SECURITY)
@ClearSystemProperty(key = "camel.extra.repos")
@ClearSystemProperty(key = "camel.default.extra.repos.default.value")
@ClearSystemProperty(key = "maven.repo.local")
@ClearSystemProperty(key = "camel.launcher")
class DoctorRepositoryTest extends CamelCommandBaseTestSupport {

    @TempDir
    Path directory;

    private Path repository;

    @BeforeEach
    void configureRepository() throws Exception {
        Path settings = directory.resolve("settings.xml");
        Files.writeString(settings,
                "<settings><localRepository>" + directory.resolve("cache") + "</localRepository></settings>");
        System.setProperty(CamelJBangConstants.MAVEN_SETTINGS, settings.toString());

        repository = directory.resolve("repository");
        String version = new DefaultCamelCatalog().getCatalogVersion();
        Path artifactDirectory = repository.resolve("org/apache/camel/camel-api").resolve(version);
        Files.createDirectories(artifactDirectory);
        Path artifact = artifactDirectory.resolve("camel-api-" + version + ".jar");
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(artifact))) {
            jar.putNextEntry(new JarEntry("META-INF/MANIFEST.MF"));
            jar.write("Manifest-Version: 1.0\n\n".getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
        }
    }

    @Test
    void resolvesArtifactFromConfiguredRepositoryWithEmptyCache() throws Exception {
        System.setProperty(CamelJBangConstants.REPOS, "product=" + repository.toUri());
        Doctor command = new Doctor(new CamelJBangMain().withPrinter(printer));

        assertThat(command.doCall()).isZero();
        assertThat(printer.getOutput()).contains("Artifact resolution (OK)");
        String version = new DefaultCamelCatalog().getCatalogVersion();
        assertThat(
                directory.resolve("cache/org/apache/camel/camel-api").resolve(version).resolve("camel-api-" + version + ".jar"))
                .exists();
    }

    @Test
    void missingArtifactFailsWithEmptyCache() throws Exception {
        Doctor command = new Doctor(new CamelJBangMain().withPrinter(printer));

        assertThat(command.doCall()).isEqualTo(1);
        assertThat(printer.getOutput()).contains("Artifact resolution failed", "Disk Space:");
    }
}
