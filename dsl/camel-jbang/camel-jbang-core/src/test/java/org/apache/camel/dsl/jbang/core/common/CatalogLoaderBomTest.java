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
package org.apache.camel.dsl.jbang.core.common;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.camel.tooling.maven.MavenArtifact;
import org.apache.camel.tooling.maven.MavenGav;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogLoaderBomTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void rejectsMissingOrBlankCatalogVersions(String version, @TempDir Path directory) throws Exception {
        String versionElement = version == null ? "" : "<version>" + version + "</version>";
        for (boolean core : new boolean[] { true, false }) {
            Path bom = directory.resolve("bom.pom");
            Files.writeString(bom,
                    """
                            <project><dependencyManagement><dependencies>
                              <dependency><groupId>org.apache.camel</groupId><artifactId>camel-catalog</artifactId>%s</dependency>
                              <dependency><groupId>org.apache.camel.quarkus</groupId><artifactId>camel-quarkus-catalog</artifactId>%s</dependency>
                            </dependencies></dependencyManagement></project>
                            """
                            .formatted(core ? versionElement : "<version>4.8.0</version>",
                                    core ? "<version>3.15.0</version>" : versionElement));
            MavenGav gav = MavenGav.fromCoordinates("test", "bom", "1", "pom", null);
            assertThatThrownBy(() -> CatalogLoader.loadQuarkusCatalog(gav, requested -> {
                assertThat(requested).as("only the BOM should be requested").isEqualTo(gav);
                return new MavenArtifact(requested, bom.toFile());
            })).as("invalid %s catalog version: [%s]", core ? "core" : "Quarkus", version)
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("Cannot determine Camel and Camel Quarkus catalog versions")
                    .hasMessageContaining(gav.toString())
                    .hasMessageContaining("org.apache.camel:camel-catalog")
                    .hasMessageContaining("org.apache.camel.quarkus:camel-quarkus-catalog");
        }
    }

    @Test
    void quarkusBomWithoutCoreCatalogVersionFails(@TempDir Path directory) throws Exception {
        Path bom = directory.resolve("bom.pom");
        Files.writeString(bom, """
                <project><dependencyManagement><dependencies><dependency>
                  <groupId>org.apache.camel.quarkus</groupId>
                  <artifactId>camel-quarkus-catalog</artifactId><version>3.15.0</version>
                </dependency></dependencies></dependencyManagement></project>
                """);
        MavenGav gav = MavenGav.fromCoordinates("test", "bom", "1", "pom", null);
        assertThatThrownBy(() -> CatalogLoader.loadQuarkusCatalog(gav, requested -> {
            assertThat(requested).isEqualTo(gav);
            return new MavenArtifact(requested, bom.toFile());
        })).isInstanceOf(IOException.class).hasMessageContaining("Cannot determine Camel and Camel Quarkus catalog versions");
    }

}
