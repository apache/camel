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

import java.io.File;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BootClasspathTest {

    @TempDir
    Path dir;

    @Test
    void jarsOfTheClasspath() {
        String m2 = String.join(File.separator, "", "m2", "org", "apache", "camel", "camel-api", "4.24.0") + File.separator;
        BootClasspath cp = BootClasspath.of(String.join(File.pathSeparator,
                m2 + "camel-api-4.24.0.jar", "lib" + File.separator + "netty-transport-native-epoll-4.2.1-linux-x86_64.jar",
                "target" + File.separator + "classes"));

        assertTrue(cp.containsJar("camel-api", "4.24.0", null));
        assertFalse(cp.containsJar("camel-api", "4.23.0", null));
        // another artifact whose name ends with the same text
        assertFalse(cp.containsJar("api", "4.24.0", null));
        // a classifier is part of the name
        assertTrue(cp.containsJar("netty-transport-native-epoll", "4.2.1", "linux-x86_64"));
        assertFalse(cp.containsJar("netty-transport-native-epoll", "4.2.1", null));
        assertFalse(cp.containsJar("camel-api", "4.24.0", "tests"));
        assertTrue(cp.contains("camel-api-4.24.0"));
        assertEquals(3, cp.getEntries().size());
    }

    @Test
    void jarsOfTheManifestOfJavaJar() throws Exception {
        // java -jar camel-launcher.jar: the other JARs are in the Class-Path of its manifest
        Path lib = Files.createDirectories(dir.resolve("my lib"));
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.CLASS_PATH, "camel-api-4.24.0.jar camel-support-4.24.0.jar");
        Path launcher = lib.resolve("camel-launcher-4.24.0.jar");
        try (OutputStream os = Files.newOutputStream(launcher); JarOutputStream jar = new JarOutputStream(os, manifest)) {
            jar.flush();
        }

        BootClasspath cp = BootClasspath.of(launcher.toString());

        assertTrue(cp.containsJar("camel-launcher", "4.24.0", null));
        assertTrue(cp.containsJar("camel-api", "4.24.0", null));
        assertTrue(cp.containsJar("camel-support", "4.24.0", null));
        assertTrue(cp.getEntries().contains(lib.resolve("camel-support-4.24.0.jar").toString()));
        assertTrue(cp.contains("camel-support-4.24.0"));
    }

    @Test
    void noClasspath() {
        BootClasspath cp = BootClasspath.of(null);

        assertFalse(cp.containsJar("camel-api", "4.24.0", null));
        assertFalse(cp.contains("camel-api"));
    }
}
