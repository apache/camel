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
package org.apache.camel.main.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.apache.camel.util.ObjectHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VersionHelperTest {

    @TempDir
    Path directory;

    @Test
    void detectsKameletsVersionFromCatalogMetadata() throws Exception {
        assertEquals("4.22.1.vendor-00001", detectVersion("version=4.22.1.vendor-00001\n"));
    }

    @Test
    void preservesSnapshotKameletsVersion() throws Exception {
        assertEquals("4.24.0-SNAPSHOT", detectVersion("version=4.24.0-SNAPSHOT\n"));
    }

    @Test
    void fallsBackWhenCatalogMetadataIsAbsent() throws Exception {
        assertEquals(KameletCatalogHelper.KAMELETS_VERSION, detectVersion(null));
    }

    @Test
    void fallsBackWhenCatalogMetadataHasNoVersion() throws Exception {
        assertEquals(KameletCatalogHelper.KAMELETS_VERSION, detectVersion("artifactId=camel-kamelets\n"));
    }

    @Test
    void fallsBackWhenCatalogVersionIsEmpty() throws Exception {
        assertEquals(KameletCatalogHelper.KAMELETS_VERSION, detectVersion("version=\n"));
    }

    @Test
    void fallsBackWhenCatalogMetadataCannotBeRead() throws Exception {
        assertEquals(KameletCatalogHelper.KAMELETS_VERSION, detectVersion("version=4.24.0\n", true));
    }

    private String detectVersion(String metadata) throws Exception {
        return detectVersion(metadata, false);
    }

    private String detectVersion(String metadata, boolean unreadable) throws Exception {
        // A renamed JAR prevents filename-based detection, as with a nested launcher dependency.
        Path catalog = directory.resolve("catalog.jar");
        writeCatalog(catalog, metadata);
        URL[] urls = {
                VersionHelper.class.getProtectionDomain().getCodeSource().getLocation(),
                ObjectHelper.class.getProtectionDomain().getCodeSource().getLocation(),
                LoggerFactory.class.getProtectionDomain().getCodeSource().getLocation(),
                catalog.toUri().toURL()
        };
        try (URLClassLoader loader = new URLClassLoader(urls, ClassLoader.getPlatformClassLoader()) {
            @Override
            public InputStream getResourceAsStream(String name) {
                if (unreadable && "META-INF/maven/org.apache.camel.kamelets/camel-kamelets/pom.properties".equals(name)) {
                    return new InputStream() {
                        @Override
                        public int read() throws IOException {
                            throw new IOException("Cannot read catalog metadata");
                        }
                    };
                }
                return super.getResourceAsStream(name);
            }
        }) {
            Class<?> helper = loader.loadClass(VersionHelper.class.getName());
            return (String) helper.getMethod("extractKameletsVersion").invoke(null);
        }
    }

    private void writeCatalog(Path catalog, String metadata) throws IOException {
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(catalog))) {
            if (metadata != null) {
                jar.putNextEntry(new JarEntry("META-INF/maven/org.apache.camel.kamelets/camel-kamelets/pom.properties"));
                jar.write(metadata.getBytes(StandardCharsets.ISO_8859_1));
                jar.closeEntry();
            }
        }
    }
}
