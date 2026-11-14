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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipFile;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.tooling.maven.MavenArtifact;
import org.apache.camel.tooling.maven.MavenDownloader;
import org.apache.camel.tooling.maven.MavenDownloaderImpl;
import org.apache.camel.tooling.maven.MavenGav;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.SetSystemProperty;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SetSystemProperty(key = CamelJBangConstants.CAMEL_SPRING_BOOT_VERSION, value = "4.8.0")
class CatalogLoaderTest {

    private static final String CAMEL_VERSION = "4.8.0";
    private static MavenDownloader downloader;
    private static MavenArtifact coreCatalog;

    // These released-artifact comparisons deliberately run in CI to catch catalog resource mixing.
    @BeforeAll
    static void downloadCatalog() {
        downloader = new MavenDownloaderImpl();
        downloader.build();
        coreCatalog = artifact("org.apache.camel", "camel-catalog", CAMEL_VERSION);
    }

    @AfterAll
    static void closeDownloader() throws Exception {
        if (downloader != null) {
            downloader.stop();
        }
    }

    @Test
    void mainCatalogUsesTargetResources() throws Exception {
        CamelCatalog catalog = CatalogLoader.loadCatalog(null, CAMEL_VERSION, true);
        assertCoreCatalog(catalog);
        assertProvider(catalog, coreCatalog, "org/apache/camel/catalog", "org.apache.camel", "camel-http", CAMEL_VERSION);
    }

    @Test
    void springBootCatalogUsesTargetResources() throws Exception {
        CamelCatalog catalog = CatalogLoader.loadSpringBootCatalog(null, CAMEL_VERSION, true);
        assertCoreCatalog(catalog);
        assertProvider(catalog, artifact("org.apache.camel.springboot", "camel-catalog-provider-springboot", CAMEL_VERSION),
                "org/apache/camel/springboot/catalog", "org.apache.camel.springboot", "camel-http-starter", CAMEL_VERSION);
        assertThat(catalog.getRuntimeProviderLoadedVersion()).isEqualTo(CAMEL_VERSION);
    }

    @Test
    void quarkusCatalogUsesBomCoreAndProviderVersions() throws Exception {
        CamelCatalog catalog = CatalogLoader.loadQuarkusCatalog(
                MavenGav.fromCoordinates("io.quarkus.platform", "quarkus-camel-bom", "3.15.1", "pom", null),
                downloader::resolveArtifact);
        assertCoreCatalog(catalog);
        assertProvider(catalog, artifact("org.apache.camel.quarkus", "camel-quarkus-catalog", "3.15.0"),
                "org/apache/camel/catalog/quarkus", "org.apache.camel.quarkus", "camel-quarkus-http", "3.15.0");
        assertThat(catalog.getRuntimeProviderLoadedVersion()).isEqualTo("3.15.0");
    }

    @Test
    void springBootProviderVersionCanDifferFromCore() throws Exception {
        CamelCatalog current = new DefaultCamelCatalog();
        CamelCatalog catalog
                = CatalogLoader.loadSpringBootCatalog(null, current.getCatalogVersion(), true);
        assertThat(catalog.getCatalogVersion()).isEqualTo(current.getCatalogVersion());
        assertThat(catalog.modelJSonSchema("split")).isEqualTo(current.modelJSonSchema("split"));
        assertThat(catalog.getRuntimeProviderLoadedVersion()).isEqualTo(CAMEL_VERSION);
        assertProvider(catalog, artifact("org.apache.camel.springboot", "camel-catalog-provider-springboot", CAMEL_VERSION),
                "org/apache/camel/springboot/catalog", "org.apache.camel.springboot", "camel-http-starter", CAMEL_VERSION);
    }

    @Test
    void quarkusMissingCoreCatalogFails() {
        MavenGav bom = MavenGav.fromCoordinates("io.quarkus.platform", "quarkus-camel-bom", "3.15.1", "pom", null);
        assertThatThrownBy(() -> CatalogLoader.loadQuarkusCatalog(bom, gav -> {
            if ("org.apache.camel".equals(gav.getGroupId()) && "camel-catalog".equals(gav.getArtifactId())) {
                return null;
            }
            return downloader.resolveArtifact(gav);
        })).isInstanceOf(IOException.class).hasMessageContaining("Cannot download org.apache.camel:camel-catalog:4.8.0");
    }

    private static MavenArtifact artifact(String groupId, String artifactId, String version) {
        return downloader.resolveArtifact(MavenGav.fromCoordinates(groupId, artifactId, version, "jar", null));
    }

    private static void assertCoreCatalog(CamelCatalog catalog) throws IOException {
        assertThat(catalog.getCatalogVersion()).isEqualTo(CAMEL_VERSION);
        assertThat(new DefaultCamelCatalog().modelJSonSchema("split")).contains("errorThreshold");
        assertThat(catalog.modelJSonSchema("split")).doesNotContain("errorThreshold");
        try (ZipFile jar = new ZipFile(coreCatalog.getFile())) {
            for (String name : catalog.findModelNames()) {
                assertThat(catalog.modelJSonSchema(name)).as("model %s", name)
                        .isEqualTo(resource(jar, "org/apache/camel/catalog/models/" + name + ".json"));
            }
        }
        assertThat(new DefaultCamelCatalog().modelJSonSchema("tokenizer")).isNotNull();
        assertThat(catalog.modelJSonSchema("tokenizer")).isNull();
        assertThat(catalog.findModelNames()).doesNotContain("tokenizer");
    }

    private static void assertProvider(
            CamelCatalog catalog, MavenArtifact artifact, String resourcePath, String groupId, String artifactId,
            String version)
            throws IOException {
        try (ZipFile jar = new ZipFile(artifact.getFile())) {
            for (String name : catalog.findComponentNames()) {
                assertThat(catalog.componentJSonSchema(name)).as("component %s", name)
                        .isEqualTo(resource(jar, resourcePath + "/components/" + name + ".json"));
            }
            for (String name : catalog.findDataFormatNames()) {
                assertThat(catalog.dataFormatJSonSchema(name)).as("data format %s", name)
                        .isEqualTo(resource(jar, resourcePath + "/dataformats/" + name + ".json"));
            }
            for (String name : catalog.findLanguageNames()) {
                assertThat(catalog.languageJSonSchema(name)).as("language %s", name)
                        .isEqualTo(resource(jar, resourcePath + "/languages/" + name + ".json"));
            }
        }
        assertThat(catalog.componentModel("http").getGroupId()).isEqualTo(groupId);
        assertThat(catalog.componentModel("http").getArtifactId()).isEqualTo(artifactId);
        assertThat(catalog.componentModel("http").getVersion()).isEqualTo(version);
    }

    private static String resource(ZipFile jar, String name) throws IOException {
        assertThat(jar.getEntry(name)).as("target resource %s", name).isNotNull();
        try (InputStream stream = jar.getInputStream(jar.getEntry(name))) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
