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
package org.apache.camel.main.download;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.Resource;
import org.apache.camel.support.PluginHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * CAMEL-27476: the launcher bundles a camel-kamelets jar; the one downloaded for --kamelets-version must win.
 */
class DependencyDownloaderClassLoaderTest {

    @TempDir
    Path dir;

    @Test
    void kameletFromDownloadedJarWins() throws Exception {
        try (URLClassLoader launcher = launcher();
             DependencyDownloaderClassLoader cl = new DependencyDownloaderClassLoader(launcher)) {
            cl.addFile(jar("camel-kamelets-4.24.0.jar", Map.of(
                    "kamelets/postgresql-sink.kamelet.yaml", "4.24",
                    "other.properties", "4.24")).toFile());

            assertEquals("4.24", read(cl.getResource("kamelets/postgresql-sink.kamelet.yaml")));
            // a Kamelet only the launcher has is still found
            assertEquals("4.22", read(cl.getResource("kamelets/old-sink.kamelet.yaml")));
            // any other resource is parent first, as before
            assertEquals("4.22", read(cl.getResource("other.properties")));
        }
    }

    @Test
    void kameletResourceOfCamelContext() throws Exception {
        try (URLClassLoader launcher = launcher();
             DependencyDownloaderClassLoader cl = new DependencyDownloaderClassLoader(launcher);
             CamelContext context = new DefaultCamelContext()) {
            cl.addFile(jar("camel-kamelets-4.24.0.jar", Map.of(
                    "kamelets/postgresql-sink.kamelet.yaml", "4.24")).toFile());
            context.setApplicationContextClassLoader(cl);

            Resource resource = PluginHelper.getResourceLoader(context)
                    .resolveResource("classpath:kamelets/postgresql-sink.kamelet.yaml");
            try (InputStream is = resource.getInputStream()) {
                assertEquals("4.24", new String(is.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
    }

    @Test
    void launcherKameletWhenNothingDownloaded() throws Exception {
        try (URLClassLoader launcher = launcher();
             DependencyDownloaderClassLoader cl = new DependencyDownloaderClassLoader(launcher)) {
            assertEquals("4.22", read(cl.getResource("kamelets/postgresql-sink.kamelet.yaml")));
        }
    }

    private URLClassLoader launcher() throws Exception {
        Path jar = jar("camel-kamelets-4.22.0.jar", Map.of(
                "kamelets/postgresql-sink.kamelet.yaml", "4.22",
                "kamelets/old-sink.kamelet.yaml", "4.22",
                "other.properties", "4.22"));
        // no parent: only the launcher jar, so the test classpath does not interfere
        return new URLClassLoader(new URL[] { jar.toUri().toURL() }, null);
    }

    private Path jar(String name, Map<String, String> entries) throws Exception {
        Path jar = dir.resolve(name);
        try (OutputStream os = Files.newOutputStream(jar);
             JarOutputStream jos = new JarOutputStream(os)) {
            for (Map.Entry<String, String> e : entries.entrySet()) {
                jos.putNextEntry(new JarEntry(e.getKey()));
                jos.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                jos.closeEntry();
            }
        }
        return jar;
    }

    private static String read(URL url) throws Exception {
        try (InputStream is = url.openStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
