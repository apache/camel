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
package org.apache.camel.dsl.jbang.run.catalog;

import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.apache.camel.dsl.jbang.run.common.VersionHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class KameletCatalogHelperTest {

    @Test
    void releasedCatalogIsLoadedOnce() throws Exception {
        String version = VersionHelper.extractKameletsVersion();
        assumeFalse(version.endsWith("-SNAPSHOT"), "a snapshot catalog is loaded every time");

        // loading the catalog scans the classpath, so a JVM that runs several commands loads a released one once
        Map<String, Object> first = KameletCatalogHelper.loadKamelets(version, null);
        Map<String, Object> second = KameletCatalogHelper.loadKamelets(version, null);

        assertThat(first).containsKey("timer-source");
        assertThat(second).isSameAs(first);
        // the callers share it
        assertThatThrownBy(() -> first.remove("timer-source")).isInstanceOf(UnsupportedOperationException.class);
    }

    private static final String INDEX = """
            {
              "kamelets" : [ {
                "name" : "aws-ddb-sink",
                "type" : "sink",
                "supportLevel" : "Stable",
                "title" : "AWS DynamoDB Sink",
                "description" : "Send data to Amazon DynamoDB. The sent data inserts, updates, or deletes an item.",
                "group" : "AWS DynamoDB Streams",
                "namespace" : "AWS",
                "provider" : "Apache Software Foundation"
              }, {
                "name" : "timer-source",
                "type" : "source",
                "supportLevel" : "Stable",
                "title" : "Timer Source",
                "description" : "Produces periodic messages with a custom payload.",
                "group" : "Timer",
                "namespace" : "Scheduling",
                "provider" : "Apache Software Foundation"
              } ]
            }
            """;

    @Test
    void indexOfTheCamelKameletsJarOfTheVersion(@TempDir Path dir) throws Exception {
        try (URLClassLoader cl = classLoader(jar(dir, "camel-kamelets-9.9.9.jar", INDEX))) {
            List<KameletModel> kamelets = KameletCatalogHelper.loadIndex(cl, "9.9.9");

            assertThat(kamelets).extracting(k -> k.name).containsExactly("aws-ddb-sink", "timer-source");
            KameletModel ddb = kamelets.get(0);
            assertThat(ddb.type).isEqualTo("sink");
            assertThat(ddb.supportLevel).isEqualTo("Stable");
            // the whole description, as the catalog has it: the list shows its first sentence
            assertThat(ddb.description)
                    .isEqualTo("Send data to Amazon DynamoDB. The sent data inserts, updates, or deletes an item.");
        }
    }

    @Test
    void noIndexForAnotherVersion(@TempDir Path dir) throws Exception {
        try (URLClassLoader cl = classLoader(jar(dir, "camel-kamelets-9.9.9.jar", INDEX))) {
            // the Kamelets of another version may differ: the catalog of that version is loaded instead
            assertThat(KameletCatalogHelper.loadIndex(cl, "9.9.8")).isNull();
            assertThat(KameletCatalogHelper.loadIndex(cl, null)).isNull();
        }
    }

    @Test
    void noIndexInTheCamelKameletsJar(@TempDir Path dir) throws Exception {
        // camel-kamelets 4.23 and older have no index
        try (URLClassLoader cl = classLoader(jar(dir, "camel-kamelets-9.9.9.jar", null))) {
            assertThat(KameletCatalogHelper.loadIndex(cl, "9.9.9")).isNull();
        }
    }

    @Test
    void indexOfAnotherJarIsNotUsed(@TempDir Path dir) throws Exception {
        try (URLClassLoader cl = classLoader(jar(dir, "camel-kamelets-catalog-9.9.9.jar", INDEX))) {
            assertThat(KameletCatalogHelper.loadIndex(cl, "9.9.9")).isNull();
        }
    }

    @Test
    void brokenIndexIsNotUsed(@TempDir Path dir) throws Exception {
        try (URLClassLoader cl = classLoader(jar(dir, "camel-kamelets-9.9.9.jar", "{ \"kamelets\" : [ "))) {
            assertThat(KameletCatalogHelper.loadIndex(cl, "9.9.9")).isNull();
        }
    }

    @Test
    void indexHasTheKameletsOfTheCatalog() throws Exception {
        String version = VersionHelper.extractKameletsVersion();
        List<KameletModel> index = KameletCatalogHelper.loadIndex(version);
        // the index is in camel-kamelets 4.24 and newer
        assumeTrue(index != null, "the camel-kamelets JAR " + version + " has no index");

        List<String> fromIndex = new ArrayList<>();
        for (KameletModel km : index) {
            fromIndex.add(km.name + "|" + km.type + "|" + km.supportLevel + "|" + km.description);
        }
        List<String> fromCatalog = new ArrayList<>();
        for (Object o : KameletCatalogHelper.loadKamelets(version, null).values()) {
            KameletModel km = KameletCatalogHelper.createModel(o, false);
            fromCatalog.add(km.name + "|" + km.type + "|" + km.supportLevel + "|" + km.description);
        }
        assertThat(fromIndex).containsExactlyInAnyOrderElementsOf(fromCatalog);
    }

    private static Path jar(Path dir, String name, String index) throws Exception {
        Path jar = dir.resolve(name);
        try (OutputStream os = Files.newOutputStream(jar); JarOutputStream out = new JarOutputStream(os)) {
            out.putNextEntry(new JarEntry("kamelets/timer-source.kamelet.yaml"));
            out.write("kind: Kamelet\n".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
            if (index != null) {
                out.putNextEntry(new JarEntry(KameletCatalogHelper.INDEX));
                out.write(index.getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        return jar;
    }

    private static URLClassLoader classLoader(Path jar) throws Exception {
        // no parent: the camel-kamelets JAR of the build is not seen
        return new URLClassLoader(new URL[] { jar.toUri().toURL() }, null);
    }
}
